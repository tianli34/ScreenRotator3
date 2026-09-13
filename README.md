# 屏幕旋转（ScreenRotator）

无 Root 的屏幕方向控制工具：前台服务 + 透明悬浮窗，通过 `WindowManager` 的方向仲裁请求屏幕方向。
**仅面向 Android 12（API 31）及以上。**

## 原理

普通无 Root 应用没有能保证覆盖所有应用、所有系统界面的通用强制旋转 API。本工具的做法是：

1. 申请 `SYSTEM_ALERT_WINDOW`（用户在系统设置中开启“显示在其他应用上层”）。
2. 前台服务维护一个 1x1、`TYPE_APPLICATION_OVERLAY` 的悬浮窗口：
   - **窗口级** `alpha = 0`（不是把 View 设为 `INVISIBLE` / `GONE`，后者会让方向请求失效）；
   - `FLAG_NOT_TOUCHABLE` + `FLAG_NOT_FOCUSABLE`，不接收触摸、不抢焦点，不截获输入；
   - 窗口极小且完全透明，满足 Android 12+ 对不可信覆盖层的遮挡触摸拦截规则。
3. 方向请求写在 `WindowManager.LayoutParams.screenOrientation` 上：

   | 目标 | 常量 |
   |---|---|
   | 竖屏 | `SCREEN_ORIENTATION_PORTRAIT` |
   | 横屏 | `SCREEN_ORIENTATION_LANDSCAPE` |
   | 反向竖屏 | `SCREEN_ORIENTATION_REVERSE_PORTRAIT` |
   | 反向横屏 | `SCREEN_ORIENTATION_REVERSE_LANDSCAPE` |

   切换方向时更新同一个窗口的布局参数；切到“自动”时移除窗口，把方向决定权交还系统。

不涉及屏幕录制、画面变换、触摸坐标变换或输入注入。系统旋转显示时会自行处理输入坐标变换。

## 为什么不用其他方式

- 只关自动旋转 / 改 `USER_ROTATION`：只改变用户旋转偏好，应用的显式方向请求仍可能优先。
- 只设置自己 Activity 的方向：通常只影响自己的界面。
- 因此本方案不依赖 `WRITE_SETTINGS`。

## 使用

1. 打开应用，按提示授予“显示在其他应用上层”权限。
2. Android 13+ 首次启用时会请求通知权限（前台服务需要）。
3. 选择方向；通知栏常驻通知可快捷切换或切回“自动”。

## 构建

需要 JDK 17+ 与 Android SDK（compileSdk 35、build-tools 35）。

```bash
./gradlew assembleDebug
```

若 Gradle 分发包下载缓慢，可改用镜像（例如 `https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip`）
替换 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl`。

## 已知限制

- 覆盖范围取决于 Android 版本与厂商系统：部分强制方向的应用、锁屏、部分系统界面可能不受影响。
- Android 14+ 起应用了 `specialUse` 前台服务类型，上架 Play 需要提交该类型的使用说明。
- Android 12+ 对后台启动前台服务有限制，本工具的方向切换由用户在前台或通知栏主动触发。
