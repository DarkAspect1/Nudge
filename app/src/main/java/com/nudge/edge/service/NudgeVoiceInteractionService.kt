package com.nudge.edge.service

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession

class NudgeVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        setDisabledShowContext(
            VoiceInteractionSession.SHOW_WITH_ASSIST or
                VoiceInteractionSession.SHOW_WITH_SCREENSHOT
        )
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        super.onLaunchVoiceAssistFromKeyguard()
        runCatching {
            showSession(Bundle(), 0)
        }
    }
}
