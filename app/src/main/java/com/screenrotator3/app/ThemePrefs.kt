package com.screenrotator3.app

import android.content.Context

/**
 * 外观模式。默认跟随系统：夜间使用最深灰底（#121212）+ 高对比卡片，
 * 白天用浅色，避免“关灯玩手机”时被纯白界面刺到。
 */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

object ThemePrefs {
    private const val FILE = "appearance"
    private const val KEY_MODE = "mode"

    fun load(context: Context): ThemeMode = ThemeMode.fromKey(
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_MODE, null)
    )

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.key)
            .apply()
    }
}

// 覆盖 uiMode 的逻辑搬到了 DisplayContext：那里同时写 uiMode 与 locale，
// 并且被 Activity 与 Service 共用，避免两套包装各改一半。
