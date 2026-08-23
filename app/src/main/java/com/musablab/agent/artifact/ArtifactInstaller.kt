package com.musablab.agent.artifact

import android.content.Context
import android.os.ParcelFileDescriptor
import com.musablab.agent.shizuku.ShizukuController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class ArtifactInstaller(private val context: Context) {
    enum class Mode { UPDATE, FRESH }

    suspend fun install(apk: File, packageName: String, mode: Mode): JSONObject = withContext(Dispatchers.IO) {
        val svc = ShizukuController.requireService()
        if (mode == Mode.FRESH) {
            val uninstall = JSONObject(svc.exec("pm uninstall ${quote(packageName)}", 60_000))
            // A missing package is acceptable for a fresh install.
            if (uninstall.optInt("rc", 0) != 0 && !uninstall.optString("stdout").contains("Unknown package", true)) {
                // Continue: pm install below is the authoritative result.
            }
        }
        ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            val rc = svc.installApk(pfd, apk.length(), mode == Mode.UPDATE)
            if (rc != 0) error("pm install failed rc=$rc")
        }
        val verify = JSONObject(svc.exec("dumpsys package ${quote(packageName)} | grep -m1 -E 'versionCode=|versionName='", 15_000))
        JSONObject().put("installed", true).put("mode", mode.name).put("verify", verify)
    }

    private fun quote(value: String): String {
        require(value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) { "invalid package" }
        return value
    }
}
