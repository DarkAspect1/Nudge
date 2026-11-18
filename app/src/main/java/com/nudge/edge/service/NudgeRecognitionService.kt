package com.nudge.edge.service

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

class NudgeRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        runCatching {
            listener?.error(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    override fun onCancel(listener: Callback?) {}

    override fun onStopListening(listener: Callback?) {}
}
