package com.screenrotator3.app

import android.content.Context

/**
 * 「通知栏控制条」开关。
 *
 * 关掉之后服务照常运行（方向锁定 / 悬浮球还需要它），但通知会退化成最朴素的一条文本，
 * 不再挂那一排五个方向按钮。前台服务的通知是系统强制要求的，所以「关」只能关掉控制条，
 * 不能连通知一起消失。
 *
 * 默认关闭：控制条是要用户主动打开才出现的功能，也让「开关 = 通知栏里有没有那排按钮」
 * 保持字面一致，不做静态的假象。
 */
object NotificationBarPrefs {

    private const val FILE = "notification_bar"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_REQUESTED = "permission_requested"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * 是否已经向系统申请过一次通知权限。
     *
     * Android 13+ 的运行时弹窗最多出现两次，之后 `requestPermissions` 会静默返回失败。
     * 用这个标记区分「还没问过」和「问过但被拒」，后者要直接引导去系统设置。
     */
    fun hasRequested(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUESTED, false)

    fun markRequested(context: Context) {
        prefs(context).edit().putBoolean(KEY_REQUESTED, true).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
