package com.nudge.edge.data

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.nudge.edge.service.ShizukuInputClient
import com.nudge.edge.service.ShizukuManager
import com.nudge.edge.service.ShizukuStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class DndController(
    private val context: Context,
    private val notificationManager: NotificationManager
) {
    companion object {
        fun isDndActive(notificationManager: NotificationManager, context: Context? = null): Boolean {
            val filter = notificationManager.currentInterruptionFilter
            if (filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                return filter != NotificationManager.INTERRUPTION_FILTER_ALL
            }
            if (context != null) {
                val zen = runCatching {
                    Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
                }.getOrDefault(0)
                if (zen != 0) return true
            }
            return false
        }
    }

    suspend fun toggleDnd(useShizukuForDnd: Boolean): Boolean = withContext(Dispatchers.IO) {
        val enableDnd = !isDndActive(notificationManager, context)
        setDndState(enableDnd, useShizukuForDnd)
    }

    suspend fun setDndState(enableDnd: Boolean, useShizukuForDnd: Boolean): Boolean = withContext(Dispatchers.IO) {
        val isShizukuReady = (ShizukuManager.status.value == ShizukuStatus.CONNECTED_AND_ACTIVE || ShizukuInputClient.isConnected.value)
        if (useShizukuForDnd && isShizukuReady) {
            val shizukuSuccess = executeShizukuDnd(enableDnd)
            if (shizukuSuccess) {
                return@withContext true
            }
        }
        executeNormalDnd(enableDnd)
    }

    private suspend fun executeShizukuDnd(enableDnd: Boolean): Boolean {
        val isShizukuReady = (ShizukuManager.status.value == ShizukuStatus.CONNECTED_AND_ACTIVE || ShizukuInputClient.isConnected.value)
        if (!isShizukuReady) {
            return executeNormalDnd(enableDnd)
        }
        var success = false
        if (ShizukuInputClient.isConnected.value) {
            success = ShizukuInputClient.setDndEnabled(enableDnd)
        }
        if (!success) {
            val arg = if (enableDnd) "priority" else "off"
            val res = runCatching {
                ShizukuManager.runCommand("cmd notification set_dnd $arg")
                ShizukuManager.runCommand("settings put global zen_mode ${if (enableDnd) "1" else "0"}")
            }
            if (res.isSuccess) {
                success = true
            }
        }

        if (success) {
            if (!enableDnd) {
                if (isDndActive(notificationManager, context) && notificationManager.isNotificationPolicyAccessGranted) {
                    runCatching {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                }
            }
            return true
        }

        return executeNormalDnd(enableDnd)
    }

    private suspend fun executeNormalDnd(enableDnd: Boolean): Boolean {
        val hasPolicyAccess = notificationManager.isNotificationPolicyAccessGranted
        if (!hasPolicyAccess) {
            val isShizukuReady = (ShizukuManager.status.value == ShizukuStatus.CONNECTED_AND_ACTIVE || ShizukuInputClient.isConnected.value)
            if (isShizukuReady) {
                return executeShizukuDnd(enableDnd)
            }
            runCatching {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }.onFailure {
                runCatching {
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                }
            }
            return false
        }

        val targetFilter = if (enableDnd) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }

        val result = runCatching {
            notificationManager.setInterruptionFilter(targetFilter)
            true
        }.getOrDefault(false)

        return result
    }

    private fun showToastOnMain(message: String) {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
