package com.musablab.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musablab.agent.agent.LabAgentService
import com.musablab.agent.ui.MusabLabScreen
import com.musablab.agent.ui.MusabLabViewModel

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val vm: MusabLabViewModel = viewModel()
            MusabLabScreen(
                state = vm.state,
                onRequestShizuku = vm::requestShizuku,
                onConnectGitHub = vm::startGitHubDeviceFlow,
                onOpenVerification = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                onStartAgent = {
                    ContextCompat.startForegroundService(this, Intent(this, LabAgentService::class.java))
                    vm.refresh()
                },
                onRefresh = vm::refresh
            )
        }
    }
}
