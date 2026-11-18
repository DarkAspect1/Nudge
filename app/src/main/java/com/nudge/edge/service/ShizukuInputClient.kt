package com.nudge.edge.service

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.nudge.edge.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList

typealias ShizukuInputEventListener = (deviceName: String, scanCode: Int, value: Int, timestampMs: Long) -> Unit

object ShizukuInputClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var inputService: IShizukuInputService? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var wantMonitoring = false

    private val listeners = CopyOnWriteArrayList<ShizukuInputEventListener>()

    private val callback = object : IShizukuInputCallback.Stub() {
        override fun onInputEvent(deviceName: String?, scanCode: Int, value: Int, timestampMs: Long) {
            val dev = deviceName ?: ""
            for (listener in listeners) {
                runCatching { listener(dev, scanCode, value, timestampMs) }
            }
        }
    }

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, ShizukuInputService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shizuku_input")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            inputService = IShizukuInputService.Stub.asInterface(service)
            _isConnected.value = true
            runCatching {
                inputService?.registerCallback(callback)
                if (wantMonitoring) {
                    inputService?.startMonitoring()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            inputService = null
            _isConnected.value = false
        }
    }

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        scope.launch {
            ShizukuManager.status.collect { status ->
                if (status == ShizukuStatus.CONNECTED_AND_ACTIVE) {
                    bindService()
                } else {
                    unbindService()
                }
            }
        }
    }

    private fun bindService() {
        if (_isConnected.value || inputService != null) return
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return

        runCatching {
            Shizuku.bindUserService(userServiceArgs, connection)
        }
    }

    private fun unbindService() {
        if (!_isConnected.value && inputService == null) return
        runCatching {
            inputService?.unregisterCallback(callback)
            inputService?.stopMonitoring()
            Shizuku.unbindUserService(userServiceArgs, connection, true)
        }
        inputService = null
        _isConnected.value = false
    }

    fun addListener(listener: ShizukuInputEventListener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: ShizukuInputEventListener) {
        listeners.remove(listener)
    }

    fun startMonitoring() {
        wantMonitoring = true
        if (_isConnected.value && inputService != null) {
            runCatching { inputService?.startMonitoring() }
        } else {
            bindService()
        }
    }

    fun stopMonitoring() {
        wantMonitoring = false
        runCatching { inputService?.stopMonitoring() }
    }

    fun getKeyLabel(deviceName: String?, scanCode: Int): String {
        return runCatching {
            inputService?.getKeyLabel(deviceName, scanCode)
        }.getOrNull() ?: "Key 0x${scanCode.toString(16).uppercase().padStart(4, '0')}"
    }

    fun setDndEnabled(enabled: Boolean): Boolean {
        return runCatching {
            inputService?.setDndEnabled(enabled) ?: false
        }.getOrDefault(false)
    }
}
