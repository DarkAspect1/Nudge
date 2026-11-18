package com.nudge.edge.model

enum class TriggerSource {
    ACCESSIBILITY,
    SHIZUKU_GETEVENT
}

data class HardwareKeyTrigger(
    val source: TriggerSource,
    val androidKeyCode: Int?,
    val scanCode: Int?,
    val linuxKeyName: String?,
    val deviceName: String?
)
