package com.moyu.writer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupInfo(
    val name: String,
    val size: Long,
    val dateAdded: Long,
)

/**
 * Listing / writing / restoring the JSON backups the app itself put in
 * 「下载/字隅」. Only files this app owns are visible through MediaStore, which
 * is the honest limit on Android 11+: a backup pushed in from a PC still has to
 * go through the system picker, not this list.
 */
object BackupOps {
    private val PREFIXES = listOf("字隅备份-", "墨屿备份-", "笔滞备份-")

    fun list(context: Context): List<BackupInfo> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val projection = arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_ADDED,
                )
                val like = PREFIXES.joinToString(" OR ") { "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?" }
                val args = PREFIXES.map { "$it%" }.toTypedArray()
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    "($like)",
                    args,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                )?.use { c ->
                    val out = ArrayList<BackupInfo>()
                    while (c.moveToNext() && out.size < 10) {
                        out += BackupInfo(
                            name = c.getString(0).orEmpty(),
                            size = c.getLong(1),
                            dateAdded = c.getLong(2) * 1000L,
                        )
                    }
                    out
                } ?: emptyList()
            } else {
                val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                listOf(File(root, "字隅"), File(root, "墨屿"), File(root, "笔滞"))
                    .flatMap { dir ->
                        dir.listFiles { f ->
                            f.isFile && PREFIXES.any { f.name.startsWith(it) } && f.name.endsWith(".json")
                        }?.toList() ?: emptyList()
                    }
                    .map { BackupInfo(it.name, it.length(), it.lastModified()) }
                    .sortedByDescending { it.dateAdded }
                    .take(10)
            }
        }.getOrDefault(emptyList())
    }

    fun readText(context: Context, name: String): String? {
        if (name.isBlank()) return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(name),
                    null,
                )?.use { c ->
                    if (!c.moveToFirst()) return@runCatching null
                    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0))
                    readBackupText(context, uri)
                }
            } else {
                val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val f = listOf(File(root, "字隅"), File(root, "墨屿"), File(root, "笔滞"))
                    .map { File(it, name) }
                    .firstOrNull { it.isFile }
                    ?: return@runCatching null
                f.readText(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    suspend fun write(context: Context, data: AppData): ExportResult {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return exportBytes(
            context,
            "字隅备份-$stamp.json",
            "application/json",
            backupJson(data).toByteArray(Charsets.UTF_8),
        )
    }

    /** Parse before touching the live store; a broken file must change nothing. */
    fun parse(text: String): AppData = parseBackup(text)
}
