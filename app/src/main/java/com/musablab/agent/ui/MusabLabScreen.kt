package com.musablab.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MusabLabScreen(
    state: UiState,
    onRequestShizuku: () -> Unit,
    onConnectGitHub: () -> Unit,
    onOpenVerification: (String) -> Unit,
    onStartAgent: () -> Unit,
    onRefresh: () -> Unit
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("MUSABLAB", style = MaterialTheme.typography.headlineLarge)
                Text("Native physical Android CI agent")
                StatusCard("Device", state.deviceId)
                StatusCard("Shizuku", state.shizuku.name)
                StatusCard("GitHub", state.githubLogin ?: if (state.oauthConfigured) "Not connected" else "OAuth client id not configured")

                if (state.shizuku.name != "READY") {
                    Button(onClick = onRequestShizuku, modifier = Modifier.fillMaxWidth()) { Text("Grant Shizuku") }
                }
                if (state.githubLogin == null) {
                    Button(onClick = onConnectGitHub, enabled = state.oauthConfigured, modifier = Modifier.fillMaxWidth()) { Text("Connect GitHub") }
                }
                if (state.oauthCode != null && state.oauthUrl != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("GitHub code", style = MaterialTheme.typography.labelLarge)
                            Text(state.oauthCode, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { onOpenVerification(state.oauthUrl) }) { Text("Open GitHub") }
                        }
                    }
                }
                Button(
                    onClick = onStartAgent,
                    enabled = state.shizuku.name == "READY" && state.githubLogin != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start Agent") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                }
                if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value.take(44), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
