package com.musablab.agent.github

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class GitHubApi(private val token: String) {
    sealed class ContentResult {
        data class Content(val bytes: ByteArray, val etag: String?, val sha: String?) : ContentResult()
        data object NotModified : ContentResult()
        data object Missing : ContentResult()
    }

    suspend fun getContent(repo: String, path: String, ref: String = "main", etag: String? = null): ContentResult = withContext(Dispatchers.IO) {
        val conn = request("GET", "https://api.github.com/repos/$repo/contents/${encodePath(path)}?ref=$ref")
        if (etag != null) conn.setRequestProperty("If-None-Match", etag)
        when (conn.responseCode) {
            304 -> ContentResult.NotModified
            404 -> ContentResult.Missing
            in 200..299 -> {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val raw = json.getString("content").replace("\n", "")
                ContentResult.Content(Base64.decode(raw, Base64.DEFAULT), conn.getHeaderField("ETag"), json.optString("sha").ifBlank { null })
            }
            else -> error(readError(conn))
        }
    }

    suspend fun putText(repo: String, path: String, text: String, message: String, branch: String = "main") {
        putBytes(repo, path, text.toByteArray(Charsets.UTF_8), message, branch)
    }

    suspend fun putBytes(repo: String, path: String, bytes: ByteArray, message: String, branch: String = "main") = withContext(Dispatchers.IO) {
        val existing = when (val current = getContentSync(repo, path, branch)) {
            is ContentResult.Content -> current.sha
            else -> null
        }
        val body = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("branch", branch)
        if (existing != null) body.put("sha", existing)
        val conn = request("PUT", "https://api.github.com/repos/$repo/contents/${encodePath(path)}")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        if (conn.responseCode !in 200..299) error(readError(conn))
    }

    suspend fun getUserLogin(): String = withContext(Dispatchers.IO) {
        val conn = request("GET", "https://api.github.com/user")
        if (conn.responseCode !in 200..299) error(readError(conn))
        JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getString("login")
    }

    suspend fun downloadArtifactZip(repo: String, artifactId: Long, destinationDir: File): File = withContext(Dispatchers.IO) {
        destinationDir.mkdirs()
        val conn = request("GET", "https://api.github.com/repos/$repo/actions/artifacts/$artifactId/zip")
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) error(readError(conn))
        var apk: File? = null
        ZipInputStream(conn.inputStream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                    val safeName = File(entry.name).name
                    val out = File(destinationDir, safeName)
                    out.outputStream().use { zip.copyTo(it, 256 * 1024) }
                    if (apk == null) apk = out
                }
                zip.closeEntry()
            }
        }
        apk ?: error("GitHub artifact did not contain an APK")
    }

    private fun getContentSync(repo: String, path: String, ref: String): ContentResult {
        val conn = request("GET", "https://api.github.com/repos/$repo/contents/${encodePath(path)}?ref=$ref")
        return when (conn.responseCode) {
            404 -> ContentResult.Missing
            in 200..299 -> {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                ContentResult.Content(ByteArray(0), conn.getHeaderField("ETag"), json.optString("sha").ifBlank { null })
            }
            else -> error(readError(conn))
        }
    }

    private fun request(method: String, url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 15_000
        readTimeout = 60_000
        setRequestProperty("Authorization", "Bearer $token")
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        setRequestProperty("User-Agent", "MUSABLAB/${com.musablab.agent.BuildConfig.VERSION_NAME}")
    }

    private fun encodePath(path: String): String = path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun readError(conn: HttpURLConnection): String {
        val body = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        return "GitHub API ${conn.responseCode}: ${body.take(2000)}"
    }
}
