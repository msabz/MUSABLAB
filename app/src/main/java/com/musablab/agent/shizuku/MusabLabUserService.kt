package com.musablab.agent.shizuku

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.annotation.Keep
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@Keep
class MusabLabUserService() : IMusabLabService.Stub() {
    @Keep
    constructor(context: Context) : this()

    override fun ping(): String = "uid=${Os.getuid()},pid=${Os.getpid()}"

    override fun exec(command: String, timeoutMs: Int): String {
        require(command.length <= 16_384) { "command too large" }
        val result = runProcess(arrayOf("sh", "-c", command), timeoutMs.coerceIn(100, 120_000))
        return JSONObject()
            .put("rc", result.rc)
            .put("stdout", result.stdout.toString(Charsets.UTF_8).take(1_000_000))
            .put("stderr", result.stderr.toString(Charsets.UTF_8).take(250_000))
            .put("timedOut", result.timedOut)
            .toString()
    }

    override fun execBytes(command: String, timeoutMs: Int): ByteArray {
        require(command.length <= 4096) { "command too large" }
        val result = runProcess(arrayOf("sh", "-c", command), timeoutMs.coerceIn(100, 120_000))
        if (result.rc != 0 || result.timedOut) {
            throw IllegalStateException("command failed rc=${result.rc}: ${result.stderr.toString(Charsets.UTF_8).take(500)}")
        }
        return result.stdout
    }

    override fun installApk(apk: ParcelFileDescriptor, sizeBytes: Long, replaceExisting: Boolean): Int {
        require(sizeBytes > 0 && sizeBytes <= 4L * 1024 * 1024 * 1024) { "invalid APK size" }
        val args = mutableListOf("pm", "install")
        if (replaceExisting) args += "-r"
        args += listOf("-S", sizeBytes.toString(), "-")
        val process = ProcessBuilder(args).start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val tOut = thread(start = true, name = "musablab-pm-out") { process.inputStream.use { it.copyTo(stdout) } }
        val tErr = thread(start = true, name = "musablab-pm-err") { process.errorStream.use { it.copyTo(stderr) } }
        FileInputStream(apk.fileDescriptor).use { input ->
            process.outputStream.use { output -> input.copyTo(output, 256 * 1024) }
        }
        val finished = process.waitFor(180, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        tOut.join(2000)
        tErr.join(2000)
        return if (finished) process.exitValue() else 124
    }

    override fun destroy() {
        System.exit(0)
    }

    private data class ProcessResult(
        val rc: Int,
        val stdout: ByteArray,
        val stderr: ByteArray,
        val timedOut: Boolean
    )

    private fun runProcess(argv: Array<String>, timeoutMs: Int): ProcessResult {
        val p = ProcessBuilder(*argv).start()
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val tOut = thread(start = true, name = "musablab-stdout") { p.inputStream.use { it.copyTo(out) } }
        val tErr = thread(start = true, name = "musablab-stderr") { p.errorStream.use { it.copyTo(err) } }
        val ok = p.waitFor(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        if (!ok) p.destroyForcibly()
        tOut.join(1500)
        tErr.join(1500)
        return ProcessResult(if (ok) p.exitValue() else 124, out.toByteArray(), err.toByteArray(), !ok)
    }
}
