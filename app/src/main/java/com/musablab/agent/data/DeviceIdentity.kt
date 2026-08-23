package com.musablab.agent.data

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.util.UUID

object DeviceIdentity {
    private const val PREFS = "musablab_device"
    private const val KEY_ID = "device_id"

    fun id(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_ID, it).apply()
        }
    }

    fun snapshot(context: Context): JSONObject = JSONObject()
        .put("deviceId", id(context))
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("device", Build.DEVICE)
        .put("sdk", Build.VERSION.SDK_INT)
        .put("release", Build.VERSION.RELEASE)
        .put("abis", Build.SUPPORTED_ABIS.joinToString(","))
}
