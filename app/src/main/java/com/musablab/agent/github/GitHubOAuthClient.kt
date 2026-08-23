package com.musablab.agent.github

import com.musablab.agent.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GitHubOAuthClient {
    data class DeviceFlow(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val interval: Int
    )

    suspend fun startDeviceFlow(): DeviceFlow = withContext(Dispatchers.IO) {
        val clientId = BuildConfig.GITHUB_CLIENT_ID
        require(clientId.isNotBlank()) { "GitHub OAuth client id is not configured in this build" }
        val json = postForm(
            "https://github.com/login/device/code",
            mapOf("client_id" to clientId, "scope" to "repo")
        )
        DeviceFlow(
            json.getString("device_code"),
            json.getString("user_code"),
            json.getString("verification_uri"),
            json.getInt("expires_in"),
            json.optInt("interval", 5)
        )
    }

    suspend fun awaitToken(flow: DeviceFlow): String {
        val started = System.currentTimeMillis()
        var interval = flow.interval.coerceAtLeast(5)
        while ((System.currentTimeMillis() - started) < flow.expiresIn * 1000L) {
            delay(interval * 1000L)
            val json = withContext(Dispatchers.IO) {
                postForm(
                    "https://github.com/login/oauth/access_token",
                    mapOf(
                        "client_id" to BuildConfig.GITHUB_CLIENT_ID,
                        "device_code" to flow.deviceCode,
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                    )
                )
            }
            json.optString("access_token").takeIf { it.isNotBlank() }?.let { return it }
            when (json.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token" -> error("GitHub authorization code expired")
                "access_denied" -> error("GitHub authorization was denied")
                else -> if (json.has("error")) error(json.optString("error_description", json.getString("error")))
            }
        }
        error("GitHub authorization timed out")
    }

    private fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8") }=${URLEncoder.encode(it.value, "UTF-8") }"
        }.toByteArray()
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.doOutput = true
        conn.outputStream.use { it.write(body) }
        val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().use { it.readText() }
        return JSONObject(text)
    }
}
