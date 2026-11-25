package com.nudge.edge.service

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.function.Consumer
import kotlin.math.roundToInt

object OverlayBlurController {
    private var semBlurInfoClass: Class<*>? = null
    private var semBuilderConstructor: Constructor<*>? = null
    private var semSetRadiusMethod: Method? = null
    private var semSetBackgroundColorMethod: Method? = null
    private var semSetCornerRadiusMethod: Method? = null
    private var semSetCornerRadius4Method: Method? = null
    private var semBuildMethod: Method? = null
    private var viewSemSetBlurInfoMethod: Method? = null

    private var isPathAAvailable: Boolean = false
    private var isInitialized: Boolean = false

    private var aospBlurEnabledListener: Consumer<Boolean>? = null
    private var isAospCrossWindowBlurActive: Boolean = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        runCatching {
            val blurInfoCls = Class.forName("android.view.SemBlurInfo")
            val builderCls = Class.forName("android.view.SemBlurInfo\$Builder")
            val ctor = builderCls.getConstructor(Int::class.javaPrimitiveType)
            val setRadius = builderCls.getMethod("setRadius", Int::class.javaPrimitiveType)
            val setBgColor = builderCls.getMethod("setBackgroundColor", Int::class.javaPrimitiveType)
            val setCorner1 = runCatching {
                builderCls.getMethod("setBackgroundCornerRadius", Float::class.javaPrimitiveType)
            }.getOrNull()
            val setCorner4 = if (setCorner1 == null) {
                runCatching {
                    builderCls.getMethod(
                        "setBackgroundCornerRadius",
                        Float::class.javaPrimitiveType,
                        Float::class.javaPrimitiveType,
                        Float::class.javaPrimitiveType,
                        Float::class.javaPrimitiveType
                    )
                }.getOrNull()
            } else null
            val build = builderCls.getMethod("build")
            val viewMethod = View::class.java.getMethod("semSetBlurInfo", blurInfoCls)

            semBlurInfoClass = blurInfoCls
            semBuilderConstructor = ctor
            semSetRadiusMethod = setRadius
            semSetBackgroundColorMethod = setBgColor
            semSetCornerRadiusMethod = setCorner1
            semSetCornerRadius4Method = setCorner4
            semBuildMethod = build
            viewSemSetBlurInfoMethod = viewMethod
            isPathAAvailable = true
        }.onFailure {
            isPathAAvailable = false
        }

        if (!isPathAAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm != null) {
                isAospCrossWindowBlurActive = runCatching { wm.isCrossWindowBlurEnabled }.getOrDefault(false)
                val listener = Consumer<Boolean> { enabled ->
                    isAospCrossWindowBlurActive = enabled
                }
                aospBlurEnabledListener = listener
                runCatching {
                    wm.addCrossWindowBlurEnabledListener(ContextCompat.getMainExecutor(context), listener)
                }
            }
        }
    }

    fun isSamsungBackendActive(): Boolean = isPathAAvailable

    fun isSamsungReduceTransparencyEnabled(context: Context): Boolean {
        return runCatching {
            Settings.System.getInt(context.contentResolver, "accessibility_reduce_transparency", 0) == 1 ||
                Settings.Global.getInt(context.contentResolver, "accessibility_reduce_transparency", 0) == 1
        }.getOrDefault(false)
    }

    fun isRuntimeBlurActive(context: Context): Boolean {
        init(context)
        if (isPathAAvailable) {
            return !isSamsungReduceTransparencyEnabled(context)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            return wm?.let { runCatching { it.isCrossWindowBlurEnabled }.getOrDefault(false) } ?: isAospCrossWindowBlurActive
        }
        return false
    }

    fun computeBlurRadiusPx(blurFraction: Float, density: Float): Int {
        if (blurFraction <= 0.001f) return 0
        return (blurFraction * 40f * density).roundToInt().coerceIn(1, 150)
    }

    fun applySamsungTrackBlur(
        view: View,
        blurRadiusPx: Int,
        cornerRadiusPx: Float,
        isHudVisible: Boolean,
        widthPx: Float = 0f,
        heightPx: Float = 0f
    ) {
        if (!isPathAAvailable) return
        val method = viewSemSetBlurInfoMethod ?: return
        if (!isHudVisible || blurRadiusPx <= 0 || isSamsungReduceTransparencyEnabled(view.context)) {
            runCatching { method.invoke(view, null) }
            return
        }

        val effW = if (view.width > 0) view.width.toFloat() else widthPx
        val effH = if (view.height > 0) view.height.toFloat() else heightPx

        val maxCorner = if (effW > 0f && effH > 0f) minOf(effW, effH) / 2f else cornerRadiusPx
        val clampedCorner = cornerRadiusPx.coerceAtMost(maxCorner)

        if (clampedCorner > 0f) {
            view.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, outline: android.graphics.Outline) {
                    val w = if (v.width > 0) v.width else effW.toInt().coerceAtLeast(1)
                    val h = if (v.height > 0) v.height else effH.toInt().coerceAtLeast(1)
                    outline.setRoundRect(0, 0, w, h, clampedCorner)
                }
            }
            view.clipToOutline = true
        }

        runCatching {
            val builder = semBuilderConstructor?.newInstance(0) ?: return
            semSetRadiusMethod?.invoke(builder, blurRadiusPx)
            semSetBackgroundColorMethod?.invoke(builder, Color.TRANSPARENT)
            if (semSetCornerRadiusMethod != null) {
                semSetCornerRadiusMethod?.invoke(builder, clampedCorner)
            } else if (semSetCornerRadius4Method != null) {
                semSetCornerRadius4Method?.invoke(builder, clampedCorner, clampedCorner, clampedCorner, clampedCorner)
            }
            val info = semBuildMethod?.invoke(builder) ?: return
            method.invoke(view, info)
        }.onFailure {
            runCatching { method.invoke(view, null) }
        }

        if (view.width <= 0 || view.height <= 0) {
            val listener = object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int
                ) {
                    if (v.width > 0 && v.height > 0) {
                        v.removeOnLayoutChangeListener(this)
                        applySamsungTrackBlur(v, blurRadiusPx, cornerRadiusPx, isHudVisible, v.width.toFloat(), v.height.toFloat())
                    }
                }
            }
            view.addOnLayoutChangeListener(listener)
        }
    }

    fun clearSamsungTrackBlur(view: View) {
        if (!isPathAAvailable) return
        val method = viewSemSetBlurInfoMethod ?: return
        runCatching { method.invoke(view, null) }
    }

    fun teardown(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && aospBlurEnabledListener != null) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            runCatching {
                aospBlurEnabledListener?.let { wm?.removeCrossWindowBlurEnabledListener(it) }
            }
            aospBlurEnabledListener = null
        }
    }
}
