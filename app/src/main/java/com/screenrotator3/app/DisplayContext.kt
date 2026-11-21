package com.screenrotator3.app

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration

/**
 * 进程内唯一的「界面配置」包装器：把外观（uiMode）与语言（locale）一次写进 Configuration。
 *
 * 两条铁律：
 * 1. **返回的 Context 没有主题**。它只用来取字符串与颜色；布局必须继续用
 *    `Activity.layoutInflater`，Dialog 必须用 Activity 作 context，否则会丢样式。
 * 2. **不要放进热路径**。每次调用都会在 ResourcesManager 里生成一套 Resources，
 *    只在 Activity 创建时、或低频刷新（磁贴 onStartListening）时调。
 *
 * 只接受「原始 Context」：Activity/Service 的 attachBaseContext 参数，或 applicationContext。
 * 重复传入包装结果也不会叠成多层——内部会先剥回原始 base 再重新算。
 */
object DisplayContext {

    /** 标记层：只为识别「已经包过一次」，不承载任何逻辑。 */
    private class Wrapped(base: Context) : ContextWrapper(base)

    /**
     * 给 Activity 用：外观 + 语言。
     *
     * 两项都跟随系统时原样返回，交给系统处理（也顺手剥掉旧的包装层）。
     */
    fun wrap(base: Context): Context = apply(base, withTheme = true)

    /**
     * 给 Service / TileService / Toast 用：只覆盖 locale。
     *
     * 故意不碰 uiMode：通知的 accent 色取自 R.color.accent，values-night 与 values 不同。
     * 服务若也跟着应用内主题走，切主题时通知会跟着变色，那属于「方向锁定」之外的副作用。
     */
    fun wrapLocale(base: Context): Context = apply(base, withTheme = false)

    private fun apply(base: Context, withTheme: Boolean): Context {
        val raw = if (base is Wrapped) base.baseContext else base
        val theme = if (withTheme) ThemePrefs.load(raw) else ThemeMode.SYSTEM
        val language = LanguagePrefs.load(raw)

        if (theme == ThemeMode.SYSTEM && language == AppLanguage.SYSTEM) return raw

        val config = Configuration(raw.resources.configuration)

        if (theme != ThemeMode.SYSTEM) {
            val night = if (theme == ThemeMode.DARK) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            // 只改 NIGHT 位，保留 UI_MODE_TYPE_*（车机 / 手表 / 电视）与其它位
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }

        // setLocale 会顺带把 layoutDirection 设成该语言的方向。中英文都是 LTR；
        // 将来加阿语/希语时布局方向才会真正翻转（清单已 supportsRtl=true）。
        language.locale?.let(config::setLocale)

        return Wrapped(raw.createConfigurationContext(config))
    }
}
