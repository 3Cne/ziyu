package com.moyu.writer

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

object AppUpdater {
    fun downloadAndInstall(context: Context, info: UpdateInfo) {
        if (info.apkUrl.isBlank()) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 26 &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            printToast("请允许素纸安装未知应用，然后重试")
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val name = info.apkName.ifBlank { "ziyu-${info.tag}.apk" }
        val relative = "updates/$name"
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("素纸 ${info.tag}")
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, relative)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)
        printToast("开始下载 ${info.tag}")

        CoroutineScope(Dispatchers.IO).launch {
            var status = DownloadManager.STATUS_RUNNING
            while (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                delay(700)
                manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    }
                }
            }
            Handler(Looper.getMainLooper()).post {
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val file = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?.resolve(relative)
                    if (file != null && file.exists()) install(context, file)
                    else printToast("下载完成但找不到安装包")
                } else {
                    printToast("下载失败，请稍后重试")
                }
            }
        }
    }

    private fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { printToast("无法打开安装器") }
    }
}
