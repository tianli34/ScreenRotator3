package com.screenrotator3.app

/**
 * 进程内的“真实持锁状态”。
 *
 * [RotationPrefs] 记的是「用户上一次选了哪个方向」，它会跨进程死亡、覆盖安装、重启一直留着，
 * 因此**不能**当作“现在正锁着”来用。真正的锁只存在于 [RotationService] 持有的那个 1x1 悬浮窗上：
 * 进程一死、服务一停，窗口即消失，方向立刻回到系统默认。
 *
 * 所以这里用一个纯内存标记记录“当前确实由我们持有的方向”：
 * - 进程重启后天然为 null（这正是我们要的“什么都没生效”）；
 * - 只有 [RotationService] 在悬浮窗 attach/update 成功后才会写入它。
 *
 * 界面与分析磁贴都以它为准，避免出现“界面说已锁定、屏幕其实是竖屏”的谎话。
 */
object RotationRuntime {

    @Volatile
    var appliedMode: RotationMode? = null
        private set

    /** 悬浮窗已成功持有 [mode] 的方向请求。 */
    fun onApplied(mode: RotationMode) {
        appliedMode = mode
    }

    /** 悬浮窗已被移除（切自动、服务退出、窗口创建失败）。 */
    fun onReleased() {
        appliedMode = null
    }

    /** [mode] 此刻是否真的被悬浮窗持有。 */
    fun isHolding(mode: RotationMode): Boolean = appliedMode == mode
}
