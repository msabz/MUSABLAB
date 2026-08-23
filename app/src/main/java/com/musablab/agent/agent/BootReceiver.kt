package com.musablab.agent.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.musablab.agent.security.SecureTokenStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (SecureTokenStore(context).getToken() != null) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, LabAgentService::class.java)) }
        }
    }
}
