# 字隅 Compose · 项目记忆

最后更新：2026-10-27  
应用源码：`D:\deep\moyu-compose`  
包名：`com.moyu.writer`（不改，保留数据/签名）  
版本：3.2.12 / versionCode 111  
最新 APK：`D:\deep\字隅-Compose-v3.2.12.apk`（7,849,980 B）  
SHA-256：`ADBCD4CD3D5CEC9224DDE010C698C2932721744EC4C117D34AEE5FC410E55277`

## 目标映射

| 用户要求 | 实现 |
| --- | --- |
| Compose、极简、ChatGPT 感 | 纯 Jetpack Compose 单模块，纸白/雾蓝/纯白/暖褐/夜间/跟随系统 6 套主题，留白、分组卡片、统一 24–30dp 圆角 |
| iOS 26 液态玻璃 TabBar/按钮/对话框 | `Design.kt` 单层半透明渐变、边缘折射、镜面高光、静态颗粒；TabBar 在 API31+ 通过本地 Haze 1.7.3 + Java 桥做真实 `RenderEffect` 背景模糊；dock/卡片/对话框统一单层材质，不叠底色/阴影/内暗带；Android 12+ Dialog 仍尝试窗口模糊 |
| 动效灵动、多种可用 | 4 档手感（灵动/柔和/迅捷/关闭）× 3 种转场（滑动/淡入/缩放）；Tab 胶囊位移、按压缩放、页面滑入淡入、流光开关、触感开关；关闭动效时同步停流光；根部 tab 转场按 tabIndex 双向 |
| 120Hz | `HighRefresh.kt` 选择本机最高 `preferredDisplayModeId`；About 显示「当前 120Hz」；可选实时帧率，Haze TabBar 滚动实测 119fps；静止无动画可降到 60fps 省电，显示模式仍 120Hz |
| 新建书籍、名称、封面、排列/占比 | 新建/编辑对话框：书名、作者、题材、简介、分组、8 组封面配色或相册自定义封面；书架支持网格/紧凑/列表、2–4 栏、卡片 78–100%、封面圆角、排序、置顶 |
| 单一新建入口 | 书架和笔记都只保留标题右侧 +；移除网格“新建”块、列表“新建一本书”行、笔记底部“新建笔记”行和空状态按钮 |
| 自定义分组 | 书籍/笔记各自 `Group` 列表；分组管理支持新建、改名、6 色、上下排序、删除（只移回未分组）；工具栏分组筛选；书/笔记编辑处分配；卡片/列表显示分组名 |
| 导出 TXT/EPUB、目录、章节名、阅读器识别 | 双格式导出到 `下载/字隅`；EPUB 3 + NCX + nav + 封面 + 章节 XHTML；TXT 开头目录、章节标题独立成行；已验证 XML/引用/封面 |
| 行文符号栏 + 工具栏 | 共用 `WritingScreen`：顶部标题/字数/保存状态，底部一整块玻璃 dock（工具栏在上、符号栏在下、无内部分隔线），随软键盘上移；收起时用锁存 IME 高度 + 165ms tween，避免被导航条顶得回弹 |
| 笔记右上角三点不闪 | 用同窗口 `InlineModalSheet`，不创建 Dialog、不隐藏 IME；面板 `imePadding` 浮在键盘上方；遮罩延迟 260ms 才可点，避免打开时同一抬手关闭；正文 `inputBlocked` 丢弃菜单期间输入 |
| 内联面板动画 | `shown` 初始 false 后置 true 触发 enter；遮罩 190ms 淡入；面板 260ms 滑入、210ms 滑出；250ms 后才 `onDismiss`；菜单项通过 `pendingMenuAction` 等退场后再执行 |
| 输入法不遮光标 | 正文改 `BoxWithConstraints + verticalScroll`；初始 caret 放 0，首次交互前不滚；只在 caret 真正超出可视区时滚，上边界不再加 26dp margin；IME 稳定 220ms 后只滚一次；`clipToBounds` 防止文字画到 dock 下 |
| 改名/图标/更新 | 应用名、关于页、导出目录、备份前缀改为「字隅」；旧备份仍可读；新图标为墨黑底 + 打开的书 + 茶褐笔锋；关于页“检查更新”读 GitHub Release |
| 关于页作者 | 版本号下显示「作者 3Cne · QQ 2503581955」；关于页 Logo 为「字」，新桌面图标为打开的书 + 笔锋 |
| 分节样式自定义 | `sectionStyle`（星号/短横/圆点/留白/自定义）+ `sectionSymbol`；写作设置页有实时预览和自定义输入，排版 sheet 也有快捷 chips；`sectionBreakText()` 同时服务工具栏与导出文本 |
| 笔记、待办、提醒 | 同一写作界面 + 笔记待办面板：添加/勾选/删除待办，快捷时间 + 自定义日期时间，AlarmManager + 通知，删除/导入/清空时取消闹钟，开机重排 |
| 设置高度可自定义且关联 | 主题/点缀色/任意色相/字体/字号/行距/页边距/玻璃/圆角/颗粒/光晕/流光/动效/转场/触感/标签栏材质/文字/四个标签名/书架排列/栏数/卡片/封面圆角/目标/保存间隔/缩进/字数/FPS/备份恢复；实时预览与底部界面同步变化 |

## 架构

- 单 Activity + 自维护页面栈；`AnimatedContent` 做页面转场，`BackHandler` 分层处理。
- `App.kt`：根导航、TabBar、FPS、Toast、状态栏 scrim。
- `Model.kt`：Book/Chapter/Note/Todo/AppSettings/AppData + `AppStore`（Compose state + JSON 原子写）。
- `Design.kt`：色板、液态玻璃、按钮、对话框、Sheet、输入框、Slider/Segmented/Toggle/FPS。
- `Library.kt`：书架、书籍详情、新建/编辑、封面、章节、导出入口。
- `Editor.kt`：通用写作页、工具栏/符号栏、笔记/待办/自定义提醒。
- `Notes.kt`：笔记列表/筛选/分组/卡片/删除。
- `Groups.kt`：共享分组筛选条、分组管理 Sheet、分组选择器、6 色 palette。
- `UpdateChecker.kt`：GitHub latest release 查询与版本比较；About 的「检查更新」使用 `BuildConfig.UPDATE_REPO`。
- `Stats.kt`：目标环、7 日柱状图、作品统计。
- `Settings.kt`：设置首页 + 外观/书架/写作/动效/标签栏/备份恢复/空间/关于。
- `Export.kt`：TXT、EPUB、封面位图、MediaStore/FileProvider、分享/打开、最近备份。
- `Reminder.kt`：通知渠道、AlarmManager、Receiver、BootReceiver。
- `HighRefresh.kt`：120Hz 请求与写作常亮。
- `Icons.kt`：自绘线性图标，避免额外 Material 依赖。
- `HazeBridge.java` + `app/libs/haze-1.7.3.aar`：Haze 用 Java 反射桥接，避免 Kotlin 2.2 编译器读取其 2.3 metadata；仅 API31+ 启用，失败自动回退静态玻璃。
- TabBar 的 `hazeSource` 在根 `AnimatedContent` 上，`hazeEffect` 在 TabBar 玻璃内；因此玻璃下方内容是真模糊，且裁切在圆角内。
- `InlineModalSheet`：笔记/书籍三点菜单在 Activity 同窗口内绘制，避免 Dialog 抢焦点导致 IME 瞬时消失；`imePadding` 让面板浮在键盘上方；遮罩 260ms 防误触；`WritingScreen.inputBlocked` 丢弃菜单打开期间的输入，但不改变焦点/键盘。
- 内联面板动画：`shown=false` 先组合，再由 `LaunchedEffect` 置 true；`AnimatedVisibility` 用 260ms slide + 200ms fade 进入、210ms slide + 150ms fade 退出；`close()` 置 false、`delay(250)` 后才回调父层移除；遮罩用 190ms 的 `animateFloatAsState`。
- 菜单项退场：`pendingMenuAction` 保存待办/删除/导出/详情动作，`onDismiss` 在退场后执行，避免下一个 Dialog/页面与退场叠加。
- `ModalSheet(imeAware)`：仅用于仍需 Dialog 的菜单/导出；TodoSheet 保留 `imeAware=true`。
- `WritingScreen` 正文：外层 `verticalScroll` + `clipToBounds`，`onTextLayout` 保存 `TextLayoutResult`，`LaunchedEffect` 用 caret rect 滚动；键盘 inset 仍是 3.1.0 的锁存高度 + 165ms。

## 数据与导出

- 数据：`filesDir/moyu.json`；临时文件写完后 rename，旧文件自动复制为 `moyu.backup.json`。
- 备份：JSON 导出到 `下载/字隅`；恢复优先查同目录最近备份，失败回退系统文件选择器；同时兼容旧的 `墨屿备份-`/`笔滞备份-` 文件与目录；恢复前取消旧闹钟，恢复后重排。
- EPUB：`mimetype` 必须首条 STORED；`container.xml`；OPF 3.0；nav + NCX 双目录；章节 XHTML；CSS；800×1176 JPEG 封面；`dc:title/creator/language`。
- TXT：UTF-8；书名/作者/题材/简介/目录/章节标题/正文；章节标题兼容「第X章」「序章」「楔子」「番外」，没有前缀的自动补「第N章」。
- 示例导出验证：`D:\deep\exports\雾港来信.epub`（35714 B）和 `雾港来信.txt`（737 字），EPUB 11 entries、3 章节、封面 800×1176、XML 全部可解析。
- 分节：设置存 `sectionStyle`/`sectionSymbol`；旧备份无字段时默认星号；自定义符号单行最多 24 字；写进正文后 TXT/EPUB 原样保留。

## 验证记录

- 真机：`7c7c01ec`（25113PN0EC，1220×2656，Android 16，支持 120Hz）。`adb install -r` 成功，包签名一致。
- 帧率：`dumpsys display` 的 active mode 为 120Hz；About 页面显示「本机支持 120Hz · 当前 120Hz」；设置中实时帧率在书架显示 120fps，About 捕获 119fps。
- 界面：32 张以上真机截图；真实 `screenrecord` 55 秒操作视频 `D:\deep\shots\moyu-tour.mp4`；抽帧 `D:\deep\shots\fps` 与索引图 `D:\deep\shots\tour-indexed.png`。
- 已操作：书架滚动、打开书、书籍详情、进入章节、弹出软键盘、玻璃 dock 贴合键盘、返回、笔记列表、笔记编辑、待办面板、外观设置、备份导出、系统文件选择器恢复。
- 提醒：AlarmManager 有两条未来闹钟；测试待办在 20:40 触发系统通知，通知标题「该写一点了」，内容「给沈雾补一段童年回忆」。
- 备份：导出 JSON（2 书 / 2 笔记 / 759 日字数），清装后通过系统文件选择器选择 JSON，确认恢复到 2 书 2 笔记，只有两条当前闹钟（旧安装闹钟随卸载清除）。
- 3.1.0 验收：`v31-about.png` 显示 3.1.0 / current 120Hz / versionCode 95；`v31-hide.mp4` 30fps 抽帧确认 dock 只向下、无末端回弹；`v31-tabs.mp4` 抽帧确认 Library→Notes→Settings 与 Settings→Notes→Library 双向；`v31-section-settings.png`、`v31-section-inserted.png` 验证 5 种分节与自定义符号；`v31-stats-blur2.png` 验证 TabBar 下方书封/文字被真实模糊且玻璃为单块；`墨屿-v3.1.0-验收.mp4` 合并录屏 17.6s。
- 3.1.1 验收：`v311-more2.mp4` + `v311-more2-contact.png` 30fps 抽帧确认输入法打开时点三点只出现一次面板、无回弹；`v311-cursor.mp4` 与 `v311-cursor.png` 确认点下方文本后 caret 在 dock 上方、正文不钻到 dock 下；`v311-about.png` 显示 3.1.1 / 120Hz / 作者 3Cne · QQ 2503581955；`v311-final-library.png` 确认 appName 回墨屿且《灼石》数据保留；`墨屿-v3.1.1-验收.mp4` 10.7s。
- 3.1.1 基线：从 3.1.0 回退 3.2.0 的「笔滞」改名、钢笔图标、作者/QQ 之外的品牌改动、玻璃透镜/`glassBlur`、标题空白化；作者/QQ 按本轮要求保留。3.2.0 工作树留档于 `D:\deep\releases\voided-3.2.0\`。
- 3.1.2 验收：`v312-more2-contact.png` 中键盘从 0.83 到 2.47 持续存在，面板从 0.93 稳定停在键盘上方，不再出现第五/六帧键盘消失；`v312-cursor` 确认光标在 dock 上方；`v312-about.png` 显示 3.1.2 / 97 / 作者；`墨屿-v3.1.2-验收.mp4` 10.7s。
- 3.1.3 验收：`v313-open-contact.png` 显示 0.97 前无面板、1.00 面板部分露出、1.03/1.07 继续上滑、1.17 稳定；`v313-close-contact.png` 显示面板从 0.80 开始下滑、0.97 半透明退出、之后移除；键盘全程存在；`v313-about.png` 显示 3.1.3 / 98 / 作者 / 120Hz；`墨屿-v3.1.3-验收.mp4` 13.9s。
- 3.2.0 验收：`v320-clean.png`/`v320-list.png` 证明网格与列表都只剩书架 +，分组栏在两种布局一致；`v320-notes.png` 证明笔记只剩 +、无底部新建行；`v320-group.png` 是分组管理；`v320-about.png` 显示 3.2.0 / 99 / 作者 / 120Hz / 检查更新；`字隅-v3.2.0-验收.mp4` 14s。
- 3.2.0 光标：`v320-before.png`/`v320-after.png` 点击正文后正文坐标不移动；本轮 scrcpy 硬件键盘下软键盘未弹出，键盘动画需用户在真机软键盘下再确认一次。
- 3.2.1 验收：`v321-grid.png` 与 `v321-list.png` 的“继续创作/我的书架/分组栏”y 坐标一致；`字隅-v3.2.1-布局验收.mp4` 6s 切换网格/列表。
- 3.2.1 更新提示：GitHub 最新 Release 仍是 v3.2.0；需要把新 APK 发成新 Release，关于页才会提示新版本。
- 3.2.3 验收：`editor-final.png` 显示点击下方行后 caret 在 dock 上方；`editor-contact.png` 为 60fps 抽帧，1.00→1.07 只发生一次滚动后稳定；`editor-60fps.mp4` 为真机录屏。正文底边与 dock 顶边均为 1311px，260px 空白已移除。
- 3.2.3 更新提示：GitHub 最新 Release 仍为 v3.2.0；3.2.3 APK 需另发 Release。
- 3.2.4 根因：Compose `BasicTextField` + 外层 scroll 无法稳定处理空行 caret；正文改为原生 `BodyEditText`（唯一滚动/caret owner），修复构造期 selection 回调空指针。
- 3.2.4 验收：`editor-low.png`（最下面一行点按后 caret 在 dock 上方）、`editor-contact.png`（60fps 单次滚动）、`editor-60fps.mp4`；数据 791 字。
- 3.2.4 更新提示：GitHub 最新 Release 仍为 v3.2.0；3.2.4 APK 需另发 Release。
- 3.2.5 工具栏收键盘：原生 EditText 下 Compose `keyboard.hide()` 不足，改为清焦点 + `InputMethodManager.hideSoftInputFromWindow` + `keyboard.hide()`；真机 `mInputShown=false`，`keyboard-hidden.png`、`keyboard-hide-60fps.mp4` 验证。
- 3.2.5 系统返回/顶栏返回/排版入口同样调用 `hideEditorKeyboard()`。
- 3.2.5 更新提示：GitHub 最新 Release 仍为 v3.2.0；3.2.5 APK 需另发 Release。
- 3.2.6 设置滚动：根 SettingsScreen 的 LazyColumn 状态提升到 MoyuRoot（`rememberLazyListState`），SettingPage 入栈/出栈不再销毁状态；返回保持原位置。
- 3.2.6 验收：旧版 `settings-old-60fps.mp4` 返回回到“设置”顶部；修复版 `settings-fix-60fps.mp4` 返回在“关于字隅”附近；60fps 抽帧联系表 `settings-fix-contact.png`。
- 3.2.6 更新提示：GitHub 最新 Release 仍为 v3.2.0；3.2.6 APK 需另发 Release。
- 3.2.7：空分组时“继续创作”不再因 visibleBooks 为空而消失，卡片改为全局最近写的书。
- 3.2.7/3.2.8 图标：C 黑白剪影（黑底白钢笔，无文字）；3.2.8 将钢笔内容缩到 660/1024，四周留约 1/5。
- 3.2.8 Toast：退场时保留最后一条文本（`display`），`fadeOut(200)+slideOutVertically(tween(200))`；不再出现“出现有动画、消失瞬间没了”。
- 3.2.8 验收：`toast-exit-60fps.mp4` / `toast-exit-contact.png`；`group327-final.mp4`；`icon-C.png`。
- 3.2.8 更新提示：GitHub 最新 Release 仍为 v3.2.0；3.2.8 APK 需另发 Release。
- 3.2.9：原生正文光标/选区手柄按 `ui.accent` 着色；`ArrowKeyMovementMethod` 改为 `ScrollingMovementMethod` 获得原生 fling。
- 3.2.9 验收：`cursor-accent.png`（accent #457F85 的 teal 手柄）、`inertia-60fps.mp4` / `inertia-contact.png`。
- 3.2.9 更新提示：GitHub 最新 Release 仍为 v3.2.0；3.2.9 APK 需另发 Release。
- 3.2.10：`ScrollingMovementMethod` 在正文里仍不稳定，改为 `BodyEditText` 自己处理触摸：VelocityTracker + OverScroller，拖动 `scrollBy`，抬手按速度 fling。
- 3.2.10 验收：`custom-fling-60fps.mp4` / `custom-fling-final.png`；光标 `cursor-accent.png`（teal #457F85）。
- 3.2.10 更新提示：GitHub 最新 Release 仍为 v3.2.0；3.2.10 APK 需另发 Release。
- 3.2.11：夜间 TabBar 泛白来自 HazeBridge 写死 `0x40FFFFFF`；改为暗色 tint `0x4D101312`。深色玻璃降低 top fill/caustic/rim/specular 多层白高光。
- 3.2.11：GlassButton/GlassIconButton 统一 strength .78 / frost .60。
- 3.2.11 验收：`tabbar-before.png`（灰白）/ `tabbar-after.png`（深色玻璃）。
- 3.2.11 更新提示：GitHub 最新 Release 仍为 v3.2.0；3.2.11 APK 需另发 Release。
- 3.2.12：按钮顶部额外弧线来自 `liquidGlass` 的 specular 横线在圆角上形成的弦；新增 `specular` 参数，GlassButton/GlassIconButton 关闭，卡片保留。
- 3.2.12 验收：`button-compare.png`（修复前顶部弧 vs 修复后单外圈）。
- 3.2.12 更新提示：GitHub 最新 Release 仍为 v3.2.0；3.2.12 APK 需另发 Release。
- 注意：沙箱中的 Gradle Kotlin daemon 会回退到 non-daemon，最终 `BUILD SUCCESSFUL`；PowerShell 的末尾 exit code 1 来自 stderr，不代表构建失败。

## 已知限制

- TabBar 在 API31+ 用 Haze 做真实背景模糊；dock、卡片、按钮、对话框仍是单层静态玻璃（Dialog 额外尝试窗口模糊）。API<31 全部回退静态玻璃。Haze 依赖本地 AAR，不通过 Gradle 远端解析。
- Android 原生文本选择手柄仍偏大；当前把颜色降到 58% 透明度，没有自定义手柄。
- 系统文件选择器「最近」页首次加载慢/空白；从「浏览 → 下载」选择 JSON 已实测成功；另有「下载/墨屿最近备份」兜底。
- 应用内不导入 EPUB/TXT，只负责导出到阅读器可识别的格式；备份恢复用于墨屿自己的 JSON。
- Haze TabBar 滚动时 119fps；静止且没有持续动画时 Compose 可显示 60fps（省电），`mActiveSfDisplayMode` 仍是 120Hz。开启流光时静止可维持更高帧。
- 真机当前是用户数据《灼石》1 本书、2 条笔记，不重置；APK 本身不含示例数据，全新安装是空书架。
- GitHub 更新渠道：`updateRepo=3Cne/ziyu`；Release `v3.2.0` 已发布；真机关于页“检查更新”显示“已经是最新版本”，API 可达。Actions 自动构建还需要源码 `.github` 与 Secrets；手动 Release 已可用。
- 按“3.2.0 作废”回退，空标题笔记会恢复为实体「无标题笔记」（3.1.0 行为），不再显示灰色 placeholder；这是本轮明确取舍。
- 真机测试时临时切换过输入法，最后已恢复原来的 `com.tencent.wetype/.plugin.hld.WxHldService` 并关闭 `show_ime_with_hard_keyboard`。
- Haze 1.7.3 是用 Kotlin 2.3/Compose 1.12 编译的，本工程用 Kotlin 2.2/Compose 1.9.5，因此通过 Java 反射桥接；不要在 Kotlin 源码里直接 import Haze 类型，也不要让 Gradle 拉取它的传递依赖。

## 交付命令

```powershell
$env:JAVA_HOME='D:\android-tools\jdk17'
$env:ANDROID_HOME='D:\android-tools\sdk'
$env:ANDROID_SDK_ROOT='D:\android-tools\sdk'
$env:ANDROID_USER_HOME='D:\deep\.android'
$env:GRADLE_USER_HOME='D:\deep\.gradle'
$env:PATH="D:\android-tools\jdk17\bin;D:\android-tools\sdk\platform-tools;$env:PATH"
cd D:\deep\moyu-compose
.\gradlew.bat :app:assembleRelease --no-daemon --console=plain
adb -s 7c7c01ec install -r app\build\outputs\apk\release\app-release.apk
```

## 下一版建议

- 130ms 级的自定义转场时长滑杆，并把所有动画统一读取同一速度倍率。
- 阅读模式（导出书在应用内预览）与 EPUB 导入。
- 文本选择手柄自定义/缩小；大段文字编辑的增量保存。
- 备份包含封面图片（当前 JSON 只记路径）。
- 更多封面排版模板（竖排书名/纯色/图片）。
- 把 Haze 真实模糊扩展到编辑 dock：需要把正文 source 铺到 dock 下方、dock 作为 overlay 单独采样，勿让 source 包含 dock 自身。
- 给玻璃加可选的折射形变（不是全屏逐帧模糊），并确保 120Hz 不回落。

## 2026-10-30 / 3.7.10（versionCode 393）

- APK：`D:\deep\字隅-Compose-v3.7.10.apk`，7,997,923 B
- SHA-256：`E5878C700DD4ABB9BDAB0D4AE8686C21C6CAAB4CBB4CDF9E8DF04AAC8A1E944B`
- 新增统一材质 `Material.kt`：SurfaceRole(bar/card/popup/control/paper)，每主题独立参数，8dp 边框（Control 2.5dp），纯色 8% 纸色偏移；封面 8/3/2dp。
- 纸张：空白/虚线/实线/稿纸、线条深浅、正文外 Compose Canvas 网格。
- 提醒：正文内提醒行、通知权限请求、通知标题=待办文字，正文=笔记标题。
- 新手引导恢复并重做 8 页；设置→关于→重看功能引导。
- 新建书籍字段切换保留 AnimatedPane 动画；Dialog 高刷仍只完成窗口请求。
- 发布说明：`docs/RELEASE-3.7.10.md`

## 2026-10-30 / 3.7.13

- APK：`D:\deep\ziyu-v3.7.13.apk`，7,997,927 B
- SHA-256：`79A429AB35E7FC915EB4D499CEEA0341B357596279983D258F6BD00443DE0F39`
- Toast 统一走 MoyuSurface(CONTROL)，增加 IME 避让。
- 提醒行禁止首行缩进，光标进入时弹回待办末尾；点提醒仍打开时间面板。
- 待办添加忽略首行缩进；当前行有正文时另起无缩进待办。
- 新建书籍 GlassDialog 禁止点蒙版关闭，返回键仍关闭。
- 发布说明：`docs/RELEASE-3.7.10.md`（内容已更新为 3.7.13）
