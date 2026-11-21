package com.screenrotator3.app

import android.content.Context

/** 记录用户最后一次选择的方向，供服务被系统重启后恢复。 */
object RotationPrefs {
    private const val FILE = "rotation"
    private const val KEY_MODE = "mode"

    fun save(context: Context, mode: RotationMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.key)
            .apply()
    }

    fun load(context: Context): RotationMode =
        RotationMode.fromKey(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        )
}
