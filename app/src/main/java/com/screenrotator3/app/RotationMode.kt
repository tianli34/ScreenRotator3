package com.screenrotator3.app

import android.content.pm.ActivityInfo
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * 支持的方向模式。
 *
 * [screenOrientation] 为写入 [android.view.WindowManager.LayoutParams.screenOrientation] 的值；
 * [AUTO] 表示不提交方向请求（移除悬浮窗，交还给系统决定）。
 *
 * [glyphRotation] 用于图标：以“竖屏直立”为 0°，图标跟着真实手机姿态一起转，
 * 这样五个图标在视觉上互相之间只有旋转关系，用户靠“摄像头在哪一侧”就能分辨。
 *
 * [tileIconRes] 是快捷设置磁贴用的矢量图。系统只给磁贴 24dp 的图标位、并会整体染色，
 * 拿不到 [GlyphPainter] 那套运行时绘制，所以每个方向配一张同几何的静态图 ——
 * 磁贴、通知栏控制条、主界面卡片因此共用同一套「手机 + 摄像头圆点」图案。
 */
enum class RotationMode(
    val key: String,
    val screenOrientation: Int?,
    @StringRes val labelRes: Int,
    @StringRes val taglineRes: Int,
    val glyphRotation: Float,
    @DrawableRes val tileIconRes: Int
) {
    AUTO(
        "auto", null,
        R.string.mode_auto, R.string.tagline_auto, 0f,
        R.drawable.ic_phone_auto
    ),
    PORTRAIT(
        "portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        R.string.mode_portrait, R.string.tagline_portrait, 0f,
        R.drawable.ic_phone_portrait
    ),
    LANDSCAPE(
        "landscape", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        R.string.mode_landscape, R.string.tagline_landscape, -90f,
        R.drawable.ic_phone_landscape
    ),
    REVERSE_PORTRAIT(
        "reverse_portrait", ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        R.string.mode_reverse_portrait, R.string.tagline_reverse_portrait, 180f,
        R.drawable.ic_phone_reverse_portrait
    ),
    REVERSE_LANDSCAPE(
        "reverse_landscape", ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        R.string.mode_reverse_landscape, R.string.tagline_reverse_landscape, 90f,
        R.drawable.ic_phone_reverse_landscape
    );

    val isAuto: Boolean get() = this == AUTO

    /** 目标是否为横向（用于把偏好和屏幕实际朝向做一次交叉校验）。 */
    val isLandscape: Boolean get() = this == LANDSCAPE || this == REVERSE_LANDSCAPE

    companion object {
        fun fromKey(key: String?): RotationMode =
            entries.firstOrNull { it.key == key } ?: AUTO

        /** 四个固定方向，界面 2×2 网格的展示顺序。 */
        val fixedModes: List<RotationMode> = listOf(
            PORTRAIT, REVERSE_PORTRAIT, LANDSCAPE, REVERSE_LANDSCAPE
        )

        /**
         * 顺序上的下一个方向：自动 → 竖屏 → 横屏 → 反向竖屏 → 反向横屏 → 自动。
         *
         * 正常路径（磁贴弹出选择窗）用不到它，只在选择窗拉不起来时作为兜底沿用。
         */
        fun next(mode: RotationMode): RotationMode {
            val order = entries
            return order[(order.indexOf(mode) + 1) % order.size]
        }
    }
}
