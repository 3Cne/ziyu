# 字隅

一个安静的移动写作空间：书、章节、笔记、待办、提醒、分组、EPUB/TXT 导出和本地 JSON 备份。纯 Jetpack Compose。

## 本地构建

```powershell
$env:JAVA_HOME='D:\android-tools\jdk17'
$env:ANDROID_HOME='D:\android-tools\sdk'
$env:GRADLE_USER_HOME='D:\deep\.gradle'
cd D:\deep\moyu-compose
.\gradlew.bat :app:assembleRelease --no-daemon
```

本地签名参数在 `keystore.properties`，该文件和 `*.keystore` 已加入 `.gitignore`，不要提交。

## GitHub 更新渠道

1. 新建公开仓库（建议名 `ziyu`），把本目录推上去。
2. 在仓库 **Settings → Secrets and variables → Actions** 添加：
   - `KEYSTORE_BASE64`：`moyu-release.keystore` 的 Base64
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`（默认 `moyu`）
   - `KEY_PASSWORD`
3. 打 tag 并推送：`git tag v3.2.0 && git push origin v3.2.0`。
4. Actions 会构建签名 APK，并发布到 **Releases**。
5. 把仓库地址写进 `gradle.properties` 的 `updateRepo=owner/ziyu`，重新构建；关于页的“检查更新”会读取 GitHub 最新 Release。

```powershell
# 生成 Base64（PowerShell）
[Convert]::ToBase64String([IO.File]::ReadAllBytes('moyu-release.keystore')) | Set-Content keystore.base64
```

不要把邮箱密码、GitHub 密码或 PAT 写进源码；PAT 只用于你本地的 `git push` 或一次性操作。
