package com.nudge.edge.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

class SystemVolumePanelSuppressor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastDismissMs = AtomicLong(0L)

    @Volatile
    private var statusBarService: Any? = null

    @Volatile
    private var collapsePanelsMethod: Method? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
        }
        refresh()
    }

    fun refresh() {
        if (!shizukuReady()) {
            statusBarService = null
            collapsePanelsMethod = null
            return
        }
        runCatching {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass
                .getDeclaredMethod("getService", String::class.java)
                .apply { isAccessible = true }
            val rawBinder = getServiceMethod.invoke(null, "statusbar") as? IBinder ?: return
            val wrappedBinder = ShizukuBinderWrapper(rawBinder)
            val stubClass = Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub")
            val asInterfaceMethod = stubClass
                .getDeclaredMethod("asInterface", IBinder::class.java)
                .apply { isAccessible = true }
            val service = asInterfaceMethod.invoke(null, wrappedBinder) ?: return
            statusBarService = service
            collapsePanelsMethod = runCatching {
                service.javaClass.methods.firstOrNull {
                    it.name == "collapsePanels" && it.parameterTypes.isEmpty()
                }?.apply { isAccessible = true }
            }.getOrNull()
        }
    }

    fun dismissIfNeeded(minIntervalMs: Long = 120L) {
        val now = SystemClock.uptimeMillis()
        while (true) {
            val last = lastDismissMs.get()
            if (now - last < minIntervalMs) return
            if (lastDismissMs.compareAndSet(last, now)) break
        }

        scope.launch {
            var collapsed = false
            val service = statusBarService
            val method = collapsePanelsMethod
            if (service != null && method != null) {
                collapsed = runCatching { method.invoke(service); true }.getOrDefault(false)
            }
            if (!collapsed) {
                ShizukuManager.runCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
            }
        }
    }

    private fun shizukuReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
}
