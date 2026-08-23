package com.musablab.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musablab.agent.BuildConfig
import com.musablab.agent.data.DeviceIdentity
import com.musablab.agent.github.GitHubApi
import com.musablab.agent.github.GitHubOAuthClient
import com.musablab.agent.security.SecureTokenStore
import com.musablab.agent.shizuku.ShizukuController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class UiState(
    val deviceId: String = "",
    val shizuku: ShizukuController.State = ShizukuController.State.UNAVAILABLE,
    val githubLogin: String? = null,
    val oauthCode: String? = null,
    val oauthUrl: String? = null,
    val message: String = "",
    val oauthConfigured: Boolean = BuildConfig.GITHUB_CLIENT_ID.isNotBlank()
)

class MusabLabViewModel(app: Application) : AndroidViewModel(app) {
    private val tokenStore = SecureTokenStore(app)
    var state by androidx.compose.runtime.mutableStateOf(UiState(deviceId = DeviceIdentity.id(app)))
        private set

    init {
        viewModelScope.launch {
            ShizukuController.state.collectLatest { s -> state = state.copy(shizuku = s) }
        }
        refresh()
    }

    fun requestShizuku() = ShizukuController.requestPermission()

    fun refresh() {
        ShizukuController.refreshState()
        val token = tokenStore.getToken()
        if (token != null) viewModelScope.launch {
            runCatching { GitHubApi(token).getUserLogin() }
                .onSuccess { state = state.copy(githubLogin = it, message = "READY") }
                .onFailure { state = state.copy(message = it.message ?: "GitHub check failed") }
        }
    }

    fun startGitHubDeviceFlow() {
        viewModelScope.launch {
            runCatching {
                val oauth = GitHubOAuthClient()
                val flow = oauth.startDeviceFlow()
                state = state.copy(oauthCode = flow.userCode, oauthUrl = flow.verificationUri, message = "Authorize in GitHub")
                val token = oauth.awaitToken(flow)
                tokenStore.putToken(token)
                val login = GitHubApi(token).getUserLogin()
                state = state.copy(githubLogin = login, oauthCode = null, oauthUrl = null, message = "GitHub connected")
            }.onFailure { state = state.copy(message = it.message ?: "GitHub authorization failed") }
        }
    }
}
