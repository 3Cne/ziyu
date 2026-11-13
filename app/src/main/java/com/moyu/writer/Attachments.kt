package com.moyu.writer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/**
 * One file the writer attached to the conversation. The text is kept in memory
 * for the session only: it is never written to `moyu.json`, so a backup cannot
 * carry a stray manuscript the writer only wanted to ask about.
 */
data class Attachment(
    val name: String,
    val text: String,
    val chars: Int = text.length,
    val kind: String = "txt",
)

/**
 * Reads what the system file picker handed back. Only plain text and EPUB are
 * accepted — the assistant has no renderer, and pretending to read a .docx as
 * text would produce plausible garbage.
 */
object Attachments {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_EPUB_UNPACKED = 8 * 1024 * 1024
    private const val MAX_EPUB_ENTRIES = 600

    private val TEXT_EXT = setOf("txt", "md", "markdown", "json", "csv", "log")

    suspend fun read(context: Context, uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val (name, size) = meta(context, uri)
        val safeName = name.ifBlank { "附件" }
        if (size > MAX_BYTES) error("文件超过 2MB，先拆分或压缩")
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(MAX_BYTES) }
            ?: error("读不到这个文件")
        if (bytes.size > MAX_BYTES) error("文件超过 2MB，先拆分或压缩")
        val ext = safeName.substringAfterLast('.', "").lowercase()
        when {
            ext == "epub" -> {
                val text = epubText(bytes)
                if (text.isBlank()) error("这本 EPUB 里没有可读正文（可能是加密或纯图片）")
                Attachment(safeName, text, text.length, "epub")
            }
            ext in TEXT_EXT || ext.isEmpty() -> {
                val text = decodeText(bytes)
                if (text.isBlank()) error("这个文件是空的")
                Attachment(safeName, text, text.length, "text")
            }
            else -> error("只支持 TXT / MD / JSON / EPUB，其他格式先转成文本")
        }
    }

    private fun meta(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment.orEmpty().substringAfterLast('/')
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0) name = c.getString(ni).orEmpty()
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        return name to size
    }

    /** UTF-8 first; Chinese .txt from Windows is often GBK, so fall back once. */
    private fun decodeText(bytes: ByteArray): String {
        return try {
            val dec = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            dec.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            runCatching { String(bytes, charset("GBK")) }.getOrDefault(String(bytes, Charsets.UTF_8))
        }
    }

    /**
     * Minimal EPUB reader: container.xml → OPF spine → XHTML text. No images,
     * no CSS, no DRM. A spine-less EPUB falls back to every XHTML entry in name
     * order, which is what most exporters produce anyway.
     */
    private fun epubText(bytes: ByteArray): String {
        val entries = LinkedHashMap<String, ByteArray>()
        var unpacked = 0
        ZipInputStream(bytes.inputStream().buffered()).use { zip ->
            var count = 0
            while (true) {
                val e = zip.nextEntry ?: break
                if (++count > MAX_EPUB_ENTRIES) error("这本 EPUB 条目太多，先解压再看")
                if (e.isDirectory) continue
                val name = e.name.replace('\\', '/')
                val data = zip.readBounded(MAX_EPUB_UNPACKED - unpacked)
                unpacked += data.size
                if (unpacked > MAX_EPUB_UNPACKED) error("这本 EPUB 解压后超过 8MB")
                entries[name] = data
            }
        }
        val container = entries["META-INF/container.xml"]
        var opfPath = container?.let { rootfilePath(it) }
        if (opfPath.isNullOrBlank()) opfPath = entries.keys.firstOrNull { it.endsWith(".opf", true) }
        val ordered = ArrayList<String>()
        if (opfPath != null) {
            val opf = entries[opfPath]
            if (opf != null) ordered += spineHrefs(opf, opfPath, entries.keys)
        }
        if (ordered.isEmpty()) {
            ordered += entries.keys.filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true) }
                .sorted()
        }
        val sb = StringBuilder()
        for (path in ordered) {
            val data = entries[path] ?: continue
            val body = htmlToText(String(data, Charsets.UTF_8))
            if (body.isBlank()) continue
            if (sb.isNotEmpty()) sb.append("\n\n\n")
            sb.append(body)
        }
        return sb.toString().trim()
    }

    private fun rootfilePath(xml: ByteArray): String? {
        return runCatching {
            val p = Xml.newPullParser()
            p.setInput(xml.inputStream(), "UTF-8")
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "rootfile") {
                    val path = p.getAttributeValue(null, "full-path")
                    if (!path.isNullOrBlank()) return@runCatching path
                }
                p.next()
            }
            null
        }.getOrNull()
    }

    /** manifest id→href, then spine order; hrefs resolved against the OPF folder. */
    private fun spineHrefs(opf: ByteArray, opfPath: String, names: Set<String>): List<String> {
        val dir = opfPath.substringBeforeLast('/', "")
        val manifest = HashMap<String, String>()
        val spine = ArrayList<String>()
        runCatching {
            val p = Xml.newPullParser()
            p.setInput(opf.inputStream(), "UTF-8")
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "item" -> {
                            val id = p.getAttributeValue(null, "id").orEmpty()
                            val href = p.getAttributeValue(null, "href").orEmpty()
                            if (id.isNotBlank() && href.isNotBlank()) manifest[id] = href
                        }
                        "itemref" -> {
                            val idref = p.getAttributeValue(null, "idref").orEmpty()
                            if (idref.isNotBlank()) spine += idref
                        }
                    }
                }
                p.next()
            }
        }
        val out = ArrayList<String>()
        for (id in spine) {
            val href = manifest[id] ?: continue
            val path = normalize(dir, href)
            if (path in names) out += path
        }
        return out
    }

    private fun normalize(dir: String, href: String): String {
        val clean = href.substringBefore('#').substringBefore('?')
        val decoded = runCatching { java.net.URLDecoder.decode(clean, "UTF-8") }.getOrDefault(clean)
        val parts = ArrayList<String>()
        if (dir.isNotBlank()) parts += dir.split('/').filter { it.isNotBlank() }
        decoded.split('/').forEach { seg ->
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += seg
            }
        }
        return parts.joinToString("/")
    }

    private val TAG = Regex("<[^>]*>")
    private val SCRIPT = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val BREAK = Regex("(?i)<(br|/p|/div|/h[1-6]|/li|/tr)[^>]*>")

    fun htmlToText(html: String): String {
        var s = html.replace(SCRIPT, " ")
        s = BREAK.replace(s, "\n")
        s = TAG.replace(s, " ")
        s = unescape(s)
        return s.lineSequence()
            .map { it.replace('\u00A0', ' ').replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")

    private fun unescape(s: String): String = ENTITY.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x", true) -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            e == "nbsp" -> " "
            e == "amp" -> "&"
            e == "lt" -> "<"
            e == "gt" -> ">"
            e == "quot" -> "\""
            e == "apos" -> "'"
            e == "mdash" -> "—"
            e == "ndash" -> "–"
            e == "hellip" -> "…"
            e == "ldquo" -> "“"
            e == "rdquo" -> "”"
            e == "lsquo" -> "‘"
            e == "rsquo" -> "’"
            else -> m.value
        }
    }

    private fun InputStream.readBounded(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n <= 0) break
            total += n
            if (total > max) error("文件太大")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
