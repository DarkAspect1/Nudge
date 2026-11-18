package com.nudge.edge.service

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.nudge.edge.model.HardwareTrigger

class NudgeVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return NudgeVoiceInteractionSession(this)
    }
}

class NudgeVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        NudgeAccessibilityService.instance?.triggerPowerPopForHold()
        NudgeAccessibilityService.onHardwareTriggerFired(HardwareTrigger.LONG_PRESS_POWER)
        finish()
    }
}
