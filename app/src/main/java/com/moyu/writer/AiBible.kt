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
                // 3.26.49：自定义类型用作者起的名字（内置类型回退枚举中文名）。
                sb.append("- ${charName(book, r.from)} — ${charName(book, r.to)}：${r.kindText}")
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
            "${charName(book, r.from)} — ${charName(book, r.to)}：${r.kindText}"
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
        rememberCtx(ctx, store)
        val book = store.book(bookId) ?: return "没找到这本书"
        val before = book.bible
        // 3.26.46：已同步到「我的」的 AI 条目按作者条目对待——AI 不能直接改，只能记待同意。
        val synced = syncedIds(bookId)
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
                    if ((old.origin == "author" || old.id in synced) && source != "author") {
                        // 3.26.45：AI 不直接删作者条目，记成待同意差异。
                        propose(ctx, bookId, source, listOf(deleteDraft("character", old.id, old.name)))
                        return "已提交待同意：删除角色「${old.name}」"
                    }
                    after = before.copy(characters = before.characters.filterNot { it.id == old.id })
                    label = "删角色 $name"
                } else {
                    val next = (old ?: StoryCharacter(id = uuid(), name = name)).copy(
                        name = name,
                        role = args.optString("role", old?.role ?: "").ifBlank { old?.role ?: "" },
                        note = args.optString("note", old?.note ?: "").ifBlank { old?.note ?: "" },
                        color = if (args.has("color")) args.optString("color").trim().ifBlank { null } else old?.color,
                        origin = source,
                        updatedAt = now(),
                    )
                    if (old != null && (old.origin == "author" || old.id in synced) && source != "author") {
                        // 3.26.45：作者条目只记字段级差异，同意后才写 bible。
                        val n = propose(ctx, bookId, source, characterDrafts(old, next))
                        return if (n > 0) "已提交待同意：角色「${old.name}」$n 处差异" else "没有变化"
                    }
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
                    if ((old.origin == "author" || old.id in synced) && source != "author") {
                        propose(
                            ctx, bookId, source,
                            listOf(deleteDraft("relation", old.id, "${charName(before, old.from)}—${charName(before, old.to)}")),
                        )
                        return "已提交待同意：删除关系「$fromName—$toName」"
                    }
                    after = before.copy(relations = before.relations.filterNot { it.id == old.id })
                    label = "删关系 $fromName—$toName"
                } else {
                    // 3.26.49：relation 既认内置枚举名 / 中文名，也认作者自定义的名字。
                    // 3.26.50：自定义类型再认「这本书里已有的同名自定义类型」——同一显示名取同一底档与
                    // 档位，AI / 自动整理写出「宿敌」时不会在两条关系上落成两个不同档位。
                    // 认不出来就保留这条关系原来的底档 / 档位（新关系 = OTHER / 派生）。
                    val rawKind = args.optString("relation").trim()
                    val spec = relationTypeSpec(rawKind, old, before)
                    val base = old ?: StoryRelation(id = uuid(), from = from.id, to = to.id)
                    val next = base.copy(
                        kind = spec.kind,
                        kindLabel = spec.label,
                        // 换回内置类型 = 丢掉自定义色（按档位取色）；自定义改自定义保留原色。
                        kindColor = if (spec.builtIn) null else base.kindColor,
                        // 换回内置类型 = 档位回到枚举派生（null）；自定义改自定义保留原档位。
                        kindTier = if (spec.builtIn) null else spec.kindTier,
                        origin = source,
                        updatedAt = now(),
                    )
                    if (old != null && (old.origin == "author" || old.id in synced) && source != "author") {
                        val reversed = old.from == to.id && old.to == from.id
                        val n = propose(ctx, bookId, source, relationDrafts(before, old, next, reversed, fromName, toName))
                        return if (n > 0) "已提交待同意：关系「$fromName—$toName」$n 处差异" else "没有变化"
                    }
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
                    if ((old.origin == "author" || old.id in synced) && source != "author") {
                        propose(ctx, bookId, source, listOf(deleteDraft("event", old.id, old.title.ifBlank { old.whenText })))
                        return "已提交待同意：删除事件「${old.title}」"
                    }
                    after = before.copy(events = before.events.filterNot { it.id == old.id })
                    label = "删事件 ${old.title}"
                } else {
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
                    if (old != null && (old.origin == "author" || old.id in synced) && source != "author") {
                        val n = propose(ctx, bookId, source, eventDrafts(book, old, next))
                        return if (n > 0) "已提交待同意：事件「${old.title}」$n 处差异" else "没有变化"
                    }
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
                    if ((old.origin == "author" || old.id in synced) && source != "author") {
                        propose(ctx, bookId, source, listOf(deleteDraft("outline", old.id, chapter.title)))
                        return "已提交待同意：删除大纲「${chapter.title}」"
                    }
                    after = before.copy(outline = before.outline.filterNot { it.id == old.id })
                    label = "删大纲 ${chapter.title}"
                } else {
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
                    if (old != null && (old.origin == "author" || old.id in synced) && source != "author") {
                        val n = propose(ctx, bookId, source, outlineDrafts(old, next))
                        return if (n > 0) "已提交待同意：大纲「${chapter.title}」$n 处差异" else "没有变化"
                    }
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
        merge: Boolean = false,
        extra: JSONObject? = null,
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
                if (merge) put("merge", true)
                // 3.26.46：同步记录里带上这一批的标记（syncBatch / syncedIds），
                // undoLastMerge 要按它把侧车标记一起清掉。
                extra?.let { e ->
                    val keys = e.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, e.get(key))
                    }
                }
            },
        )
    }

    /** 撤销最近一次 AI 改动（自动整理与工具都算；「同步到我的」另有 undoLastMerge）。 */
    fun undoLast(ctx: Context, store: AppStore, bookId: String): String {
        rememberCtx(ctx, store)
        val arr = loadJournal(ctx)
        var index = -1
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            if (e.optBoolean("conflict", false)) continue
            if (e.optBoolean("merge", false)) continue
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
        rememberCtx(ctx)
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

    // ───────────────────── 3.26.45 待同意 / 同步到我的 ─────────────────────

    /**
     * 3.26.45：故事页三 tab 分「我的（origin=author）/ AI」之后，AI 不再直接改作者条目。
     *
     * - AI（工具或章节保存后的自动整理）想改作者名下**已有**条目时，[mutate] 只把字段级
     *   差异写进 `filesDir/ai/bible-pending.json`（侧车、原子写、进程被杀不丢），bible
     *   一个字都不动；用户在故事页逐条同意后才落库，拒绝只在 journal 留一条，不进数据。
     *   AI 自己的新条目照旧直接写（origin=ai / text），不进待同意列表。
     * - 「同步 AI 内容到我的」：**不删 AI 条目、也不改 origin**，只做两件事——
     *   ① 把字段并进同名的作者条目（有同名才并）；② 把条目 id 记进
     *   `filesDir/ai/bible-synced.json`（侧车、原子写）当「已同步」标记。AI 页继续显示
     *   这些条目（淡一档 + 「已同步」角标），「我的」页按标记把它们铺出来；重复同步按
     *   标记跳过。写前留整份 bible 的 journal（merge 标记 + syncBatch），
     *   [undoLastMerge] 一步退回并清掉这一批标记。
     *
     * 这五个入口给故事页直接调（签名里没有 Context / AppStore），上下文从任意带 ctx 的
     * AiBible 调用里缓存；缓存没热时退回 `ActivityThread.currentApplication()`；都拿不到
     * 就安全地当空处理，不抛异常。
     */
    private const val MAX_PENDING = 200
    private const val PENDING_FILE = "bible-pending.json"

    /** 3.26.46：「已同步」标记侧车——同步不再删 AI 条目，只在这里记一笔。 */
    private const val MAX_SYNCED = 600
    private const val SYNCED_FILE = "bible-synced.json"

    private var cachedCtx: Context? = null
    private var cachedStore: AppStore? = null

    /** 故事页手上已有 Context 时顺手调一下，给无 Context 的入口预热。 */
    fun host(context: Context, store: AppStore? = null) {
        rememberCtx(context, store)
    }

    private fun rememberCtx(context: Context, store: AppStore? = null) {
        val app = context.applicationContext
        cachedCtx = app
        if (store != null) {
            cachedStore = store
            return
        }
        val found = runCatching { (app as? MoyuApplication)?.store }.getOrNull()
        if (found != null) cachedStore = found
    }

    private fun appContextOrNull(): Context? {
        cachedCtx?.let { return it }
        val app = runCatching {
            // 签名里没有 Context：Application 已经起来时，这条路是唯一不靠 CompositionLocal 的取法。
            val thread = Class.forName("android.app.ActivityThread")
            thread.getMethod("currentApplication").invoke(null) as? android.app.Application
        }.getOrNull() ?: runCatching {
            val globals = Class.forName("android.app.AppGlobals")
            globals.getMethod("getInitialApplication").invoke(null) as? android.app.Application
        }.getOrNull()
        return app?.applicationContext?.also { cachedCtx = it }
    }

    private fun appStoreOrNull(): AppStore? {
        cachedStore?.let { return it }
        val app = appContextOrNull() as? MoyuApplication ?: return null
        return runCatching { app.store }.getOrNull()?.also { cachedStore = it }
    }

    private fun isMainThread(): Boolean =
        android.os.Looper.myLooper() == android.os.Looper.getMainLooper()

    private fun postToMain(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }

    private fun pendingFile(ctx: Context): File = File(dir(ctx), PENDING_FILE)

    @Synchronized
    private fun loadPending(ctx: Context): JSONArray = runCatching {
        val file = pendingFile(ctx)
        if (!file.exists()) JSONArray() else JSONArray(file.readText())
    }.getOrElse { JSONArray() }

    @Synchronized
    private fun writePending(ctx: Context, arr: JSONArray) {
        runCatching {
            // 与 journal 同一套原子写：临时文件 + rename，进程被杀不会留半截 JSON。
            val target = pendingFile(ctx)
            val tmp = File(target.parentFile, "bible-pending.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(target)) {
                target.writeText(arr.toString())
                tmp.delete()
            }
        }
    }

    // ── 已同步标记（3.26.46） ──

    private fun syncedFile(ctx: Context): File = File(dir(ctx), SYNCED_FILE)

    @Synchronized
    private fun loadSynced(ctx: Context): JSONArray = runCatching {
        val file = syncedFile(ctx)
        if (!file.exists()) JSONArray() else JSONArray(file.readText())
    }.getOrElse { JSONArray() }

    @Synchronized
    private fun writeSynced(ctx: Context, arr: JSONArray) {
        runCatching {
            // 与 pending / journal 同一套原子写：临时文件 + rename。
            val target = syncedFile(ctx)
            val tmp = File(target.parentFile, "bible-synced.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(target)) {
                target.writeText(arr.toString())
                tmp.delete()
            }
        }
    }

    /**
     * 这本书里「已经同步到我的」的 AI 条目 id（侧车）。
     *
     * 3.26.46：同步不再把 AI 条目升级成 author、也不再删条目——那样同步完 AI 页就空了
     * （用户：「同步完 AI 内容消失」）。现在同步只做两件事：字段并进同名作者条目、
     * id 记进这份侧车。AI 页按标记淡一档并显示「已同步」，「我的」页按同一份标记
     * 把条目铺出来。
     */
    fun syncedIds(bookId: String): Set<String> {
        val ctx = appContextOrNull() ?: return emptySet()
        val arr = loadSynced(ctx)
        val out = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("bookId") != bookId) continue
            out += o.optString("id")
        }
        return out
    }

    @Synchronized
    private fun markSynced(ctx: Context, bookId: String, ids: List<String>, batch: String) {
        if (ids.isEmpty()) return
        val old = loadSynced(ctx)
        val set = ids.toHashSet()
        val out = JSONArray()
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            // 同一本书同一个条目只留一条标记（重复同步时换成新批次）。
            if (o.optString("bookId") == bookId && o.optString("id") in set) continue
            out.put(o)
        }
        ids.forEach { id ->
            out.put(
                JSONObject().apply {
                    put("bookId", bookId)
                    put("id", id)
                    put("batch", batch)
                    put("time", now())
                },
            )
        }
        val finalArr = if (out.length() <= MAX_SYNCED) out else {
            val trimmed = JSONArray()
            for (i in (out.length() - MAX_SYNCED) until out.length()) out.opt(i)?.let { trimmed.put(it) }
            trimmed
        }
        writeSynced(ctx, finalArr)
    }

    /** 撤销一次同步：把这批打上的标记清掉（按批次号，顺带兜底按 id）。 */
    @Synchronized
    private fun clearSynced(ctx: Context, bookId: String, batch: String, ids: List<String>) {
        val arr = loadSynced(ctx)
        val idSet = ids.toHashSet()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val drop = o.optString("bookId") == bookId &&
                (o.optString("batch") == batch || o.optString("id") in idSet)
            if (!drop) out.put(o)
        }
        writeSynced(ctx, out)
    }

    // ── 待同意 ──

    /** 待同意的字段级差异，按写入顺序（旧的在前）。 */
    fun pendingChanges(bookId: String): List<BibleChange> {
        val ctx = appContextOrNull() ?: return emptyList()
        val arr = loadPending(ctx)
        val out = ArrayList<BibleChange>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("bookId") != bookId) continue
            out += BibleChange(
                id = o.optString("id"),
                kind = o.optString("kind"),
                title = o.optString("title"),
                field = o.optString("field"),
                before = o.optString("before"),
                after = o.optString("after"),
                bookId = bookId,
            )
        }
        return out
    }

    /** 同意 / 拒绝一条待同意差异。同意才写 bible；拒绝只在 journal 留一条，不进数据。 */
    fun resolveChange(id: String, accept: Boolean): Boolean {
        val ctx = appContextOrNull() ?: return false
        if (!isMainThread()) {
            postToMain { resolveChangeOnMain(ctx, id, accept) }
            return true
        }
        return resolveChangeOnMain(ctx, id, accept)
    }

    private fun resolveChangeOnMain(ctx: Context, id: String, accept: Boolean): Boolean {
        val store = appStoreOrNull() ?: return false
        val arr = loadPending(ctx)
        var record: JSONObject? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") == id) {
                record = o
                break
            }
        }
        val r = record ?: return false
        val book = store.book(r.optString("bookId"))
        val ok = when {
            book == null -> false
            accept -> acceptRecord(ctx, store, book, r)
            else -> rejectRecord(ctx, book, r)
        }
        removePending(ctx, id)
        return ok
    }

    private fun removePending(ctx: Context, id: String) {
        val arr = loadPending(ctx)
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") != id) next.put(o)
        }
        writePending(ctx, next)
    }

    /** 同意一条：按记录的 key 只改这一个字段，写 bible + journal（通用撤销也能退）。 */
    private fun acceptRecord(ctx: Context, store: AppStore, book: Book, r: JSONObject): Boolean {
        val before = book.bible
        val kind = r.optString("kind")
        val op = r.optString("op")
        val targetId = r.optString("targetId")
        val key = r.optString("key")
        val payload = r.optJSONObject("payload") ?: JSONObject()
        val value = payload.optString("value")
        var after = before
        when (kind) {
            "character" -> {
                val old = before.characters.firstOrNull { it.id == targetId } ?: return false
                if (op == "delete") {
                    after = before.copy(characters = before.characters.filterNot { it.id == targetId })
                } else {
                    val next = when (key) {
                        "name" -> old.copy(name = value)
                        "role" -> old.copy(role = value)
                        "note" -> old.copy(note = value)
                        "color" -> old.copy(color = value.ifBlank { null })
                        else -> return false
                    }.copy(origin = "author", updatedAt = now())
                    after = before.copy(characters = before.characters.map { if (it.id == targetId) next else it })
                }
            }
            "relation" -> {
                val old = before.relations.firstOrNull { it.id == targetId } ?: return false
                if (op == "delete") {
                    after = before.copy(relations = before.relations.filterNot { it.id == targetId })
                } else {
                    val next = when (key) {
                        "kind" -> {
                            // 3.26.49：底档 + 显示名 + 自定义色是一条差异的三个部分，一次写入。
                            // 3.26.50：档位也随类型一起写（null = 按底档派生）。
                            val kind = parseRelationKind(payload.optString("kind", value))
                            old.copy(
                                kind = kind,
                                kindLabel = capRelationLabel(payload.optString("kindLabel").trim()).ifBlank { kind.label },
                                kindColor = normalizeRelationColor(payload.optString("kindColor")),
                                kindTier = decodeRelationTier(payload.opt("kindTier")),
                            )
                        }
                        "ends" -> {
                            val from = before.characters.firstOrNull { it.name == payload.optString("fromName") } ?: return false
                            val to = before.characters.firstOrNull { it.name == payload.optString("toName") } ?: return false
                            old.copy(from = from.id, to = to.id)
                        }
                        else -> return false
                    }.copy(origin = "author", updatedAt = now())
                    after = before.copy(relations = before.relations.map { if (it.id == targetId) next else it })
                }
            }
            "event" -> {
                val old = before.events.firstOrNull { it.id == targetId } ?: return false
                if (op == "delete") {
                    after = before.copy(events = before.events.filterNot { it.id == targetId })
                } else {
                    val next = when (key) {
                        "whenText" -> old.copy(whenText = value)
                        "title" -> old.copy(title = value)
                        "note" -> old.copy(note = value)
                        "chapters" -> old.copy(chapterIds = payload.optJSONArray("chapterIds")?.toStringList() ?: old.chapterIds)
                        "characters" -> old.copy(characterIds = payload.optJSONArray("characterIds")?.toStringList() ?: old.characterIds)
                        else -> return false
                    }.copy(origin = "author", updatedAt = now())
                    after = before.copy(events = before.events.map { if (it.id == targetId) next else it })
                }
            }
            "outline" -> {
                val old = before.outline.firstOrNull { it.id == targetId } ?: return false
                if (op == "delete") {
                    after = before.copy(outline = before.outline.filterNot { it.id == targetId })
                } else {
                    val next = when (key) {
                        "title" -> old.copy(title = value)
                        "beats" -> old.copy(beats = buildBeats(old.beats, payload.optJSONArray("beats")?.toStringList() ?: emptyList()))
                        else -> return false
                    }.copy(origin = "author", updatedAt = now())
                    after = before.copy(outline = before.outline.map { if (it.id == targetId) next else it })
                }
            }
            else -> return false
        }
        if (after == before) return false
        apply(
            ctx, store, book.id, before, after, "author", kind, "consent",
            "同意 AI 改动：${kindLabel(kind)}「${r.optString("title")}」${r.optString("field")}",
        )
        return true
    }

    /** 拒绝一条：只留一条 journal 记录（conflict=true，撤销不会碰它），bible 不动。 */
    private fun rejectRecord(ctx: Context, book: Book, r: JSONObject): Boolean {
        appendJournal(
            ctx, JSONObject().apply {
                put("id", uuid())
                put("time", now())
                put("source", r.optString("source", "ai"))
                put("kind", r.optString("kind"))
                put("action", "reject")
                put("label", "拒绝 AI 改动：${kindLabel(r.optString("kind"))}「${r.optString("title")}」${r.optString("field")}")
                put("conflict", true)
                put("bookId", book.id)
            },
        )
        return true
    }

    /** 一条待同意的草稿：同一本书里按 kind + targetId + key 去重。 */
    private data class PendingDraft(
        val kind: String,
        val op: String,
        val targetId: String,
        val title: String,
        val key: String,
        val field: String,
        val before: String,
        val after: String,
        val payload: JSONObject = JSONObject(),
    )

    private fun deleteDraft(kind: String, id: String, title: String): PendingDraft {
        val shown = title.ifBlank { "未命名" }
        return PendingDraft(kind, "delete", id, shown, "", "删除", shown, "（删除）")
    }

    private fun characterDrafts(old: StoryCharacter, next: StoryCharacter): List<PendingDraft> {
        val title = old.name.ifBlank { "未命名角色" }
        val out = ArrayList<PendingDraft>(4)
        if (old.name != next.name) {
            out += PendingDraft("character", "update", old.id, title, "name", "名字", old.name, next.name, JSONObject().put("value", next.name))
        }
        if (old.role != next.role) {
            out += PendingDraft("character", "update", old.id, title, "role", "身份", blankShow(old.role), blankShow(next.role), JSONObject().put("value", next.role))
        }
        if (old.note != next.note) {
            out += PendingDraft("character", "update", old.id, title, "note", "备注", blankShow(old.note), blankShow(next.note), JSONObject().put("value", next.note))
        }
        if (old.color != next.color) {
            out += PendingDraft("character", "update", old.id, title, "color", "颜色", old.color ?: "自动", next.color ?: "自动", JSONObject().put("value", next.color ?: ""))
        }
        return out
    }

    /**
     * 3.26.49：类型差异把 kind（底档）和 kindLabel（显示名）一起比较——内置名字换自定义、
     * 自定义名字互改都会出差异；颜色随类型一起改（payload 也带 kindColor，同意时一次落库）。
     * 3.26.50：档位（实际生效值）也一起比，payload 带 kindTier，同意时档位一并落库。
     */
    private fun relationDrafts(
        before: BookBible,
        old: StoryRelation,
        next: StoryRelation,
        reversed: Boolean,
        fromName: String,
        toName: String,
    ): List<PendingDraft> {
        val title = "${charName(before, old.from)}—${charName(before, old.to)}"
        val out = ArrayList<PendingDraft>(2)
        if (old.kind != next.kind || old.kindText != next.kindText || old.tier != next.tier) {
            out += PendingDraft(
                "relation", "update", old.id, title, "kind", "类型",
                old.kindText, next.kindText,
                JSONObject().apply {
                    put("kind", next.kind.name)
                    put("kindLabel", next.kindText)
                    put("kindColor", next.kindColor ?: "")
                    put("kindTier", next.kindTier ?: JSONObject.NULL)
                },
            )
        }
        if (reversed) {
            out += PendingDraft("relation", "update", old.id, title, "ends", "两端", title, "$fromName—$toName", JSONObject().put("fromName", fromName).put("toName", toName))
        }
        return out
    }

    private fun eventDrafts(book: Book, old: StoryEvent, next: StoryEvent): List<PendingDraft> {
        val title = next.title.ifBlank { old.title }.ifBlank { "未命名事件" }
        val out = ArrayList<PendingDraft>(5)
        if (old.whenText != next.whenText) {
            out += PendingDraft("event", "update", old.id, title, "whenText", "时间", blankShow(old.whenText), blankShow(next.whenText), JSONObject().put("value", next.whenText))
        }
        if (old.title != next.title) {
            out += PendingDraft("event", "update", old.id, title, "title", "标题", blankShow(old.title), blankShow(next.title), JSONObject().put("value", next.title))
        }
        if (old.note != next.note) {
            out += PendingDraft("event", "update", old.id, title, "note", "备注", blankShow(old.note), blankShow(next.note), JSONObject().put("value", next.note))
        }
        if (old.chapterIds.toSet() != next.chapterIds.toSet()) {
            out += PendingDraft(
                "event", "update", old.id, title, "chapters", "章节",
                chapterTitleText(book, old.chapterIds), chapterTitleText(book, next.chapterIds),
                JSONObject().put("chapterIds", JSONArray(next.chapterIds)),
            )
        }
        if (old.characterIds.toSet() != next.characterIds.toSet()) {
            out += PendingDraft(
                "event", "update", old.id, title, "characters", "出场角色",
                namesText(book.bible, old.characterIds), namesText(book.bible, next.characterIds),
                JSONObject().put("characterIds", JSONArray(next.characterIds)),
            )
        }
        return out
    }

    private fun outlineDrafts(old: OutlineNode, next: OutlineNode): List<PendingDraft> {
        val title = next.title.ifBlank { old.title }.ifBlank { "未命名大纲" }
        val out = ArrayList<PendingDraft>(2)
        if (old.title != next.title) {
            out += PendingDraft("outline", "update", old.id, title, "title", "标题", blankShow(old.title), blankShow(next.title), JSONObject().put("value", next.title))
        }
        val oldTexts = old.beats.map { it.text }
        val newTexts = next.beats.map { it.text }
        if (oldTexts != newTexts) {
            out += PendingDraft(
                "outline", "update", old.id, title, "beats", "情节点",
                oldTexts.joinToString("；").ifBlank { "（空）" }, newTexts.joinToString("；").ifBlank { "（空）" },
                JSONObject().put("beats", JSONArray(newTexts)),
            )
        }
        return out
    }

    /** 把草稿落进待同意侧车；同一条（kind + targetId + key）重复提议时更新，不叠一条。 */
    private fun propose(ctx: Context, bookId: String, source: String, drafts: List<PendingDraft>): Int {
        if (drafts.isEmpty()) return 0
        rememberCtx(ctx)
        val oldArr = loadPending(ctx)
        val existing = ArrayList<JSONObject>()
        for (i in 0 until oldArr.length()) oldArr.optJSONObject(i)?.let { existing += it }
        val used = BooleanArray(drafts.size)
        val out = JSONArray()
        var changed = 0
        for (e in existing) {
            var matched = -1
            if (e.optString("bookId") == bookId) {
                for (i in drafts.indices) {
                    if (used[i]) continue
                    val d = drafts[i]
                    if (d.kind == e.optString("kind") && d.op == e.optString("op") &&
                        d.targetId == e.optString("targetId") && d.key == e.optString("key")
                    ) {
                        matched = i
                        break
                    }
                }
            }
            if (matched < 0) {
                out.put(e)
                continue
            }
            used[matched] = true
            val d = drafts[matched]
            if (e.optString("before") == d.before && e.optString("after") == d.after) {
                out.put(e)
            } else {
                e.put("title", d.title)
                e.put("field", d.field)
                e.put("before", d.before)
                e.put("after", d.after)
                e.put("payload", d.payload)
                e.put("source", source)
                e.put("time", now())
                out.put(e)
                changed++
            }
        }
        for (i in drafts.indices) {
            if (used[i]) continue
            val d = drafts[i]
            out.put(JSONObject().apply {
                put("id", "pc_" + uuid())
                put("bookId", bookId)
                put("kind", d.kind)
                put("op", d.op)
                put("targetId", d.targetId)
                put("title", d.title)
                put("field", d.field)
                put("key", d.key)
                put("before", d.before)
                put("after", d.after)
                put("payload", d.payload)
                put("source", source)
                put("time", now())
            })
            changed++
        }
        if (changed == 0) return 0
        // 只留最近 MAX_PENDING 条，防止侧车无限长（旧的先丢）。
        val finalArr = if (out.length() <= MAX_PENDING) out else {
            val trimmed = JSONArray()
            for (i in (out.length() - MAX_PENDING) until out.length()) out.opt(i)?.let { trimmed.put(it) }
            trimmed
        }
        writePending(ctx, finalArr)
        return changed
    }

    // ───────────────────────── 内部 ─────────────────────────

    // ── 同步 AI 内容到我的 ──

    /** 「同步到我的」预览：AI 条目 → 作者条目的字段级差异；id 直接给 [applyMerge] 用。 */
    fun previewMerge(bookId: String): List<BibleChange> {
        val store = appStoreOrNull() ?: return emptyList()
        val book = store.book(bookId) ?: return emptyList()
        val bible = book.bible
        val out = ArrayList<BibleChange>()
        // 3.26.46：已经同步过的条目按侧车标记跳过，不再重复列出来。
        val alreadySynced = syncedIds(bookId)
        val mineChars = bible.characters.filter { it.origin == "author" }
        val aiChars = bible.characters.filter { it.origin != "author" && it.id !in alreadySynced }
        // AI 角色 → 我的同名角色：并掉之后关系两端要换 id，否则留下悬空引用。
        val charMap = HashMap<String, String>()
        aiChars.forEach { c ->
            if (c.name.isNotBlank()) {
                mineChars.firstOrNull { it.name == c.name }?.let { charMap[c.id] = it.id }
            }
        }
        fun add(kind: String, entryId: String, key: String, title: String, field: String, before: String, after: String) {
            out += BibleChange(mergeId(kind, entryId, key), kind, title, field, before, after, bookId)
        }
        aiChars.forEach { c ->
            val title = c.name.ifBlank { "未命名角色" }
            val mine = if (c.name.isBlank()) null else mineChars.firstOrNull { it.name == c.name }
            if (mine == null) {
                add("character", c.id, "origin", title, "归属", "AI", "我的")
            } else {
                if (c.role.isNotBlank() && c.role != mine.role) {
                    add("character", c.id, "role", title, "身份", blankShow(mine.role), c.role)
                }
                if (c.note.isNotBlank() && c.note != mine.note) {
                    add("character", c.id, "note", title, "备注", blankShow(mine.note), c.note)
                }
                if (c.color != null && c.color != mine.color) {
                    add("character", c.id, "color", title, "颜色", mine.color ?: "自动", c.color)
                }
                add("character", c.id, "merge", title, "并入", "AI 角色", "并进我的同名角色")
            }
        }
        bible.relations.filter { it.origin != "author" && it.id !in alreadySynced }.forEach { r ->
            val rawFrom = charName(bible, r.from)
            val rawTo = charName(bible, r.to)
            val mf = charMap[r.from] ?: r.from
            val mt = charMap[r.to] ?: r.to
            val title = "$rawFrom—$rawTo"
            val counterpart = bible.relations.firstOrNull {
                it.origin == "author" && it.id != r.id && samePair(it, mf, mt)
            }
            if (counterpart == null) {
                add("relation", r.id, "origin", title, "归属", "AI", "我的")
                if (mf != r.from || mt != r.to) {
                    add(
                        "relation", r.id, "ends", title, "两端",
                        "$rawFrom（AI）—$rawTo（AI）", "${charName(bible, mf)}—${charName(bible, mt)}",
                    )
                }
            } else {
                if (counterpart.kind != r.kind || counterpart.kindText != r.kindText || counterpart.tier != r.tier) {
                    // 3.26.49：类型差异带显示名（自定义名词也参与差异），不再只比枚举。
                    // 3.26.50：实际生效档位不同也算类型差异。
                    add("relation", r.id, "kind", title, "类型", counterpart.kindText, r.kindText)
                }
                add("relation", r.id, "merge", title, "并入", "AI 关系", "并进我的同名关系")
            }
        }
        bible.events.filter { it.origin != "author" && it.id !in alreadySynced }.forEach { e ->
            val title = e.title.ifBlank { e.whenText.ifBlank { "未命名事件" } }
            val mine = bible.events.firstOrNull {
                it.origin == "author" && it.id != e.id && it.title == e.title && it.title.isNotBlank()
            }
            if (mine == null) {
                add("event", e.id, "origin", title, "归属", "AI", "我的")
            } else {
                if (e.whenText.isNotBlank() && e.whenText != mine.whenText) {
                    add("event", e.id, "whenText", title, "时间", blankShow(mine.whenText), e.whenText)
                }
                if (e.note.isNotBlank() && e.note != mine.note) {
                    add("event", e.id, "note", title, "备注", blankShow(mine.note), e.note)
                }
                add("event", e.id, "merge", title, "并入", "AI 事件", "并进我的同名事件")
            }
        }
        bible.outline.filter { it.origin != "author" && it.id !in alreadySynced }.forEach { n ->
            val title = book.chapters.firstOrNull { it.id == n.chapterId }?.title ?: n.title.ifBlank { "未命名大纲" }
            val mine = bible.outline.firstOrNull {
                it.origin == "author" && it.id != n.id &&
                    if (n.chapterId != null) it.chapterId == n.chapterId else it.title == n.title
            }
            if (mine == null) {
                add("outline", n.id, "origin", title, "归属", "AI", "我的")
            } else {
                if (n.title.isNotBlank() && n.title != mine.title) {
                    add("outline", n.id, "title", title, "标题", blankShow(mine.title), n.title)
                }
                val union = (mine.beats.map { it.text } + n.beats.map { it.text }).distinct()
                if (union.size > mine.beats.size) {
                    add("outline", n.id, "beats", title, "情节点", mine.beats.joinToString("；").ifBlank { "（空）" }, union.joinToString("；"))
                }
                add("outline", n.id, "merge", title, "并入", "AI 大纲", "并进我的同名大纲")
            }
        }
        return out
    }

    /** 执行选中的合并项，返回打上「已同步」标记的条目数（0 = 没有可同步的条目）。 */
    fun applyMerge(bookId: String, ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        val ctx = appContextOrNull() ?: return 0
        if (!isMainThread()) {
            postToMain { applyMergeOnMain(ctx, bookId, ids) }
            return ids.size
        }
        return applyMergeOnMain(ctx, bookId, ids)
    }

    /** 故事页「同步 AI 内容到我的」的整批入口（UI 约定名）：预览全选 → 执行。 */
    fun syncAiIntoMine(store: AppStore, bookId: String): Int {
        cachedStore = store
        val ids = previewMerge(bookId).map { it.id }
        return applyMerge(bookId, ids)
    }

    /** 一步撤销最近一次「同步到我的」（取最近一条 merge journal 的 before 写回）。 */
    fun undoLastMerge(bookId: String): Boolean {
        val ctx = appContextOrNull() ?: return false
        if (!isMainThread()) {
            postToMain { undoLastMergeOnMain(ctx, bookId) }
            return true
        }
        return undoLastMergeOnMain(ctx, bookId)
    }

    private fun applyMergeOnMain(ctx: Context, bookId: String, ids: List<String>): Int {
        val store = appStoreOrNull() ?: return 0
        val book = store.book(bookId) ?: return 0
        val before = book.bible
        var b = before
        var applied = 0
        val byEntry = HashMap<String, MutableSet<String>>()
        ids.forEach { raw ->
            val parsed = parseMergeId(raw) ?: return@forEach
            byEntry.getOrPut(parsed.kind + "\u0000" + parsed.entryId) { HashSet() }.add(parsed.key)
        }
        fun keysOf(kind: String, entryId: String): Set<String> =
            byEntry[kind + "\u0000" + entryId] ?: emptySet()

        // AI 角色 id → 我的同名角色 id：并字段时关系要换引用。
        val charMap = HashMap<String, String>()
        b.characters.filter { it.origin != "author" }.forEach { c ->
            if (c.name.isNotBlank()) {
                b.characters.firstOrNull { it.origin == "author" && it.name == c.name }?.let { charMap[c.id] = it.id }
            }
        }
        // 3.26.46：这一批真正打上「已同步」标记的 AI 条目 id。
        val marked = LinkedHashSet<String>()

        // 1) 角色：只把字段并进同名作者角色；AI 条目原样保留（AI 页继续显示「已同步」）。
        val aiCharacters = b.characters.filter { it.origin != "author" }
        for (snapshot in aiCharacters) {
            val keys = keysOf("character", snapshot.id)
            if (keys.isEmpty()) continue
            val current = b.characters.firstOrNull { it.id == snapshot.id && it.origin != "author" } ?: continue
            marked += current.id
            // 不升级 origin、不删 AI 条目：只在有同名作者角色时把字段并过去。
            val mine = b.characters.firstOrNull { it.origin == "author" && it.name == current.name } ?: continue
            var target = mine
            if ("role" in keys && current.role.isNotBlank()) {
                target = target.copy(role = current.role, updatedAt = now())
                applied++
            }
            if ("note" in keys && current.note.isNotBlank()) {
                target = target.copy(note = current.note, updatedAt = now())
                applied++
            }
            if ("color" in keys && current.color != null) {
                target = target.copy(color = current.color, updatedAt = now())
                applied++
            }
            if ("merge" in keys) {
                target = target.copy(
                    role = current.role.ifBlank { target.role },
                    note = current.note.ifBlank { target.note },
                    color = current.color ?: target.color,
                    updatedAt = now(),
                )
                applied++
            }
            b = b.copy(
                characters = b.characters.map { if (it.id == target.id) target else it },
            )
        }

        // 2) 关系：两端先按角色映射换 id，再找我的同两人关系并类型；AI 关系保留。
        val aiRelations = b.relations.filter { it.origin != "author" }
        for (snapshot in aiRelations) {
            val keys = keysOf("relation", snapshot.id)
            if (keys.isEmpty()) continue
            val current = b.relations.firstOrNull { it.id == snapshot.id } ?: continue
            marked += current.id
            val mf = charMap[current.from] ?: current.from
            val mt = charMap[current.to] ?: current.to
            val counterpart = b.relations.firstOrNull { it.origin == "author" && samePair(it, mf, mt) }
                ?: continue
            var target = counterpart
            if ("kind" in keys) {
                // 3.26.49：类型同步连显示名与自定义色一起并过去。AI 自定义类型没带颜色时
                // 保留作者原来设的颜色（不静默丢色）；并回内置类型则清掉自定义色。
                // 3.26.50：档位同理——AI 自定义类型没带档位时保留作者原档位，并回内置类型则派生。
                target = target.copy(
                    kind = current.kind,
                    kindLabel = current.kindText,
                    kindColor = if (current.customKind) current.kindColor ?: target.kindColor else null,
                    kindTier = if (current.customKind) current.kindTier ?: target.kindTier else null,
                    updatedAt = now(),
                )
                applied++
            }
            if ("merge" in keys || "ends" in keys) {
                target = target.copy(from = mf, to = mt, updatedAt = now())
                applied++
            }
            b = b.copy(
                relations = b.relations.map { if (it.id == target.id) target else it },
            )
        }

        // 3) 事件：有同名作者事件就并字段（章节 / 出场角色取并集）；AI 事件保留。
        val aiEvents = b.events.filter { it.origin != "author" }
        for (snapshot in aiEvents) {
            val keys = keysOf("event", snapshot.id)
            if (keys.isEmpty()) continue
            val current = b.events.firstOrNull { it.id == snapshot.id } ?: continue
            marked += current.id
            val mine = b.events.firstOrNull {
                it.origin == "author" && it.id != current.id && it.title == current.title && it.title.isNotBlank()
            } ?: continue
            var target = mine
            if ("whenText" in keys && current.whenText.isNotBlank()) {
                target = target.copy(whenText = current.whenText, updatedAt = now())
                applied++
            }
            if ("note" in keys && current.note.isNotBlank()) {
                target = target.copy(note = current.note, updatedAt = now())
                applied++
            }
            if ("merge" in keys) {
                target = target.copy(
                    whenText = current.whenText.ifBlank { target.whenText },
                    note = current.note.ifBlank { target.note },
                    chapterIds = (target.chapterIds + current.chapterIds).distinct(),
                    characterIds = (target.characterIds + current.characterIds.map { charMap[it] ?: it }).distinct(),
                    updatedAt = now(),
                )
                applied++
            }
            b = b.copy(
                events = b.events.map { if (it.id == target.id) target else it },
            )
        }

        // 4) 大纲：同章（或同名未落地节点）并情节点；AI 节点保留。
        val aiOutline = b.outline.filter { it.origin != "author" }
        for (snapshot in aiOutline) {
            val keys = keysOf("outline", snapshot.id)
            if (keys.isEmpty()) continue
            val current = b.outline.firstOrNull { it.id == snapshot.id } ?: continue
            marked += current.id
            val mine = b.outline.firstOrNull {
                it.origin == "author" && it.id != current.id &&
                    (if (current.chapterId != null) it.chapterId == current.chapterId else it.title == current.title)
            } ?: continue
            var target = mine
            if ("title" in keys && current.title.isNotBlank()) {
                target = target.copy(title = current.title, updatedAt = now())
                applied++
            }
            if ("beats" in keys) {
                target = target.copy(beats = mergeBeats(target.beats, current.beats), updatedAt = now())
                applied++
            }
            if ("merge" in keys) {
                target = target.copy(
                    beats = mergeBeats(target.beats, current.beats),
                    title = target.title.ifBlank { current.title },
                    updatedAt = now(),
                )
                applied++
            }
            b = b.copy(
                outline = b.outline.map { if (it.id == target.id) target else it },
            )
        }

        if (marked.isEmpty()) return 0
        // 3.26.46：先写 bible + journal（记下这一批的批次号），再打「已同步」标记；
        // 万一两步之间被杀，只是标记没打上（可以再同步一次），不会留下撤不掉的孤儿标记。
        val batch = "sync_" + uuid()
        val ids = marked.toList()
        apply(
            ctx, store, bookId, before, b, "author", "merge", "merge",
            "同步 AI 内容到我的（${ids.size} 条，$applied 处字段）", merge = true,
            extra = JSONObject().apply {
                put("syncBatch", batch)
                put("syncedIds", JSONArray(ids))
            },
        )
        markSynced(ctx, bookId, ids, batch)
        return ids.size
    }

    private fun undoLastMergeOnMain(ctx: Context, bookId: String): Boolean {
        val store = appStoreOrNull() ?: return false
        val arr = loadJournal(ctx)
        var index = -1
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            if (!e.optBoolean("merge", false)) continue
            if (e.optString("bookId") != bookId) continue
            index = i
            break
        }
        if (index < 0) return false
        val entry = arr.optJSONObject(index) ?: return false
        val before = entry.optJSONObject("before")?.let { decodeBible(it) } ?: return false
        store.mutate { d ->
            d.copy(books = d.books.map { b -> if (b.id == bookId) b.copy(bible = before) else b })
        }
        // 3.26.46：连这一批「已同步」标记一起清掉（旧版 journal 没有批次号，按 id 兜底）。
        val batch = entry.optString("syncBatch")
        val markedIds = entry.optJSONArray("syncedIds")?.toStringList() ?: emptyList()
        if (batch.isNotBlank() || markedIds.isNotEmpty()) {
            clearSynced(ctx, bookId, batch, markedIds)
        }
        val rest = JSONArray()
        for (i in 0 until arr.length()) if (i != index) rest.put(arr.opt(i))
        writeJournal(ctx, rest)
        return true
    }

    private data class MergeKey(val kind: String, val entryId: String, val key: String)

    private fun mergeId(kind: String, entryId: String, key: String): String = "m:$kind:$entryId:$key"

    private fun parseMergeId(id: String): MergeKey? {
        if (!id.startsWith("m:")) return null
        val parts = id.substring(2).split(':')
        if (parts.size != 3) return null
        if (parts[0] !in setOf("character", "relation", "event", "outline")) return null
        return MergeKey(parts[0], parts[1], parts[2])
    }

    private fun samePair(r: StoryRelation, a: String, b: String): Boolean =
        (r.from == a && r.to == b) || (r.from == b && r.to == a)

    private fun mergeBeats(mine: List<OutlineBeat>, ai: List<OutlineBeat>): List<OutlineBeat> {
        val out = ArrayList<OutlineBeat>(mine.size + ai.size)
        out.addAll(mine)
        val seen = HashSet<String>()
        mine.forEach { seen += it.text }
        ai.forEach { beat ->
            if (beat.text.isNotBlank() && seen.add(beat.text)) out += beat.copy(id = uuid(), done = false)
        }
        return out
    }

    /** 同文保留原勾选状态，新文本新建。 */
    private fun buildBeats(mine: List<OutlineBeat>, texts: List<String>): List<OutlineBeat> {
        val existing = HashMap<String, OutlineBeat>()
        mine.forEach { existing[it.text] = it }
        val out = ArrayList<OutlineBeat>(texts.size)
        texts.forEach { raw ->
            val text = raw.trim()
            if (text.isNotBlank()) out += existing[text] ?: OutlineBeat(id = uuid(), text = text, done = false)
        }
        return out
    }

    private fun parseRelationKind(raw: String): RelationKind =
        RelationKind.entries.firstOrNull { it.name == raw }
            ?: RelationKind.entries.firstOrNull { it.label == raw }
            ?: RelationKind.OTHER

    private fun kindLabel(kind: String): String = when (kind) {
        "character" -> "角色"
        "relation" -> "关系"
        "event" -> "事件"
        "outline" -> "大纲"
        else -> "故事圣经"
    }

    private fun blankShow(text: String?): String = if (text.isNullOrBlank()) "（空）" else text

    private fun chapterTitleText(book: Book, ids: List<String>): String {
        val titles = ids.mapNotNull { id -> book.chapters.firstOrNull { it.id == id }?.title }
        return if (titles.isEmpty()) "（无）" else titles.joinToString("、")
    }

    private fun namesText(bible: BookBible, ids: List<String>): String {
        val names = ids.mapNotNull { id -> bible.characters.firstOrNull { it.id == id }?.name }
        return if (names.isEmpty()) "（无）" else names.joinToString("、")
    }

    private fun JSONArray.toStringList(): List<String> {
        val out = ArrayList<String>(length())
        for (i in 0 until length()) out += optString(i)
        return out
    }

    private fun charName(bible: BookBible, id: String): String =
        bible.characters.firstOrNull { it.id == id }?.name ?: id

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

/**
 * 3.26.50：AI / 自动整理给的 `relation` 文本 → 落库后的类型三件套（底档 + 显示名 + 档位）。
 *
 * 与 [AiBible.mutate] 的关系 upsert 是**同一口径**（那边直接调这个函数），自动整理判断
 * 「无变化」时也复用它，避免「判据说没变、落库却改了一处」这种两处漂移。
 *
 * - 内置枚举名 / 中文名（[RelationKind]）→ 内置类型，档位回到枚举派生（null）；
 * - 其它文本 = 自定义类型：先认这本书里已有的同名自定义类型（取它的底档 / 档位），
 *   认不出来就保留 `old` 这条关系原来的底档 / 档位；`old == null`（新关系）落 OTHER / null。
 */
internal data class RelationTypeSpec(
    val kind: RelationKind,
    val label: String,
    val kindTier: Int?,
    val builtIn: Boolean,
) {
    /** 实际生效档位：显式值优先，缺 / 越界按底档派生（与 [StoryRelation.tier] 同口径）。 */
    val tier: Int get() = kindTier?.takeIf { it in RELATION_TIER_MIN..RELATION_TIER_MAX } ?: kind.tier
}

internal fun relationTypeSpec(raw: String, old: StoryRelation?, bible: BookBible): RelationTypeSpec {
    val rawKind = raw.trim()
    val builtIn = RelationKind.entries.firstOrNull { it.name == rawKind }
        ?: RelationKind.entries.firstOrNull { it.label == rawKind }
    if (builtIn != null) return RelationTypeSpec(builtIn, builtIn.label, null, builtIn = true)
    val base = old ?: StoryRelation(from = "", to = "")
    if (rawKind.isBlank()) {
        return RelationTypeSpec(base.kind, base.kindText, base.kindTier, builtIn = false)
    }
    val label = capRelationLabel(rawKind)
    val known = bible.relations.firstOrNull { it.customKind && it.kindText == label }
    return if (known != null) {
        RelationTypeSpec(known.kind, label, known.kindTier, builtIn = false)
    } else {
        RelationTypeSpec(base.kind, label, base.kindTier, builtIn = false)
    }
}

// ───────────────────── 3.26.45 故事页直接调的对外签名 ─────────────────────

/**
 * 一条字段级故事圣经差异（待同意卡 / 同步预览共用）。
 *
 * - `kind`：`character` / `relation` / `event` / `outline`；
 * - `field`：人能看懂的字段名 —— 名字 / 身份 / 备注 / 颜色 / 类型 / 两端 / 时间 / 标题 /
 *   情节点 / 归属 / 并入 / 删除；空值已经写成「（空）」；
 * - `title`：条目名（角色名、`A—B`、事件标题、章节名）。
 */
data class BibleChange(
    val id: String,
    val kind: String,
    val title: String,
    val field: String,
    val before: String,
    val after: String,
    val bookId: String,
)

/** 待同意的 AI 差异（按写入顺序，旧的在前；侧车持久化，进程被杀不丢）。 */
fun pendingChanges(bookId: String): List<BibleChange> = AiBible.pendingChanges(bookId)

/** 同意 / 拒绝一条待同意差异。同意才写 bible，拒绝只记一条 journal。 */
fun resolveChange(id: String, accept: Boolean): Boolean = AiBible.resolveChange(id, accept)

/** 「同步 AI 内容到我的」预览：AI 条目 → 作者条目的字段级差异。 */
fun previewMerge(bookId: String): List<BibleChange> = AiBible.previewMerge(bookId)

/** 执行选中的合并项（id 来自 [previewMerge]），返回实际落地的条数。 */
fun applyMerge(bookId: String, ids: List<String>): Int = AiBible.applyMerge(bookId, ids)

/** 一步撤销最近一次「同步 AI 内容到我的」（同时清掉那一批「已同步」标记）。 */
fun undoLastMerge(bookId: String): Boolean = AiBible.undoLastMerge(bookId)

/** 3.26.46：这本书里已经同步到「我的」的 AI 条目 id（侧车标记，AI 页据此淡一档）。 */
fun bibleSyncedIds(bookId: String): Set<String> = AiBible.syncedIds(bookId)

