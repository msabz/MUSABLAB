package com.musablab.agent.testengine

import android.content.Context
import com.musablab.agent.shizuku.ShizukuController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class TestEngine(private val context: Context) {
    data class RunResult(val status: String, val timeline: JSONArray, val evidenceDir: File, val errors: JSONArray)

    suspend fun run(sessionId: String, plan: TestPlan): RunResult {
        val root = File(context.filesDir, "sessions/$sessionId").apply { mkdirs() }
        val timeline = JSONArray()
        val errors = JSONArray()
        val failed = AtomicBoolean(false)
        val startedNs = System.nanoTime()

        suspend fun record(node: String, state: String, detail: JSONObject = JSONObject()) {
            synchronized(timeline) {
                timeline.put(JSONObject()
                    .put("node", node)
                    .put("state", state)
                    .put("elapsedMs", (System.nanoTime() - startedNs) / 1_000_000.0)
                    .put("detail", detail))
            }
        }

        suspend fun executeNodes(nodes: List<TestNode>) {
            for (node in nodes) {
                if (failed.get()) break
                when (node) {
                    is TestNode.Step -> {
                        record(node.id, "STARTED")
                        runCatching { executeStep(plan.packageName, node, root) }
                            .onSuccess { record(node.id, "COMPLETE", it) }
                            .onFailure {
                                failed.set(true)
                                synchronized(errors) { errors.put("${node.id}: ${it.message}") }
                                record(node.id, "FAILED", JSONObject().put("error", it.message ?: it.javaClass.simpleName))
                            }
                    }
                    is TestNode.If -> {
                        val value = evaluate(plan.packageName, node.condition)
                        record(node.id, "CONDITION", JSONObject().put("value", value))
                        executeNodes(if (value) node.thenNodes else node.elseNodes)
                    }
                    is TestNode.Retry -> {
                        var passed = false
                        for (attempt in 1..node.maxAttempts) {
                            record(node.id, "ATTEMPT", JSONObject().put("attempt", attempt))
                            failed.set(false)
                            executeNodes(node.nodes)
                            val conditionOk = node.condition?.let { evaluate(plan.packageName, it) } ?: !failed.get()
                            if (conditionOk && !failed.get()) { passed = true; break }
                            failed.set(false)
                            delay((250L * attempt).coerceAtMost(1500L))
                        }
                        if (!passed) {
                            failed.set(true)
                            errors.put("${node.id}: retry exhausted")
                            record(node.id, "FAILED", JSONObject().put("reason", "retry exhausted"))
                        }
                    }
                    is TestNode.Parallel -> coroutineScope {
                        val branchFailures = node.branches.mapIndexed { index, branch ->
                            async(Dispatchers.IO) {
                                runCatching { executeNodes(branch) }.exceptionOrNull()?.let { index to it }
                            }
                        }.awaitAll().filterNotNull()
                        if (branchFailures.isNotEmpty()) {
                            failed.set(true)
                            branchFailures.forEach { errors.put("${node.id}/branch-${it.first}: ${it.second.message}") }
                        }
                        record(node.id, if (branchFailures.isEmpty()) "COMPLETE" else "FAILED")
                    }
                }
            }
        }

        executeNodes(plan.nodes)
        return RunResult(if (failed.get()) "FAILED" else "PASS", timeline, root, errors)
    }

    private suspend fun executeStep(pkg: String, step: TestNode.Step, root: File): JSONObject = withContext(Dispatchers.IO) {
        val svc = ShizukuController.requireService()
        val a = step.args
        fun exec(command: String, timeout: Int = a.optInt("timeoutMs", 15_000)): JSONObject = JSONObject(svc.exec(command, timeout))
        when (step.action) {
            "launch" -> {
                val resolved = exec("cmd package resolve-activity --brief $pkg", 10_000)
                val component = resolved.optString("stdout").trim().lineSequence().lastOrNull().orEmpty()
                if (component.contains('/')) exec("am start -W -n '${component.replace("'", "")}'", 30_000)
                else exec("monkey -p $pkg -c android.intent.category.LAUNCHER 1", 30_000)
            }
            "force_stop" -> exec("am force-stop $pkg")
            "clear_logcat" -> exec("logcat -c")
            "wait_ms" -> { Thread.sleep(a.optLong("ms", 0).coerceIn(0, 30_000)); JSONObject().put("waited", true) }
            "tap" -> exec("input tap ${a.getInt("x")} ${a.getInt("y")}")
            "swipe" -> exec("input swipe ${a.getInt("x1")} ${a.getInt("y1")} ${a.getInt("x2")} ${a.getInt("y2")} ${a.optInt("durationMs", 300)}")
            "keyevent" -> exec("input keyevent ${a.getInt("keycode")}")
            "text" -> {
                val value = a.getString("value")
                require(value.length <= 500 && value.matches(Regex("[A-Za-z0-9 ._@%+\\-]*"))) { "unsupported input text" }
                exec("input text '${value.replace(" ", "%s")}'")
            }
            "assert_process" -> {
                val r = exec("pidof $pkg", 5_000)
                if (r.optInt("rc") != 0 || r.optString("stdout").isBlank()) error("process is not running")
                r
            }
            "wait_process" -> {
                val timeout = a.optLong("timeoutMs", 10_000).coerceIn(100, 60_000)
                val deadline = System.nanoTime() + timeout * 1_000_000
                var seen = false
                while (System.nanoTime() < deadline) {
                    val r = exec("pidof $pkg", 3_000)
                    if (r.optInt("rc") == 0 && r.optString("stdout").isNotBlank()) { seen = true; break }
                    Thread.sleep(100)
                }
                if (!seen) error("process did not become ready")
                JSONObject().put("running", true)
            }
            "capture_logcat" -> saveText(root, "${step.id}-logcat.txt", exec("logcat -d -v threadtime -t ${a.optInt("lines", 1500).coerceIn(50, 10000)}", 30_000).optString("stdout"))
            "capture_meminfo" -> saveText(root, "${step.id}-meminfo.txt", exec("dumpsys meminfo $pkg", 20_000).optString("stdout"))
            "capture_gfxinfo" -> saveText(root, "${step.id}-gfxinfo.txt", exec("dumpsys gfxinfo $pkg", 20_000).optString("stdout"))
            "capture_ui" -> saveText(root, "${step.id}-ui.xml", exec("uiautomator dump /data/local/tmp/musablab-ui.xml >/dev/null 2>&1; cat /data/local/tmp/musablab-ui.xml; rm -f /data/local/tmp/musablab-ui.xml", 30_000).optString("stdout"))
            "capture_screenshot" -> {
                val file = File(root, "${step.id}-screen.png")
                file.writeBytes(svc.execBytes("screencap -p", 30_000))
                JSONObject().put("file", file.name).put("bytes", file.length())
            }
            "capture_bundle" -> coroutineScope {
                val commands = listOf(
                    "logcat" to "logcat -d -v threadtime -t 2000",
                    "meminfo" to "dumpsys meminfo $pkg",
                    "gfxinfo" to "dumpsys gfxinfo $pkg",
                    "package" to "dumpsys package $pkg"
                )
                val results = commands.map { (name, cmd) -> async(Dispatchers.IO) {
                    val r = JSONObject(svc.exec(cmd, 30_000))
                    saveText(root, "${step.id}-$name.txt", r.optString("stdout"))
                }}.awaitAll()
                val screen = File(root, "${step.id}-screen.png").apply { writeBytes(svc.execBytes("screencap -p", 30_000)) }
                JSONObject().put("files", JSONArray(results.map { it.getString("file") } + screen.name))
            }
            else -> error("unsupported action: ${step.action}")
        }
    }

    private suspend fun evaluate(pkg: String, c: Condition): Boolean = withContext(Dispatchers.IO) {
        val svc = ShizukuController.requireService()
        fun exec(cmd: String): JSONObject = JSONObject(svc.exec(cmd, c.args.optInt("timeoutMs", 10_000)))
        when (c.type) {
            "process_running" -> exec("pidof $pkg").let { it.optInt("rc") == 0 && it.optString("stdout").isNotBlank() }
            "log_contains" -> exec("logcat -d -t ${c.args.optInt("lines", 500).coerceIn(50, 5000)}").optString("stdout").contains(c.args.getString("text"), ignoreCase = c.args.optBoolean("ignoreCase", false))
            "ui_contains" -> exec("uiautomator dump /data/local/tmp/musablab-ui.xml >/dev/null 2>&1; cat /data/local/tmp/musablab-ui.xml; rm -f /data/local/tmp/musablab-ui.xml").optString("stdout").contains(c.args.getString("text"), true)
            else -> error("unsupported condition: ${c.type}")
        }
    }

    private fun saveText(root: File, name: String, text: String): JSONObject {
        val file = File(root, name).apply { writeText(text) }
        return JSONObject().put("file", file.name).put("bytes", file.length())
    }
}
