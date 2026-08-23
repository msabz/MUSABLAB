package com.musablab.agent.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

object ShizukuController {
    enum class State { UNAVAILABLE, PERMISSION_REQUIRED, CONNECTING, READY, DEAD }

    private lateinit var appContext: Context
    private val _state = MutableStateFlow(State.UNAVAILABLE)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var remote: IMusabLabService? = null
    private const val REQUEST_CODE = 4107

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) bind()
        else refreshState()
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshState(); if (hasPermission()) bind() }
    private val binderDead = Shizuku.OnBinderDeadListener { remote = null; _state.value = State.DEAD }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = IMusabLabService.Stub.asInterface(service)
            _state.value = if (remote != null) State.READY else State.UNAVAILABLE
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            _state.value = State.DEAD
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshState()
    }

    fun refreshState() {
        if (!Shizuku.pingBinder()) {
            _state.value = State.UNAVAILABLE
            return
        }
        if (!hasPermission()) {
            _state.value = State.PERMISSION_REQUIRED
            return
        }
        if (remote == null) bind() else _state.value = State.READY
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            _state.value = State.UNAVAILABLE
            return
        }
        if (hasPermission()) bind() else Shizuku.requestPermission(REQUEST_CODE)
    }

    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    @Synchronized
    fun bind() {
        if (!::appContext.isInitialized || !hasPermission() || remote != null) return
        _state.value = State.CONNECTING
        val args = Shizuku.UserServiceArgs(ComponentName(appContext, MusabLabUserService::class.java))
            .daemon(true)
            .processNameSuffix("lab")
            .tag("musablab-native-agent")
            .version(1)
            .debuggable(false)
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure { _state.value = State.UNAVAILABLE }
    }

    fun serviceOrNull(): IMusabLabService? = remote
    fun requireService(): IMusabLabService = remote ?: throw IllegalStateException("Shizuku UserService is not ready")
}
