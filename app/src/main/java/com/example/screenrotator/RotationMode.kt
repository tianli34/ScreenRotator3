package com.example.screenrotator

import android.content.pm.ActivityInfo

/**
 * 支持的方向模式。
 *
 * [screenOrientation] 为写入 [android.view.WindowManager.LayoutParams.screenOrientation] 的值；
 * [AUTO] 表示不提交方向请求（移除悬浮窗，交还给系统决定）。
 */
enum class RotationMode(val key: String, val screenOrientation: Int?) {
    AUTO("auto", null),
    PORTRAIT("portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LANDSCAPE("landscape", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    REVERSE_PORTRAIT("reverse_portrait", ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    REVERSE_LANDSCAPE("reverse_landscape", ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);

    companion object {
        fun fromKey(key: String?): RotationMode =
            entries.firstOrNull { it.key == key } ?: AUTO
    }
}
