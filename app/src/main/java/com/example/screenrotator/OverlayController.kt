package com.example.screenrotator

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 通过 WindowManager 的方向仲裁请求屏幕方向。
 *
 * 关键点（与实现方案一致）：
 * - 使用 TYPE_APPLICATION_OVERLAY（Android 8.0+），由系统设置中的“显示在其他应用上层”授权。
 * - 窗口极小（1x1）且**窗口级** alpha = 0，满足 Android 12+ 对不可信覆盖层的不透明度要求，
 *   避免触发遮挡触摸拦截。
 * - FLAG_NOT_TOUCHABLE + FLAG_NOT_FOCUSABLE：不接收触摸、不抢焦点，因此不截获输入。
 *   仅使用 FLAG_NOT_TOUCH_MODAL 是不够的，它只放行窗口范围外的触摸。
 * - **绝不能**把 view 设为 INVISIBLE / GONE，否则窗口会被视为不可见而失去方向请求能力；
 *   透明是通过 WindowManager.LayoutParams.alpha 实现的，而不是靠隐藏 view。
 * - 方向请求写在 WindowManager.LayoutParams.screenOrientation 上，切换时更新同一个窗口的参数，
 *   而不是改 USER_ROTATION 或只改自己 Activity 的方向。
 * - 系统旋转显示时会同步处理输入坐标变换，本类不参与任何坐标变换、录屏或注入。
 */
class OverlayController(context: Context) {

    private val appContext: Context = context.applicationContext
    private val windowManager: WindowManager =
        appContext.getSystemService(WindowManager::class.java)

    @Volatile
    private var overlayView: View? = null

    @Volatile
    private var currentOrientation: Int? = null

    val isAttached: Boolean
        get() = overlayView != null

    /**
     * 应用方向模式：[RotationMode.AUTO] 表示移除窗口、交还系统决定。
     *
     * @return 是否成功持有方向请求（false 通常表示缺少悬浮窗权限）
     */
    fun apply(mode: RotationMode): Boolean {
        val orientation = mode.screenOrientation
        if (orientation == null) {
            detach()
            return false
        }
        if (!Settings.canDrawOverlays(appContext)) {
            Log.w(TAG, "缺少 SYSTEM_ALERT_WINDOW 权限，无法创建方向控制窗口")
            detach()
            return false
        }

        val view = overlayView
        if (view == null) {
            return attach(orientation)
        }
        if (currentOrientation == orientation) {
            return true
        }
        return update(view, orientation)
    }

    /** 移除窗口，让系统按前台应用与用户设置决定方向。 */
    fun detach() {
        val view = overlayView ?: return
        overlayView = null
        currentOrientation = null
        runCatching { windowManager.removeView(view) }
            .onFailure { Log.w(TAG, "移除方向控制窗口失败", it) }
    }

    @SuppressLint("RtlHardcoded")
    private fun attach(orientation: Int): Boolean {
        // 内容本身也保持透明，但真正的“完全透明”依赖窗口级 alpha
        val view = View(appContext).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        return try {
            windowManager.addView(view, createParams(orientation))
            overlayView = view
            currentOrientation = orientation
            true
        } catch (t: Throwable) {
            Log.w(TAG, "添加方向控制窗口失败", t)
            false
        }
    }

    private fun update(view: View, orientation: Int): Boolean {
        return try {
            val params = view.layoutParams as WindowManager.LayoutParams
            params.screenOrientation = orientation
            windowManager.updateViewLayout(view, params)
            currentOrientation = orientation
            true
        } catch (t: Throwable) {
            Log.w(TAG, "更新方向控制窗口失败，尝试重建窗口", t)
            detach()
            attach(orientation)
        }
    }

    private fun createParams(orientation: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            // 尽量小，避免不必要的覆盖区域
            width = 1
            height = 1
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
            format = PixelFormat.TRANSPARENT
            // 窗口级完全透明：Android 12+ 的遮挡触摸拦截基于窗口不透明度
            alpha = 0f
            flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            screenOrientation = orientation
        }

    private companion object {
        const val TAG = "OverlayController"
    }
}
