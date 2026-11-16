package com.nudge.edge.service

import android.app.Activity
import android.os.Build
import android.os.Bundle
import com.nudge.edge.model.HardwareTrigger

class NudgePowerTriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NudgeAccessibilityService.instance?.triggerPowerPopForHold()
        NudgeAccessibilityService.onHardwareTriggerFired(HardwareTrigger.LONG_PRESS_POWER)
        finishAndRemoveTask()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
