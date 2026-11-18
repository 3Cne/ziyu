package com.moyu.writer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 3.26.23：故事圣经是 AI 唯一的记忆层。
 *
 * 三条约定：
 * - 所有 AI 增删改都记进 `filesDir/ai/bible-journal.json`：每条存「改动前 / 改动后」的整份 bible，
 *   所以「撤销最近一次 AI 改动」不需要反推操作，直接把 before 写回去；
 * - `source` 区分发起方：`ai`（工具，作者让 AI 改）、`text`（章节保存后自动整理）、`author`（作者在故事页手改）；
 * - 自动整理（`text`）**不覆盖** `origin == "author"` 的条目，只记一条待看冲突。
 */
object AiBible {
    private const val MAX_JOURNAL = 100

    private fun dir(ctx: Context): File = File(ctx.filesDir, "ai").apply { mkdirs() }
    private fun journalFile(ctx: Context): File = File(dir(ctx), "bible-journal.json")

    // ───────────────────────── 读 ─────────────────────────

    fun summary(book: Book): String {
        val b = book.bible
        if (b.isEmpty) return "《${book.title}》的故事圣经还空着（没有角色、关系、事件、大纲）。"
        val sb = StringBuilder("《${book.title}》故事圣经：\n")
        if (b.characters.isNotEmpty()) {
            sb.append("角色：\n")
            b.characters.forEach { c ->
                sb.append("- ${c.name}")
                if (c.role.isNotBlank()) sb.append("（${c.role}）")
                if (c.note.isNotBlank()) sb.append("：${c.note}")
                sb.append(if (c.origin == "author") " [作者设定]" else " [${c.origin}]")
                sb.append('\n')
            }
        }
        if (b.relations.isNotEmpty()) {
            sb.append("关系：\n")
            b.relations.forEach { r ->
                sb.append("- ${charName(book, r.from)} — ${charName(book, r.to)}：${r.kind.label}")
                sb.append(if (r.origin == "author") " [作者设定]" else " [${r.origin}]")
                sb.append('\n')
            }
        }
        if (b.events.isNotEmpty()) {
            sb.append("事件（按故事时间）：\n")
            b.events.sortedBy { storyTimeKey(it.whenText) ?: Int.MAX_VALUE }.forEach { e ->
                sb.append("- ${e.whenText.ifBlank { "时间不详" }} · ${e.title.ifBlank { "未命名" }}")
                if (e.note.isNotBlank()) sb.append("：${e.note}")
                val ch = e.chapterIds.mapNotNull { id -> book.chapters.firstOrNull { it.id == id }?.title }
                if (ch.isNotEmpty()) sb.append("（${ch.joinToString("、")}）")
                sb.append(if (e.origin == "author") " [作者设定]" else " [${e.origin}]")
                sb.append('\n')
            }
        }
        if (b.outline.isNotEmpty()) {
            sb.append("大纲：\n")
            b.outline.forEach { n ->
                val title = book.chapters.firstOrNull { it.id == n.chapterId }?.title ?: n.title
                sb.append("- $title：")
                if (n.beats.isEmpty()) sb.append("（空）")
                else sb.append(n.beats.joinToString("；") { (if (it.done) "✓" else "") + it.text })
                sb.append(if (n.origin == "author") " [作者设定]" else " [${n.origin}]")
                sb.append('\n')
            }
        }
        return sb.toString().trimEnd()
    }

    fun readSection(book: Book, section: String): String = when (section) {
        "characters" -> if (book.bible.characters.isEmpty()) "没有角色"
        else book.bible.characters.joinToString("\n") { c ->
            "${c.name}" + (if (c.role.isNotBlank()) "（${c.role}）" else "") +
                (if (c.note.isNotBlank()) "：${c.note}" else "")
        }
        "relations" -> if (book.bible.relations.isEmpty()) "没有关系"
        else book.bible.relations.joinToString("\n") { r ->
            "${charName(book, r.from)} — ${charName(book, r.to)}：${r.kind.label}"
        }
        "events" -> if (book.bible.events.isEmpty()) "没有事件"
        else book.bible.events.sortedBy { storyTimeKey(it.whenText) ?: Int.MAX_VALUE }
            .joinToString("\n") { e ->
                "${e.whenText.ifBlank { "时间不详" }} · ${e.title.ifBlank { "未命名" }}" +
                    (if (e.note.isNotBlank()) "：${e.note}" else "")
            }
        "outline" -> if (book.bible.outline.isEmpty()) "没有大纲"
        else book.bible.outline.joinToString("\n") { n ->
            val title = book.chapters.firstOrNull { it.id == n.chapterId }?.title ?: n.title
            "$title：" + if (n.beats.isEmpty()) "（空）" else n.beats.joinToString("；") { it.text }
        }
        else -> summary(book)
    }

    /** 让 AI 一次读完一章：正文纲要、情节点、本章事件、出场角色。 */
    fun deepChapter(book: Book, chapterTitle: String): String {
        val chapter = book.chapters.firstOrNull { it.title == chapterTitle }
            ?: book.chapters.firstOrNull { it.title.contains(chapterTitle) && chapterTitle.isNotBlank() }
            ?: return "没找到这一章：$chapterTitle"
        val sb = StringBuilder()
        sb.append("《${book.title}》${chapter.title}（${countWords(chapter.content)} 字）\n")
        val node = book.bible.outline.firstOrNull { it.chapterId == chapter.id }
        if (node != null && node.beats.isNotEmpty()) {
            sb.append("本章情节点：\n")
            node.beats.forEach { b -> sb.append("- ").append(if (b.done) "✓ " else "").append(b.text).append('\n') }
        }
        val events = book.bible.events.filter { chapter.id in it.chapterIds }
        if (events.isNotEmpty()) {
            sb.append("本章事件：\n")
            events.forEach { e ->
                sb.append("- ").append(e.whenText.ifBlank { "时间不详" }).append(" · ")
                    .append(e.title.ifBlank { "未命名" })
                if (e.note.isNotBlank()) sb.append("：").append(e.note)
                sb.append('\n')
            }
        }
        val cast = book.bible.characters.filter { c -> c.name.length >= 2 && chapter.content.contains(c.name) }
        if (cast.isNotEmpty()) {
            sb.append("出场角色：\n")
            cast.forEach { c ->
                sb.append("- ").append(c.name)
                if (c.role.isNotBlank()) sb.append("（").append(c.role).append("）")
                if (c.note.isNotBlank()) sb.append("：").append(c.note)
                sb.append('\n')
            }
        }
        sb.append("\n正文：\n").append(chapter.content)
        return sb.toString()
    }

    // ───────────────────────── 写 ─────────────────────────

    /**
     * 应用一条圣经改动。`kind`：character / relation / event / outline。
     * `action`：upsert（新增或改）/ delete；outline 另支持 add_beat / update_beat / delete_beat / clear_beats / delete_node。
     */
    fun mutate(
        ctx: Context,
        store: AppStore,
        bookId: String,
        kind: String,
        action: String,
        args: JSONObject,
        source: String = "ai",
    ): String {
        val book = store.book(bookId) ?: return "没找到这本书"
        val before = book.bible
        var after = before
        var label = ""
        when (kind) {
            "character" -> {
                val name = args.optString("name").trim()
                if (name.isBlank()) return "角色名不能为空"
                val old = before.characters.firstOrNull { it.id == args.optString("id") }
                    ?: before.characters.firstOrNull { it.name == name }
                if (action == "delete") {
                    if (old == null) return "圣经里没有角色「$name」"
                    if (source == "text" && old.origin == "author") {
                        conflict(ctx, bookId, "角色「$name」是作者手改的，正文里的变化没有覆盖它")
                        return "跳过：角色「$name」是作者手改的"
                    }
                    after = before.copy(characters = before.characters.filterNot { it.id == old.id })
                    label = "删角色 $name"
                } else {
                    if (old != null && source == "text" && old.origin == "author") {
                        conflict(ctx, bookId, "角色「$name」是作者手改的，正文里的新描述没有覆盖它")
                        return "跳过：角色「$name」是作者手改的"
                    }
                    val next = (old ?: StoryCharacter(id = uuid(), name = name)).copy(
                        name = name,
                        role = args.optString("role", old?.role ?: "").ifBlank { old?.role ?: "" },
                        note = args.optString("note", old?.note ?: "").ifBlank { old?.note ?: "" },
                        origin = source,
                        updatedAt = now(),
                    )
                    after = if (old == null) before.copy(characters = before.characters + next)
                    else before.copy(characters = before.characters.map { if (it.id == old.id) next else it })
                    label = (if (old == null) "新增角色 " else "更新角色 ") + name
                }
            }
            "relation" -> {
                val fromName = args.optString("from").trim()
                val toName = args.optString("to").trim()
                val from = before.characters.firstOrNull { it.name == fromName }
                    ?: return "没有角色「$fromName」"
                val to = before.characters.firstOrNull { it.name == toName }
                    ?: return "没有角色「$toName」"
                val old = before.relations.firstOrNull {
                    (it.from == from.id && it.to == to.id) || (it.from == to.id && it.to == from.id)
                }
                if (action == "delete") {
                    if (old == null) return "没有「$fromName—$toName」这条关系"
                    if (source == "text" && old.origin == "author") {
                        conflict(ctx, bookId, "「$fromName—$toName」是作者手改的关系，没有删")
                        return "跳过：关系是作者手改的"
                    }
                    after = before.copy(relations = before.relations.filterNot { it.id == old.id })
                    label = "删关系 $fromName—$toName"
                } else {
                    if (old != null && source == "text" && old.origin == "author") {
                        conflict(ctx, bookId, "「$fromName—$toName」是作者手改的关系，正文里的新说法没有覆盖它")
                        return "跳过：关系是作者手改的"
                    }
                    val kindValue = RelationKind.entries.firstOrNull { it.name == args.optString("relation") }
                        ?: RelationKind.entries.firstOrNull { it.label == args.optString("relation") }
                        ?: RelationKind.OTHER
                    val next = (old ?: StoryRelation(id = uuid(), from = from.id, to = to.id)).copy(
                        kind = kindValue,
                        origin = source,
                        updatedAt = now(),
                    )
                    after = if (old == null) before.copy(relations = before.relations + next)
                    else before.copy(relations = before.relations.map { if (it.id == old.id) next else it })
                    label = (if (old == null) "新增关系 " else "更新关系 ") + "$fromName—$toName"
                }
            }
            "event" -> {
                val title = args.optString("title").trim()
                val whenText = args.optString("whenText").trim()
                val old = before.events.firstOrNull { it.id == args.optString("id") }
                    ?: before.events.firstOrNull { it.title == title && it.whenText == whenText }
                if (action == "delete") {
                    if (old == null) return "没有这个事件"
                    if (source == "text" && old.origin == "author") {
                        conflict(ctx, bookId, "事件「${old.title}」是作者手改的，没有删")
                        return "跳过：事件是作者手改的"
                    }
                    after = before.copy(events = before.events.filterNot { it.id == old.id })
                    label = "删事件 ${old.title}"
                } else {
                    if (old != null && source == "text" && old.origin == "author") {
                        conflict(ctx, bookId, "事件「${old.title}」是作者手改的，正文里的新说法没有覆盖它")
                        return "跳过：事件是作者手改的"
                    }
                    val chapterIds = args.optJSONArray("chapterIds")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i -> arr.optString(i).ifBlank { null } }
                    } ?: args.optJSONArray("chapters")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            book.chapters.firstOrNull { it.title == arr.optString(i) }?.id
                        }
                    } ?: old?.chapterIds ?: emptyList()
                    val charIds = args.optJSONArray("characters")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            book.bible.characters.firstOrNull { it.name == arr.optString(i) }?.id
                        }
                    } ?: old?.characterIds ?: emptyList()
                    val next = (old ?: StoryEvent(id = uuid())).copy(
                        whenText = whenText.ifBlank { old?.whenText ?: "" },
                        title = title.ifBlank { old?.title ?: "" },
                        note = args.optString("note", old?.note ?: ""),
                        chapterIds = chapterIds.distinct(),
                        characterIds = charIds.distinct(),
                        origin = source,
                        updatedAt = now(),
                    )
                    after = if (old == null) before.copy(events = before.events + next)
                    else before.copy(events = before.events.map { if (it.id == old.id) next else it })
                    label = (if (old == null) "新增事件 " else "更新事件 ") + next.title
                }
            }
            "outline" -> {
                val chapterTitle = args.optString("chapter").trim()
                val chapter = book.chapters.firstOrNull { it.title == chapterTitle }
                    ?: return "没有这一章：$chapterTitle"
                val old = before.outline.firstOrNull { it.chapterId == chapter.id }
                if (action == "delete_node") {
                    if (old == null) return "${chapter.title} 还没有大纲"
                    if (source == "text" && old.origin == "author") {
                        conflict(ctx, bookId, "${chapter.title} 的大纲是作者手改的，没有删")
                        return "跳过：大纲是作者手改的"
                    }
                    after = before.copy(outline = before.outline.filterNot { it.id == old.id })
                    label = "删大纲 ${chapter.title}"
                } else {
                    if (old != null && source == "text" && old.origin == "author") {
                        conflict(ctx, bookId, "${chapter.title} 的大纲是作者手改的，正文里的新情节点没有覆盖它")
                        return "跳过：大纲是作者手改的"
                    }
                    val current = old ?: OutlineNode(id = uuid(), chapterId = chapter.id, title = chapter.title)
                    val beats = when (action) {
                        "add_beat" -> current.beats + OutlineBeat(
                            id = uuid(),
                            text = args.optString("text").trim(),
                            done = false,
                        )
                        "clear_beats" -> emptyList()
                        "delete_beat" -> {
                            val text = args.optString("text").trim()
                            current.beats.filterNot { it.text == text || it.id == text }
                        }
                        "update_beat" -> {
                            val from = args.optString("text").trim()
                            val to = args.optString("to_text", args.optString("to")).trim()
                            current.beats.map { if (it.text == from) it.copy(text = to) else it }
                        }
                        else -> args.optJSONArray("beats")?.let { arr ->
                            val existing = current.beats.associateBy { it.text }
                            (0 until arr.length()).mapNotNull { i ->
                                val text = arr.optString(i).trim()
                                if (text.isBlank()) null else existing[text] ?: OutlineBeat(id = uuid(), text = text)
                            }
                        } ?: current.beats
                    }.filter { it.text.isNotBlank() }
                    val next = current.copy(beats = beats, origin = source, updatedAt = now())
                    after = if (old == null) before.copy(outline = before.outline + next)
                    else before.copy(outline = before.outline.map { if (it.id == old.id) next else it })
                    label = "更新大纲 ${chapter.title}（${beats.size} 条情节点）"
                }
            }
            else -> return "不认识的类别：$kind"
        }
        if (after == before) return "没有变化"
        apply(ctx, store, bookId, before, after, source, kind, action, label)
        return "已写入故事圣经：$label"
    }

    private fun apply(
        ctx: Context,
        store: AppStore,
        bookId: String,
        before: BookBible,
        after: BookBible,
        source: String,
        kind: String,
        action: String,
        label: String,
    ) {
        store.mutate { d ->
            d.copy(books = d.books.map { b -> if (b.id == bookId) b.copy(bible = after) else b })
        }
        appendJournal(
            ctx, JSONObject().apply {
                put("id", uuid())
                put("time", now())
                put("source", source)
                put("kind", kind)
                put("action", action)
                put("label", label)
                put("conflict", false)
                put("bookId", bookId)
                put("before", encodeBible(before))
                put("after", encodeBible(after))
            },
        )
    }

    /** 撤销最近一次 AI 改动（自动整理与工具都算）。 */
    fun undoLast(ctx: Context, store: AppStore, bookId: String): String {
        val arr = loadJournal(ctx)
        var index = -1
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            if (e.optBoolean("conflict", false)) continue
            val owner = e.optString("bookId")
            if (owner.isNotEmpty() && owner != bookId) continue
            index = i
            break
        }
        if (index < 0) return "没有可撤销的改动"
        val entry = arr.optJSONObject(index) ?: return "没有可撤销的改动"
        val before = entry.optJSONObject("before")?.let { decodeBible(it) } ?: return "记录损坏，撤不了"
        store.mutate { d ->
            d.copy(books = d.books.map { b -> if (b.id == bookId) b.copy(bible = before) else b })
        }
        val rest = JSONArray()
        for (i in 0 until arr.length()) if (i != index) rest.put(arr.opt(i))
        writeJournal(ctx, rest)
        return "已撤销：${entry.optString("label")}"
    }

    fun recent(ctx: Context, limit: Int = 20): List<JSONObject> {
        val arr = loadJournal(ctx)
        val out = ArrayList<JSONObject>()
        for (i in 0 until minOf(arr.length(), limit)) {
            arr.optJSONObject(i)?.let { out += it }
        }
        return out
    }

    /**
     * 3.26.23: input for a whole-book consistency pass — bible facts plus every chapter.
     * The caller asks the model to flag only passages that contradict the bible.
     */
    fun consistencyPrompt(book: Book, maxChars: Int = 12000): String {
        val sb = StringBuilder()
        sb.append("故事圣经（唯一事实来源）：\n\n").append(summary(book)).append("\n\n正文：\n")
        book.chapters.forEach { c ->
            sb.append("\n【").append(c.title).append("】\n").append(c.content).append('\n')
        }
        val text = sb.toString()
        return if (text.length <= maxChars) text
        else text.take(maxChars) + "\n…（这次只放了前面这些章节）"
    }

    // ───────────────────────── 内部 ─────────────────────────

    private fun conflict(ctx: Context, bookId: String, message: String) {
        appendJournal(
            ctx, JSONObject().apply {
                put("id", uuid())
                put("time", now())
                put("source", "text")
                put("kind", "conflict")
                put("action", "conflict")
                put("label", message)
                put("conflict", true)
                put("bookId", bookId)
            },
        )
    }

    @Synchronized
    private fun loadJournal(ctx: Context): JSONArray = runCatching {
        val file = journalFile(ctx)
        if (!file.exists()) JSONArray() else JSONArray(file.readText())
    }.getOrElse { JSONArray() }

    @Synchronized
    private fun writeJournal(ctx: Context, arr: JSONArray) {
        runCatching {
            // Atomic-ish: write a temp file then rename, so a kill mid-write cannot
            // leave a truncated journal that silently drops the undo history.
            val target = journalFile(ctx)
            val tmp = File(target.parentFile, "bible-journal.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(target)) {
                target.writeText(arr.toString())
                tmp.delete()
            }
        }
    }

    @Synchronized
    private fun appendJournal(ctx: Context, entry: JSONObject) {
        val arr = loadJournal(ctx)
        val next = JSONArray()
        next.put(entry)
        for (i in 0 until arr.length()) {
            if (next.length() >= MAX_JOURNAL) break
            arr.opt(i)?.let { next.put(it) }
        }
        writeJournal(ctx, next)
    }

    private fun charName(book: Book, id: String): String =
        book.bible.characters.firstOrNull { it.id == id }?.name ?: id

    private fun now(): Long = System.currentTimeMillis()
}
