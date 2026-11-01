package com.moyu.writer

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

data class Chapter(
    val id: String = uuid(),
    val title: String = "第一章",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class Book(
    val id: String = uuid(),
    val title: String = "未命名",
    val author: String = "",
    val genre: String = "小说",
    val synopsis: String = "",
    val coverStyle: Int = 0,
    val coverUri: String? = null,
    val chapters: List<Chapter> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val groupId: String? = null,
) {
    val words: Int get() = chapters.sumOf { countWords(it.content) }

    fun chapter(id: String?): Chapter? = chapters.firstOrNull { it.id == id } ?: chapters.firstOrNull()
    fun lastChapter(): Chapter? = chapters.maxByOrNull { it.updatedAt } ?: chapters.lastOrNull()
}

data class Todo(
    val id: String = uuid(),
    val text: String = "",
    val done: Boolean = false,
    val remindAt: Long? = null,
    val notified: Boolean = false,
    val start: Int = -1,
    val end: Int = -1,
)

data class Note(
    val id: String = uuid(),
    val title: String = "无标题笔记",
    val content: String = "",
    val todos: List<Todo> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val groupId: String? = null,
) {
    val words: Int get() = countWords(content)
    fun pendingTodos(): List<Todo> = todos.filter { !it.done }
    fun nextReminder(): Todo? = todos.filter { !it.done && it.remindAt != null }.minByOrNull { it.remindAt!! }
}

enum class GroupKind { BOOK, NOTE }

data class Group(
    val id: String = uuid(),
    val name: String = "新分组",
    val color: Long = 0xFF8A7A62,
    val order: Int = 0,
)

data class AppSettings(
    val theme: String = "paper",
    val accent: Long = 0xFF22221E,
    val font: String = "serif",
    val fontScale: Float = 1f,
    val lineHeight: Float = 1.82f,
    val pageMargin: Float = 22f,
    val glow: Boolean = true,
    val grain: Boolean = true,
    val motion: String = "spring",
    val pageMotion: String = "slide",
    val libraryMode: String = "grid",
    val noteMode: String = "list",
    val librarySort: String = "updated",
    val columns: Int = 3,
    val cardScale: Float = 1f,
    val coverRadius: Float = 18f,
    val showTabLabels: Boolean = true,
    val tabNames: List<String> = listOf("书架", "笔记", "数据", "设置"),
    val tabStyle: String = "glass",
    val dailyGoal: Int = 2000,
    val indent: Boolean = true,
    val sectionStyle: String = "asterisk",
    val sectionSymbol: String = "＊　＊　＊",
    val autoSave: Int = 700,
    val showWordCount: Boolean = true,
    val showStats: Boolean = true,
    val haptics: Boolean = true,
    val appName: String = "字隅",
    val quote: String = "把故事，写进字的角落。",
    val showFps: Boolean = false,
    val autoUpdate: Boolean = true,
    val paperStyle: String = "plain",
    val paperLineAlpha: Float = .5f,
    val onboardingDone: Boolean = false,
    val materialVersion: Int = 1,
    val materials: Map<String, Map<String, MaterialSpec>> = emptyMap(),
)

data class AppData(
    val books: List<Book> = emptyList(),
    val notes: List<Note> = emptyList(),
    val bookGroups: List<Group> = emptyList(),
    val noteGroups: List<Group> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val dailyWords: Map<String, Int> = emptyMap(),
    val lastBookId: String? = null,
    val lastChapterId: String? = null,
    val version: Int = 1,
)

class AppStore(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "moyu.json")
    private val backup = File(appContext.filesDir, "moyu.backup.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: kotlinx.coroutines.Job? = null

    var data by mutableStateOf(normalize(load()))
        private set

    val settings: AppSettings get() = data.settings
    val books: List<Book>
        get() {
            val order = when (data.settings.librarySort) {
                "title" -> compareBy<Book> { it.title.lowercase() }
                "created" -> compareByDescending<Book> { it.createdAt }
                "words" -> compareByDescending<Book> { it.words }
                else -> compareByDescending<Book> { it.updatedAt }
            }
            return data.books.sortedWith(compareByDescending<Book> { it.pinned }.then(order))
        }
    val notes: List<Note> get() = data.notes.sortedWith(
        compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt }
    )

    fun mutate(transform: (AppData) -> AppData) {
        data = transform(data)
        scheduleSave()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        mutate { it.copy(settings = transform(it.settings)) }
    }

    fun book(id: String?): Book? = data.books.firstOrNull { it.id == id }

    val bookGroups: List<Group> get() = data.bookGroups.sortedBy { it.order }
    val noteGroups: List<Group> get() = data.noteGroups.sortedBy { it.order }

    fun group(kind: GroupKind, id: String?): Group? =
        if (kind == GroupKind.BOOK) data.bookGroups.firstOrNull { it.id == id }
        else data.noteGroups.firstOrNull { it.id == id }

    fun addGroup(kind: GroupKind, group: Group) {
        mutate { d ->
            val groups = if (kind == GroupKind.BOOK) d.bookGroups else d.noteGroups
            val nextOrder = (groups.maxOfOrNull { it.order } ?: -1) + 1
            val added = group.copy(order = nextOrder)
            if (kind == GroupKind.BOOK) d.copy(bookGroups = groups + added)
            else d.copy(noteGroups = groups + added)
        }
    }

    fun updateGroup(kind: GroupKind, id: String, transform: (Group) -> Group) {
        mutate { d ->
            if (kind == GroupKind.BOOK) d.copy(bookGroups = d.bookGroups.map { if (it.id == id) transform(it) else it })
            else d.copy(noteGroups = d.noteGroups.map { if (it.id == id) transform(it) else it })
        }
    }

    fun deleteGroup(kind: GroupKind, id: String) {
        mutate { d ->
            if (kind == GroupKind.BOOK) {
                d.copy(
                    bookGroups = d.bookGroups.filterNot { it.id == id },
                    books = d.books.map { if (it.groupId == id) it.copy(groupId = null) else it },
                )
            } else {
                d.copy(
                    noteGroups = d.noteGroups.filterNot { it.id == id },
                    notes = d.notes.map { if (it.groupId == id) it.copy(groupId = null) else it },
                )
            }
        }
    }

    fun moveGroup(kind: GroupKind, id: String, delta: Int) {
        mutate { d ->
            val source = if (kind == GroupKind.BOOK) d.bookGroups else d.noteGroups
            val sorted = source.sortedBy { it.order }
            val index = sorted.indexOfFirst { it.id == id }
            val other = (index + delta).coerceIn(0, sorted.lastIndex)
            if (index < 0 || index == other) d else {
                val swapped = sorted.toMutableList().also {
                    val tmp = it[index]
                    it[index] = it[other]
                    it[other] = tmp
                }.mapIndexed { i, g -> g.copy(order = i) }
                if (kind == GroupKind.BOOK) d.copy(bookGroups = swapped) else d.copy(noteGroups = swapped)
            }
        }
    }

    fun setBookGroup(bookId: String, groupId: String?) {
        mutate { d ->
            d.copy(books = d.books.map { if (it.id == bookId) it.copy(groupId = groupId) else it })
        }
    }

    fun setNoteGroup(noteId: String, groupId: String?) {
        mutate { d ->
            d.copy(notes = d.notes.map { if (it.id == noteId) it.copy(groupId = groupId) else it })
        }
    }

    fun addBook(book: Book) {
        mutate { d ->
            d.copy(
                books = d.books + book,
                lastBookId = book.id,
                lastChapterId = book.chapters.firstOrNull()?.id,
            )
        }
    }

    fun updateBook(id: String, transform: (Book) -> Book) {
        mutate { d ->
            d.copy(books = d.books.map { if (it.id == id) transform(it).copy(updatedAt = now()) else it })
        }
    }

    fun deleteBook(id: String) {
        mutate { d ->
            d.copy(
                books = d.books.filterNot { it.id == id },
                lastBookId = if (d.lastBookId == id) d.books.firstOrNull { it.id != id }?.id else d.lastBookId,
                lastChapterId = if (d.lastBookId == id) null else d.lastChapterId,
            )
        }
    }

    fun touchBook(id: String, chapterId: String?) {
        mutate { d ->
            d.copy(
                lastBookId = id,
                lastChapterId = chapterId,
                books = d.books.map { b ->
                    if (b.id == id) b.copy(updatedAt = now()) else b
                }
            )
        }
    }

    fun addChapter(bookId: String, chapter: Chapter) {
        mutate { d ->
            d.copy(
                books = d.books.map { b ->
                    if (b.id == bookId) b.copy(chapters = b.chapters + chapter, updatedAt = now()) else b
                },
                lastBookId = bookId,
                lastChapterId = chapter.id,
            )
        }
    }

    fun updateChapter(bookId: String, chapterId: String, title: String? = null, content: String? = null) {
        mutate { d ->
            var delta = 0
            val books = d.books.map { b ->
                if (b.id != bookId) b else {
                    val chapters = b.chapters.map { c ->
                        if (c.id != chapterId) c else {
                            val newContent = content ?: c.content
                            delta += countWords(newContent) - countWords(c.content)
                            c.copy(
                                title = title ?: c.title,
                                content = newContent,
                                updatedAt = now(),
                            )
                        }
                    }
                    b.copy(chapters = chapters, updatedAt = now())
                }
            }
            val today = todayKey()
            val daily = if (delta != 0) {
                d.dailyWords.toMutableMap().also { it[today] = max(0, (it[today] ?: 0) + delta) }
            } else d.dailyWords
            d.copy(books = books, dailyWords = daily, lastBookId = bookId, lastChapterId = chapterId)
        }
    }

    fun deleteChapter(bookId: String, chapterId: String) {
        mutate { d ->
            d.copy(books = d.books.map { b ->
                if (b.id == bookId) b.copy(chapters = b.chapters.filterNot { it.id == chapterId }) else b
            })
        }
    }

    fun addNote(note: Note) {
        mutate { it.copy(notes = it.notes + note) }
    }

    fun updateNote(id: String, transform: (Note) -> Note) {
        mutate { d ->
            d.copy(notes = d.notes.map {
                if (it.id == id) transform(it).copy(updatedAt = now()) else it
            })
        }
    }

    fun deleteNote(id: String) {
        mutate { it.copy(notes = it.notes.filterNot { n -> n.id == id }) }
    }

    fun markReminderDone(noteId: String, todoId: String) {
        updateNote(noteId) { n ->
            n.copy(todos = n.todos.map { if (it.id == todoId) it.copy(done = true) else it })
        }
    }

    fun replaceAll(replacement: AppData) {
        data = normalize(replacement)
        saveNow()
    }

    fun clearAll(books: Boolean = true, notes: Boolean = true) {
        mutate { d ->
            d.copy(
                books = if (books) emptyList() else d.books,
                notes = if (notes) emptyList() else d.notes,
                lastBookId = if (books) null else d.lastBookId,
                lastChapterId = if (books) null else d.lastChapterId,
                dailyWords = if (books && notes) emptyMap() else d.dailyWords,
            )
        }
    }

    fun storageBytes(): Long = file.length() + backup.length()

    fun wordsOn(date: LocalDate): Int = data.dailyWords[date.format(DateTimeFormatter.ISO_DATE)] ?: 0

    fun todayWords(): Int = wordsOn(LocalDate.now())

    fun streak(): Int {
        var day = LocalDate.now()
        if (wordsOn(day) == 0) day = day.minusDays(1)
        var count = 0
        while (wordsOn(day) > 0 && count < 999) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            writeNow()
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        scope.launch { writeNow() }
    }

    fun flush() {
        saveJob?.cancel()
        writeNow()
    }

    private fun writeNow() {
        try {
            val json = encodeData(data)
            val tmp = File(appContext.filesDir, "moyu.tmp")
            tmp.writeText(json, Charsets.UTF_8)
            if (file.exists()) file.copyTo(backup, overwrite = true)
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } catch (_: Throwable) {
        }
    }

    private fun normalize(data: AppData): AppData {
        // Keep the visible fallback for notes with an empty title.
        val fixedNotes = data.notes.map { note ->
            if (note.title.isBlank()) note.copy(title = "无标题笔记") else note
        }
        val bookGroups = data.bookGroups.distinctBy { it.id }.sortedBy { it.order }
            .mapIndexed { i, g -> g.copy(order = i) }
        val noteGroups = data.noteGroups.distinctBy { it.id }.sortedBy { it.order }
            .mapIndexed { i, g -> g.copy(order = i) }
        val bookIds = bookGroups.map { it.id }.toSet()
        val noteIds = noteGroups.map { it.id }.toSet()
        val fixedBooks = data.books.map { if (it.groupId != null && it.groupId !in bookIds) it.copy(groupId = null) else it }
        val fixedNotesWithGroups = fixedNotes.map {
            if (it.groupId != null && it.groupId !in noteIds) it.copy(groupId = null) else it
        }
        val oldQuote = "把故事，写成一座岛。"
        var settings = data.settings.copy(
            // Migrate only the old defaults; a user-customized name is preserved.
            appName = if (data.settings.appName in setOf("", "墨屿", "笔滞", "3Cne")) "字隅" else data.settings.appName,
            quote = if (data.settings.quote == oldQuote) "把故事，写进字的角落。" else data.settings.quote,
        )
        if (settings.paperStyle !in setOf("plain", "ruled", "solid")) {
            settings = settings.copy(paperStyle = "plain")
        }
        if (settings.materialVersion < 1) {
            val migrated = settings.materials.mapValues { (_, roles) ->
                roles.mapValues { (role, spec) ->
                    spec.copy(
                        edge = when (role) {
                            SurfaceRole.CONTROL.key -> 2.5f
                            SurfaceRole.PAPER.key -> 0f
                            else -> 8f
                        },
                    )
                }
            }
            settings = settings.copy(materials = migrated, materialVersion = 1)
        }
        return data.copy(
            books = fixedBooks,
            notes = fixedNotesWithGroups,
            bookGroups = bookGroups,
            noteGroups = noteGroups,
            settings = settings,
        )
    }

    private fun load(): AppData {
        return try {
            if (file.exists()) decodeData(file.readText(Charsets.UTF_8))
            else AppData()
        } catch (_: Throwable) {
            try {
                if (backup.exists()) decodeData(backup.readText(Charsets.UTF_8)) else AppData()
            } catch (_: Throwable) {
                AppData()
            }
        }
    }
}


fun sectionBreakText(settings: AppSettings): String {
    val symbol = when (settings.sectionStyle) {
        "line" -> "——"
        "dots" -> "·　·　·"
        "blank" -> ""
        "custom" -> settings.sectionSymbol.ifBlank { "＊　＊　＊" }
        else -> "＊　＊　＊"
    }
    return if (symbol.isBlank()) "\n\n\n" else "\n\n$symbol\n\n"
}

fun backupJson(data: AppData): String = encodeData(data)

fun parseBackup(text: String): AppData = decodeData(text)

fun uuid(): String = UUID.randomUUID().toString()
fun now(): Long = System.currentTimeMillis()
fun todayKey(): String = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

const val REMINDER_LINE_MARK = "◷"

fun isReminderLine(line: String): Boolean =
    line.trimStart().startsWith(REMINDER_LINE_MARK)

fun contentWithoutReminders(text: String): String =
    text.lineSequence().filterNot { isReminderLine(it) }.joinToString("\n")

fun countWords(text: String): Int {
    if (text.isBlank()) return 0
    var count = 0
    contentWithoutReminders(text).forEach { c ->
        if (!c.isWhitespace()) count++
    }
    return count
}

fun formatWords(n: Int): String = when {
    n >= 100000 -> String.format("%.1f万", n / 10000f)
    n >= 10000 -> String.format("%.1f万", n / 10000f)
    else -> n.toString()
}

fun relativeTime(time: Long): String {
    val diff = now() - time
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 172_800_000 -> "昨天"
        diff < 604_800_000 -> "${diff / 86_400_000} 天前"
        else -> java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("M月d日"))
    }
}

fun formatDateTime(ms: Long): String {
    val d = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    return d.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
}

fun formatTime(ms: Long): String {
    val d = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    return d.format(DateTimeFormatter.ofPattern("HH:mm"))
}

/** Encoding is intentionally plain JSON so a backup stays readable and version tolerant. */
private fun encodeData(data: AppData): String {
    val root = JSONObject()
    root.put("version", 1)
    root.put("lastBookId", data.lastBookId ?: JSONObject.NULL)
    root.put("lastChapterId", data.lastChapterId ?: JSONObject.NULL)
    root.put("settings", encodeSettings(data.settings))
    val daily = JSONObject()
    data.dailyWords.forEach { (k, v) -> daily.put(k, v) }
    root.put("dailyWords", daily)
    val books = JSONArray()
    data.books.forEach { b ->
        val chapters = JSONArray()
        b.chapters.forEach { c ->
            chapters.put(JSONObject().apply {
                put("id", c.id); put("title", c.title); put("content", c.content)
                put("createdAt", c.createdAt); put("updatedAt", c.updatedAt)
            })
        }
        books.put(JSONObject().apply {
            put("id", b.id); put("title", b.title); put("author", b.author)
            put("genre", b.genre); put("synopsis", b.synopsis)
            put("coverStyle", b.coverStyle); put("coverUri", b.coverUri ?: JSONObject.NULL)
            put("chapters", chapters); put("createdAt", b.createdAt)
            put("updatedAt", b.updatedAt); put("pinned", b.pinned)
            put("groupId", b.groupId ?: JSONObject.NULL)
        })
    }
    root.put("books", books)
    root.put("bookGroups", encodeGroups(data.bookGroups))
    root.put("noteGroups", encodeGroups(data.noteGroups))
    val notes = JSONArray()
    data.notes.forEach { n ->
        val todos = JSONArray()
        n.todos.forEach { t ->
            todos.put(JSONObject().apply {
                put("id", t.id); put("text", t.text); put("done", t.done)
                put("remindAt", t.remindAt ?: JSONObject.NULL); put("notified", t.notified)
                put("start", t.start); put("end", t.end)
            })
        }
        notes.put(JSONObject().apply {
            put("id", n.id); put("title", n.title); put("content", n.content)
            put("todos", todos); put("createdAt", n.createdAt)
            put("updatedAt", n.updatedAt); put("pinned", n.pinned)
            put("groupId", n.groupId ?: JSONObject.NULL)
        })
    }
    root.put("notes", notes)
    return root.toString(2)
}

private fun encodeGroups(groups: List<Group>): JSONArray {
    val arr = JSONArray()
    groups.forEach { g ->
        arr.put(JSONObject().apply {
            put("id", g.id); put("name", g.name); put("color", g.color); put("order", g.order)
        })
    }
    return arr
}

private fun decodeGroups(a: JSONArray?): List<Group> {
    val out = mutableListOf<Group>()
    if (a == null) return out
    for (i in 0 until a.length()) {
        val o = a.optJSONObject(i) ?: continue
        out += Group(
            id = o.optString("id", uuid()),
            name = o.optString("name", "新分组"),
            color = o.optLong("color", 0xFF8A7A62),
            order = o.optInt("order", i),
        )
    }
    return out
}

private fun encodeSettings(s: AppSettings): JSONObject = JSONObject().apply {
    put("theme", s.theme); put("accent", s.accent); put("font", s.font)
    put("fontScale", s.fontScale.toDouble()); put("lineHeight", s.lineHeight.toDouble())
    put("pageMargin", s.pageMargin.toDouble())
    put("glow", s.glow); put("grain", s.grain)
    put("motion", s.motion); put("pageMotion", s.pageMotion)
    put("libraryMode", s.libraryMode); put("noteMode", s.noteMode)
    put("librarySort", s.librarySort); put("columns", s.columns)
    put("cardScale", s.cardScale.toDouble()); put("coverRadius", s.coverRadius.toDouble())
    put("showTabLabels", s.showTabLabels)
    put("tabNames", JSONArray(s.tabNames))
    put("tabStyle", s.tabStyle)
    put("dailyGoal", s.dailyGoal); put("indent", s.indent)
    put("sectionStyle", s.sectionStyle); put("sectionSymbol", s.sectionSymbol)
    put("autoSave", s.autoSave); put("showWordCount", s.showWordCount)
    put("showStats", s.showStats); put("haptics", s.haptics)
    put("appName", s.appName); put("quote", s.quote); put("showFps", s.showFps)
    put("autoUpdate", s.autoUpdate); put("paperStyle", s.paperStyle)
    put("paperLineAlpha", s.paperLineAlpha.toDouble())
    put("onboardingDone", s.onboardingDone)
    put("materialVersion", s.materialVersion)
    put("materials", encodeMaterials(s.materials))
}

private fun decodeSettings(o: JSONObject?) = AppSettings(
    theme = o?.optString("theme", "paper") ?: "paper",
    accent = o?.optLong("accent", 0xFF22221E) ?: 0xFF22221E,
    font = o?.optString("font", "serif") ?: "serif",
    fontScale = (o?.optDouble("fontScale", 1.0) ?: 1.0).toFloat(),
    lineHeight = (o?.optDouble("lineHeight", 1.82) ?: 1.82).toFloat(),
    pageMargin = (o?.optDouble("pageMargin", 22.0) ?: 22.0).toFloat(),
    glow = o?.optBoolean("glow", true) ?: true,
    grain = o?.optBoolean("grain", true) ?: true,
    motion = o?.optString("motion", "spring") ?: "spring",
    pageMotion = o?.optString("pageMotion", "slide") ?: "slide",
    libraryMode = o?.optString("libraryMode", "grid") ?: "grid",
    noteMode = o?.optString("noteMode", "list") ?: "list",
    librarySort = o?.optString("librarySort", "updated") ?: "updated",
    columns = o?.optInt("columns", 3) ?: 3,
    cardScale = (o?.optDouble("cardScale", 1.0) ?: 1.0).toFloat(),
    coverRadius = (o?.optDouble("coverRadius", 18.0) ?: 18.0).toFloat(),
    showTabLabels = o?.optBoolean("showTabLabels", true) ?: true,
    tabNames = o?.optJSONArray("tabNames")?.let { a -> (0 until a.length()).map { a.optString(it) } }
        ?: listOf("书架", "笔记", "数据", "设置"),
    tabStyle = o?.optString("tabStyle", "glass") ?: "glass",
    dailyGoal = o?.optInt("dailyGoal", 2000) ?: 2000,
    indent = o?.optBoolean("indent", true) ?: true,
    sectionStyle = o?.optString("sectionStyle", "asterisk") ?: "asterisk",
    sectionSymbol = o?.optString("sectionSymbol", "＊　＊　＊") ?: "＊　＊　＊",
    autoSave = o?.optInt("autoSave", 700) ?: 700,
    showWordCount = o?.optBoolean("showWordCount", true) ?: true,
    showStats = o?.optBoolean("showStats", true) ?: true,
    haptics = o?.optBoolean("haptics", true) ?: true,
    appName = o?.optString("appName", "字隅") ?: "字隅",
    quote = o?.optString("quote", "把故事，写进字的角落。") ?: "把故事，写进字的角落。",
    showFps = o?.optBoolean("showFps", false) ?: false,
    autoUpdate = o?.optBoolean("autoUpdate", true) ?: true,
    paperStyle = when (o?.optString("paperStyle", "plain")) {
        "ruled" -> "ruled"
        "solid" -> "solid"
        else -> "plain"
    },
    paperLineAlpha = (o?.optDouble("paperLineAlpha", .5) ?: .5).toFloat(),
    onboardingDone = o?.optBoolean("onboardingDone", false) ?: false,
    materialVersion = o?.optInt("materialVersion", 0) ?: 0,
    materials = decodeMaterials(o?.optJSONObject("materials")),
)

private fun decodeData(text: String): AppData {
    val root = JSONObject(text)
    val settings = decodeSettings(root.optJSONObject("settings"))
    val books = mutableListOf<Book>()
    val ba = root.optJSONArray("books") ?: JSONArray()
    for (i in 0 until ba.length()) {
        val o = ba.optJSONObject(i) ?: continue
        val chapters = mutableListOf<Chapter>()
        val ca = o.optJSONArray("chapters") ?: JSONArray()
        for (j in 0 until ca.length()) {
            val c = ca.optJSONObject(j) ?: continue
            chapters += Chapter(
                id = c.optString("id", uuid()),
                title = c.optString("title", "第${j + 1}章"),
                content = c.optString("content", ""),
                createdAt = c.optLong("createdAt", now()),
                updatedAt = c.optLong("updatedAt", now()),
            )
        }
        books += Book(
            id = o.optString("id", uuid()),
            title = o.optString("title", "未命名"),
            author = o.optString("author", ""),
            genre = o.optString("genre", "小说"),
            synopsis = o.optString("synopsis", ""),
            coverStyle = o.optInt("coverStyle", 0),
            coverUri = if (o.isNull("coverUri")) null else o.optString("coverUri"),
            chapters = chapters,
            createdAt = o.optLong("createdAt", now()),
            updatedAt = o.optLong("updatedAt", now()),
            pinned = o.optBoolean("pinned", false),
            groupId = if (o.isNull("groupId")) null else o.optString("groupId"),
        )
    }
    val notes = mutableListOf<Note>()
    val na = root.optJSONArray("notes") ?: JSONArray()
    for (i in 0 until na.length()) {
        val o = na.optJSONObject(i) ?: continue
        val todos = mutableListOf<Todo>()
        val ta = o.optJSONArray("todos") ?: JSONArray()
        for (j in 0 until ta.length()) {
            val t = ta.optJSONObject(j) ?: continue
            todos += Todo(
                id = t.optString("id", uuid()),
                text = t.optString("text", ""),
                done = t.optBoolean("done", false),
                remindAt = if (t.isNull("remindAt")) null else t.optLong("remindAt"),
                notified = t.optBoolean("notified", false),
                start = t.optInt("start", -1),
                end = t.optInt("end", -1),
            )
        }
        notes += Note(
            id = o.optString("id", uuid()),
            title = o.optString("title", "无标题笔记"),
            content = o.optString("content", ""),
            todos = todos,
            createdAt = o.optLong("createdAt", now()),
            updatedAt = o.optLong("updatedAt", now()),
            pinned = o.optBoolean("pinned", false),
            groupId = if (o.isNull("groupId")) null else o.optString("groupId"),
        )
    }
    val daily = mutableMapOf<String, Int>()
    root.optJSONObject("dailyWords")?.let { d ->
        d.keys().forEach { k -> daily[k] = d.optInt(k, 0) }
    }
    return AppData(
        books = books,
        notes = notes,
        bookGroups = decodeGroups(root.optJSONArray("bookGroups")),
        noteGroups = decodeGroups(root.optJSONArray("noteGroups")),
        settings = settings,
        dailyWords = daily,
        lastBookId = if (root.isNull("lastBookId")) null else root.optString("lastBookId"),
        lastChapterId = if (root.isNull("lastChapterId")) null else root.optString("lastChapterId"),
    )
}

fun accentFor(accent: Long, dark: Boolean): androidx.compose.ui.graphics.Color {
    val c = androidx.compose.ui.graphics.Color(accent)
    if (!dark) return c
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(accent.toInt(), hsv)
    hsv[1] = (hsv[1] * .72f).coerceIn(0f, 1f)
    hsv[2] = max(hsv[2], .82f)
    return androidx.compose.ui.graphics.Color(AndroidColor.HSVToColor(hsv))
}

fun readableOn(color: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color {
    val luminance = .2126f * color.red + .7152f * color.green + .0722f * color.blue
    return if (luminance > .62f) androidx.compose.ui.graphics.Color(0xFF171713) else androidx.compose.ui.graphics.Color(0xFFF7F3EA)
}
