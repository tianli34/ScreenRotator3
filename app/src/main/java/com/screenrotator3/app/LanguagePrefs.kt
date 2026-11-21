package com.screenrotator3.app

import android.content.Context
import java.util.Locale

/**
 * 应用内语言。
 *
 * [key] 是落盘标识，稳定不变（`system` / `en` / `zh`），与界面文案彻底分离；
 * [locale] 为 null 表示「跟随系统」——完全不碰 Configuration 里的 locale。
 *
 * 简体中文用 zh-CN 而不是裸 zh：现在只有 values-zh 时 zh-CN 会正常回落到它；
 * 将来加了 values-zh-rTW，选「简体中文」也不会被繁体资源抢走。
 *
 * 新增语种：加一项枚举 + 一份 values-xx/strings.xml + 设置面板语言段加一段。
 */
enum class AppLanguage(val key: String, val locale: Locale?) {
    SYSTEM("system", null),
    ENGLISH("en", Locale.ENGLISH),
    CHINESE("zh", Locale.SIMPLIFIED_CHINESE);

    companion object {
        fun fromKey(key: String?): AppLanguage =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * 语言偏好。与 [ThemePrefs] 一样一个文件一个 object，不引入新依赖。
 *
 * 用 apply() 落盘：写入先发生在内存里，紧接着的 recreate() 一定读得到新值。
 */
object LanguagePrefs {
    private const val FILE = "language"
    private const val KEY_LANGUAGE = "language"

    fun load(context: Context): AppLanguage = AppLanguage.fromKey(
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
    )

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.key)
            .apply()
    }
}
