package com.moyu.writer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportResult(
    val shareUri: Uri,
    val fileName: String,
    val location: String,
    val mime: String,
    val bytes: Long,
)

private fun safeFileName(name: String): String {
    val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().ifBlank { "未命名" }
    return cleaned.take(60)
}

fun chapterDisplayTitle(book: Book, index: Int): String {
    val raw = book.chapters.getOrNull(index)?.title?.trim().orEmpty().ifBlank { "第${index + 1}章" }
    val recognized = Regex("^(第[一二三四五六七八九十百千万零两0-9]+[章节卷回]|序章|楔子|引子|后记|尾声|番外)")
    return if (recognized.containsMatchIn(raw)) raw else "第${index + 1}章 $raw"
}

fun buildTxt(book: Book, includeSynopsis: Boolean): ByteArray {
    val sb = StringBuilder()
    sb.append(book.title.trim().ifBlank { "未命名" }).append("\n")
    if (book.author.isNotBlank()) sb.append("作者：").append(book.author).append("\n")
    sb.append("题材：").append(book.genre.ifBlank { "小说" }).append("\n")
    sb.append("\n")
    if (includeSynopsis && book.synopsis.isNotBlank()) {
        sb.append("简介\n").append(book.synopsis).append("\n\n")
    }
    if (book.chapters.isNotEmpty()) {
        sb.append("目录\n")
        book.chapters.forEachIndexed { index, _ ->
            sb.append(chapterDisplayTitle(book, index)).append("\n")
        }
        sb.append("\n\n")
    }
    book.chapters.forEachIndexed { index, chapter ->
        sb.append(chapterDisplayTitle(book, index)).append("\n\n")
        val body = chapter.content.trimEnd()
        if (body.isBlank()) sb.append("（本章尚未落笔）\n") else sb.append(body).append("\n")
        sb.append("\n\n")
    }
    sb.append("—— 由字隅导出 ——\n")
    return sb.toString().toByteArray(Charsets.UTF_8)
}

fun buildEpub(context: Context, book: Book, includeSynopsis: Boolean, out: OutputStream) {
    val title = book.title.trim().ifBlank { "未命名" }
    val author = book.author.trim().ifBlank { "佚名" }
    val uuid = UUID.randomUUID().toString()
    val modified = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
    val zip = ZipOutputStream(BufferedOutputStream(out))
    try {
        val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        val mime = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = mimeBytes.size.toLong()
            crc = CRC32().apply { update(mimeBytes) }.value
            time = 0L
        }
        zip.putNextEntry(mime)
        zip.write(mimeBytes)
        zip.closeEntry()

        zip.setLevel(6)
        fun entry(name: String, text: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry(
            "META-INF/container.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""
        )

        val cover = CoverArt.render(context, book)
        val coverBytes = java.io.ByteArrayOutputStream().use { bos ->
            cover.compress(Bitmap.CompressFormat.JPEG, 92, bos)
            bos.toByteArray()
        }
        zip.putNextEntry(ZipEntry("OEBPS/cover.jpg"))
        zip.write(coverBytes)
        zip.closeEntry()

        val chapters = book.chapters.ifEmpty { listOf(Chapter(title = "第一章", content = "")) }
        val manifestChapters = StringBuilder()
        val spine = StringBuilder()
        val navItems = StringBuilder()
        val ncxNav = StringBuilder()
        chapters.forEachIndexed { index, chapter ->
            val n = index + 1
            val href = "text/chapter$n.xhtml"
            manifestChapters.append("""    <item id="ch$n" href="$href" media-type="application/xhtml+xml"/>
""")
            spine.append("""    <itemref idref="ch$n"/>
""")
            val label = xmlEscape(chapterDisplayTitle(book, index))
            navItems.append("""      <li><a href="$href">$label</a></li>
""")
            ncxNav.append("""    <navPoint id="nav$n" playOrder="${n + 1}">
      <navLabel><text>$label</text></navLabel>
      <content src="$href"/>
    </navPoint>
""")
            entry("OEBPS/$href", chapterXhtml(chapterDisplayTitle(book, index), chapter.content))
        }
        entry(
            "OEBPS/content.opf",
            """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="zh-CN">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:uuid:$uuid</dc:identifier>
    <dc:title>${xmlEscape(title)}</dc:title>
    <dc:creator>${xmlEscape(author)}</dc:creator>
    <dc:language>zh-CN</dc:language>
    <dc:publisher>字隅</dc:publisher>
    <dc:description>${xmlEscape(if (includeSynopsis) book.synopsis else "")}</dc:description>
    <meta property="dcterms:modified">$modified</meta>
    <meta name="cover" content="cover-image"/>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="css" href="style.css" media-type="text/css"/>
    <item id="cover-image" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
    <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
$manifestChapters  </manifest>
  <spine toc="ncx">
    <itemref idref="cover" linear="no"/>
$spine  </spine>
  <guide>
    <reference type="cover" title="封面" href="cover.xhtml"/>
    <reference type="toc" title="目录" href="nav.xhtml"/>
  </guide>
</package>
"""
        )
        entry(
            "OEBPS/nav.xhtml",
            """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="zh-CN">
<head><meta charset="utf-8"/><title>目录</title><link rel="stylesheet" href="style.css"/></head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>目录</h1>
    <ol>
$navItems    </ol>
  </nav>
</body>
</html>
"""
        )
        entry(
            "OEBPS/toc.ncx",
            """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="urn:uuid:$uuid"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle><text>${xmlEscape(title)}</text></docTitle>
  <docAuthor><text>${xmlEscape(author)}</text></docAuthor>
  <navMap>
$ncxNav  </navMap>
</ncx>
"""
        )
        entry(
            "OEBPS/cover.xhtml",
            """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="zh-CN">
<head><meta charset="utf-8"/><title>封面</title><link rel="stylesheet" href="style.css"/></head>
<body class="cover-page"><img src="cover.jpg" alt="${xmlEscape(title)}"/></body>
</html>
"""
        )
        entry(
            "OEBPS/style.css",
            """html,body{margin:0;padding:0;background:#f6f2e9;color:#1b1914;}
body{font-family:"Noto Serif CJK SC","Source Han Serif SC",serif;line-height:1.85;}
.cover-page{text-align:center;margin:0;padding:0;}
.cover-page img{max-width:100%;height:auto;}
h1{font-size:1.5em;font-weight:600;margin:2.4em 0 1.4em;letter-spacing:.04em;}
p{font-size:1em;text-indent:2em;margin:0 0 .72em;text-align:justify;}
nav ol{list-style:none;padding:0;} nav li{margin:.72em 0;} nav a{color:#1b1914;text-decoration:none;}
"""
        )
    } finally {
        zip.finish()
        zip.flush()
    }
}

private fun chapterXhtml(title: String, content: String): String {
    val paragraphs = content.trim().replace("\r\n", "\n").split("\n")
    val body = if (paragraphs.all { it.isBlank() }) {
        "<p class=\"empty\">（本章尚未落笔）</p>"
    } else {
        paragraphs.joinToString("\n") { p ->
            if (p.isBlank()) "<p><br/></p>" else "<p>${xmlEscape(p)}</p>"
        }
    }
    return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="zh-CN">
<head><meta charset="utf-8"/><title>${xmlEscape(title)}</title><link rel="stylesheet" href="../style.css"/></head>
<body>
  <h1>${xmlEscape(title)}</h1>
$body
</body>
</html>
"""
}

fun xmlEscape(input: String): String = input
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

suspend fun exportBook(
    context: Context,
    book: Book,
    format: String,
    includeSynopsis: Boolean,
): ExportResult = withContext(Dispatchers.IO) {
    val base = safeFileName(book.title)
    val now = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    val ext = if (format == "epub") "epub" else "txt"
    val mime = if (format == "epub") "application/epub+zip" else "text/plain"
    val fileName = "$base-$now.$ext"
    if (Build.VERSION.SDK_INT >= 29) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/字隅")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("无法在下载目录创建文件")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (format == "epub") buildEpub(context, book, includeSynopsis, out)
                else out.write(buildTxt(book, includeSynopsis))
            } ?: error("无法写入文件")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            val bytes = if (format == "epub") {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
            } else buildTxt(book, includeSynopsis).size.toLong()
            ExportResult(uri, fileName, "下载/字隅/$fileName", mime, bytes)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    } else {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            "字隅",
        ).apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            if (format == "epub") buildEpub(context, book, includeSynopsis, out)
            else out.write(buildTxt(book, includeSynopsis))
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        ExportResult(uri, fileName, file.absolutePath, mime, file.length())
    }
}

suspend fun exportBytes(
    context: Context,
    fileName: String,
    mime: String,
    bytes: ByteArray,
): ExportResult = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT >= 29) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/字隅")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("无法在下载目录创建文件")
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入文件")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        ExportResult(uri, fileName, "下载/字隅/$fileName", mime, bytes.size.toLong())
    } else {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            "字隅",
        ).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        ExportResult(uri, fileName, file.absolutePath, mime, file.length())
    }
}

fun noteExportBytes(note: Note, format: String): ByteArray {
    val base = note.title.trim().ifBlank { "无标题笔记" }
    return when (format) {
        "md" -> buildString {
            append("# ").append(base).append("\n\n")
            append(contentWithoutReminders(note.content)).append("\n")
            if (note.todos.isNotEmpty()) {
                append("\n## 待办\n\n")
                note.todos.forEach { append(if (it.done) "- [x] " else "- [ ] ").append(it.text).append("\n") }
            }
        }.toByteArray()
        "json" -> {
            val root = org.json.JSONObject()
            root.put("title", base)
            root.put("content", contentWithoutReminders(note.content))
            root.put("pinned", note.pinned)
            root.put("createdAt", note.createdAt)
            root.put("updatedAt", note.updatedAt)
            root.put("groupId", note.groupId ?: "")
            val arr = org.json.JSONArray()
            note.todos.forEach { todo ->
                arr.put(org.json.JSONObject().apply {
                    put("text", todo.text)
                    put("done", todo.done)
                    put("remindAt", todo.remindAt ?: 0L)
                })
            }
            root.put("todos", arr)
            root.toString(2).toByteArray()
        }
        else -> buildString {
            append(base).append("\n\n").append(contentWithoutReminders(note.content)).append("\n")
            if (note.todos.isNotEmpty()) {
                append("\n待办\n")
                note.todos.forEach { append(if (it.done) "[x] " else "[ ] ").append(it.text).append("\n") }
            }
        }.toByteArray()
    }
}

suspend fun exportNote(context: Context, note: Note, format: String): ExportResult =
    withContext(Dispatchers.IO) {
        val base = safeFileName(note.title.ifBlank { "无标题笔记" })
        val now = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val ext = when (format) { "md" -> "md"; "json" -> "json"; else -> "txt" }
        val mime = when (format) {
            "md" -> "text/markdown"
            "json" -> "application/json"
            else -> "text/plain"
        }
        exportBytes(context, "$base-$now.$ext", mime, noteExportBytes(note, format))
    }

suspend fun copyExportTo(context: Context, result: ExportResult, target: Uri): String =
    withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(result.shareUri)?.use { input ->
            context.contentResolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
                ?: error("无法写入所选位置")
        } ?: error("无法读取导出文件")
        target.toString()
    }

fun findLatestBackup(context: Context): Uri? {
    return try {
        if (Build.VERSION.SDK_INT >= 29) {
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
            val uri = context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                "(${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?)",
                arrayOf("字隅备份-%", "墨屿备份-%", "笔滞备份-%"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                } else null
            }
            uri
        } else {
            val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            listOf(File(root, "字隅"), File(root, "墨屿"), File(root, "笔滞"))
                .flatMap { dir ->
                    dir.listFiles { f ->
                        (f.name.startsWith("字隅备份-") || f.name.startsWith("墨屿备份-") || f.name.startsWith("笔滞备份-")) &&
                            f.name.endsWith(".json")
                    }?.toList() ?: emptyList()
                }
                .maxByOrNull { it.lastModified() }
                ?.let { Uri.fromFile(it) }
        }
    } catch (_: Throwable) {
        null
    }
}

fun readBackupText(context: Context, uri: Uri): String =
    if (uri.scheme == "file") {
        File(uri.path ?: error("无法读取备份")).readText(Charsets.UTF_8)
    } else {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("无法读取备份")
    }

fun shareExport(context: Context, result: ExportResult) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = result.mime
        putExtra(Intent.EXTRA_STREAM, result.shareUri)
        putExtra(Intent.EXTRA_SUBJECT, result.fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享电子书").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { ToastBus.show("没有可以分享的应用") }
}

fun openExport(context: Context, result: ExportResult) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(result.shareUri, result.mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { ToastBus.show("没有找到能打开这种格式的阅读器") }
}

@Composable
fun ExportSheet(book: Book, onDismiss: () -> Unit) {
    val ui = LocalInk.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf("epub") }
    var synopsis by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExportResult?>(null) }
    ModalSheet(onDismiss) {
        DialogTitle("导出书籍", "目录、章节名和封面都会写进文件。", onClose = onDismiss)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FormatCard(
                name = "EPUB",
                detail = "封面 · 目录 · 章节",
                glyph = Glyph.Book,
                selected = format == "epub",
                modifier = Modifier.weight(1f),
            ) { format = "epub"; result = null }
            FormatCard(
                name = "TXT",
                detail = "通用纯文本 · 带目录",
                glyph = Glyph.Note,
                selected = format == "txt",
                modifier = Modifier.weight(1f),
            ) { format = "txt"; result = null }
        }
        Spacer(Modifier.height(14.dp))
        MoyuSurface(SurfaceRole.CARD, Modifier.fillMaxWidth(), radius = 18f) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Txt("包含书籍简介", size = 12.5f, weight = FontWeight.Medium)
                    Txt("简介会写入元数据或正文开头", size = 9f, color = ui.palette.muted)
                }
                InkToggle(synopsis) { synopsis = it }
            }
        }
        Spacer(Modifier.height(12.dp))
        Txt(
            when (format) {
                "epub" -> "标准 EPUB 3 电子书，包含 ${book.chapters.size} 个章节、可跳转目录、封面和作者信息。可导入 Apple Books、微信读书、Kindle（转换后）等阅读器。"
                else -> "UTF-8 纯文本，开头带目录，章节标题单独成行；大多数阅读器会自动识别为章节。"
            },
            size = 10f,
            color = ui.palette.muted,
            lineHeight = 16f,
            modifier = Modifier.padding(horizontal = 3.dp),
        )
        Spacer(Modifier.height(19.dp))
        if (result == null) {
            PrimaryButton(
                if (busy) "正在生成…" else "导出到下载",
                {
                    if (busy) return@PrimaryButton
                    busy = true
                    scope.launch {
                        runCatching { exportBook(context, book, format, synopsis) }
                            .onSuccess {
                                result = it
                                ToastBus.show("已导出到 ${it.location}")
                            }
                            .onFailure {
                                ToastBus.show("导出失败：${it.message ?: "未知错误"}")
                            }
                        busy = false
                    }
                },
                Modifier.fillMaxWidth(),
                glyph = Glyph.Export,
                enabled = !busy,
            )
        } else {
            val r = result!!
            MoyuSurface(SurfaceRole.CARD, Modifier.fillMaxWidth(), radius = 22f) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFDDF1E4)),
                            contentAlignment = Alignment.Center,
                        ) {
                            LineIcon(Glyph.Check, Color(0xFF226B43), 18.dp, 2f)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Txt("导出完成", size = 13.5f, weight = FontWeight.Medium)
                            Txt(r.fileName, size = 9.5f, color = ui.palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(11.dp))
                    Txt(r.location, size = 9f, color = ui.palette.muted, lineHeight = 14f)
                    Spacer(Modifier.height(10.dp))
                    SaveAsButton(r)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("打开", { openExport(context, r) }, Modifier.weight(1f), glyph = Glyph.Book)
                        GlassButton("分享", { shareExport(context, r) }, Modifier.weight(1f), glyph = Glyph.Share)
                        GlassButton("完成", onDismiss, Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SaveAsButton(result: ExportResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { copyExportTo(context, result, uri) }
                    .onSuccess { ToastBus.show("已保存到所选位置") }
                    .onFailure { ToastBus.show("保存失败：${it.message ?: "未知错误"}") }
            }
        }
    }
    GlassButton(
        "保存到…",
        { launcher.launch(result.fileName) },
        Modifier.fillMaxWidth(),
        glyph = Glyph.Folder,
    )
}

@Composable
fun NoteExportSheet(note: Note, onDismiss: () -> Unit) {
    val ui = LocalInk.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf("txt") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExportResult?>(null) }
    ModalSheet(onDismiss) {
        DialogTitle("导出笔记", "正文和待办会一起写进文件。", onClose = onDismiss)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FormatCard("TXT", "纯文本 · 最通用", Glyph.Note, format == "txt", Modifier.weight(1f)) {
                format = "txt"; result = null
            }
            FormatCard("MD", "Markdown · 带待办", Glyph.Rows, format == "md", Modifier.weight(1f)) {
                format = "md"; result = null
            }
            FormatCard("JSON", "结构化 · 可再导入", Glyph.Layers, format == "json", Modifier.weight(1f)) {
                format = "json"; result = null
            }
        }
        Spacer(Modifier.height(16.dp))
        if (result == null) {
            PrimaryButton(
                if (busy) "正在生成…" else "导出到下载",
                {
                    if (busy) return@PrimaryButton
                    busy = true
                    scope.launch {
                        runCatching { exportNote(context, note, format) }
                            .onSuccess { result = it; ToastBus.show("已导出到 ${it.location}") }
                            .onFailure { ToastBus.show("导出失败：${it.message ?: "未知错误"}") }
                        busy = false
                    }
                },
                Modifier.fillMaxWidth(),
                glyph = Glyph.Export,
                enabled = !busy,
            )
        } else {
            val r = result!!
            MoyuSurface(SurfaceRole.CARD, Modifier.fillMaxWidth(), radius = 22f) {
                Column(Modifier.padding(16.dp)) {
                    Txt("导出完成", size = 13.5f, weight = FontWeight.Medium)
                    Txt(r.fileName, size = 9.5f, color = ui.palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(9.dp))
                    Txt(r.location, size = 9f, color = ui.palette.muted, lineHeight = 14f)
                    Spacer(Modifier.height(10.dp))
                    SaveAsButton(r)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("打开", { openExport(context, r) }, Modifier.weight(1f), glyph = Glyph.Note)
                        GlassButton("分享", { shareExport(context, r) }, Modifier.weight(1f), glyph = Glyph.Share)
                        GlassButton("完成", onDismiss, Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FormatCard(
    name: String,
    detail: String,
    glyph: Glyph,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ui = LocalInk.current
    val border by animateFloatAsState(if (selected) 1.6f else .8f, spring(.9f, 500f), label = "border")
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) ui.palette.ink.copy(alpha = if (ui.palette.isDark) .10f else .055f) else ui.palette.faint)
            .then(if (selected) Modifier.border(border.dp, ui.accent, RoundedCornerShape(20.dp)) else Modifier.border(.8.dp, ui.palette.line, RoundedCornerShape(20.dp)))
            .bouncy(scale = .98f) { onClick() }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon(glyph, if (selected) ui.accent else ui.palette.muted, 19.dp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(if (selected) ui.accent else Color.Transparent)
                    .border(1.2.dp, if (selected) ui.accent else ui.palette.muted.copy(alpha = .4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(5.dp).clip(CircleShape).background(ui.palette.bgTop))
            }
        }
        Spacer(Modifier.height(10.dp))
        Txt(name, size = 14f, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Txt(detail, size = 8.5f, color = ui.palette.muted, maxLines = 1)
    }
}

/** Generates the EPUB cover with the same palette as the in-app shelf. */
object CoverArt {
    fun render(context: Context, book: Book): Bitmap {
        val w = 800
        val h = 1176
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val a = coverColorA(book.coverStyle).toArgb()
        val b = coverColorB(book.coverStyle).toArgb()
        val ink = coverInk(book.coverStyle).toArgb()
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a, b, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val custom = loadBitmap(context, book.coverUri)
        if (custom != null) {
            val src = Rect(0, 0, custom.width, custom.height)
            val scale = maxOf(w.toFloat() / custom.width, h.toFloat() / custom.height)
            val dw = (custom.width * scale).toInt()
            val dh = (custom.height * scale).toInt()
            val dst = Rect((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2)
            canvas.drawBitmap(custom, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawRect(
                0f, 0f, w.toFloat(), h.toFloat(),
                Paint().apply {
                    shader = LinearGradient(
                        0f, h * .3f, 0f, h.toFloat(),
                        intArrayOf(0x00000000, 0x55000000.toInt(), 0xCC000000.toInt()),
                        floatArrayOf(0f, .55f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
        } else {
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.argb(30, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 2f }
            when (book.coverStyle.mod(8)) {
                0 -> {
                    canvas.drawCircle(w * .3f, h * .18f, w * .4f, Paint().apply { color = AColor.argb(18, 255, 255, 255) })
                    canvas.drawArc(w * .16f, h * .12f, w * 1.02f, h * .68f, -36f, 132f, false, white)
                    canvas.drawLine(w * .14f, h * .62f, w * .86f, h * .62f, white)
                }
                1 -> {
                    var i = 0
                    while (i < 8) {
                        canvas.drawLine(w * .12f, h * .16f + i * 64f, w * (.6f + i * .035f), h * .16f + i * 64f, white)
                        i++
                    }
                }
                2 -> {
                    var i = 0
                    while (i < 6) {
                        val p = android.graphics.Path()
                        p.moveTo(w * .08f, h * (.2f + i * .1f))
                        p.cubicTo(w * .35f, h * (.15f + i * .1f), w * .65f, h * (.28f + i * .1f), w * .94f, h * (.2f + i * .1f))
                        canvas.drawPath(p, white)
                        i++
                    }
                }
                3 -> {
                    val p = android.graphics.Path()
                    p.moveTo(0f, h * .3f); p.lineTo(w.toFloat(), h * .17f); p.lineTo(w.toFloat(), h * .33f); p.lineTo(0f, h * .46f); p.close()
                    canvas.drawPath(p, Paint().apply { color = AColor.argb(18, 255, 255, 255) })
                }
                4 -> {
                    canvas.drawRect(w * .5f, h * .06f, w.toFloat(), h * .3f, Paint().apply { color = AColor.argb(20, 255, 255, 255) })
                }
                5 -> {
                    var i = 0
                    while (i < 10) {
                        canvas.drawLine(w * .08f, h * (.16f + i * .06f), w * .92f, h * (.13f + i * .06f), white)
                        i++
                    }
                }
                6 -> {
                    canvas.drawLine(w * .12f, h * .12f, w * .12f, h * .86f, Paint().apply { color = AColor.argb(55, AColor.red(ink), AColor.green(ink), AColor.blue(ink)); strokeWidth = 4f })
                }
                else -> {
                    var i = 0
                    while (i < 6) {
                        canvas.drawCircle(w * .84f, h * (.13f + i * .13f), w * .12f, white)
                        i++
                    }
                }
            }
        }
        val inkColor = if (custom != null) AColor.WHITE else ink
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = inkColor; alpha = 150; textSize = 26f; typeface = Typeface.create("sans", Typeface.NORMAL); letterSpacing = .18f }
        canvas.drawText(book.genre.ifBlank { "小说" }, 72f, 112f, light)
        canvas.drawLine(72f, h * .68f, 222f, h * .68f, Paint().apply { this.color = inkColor; alpha = 100; strokeWidth = 3f })

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = inkColor
            textSize = 92f
            typeface = Typeface.create("serif", Typeface.BOLD)
            letterSpacing = .04f
        }
        drawWrapped(canvas, book.title.ifBlank { "未命名" }, 72f, h * .75f, w - 144f, titlePaint, 2, 108f)
        if (book.author.isNotBlank()) {
            val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = inkColor; alpha = 170; textSize = 30f; typeface = Typeface.create("sans", Typeface.NORMAL)
            }
            canvas.drawText(book.author, 74f, h * .9f, authorPaint)
        }
        val credit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = inkColor; alpha = 95; textSize = 21f; typeface = Typeface.create("sans", Typeface.NORMAL); letterSpacing = .2f
        }
        canvas.drawText("字隅", 74f, h - 54f, credit)
        return bitmap
    }

    private fun drawWrapped(canvas: Canvas, text: String, x: Float, baseline: Float, maxWidth: Float, paint: Paint, maxLines: Int, lineHeight: Float) {
        var line = StringBuilder()
        var lines = mutableListOf<String>()
        text.forEach { ch ->
            val test = line.toString() + ch
            if (paint.measureText(test) > maxWidth && line.isNotEmpty()) {
                lines.add(line.toString())
                line = StringBuilder().append(ch)
            } else {
                line.append(ch)
            }
        }
        if (line.isNotEmpty()) lines.add(line.toString())
        if (lines.size > maxLines) {
            lines = lines.take(maxLines).toMutableList()
            lines[maxLines - 1] = lines.last().dropLast(1) + "…"
        }
        lines.forEachIndexed { i, s -> canvas.drawText(s, x, baseline + i * lineHeight, paint) }
    }

    private fun loadBitmap(context: Context, path: String?): Bitmap? = runCatching {
        if (path.isNullOrBlank()) return null
        val raw = if (path.startsWith("content:") || path.startsWith("file:")) {
            context.contentResolver.openInputStream(Uri.parse(path))?.use { BitmapFactory.decodeStream(it) }
        } else BitmapFactory.decodeFile(path)
        raw
    }.getOrNull()
}
