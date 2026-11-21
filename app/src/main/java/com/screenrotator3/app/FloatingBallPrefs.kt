package com.screenrotator3.app

import android.content.Context

/** 悬浮球开关。开启后即使方向是「自动」，服务也会常驻以承载悬浮球。 */
object FloatingBallPrefs {
    private const val FILE = "floating_ball"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
