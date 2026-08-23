package com.musablab.agent.report

import android.content.Context
import com.musablab.agent.BuildConfig
import com.musablab.agent.data.DeviceIdentity
import com.musablab.agent.github.GitHubApi
import com.musablab.agent.testengine.TestEngine
import com.musablab.agent.testengine.TestPlan
import org.json.JSONObject

class ReportWriter(private val context: Context, private val github: GitHubApi) {
    suspend fun publish(
        repo: String,
        sessionId: String,
        commandId: String,
        plan: TestPlan,
        result: TestEngine.RunResult,
        source: JSONObject
    ) {
        val deviceId = DeviceIdentity.id(context)
        val prefix = "test-results/$sessionId/devices/$deviceId"
        result.evidenceDir.listFiles()?.filter { it.isFile }?.forEach { file ->
            github.putBytes(repo, "$prefix/evidence/${file.name}", file.readBytes(), "test($sessionId): evidence ${file.name}")
        }
        val report = JSONObject()
            .put("schema", 1)
            .put("sessionId", sessionId)
            .put("commandId", commandId)
            .put("status", result.status)
            .put("test", plan.name)
            .put("package", plan.packageName)
            .put("device", DeviceIdentity.snapshot(context))
            .put("agent", JSONObject().put("version", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE).put("protocol", BuildConfig.AGENT_PROTOCOL))
            .put("source", source)
            .put("timeline", result.timeline)
            .put("errors", result.errors)
        github.putText(repo, "$prefix/report.json", report.toString(2), "test($sessionId): device report $deviceId")
        github.putText(repo, "$prefix/report.md", markdown(report), "test($sessionId): device summary $deviceId")
    }

    private fun markdown(r: JSONObject): String = buildString {
        appendLine("# MUSABLAB Test Report")
        appendLine()
        appendLine("- Session: `${r.getString("sessionId")}`")
        appendLine("- Status: **${r.getString("status")}**")
        appendLine("- Test: `${r.getString("test")}`")
        appendLine("- Package: `${r.getString("package")}`")
        appendLine("- Device: `${r.getJSONObject("device").optString("manufacturer")} ${r.getJSONObject("device").optString("model")}`")
        appendLine("- Android: `${r.getJSONObject("device").optInt("sdk")}`")
        appendLine()
        appendLine("## Errors")
        val errors = r.getJSONArray("errors")
        if (errors.length() == 0) appendLine("None.") else for (i in 0 until errors.length()) appendLine("- ${errors.getString(i)}")
        appendLine()
        appendLine("## Timeline")
        appendLine("```json")
        appendLine(r.getJSONArray("timeline").toString(2))
        appendLine("```")
    }
}
