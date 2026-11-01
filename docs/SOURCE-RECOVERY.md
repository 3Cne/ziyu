# 源码恢复记录（2026-10-30）

## 事故

编辑 `app/src/main/java/com/moyu/writer/Editor.kt` 时，PowerShell 用错误编码回写文件，导致：

- 中文被转成乱码；
- 部分字符串结尾引号、`'☐' / '☑'` 字符丢失；
- `Editor.kt` 当前无法编译。

## 当前状态

- 真机 `7c7c01ec` 已安装并运行 **3.7.64 / 447**，功能可用。
- 不降级到 3.7.17：会同时回退纸线、固定行高、空行/页脚修复。
- 源码快照只到 `releases/v3.2.231 / 340`，没有 3.7 系列源码。
- 3.7 系列只有 APK，没有源码快照。

## 保留文件

- 3.7.64 APK：`D:\deep\ziyu-v3.7.64.apk`
  - SHA-256：`FBF8C84422B9E52AB67A6B10C84E6B9B8B697A6F16CE497FCD76FA7BB9826229`
- 损坏源码：`D:\deep\shots\Editor.corrupt.kt`
- 反向还原稿：`D:\deep\shots\Editor.recovered.kt`
- 3.7.17 APK：`D:\deep\ziyu-v3.7.17.apk`（仅临时回退用，不推荐）

## 恢复优先级

1. 找损坏前的 `Editor.kt`：
   - Git 提交；
   - Android Studio Local History；
   - 本地备份/压缩包。
2. 恢复后只改自绘光标，不动纸线。
3. 无备份时的最后手段：
   - 装 3.7.17 APK（全量回退）；
   - 或从 `releases/v3.2.231` 恢复源码（丢失后续功能，最不推荐）。

## 版本快照规则

每次升级 `versionCode/versionName` 后、安装验证通过后，必须执行：

```text
D:\deep\moyu-compose\scripts\snapshot-source.ps1
```

脚本会：

- 读取 `app/build.gradle.kts` 的版本；
- 把 `app/src`、`app/build.gradle.kts`、根构建文件、`gradle/` 复制到 `releases/v<versionName>/src/`；
- 复制 APK 并写入 `SNAPSHOT.json`；
- 已存在快照默认拒绝覆盖，必须确认后用 `-Force`。

当前 `3.7.64` 源码已损坏，不能生成有效源码快照；不能用损坏源码伪造快照。

## 光标问题记录

- `TextView` 会在布局/行高变化时重设 `textCursorDrawable` 的 bounds。
- 录屏测量到光标高度 `34 → 32 → 48px`，位置也变化。
- 最终方案：不再用 `textCursorDrawable`，在 `BodyEditText.onDraw()` 自绘光标：
  - 高度 `行距 × 0.5`；
  - 底端贴纸线；
  - 自己控制闪烁。
- 该方案必须等源码恢复后实施。
