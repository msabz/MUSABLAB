package com.musablab.agent.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.musablab.agent.BuildConfig
import com.musablab.agent.artifact.ArtifactInstaller
import com.musablab.agent.data.DeviceIdentity
import com.musablab.agent.github.GitHubApi
import com.musablab.agent.report.ReportWriter
import com.musablab.agent.security.SecureTokenStore
import com.musablab.agent.shizuku.ShizukuController
import com.musablab.agent.testengine.TestEngine
import com.musablab.agent.testengine.TestPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class LabAgentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1001, notification("Starting"))
        loopJob = scope.launch { loop() }
    }

    override fun onDestroy() {
        loopJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun loop() {
        val tokenStore = SecureTokenStore(this)
        var etag: String? = null
        var lastCommandId = getSharedPreferences("musablab_agent", MODE_PRIVATE).getString("last_command", null)
        while (true) {
            try {
                val token = tokenStore.getToken()
                if (token == null) {
                    updateNotification("GitHub login required")
                    delay(1500)
                    continue
                }
                ShizukuController.refreshState()
                if (ShizukuController.state.value != ShizukuController.State.READY) {
                    updateNotification("Shizuku permission required")
                    delay(1000)
                    continue
                }
                val github = GitHubApi(token)
                val deviceId = DeviceIdentity.id(this)
                ensureRegistered(github, deviceId)
                val commandPath = "control/devices/$deviceId/command.json"
                when (val result = github.getContent(BuildConfig.CONTROL_REPO, commandPath, BuildConfig.CONTROL_BRANCH, etag)) {
                    is GitHubApi.ContentResult.NotModified -> Unit
                    is GitHubApi.ContentResult.Missing -> {
                        github.putText(BuildConfig.CONTROL_REPO, commandPath, JSONObject().put("schema", 1).put("commandId", JSONObject.NULL).toString(2), "agent: initialize command inbox for $deviceId")
                        etag = null
                    }
                    is GitHubApi.ContentResult.Content -> {
                        etag = result.etag
                        val cmd = JSONObject(String(result.bytes, Charsets.UTF_8))
                        val commandId = cmd.optString("commandId").takeIf { it.isNotBlank() && it != "null" }
                        if (commandId != null && commandId != lastCommandId) {
                            execute(github, cmd, commandId)
                            lastCommandId = commandId
                            getSharedPreferences("musablab_agent", MODE_PRIVATE).edit().putString("last_command", commandId).apply()
                        }
                    }
                }
                updateNotification("READY • ${deviceId.take(8)}")
                delay(1000)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                updateNotification("ERROR • ${t.message?.take(60)}")
                delay(3000)
            }
        }
    }

    private suspend fun ensureRegistered(github: GitHubApi, deviceId: String) {
        val prefs = getSharedPreferences("musablab_agent", MODE_PRIVATE)
        if (prefs.getBoolean("registered", false)) return
        val status = DeviceIdentity.snapshot(this)
            .put("state", "READY")
            .put("agentVersion", BuildConfig.VERSION_NAME)
            .put("protocol", BuildConfig.AGENT_PROTOCOL)
            .put("shizuku", ShizukuController.serviceOrNull()?.ping() ?: "unavailable")
        github.putText(BuildConfig.CONTROL_REPO, "control/devices/$deviceId/status.json", status.toString(2), "agent: register device $deviceId")
        prefs.edit().putBoolean("registered", true).apply()
    }

    private suspend fun execute(github: GitHubApi, cmd: JSONObject, commandId: String) {
        require(cmd.optInt("schema") == 1) { "unsupported command schema" }
        val deviceId = DeviceIdentity.id(this)
        val sessionId = cmd.optString("sessionId", commandId)
        updateNotification("BUSY • $sessionId")
        val source = cmd.optJSONObject("source") ?: JSONObject()
        val packageName = cmd.getString("package")
        val kind = cmd.optString("kind", "test_installed")

        if (kind == "test_artifact") {
            val repo = source.getString("repo")
            val artifactId = source.getLong("artifactId")
            val dir = File(cacheDir, "artifacts/$commandId").apply { deleteRecursively(); mkdirs() }
            val apk = github.downloadArtifactZip(repo, artifactId, dir)
            val mode = when (cmd.optString("installMode", "update")) {
                "fresh" -> ArtifactInstaller.Mode.FRESH
                else -> ArtifactInstaller.Mode.UPDATE
            }
            ArtifactInstaller(this).install(apk, packageName, mode)
        }

        val planObj = cmd.getJSONObject("plan")
        if (!planObj.has("package")) planObj.put("package", packageName)
        val plan = TestPlan.parse(planObj)
        val result = TestEngine(this).run(sessionId, plan)
        ReportWriter(this, github).publish(BuildConfig.CONTROL_REPO, sessionId, commandId, plan, result, source)
        val status = JSONObject()
            .put("deviceId", deviceId)
            .put("state", "READY")
            .put("lastSession", sessionId)
            .put("lastCommand", commandId)
            .put("lastResult", result.status)
            .put("agentVersion", BuildConfig.VERSION_NAME)
        github.putText(BuildConfig.CONTROL_REPO, "control/devices/$deviceId/status.json", status.toString(2), "test($sessionId): update device status")
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("agent", "MUSABLAB Agent", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, "agent")
        .setContentTitle("MUSABLAB")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setOngoing(true)
        .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(1001, notification(text))
    }
}
