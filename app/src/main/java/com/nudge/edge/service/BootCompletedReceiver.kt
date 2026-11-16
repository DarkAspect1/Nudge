package com.nudge.edge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nudge.edge.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {

            NudgeKeepAliveService.start(context)

            CoroutineScope(Dispatchers.Default).launch {
                val repo = SettingsRepository.getInstance(context)
                val settings = repo.settings.value
                if (settings.isAutoKeepAliveViaShizuku && NudgeAccessibilityService.instance == null) {
                    ShizukuManager.enableAccessibility(context)
                }
            }
        }
    }
}
