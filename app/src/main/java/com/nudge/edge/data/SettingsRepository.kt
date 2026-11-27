package com.nudge.edge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nudge.edge.model.BezelEdge
import com.nudge.edge.model.CustomMode
import com.nudge.edge.model.DndModeType
import com.nudge.edge.model.EdgePopStyle
import com.nudge.edge.model.EdgeSide
import com.nudge.edge.model.HardwareKeyTrigger
import com.nudge.edge.model.HardwareTrigger
import com.nudge.edge.model.TriggerSource
import com.nudge.edge.model.HudTriggerMode
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.PreviewTarget
import com.nudge.edge.model.ScreenEdge
import com.nudge.edge.model.PersistentNudge
import com.nudge.edge.model.deserializePersistentNudges
import com.nudge.edge.model.serializePersistentNudges
import com.nudge.edge.model.NudgeMenuApp
import com.nudge.edge.model.deserializeNudgeMenuApps
import com.nudge.edge.model.serializeNudgeMenuApps
import com.nudge.edge.model.deserializeCustomModes
import com.nudge.edge.model.serializeCustomModes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val Context.nudgeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nudge_preferences"
)

class SettingsRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val POP_EDGE_SIDE = stringPreferencesKey("pop_edge_side")
        val VOLUME_UP_Y = floatPreferencesKey("volume_up_y")
        val VOLUME_DOWN_Y = floatPreferencesKey("volume_down_y")
        val POP_WIDTH_DP = floatPreferencesKey("pop_width_dp")
        val POP_HEIGHT_DP = floatPreferencesKey("pop_height_dp")
        val POP_AUTO_HIDE_MS = longPreferencesKey("pop_auto_hide_ms")
        val ENABLE_EDGE_POP_LANDSCAPE = booleanPreferencesKey("enable_edge_pop_landscape")
        val ENABLE_POWER_POP = booleanPreferencesKey("enable_power_pop")
        val POWER_BUTTON_Y = floatPreferencesKey("power_button_y")
        val POWER_POP_WIDTH_DP = floatPreferencesKey("power_pop_width_dp")
        val POWER_POP_HEIGHT_DP = floatPreferencesKey("power_pop_height_dp")
        val EDGE_POP_STYLE = stringPreferencesKey("edge_pop_style")
        val EDGE_POP_FLAT_TOP_GLOW_ENABLED = booleanPreferencesKey("edge_pop_flat_top_glow_enabled")
        val ENABLE_VOLUME_SLIDER_HUD = booleanPreferencesKey("enable_volume_slider_hud")
        val SHOW_SYSTEM_PANEL_WHEN_HUD_OFF = booleanPreferencesKey("show_system_panel_when_hud_off")
        val HUD_SIDE_SYNCED = booleanPreferencesKey("hud_side_synced")
        val HUD_EDGE_SIDE = stringPreferencesKey("hud_edge_side")
        val HUD_Y = floatPreferencesKey("hud_y")
        val HUD_HEIGHT_DP = floatPreferencesKey("hud_height_dp")
        val HUD_WIDTH_DP = floatPreferencesKey("hud_width_dp")
        val HUD_CORNER_RADIUS_DP = floatPreferencesKey("hud_corner_radius_dp")
        val HUD_AUTO_HIDE_MS = longPreferencesKey("hud_auto_hide_ms")
        val HUD_TRIGGER_MODE = stringPreferencesKey("hud_trigger_mode")
        val ENABLE_SLIDER_HUD_LANDSCAPE = booleanPreferencesKey("enable_slider_hud_landscape")
        val LANDSCAPE_HUD_EDGE_SIDE = stringPreferencesKey("landscape_hud_edge_side")
        val LANDSCAPE_HUD_Y = floatPreferencesKey("landscape_hud_y")
        val SHOW_SYSTEM_PANEL_LANDSCAPE = booleanPreferencesKey("show_system_panel_landscape")
        val FLOATING_MODE = booleanPreferencesKey("floating_mode")
        val HUD_EDGE_INSET_DP = floatPreferencesKey("hud_edge_inset_dp")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val LIGHT_MODE = booleanPreferencesKey("light_mode")
        val LAST_PROMPTED_ACCESSIBILITY_VERSION = intPreferencesKey("last_prompted_accessibility_version")

        val ENABLE_DND_TOGGLE = booleanPreferencesKey("enable_dnd_toggle")
        val DND_MODE_TYPE = stringPreferencesKey("dnd_mode_type")
        val DND_INCLUDE_SOUND = booleanPreferencesKey("dnd_include_sound")
        val DND_INCLUDE_VIBRATE = booleanPreferencesKey("dnd_include_vibrate")
        val DND_INCLUDE_SILENT = booleanPreferencesKey("dnd_include_silent")
        val DND_TRIGGER = stringPreferencesKey("dnd_trigger")
        val DND_DOUBLE_TAP_TIMEOUT_MS = longPreferencesKey("dnd_double_tap_timeout_ms")
        val DND_TIMEOUT_MS = longPreferencesKey("dnd_timeout_ms")
        val DND_PORTRAIT_EDGE = stringPreferencesKey("dnd_portrait_edge")
        val DND_PORTRAIT_OFFSET = floatPreferencesKey("dnd_portrait_offset")
        val DND_ENABLE_LANDSCAPE = booleanPreferencesKey("dnd_enable_landscape")
        val DND_LANDSCAPE_EDGE = stringPreferencesKey("dnd_landscape_edge")
        val DND_LANDSCAPE_OFFSET = floatPreferencesKey("dnd_landscape_offset")
        val DND_THICKNESS_DP = floatPreferencesKey("dnd_thickness_dp")
        val DND_LENGTH_DP = floatPreferencesKey("dnd_length_dp")
        val DND_EDGE_SIDE = stringPreferencesKey("dnd_edge_side")
        val DND_Y = floatPreferencesKey("dnd_y")
        val DND_WIDTH_DP = floatPreferencesKey("dnd_width_dp")
        val DND_HEIGHT_DP = floatPreferencesKey("dnd_height_dp")

        val IS_EDGE_POP_MASTER_ENABLED = booleanPreferencesKey("is_edge_pop_master_enabled")

        val ENABLE_ACTIONS_PANEL = booleanPreferencesKey("enable_actions_panel")
        val ENABLE_MODES_CAROUSEL = booleanPreferencesKey("enable_modes_carousel")
        val MODES_TRIGGER = stringPreferencesKey("modes_trigger")
        val MODES_DOUBLE_TAP_TIMEOUT_MS = longPreferencesKey("modes_double_tap_timeout_ms")
        val SIMULTANEOUS_PRESS_TIMEOUT_MS = longPreferencesKey("simultaneous_press_timeout_ms")
        val ENABLE_MODES_NOTIFICATION_BRIDGE = booleanPreferencesKey("enable_modes_notification_bridge")
        val ENABLE_MODES_BUILT_IN_PRESETS = booleanPreferencesKey("enable_modes_built_in_presets")
        val MODES_TIMEOUT_MS = longPreferencesKey("modes_timeout_ms")
        val MODES_PORTRAIT_EDGE = stringPreferencesKey("modes_portrait_edge")
        val MODES_PORTRAIT_OFFSET = floatPreferencesKey("modes_portrait_offset")
        val MODES_ENABLE_LANDSCAPE = booleanPreferencesKey("modes_enable_landscape")
        val MODES_LANDSCAPE_EDGE = stringPreferencesKey("modes_landscape_edge")
        val MODES_LANDSCAPE_OFFSET = floatPreferencesKey("modes_landscape_offset")
        val MODES_THICKNESS_DP = floatPreferencesKey("modes_thickness_dp")
        val MODES_LENGTH_DP = floatPreferencesKey("modes_length_dp")
        val ACTIONS_ICON_SPACING_DP = floatPreferencesKey("actions_icon_spacing_dp")
        val MODES_EDGE_SIDE = stringPreferencesKey("modes_edge_side")
        val MODES_Y = floatPreferencesKey("modes_y")
        val MODES_WIDTH_DP = floatPreferencesKey("modes_width_dp")
        val MODES_HEIGHT_DP = floatPreferencesKey("modes_height_dp")
        val CUSTOM_MODES_JSON = stringPreferencesKey("custom_modes_json")
        val POWER_ASSISTANT_CONFIGURED = booleanPreferencesKey("power_assistant_configured")
        val ENABLE_SHIZUKU_POWER_HOLD_FALLBACK = booleanPreferencesKey("enable_shizuku_power_hold_fallback")

        val CUSTOM_BUTTON_SOURCE = stringPreferencesKey("custom_button_source")
        val CUSTOM_BUTTON_KEY_CODE = intPreferencesKey("custom_button_key_code")
        val CUSTOM_BUTTON_SCAN_CODE = intPreferencesKey("custom_button_scan_code")
        val CUSTOM_BUTTON_LABEL = stringPreferencesKey("custom_button_label")
        val CUSTOM_BUTTON_DEVICE_NAME = stringPreferencesKey("custom_button_device_name")

        val IS_HIDE_ACCESSIBILITY_ENABLED = booleanPreferencesKey("is_hide_accessibility_enabled")
        val CLOAKED_PACKAGES = stringSetPreferencesKey("cloaked_packages")
        val IS_AUTO_KEEP_ALIVE_VIA_SHIZUKU = booleanPreferencesKey("is_auto_keep_alive_via_shizuku")

        val EDGE_POP_HAPTICS_ENABLED = booleanPreferencesKey("edge_pop_haptics_enabled")
        val VOLUME_SLIDER_HAPTICS_ENABLED = booleanPreferencesKey("volume_slider_haptics_enabled")
        val SOUND_ICON_MODE_SWITCH_HAPTICS_ENABLED = booleanPreferencesKey("sound_icon_mode_switch_haptics_enabled")
        val DND_PANEL_HAPTICS_ENABLED = booleanPreferencesKey("dnd_panel_haptics_enabled")
        val ACTIONS_PANEL_HAPTICS_ENABLED = booleanPreferencesKey("actions_panel_haptics_enabled")
        val NUDGE_HAPTICS_ENABLED = booleanPreferencesKey("nudge_haptics_enabled")

        val DND_ICON_SIZE_DP = floatPreferencesKey("dnd_icon_size_dp")
        val ACTIONS_ICON_SIZE_DP = floatPreferencesKey("actions_icon_size_dp")
        val DND_UNFOCUSED_OFFSET_DP = floatPreferencesKey("dnd_unfocused_offset_dp")
        val IS_SHIZUKU_WATCHDOG_ENABLED = booleanPreferencesKey("is_shizuku_watchdog_enabled")
        val IS_ALWAYS_SHOW_CALL_SLIDER_IN_MIXER = booleanPreferencesKey("is_always_show_call_slider_in_mixer")
        val EDGE_POP_FLUIDITY_PERCENT = intPreferencesKey("edge_pop_fluidity_percent")
        val VOLUME_SLIDER_FLUIDITY_PERCENT = intPreferencesKey("volume_slider_fluidity_percent")
        val SHOW_VOLUME_COUNTER = booleanPreferencesKey("show_volume_counter")
        val DND_PANEL_FLUIDITY_PERCENT = intPreferencesKey("dnd_panel_fluidity_percent")
        val ACTIONS_PANEL_FLUIDITY_PERCENT = intPreferencesKey("actions_panel_fluidity_percent")
        val IS_PER_APP_VOLUME_ENABLED = booleanPreferencesKey("is_per_app_volume_enabled")
        val PER_APP_VOLUME_DISPLAY_MODE = stringPreferencesKey("per_app_volume_display_mode")
        val UPDATE_CHECK_FREQUENCY = stringPreferencesKey("update_check_frequency")
        val LAST_UPDATE_CHECK_TIMESTAMP_MS = longPreferencesKey("last_update_check_timestamp_ms")
        val CACHED_LATEST_RELEASE_JSON = stringPreferencesKey("cached_latest_release_json")
        val CACHED_RELEASE_VERSION_TAG = stringPreferencesKey("cached_release_version_tag")
        val CACHED_RELEASE_CHANGELOG = stringPreferencesKey("cached_release_changelog")
        val CACHED_RELEASE_APK_URL = stringPreferencesKey("cached_release_apk_url")
        val CACHED_RELEASE_APK_SIZE = longPreferencesKey("cached_release_apk_size")

        val AUDIO_MIXER_POPUP_WIDTH_DP = floatPreferencesKey("audio_mixer_popup_width_dp")
        val AUDIO_MIXER_POPUP_HEIGHT_DP = floatPreferencesKey("audio_mixer_popup_height_dp")
        val AUDIO_MIXER_PILL_WIDTH_DP = floatPreferencesKey("audio_mixer_pill_width_dp")
        val AUDIO_MIXER_PILL_HEIGHT_DP = floatPreferencesKey("audio_mixer_pill_height_dp")
        val AUDIO_MIXER_PILL_SPACING_DP = floatPreferencesKey("audio_mixer_pill_spacing_dp")
        val IS_AUDIO_MIXER_ATTACHED_TO_SLIDER = booleanPreferencesKey("is_audio_mixer_attached_to_slider")
        val AUDIO_MIXER_X_PERCENT = floatPreferencesKey("audio_mixer_x_percent")
        val AUDIO_MIXER_Y_PERCENT = floatPreferencesKey("audio_mixer_y_percent")
        val AUDIO_MIXER_SCALE_PERCENT = intPreferencesKey("audio_mixer_scale_percent")
        val IS_AUDIO_MIXER_MANUALLY_SIZED = booleanPreferencesKey("is_audio_mixer_manually_sized")
        val SHOW_DND_LABEL = booleanPreferencesKey("show_dnd_label")
        val SHOW_ACTIONS_LABEL = booleanPreferencesKey("show_actions_label")
        val VOLUME_SLIDER_PILL_TRANSPARENCY = intPreferencesKey("volume_slider_pill_transparency")
        val VOLUME_SLIDER_BLUR = floatPreferencesKey("volume_slider_blur")
        val VOLUME_SLIDER_PILL_BLUR_DP = floatPreferencesKey("volume_slider_pill_blur_dp")
        val USE_SHIZUKU_FOR_DND = booleanPreferencesKey("use_shizuku_for_dnd")
        val ENABLE_PERSISTENT_NUDGES = booleanPreferencesKey("enable_persistent_nudges")
        val PERSISTENT_NUDGES_JSON = stringPreferencesKey("persistent_nudges_json")
        val NUDGE_WIDTH_DP = floatPreferencesKey("nudge_width_dp")
        val NUDGE_HEIGHT_DP = floatPreferencesKey("nudge_height_dp")
        val NUDGE_GLOW_ENABLED = booleanPreferencesKey("nudge_glow_enabled")
        val NUDGE_MENU_WIDTH_DP = floatPreferencesKey("nudge_menu_width_dp")
        val NUDGE_MENU_HEIGHT_DP = floatPreferencesKey("nudge_menu_height_dp")
        val NUDGE_MENU_X_FRACTION = floatPreferencesKey("nudge_menu_x_fraction")
        val NUDGE_MENU_Y_FRACTION = floatPreferencesKey("nudge_menu_y_fraction")
        val NUDGE_MENU_EDGE = stringPreferencesKey("nudge_menu_edge")
        val ENABLE_THIN_VOLUME_HUD = booleanPreferencesKey("enable_thin_volume_hud")
        val THIN_HUD_EDGE_INSET_DP = floatPreferencesKey("thin_hud_edge_inset_dp")
        val THIN_HUD_ON_LANDSCAPE_FULLSCREEN = booleanPreferencesKey("thin_hud_on_landscape_fullscreen")
        val ENABLE_HUD_SHOW_DELAY = booleanPreferencesKey("enable_hud_show_delay")
        val HUD_SHOW_DELAY_MS = longPreferencesKey("hud_show_delay_ms")
        val SHOW_THIN_PER_APP_VOLUME_HUD = booleanPreferencesKey("show_thin_per_app_volume_hud")
        val NUDGE_MENU_APPS_JSON = stringPreferencesKey("nudge_menu_apps_json")
    }

    private val _settings = MutableStateFlow(NudgeSettings())
    val settings: StateFlow<NudgeSettings> = _settings.asStateFlow()

    private val _previewTarget = MutableStateFlow(PreviewTarget.NONE)
    val previewTarget: StateFlow<PreviewTarget> = _previewTarget.asStateFlow()

    private val _previewLandscape = MutableStateFlow(false)
    val previewLandscape: StateFlow<Boolean> = _previewLandscape.asStateFlow()

    private val _preferencesLoaded = MutableStateFlow(false)
    val preferencesLoaded: StateFlow<Boolean> = _preferencesLoaded.asStateFlow()

    private val _lastPromptedAccessibilityVersion = MutableStateFlow(0)
    val lastPromptedAccessibilityVersion: StateFlow<Int> = _lastPromptedAccessibilityVersion.asStateFlow()

    private var previewClearJob: Job? = null

    init {
        scope.launch {
            appContext.nudgeDataStore.data
                .distinctUntilChanged()
                .collectLatest { prefs ->
                    _settings.value = mapPreferences(prefs)
                    _lastPromptedAccessibilityVersion.value =
                        prefs[Keys.LAST_PROMPTED_ACCESSIBILITY_VERSION] ?: 0
                    _preferencesLoaded.value = true
                }
        }
    }

    private fun mapPreferences(prefs: Preferences): NudgeSettings {
        val defaults = NudgeSettings()
        return NudgeSettings(
            isEdgePopMasterEnabled = prefs[Keys.IS_EDGE_POP_MASTER_ENABLED] ?: defaults.isEdgePopMasterEnabled,
            popEdgeSide = prefs[Keys.POP_EDGE_SIDE]?.let {
                runCatching { EdgeSide.valueOf(it) }.getOrNull()
            } ?: defaults.popEdgeSide,
            volumeUpYFraction = NudgeSettings.clampFraction(
                prefs[Keys.VOLUME_UP_Y] ?: defaults.volumeUpYFraction
            ),
            volumeDownYFraction = NudgeSettings.clampFraction(
                prefs[Keys.VOLUME_DOWN_Y] ?: defaults.volumeDownYFraction
            ),
            popWidthDp = NudgeSettings.clampPopWidth(
                prefs[Keys.POP_WIDTH_DP] ?: defaults.popWidthDp
            ),
            popHeightDp = NudgeSettings.clampPopHeight(
                prefs[Keys.POP_HEIGHT_DP] ?: defaults.popHeightDp
            ),
            popAutoHideMs = NudgeSettings.clampPopAutoHideMs(
                prefs[Keys.POP_AUTO_HIDE_MS] ?: defaults.popAutoHideMs
            ),
            edgePopFluidityPercent = NudgeSettings.clampFluidityPercent(
                prefs[Keys.EDGE_POP_FLUIDITY_PERCENT] ?: defaults.edgePopFluidityPercent
            ),
            enableEdgePopInLandscape =
                prefs[Keys.ENABLE_EDGE_POP_LANDSCAPE] ?: defaults.enableEdgePopInLandscape,
            enablePowerButtonPop =
                prefs[Keys.ENABLE_POWER_POP] ?: defaults.enablePowerButtonPop,
            powerButtonYFraction = NudgeSettings.clampFraction(
                prefs[Keys.POWER_BUTTON_Y] ?: defaults.powerButtonYFraction
            ),
            powerPopWidthDp = NudgeSettings.clampPopWidth(
                prefs[Keys.POWER_POP_WIDTH_DP] ?: defaults.powerPopWidthDp
            ),
            powerPopHeightDp = NudgeSettings.clampPopHeight(
                prefs[Keys.POWER_POP_HEIGHT_DP] ?: defaults.powerPopHeightDp
            ),
            edgePopStyle = prefs[Keys.EDGE_POP_STYLE]?.let {
                runCatching { EdgePopStyle.valueOf(it) }.getOrNull()
            } ?: defaults.edgePopStyle,
            edgePopFlatTopGlowEnabled = prefs[Keys.EDGE_POP_FLAT_TOP_GLOW_ENABLED] ?: defaults.edgePopFlatTopGlowEnabled,
            enableVolumeSliderHud =
                prefs[Keys.ENABLE_VOLUME_SLIDER_HUD] ?: defaults.enableVolumeSliderHud,
            showSystemPanelWhenHudOff =
                prefs[Keys.SHOW_SYSTEM_PANEL_WHEN_HUD_OFF] ?: defaults.showSystemPanelWhenHudOff,
            isPerAppVolumeEnabled = prefs[Keys.IS_PER_APP_VOLUME_ENABLED] ?: defaults.isPerAppVolumeEnabled,
            perAppVolumeDisplayMode = prefs[Keys.PER_APP_VOLUME_DISPLAY_MODE] ?: defaults.perAppVolumeDisplayMode,
            showThinPerAppVolumeHud = prefs[Keys.SHOW_THIN_PER_APP_VOLUME_HUD] ?: defaults.showThinPerAppVolumeHud,
            updateCheckFrequency = prefs[Keys.UPDATE_CHECK_FREQUENCY] ?: defaults.updateCheckFrequency,
            lastUpdateCheckTimestampMs = prefs[Keys.LAST_UPDATE_CHECK_TIMESTAMP_MS] ?: defaults.lastUpdateCheckTimestampMs,
            cachedLatestReleaseJson = prefs[Keys.CACHED_LATEST_RELEASE_JSON] ?: defaults.cachedLatestReleaseJson,
            cachedReleaseVersionTag = prefs[Keys.CACHED_RELEASE_VERSION_TAG] ?: defaults.cachedReleaseVersionTag,
            cachedReleaseChangelog = prefs[Keys.CACHED_RELEASE_CHANGELOG] ?: defaults.cachedReleaseChangelog,
            cachedReleaseApkUrl = prefs[Keys.CACHED_RELEASE_APK_URL] ?: defaults.cachedReleaseApkUrl,
            cachedReleaseApkSize = prefs[Keys.CACHED_RELEASE_APK_SIZE] ?: defaults.cachedReleaseApkSize,
            volumeSliderFluidityPercent = NudgeSettings.clampFluidityPercent(
                prefs[Keys.VOLUME_SLIDER_FLUIDITY_PERCENT] ?: defaults.volumeSliderFluidityPercent
            ),
            showVolumeCounter = prefs[Keys.SHOW_VOLUME_COUNTER] ?: defaults.showVolumeCounter,
            hudSideSynced = prefs[Keys.HUD_SIDE_SYNCED] ?: defaults.hudSideSynced,
            hudEdgeSide = prefs[Keys.HUD_EDGE_SIDE]?.let {
                runCatching { EdgeSide.valueOf(it) }.getOrNull()
            } ?: defaults.hudEdgeSide,
            hudYFraction = NudgeSettings.clampFraction(
                prefs[Keys.HUD_Y] ?: defaults.hudYFraction
            ),
            hudHeightDp = NudgeSettings.clampHudHeight(
                prefs[Keys.HUD_HEIGHT_DP] ?: defaults.hudHeightDp
            ),
            hudWidthDp = NudgeSettings.clampHudWidth(
                prefs[Keys.HUD_WIDTH_DP] ?: defaults.hudWidthDp
            ),
            hudCornerRadiusDp = NudgeSettings.clampHudCornerRadius(
                prefs[Keys.HUD_CORNER_RADIUS_DP] ?: defaults.hudCornerRadiusDp
            ),
            volumeSliderPillTransparency = prefs[Keys.VOLUME_SLIDER_PILL_TRANSPARENCY]?.coerceIn(0, 100) ?: defaults.volumeSliderPillTransparency,
            volumeSliderBlur = prefs[Keys.VOLUME_SLIDER_BLUR]?.coerceIn(0f, 1f) ?: defaults.volumeSliderBlur,
            volumeSliderPillBlurDp = prefs[Keys.VOLUME_SLIDER_PILL_BLUR_DP]?.coerceIn(0f, 30f) ?: defaults.volumeSliderPillBlurDp,
            hudAutoHideMs = NudgeSettings.clampAutoHideMs(
                prefs[Keys.HUD_AUTO_HIDE_MS] ?: defaults.hudAutoHideMs
            ),
            hudTriggerMode = prefs[Keys.HUD_TRIGGER_MODE]?.let {
                runCatching { HudTriggerMode.valueOf(it) }.getOrNull()
            } ?: defaults.hudTriggerMode,
            enableSliderHudInLandscape =
                prefs[Keys.ENABLE_SLIDER_HUD_LANDSCAPE] ?: defaults.enableSliderHudInLandscape,
            landscapeHudEdgeSide = prefs[Keys.LANDSCAPE_HUD_EDGE_SIDE]?.let {
                runCatching { ScreenEdge.valueOf(it) }.getOrNull()
            } ?: defaults.landscapeHudEdgeSide,
            landscapeHudYFraction = NudgeSettings.clampFraction(
                prefs[Keys.LANDSCAPE_HUD_Y] ?: defaults.landscapeHudYFraction
            ),
            showSystemPanelInLandscape =
                prefs[Keys.SHOW_SYSTEM_PANEL_LANDSCAPE] ?: defaults.showSystemPanelInLandscape,
            floatingMode = prefs[Keys.FLOATING_MODE] ?: defaults.floatingMode,
            hudEdgeInsetDp = NudgeSettings.clampHudEdgeInset(
                prefs[Keys.HUD_EDGE_INSET_DP] ?: defaults.hudEdgeInsetDp
            ),
            enableThinVolumeHud = prefs[Keys.ENABLE_THIN_VOLUME_HUD] ?: defaults.enableThinVolumeHud,
            thinHudEdgeInsetDp = NudgeSettings.clampThinHudEdgeInset(
                prefs[Keys.THIN_HUD_EDGE_INSET_DP] ?: defaults.thinHudEdgeInsetDp
            ),
            thinHudOnLandscapeFullscreen = prefs[Keys.THIN_HUD_ON_LANDSCAPE_FULLSCREEN] ?: defaults.thinHudOnLandscapeFullscreen,
            enableHudShowDelay = prefs[Keys.ENABLE_HUD_SHOW_DELAY] ?: defaults.enableHudShowDelay,
            hudShowDelayMs = NudgeSettings.clampHudShowDelayMs(
                prefs[Keys.HUD_SHOW_DELAY_MS] ?: defaults.hudShowDelayMs
            ),
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: defaults.hapticsEnabled,
            edgePopHapticsEnabled = prefs[Keys.EDGE_POP_HAPTICS_ENABLED] ?: defaults.edgePopHapticsEnabled,
            volumeSliderHapticsEnabled = prefs[Keys.VOLUME_SLIDER_HAPTICS_ENABLED] ?: defaults.volumeSliderHapticsEnabled,
            soundIconModeSwitchHapticsEnabled = prefs[Keys.SOUND_ICON_MODE_SWITCH_HAPTICS_ENABLED] ?: defaults.soundIconModeSwitchHapticsEnabled,
            dndPanelHapticsEnabled = prefs[Keys.DND_PANEL_HAPTICS_ENABLED] ?: defaults.dndPanelHapticsEnabled,
            actionsPanelHapticsEnabled = prefs[Keys.ACTIONS_PANEL_HAPTICS_ENABLED] ?: defaults.actionsPanelHapticsEnabled,
            nudgeHapticsEnabled = prefs[Keys.NUDGE_HAPTICS_ENABLED] ?: defaults.nudgeHapticsEnabled,
            lightMode = prefs[Keys.LIGHT_MODE] ?: defaults.lightMode,

            audioMixerPopupWidthDp = NudgeSettings.clampAudioMixerPopupWidth(
                prefs[Keys.AUDIO_MIXER_POPUP_WIDTH_DP] ?: defaults.audioMixerPopupWidthDp
            ),
            audioMixerPopupHeightDp = NudgeSettings.clampAudioMixerPopupHeight(
                prefs[Keys.AUDIO_MIXER_POPUP_HEIGHT_DP] ?: defaults.audioMixerPopupHeightDp
            ),
            audioMixerPillWidthDp = NudgeSettings.clampAudioMixerPillWidth(
                prefs[Keys.AUDIO_MIXER_PILL_WIDTH_DP] ?: defaults.audioMixerPillWidthDp
            ),
            audioMixerPillHeightDp = NudgeSettings.clampAudioMixerPillHeight(
                prefs[Keys.AUDIO_MIXER_PILL_HEIGHT_DP] ?: defaults.audioMixerPillHeightDp
            ),
            audioMixerPillSpacingDp = NudgeSettings.clampAudioMixerPillSpacing(
                prefs[Keys.AUDIO_MIXER_PILL_SPACING_DP] ?: defaults.audioMixerPillSpacingDp
            ),
            isAudioMixerAttachedToSlider = prefs[Keys.IS_AUDIO_MIXER_ATTACHED_TO_SLIDER] ?: defaults.isAudioMixerAttachedToSlider,
            isAudioMixerManuallySized = prefs[Keys.IS_AUDIO_MIXER_MANUALLY_SIZED] ?: defaults.isAudioMixerManuallySized,
            audioMixerXPercent = NudgeSettings.clampAudioMixerPercent(
                prefs[Keys.AUDIO_MIXER_X_PERCENT] ?: defaults.audioMixerXPercent
            ),
            audioMixerYPercent = NudgeSettings.clampAudioMixerPercent(
                prefs[Keys.AUDIO_MIXER_Y_PERCENT] ?: defaults.audioMixerYPercent
            ),
            audioMixerScalePercent = NudgeSettings.clampAudioMixerScalePercent(
                prefs[Keys.AUDIO_MIXER_SCALE_PERCENT] ?: defaults.audioMixerScalePercent
            ),
            isAlwaysShowCallSliderInMixer = prefs[Keys.IS_ALWAYS_SHOW_CALL_SLIDER_IN_MIXER] ?: defaults.isAlwaysShowCallSliderInMixer,

            enableDndToggle = prefs[Keys.ENABLE_DND_TOGGLE] ?: defaults.enableDndToggle,
            dndModeType = prefs[Keys.DND_MODE_TYPE]?.let {
                runCatching { DndModeType.valueOf(it) }.getOrNull()
            } ?: defaults.dndModeType,
            dndIncludeSound = prefs[Keys.DND_INCLUDE_SOUND] ?: defaults.dndIncludeSound,
            dndIncludeVibrate = prefs[Keys.DND_INCLUDE_VIBRATE] ?: defaults.dndIncludeVibrate,
            dndIncludeSilent = prefs[Keys.DND_INCLUDE_SILENT] ?: defaults.dndIncludeSilent,
            useShizukuForDnd = prefs[Keys.USE_SHIZUKU_FOR_DND] ?: defaults.useShizukuForDnd,
            dndTrigger = prefs[Keys.DND_TRIGGER]?.let {
                runCatching { HardwareTrigger.valueOf(it) }.getOrNull()
            } ?: defaults.dndTrigger,
            dndDoubleTapTimeoutMs = NudgeSettings.clampDoubleTapTimeoutMs(
                prefs[Keys.DND_DOUBLE_TAP_TIMEOUT_MS] ?: defaults.dndDoubleTapTimeoutMs
            ),
            dndTimeoutMs = NudgeSettings.clampDndTimeoutMs(
                prefs[Keys.DND_TIMEOUT_MS] ?: defaults.dndTimeoutMs
            ),
            dndPortraitEdge = prefs[Keys.DND_PORTRAIT_EDGE]?.let {
                runCatching { BezelEdge.valueOf(it) }.getOrNull()
            } ?: prefs[Keys.DND_EDGE_SIDE]?.let {
                if (it == EdgeSide.LEFT.name) BezelEdge.LEFT else BezelEdge.RIGHT
            } ?: defaults.dndPortraitEdge,
            dndPortraitOffsetPercent = NudgeSettings.clampBezelOffsetPercent(
                prefs[Keys.DND_PORTRAIT_OFFSET] ?: prefs[Keys.DND_Y] ?: defaults.dndPortraitOffsetPercent
            ),
            dndEnableLandscape = prefs[Keys.DND_ENABLE_LANDSCAPE] ?: defaults.dndEnableLandscape,
            dndLandscapeEdge = prefs[Keys.DND_LANDSCAPE_EDGE]?.let {
                runCatching { BezelEdge.valueOf(it) }.getOrNull()
            } ?: defaults.dndLandscapeEdge,
            dndLandscapeOffsetPercent = NudgeSettings.clampBezelOffsetPercent(
                prefs[Keys.DND_LANDSCAPE_OFFSET] ?: defaults.dndLandscapeOffsetPercent
            ),
            dndThicknessDp = NudgeSettings.clampBezelThicknessDp(
                prefs[Keys.DND_THICKNESS_DP] ?: prefs[Keys.DND_WIDTH_DP] ?: defaults.dndThicknessDp
            ),
            dndLengthDp = NudgeSettings.clampBezelLengthDp(
                prefs[Keys.DND_LENGTH_DP] ?: prefs[Keys.DND_HEIGHT_DP] ?: defaults.dndLengthDp
            ),
            dndIconSizeDp = NudgeSettings.clampIconSizeDp(
                prefs[Keys.DND_ICON_SIZE_DP] ?: defaults.dndIconSizeDp
            ),
            dndUnfocusedOffsetDp = NudgeSettings.clampDndUnfocusedOffsetDp(
                prefs[Keys.DND_UNFOCUSED_OFFSET_DP] ?: defaults.dndUnfocusedOffsetDp
            ),
            dndPanelFluidityPercent = NudgeSettings.clampFluidityPercent(
                prefs[Keys.DND_PANEL_FLUIDITY_PERCENT] ?: defaults.dndPanelFluidityPercent
            ),
            showDndLabel = prefs[Keys.SHOW_DND_LABEL] ?: defaults.showDndLabel,

            enableActionsPanel = prefs[Keys.ENABLE_ACTIONS_PANEL] ?: prefs[Keys.ENABLE_MODES_CAROUSEL] ?: defaults.enableActionsPanel,
            modesTrigger = prefs[Keys.MODES_TRIGGER]?.let {
                runCatching { HardwareTrigger.valueOf(it) }.getOrNull()
            } ?: defaults.modesTrigger,
            modesDoubleTapTimeoutMs = NudgeSettings.clampDoubleTapTimeoutMs(
                prefs[Keys.MODES_DOUBLE_TAP_TIMEOUT_MS] ?: defaults.modesDoubleTapTimeoutMs
            ),
            simultaneousPressTimeoutMs = NudgeSettings.clampSimultaneousTimeoutMs(
                prefs[Keys.SIMULTANEOUS_PRESS_TIMEOUT_MS] ?: defaults.simultaneousPressTimeoutMs
            ),
            enableModesNotificationBridge = prefs[Keys.ENABLE_MODES_NOTIFICATION_BRIDGE] ?: defaults.enableModesNotificationBridge,
            modesTimeoutMs = NudgeSettings.clampModesTimeoutMs(
                prefs[Keys.MODES_TIMEOUT_MS] ?: defaults.modesTimeoutMs
            ),
            modesPortraitEdge = prefs[Keys.MODES_PORTRAIT_EDGE]?.let {
                runCatching { BezelEdge.valueOf(it) }.getOrNull()
            } ?: prefs[Keys.MODES_EDGE_SIDE]?.let {
                if (it == EdgeSide.LEFT.name) BezelEdge.LEFT else BezelEdge.RIGHT
            } ?: defaults.modesPortraitEdge,
            modesPortraitOffsetPercent = NudgeSettings.clampBezelOffsetPercent(
                prefs[Keys.MODES_PORTRAIT_OFFSET] ?: prefs[Keys.MODES_Y] ?: defaults.modesPortraitOffsetPercent
            ),
            modesEnableLandscape = prefs[Keys.MODES_ENABLE_LANDSCAPE] ?: defaults.modesEnableLandscape,
            modesLandscapeEdge = prefs[Keys.MODES_LANDSCAPE_EDGE]?.let {
                runCatching { BezelEdge.valueOf(it) }.getOrNull()
            } ?: defaults.modesLandscapeEdge,
            modesLandscapeOffsetPercent = NudgeSettings.clampBezelOffsetPercent(
                prefs[Keys.MODES_LANDSCAPE_OFFSET] ?: defaults.modesLandscapeOffsetPercent
            ),
            modesThicknessDp = NudgeSettings.clampBezelThicknessDp(
                prefs[Keys.MODES_THICKNESS_DP] ?: prefs[Keys.MODES_WIDTH_DP] ?: defaults.modesThicknessDp
            ),
            modesLengthDp = NudgeSettings.clampBezelLengthDp(
                prefs[Keys.MODES_LENGTH_DP] ?: prefs[Keys.MODES_HEIGHT_DP] ?: defaults.modesLengthDp
            ),
            actionsIconSizeDp = NudgeSettings.clampIconSizeDp(
                prefs[Keys.ACTIONS_ICON_SIZE_DP] ?: defaults.actionsIconSizeDp
            ),
            actionsIconSpacingDp = NudgeSettings.clampActionsIconSpacingDp(
                prefs[Keys.ACTIONS_ICON_SPACING_DP] ?: defaults.actionsIconSpacingDp
            ),
            actionsPanelFluidityPercent = NudgeSettings.clampFluidityPercent(
                prefs[Keys.ACTIONS_PANEL_FLUIDITY_PERCENT] ?: defaults.actionsPanelFluidityPercent
            ),
            showActionsLabel = prefs[Keys.SHOW_ACTIONS_LABEL] ?: defaults.showActionsLabel,
            customModes = deserializeCustomModes(prefs[Keys.CUSTOM_MODES_JSON]),
            enablePersistentNudges = prefs[Keys.ENABLE_PERSISTENT_NUDGES] ?: defaults.enablePersistentNudges,
            persistentNudges = deserializePersistentNudges(prefs[Keys.PERSISTENT_NUDGES_JSON]),
            nudgeWidthDp = prefs[Keys.NUDGE_WIDTH_DP] ?: defaults.nudgeWidthDp,
            nudgeHeightDp = prefs[Keys.NUDGE_HEIGHT_DP] ?: defaults.nudgeHeightDp,
            nudgeGlowEnabled = prefs[Keys.NUDGE_GLOW_ENABLED] ?: defaults.nudgeGlowEnabled,
            nudgeMenuWidthDp = prefs[Keys.NUDGE_MENU_WIDTH_DP] ?: defaults.nudgeMenuWidthDp,
            nudgeMenuHeightDp = prefs[Keys.NUDGE_MENU_HEIGHT_DP] ?: defaults.nudgeMenuHeightDp,
            nudgeMenuXFraction = prefs[Keys.NUDGE_MENU_X_FRACTION] ?: defaults.nudgeMenuXFraction,
            nudgeMenuYFraction = prefs[Keys.NUDGE_MENU_Y_FRACTION] ?: defaults.nudgeMenuYFraction,
            nudgeMenuEdge = prefs[Keys.NUDGE_MENU_EDGE]?.let { runCatching { ScreenEdge.valueOf(it) }.getOrNull() } ?: defaults.nudgeMenuEdge,
            powerAssistantConfigured = prefs[Keys.POWER_ASSISTANT_CONFIGURED] ?: defaults.powerAssistantConfigured,
            enableShizukuPowerHoldFallback = prefs[Keys.ENABLE_SHIZUKU_POWER_HOLD_FALLBACK] ?: defaults.enableShizukuPowerHoldFallback,
            nudgeMenuApps = deserializeNudgeMenuApps(prefs[Keys.NUDGE_MENU_APPS_JSON]),

            customButtonSource = prefs[Keys.CUSTOM_BUTTON_SOURCE] ?: defaults.customButtonSource,
            customButtonKeyCode = prefs[Keys.CUSTOM_BUTTON_KEY_CODE] ?: defaults.customButtonKeyCode,
            customButtonScanCode = prefs[Keys.CUSTOM_BUTTON_SCAN_CODE] ?: defaults.customButtonScanCode,
            customButtonLabel = prefs[Keys.CUSTOM_BUTTON_LABEL] ?: defaults.customButtonLabel,
            customButtonDeviceName = prefs[Keys.CUSTOM_BUTTON_DEVICE_NAME] ?: defaults.customButtonDeviceName,

            isHideAccessibilityEnabled = prefs[Keys.IS_HIDE_ACCESSIBILITY_ENABLED] ?: defaults.isHideAccessibilityEnabled,
            cloakedPackages = prefs[Keys.CLOAKED_PACKAGES] ?: defaults.cloakedPackages,
            isAutoKeepAliveViaShizuku = prefs[Keys.IS_AUTO_KEEP_ALIVE_VIA_SHIZUKU] ?: defaults.isAutoKeepAliveViaShizuku,
            isShizukuWatchdogEnabled = prefs[Keys.IS_SHIZUKU_WATCHDOG_ENABLED] ?: defaults.isShizukuWatchdogEnabled
        )
    }

    fun startLivePreview(target: PreviewTarget, forceLandscape: Boolean = false) {
        previewClearJob?.cancel()
        _previewLandscape.value = forceLandscape
        _previewTarget.value = target
    }

    fun stopLivePreview(delayMs: Long = 800L) {
        previewClearJob?.cancel()
        previewClearJob = scope.launch {
            delay(delayMs)
            _previewTarget.value = PreviewTarget.NONE
            delay(400L)
            if (_previewTarget.value == PreviewTarget.NONE) {
                _previewLandscape.value = false
            }
        }
    }

    fun requestLivePreview(
        target: PreviewTarget,
        forceLandscape: Boolean = false,
        durationMs: Long = 800L
    ) {
        previewClearJob?.cancel()
        _previewLandscape.value = forceLandscape
        _previewTarget.value = target
        if (target != PreviewTarget.NONE) {
            previewClearJob = scope.launch {
                delay(durationMs)
                _previewTarget.value = PreviewTarget.NONE
                delay(400L)
                if (_previewTarget.value == PreviewTarget.NONE) {
                    _previewLandscape.value = false
                }
            }
        }
    }

    fun requestLivePreview(target: PreviewTarget, durationMs: Long) {
        requestLivePreview(target, forceLandscape = false, durationMs = durationMs)
    }

    fun clearLivePreview() {
        previewClearJob?.cancel()
        _previewTarget.value = PreviewTarget.NONE
        _previewLandscape.value = false
    }

    fun setPowerAssistantConfigured(configured: Boolean) {
        if (_settings.value.powerAssistantConfigured == configured) return
        _settings.value = _settings.value.copy(powerAssistantConfigured = configured)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.POWER_ASSISTANT_CONFIGURED] = configured }
        }
    }

    fun setPopEdgeSide(side: EdgeSide) {
        if (_settings.value.popEdgeSide == side) return
        _settings.value = _settings.value.copy(popEdgeSide = side)
        requestLivePreview(PreviewTarget.POP_BOTH)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.POP_EDGE_SIDE] = side.name }
        }
    }

    fun setVolumeUpYFraction(fraction: Float) {
        val clamped = NudgeSettings.clampFraction(fraction)
        if (_settings.value.volumeUpYFraction == clamped) return
        _settings.value = _settings.value.copy(volumeUpYFraction = clamped)
        requestLivePreview(PreviewTarget.POP_UP)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.VOLUME_UP_Y] = clamped }
        }
    }

    fun setVolumeDownYFraction(fraction: Float) {
        val clamped = NudgeSettings.clampFraction(fraction)
        if (_settings.value.volumeDownYFraction == clamped) return
        _settings.value = _settings.value.copy(volumeDownYFraction = clamped)
        requestLivePreview(PreviewTarget.POP_DOWN)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.VOLUME_DOWN_Y] = clamped }
        }
    }

    fun setPopWidthDp(widthDp: Float) {
        val clamped = NudgeSettings.clampPopWidth(widthDp)
        if (_settings.value.popWidthDp == clamped) return
        _settings.value = _settings.value.copy(popWidthDp = clamped)
        requestLivePreview(PreviewTarget.POP_BOTH)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.POP_WIDTH_DP] = clamped }
        }
    }

    fun setPopHeightDp(heightDp: Float) {
        val clamped = NudgeSettings.clampPopHeight(heightDp)
        if (_settings.value.popHeightDp == clamped) return
        _settings.value = _settings.value.copy(popHeightDp = clamped)
        requestLivePreview(PreviewTarget.POP_BOTH)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.POP_HEIGHT_DP] = clamped }
        }
    }

    fun setPopAutoHideMs(timeoutMs: Long) {
        val clamped = NudgeSettings.clampPopAutoHideMs(timeoutMs)
        if (_settings.value.popAutoHideMs == clamped) return
        _settings.value = _settings.value.copy(popAutoHideMs = clamped)
        requestLivePreview(PreviewTarget.POP_BOTH, durationMs = clamped)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.POP_AUTO_HIDE_MS] = clamped }
        }
    }

    fun setEdgePopStyle(style: EdgePopStyle) {
        if (_settings.value.edgePopStyle == style) return
        _settings.value = _settings.value.copy(edgePopStyle = style)
        requestLivePreview(PreviewTarget.POP_BOTH)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.EDGE_POP_STYLE] = style.name }
        }
    }

    fun setEdgePopFlatTopGlowEnabled(enabled: Boolean) {
        if (_settings.value.edgePopFlatTopGlowEnabled == enabled) return
        _settings.value = _settings.value.copy(edgePopFlatTopGlowEnabled = enabled)
        requestLivePreview(PreviewTarget.POP_BOTH)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.EDGE_POP_FLAT_TOP_GLOW_ENABLED] = enabled }
        }
    }

    fun setEnableEdgePopInLandscape(enabled: Boolean) {
        if (_settings.value.enableEdgePopInLandscape == enabled) return
        _settings.value = _settings.value.copy(enableEdgePopInLandscape = enabled)
        if (enabled) {
            requestLivePreview(PreviewTarget.POP_BOTH)
        } else {
            clearLivePreview()
        }
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ENABLE_EDGE_POP_LANDSCAPE] = enabled }
        }
    }

    fun setEnablePowerButtonPop(enabled: Boolean) {
        if (_settings.value.enablePowerButtonPop == enabled) return
        _settings.value = _settings.value.copy(enablePowerButtonPop = enabled)
        if (enabled) {
            requestLivePreview(PreviewTarget.POP_POWER)
        } else {
            clearLivePreview()
        }
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ENABLE_POWER_POP] = enabled }
        }
    }

    fun setPowerButtonYFraction(fraction: Float) {
        val clamped = NudgeSettings.clampFraction(fraction)
        if (_settings.value.powerButtonYFraction == clamped) return
        _settings.value = _settings.value.copy(powerButtonYFraction = clamped)
        requestLivePreview(PreviewTarget.POP_POWER)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.POWER_BUTTON_Y] = clamped }
        }
    }

    fun setPowerPopWidthDp(widthDp: Float) {
        val clamped = NudgeSettings.clampPopWidth(widthDp)
        if (_settings.value.powerPopWidthDp == clamped) return
        _settings.value = _settings.value.copy(powerPopWidthDp = clamped)
        requestLivePreview(PreviewTarget.POP_POWER)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.POWER_POP_WIDTH_DP] = clamped }
        }
    }

    fun setPowerPopHeightDp(heightDp: Float) {
        val clamped = NudgeSettings.clampPopHeight(heightDp)
        if (_settings.value.powerPopHeightDp == clamped) return
        _settings.value = _settings.value.copy(powerPopHeightDp = clamped)
        requestLivePreview(PreviewTarget.POP_POWER)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.POWER_POP_HEIGHT_DP] = clamped }
        }
    }

    fun setEnableVolumeSliderHud(enabled: Boolean) {
        if (_settings.value.enableVolumeSliderHud == enabled) return
        _settings.value = _settings.value.copy(enableVolumeSliderHud = enabled)
        if (enabled) {
            requestLivePreview(PreviewTarget.HUD)
        } else {
            clearLivePreview()
        }
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ENABLE_VOLUME_SLIDER_HUD] = enabled }
        }
    }

    fun setShowSystemPanelWhenHudOff(enabled: Boolean) {
        if (_settings.value.showSystemPanelWhenHudOff == enabled) return
        _settings.value = _settings.value.copy(showSystemPanelWhenHudOff = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.SHOW_SYSTEM_PANEL_WHEN_HUD_OFF] = enabled }
        }
    }

    fun setHudSideSynced(synced: Boolean) {
        if (_settings.value.hudSideSynced == synced) return
        _settings.value = _settings.value.copy(hudSideSynced = synced)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_SIDE_SYNCED] = synced }
        }
    }

    fun setHudEdgeSide(side: EdgeSide) {
        if (!_settings.value.hudSideSynced && _settings.value.hudEdgeSide == side) return
        _settings.value = _settings.value.copy(
            hudSideSynced = false,
            hudEdgeSide = side
        )
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit {
                it[Keys.HUD_SIDE_SYNCED] = false
                it[Keys.HUD_EDGE_SIDE] = side.name
            }
        }
    }

    fun setHudYFraction(fraction: Float) {
        val clamped = NudgeSettings.clampFraction(fraction)
        if (_settings.value.hudYFraction == clamped) return
        _settings.value = _settings.value.copy(hudYFraction = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_Y] = clamped }
        }
    }

    fun setHudHeightDp(heightDp: Float) {
        val clamped = NudgeSettings.clampHudHeight(heightDp)
        if (_settings.value.hudHeightDp == clamped) return
        _settings.value = _settings.value.copy(hudHeightDp = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_HEIGHT_DP] = clamped }
        }
    }

    fun setHudWidthDp(widthDp: Float) {
        val clamped = NudgeSettings.clampHudWidth(widthDp)
        if (_settings.value.hudWidthDp == clamped) return
        _settings.value = _settings.value.copy(hudWidthDp = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_WIDTH_DP] = clamped }
        }
    }

    fun setHudCornerRadiusDp(cornerRadiusDp: Float) {
        val clamped = NudgeSettings.clampHudCornerRadius(cornerRadiusDp)
        if (_settings.value.hudCornerRadiusDp == clamped) return
        _settings.value = _settings.value.copy(hudCornerRadiusDp = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_CORNER_RADIUS_DP] = clamped }
        }
    }

    fun setHudAutoHideMs(timeoutMs: Long) {
        val clamped = NudgeSettings.clampAutoHideMs(timeoutMs)
        if (_settings.value.hudAutoHideMs == clamped) return
        _settings.value = _settings.value.copy(hudAutoHideMs = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_AUTO_HIDE_MS] = clamped }
        }
    }

    fun setHudTriggerMode(mode: HudTriggerMode) {
        if (_settings.value.hudTriggerMode == mode) return
        _settings.value = _settings.value.copy(hudTriggerMode = mode)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_TRIGGER_MODE] = mode.name }
        }
    }

    fun setEnableSliderHudInLandscape(enabled: Boolean) {
        if (_settings.value.enableSliderHudInLandscape == enabled) return
        _settings.value = _settings.value.copy(enableSliderHudInLandscape = enabled)
        if (enabled) {
            requestLivePreview(PreviewTarget.HUD)
        } else {
            clearLivePreview()
        }
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ENABLE_SLIDER_HUD_LANDSCAPE] = enabled }
        }
    }

    fun setLandscapeHudEdgeSide(side: ScreenEdge) {
        if (_settings.value.landscapeHudEdgeSide == side) return
        _settings.value = _settings.value.copy(landscapeHudEdgeSide = side)
        requestLivePreview(PreviewTarget.HUD, forceLandscape = true, durationMs = 800L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.LANDSCAPE_HUD_EDGE_SIDE] = side.name }
        }
    }

    fun setLandscapeHudYFraction(fraction: Float) {
        val clamped = NudgeSettings.clampFraction(fraction)
        if (_settings.value.landscapeHudYFraction == clamped) return
        _settings.value = _settings.value.copy(landscapeHudYFraction = clamped)
        startLivePreview(PreviewTarget.HUD, forceLandscape = true)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.LANDSCAPE_HUD_Y] = clamped }
        }
    }

    fun setShowSystemPanelInLandscape(enabled: Boolean) {
        if (_settings.value.showSystemPanelInLandscape == enabled) return
        _settings.value = _settings.value.copy(showSystemPanelInLandscape = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.SHOW_SYSTEM_PANEL_LANDSCAPE] = enabled }
        }
    }

    fun setFloatingMode(enabled: Boolean) {
        if (_settings.value.floatingMode == enabled) return
        _settings.value = _settings.value.copy(floatingMode = enabled)
        requestLivePreview(PreviewTarget.HUD, durationMs = 2400L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.FLOATING_MODE] = enabled }
        }
    }

    fun setHudEdgeInsetDp(insetDp: Float) {
        val clamped = NudgeSettings.clampHudEdgeInset(insetDp)
        if (_settings.value.hudEdgeInsetDp == clamped) return
        _settings.value = _settings.value.copy(hudEdgeInsetDp = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_EDGE_INSET_DP] = clamped }
        }
    }

    fun setShowVolumeCounter(enabled: Boolean) {
        if (_settings.value.showVolumeCounter == enabled) return
        _settings.value = _settings.value.copy(showVolumeCounter = enabled)
        requestLivePreview(PreviewTarget.VOLUME_SLIDER)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.SHOW_VOLUME_COUNTER] = enabled }
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        if (_settings.value.hapticsEnabled == enabled) return
        _settings.value = _settings.value.copy(hapticsEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
        }
    }

    fun setLightMode(enabled: Boolean) {
        if (_settings.value.lightMode == enabled) return
        _settings.value = _settings.value.copy(lightMode = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.LIGHT_MODE] = enabled }
        }
    }

    fun markAccessibilityPromptHandled(versionCode: Int) {
        if (_lastPromptedAccessibilityVersion.value >= versionCode) return
        _lastPromptedAccessibilityVersion.value = versionCode
        scope.launch {
            appContext.nudgeDataStore.edit {
                it[Keys.LAST_PROMPTED_ACCESSIBILITY_VERSION] = versionCode
            }
        }
    }

    fun setEnableDndToggle(enabled: Boolean) {
        if (_settings.value.enableDndToggle == enabled) return
        _settings.value = _settings.value.copy(enableDndToggle = enabled)
        if (enabled) {
            requestLivePreview(PreviewTarget.DND, durationMs = _settings.value.dndTimeoutMs)
        } else {
            clearLivePreview()
        }
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ENABLE_DND_TOGGLE] = enabled }
        }
    }

    fun setDndModeType(modeType: DndModeType) {
        if (_settings.value.dndModeType == modeType) return
        _settings.value = _settings.value.copy(dndModeType = modeType)
        requestLivePreview(PreviewTarget.DND, durationMs = _settings.value.dndTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_MODE_TYPE] = modeType.name }
        }
    }

    fun setDndIncludeSound(enabled: Boolean) {
        val current = _settings.value
        if (!enabled) {
            val count = (if (current.dndIncludeSound) 1 else 0) +
                (if (current.dndIncludeVibrate) 1 else 0) +
                (if (current.dndIncludeSilent) 1 else 0)
            if (count <= 2) return
        }
        if (current.dndIncludeSound == enabled) return
        _settings.value = current.copy(dndIncludeSound = enabled)
        requestLivePreview(PreviewTarget.DND, durationMs = current.dndTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_INCLUDE_SOUND] = enabled }
        }
    }

    fun setDndIncludeVibrate(enabled: Boolean) {
        val current = _settings.value
        if (!enabled) {
            val count = (if (current.dndIncludeSound) 1 else 0) +
                (if (current.dndIncludeVibrate) 1 else 0) +
                (if (current.dndIncludeSilent) 1 else 0)
            if (count <= 2) return
        }
        if (current.dndIncludeVibrate == enabled) return
        _settings.value = current.copy(dndIncludeVibrate = enabled)
        requestLivePreview(PreviewTarget.DND, durationMs = current.dndTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_INCLUDE_VIBRATE] = enabled }
        }
    }

    fun setDndIncludeSilent(enabled: Boolean) {
        val current = _settings.value
        if (!enabled) {
            val count = (if (current.dndIncludeSound) 1 else 0) +
                (if (current.dndIncludeVibrate) 1 else 0) +
                (if (current.dndIncludeSilent) 1 else 0)
            if (count <= 2) return
        }
        if (current.dndIncludeSilent == enabled) return
        _settings.value = current.copy(dndIncludeSilent = enabled)
        requestLivePreview(PreviewTarget.DND, durationMs = current.dndTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_INCLUDE_SILENT] = enabled }
        }
    }

    fun setDndTrigger(trigger: HardwareTrigger) {
        val current = _settings.value
        val shouldClearModes = trigger != HardwareTrigger.NONE && current.modesTrigger == trigger
        val updated = current.copy(
            dndTrigger = trigger,
            modesTrigger = if (shouldClearModes) HardwareTrigger.NONE else current.modesTrigger
        )
        _settings.value = updated
        scope.launch {
            appContext.nudgeDataStore.edit { prefs ->
                prefs[Keys.DND_TRIGGER] = trigger.name
                if (shouldClearModes) {
                    prefs[Keys.MODES_TRIGGER] = HardwareTrigger.NONE.name
                }
            }
        }
    }

    fun setDndTimeoutMs(timeoutMs: Long) {
        val clamped = NudgeSettings.clampDndTimeoutMs(timeoutMs)
        if (_settings.value.dndTimeoutMs == clamped) return
        _settings.value = _settings.value.copy(dndTimeoutMs = clamped)
        requestLivePreview(PreviewTarget.DND, durationMs = clamped)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_TIMEOUT_MS] = clamped }
        }
    }

    fun setDndEdgeSide(side: EdgeSide) {
        val bezelEdge = if (side == EdgeSide.LEFT) BezelEdge.LEFT else BezelEdge.RIGHT
        if (_settings.value.dndPortraitEdge == bezelEdge) return
        _settings.value = _settings.value.copy(dndPortraitEdge = bezelEdge)
        requestLivePreview(PreviewTarget.DND, durationMs = _settings.value.dndTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_EDGE_SIDE] = side.name }
        }
    }

    fun setDndYFraction(fraction: Float) {
        val clamped = NudgeSettings.clampBezelYFraction(fraction)
        if (_settings.value.dndPortraitOffsetPercent == clamped) return
        _settings.value = _settings.value.copy(dndPortraitOffsetPercent = clamped)
        requestLivePreview(PreviewTarget.DND, durationMs = _settings.value.dndTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_Y] = clamped }
        }
    }

    fun setDndWidthDp(widthDp: Float) {
        val clamped = NudgeSettings.clampDndWidth(widthDp)
        if (_settings.value.dndThicknessDp == clamped) return
        _settings.value = _settings.value.copy(dndThicknessDp = clamped)
        requestLivePreview(PreviewTarget.DND, durationMs = _settings.value.dndTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_WIDTH_DP] = clamped }
        }
    }

    fun setDndHeightDp(heightDp: Float) {
        val clamped = NudgeSettings.clampDndHeight(heightDp)
        if (_settings.value.dndLengthDp == clamped) return
        _settings.value = _settings.value.copy(dndLengthDp = clamped)
        requestLivePreview(PreviewTarget.DND_PANEL, durationMs = _settings.value.dndTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit {
                it[Keys.DND_HEIGHT_DP] = clamped
                it[Keys.DND_LENGTH_DP] = clamped
            }
        }
    }

    fun setIsEdgePopMasterEnabled(enabled: Boolean) {
        if (_settings.value.isEdgePopMasterEnabled == enabled) return
        _settings.value = _settings.value.copy(isEdgePopMasterEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.IS_EDGE_POP_MASTER_ENABLED] = enabled }
        }
    }

    fun setEnableActionsPanel(enabled: Boolean) {
        if (_settings.value.enableActionsPanel == enabled) return
        _settings.value = _settings.value.copy(enableActionsPanel = enabled)
        if (enabled) {
            requestLivePreview(PreviewTarget.MODES, durationMs = _settings.value.modesTimeoutMs)
        } else {
            clearLivePreview()
        }
        scope.launch {
            appContext.nudgeDataStore.edit {
                it[Keys.ENABLE_ACTIONS_PANEL] = enabled
                it[Keys.ENABLE_MODES_CAROUSEL] = enabled
            }
        }
    }

    fun setEnableModesCarousel(enabled: Boolean) = setEnableActionsPanel(enabled)

    fun setSimultaneousPressTimeoutMs(timeoutMs: Long) {
        val clamped = NudgeSettings.clampSimultaneousTimeoutMs(timeoutMs)
        if (_settings.value.simultaneousPressTimeoutMs == clamped) return
        _settings.value = _settings.value.copy(simultaneousPressTimeoutMs = clamped)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.SIMULTANEOUS_PRESS_TIMEOUT_MS] = clamped }
        }
    }

    fun setActionsIconSpacingDp(spacingDp: Float) {
        val clamped = NudgeSettings.clampActionsIconSpacingDp(spacingDp)
        if (_settings.value.actionsIconSpacingDp == clamped) return
        _settings.value = _settings.value.copy(actionsIconSpacingDp = clamped)
        requestLivePreview(PreviewTarget.MODES, durationMs = _settings.value.modesTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ACTIONS_ICON_SPACING_DP] = clamped }
        }
    }

    fun setCustomButton(keyCode: Int, scanCode: Int, label: String) {
        val src = TriggerSource.ACCESSIBILITY.name
        _settings.value = _settings.value.copy(
            customButtonSource = src,
            customButtonKeyCode = keyCode,
            customButtonScanCode = scanCode,
            customButtonLabel = label,
            customButtonDeviceName = ""
        )
        scope.launch {
            appContext.nudgeDataStore.edit { prefs ->
                prefs[Keys.CUSTOM_BUTTON_SOURCE] = src
                prefs[Keys.CUSTOM_BUTTON_KEY_CODE] = keyCode
                prefs[Keys.CUSTOM_BUTTON_SCAN_CODE] = scanCode
                prefs[Keys.CUSTOM_BUTTON_LABEL] = label
                prefs[Keys.CUSTOM_BUTTON_DEVICE_NAME] = ""
            }
        }
    }

    fun setCustomHardwareButton(trigger: HardwareKeyTrigger) {
        val src = trigger.source.name
        val keyCode = trigger.androidKeyCode ?: 0
        val scanCode = trigger.scanCode ?: 0
        val label = trigger.linuxKeyName ?: ""
        val devName = trigger.deviceName ?: ""
        _settings.value = _settings.value.copy(
            customButtonSource = src,
            customButtonKeyCode = keyCode,
            customButtonScanCode = scanCode,
            customButtonLabel = label,
            customButtonDeviceName = devName
        )
        scope.launch {
            appContext.nudgeDataStore.edit { prefs ->
                prefs[Keys.CUSTOM_BUTTON_SOURCE] = src
                prefs[Keys.CUSTOM_BUTTON_KEY_CODE] = keyCode
                prefs[Keys.CUSTOM_BUTTON_SCAN_CODE] = scanCode
                prefs[Keys.CUSTOM_BUTTON_LABEL] = label
                prefs[Keys.CUSTOM_BUTTON_DEVICE_NAME] = devName
            }
        }
    }

    fun setEnableShizukuPowerHoldFallback(enabled: Boolean) {
        if (_settings.value.enableShizukuPowerHoldFallback == enabled) return
        _settings.value = _settings.value.copy(enableShizukuPowerHoldFallback = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { prefs ->
                prefs[Keys.ENABLE_SHIZUKU_POWER_HOLD_FALLBACK] = enabled
            }
        }
    }

    fun setCustomHardwareButton(keyCode: Int, scanCode: Int, label: String) =
        setCustomButton(keyCode, scanCode, label)

    fun setIsHideAccessibilityEnabled(enabled: Boolean) {
        if (_settings.value.isHideAccessibilityEnabled == enabled) return
        _settings.value = _settings.value.copy(isHideAccessibilityEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.IS_HIDE_ACCESSIBILITY_ENABLED] = enabled }
        }
    }

    fun setCloakedPackages(packages: Set<String>) {
        _settings.value = _settings.value.copy(cloakedPackages = packages)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.CLOAKED_PACKAGES] = packages }
        }
    }

    fun addCloakedPackage(pkg: String) {
        val updated = _settings.value.cloakedPackages + pkg
        setCloakedPackages(updated)
    }

    fun removeCloakedPackage(pkg: String) {
        val updated = _settings.value.cloakedPackages - pkg
        setCloakedPackages(updated)
    }

    fun setIsAutoKeepAliveViaShizuku(enabled: Boolean) {
        if (_settings.value.isAutoKeepAliveViaShizuku == enabled) return
        _settings.value = _settings.value.copy(isAutoKeepAliveViaShizuku = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.IS_AUTO_KEEP_ALIVE_VIA_SHIZUKU] = enabled }
        }
    }

    fun setModesTrigger(trigger: HardwareTrigger) {
        val current = _settings.value
        val shouldClearDnd = trigger != HardwareTrigger.NONE && current.dndTrigger == trigger
        val updated = current.copy(
            modesTrigger = trigger,
            dndTrigger = if (shouldClearDnd) HardwareTrigger.NONE else current.dndTrigger
        )
        _settings.value = updated
        scope.launch {
            appContext.nudgeDataStore.edit { prefs ->
                prefs[Keys.MODES_TRIGGER] = trigger.name
                if (shouldClearDnd) {
                    prefs[Keys.DND_TRIGGER] = HardwareTrigger.NONE.name
                }
            }
        }
    }

    fun setEnableModesNotificationBridge(enabled: Boolean) {
        if (_settings.value.enableModesNotificationBridge == enabled) return
        _settings.value = _settings.value.copy(enableModesNotificationBridge = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ENABLE_MODES_NOTIFICATION_BRIDGE] = enabled }
        }
    }

    fun setEnableModesBuiltInPresets(enabled: Boolean) {
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ENABLE_MODES_BUILT_IN_PRESETS] = enabled }
        }
    }

    fun setModesTimeoutMs(timeoutMs: Long) {
        val clamped = NudgeSettings.clampModesTimeoutMs(timeoutMs)
        if (_settings.value.modesTimeoutMs == clamped) return
        _settings.value = _settings.value.copy(modesTimeoutMs = clamped)
        requestLivePreview(PreviewTarget.MODES, durationMs = clamped)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_TIMEOUT_MS] = clamped }
        }
    }

    fun setModesEdgeSide(side: EdgeSide) {
        val bezelEdge = if (side == EdgeSide.LEFT) BezelEdge.LEFT else BezelEdge.RIGHT
        if (_settings.value.modesPortraitEdge == bezelEdge) return
        _settings.value = _settings.value.copy(modesPortraitEdge = bezelEdge)
        requestLivePreview(PreviewTarget.MODES, durationMs = _settings.value.modesTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_EDGE_SIDE] = side.name }
        }
    }

    fun setModesYFraction(fraction: Float) {
        val clamped = NudgeSettings.clampBezelYFraction(fraction)
        if (_settings.value.modesPortraitOffsetPercent == clamped) return
        _settings.value = _settings.value.copy(modesPortraitOffsetPercent = clamped)
        requestLivePreview(PreviewTarget.MODES, durationMs = _settings.value.modesTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_Y] = clamped }
        }
    }

    fun setModesWidthDp(widthDp: Float) {
        val clamped = NudgeSettings.clampModesWidth(widthDp)
        if (_settings.value.modesThicknessDp == clamped) return
        _settings.value = _settings.value.copy(modesThicknessDp = clamped)
        requestLivePreview(PreviewTarget.MODES, durationMs = _settings.value.modesTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_WIDTH_DP] = clamped }
        }
    }

    fun setModesHeightDp(heightDp: Float) {
        val clamped = NudgeSettings.clampModesHeight(heightDp)
        if (_settings.value.modesLengthDp == clamped) return
        _settings.value = _settings.value.copy(modesLengthDp = clamped)
        requestLivePreview(PreviewTarget.MODES, durationMs = _settings.value.modesTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_HEIGHT_DP] = clamped }
        }
    }

    fun setDndDoubleTapTimeoutMs(timeoutMs: Long) {
        val clamped = NudgeSettings.clampDoubleTapTimeoutMs(timeoutMs)
        if (_settings.value.dndDoubleTapTimeoutMs == clamped) return
        _settings.value = _settings.value.copy(dndDoubleTapTimeoutMs = clamped)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_DOUBLE_TAP_TIMEOUT_MS] = clamped }
        }
    }

    fun setDndPortraitEdge(edge: BezelEdge) {
        if (_settings.value.dndPortraitEdge == edge) return
        _settings.value = _settings.value.copy(dndPortraitEdge = edge)
        requestLivePreview(PreviewTarget.DND, durationMs = _settings.value.dndTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_PORTRAIT_EDGE] = edge.name }
        }
    }

    fun setDndPortraitOffset(offset: Float) {
        val clamped = NudgeSettings.clampBezelOffsetPercent(offset)
        if (_settings.value.dndPortraitOffsetPercent == clamped) return
        _settings.value = _settings.value.copy(dndPortraitOffsetPercent = clamped)
        requestLivePreview(PreviewTarget.DND, durationMs = _settings.value.dndTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_PORTRAIT_OFFSET] = clamped }
        }
    }

    fun setDndEnableLandscape(enabled: Boolean) {
        if (_settings.value.dndEnableLandscape == enabled) return
        _settings.value = _settings.value.copy(dndEnableLandscape = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_ENABLE_LANDSCAPE] = enabled }
        }
    }

    fun setDndLandscapeEdge(edge: BezelEdge) {
        if (_settings.value.dndLandscapeEdge == edge) return
        _settings.value = _settings.value.copy(dndLandscapeEdge = edge)
        requestLivePreview(PreviewTarget.DND, forceLandscape = true, durationMs = 800L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_LANDSCAPE_EDGE] = edge.name }
        }
    }

    fun setDndLandscapeOffset(offset: Float) {
        val clamped = NudgeSettings.clampBezelOffsetPercent(offset)
        if (_settings.value.dndLandscapeOffsetPercent == clamped) return
        _settings.value = _settings.value.copy(dndLandscapeOffsetPercent = clamped)
        startLivePreview(PreviewTarget.DND, forceLandscape = true)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_LANDSCAPE_OFFSET] = clamped }
        }
    }

    fun setDndThicknessDp(thicknessDp: Float) {
        val clamped = NudgeSettings.clampBezelThicknessDp(thicknessDp)
        if (_settings.value.dndThicknessDp == clamped) return
        _settings.value = _settings.value.copy(dndThicknessDp = clamped)
        requestLivePreview(PreviewTarget.DND, durationMs = _settings.value.dndTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_THICKNESS_DP] = clamped }
        }
    }

    fun setDndLengthDp(lengthDp: Float) {
        val clamped = NudgeSettings.clampBezelLengthDp(lengthDp)
        if (_settings.value.dndLengthDp == clamped) return
        _settings.value = _settings.value.copy(dndLengthDp = clamped)
        requestLivePreview(PreviewTarget.DND_PANEL, durationMs = _settings.value.dndTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit {
                it[Keys.DND_LENGTH_DP] = clamped
                it[Keys.DND_HEIGHT_DP] = clamped
            }
        }
    }

    fun setModesDoubleTapTimeoutMs(timeoutMs: Long) {
        val clamped = NudgeSettings.clampDoubleTapTimeoutMs(timeoutMs)
        if (_settings.value.modesDoubleTapTimeoutMs == clamped) return
        _settings.value = _settings.value.copy(modesDoubleTapTimeoutMs = clamped)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_DOUBLE_TAP_TIMEOUT_MS] = clamped }
        }
    }

    fun setModesPortraitEdge(edge: BezelEdge) {
        if (_settings.value.modesPortraitEdge == edge) return
        _settings.value = _settings.value.copy(modesPortraitEdge = edge)
        requestLivePreview(PreviewTarget.MODES, durationMs = _settings.value.modesTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_PORTRAIT_EDGE] = edge.name }
        }
    }

    fun setModesPortraitOffset(offset: Float) {
        val clamped = NudgeSettings.clampBezelOffsetPercent(offset)
        if (_settings.value.modesPortraitOffsetPercent == clamped) return
        _settings.value = _settings.value.copy(modesPortraitOffsetPercent = clamped)
        requestLivePreview(PreviewTarget.MODES, durationMs = _settings.value.modesTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_PORTRAIT_OFFSET] = clamped }
        }
    }

    fun setModesEnableLandscape(enabled: Boolean) {
        if (_settings.value.modesEnableLandscape == enabled) return
        _settings.value = _settings.value.copy(modesEnableLandscape = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_ENABLE_LANDSCAPE] = enabled }
        }
    }

    fun setModesLandscapeEdge(edge: BezelEdge) {
        if (_settings.value.modesLandscapeEdge == edge) return
        _settings.value = _settings.value.copy(modesLandscapeEdge = edge)
        requestLivePreview(PreviewTarget.MODES, forceLandscape = true, durationMs = 800L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_LANDSCAPE_EDGE] = edge.name }
        }
    }

    fun setModesLandscapeOffset(offset: Float) {
        val clamped = NudgeSettings.clampBezelOffsetPercent(offset)
        if (_settings.value.modesLandscapeOffsetPercent == clamped) return
        _settings.value = _settings.value.copy(modesLandscapeOffsetPercent = clamped)
        startLivePreview(PreviewTarget.MODES, forceLandscape = true)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_LANDSCAPE_OFFSET] = clamped }
        }
    }

    fun setModesThicknessDp(thicknessDp: Float) {
        val clamped = NudgeSettings.clampBezelThicknessDp(thicknessDp)
        if (_settings.value.modesThicknessDp == clamped) return
        _settings.value = _settings.value.copy(modesThicknessDp = clamped)
        requestLivePreview(PreviewTarget.MODES, durationMs = _settings.value.modesTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_THICKNESS_DP] = clamped }
        }
    }

    fun setModesLengthDp(lengthDp: Float) {
        val clamped = NudgeSettings.clampBezelLengthDp(lengthDp)
        if (_settings.value.modesLengthDp == clamped) return
        _settings.value = _settings.value.copy(modesLengthDp = clamped)
        requestLivePreview(PreviewTarget.MODES, durationMs = _settings.value.modesTimeoutMs)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.MODES_LENGTH_DP] = clamped }
        }
    }

    fun setCustomModes(modes: List<CustomMode>) {
        if (modes.size < 2) return
        _settings.value = _settings.value.copy(customModes = modes)
        val json = serializeCustomModes(modes)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.CUSTOM_MODES_JSON] = json }
        }
    }

    fun addCustomMode(mode: CustomMode) {
        val current = _settings.value.customModes.toMutableList()
        current.add(mode)
        setCustomModes(current)
    }

    fun updateCustomMode(mode: CustomMode) {
        val current = _settings.value.customModes.map { if (it.id == mode.id) mode else it }
        setCustomModes(current)
    }

    fun deleteCustomMode(id: String) {
        val current = _settings.value.customModes.filterNot { it.id == id }
        if (current.size >= 2) {
            setCustomModes(current)
        }
    }

    fun reorderCustomMode(fromIndex: Int, toIndex: Int) {
        val current = _settings.value.customModes.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            setCustomModes(current)
        }
    }

    fun setEdgePopHapticsEnabled(enabled: Boolean) {
        if (_settings.value.edgePopHapticsEnabled == enabled) return
        _settings.value = _settings.value.copy(edgePopHapticsEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.EDGE_POP_HAPTICS_ENABLED] = enabled }
        }
    }

    fun setVolumeSliderHapticsEnabled(enabled: Boolean) {
        if (_settings.value.volumeSliderHapticsEnabled == enabled) return
        _settings.value = _settings.value.copy(volumeSliderHapticsEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.VOLUME_SLIDER_HAPTICS_ENABLED] = enabled }
        }
    }

    fun setSoundIconModeSwitchHapticsEnabled(enabled: Boolean) {
        if (_settings.value.soundIconModeSwitchHapticsEnabled == enabled) return
        _settings.value = _settings.value.copy(soundIconModeSwitchHapticsEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.SOUND_ICON_MODE_SWITCH_HAPTICS_ENABLED] = enabled }
        }
    }

    fun setDndPanelHapticsEnabled(enabled: Boolean) {
        if (_settings.value.dndPanelHapticsEnabled == enabled) return
        _settings.value = _settings.value.copy(dndPanelHapticsEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_PANEL_HAPTICS_ENABLED] = enabled }
        }
    }

    fun setActionsPanelHapticsEnabled(enabled: Boolean) {
        if (_settings.value.actionsPanelHapticsEnabled == enabled) return
        _settings.value = _settings.value.copy(actionsPanelHapticsEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ACTIONS_PANEL_HAPTICS_ENABLED] = enabled }
        }
    }

    fun setNudgeHapticsEnabled(enabled: Boolean) {
        if (_settings.value.nudgeHapticsEnabled == enabled) return
        _settings.value = _settings.value.copy(nudgeHapticsEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.NUDGE_HAPTICS_ENABLED] = enabled }
        }
    }

    fun setAudioMixerPopupWidthDp(width: Float) {
        val clamped = NudgeSettings.clampAudioMixerPopupWidth(width)
        if (_settings.value.audioMixerPopupWidthDp == clamped && _settings.value.isAudioMixerManuallySized) return
        _settings.value = _settings.value.copy(audioMixerPopupWidthDp = clamped, isAudioMixerManuallySized = true)
        scope.launch {
            appContext.nudgeDataStore.edit {
                it[Keys.AUDIO_MIXER_POPUP_WIDTH_DP] = clamped
                it[Keys.IS_AUDIO_MIXER_MANUALLY_SIZED] = true
            }
        }
    }

    fun setAudioMixerPopupHeightDp(height: Float) {
        val clamped = NudgeSettings.clampAudioMixerPopupHeight(height)
        if (_settings.value.audioMixerPopupHeightDp == clamped && _settings.value.isAudioMixerManuallySized) return
        _settings.value = _settings.value.copy(audioMixerPopupHeightDp = clamped, isAudioMixerManuallySized = true)
        scope.launch {
            appContext.nudgeDataStore.edit {
                it[Keys.AUDIO_MIXER_POPUP_HEIGHT_DP] = clamped
                it[Keys.IS_AUDIO_MIXER_MANUALLY_SIZED] = true
            }
        }
    }

    fun setAudioMixerPillWidthDp(width: Float) {
        val clamped = NudgeSettings.clampAudioMixerPillWidth(width)
        if (_settings.value.audioMixerPillWidthDp == clamped && _settings.value.isAudioMixerManuallySized) return
        _settings.value = _settings.value.copy(audioMixerPillWidthDp = clamped, isAudioMixerManuallySized = true)
        scope.launch {
            appContext.nudgeDataStore.edit {
                it[Keys.AUDIO_MIXER_PILL_WIDTH_DP] = clamped
                it[Keys.IS_AUDIO_MIXER_MANUALLY_SIZED] = true
            }
        }
    }

    fun setAudioMixerPillHeightDp(height: Float) {
        val clamped = NudgeSettings.clampAudioMixerPillHeight(height)
        if (_settings.value.audioMixerPillHeightDp == clamped && _settings.value.isAudioMixerManuallySized) return
        _settings.value = _settings.value.copy(audioMixerPillHeightDp = clamped, isAudioMixerManuallySized = true)
        scope.launch {
            appContext.nudgeDataStore.edit {
                it[Keys.AUDIO_MIXER_PILL_HEIGHT_DP] = clamped
                it[Keys.IS_AUDIO_MIXER_MANUALLY_SIZED] = true
            }
        }
    }

    fun setAudioMixerPillSpacingDp(spacing: Float) {
        val clamped = NudgeSettings.clampAudioMixerPillSpacing(spacing)
        if (_settings.value.audioMixerPillSpacingDp == clamped && _settings.value.isAudioMixerManuallySized) return
        _settings.value = _settings.value.copy(audioMixerPillSpacingDp = clamped, isAudioMixerManuallySized = true)
        scope.launch {
            appContext.nudgeDataStore.edit {
                it[Keys.AUDIO_MIXER_PILL_SPACING_DP] = clamped
                it[Keys.IS_AUDIO_MIXER_MANUALLY_SIZED] = true
            }
        }
    }

    fun setIsAudioMixerManuallySized(manual: Boolean) {
        if (_settings.value.isAudioMixerManuallySized == manual) return
        _settings.value = _settings.value.copy(isAudioMixerManuallySized = manual)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.IS_AUDIO_MIXER_MANUALLY_SIZED] = manual }
        }
    }

    fun resetAudioMixerAutoFit() {
        setIsAudioMixerManuallySized(false)
    }

    fun setIsAudioMixerAttachedToSlider(attached: Boolean) {
        if (_settings.value.isAudioMixerAttachedToSlider == attached) return
        _settings.value = _settings.value.copy(isAudioMixerAttachedToSlider = attached)
        requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2000L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.IS_AUDIO_MIXER_ATTACHED_TO_SLIDER] = attached }
        }
    }

    fun setAudioMixerXPercent(percent: Float, triggerPreview: Boolean = false) {
        val clamped = NudgeSettings.clampAudioMixerPercent(percent)
        if (_settings.value.audioMixerXPercent == clamped) return
        _settings.value = _settings.value.copy(audioMixerXPercent = clamped)
        if (triggerPreview) {
            requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2000L)
        }
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.AUDIO_MIXER_X_PERCENT] = clamped }
        }
    }

    fun setAudioMixerYPercent(percent: Float, triggerPreview: Boolean = false) {
        val clamped = NudgeSettings.clampAudioMixerPercent(percent)
        if (_settings.value.audioMixerYPercent == clamped) return
        _settings.value = _settings.value.copy(audioMixerYPercent = clamped)
        if (triggerPreview) {
            requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2000L)
        }
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.AUDIO_MIXER_Y_PERCENT] = clamped }
        }
    }

    fun setAudioMixerScalePercent(percent: Int, triggerPreview: Boolean = false) {
        val clamped = NudgeSettings.clampAudioMixerScalePercent(percent)
        if (_settings.value.audioMixerScalePercent == clamped) return
        _settings.value = _settings.value.copy(audioMixerScalePercent = clamped)
        if (triggerPreview) {
            requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2000L)
        }
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.AUDIO_MIXER_SCALE_PERCENT] = clamped }
        }
    }

    fun setIsAlwaysShowCallSliderInMixer(alwaysShow: Boolean) {
        if (_settings.value.isAlwaysShowCallSliderInMixer == alwaysShow) return
        _settings.value = _settings.value.copy(isAlwaysShowCallSliderInMixer = alwaysShow)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.IS_ALWAYS_SHOW_CALL_SLIDER_IN_MIXER] = alwaysShow }
        }
    }

    fun setDndIconSizeDp(size: Float) {
        val clamped = NudgeSettings.clampIconSizeDp(size)
        if (_settings.value.dndIconSizeDp == clamped) return
        _settings.value = _settings.value.copy(dndIconSizeDp = clamped)
        startLivePreview(PreviewTarget.DND)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_ICON_SIZE_DP] = clamped }
        }
    }

    fun setActionsIconSizeDp(size: Float) {
        val clamped = NudgeSettings.clampIconSizeDp(size)
        if (_settings.value.actionsIconSizeDp == clamped) return
        _settings.value = _settings.value.copy(actionsIconSizeDp = clamped)
        startLivePreview(PreviewTarget.MODES)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ACTIONS_ICON_SIZE_DP] = clamped }
        }
    }

    fun setDndUnfocusedOffsetDp(offset: Float) {
        val clamped = NudgeSettings.clampDndUnfocusedOffsetDp(offset)
        if (_settings.value.dndUnfocusedOffsetDp == clamped) return
        _settings.value = _settings.value.copy(dndUnfocusedOffsetDp = clamped)
        startLivePreview(PreviewTarget.DND)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_UNFOCUSED_OFFSET_DP] = clamped }
        }
    }

    fun setEdgePopFluidityPercent(percent: Int) {
        val clamped = NudgeSettings.clampFluidityPercent(percent)
        if (_settings.value.edgePopFluidityPercent == clamped) return
        _settings.value = _settings.value.copy(edgePopFluidityPercent = clamped)
        startLivePreview(PreviewTarget.POP_BOTH)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.EDGE_POP_FLUIDITY_PERCENT] = clamped }
        }
    }
    fun setEdgePopCurvaturePercent(percent: Int) = setEdgePopFluidityPercent(percent)

    fun setVolumeSliderFluidityPercent(percent: Int) {
        val clamped = NudgeSettings.clampFluidityPercent(percent)
        if (_settings.value.volumeSliderFluidityPercent == clamped) return
        _settings.value = _settings.value.copy(volumeSliderFluidityPercent = clamped)
        startLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.VOLUME_SLIDER_FLUIDITY_PERCENT] = clamped }
        }
    }
    fun setVolumeSliderCurvaturePercent(percent: Int) = setVolumeSliderFluidityPercent(percent)

    fun setDndPanelFluidityPercent(percent: Int) {
        val clamped = NudgeSettings.clampFluidityPercent(percent)
        if (_settings.value.dndPanelFluidityPercent == clamped) return
        _settings.value = _settings.value.copy(dndPanelFluidityPercent = clamped)
        startLivePreview(PreviewTarget.DND)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.DND_PANEL_FLUIDITY_PERCENT] = clamped }
        }
    }
    fun setDndPanelCurvaturePercent(percent: Int) = setDndPanelFluidityPercent(percent)

    fun setShowDndLabel(show: Boolean) {
        if (_settings.value.showDndLabel == show) return
        _settings.value = _settings.value.copy(showDndLabel = show)
        startLivePreview(PreviewTarget.DND)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.SHOW_DND_LABEL] = show }
        }
    }

    fun setActionsPanelFluidityPercent(percent: Int) {
        val clamped = NudgeSettings.clampFluidityPercent(percent)
        if (_settings.value.actionsPanelFluidityPercent == clamped) return
        _settings.value = _settings.value.copy(actionsPanelFluidityPercent = clamped)
        startLivePreview(PreviewTarget.MODES)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ACTIONS_PANEL_FLUIDITY_PERCENT] = clamped }
        }
    }
    fun setActionsPanelCurvaturePercent(percent: Int) = setActionsPanelFluidityPercent(percent)

    fun setShowActionsLabel(show: Boolean) {
        if (_settings.value.showActionsLabel == show) return
        _settings.value = _settings.value.copy(showActionsLabel = show)
        startLivePreview(PreviewTarget.MODES)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.SHOW_ACTIONS_LABEL] = show }
        }
    }

    fun setIsPerAppVolumeEnabled(enabled: Boolean) {
        if (_settings.value.isPerAppVolumeEnabled == enabled) return
        _settings.value = _settings.value.copy(isPerAppVolumeEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.IS_PER_APP_VOLUME_ENABLED] = enabled }
        }
    }

    fun setPerAppVolumeDisplayMode(mode: String) {
        if (_settings.value.perAppVolumeDisplayMode == mode) return
        _settings.value = _settings.value.copy(perAppVolumeDisplayMode = mode)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.PER_APP_VOLUME_DISPLAY_MODE] = mode }
        }
    }

    fun setUpdateCheckFrequency(freq: String) {
        if (_settings.value.updateCheckFrequency == freq) return
        _settings.value = _settings.value.copy(updateCheckFrequency = freq)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.UPDATE_CHECK_FREQUENCY] = freq }
        }
    }

    fun setLastUpdateCheckTimestampMs(ts: Long) {
        if (_settings.value.lastUpdateCheckTimestampMs == ts) return
        _settings.value = _settings.value.copy(lastUpdateCheckTimestampMs = ts)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.LAST_UPDATE_CHECK_TIMESTAMP_MS] = ts }
        }
    }

    fun setCachedLatestReleaseJson(json: String) {
        if (_settings.value.cachedLatestReleaseJson == json) return
        _settings.value = _settings.value.copy(cachedLatestReleaseJson = json)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.CACHED_LATEST_RELEASE_JSON] = json }
        }
    }

    fun saveCachedRelease(
        versionTag: String,
        changelog: String,
        apkUrl: String,
        apkSize: Long,
        checkTimestampMs: Long
    ) {
        _settings.value = _settings.value.copy(
            cachedReleaseVersionTag = versionTag,
            cachedReleaseChangelog = changelog,
            cachedReleaseApkUrl = apkUrl,
            cachedReleaseApkSize = apkSize,
            lastUpdateCheckTimestampMs = checkTimestampMs
        )
        scope.launch {
            appContext.nudgeDataStore.edit {
                it[Keys.CACHED_RELEASE_VERSION_TAG] = versionTag
                it[Keys.CACHED_RELEASE_CHANGELOG] = changelog
                it[Keys.CACHED_RELEASE_APK_URL] = apkUrl
                it[Keys.CACHED_RELEASE_APK_SIZE] = apkSize
                it[Keys.LAST_UPDATE_CHECK_TIMESTAMP_MS] = checkTimestampMs
            }
        }
    }

    fun setIsShizukuWatchdogEnabled(enabled: Boolean) {
        if (_settings.value.isShizukuWatchdogEnabled == enabled) return
        _settings.value = _settings.value.copy(isShizukuWatchdogEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.IS_SHIZUKU_WATCHDOG_ENABLED] = enabled }
        }
    }

    fun setIsMasterHapticsEnabled(enabled: Boolean) = setHapticsEnabled(enabled)

    fun setVolumeSliderPillTransparency(transparency: Int) {
        val clamped = transparency.coerceIn(0, 100)
        if (_settings.value.volumeSliderPillTransparency == clamped) return
        _settings.value = _settings.value.copy(volumeSliderPillTransparency = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.VOLUME_SLIDER_PILL_TRANSPARENCY] = clamped }
        }
    }

    fun setVolumeSliderBlur(blur: Float) {
        val clamped = blur.coerceIn(0f, 1f)
        if (_settings.value.volumeSliderBlur == clamped) return
        _settings.value = _settings.value.copy(volumeSliderBlur = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.VOLUME_SLIDER_BLUR] = clamped }
        }
    }

    fun setVolumeSliderPillBlurDp(blurDp: Float) {
        val clamped = blurDp.coerceIn(0f, 30f)
        if (_settings.value.volumeSliderPillBlurDp == clamped) return
        _settings.value = _settings.value.copy(volumeSliderPillBlurDp = clamped)
        requestLivePreview(PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.VOLUME_SLIDER_PILL_BLUR_DP] = clamped }
        }
    }

    fun setUseShizukuForDnd(enabled: Boolean) {
        if (_settings.value.useShizukuForDnd == enabled) return
        _settings.value = _settings.value.copy(useShizukuForDnd = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.USE_SHIZUKU_FOR_DND] = enabled }
        }
    }

    fun setEnablePersistentNudges(enabled: Boolean) {
        if (_settings.value.enablePersistentNudges == enabled) return
        _settings.value = _settings.value.copy(enablePersistentNudges = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ENABLE_PERSISTENT_NUDGES] = enabled }
        }
    }

    fun setPersistentNudges(nudges: List<PersistentNudge>) {
        val limited = nudges.take(10)
        _settings.value = _settings.value.copy(persistentNudges = limited)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.PERSISTENT_NUDGES_JSON] = serializePersistentNudges(limited) }
        }
    }

    fun addPersistentNudge(nudge: PersistentNudge) {
        val current = _settings.value.persistentNudges
        if (current.size >= 10) return
        val updated = current + nudge
        setPersistentNudges(updated)
    }

    fun updatePersistentNudge(nudge: PersistentNudge) {
        val current = _settings.value.persistentNudges
        val updated = current.map { if (it.id == nudge.id) nudge else it }
        setPersistentNudges(updated)
    }

    fun deletePersistentNudge(id: String) {
        val current = _settings.value.persistentNudges
        val updated = current.filterNot { it.id == id }
        setPersistentNudges(updated)
    }

    fun setNudgeWidthDp(width: Float) {
        val clamped = width.coerceIn(4f, 30f)
        if (_settings.value.nudgeWidthDp == clamped) return
        _settings.value = _settings.value.copy(nudgeWidthDp = clamped)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.NUDGE_WIDTH_DP] = clamped }
        }
    }

    fun setNudgeHeightDp(height: Float) {
        val clamped = height.coerceIn(40f, 140f)
        if (_settings.value.nudgeHeightDp == clamped) return
        _settings.value = _settings.value.copy(nudgeHeightDp = clamped)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.NUDGE_HEIGHT_DP] = clamped }
        }
    }

    fun setNudgeGlowEnabled(enabled: Boolean) {
        if (_settings.value.nudgeGlowEnabled == enabled) return
        _settings.value = _settings.value.copy(nudgeGlowEnabled = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.NUDGE_GLOW_ENABLED] = enabled }
        }
    }

    fun setNudgeMenuWidthDp(width: Float) {
        val clamped = width.coerceIn(20f, 100f)
        if (_settings.value.nudgeMenuWidthDp == clamped) return
        _settings.value = _settings.value.copy(nudgeMenuWidthDp = clamped)
        requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 1500L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.NUDGE_MENU_WIDTH_DP] = clamped }
        }
    }

    fun setNudgeMenuHeightDp(height: Float) {
        val clamped = height.coerceIn(180f, 600f)
        if (_settings.value.nudgeMenuHeightDp == clamped) return
        _settings.value = _settings.value.copy(nudgeMenuHeightDp = clamped)
        requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 1500L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.NUDGE_MENU_HEIGHT_DP] = clamped }
        }
    }

    fun setNudgeMenuXFraction(x: Float) {
        val clamped = x.coerceIn(0f, 0.5f)
        if (_settings.value.nudgeMenuXFraction == clamped) return
        _settings.value = _settings.value.copy(nudgeMenuXFraction = clamped)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.NUDGE_MENU_X_FRACTION] = clamped }
        }
    }

    fun setNudgeMenuYFraction(y: Float) {
        val clamped = y.coerceIn(0.1f, 0.9f)
        if (_settings.value.nudgeMenuYFraction == clamped) return
        _settings.value = _settings.value.copy(nudgeMenuYFraction = clamped)
        requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 1500L)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.NUDGE_MENU_Y_FRACTION] = clamped }
        }
    }

    fun setNudgeMenuEdge(edge: ScreenEdge) {
        if (_settings.value.nudgeMenuEdge == edge) return
        _settings.value = _settings.value.copy(nudgeMenuEdge = edge)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.NUDGE_MENU_EDGE] = edge.name }
        }
    }

    fun setEnableThinVolumeHud(enabled: Boolean) {
        if (_settings.value.enableThinVolumeHud == enabled) return
        _settings.value = _settings.value.copy(enableThinVolumeHud = enabled)
        requestLivePreview(if (enabled) PreviewTarget.THIN_HUD else PreviewTarget.HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ENABLE_THIN_VOLUME_HUD] = enabled }
        }
    }

    fun setThinHudEdgeInsetDp(insetDp: Float) {
        val clamped = NudgeSettings.clampThinHudEdgeInset(insetDp)
        if (_settings.value.thinHudEdgeInsetDp == clamped) return
        _settings.value = _settings.value.copy(thinHudEdgeInsetDp = clamped)
        requestLivePreview(PreviewTarget.THIN_HUD)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.THIN_HUD_EDGE_INSET_DP] = clamped }
        }
    }

    fun setThinHudOnLandscapeFullscreen(enabled: Boolean) {
        if (_settings.value.thinHudOnLandscapeFullscreen == enabled) return
        _settings.value = _settings.value.copy(thinHudOnLandscapeFullscreen = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.THIN_HUD_ON_LANDSCAPE_FULLSCREEN] = enabled }
        }
    }

    fun setEnableHudShowDelay(enabled: Boolean) {
        if (_settings.value.enableHudShowDelay == enabled) return
        _settings.value = _settings.value.copy(enableHudShowDelay = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.ENABLE_HUD_SHOW_DELAY] = enabled }
        }
    }

    fun setHudShowDelayMs(delayMs: Long) {
        val clamped = NudgeSettings.clampHudShowDelayMs(delayMs)
        if (_settings.value.hudShowDelayMs == clamped) return
        _settings.value = _settings.value.copy(hudShowDelayMs = clamped)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.HUD_SHOW_DELAY_MS] = clamped }
        }
    }

    fun setShowThinPerAppVolumeHud(enabled: Boolean) {
        if (_settings.value.showThinPerAppVolumeHud == enabled) return
        _settings.value = _settings.value.copy(showThinPerAppVolumeHud = enabled)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.SHOW_THIN_PER_APP_VOLUME_HUD] = enabled }
        }
    }

    fun setNudgeMenuApps(apps: List<NudgeMenuApp>) {
        val limited = apps.take(20)
        if (_settings.value.nudgeMenuApps == limited) return
        _settings.value = _settings.value.copy(nudgeMenuApps = limited)
        scope.launch {
            appContext.nudgeDataStore.edit { it[Keys.NUDGE_MENU_APPS_JSON] = serializeNudgeMenuApps(limited) }
        }
    }

    fun addNudgeMenuApp(app: NudgeMenuApp) {
        val current = _settings.value.nudgeMenuApps
        if (current.any { it.packageName == app.packageName }) return
        val updated = current + app
        setNudgeMenuApps(updated)
    }

    fun removeNudgeMenuApp(packageName: String) {
        val current = _settings.value.nudgeMenuApps
        val updated = current.filterNot { it.packageName == packageName }
        setNudgeMenuApps(updated)
    }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
        }
    }
}
