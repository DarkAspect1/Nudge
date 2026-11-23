package com.nudge.edge.model

import org.json.JSONArray
import org.json.JSONObject

enum class PersistentNudgeAction(val id: String, val displayName: String) {
    NUDGE_MENU("nudge_menu", "Open Nudge Menu"),
    VOLUME_SLIDER_HUD("volume_slider_hud", "Show Volume Slider HUD"),
    AUDIO_MIXER("audio_mixer", "Show Audio Mixer"),
    VOLUME_UP("volume_up", "Volume Up"),
    VOLUME_DOWN("volume_down", "Volume Down"),
    DND_PANEL("dnd_panel", "Show DND / Silent Panel"),
    TOGGLE_DND("toggle_dnd", "Toggle DND On/Off"),
    TOGGLE_SILENT_RING("toggle_silent_ring", "Toggle Silent / Ring"),
    ACTIONS_PANEL("actions_panel", "Show Actions Panel"),
    NOTIFICATIONS("notifications", "Open Notifications"),
    QUICK_PANEL("quick_panel", "Open Quick Panel"),
    TAKE_SCREENSHOT("take_screenshot", "Take Screenshot"),
    LOCK_SCREEN("lock_screen", "Lock Screen"),
    POWER_MENU("power_menu", "Power Menu"),
    BACK("back", "Back"),
    HOME("home", "Home"),
    RECENTS("recents", "Recent Apps"),
    VOICE_ASSISTANT("voice_assistant", "Voice Assistant"),
    MEDIA_PLAY_PAUSE("media_play_pause", "Play / Pause Media"),
    MEDIA_NEXT("media_next", "Next Track"),
    MEDIA_PREVIOUS("media_previous", "Previous Track"),
    TOGGLE_FLASHLIGHT("toggle_flashlight", "Toggle Flashlight"),
    TOGGLE_AUTO_ROTATE("toggle_auto_rotate", "Toggle Auto-Rotate"),
    OPEN_CAMERA("open_camera", "Open Camera"),
    OPEN_APP("open_app", "Open App(s)"),
    SHOW_SYSTEM_VOLUME("show_system_volume", "Show System Volume Slider");

    companion object {
        fun fromId(id: String): PersistentNudgeAction {
            return when (id) {
                "quick_settings" -> QUICK_PANEL
                "mute_unmute" -> VOLUME_SLIDER_HUD
                "one_handed_mode" -> VOLUME_SLIDER_HUD
                "split_screen" -> VOLUME_SLIDER_HUD
                "app_switch" -> VOLUME_SLIDER_HUD
                else -> entries.firstOrNull { it.id == id } ?: VOLUME_SLIDER_HUD
            }
        }
    }
}

data class PersistentNudge(
    val id: String,
    val enabled: Boolean = true,
    val edge: ScreenEdge = ScreenEdge.RIGHT,
    val yFraction: Float = 0.50f,
    val displayOnLandscape: Boolean = false,
    val action: PersistentNudgeAction = PersistentNudgeAction.VOLUME_SLIDER_HUD,
    val targetPackage: String = "",
    val targetAppName: String = "",
    val selectedApps: List<NudgeMenuApp> = emptyList(),
    val menuWidthDp: Float = 50f,
    val menuHeightDp: Float = 300f,
    val menuYFraction: Float = 0.50f
)

val DEFAULT_PERSISTENT_NUDGES = listOf(
    PersistentNudge(
        id = "nudge_1",
        enabled = true,
        edge = ScreenEdge.RIGHT,
        yFraction = 0.50f,
        displayOnLandscape = false,
        action = PersistentNudgeAction.VOLUME_SLIDER_HUD,
        targetPackage = "",
        targetAppName = "",
        selectedApps = emptyList(),
        menuWidthDp = 50f,
        menuHeightDp = 300f,
        menuYFraction = 0.50f
    )
)

fun serializePersistentNudges(nudges: List<PersistentNudge>): String {
    val arr = JSONArray()
    for (n in nudges) {
        val obj = JSONObject()
        obj.put("id", n.id)
        obj.put("enabled", n.enabled)
        obj.put("edge", n.edge.name)
        obj.put("yFraction", n.yFraction.toDouble())
        obj.put("displayOnLandscape", n.displayOnLandscape)
        obj.put("action", n.action.id)
        obj.put("targetPackage", n.targetPackage)
        obj.put("targetAppName", n.targetAppName)
        obj.put("selectedApps", serializeNudgeMenuApps(n.selectedApps))
        obj.put("menuWidthDp", n.menuWidthDp.toDouble())
        obj.put("menuHeightDp", n.menuHeightDp.toDouble())
        obj.put("menuYFraction", n.menuYFraction.toDouble())
        arr.put(obj)
    }
    return arr.toString()
}

fun deserializePersistentNudges(json: String?): List<PersistentNudge> {
    if (json.isNullOrBlank()) return DEFAULT_PERSISTENT_NUDGES
    return runCatching {
        val arr = JSONArray(json)
        val list = mutableListOf<PersistentNudge>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val edgeName = obj.optString("edge", "RIGHT")
            val edge = if (edgeName == "LEFT") ScreenEdge.LEFT else ScreenEdge.RIGHT
            val targetPkg = obj.optString("targetPackage", "")
            val targetName = obj.optString("targetAppName", "")
            val apps = deserializeNudgeMenuApps(obj.optString("selectedApps", "")).ifEmpty {
                if (targetPkg.isNotBlank()) listOf(NudgeMenuApp(targetPkg, targetName)) else emptyList()
            }
            list.add(
                PersistentNudge(
                    id = obj.optString("id", "nudge_${i + 1}"),
                    enabled = obj.optBoolean("enabled", true),
                    edge = edge,
                    yFraction = obj.optDouble("yFraction", 0.50).toFloat().coerceIn(0.05f, 0.95f),
                    displayOnLandscape = obj.optBoolean("displayOnLandscape", false),
                    action = PersistentNudgeAction.fromId(obj.optString("action", "volume_slider_hud")),
                    targetPackage = targetPkg,
                    targetAppName = targetName,
                    selectedApps = apps,
                    menuWidthDp = obj.optDouble("menuWidthDp", 50.0).toFloat().coerceIn(20f, 100f),
                    menuHeightDp = obj.optDouble("menuHeightDp", 300.0).toFloat().coerceIn(180f, 500f),
                    menuYFraction = obj.optDouble("menuYFraction", 0.50).toFloat().coerceIn(0.05f, 0.95f)
                )
            )
        }
        if (list.isNotEmpty()) list else DEFAULT_PERSISTENT_NUDGES
    }.getOrDefault(DEFAULT_PERSISTENT_NUDGES)
}

data class NudgeMenuApp(
    val packageName: String,
    val appName: String
)

fun serializeNudgeMenuApps(apps: List<NudgeMenuApp>): String {
    val arr = JSONArray()
    for (app in apps) {
        val obj = JSONObject()
        obj.put("packageName", app.packageName)
        obj.put("appName", app.appName)
        arr.put(obj)
    }
    return arr.toString()
}

fun deserializeNudgeMenuApps(json: String?): List<NudgeMenuApp> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        val list = mutableListOf<NudgeMenuApp>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val pkg = obj.optString("packageName", "")
            val name = obj.optString("appName", "")
            if (pkg.isNotBlank()) {
                list.add(NudgeMenuApp(packageName = pkg, appName = name))
            }
        }
        list
    }.getOrDefault(emptyList())
}
