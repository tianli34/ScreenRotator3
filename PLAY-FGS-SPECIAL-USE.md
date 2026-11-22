# Play 上架：FOREGROUND_SERVICE_SPECIAL_USE 声明 + 演示视频脚本

对应对象：`com.screenrotator3.app` v1.0 (versionCode 2)，targetSdk 36。
清单里的声明：`android:foregroundServiceType="specialUse"` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`。

---

## 一、下拉选项怎么选

**选「其他 / Other」。**

表单里那些分类（导航、通话、设备管理一类）都对应系统内置的 FGS 类型，屏幕方向锁定不在其中。
「其他」不是兜底糊弄——specialUse 本来就是给「确实没有内置类型可用、但用户能感知到」的场景留的口子，
本应用正好是这种场景。**关键是用下面的文字把「为什么只能是 specialUse」说清楚。**

若下拉里出现更贴切的项（例如设备控制 / device control 一类），选它也可以，
但说明文字仍然照抄下面这份——文字比分类更能决定审核结论。

---

## 二、「其他」说明文案（英文，直接粘贴）

### 标准版（推荐，约 480 字符）

```
Screen Rotation locks the screen to a user-chosen orientation across all apps. The foreground
service keeps a 1x1, fully transparent, non-touchable overlay window attached; the window's
screenOrientation parameter is what requests the rotation. The task is user-perceivable: the
screen stays in the chosen orientation in every app, and an ongoing notification is always
shown. No built-in foreground service type applies (no media, location, camera or data
transfer). The service stops as soon as the user selects Auto or swipes the app away.
```

### 短版（字段有字数上限时用）

```
The app locks the screen to a user-chosen orientation (portrait / landscape / reverse) across all
apps. A foreground service must keep a 1x1 fully transparent, non-touchable overlay window
attached, because that window's screenOrientation parameter is what requests the rotation. The
result is user-perceivable: the screen keeps that orientation everywhere and an ongoing
notification is always shown. No built-in FGS type fits. The service stops when the user selects
Auto.
```

### 补充说明（若表单有 "Notes for reviewer" 之类的可选框，填这段）

```
How the user starts it: only by an explicit tap — a direction card in the app, one of the five
buttons in the ongoing notification, the Quick Settings tile, or the floating ball. There is no
automatic or background start.

Why it must be a foreground service: the rotation request lives on a WindowManager overlay that
must stay attached while the user is in other apps. Without a foreground service the process is
cached and the window is dropped, i.e. the feature cannot work at all.

What it does NOT do: no screen capture or recording, no input injection or coordinate remapping,
no accessibility service, no collection of personal data. The overlay is 1x1 px, window alpha 0,
FLAG_NOT_TOUCHABLE + FLAG_NOT_FOCUSABLE, so it never covers content or intercepts touches.

When it stops: selecting "Auto", turning off the notification bar control and the floating ball,
swiping the app out of Recents (onTaskRemoved), or if the overlay permission is missing.
```

> 三条硬规矩：**不提「保活 / 防止被杀 / 后台常驻」**、不夸大能力、不承诺其他类型也能实现。
> 审核最反感的就是把 specialUse 当后台存活的后门。

---

## 三、演示视频（已录制）

**文件：`store_assets/video/ScreenRotation-specialUse-demo.mp4`**
1080×2400，73 秒，28 MB，无音轨，一条连续录屏（未剪辑、未拼接）。

录制对象就是上架用的那个包：versionCode 2 / targetSdk 36，upload key 签名，先 `pm clear` 清到
干净状态再录。应用内语言切到 English；系统级界面（通知栏、计算器）是中文——那段只需要看
"通知还在、方向变了"，不影响判断。系统「显示触摸位置」开着，所以每次点击都有一枚小白点。

| 时间 | 屏幕上的事 | 对应审核要点 |
|---|---|---|
| 0:00–0:05 | 应用主界面，五张方向卡片，胶囊显示 `Following system`，通知栏干净 | 起始状态：没有服务、没有锁定 |
| 0:05–0:12 | 点 **Landscape** 卡片 → 屏幕当场转成横屏，胶囊变成 `Landscape · Locked`，状态栏出现常驻通知图标 | **用户主动触发**，服务由此启动 |
| 0:13–0:29 | 右上角菜单 → 打开 `Notification bar` 开关 → 关掉菜单；再下拉通知栏，通知里出现一排五个方向按钮 | 常驻通知是真实控制面，不是装饰 |
| 0:29–0:40 | 打开系统**计算器**（另一个应用）→ 计算器跟着横屏 | **user-perceivable**：用户不在本应用里，效果依然持续 |
| 0:40–0:46 | 通知栏里点 **Portrait** → 计算器当场转回竖屏 | 通知里的按钮立刻生效 |
| 0:46–1:00 | 收起通知栏 → 回到应用 → 点 **Auto** → 通知消失，胶囊回到 `Following system` | **及时停止**，不是拿它换后台常驻 |
| 1:00–1:13 | Auto 状态下屏幕自己转横又转回竖 | 方向决定权已交还系统 |

几点刻意的取舍：

- **没有录「授予悬浮窗权限」那一段。** HyperOS 的权限页是一长串应用列表，要滚好几屏才找到本应用，
  剪进去节奏很差；权限在录制前由 `adb appops set … SYSTEM_ALERT_WINDOW allow` 预授予。
  若审核追问权限来源，回答口径见第四节第 1 条——应用内菜单第一行就是授权入口。
- **横屏时的画面在成片里是「躺着的」。** 录屏画布固定 1080×2400，屏幕转 90° 后内容在画布里
  跟着转，这是手机录屏的正常表现（内容原地转了，正是要证明的事）。用手机看是正的。
- **用的是系统计算器当「另一个应用」**，不用浏览器：不联网、无广告、无第三方内容，
  也不会把任何与产品无关的东西带进画面。

### 上传时填的英文简介（可直接粘给 YouTube）

```
Screen Rotation (foreground service, type specialUse) — how the app uses
FOREGROUND_SERVICE_SPECIAL_USE.

0:00  App idle: no service running, orientation follows the system.
0:05  User taps "Landscape": the foreground service starts, the screen rotates,
      and an ongoing notification appears.
0:13  User enables the notification control bar; the notification shows five
      one-tap direction buttons.
0:29  Another app (the system Calculator) opens in landscape — the task keeps
      running while the user is not interacting with our app.
0:40  Tapping "Portrait" in the ongoing notification rotates the Calculator back.
1:00  Tapping "Auto" releases the lock: the notification disappears and the
      system decides the orientation again.

No screen capture, no input interception, no data collection.
```

### 如果真要重录

- 一条连续录屏，不要拼接；拼接版多数会被要求重录。
- 录前清空通知栏（尤其银行、短信这类私人通知），关掉 DND 之外的弹窗来源。
- `adb shell settings put system show_touches 1` 让点击可见，录完记得设回 0。
- 上传 YouTube 设为 **Unlisted（不公开）**，把链接填进表单；Private 审核打不开。
- 控制在 1–2 分钟。超过 3 分钟，审核容易只看前 30 秒就下结论。

> 录制脚本留在 `.workbuddy/tmp/take5.sh`（配合同目录 `uidriver.py`），改几行就能重跑：
> 它做的是 `pm clear` → 授予权限 → 切英文 → 起录 → 点横屏 → 开控制条 → 开计算器 →
> 通知里点竖屏 → 回应用点 Auto → 用 `user_rotation` 演示系统接管。

---

## 四、一致性检查（这几处对不上是常见的退回原因）

1. **清单 property 文案**要和上面说明同义。当前值：
   `Keeps a fully transparent, non-touchable overlay window used to request a user-selected screen orientation. No screen capture, no input interception.`
   —— 已经对齐，不用改。
2. **隐私政策**（`privacy_policy/index.html`）里要写明：使用「显示在其他应用上层」与前台服务、
   用途是方向控制、不采集数据、不做录屏。Play 会交叉比对。
3. **Data safety** 填「不收集、不共享任何数据」——与说明里「no collection of personal data」一致。
4. **应用描述**里不要写「强制横屏」「破解方向锁」这类词；写「lock / keep the orientation you choose」。
   方向锁定不是绕过其他应用的限制，措辞要落在「用户自己的选择」上。
5. 若这次是**更新已有应用**才被要求声明：确认 `versionCode` 已递增（当前 2），审核看的是新上传的包。

---

## 五、被退回时的应对

- 若理由含糊（"insufficient justification"）：不要重写整套方案，只把**第二段补充说明**里
  「Why it must be a foreground service」那一小节加粗式地再强调一次，并补一段视频：
  在没开服务的情况下切到别的应用，屏幕方向被系统改回 —— 用「不这么做就不成立」反击。
- 若被指「可用其他方式实现」：明确回答尝试过的替代方案都不可行 ——
  `WRITE_SETTINGS` 只能改用户旋转偏好，应用的显式方向请求优先级更高；
  只锁自己 Activity 的方向在离开应用后失效。这两条写进说明里比空口辩解有用。
- 坚持 specialUse，不要为了过审改成 `dataSync` / `mediaPlayback` 之类 —— 类型与行为不符会被判
  声明不实，代价比退回一次大得多。

---

## 六、若审核坚持不认可 specialUse（备选方案）

作为最后手段，可以放弃 specialUse 路线，改走「无障碍服务」或「设备管理器」——
但两者都要求用户在系统设置里做更重的手动授权，体验明显更差，且各自的审核门槛也不低。
优先把上面的材料补齐，这条路一般不必走。
