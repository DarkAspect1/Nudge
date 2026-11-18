package com.nudge.edge.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LivePreviewState(
    val activeTarget: PreviewTarget = PreviewTarget.NONE,
    val isLandscapePreview: Boolean = false,
    val overrideWidthDp: Float? = null,
    val overrideHeightDp: Float? = null,
    val overrideOffsetPercent: Float? = null,
    val overrideCornerRadiusDp: Float? = null,
    val overrideUnfocusedOffsetDp: Float? = null
)

object LivePreviewController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoHideJob: Job? = null

    private val _state = MutableStateFlow(LivePreviewState())
    val state: StateFlow<LivePreviewState> = _state.asStateFlow()

    fun onSliderDrag(
        target: PreviewTarget,
        isLandscape: Boolean = false,
        update: (LivePreviewState) -> LivePreviewState = { it }
    ) {
        autoHideJob?.cancel()
        val base = if (_state.value.activeTarget == target && _state.value.isLandscapePreview == isLandscape) {
            _state.value
        } else {
            LivePreviewState(activeTarget = target, isLandscapePreview = isLandscape)
        }
        _state.value = update(base.copy(activeTarget = target, isLandscapePreview = isLandscape))
        autoHideJob = scope.launch {
            delay(4000L)
            _state.value = LivePreviewState(activeTarget = PreviewTarget.NONE)
        }
    }

    fun onSliderRelease(holdDurationMs: Long = 800L) {
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(holdDurationMs)
            _state.value = LivePreviewState(activeTarget = PreviewTarget.NONE)
        }
    }

    fun clear() {
        autoHideJob?.cancel()
        _state.value = LivePreviewState(activeTarget = PreviewTarget.NONE)
    }
}
