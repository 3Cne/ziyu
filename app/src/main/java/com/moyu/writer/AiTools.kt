package com.moyu.writer

import android.content.Context
import org.json.JSONObject

/** What a queued change will do once the writer taps 采纳. */
enum class AiPendingKind {
    Note, NoteEdit, NoteOff,
    Append, Replace,
    Memo, GlobalMemo, MemoOff,
    Book, BookEdit, BookOff,
    Chapter, ChapterEdit, ChapterOff,
    Group, GroupEdit, GroupOff, GroupBind,
    BindNote, Todo, TodoDone, Style,
    Export, Backup, Import, Reminder, Goal, Restore,
    /** 3.26.29：把一章恢复成某个历史版本。 */
    ChapterRestore,
}

/** A change the model proposed. Nothing is written until the writer accepts it. */
data class AiPending(
    val label: String,
    val kind: AiPendingKind = AiPendingKind.Append,
    /** Empty for a new note. */
    val chapterId: String = "",
    val text: String = "",
    val isNewNote: Boolean = false,
    val noteTitle: String = "",
    /** Replace: the exact passage that will be swapped out. */
    val findText: String = "",
    /** Memory fields. */
    val memoKind: String = "",
    val memoName: String = "",
    val memoDetail: String = "",
    /** For MemoOff: the fact that will be switched off. */
    val memoId: String = "",
    /** Targets for the full-app tools. */
    val bookId: String = "",
    val noteId: String = "",
    val chapterTitle: String = "",
    val bookTitle: String = "",
    /** "book" or "note" for group operations. */
    val groupKind: String = "",
    val groupId: String = "",
    val groupName: String = "",
    /** Free-form payload (style values, note edits, todo text). */
    val payload: String = "",
    /** Deletions need a second confirmation before they run. */
    val danger: Boolean = false,
)

/**
 * Everything a tool needs that is not in AppStore: the Android context (files,
 * exports, backups), the files picked in this conversation, the web switch and
 * its per-answer budget, and the navigation callbacks.
 *
 * The budget is mutable on purpose: the model may ask to search five times in
 * one answer, and each call has to decrement the same counter it was given.
 */
class AiToolEnv(
    val context: Context,
    val attachments: List<Attachment> = emptyList(),
    val webEnabled: Boolean = false,
    val webMax: Int = 3,
    /** 0 只读 / 1 询问 / 2 自动。只读档下写工具一律不执行。 */
    val writeMode: Int = 1,
    val key: String = "",
    val baseUrl: String = "",
    val openBook: (String) -> Unit = {},
    val openChapter: (String, String) -> Unit = { _, _ -> },
    val openNote: (String) -> Unit = {},
) {
    var searches = 0
    var fetches = 0
}

/**
 * The tools the writer's assistant may call. Reads are free; writes are only
 * ever *queued* — the model gets "已写入" back, and the actual edit happens at the end of
 * the writer taps 采纳. A model that could silently rewrite the manuscript
 * would be worse than no assistant at all.
 *
 * Deletions are offered too (the writer asked for full-app reach), but every
 * queued deletion carries [AiPending.danger] and the UI asks once more.
 */
object AiTools {
    /** Longest chapter handed over whole; longer ones go through the index. */
    private const val READ_WHOLE = 1200

    /** Longest single segment reply, so one read cannot eat the whole budget. */
    private const val READ_MAX = 1200

    private const val READ_NOTE_MAX = 2400

    /**
     * 3.26.29：会改东西的工具。只读档下这些一律不执行——
     * 作者选「只读」就是只要回答，不要任何写盘动作（含圣经、导出、备份）。
     */
    private val WRITE_TOOLS = setOf(
        "create_book", "edit_book", "delete_book",
        "create_chapter", "rename_chapter", "delete_chapter",
        "append_to_chapter", "replace_in_chapter", "restore_chapter_history",
        "create_note", "edit_note", "delete_note", "bind_note",
        "create_group", "rename_group", "delete_group", "move_to_group",
        "add_todo", "complete_todo", "set_style",
        "export_item", "backup_now", "import_attachment",
        "set_reminder", "set_daily_goal", "restore_backup",
        "write_story_bible", "undo_bible_change",
    )

    /** 询问档下只排补丁、当场不落地的三个工具。 */
    private val PATCH_TOOLS = setOf(
        "append_to_chapter", "replace_in_chapter", "restore_chapter_history",
    )

    val DEFS: String = """
    [
      {"type":"function","function":{"name":"list_books","description":"列出所有书：标题、章节数、字数。要操作别的书先看这里","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"list_chapters","description":"列出某本书的章节标题和字数；不填 book 就是当前书","parameters":{"type":"object","properties":{"book":{"type":"string","description":"书名，不填=当前书"}}}}},
      {"type":"function","function":{"name":"read_chapter","description":"读某一章。不带 from 时：短章（1200 字内）直接给全文，长章只给段落索引；带 from/count 时按段号读原文，单次最多 1200 字","parameters":{"type":"object","properties":{"title":{"type":"string","description":"章节标题，例如 第二章"},"book":{"type":"string","description":"书名，不填=当前书"},"from":{"type":"integer","description":"从第几段开始，1 起"},"count":{"type":"integer","description":"读几段，默认 1"}},"required":["title"]}}},
      {"type":"function","function":{"name":"search_book","description":"在全书里搜一个关键词（人名、物件、伏笔、地点），返回命中的章节和前后文","parameters":{"type":"object","properties":{"keyword":{"type":"string","description":"要搜的词，两到六个字最好"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["keyword"]}}},
      {"type":"function","function":{"name":"list_notes","description":"列出本书绑定到章节的笔记标题；不填 book 就是当前书","parameters":{"type":"object","properties":{"book":{"type":"string","description":"书名，不填=当前书"}}}}},
      {"type":"function","function":{"name":"list_all_notes","description":"列出所有笔记的标题、字数、是否绑定了章节（跨书）","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"read_note","description":"读一条笔记的正文（最多 2400 字）","parameters":{"type":"object","properties":{"title":{"type":"string","description":"笔记标题，或其中几个字"}},"required":["title"]}}},
      {"type":"function","function":{"name":"list_groups","description":"列出书籍分组和笔记分组","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"get_settings","description":"读当前排版与外观：主题、字体、字号、行距、页边距、纸张、分节样式、每日目标","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"get_stats","description":"读统计：总字数、今日字数、连续天数、书数、笔记数","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"create_book","description":"新建一本书；要等作者确认才写入","parameters":{"type":"object","properties":{"title":{"type":"string"},"author":{"type":"string"},"genre":{"type":"string"},"synopsis":{"type":"string"}},"required":["title"]}}},
      {"type":"function","function":{"name":"edit_book","description":"改一本书的书名/作者/类型/简介；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"现有书名"},"new_title":{"type":"string"},"author":{"type":"string"},"genre":{"type":"string"},"synopsis":{"type":"string"}},"required":["title"]}}},
      {"type":"function","function":{"name":"delete_book","description":"删除一本书及其全部章节；不可恢复，要等作者二次确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"书名，必须写全"}},"required":["title"]}}},
      {"type":"function","function":{"name":"create_chapter","description":"给某本书新建一章；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["title"]}}},
      {"type":"function","function":{"name":"rename_chapter","description":"改某一章的标题；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"现在的标题"},"new_title":{"type":"string"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["title","new_title"]}}},
      {"type":"function","function":{"name":"delete_chapter","description":"删除一章及其正文；不可恢复，要等作者二次确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"章节标题，必须写全"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["title"]}}},
      {"type":"function","function":{"name":"append_to_chapter","description":"在某章末尾追加一段；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"章节标题"},"text":{"type":"string"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["title","text"]}}},
      {"type":"function","function":{"name":"replace_in_chapter","description":"改某一章里已经写好的一小段：给出原文片段和改后片段，章节里必须能原样找到这段原文；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"章节标题"},"find":{"type":"string","description":"要替换掉的原文，必须原样抄，标点和换行都不能改"},"replace":{"type":"string","description":"改后的文字"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["title","find","replace"]}}},
      {"type":"function","function":{"name":"create_note","description":"建一条笔记，可指定分组名；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string"},"body":{"type":"string"},"group":{"type":"string","description":"笔记分组名，可不填"}},"required":["title","body"]}}},
      {"type":"function","function":{"name":"edit_note","description":"改一条笔记的标题/正文/置顶；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"现有标题"},"new_title":{"type":"string"},"body":{"type":"string"},"pinned":{"type":"boolean"}},"required":["title"]}}},
      {"type":"function","function":{"name":"delete_note","description":"删除一条笔记（正文和待办一起删）；不可恢复，要等作者二次确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"笔记标题，必须写全"}},"required":["title"]}}},
      {"type":"function","function":{"name":"bind_note","description":"把一条笔记绑定到某一章，或解绑；要等作者确认","parameters":{"type":"object","properties":{"note_title":{"type":"string"},"chapter_title":{"type":"string"},"book":{"type":"string","description":"书名，不填=当前书"},"on":{"type":"boolean","description":"true=绑定，false=解绑，默认 true"}},"required":["note_title","chapter_title"]}}},
      {"type":"function","function":{"name":"create_group","description":"新建书籍分组或笔记分组；要等作者确认","parameters":{"type":"object","properties":{"kind":{"type":"string","enum":["book","note"]},"name":{"type":"string"}},"required":["kind","name"]}}},
      {"type":"function","function":{"name":"rename_group","description":"改分组名；要等作者确认","parameters":{"type":"object","properties":{"kind":{"type":"string","enum":["book","note"]},"name":{"type":"string"},"new_name":{"type":"string"}},"required":["kind","name","new_name"]}}},
      {"type":"function","function":{"name":"delete_group","description":"删除一个分组（里面的书/笔记回到未分组，不会删内容）；要等作者确认","parameters":{"type":"object","properties":{"kind":{"type":"string","enum":["book","note"]},"name":{"type":"string"}},"required":["kind","name"]}}},
      {"type":"function","function":{"name":"move_to_group","description":"把一本书或一条笔记移到某个分组；要等作者确认","parameters":{"type":"object","properties":{"kind":{"type":"string","enum":["book","note"]},"name":{"type":"string","description":"目标分组名"},"item_title":{"type":"string","description":"书名或笔记标题"}},"required":["kind","name","item_title"]}}},
      {"type":"function","function":{"name":"add_todo","description":"给一条笔记加一条待办；要等作者确认","parameters":{"type":"object","properties":{"note_title":{"type":"string"},"text":{"type":"string"}},"required":["note_title","text"]}}},
      {"type":"function","function":{"name":"complete_todo","description":"把一条笔记里的某项待办标为完成；要等作者确认","parameters":{"type":"object","properties":{"note_title":{"type":"string"},"text":{"type":"string","description":"待办文字，或其中几个字"}},"required":["note_title","text"]}}},
      {"type":"function","function":{"name":"set_style","description":"改排版或外观：只传要改的项。font 用 sans/serif/kai；theme 用 paper/eye/ink/sand；paper_style 用 plain/solid/lines/grid；section_style 用 line/blank/star/ellipsis。要等作者确认","parameters":{"type":"object","properties":{"font_size":{"type":"number","description":"0.82 到 1.3，1 是默认"},"line_height":{"type":"number","description":"1.3 到 2.4"},"page_margin":{"type":"number","description":"12 到 40 dp"},"font":{"type":"string"},"theme":{"type":"string"},"paper_style":{"type":"string"},"section_style":{"type":"string"}},"required":[]}}},
      {"type":"function","function":{"name":"search_all","description":"在全部书和笔记里搜关键词，跨书找伏笔、设定、旧笔记","parameters":{"type":"object","properties":{"keyword":{"type":"string","description":"两到六个字"},"limit":{"type":"integer","description":"最多几处，默认 12"}},"required":["keyword"]}}},
      {"type":"function","function":{"name":"get_progress","description":"读最近在读的书和章，以及每本书的进度","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"read_attachment","description":"读作者在这条对话里传的附件；一次最多 6000 字，用 from 翻页","parameters":{"type":"object","properties":{"name":{"type":"string","description":"附件名，或其中几个字"},"from":{"type":"integer","description":"从第几页开始，1 起"}},"required":["name"]}}},
      {"type":"function","function":{"name":"web_search","description":"联网搜索，返回标题、网址和摘要；要作者先打开联网","parameters":{"type":"object","properties":{"query":{"type":"string"},"count":{"type":"integer","description":"几条，默认 5"}},"required":["query"]}}},
      {"type":"function","function":{"name":"web_fetch","description":"抓一个网页的正文（Markdown），最多 6000 字","parameters":{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}}},
      {"type":"function","function":{"name":"list_backups","description":"列出 App 自己导出的最近备份：时间、大小","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"open_item","description":"直接打开一本书、一章或一条笔记；不改内容","parameters":{"type":"object","properties":{"kind":{"type":"string","enum":["book","chapter","note"]},"title":{"type":"string"},"book":{"type":"string","description":"章节所属书名，不填=当前书"}},"required":["kind","title"]}}},
      {"type":"function","function":{"name":"export_item","description":"把一本书导出 EPUB/TXT，或把一条笔记导出 TXT/MD/JSON；要等作者确认","parameters":{"type":"object","properties":{"kind":{"type":"string","enum":["book","note"]},"title":{"type":"string"},"format":{"type":"string","description":"book: epub/txt；note: txt/md/json"}},"required":["kind","title"]}}},
      {"type":"function","function":{"name":"backup_now","description":"把全部数据导出成一份 JSON 备份；要等作者确认","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"import_attachment","description":"把附件导入成一本新书或一条新笔记；要等作者确认","parameters":{"type":"object","properties":{"name":{"type":"string"},"as":{"type":"string","enum":["book","note"]}},"required":["name"]}}},
      {"type":"function","function":{"name":"set_reminder","description":"给某条笔记里的待办设提醒；要等作者确认","parameters":{"type":"object","properties":{"note_title":{"type":"string"},"todo_text":{"type":"string"},"at":{"type":"string","description":"本地时间，如 2026-11-13 09:00"}},"required":["note_title","todo_text","at"]}}},
      {"type":"function","function":{"name":"set_daily_goal","description":"改每日写作目标（100–100000 字）；要等作者确认","parameters":{"type":"object","properties":{"words":{"type":"integer"}},"required":["words"]}}},
      {"type":"function","function":{"name":"restore_backup","description":"用某份备份覆盖当前全部数据；不可撤销。作者明确同意后才会执行，执行前还会再确认一次","parameters":{"type":"object","properties":{"name":{"type":"string","description":"list_backups 返回的文件名"}},"required":["name"]}}},
      {"type":"function","function":{"name":"read_story_bible","description":"读故事圣经（AI 的长期记忆）：角色/关系/事件/大纲；不传 section 给全貌","parameters":{"type":"object","properties":{"book":{"type":"string","description":"书名，不填=当前书"},"section":{"type":"string","enum":["characters","relations","events","outline"],"description":"只看一类"}}}}},
      {"type":"function","function":{"name":"read_chapter_deep","description":"一次读完一章：正文+本章情节点+本章事件+出场角色（比 read_chapter 更深）","parameters":{"type":"object","properties":{"title":{"type":"string"},"book":{"type":"string"}},"required":["title"]}}},
      {"type":"function","function":{"name":"write_story_bible","description":"写故事圣经（立即写入并留一条可撤销记录）。kind=character/relation/event/outline；action=upsert/delete，outline 另支持 add_beat/update_beat/delete_beat/clear_beats/delete_node","parameters":{"type":"object","properties":{"book":{"type":"string"},"kind":{"type":"string","enum":["character","relation","event","outline"]},"action":{"type":"string","description":"默认 upsert"},"name":{"type":"string","description":"角色名"},"role":{"type":"string"},"note":{"type":"string"},"from":{"type":"string","description":"关系起点角色名"},"to":{"type":"string","description":"关系终点角色名"},"relation":{"type":"string","description":"亲近/对立/其他 或 枚举名"},"whenText":{"type":"string","description":"事件故事时间，自由文本"},"title":{"type":"string","description":"事件标题"},"chapters":{"type":"array","items":{"type":"string"},"description":"事件所属章节标题，可多个"},"characters":{"type":"array","items":{"type":"string"},"description":"事件涉及角色名"},"chapter":{"type":"string","description":"大纲所在章节标题"},"text":{"type":"string","description":"情节点文字 / update_beat 的原文字"},"to_text":{"type":"string","description":"update_beat 改后的文字"},"beats":{"type":"array","items":{"type":"string"},"description":"整章情节点（替换）"}},"required":["kind"]}}},
      {"type":"function","function":{"name":"undo_bible_change","description":"撤销最近一次 AI 对故事圣经的改动（含章节保存后的自动整理）","parameters":{"type":"object","properties":{"book":{"type":"string"}}}}},
      {"type":"function","function":{"name":"list_chapter_history","description":"列出一章的历史版本：时间与字数。改坏了可以用它找回来","parameters":{"type":"object","properties":{"title":{"type":"string","description":"章节标题"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["title"]}}},
      {"type":"function","function":{"name":"read_chapter_history","description":"读某一章的某个历史版本（最多 6000 字）","parameters":{"type":"object","properties":{"title":{"type":"string"},"at":{"type":"string","description":"list_chapter_history 返回的时间戳"},"book":{"type":"string"}},"required":["title","at"]}}},
      {"type":"function","function":{"name":"restore_chapter_history","description":"把某一章恢复成某个历史版本；要等作者确认（询问档出确认卡，自动档直接恢复）","parameters":{"type":"object","properties":{"title":{"type":"string"},"at":{"type":"string","description":"时间戳"},"book":{"type":"string"}},"required":["title","at"]}}}
    ]
    """.trimIndent()

    /** Short Chinese name for the per-step trace under an answer. */
    fun label(name: String): String = when (name) {
        "list_books" -> "列书"
        "list_chapters" -> "列章"
        "read_chapter" -> "读章"
        "search_book" -> "搜书"
        "list_notes" -> "列笔记"
        "list_all_notes" -> "列全部笔记"
        "read_note" -> "读笔记"
        "list_groups" -> "列分组"
        "get_settings" -> "读设置"
        "get_stats" -> "读统计"
        "create_book" -> "新建书"
        "edit_book" -> "改书"
        "delete_book" -> "删书"
        "create_chapter" -> "新建章"
        "rename_chapter" -> "改章名"
        "delete_chapter" -> "删章"
        "append_to_chapter" -> "追加正文"
        "replace_in_chapter" -> "替换正文"
        "create_note" -> "新建笔记"
        "edit_note" -> "改笔记"
        "delete_note" -> "删笔记"
        "bind_note" -> "绑定笔记"
        "create_group" -> "新建分组"
        "rename_group" -> "改分组"
        "delete_group" -> "删分组"
        "move_to_group" -> "移动分组"
        "add_todo" -> "加待办"
        "complete_todo" -> "完成待办"
        "set_style" -> "改排版"
        "save_memory" -> "存记忆"
        "delete_memory" -> "删记忆"
        "search_all" -> "全局搜索"
        "get_progress" -> "读进度"
        "read_attachment" -> "读附件"
        "web_search" -> "联网搜索"
        "web_fetch" -> "抓网页"
        "list_backups" -> "列备份"
        "open_item" -> "打开"
        "export_item" -> "导出"
        "backup_now" -> "备份"
        "import_attachment" -> "导入附件"
        "set_reminder" -> "设提醒"
        "set_daily_goal" -> "改目标"
        "restore_backup" -> "恢复备份"
        "read_story_bible" -> "读圣经"
        "read_chapter_deep" -> "深读一章"
        "write_story_bible" -> "改圣经"
        "undo_bible_change" -> "撤销圣经"
        "list_chapter_history" -> "列历史"
        "read_chapter_history" -> "读历史"
        "restore_chapter_history" -> "恢复历史"
        else -> name
    }

    /**
     * 把 AI 写来的正文套进目标章已有的排版：每段补回该章的段首前缀，段间只留一个换行。
     * 2026-11-14 用户报「直接让问书修改正文时也不会读格式，会乱加换行」——
     * 写入路径原来是把模型给的原样塞进正文，模型的硬换行就成了正文的换行。
     */
    private fun fitToChapter(template: String, incoming: String, autoIndent: Boolean): String {
        val paras = template.split('\n').filter { it.isNotBlank() }
        val prefix = paras
            .map { line -> line.takeWhile { it.isWhitespace() } }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key
            ?: if (autoIndent) "\u3000\u3000" else ""
        val blocks = incoming
            .replace("\r\n", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (blocks.isEmpty()) return incoming.trim()
        return blocks.joinToString("\n") { prefix + it }
    }

    suspend fun run(
        name: String,
        args: JSONObject,
        store: AppStore,
        book: Book,
        notes: List<Note>,
        pending: MutableList<AiPending>,
        facts: List<MemoFact> = emptyList(),
        globalFacts: List<MemoFact> = emptyList(),
        env: AiToolEnv? = null,
    ): String {
        if (env?.writeMode == 0 && name in WRITE_TOOLS) {
            return "现在是只读档：只回答，不改稿。请直接用文字告诉作者要怎么改。"
        }
        val out = when (name) {
        "list_books" -> store.books.joinToString("\n") { b ->
            "· 《" + b.title + "》" + b.chapters.size + " 章 · " + b.words + " 字" +
                (if (b.id == book.id) "（当前）" else "")
        }.ifBlank { "还没有书" }

        "list_chapters" -> {
            val b = findBook(store, args.optString("book")) ?: book
            if (b.chapters.isEmpty()) "《" + b.title + "》还没有章节"
            else b.chapters.joinToString("\n") { c -> "· " + c.title + "（" + countWords(c.content) + " 字）" }
        }

        "search_book" -> {
            val b = findBook(store, args.optString("book")) ?: book
            val kw = args.optString("keyword").trim()
            if (kw.length < 2) "关键词太短，给两个字以上"
            else {
                val hits = ArrayList<String>()
                for (c in b.chapters) {
                    var from = 0
                    var shown = 0
                    while (shown < 3) {
                        val at = c.content.indexOf(kw, from)
                        if (at < 0) break
                        val start = (at - 60).coerceAtLeast(0)
                        val end = (at + kw.length + 60).coerceAtMost(c.content.length)
                        hits += c.title + "｜…" + c.content.substring(start, end).replace("\n", " ") + "…"
                        from = at + kw.length
                        shown++
                    }
                }
                if (hits.isEmpty()) "《" + b.title + "》里没搜到「" + kw + "」"
                else "搜到 " + hits.size + " 处：\n" + hits.joinToString("\n")
            }
        }

        "read_chapter" -> readChapter(store, args, book)

        "list_notes" -> {
            val b = findBook(store, args.optString("book")) ?: book
            val mine = b.chapters.map { it.id }.toSet()
            val visible = notes.filter { n -> n.chapterIds.any { it in mine } }
            when {
                notes.isEmpty() -> "还没有笔记"
                visible.isEmpty() -> "《" + b.title + "》还没有绑定笔记（散笔记不算，要用就把它绑到某一章）"
                else -> visible.take(30).joinToString("\n") { n ->
                    val todos = n.todos.count { !it.done }
                    "· " + (n.title.ifBlank { "无标题笔记" }) +
                        if (todos > 0) "（$todos 项待办未完成）" else ""
                }
            }
        }

        "list_all_notes" -> if (notes.isEmpty()) "还没有笔记" else notes.joinToString("\n") { n ->
            val todos = n.todos.count { !it.done }
            "· " + (n.title.ifBlank { "无标题笔记" }) + "（" + n.words + " 字" +
                (if (n.chapterIds.isEmpty()) "，未绑定章节" else "，已绑定") +
                (if (todos > 0) "，$todos 项待办未完成" else "") + "）"
        }

        "read_note" -> {
            val q = args.optString("title").trim()
            val n = findNote(store, q)
            when {
                q.isBlank() -> "没给笔记标题"
                n == null -> "没有叫「" + q + "」的笔记。现有：" + notes.take(12).joinToString("、") { it.title.ifBlank { "无标题笔记" } }
                else -> {
                    val body = n.content.trim()
                    "《" + (n.title.ifBlank { "无标题笔记" }) + "》" +
                        if (body.isEmpty()) "（正文为空）"
                        else "\n" + body.take(READ_NOTE_MAX) +
                            (if (body.length > READ_NOTE_MAX) "\n……这条超过 " + READ_NOTE_MAX + " 字，只给前半" else "")
                }
            }
        }

        "list_groups" -> {
            val bookGroups = store.bookGroups
            val noteGroups = store.noteGroups
            "书籍分组：" + (if (bookGroups.isEmpty()) "无" else bookGroups.joinToString("、") { it.name }) +
                "\n笔记分组：" + (if (noteGroups.isEmpty()) "无" else noteGroups.joinToString("、") { it.name })
        }

        "get_settings" -> {
            val s = store.data.settings
            "主题：" + s.theme + "　字体：" + s.font + "　字号：" + "%.2f".format(java.util.Locale.US, s.fontScale) +
                "　行距：" + "%.2f".format(java.util.Locale.US, s.lineHeight) +
                "　页边距：" + s.pageMargin.toInt() + "dp" +
                "\n纸张：" + s.paperStyle + "　分节：" + s.sectionStyle + "　目标：" + s.dailyGoal + " 字/天"
        }

        "get_stats" -> {
            val total = store.books.sumOf { b -> b.chapters.sumOf { countWords(it.content) } }
            "书 " + store.books.size + " 本、笔记 " + store.notes.size + " 条；总字数 " + total +
                "，今日 " + store.todayWords() + " 字，连续 " + store.streak() + " 天"
        }

        "create_book" -> {
            val title = args.optString("title").trim()
            if (title.isBlank()) "书名不能为空"
            else {
                pending += AiPending(
                    label = "新建书《" + title + "》",
                    kind = AiPendingKind.Book,
                    bookTitle = title,
                    payload = JSONObject().apply {
                        put("author", args.optString("author"))
                        put("genre", args.optString("genre"))
                        put("synopsis", args.optString("synopsis"))
                    }.toString(),
                )
                "已新建"
            }
        }

        "edit_book" -> {
            val b = findBook(store, args.optString("title"))
            val changes = JSONObject().apply {
                for (k in listOf("new_title", "author", "genre", "synopsis")) {
                    if (args.has(k)) put(k, args.optString(k))
                }
            }
            when {
                b == null -> "没有叫「" + args.optString("title") + "」的书。现有：" + store.books.joinToString("、") { it.title }
                changes.length() == 0 -> "没给要改的项目"
                else -> {
                    pending += AiPending(
                        label = "改书《" + b.title + "》",
                        kind = AiPendingKind.BookEdit,
                        bookId = b.id,
                        bookTitle = b.title,
                        payload = changes.toString(),
                    )
                    "已修改"
                }
            }
        }

        "delete_book" -> {
            val b = findBook(store, args.optString("title"))
            if (b == null) "没有叫「" + args.optString("title") + "」的书。删除必须写全书名"
            else {
                pending += AiPending(
                    label = "删书《" + b.title + "》（" + b.chapters.size + " 章）",
                    kind = AiPendingKind.BookOff,
                    bookId = b.id,
                    bookTitle = b.title,
                    danger = true,
                )
                "已记下这次删除，等你在下面确认；删掉不可恢复"
            }
        }

        "create_chapter" -> {
            val b = findBook(store, args.optString("book")) ?: book
            val title = args.optString("title").trim()
            if (title.isBlank()) "章节标题不能为空"
            else {
                pending += AiPending(
                    label = "在《" + b.title + "》新建「" + title + "」",
                    kind = AiPendingKind.Chapter,
                    bookId = b.id,
                    bookTitle = b.title,
                    chapterTitle = title,
                )
                "已新建"
            }
        }

        "rename_chapter" -> {
            val b = findBook(store, args.optString("book")) ?: book
            val c = findChapter(b, args.optString("title"))
            val newTitle = args.optString("new_title").trim()
            when {
                c == null -> "《" + b.title + "》没有叫「" + args.optString("title") + "」的章节。现有：" + b.chapters.joinToString("、") { it.title }
                newTitle.isBlank() -> "新标题不能为空"
                else -> {
                    pending += AiPending(
                        label = "改章名：「" + c.title + "」→「" + newTitle + "」",
                        kind = AiPendingKind.ChapterEdit,
                        bookId = b.id,
                        chapterId = c.id,
                        text = newTitle,
                    )
                    "已修改"
                }
            }
        }

        "delete_chapter" -> {
            val b = findBook(store, args.optString("book")) ?: book
            val c = findChapter(b, args.optString("title"))
            if (c == null) "《" + b.title + "》没有叫「" + args.optString("title") + "」的章节"
            else {
                pending += AiPending(
                    label = "删章《" + b.title + "》·「" + c.title + "」",
                    kind = AiPendingKind.ChapterOff,
                    bookId = b.id,
                    chapterId = c.id,
                    chapterTitle = c.title,
                    danger = true,
                )
                "已记下这次删除，等你在下面确认；删掉不可恢复"
            }
        }

        "append_to_chapter" -> {
            val b = findBook(store, args.optString("book")) ?: book
            val want = args.optString("title")
            val text = args.optString("text")
            val c = findChapter(b, want)
            if (c == null) {
                "《" + b.title + "》没有叫「" + want + "」的章节"
            } else {
                // 2026-11-14 用户报：直接让问书写正文时也不读格式、乱加换行。
                // 追加前先按这一章已有的排版（段首前缀 + 段间单换行）规整一遍。
                val fitted = fitToChapter(c.content, text, store.data.settings.indent)
                pending += AiPending(
                    label = "在「" + c.title + "」末尾加 " + countWords(fitted) + " 字",
                    kind = AiPendingKind.Append,
                    bookId = b.id,
                    chapterId = c.id,
                    text = fitted,
                    noteTitle = c.title,
                )
                "已在「" + c.title + "」末尾写入 " + countWords(fitted) + " 字（排版按这一章现有的段落与缩进）"
            }
        }

        "replace_in_chapter" -> {
            val b = findBook(store, args.optString("book")) ?: book
            val want = args.optString("title")
            val find = args.optString("find")
            val repl = args.optString("replace")
            val c = findChapter(b, want)
            when {
                c == null -> "《" + b.title + "》没有叫「" + want + "」的章节。现有：" + b.chapters.joinToString("、") { it.title }
                find.isBlank() -> "没给要改的原文"
                else -> {
                    val n = countOf(c.content, find)
                    when {
                        n == 0 -> "「" + c.title + "」里没有这段原文。把它原样抄一遍（标点、换行都要一样），或者换更短、更有辨识度的一段"
                        n > 1 -> "这段在「" + c.title + "」里出现了 " + n + " 次，给更长的上下文，只改一处"
                        else -> {
                            val fitted = fitToChapter(c.content, repl, store.data.settings.indent)
                            pending += AiPending(
                                label = "改正文：" + c.title + " · 原 " + find.length + " 字 → 新 " + fitted.length + " 字",
                                kind = AiPendingKind.Replace,
                                bookId = b.id,
                                chapterId = c.id,
                                text = repl,
                                findText = find,
                                noteTitle = c.title,
                            )
                            "已改「" + c.title + "」"
                        }
                    }
                }
            }
        }

        "create_note" -> {
            val title = args.optString("title").ifBlank { "无标题笔记" }
            val group = args.optString("group").trim()
            pending += AiPending(
                label = "建笔记「" + title + "」" + if (group.isNotBlank()) "（分组：" + group + "）" else "",
                kind = AiPendingKind.Note,
                text = args.optString("body"),
                isNewNote = true,
                noteTitle = title,
                groupName = group,
            )
            "已写入"
        }

        "edit_note" -> {
            val n = findNote(store, args.optString("title"))
            val changes = JSONObject().apply {
                if (args.has("new_title")) put("new_title", args.optString("new_title"))
                if (args.has("body")) put("body", args.optString("body"))
                if (args.has("pinned")) put("pinned", args.optBoolean("pinned"))
            }
            when {
                n == null -> "没有叫「" + args.optString("title") + "」的笔记。现有：" + notes.take(12).joinToString("、") { it.title.ifBlank { "无标题笔记" } }
                changes.length() == 0 -> "没给要改的项目"
                else -> {
                    pending += AiPending(
                        label = "改笔记「" + (n.title.ifBlank { "无标题笔记" }) + "」",
                        kind = AiPendingKind.NoteEdit,
                        noteId = n.id,
                        noteTitle = n.title,
                        payload = changes.toString(),
                    )
                    "已修改"
                }
            }
        }

        "delete_note" -> {
            val n = findNote(store, args.optString("title"))
            if (n == null) "没有叫「" + args.optString("title") + "」的笔记。删除必须写全标题"
            else {
                pending += AiPending(
                    label = "删笔记「" + (n.title.ifBlank { "无标题笔记" }) + "」",
                    kind = AiPendingKind.NoteOff,
                    noteId = n.id,
                    noteTitle = n.title,
                    danger = true,
                )
                "已记下这次删除，等你在下面确认；删掉不可恢复"
            }
        }

        "bind_note" -> {
            val b = findBook(store, args.optString("book")) ?: book
            val n = findNote(store, args.optString("note_title"))
            val c = findChapter(b, args.optString("chapter_title"))
            when {
                n == null -> "没有叫「" + args.optString("note_title") + "」的笔记"
                c == null -> "《" + b.title + "》没有叫「" + args.optString("chapter_title") + "」的章节"
                else -> {
                    val on = args.optBoolean("on", true)
                    pending += AiPending(
                        label = (if (on) "绑定" else "解绑") + "笔记「" + (n.title.ifBlank { "无标题笔记" }) + "」↔「" + c.title + "」",
                        kind = AiPendingKind.BindNote,
                        bookId = b.id,
                        noteId = n.id,
                        chapterId = c.id,
                        text = if (on) "1" else "0",
                    )
                    "已" + (if (on) "绑定" else "解绑")
                }
            }
        }

        "create_group" -> {
            val kind = args.optString("kind").ifBlank { "note" }
            val name = args.optString("name").trim()
            if (name.isBlank()) "分组名不能为空"
            else {
                pending += AiPending(
                    label = "新建" + (if (kind == "book") "书籍" else "笔记") + "分组「" + name + "」",
                    kind = AiPendingKind.Group,
                    groupKind = kind,
                    groupName = name,
                )
                "已新建"
            }
        }

        "rename_group" -> {
            val kind = args.optString("kind").ifBlank { "note" }
            val name = args.optString("name").trim()
            val newName = args.optString("new_name").trim()
            val g = groupsOf(store, kind).firstOrNull { it.name == name }
            when {
                g == null -> "没有叫「" + name + "」的分组"
                newName.isBlank() -> "新分组名不能为空"
                else -> {
                    pending += AiPending(
                        label = "改分组名：「" + g.name + "」→「" + newName + "」",
                        kind = AiPendingKind.GroupEdit,
                        groupKind = kind,
                        groupId = g.id,
                        groupName = newName,
                    )
                    "已修改"
                }
            }
        }

        "delete_group" -> {
            val kind = args.optString("kind").ifBlank { "note" }
            val name = args.optString("name").trim()
            val g = groupsOf(store, kind).firstOrNull { it.name == name }
            if (g == null) "没有叫「" + name + "」的分组"
            else {
                pending += AiPending(
                    label = "删分组「" + g.name + "」（内容回到未分组）",
                    kind = AiPendingKind.GroupOff,
                    groupKind = kind,
                    groupId = g.id,
                    groupName = g.name,
                    danger = true,
                )
                "已记下，等你在下面确认后才会执行"
            }
        }

        "move_to_group" -> {
            val kind = args.optString("kind").ifBlank { "note" }
            val name = args.optString("name").trim()
            val item = args.optString("item_title").trim()
            val g = groupsOf(store, kind).firstOrNull { it.name == name }
            val target = if (kind == "book") findBook(store, item) else findNote(store, item)
            when {
                g == null -> "没有叫「" + name + "」的分组"
                target == null -> "没有叫「" + item + "」的" + (if (kind == "book") "书" else "笔记")
                else -> {
                    pending += AiPending(
                        label = "把「" + item + "」移到分组「" + g.name + "」",
                        kind = AiPendingKind.GroupBind,
                        groupKind = kind,
                        groupId = g.id,
                        groupName = g.name,
                        bookId = if (kind == "book") (target as Book).id else "",
                        noteId = if (kind == "note") (target as Note).id else "",
                        bookTitle = item,
                    )
                    "已移动"
                }
            }
        }

        "add_todo" -> {
            val n = findNote(store, args.optString("note_title"))
            val text = args.optString("text").trim()
            when {
                n == null -> "没有叫「" + args.optString("note_title") + "」的笔记"
                text.isBlank() -> "待办内容不能为空"
                else -> {
                    pending += AiPending(
                        label = "给笔记「" + (n.title.ifBlank { "无标题笔记" }) + "」加待办：" + text.take(20),
                        kind = AiPendingKind.Todo,
                        noteId = n.id,
                        text = text,
                    )
                    "已写入"
                }
            }
        }

        "complete_todo" -> {
            val n = findNote(store, args.optString("note_title"))
            val text = args.optString("text").trim()
            val hit = n?.todos?.firstOrNull { !it.done && (it.text == text || (text.isNotBlank() && it.text.contains(text))) }
            when {
                n == null -> "没有叫「" + args.optString("note_title") + "」的笔记"
                hit == null -> "这条笔记里没找到未完成的待办「" + text + "」。现有：" + n.todos.filter { !it.done }.take(10).joinToString("、") { it.text }
                else -> {
                    pending += AiPending(
                        label = "完成待办：" + hit.text.take(24),
                        kind = AiPendingKind.TodoDone,
                        noteId = n.id,
                        text = hit.id,
                    )
                    "已标记完成"
                }
            }
        }

        "set_style" -> {
            val keys = listOf("font_size", "line_height", "page_margin", "font", "theme", "paper_style", "section_style")
            val changes = JSONObject()
            for (k in keys) if (args.has(k)) changes.put(k, args.opt(k))
            if (changes.length() == 0) {
                "没给要改的项"
            } else {
                val parts = ArrayList<String>()
                if (changes.has("font_size")) parts += "字号 " + String.format(java.util.Locale.US, "%.2f", changes.optDouble("font_size"))
                if (changes.has("line_height")) parts += "行距 " + String.format(java.util.Locale.US, "%.2f", changes.optDouble("line_height"))
                if (changes.has("page_margin")) parts += "边距 " + changes.optDouble("page_margin").toInt() + "dp"
                if (changes.has("font")) parts += "字体 " + changes.optString("font")
                if (changes.has("theme")) parts += "主题 " + changes.optString("theme")
                if (changes.has("paper_style")) parts += "纸张 " + changes.optString("paper_style")
                if (changes.has("section_style")) parts += "分节 " + changes.optString("section_style")
                pending += AiPending(
                    label = "改排版：" + parts.joinToString("、"),
                    kind = AiPendingKind.Style,
                    payload = changes.toString(),
                )
                "已生效"
            }
        }

        "save_memory" -> {
            val scope = args.optString("scope").ifBlank { "book" }
            val global = scope == "global"
            val kind = args.optString("kind").trim().ifBlank { if (global) "偏好" else "剧情" }
            val nm = args.optString("name").trim()
            val detail = args.optString("detail").trim()
            if (nm.isBlank() || detail.isBlank()) {
                "名字或内容为空，没有记"
            } else {
                pending += AiPending(
                    label = (if (global) "跨书记忆：" else "记进记忆：") + kind + " · " + nm,
                    kind = if (global) AiPendingKind.GlobalMemo else AiPendingKind.Memo,
                    memoKind = kind,
                    memoName = nm,
                    memoDetail = detail,
                )
                "已写入记忆"
            }
        }

        "delete_memory" -> {
            val scope = args.optString("scope").ifBlank { "book" }
            val global = scope == "global"
            val q = args.optString("name").trim()
            val pool = if (global) globalFacts else facts
            val live = pool.filter { it.enabled }
            val hit = live.firstOrNull { it.name == q }
                ?: live.firstOrNull { q.isNotBlank() && (it.name.contains(q) || q.contains(it.name)) }
            when {
                q.isBlank() -> "没给要停用哪一条"
                hit == null -> "记忆里没找到「" + q + "」。" + (
                    if (live.isEmpty()) "这一层还没有记忆。"
                    else "现有：" + live.take(12).joinToString("、") { it.name }
                    )
                else -> {
                    pending += AiPending(
                        label = "停用记忆：" + hit.kind + " · " + hit.name + if (global) "（跨书）" else "",
                        kind = AiPendingKind.MemoOff,
                        memoId = hit.id,
                        memoName = hit.name,
                        memoKind = hit.kind,
                        memoDetail = hit.detail,
                    )
                    "已停用「" + hit.name + "」"
                }
            }
        }

        "search_all" -> {
            val kw = args.optString("keyword").trim()
            val limit = args.optInt("limit", 12).coerceIn(1, 20)
            if (kw.length < 2) {
                "关键词太短，给两个字以上"
            } else {
                val hits = ArrayList<String>()
                books@ for (b in store.books) {
                    for (c in b.chapters) {
                        var from = 0
                        var n = 0
                        while (n < 2 && hits.size < limit) {
                            val at = c.content.indexOf(kw, from, ignoreCase = true)
                            if (at < 0) break
                            val start = (at - 40).coerceAtLeast(0)
                            val end = (at + kw.length + 40).coerceAtMost(c.content.length)
                            hits += "《" + b.title + "》" + c.title + "｜…" +
                                c.content.substring(start, end).replace("\n", " ") + "…"
                            from = at + kw.length
                            n++
                        }
                        if (hits.size >= limit) break@books
                    }
                }
                if (hits.size < limit) {
                    for (n in store.notes) {
                        val at = n.content.indexOf(kw, 0, ignoreCase = true)
                        if (at < 0) continue
                        val start = (at - 40).coerceAtLeast(0)
                        val end = (at + kw.length + 40).coerceAtMost(n.content.length)
                        hits += "笔记《" + (n.title.ifBlank { "无标题笔记" }) + "》｜…" +
                            n.content.substring(start, end).replace("\n", " ") + "…"
                        if (hits.size >= limit) break
                    }
                }
                if (hits.isEmpty()) "全部书和笔记里都没搜到「" + kw + "」"
                else "搜到 " + hits.size + " 处：\n" + hits.joinToString("\n")
            }
        }

        "get_progress" -> {
            val lastBook = store.book(store.data.lastBookId)
            val lastChapter = lastBook?.chapter(store.data.lastChapterId)
            val sb = StringBuilder()
            if (lastBook == null) {
                sb.append("还没有在读的书\n")
            } else {
                sb.append("最近在读：《").append(lastBook.title).append("》")
                if (lastChapter != null) sb.append("「").append(lastChapter.title).append("」")
                sb.append('\n')
            }
            store.books.take(6).forEach { b ->
                sb.append("· 《").append(b.title).append("》").append(b.chapters.size).append(" 章 · ")
                    .append(b.words).append(" 字 · 最近写：")
                    .append(b.lastChapter()?.title ?: "还没写").append('\n')
            }
            sb.toString().trim()
        }

        "read_attachment" -> {
            val e = env
            val wanted = args.optString("name").trim()
            if (e == null) {
                "error: 工具环境没准备好"
            } else {
                val a = findAttachment(e.attachments, wanted)
                val page = args.optInt("from", 1).coerceAtLeast(1)
                val start = (page - 1) * 6000
                when {
                    a == null -> "没有叫「" + wanted + "」的附件。" +
                        if (e.attachments.isEmpty()) "这条对话还没有附件，让作者点输入框左边的回形针选一个"
                        else "现有：" + e.attachments.joinToString("、") { it.name }
                    start >= a.text.length -> "《" + a.name + "》共 " + a.chars + " 字，没有第 " + page + " 页"
                    else -> {
                        val end = (start + 6000).coerceAtMost(a.text.length)
                        "附件《" + a.name + "》（共 " + a.chars + " 字，第 " + (start + 1) + "–" + end + " 字）：\n\n" +
                            a.text.substring(start, end)
                    }
                }
            }
        }

        "web_search" -> {
            val e = env
            when {
                e == null -> "error: 工具环境没准备好"
                !e.webEnabled -> "联网没开。作者可以在 设置 → 问书 → 联网与文件 打开"
                e.searches >= e.webMax -> "这次已经搜了 " + e.searches + " 次，到上限了；先用已有结果回答"
                else -> {
                    e.searches++
                    val query = args.optString("query").trim()
                    val r = AiWeb.search(e.baseUrl, e.key, query, args.optInt("count", 5).coerceIn(1, 8))
                    when {
                        r.error != null -> "error: 搜索没成功（" + r.error + "）"
                        r.hits.isEmpty() -> "没搜到「" + query + "」的结果"
                        else -> "搜到 " + r.hits.size + " 条：\n" + r.hits.joinToString("\n") { h ->
                            "· " + h.title + "｜" + h.site.ifBlank { "网页" } +
                                (if (h.date.isNotBlank()) "｜" + h.date.take(10) else "") + "\n  " +
                                h.url + "\n  " + h.summary.ifBlank { h.snippet }.take(300)
                        }
                    }
                }
            }
        }

        "web_fetch" -> {
            val e = env
            when {
                e == null -> "error: 工具环境没准备好"
                !e.webEnabled -> "联网没开。作者可以在 设置 → 问书 → 联网与文件 打开"
                e.fetches >= 3 -> "这次已经抓了 " + e.fetches + " 页，到上限了"
                else -> {
                    e.fetches++
                    val p = AiWeb.fetch(e.baseUrl, e.key, args.optString("url"))
                    if (p.error != null) {
                        "error: 抓取没成功（" + p.error + "）"
                    } else {
                        "《" + p.title.ifBlank { "网页" } + "》\n\n" + p.content.take(6000) +
                            (if (p.content.length > 6000) "\n……原文 " + p.content.length + " 字，只给前 6000 字" else "")
                    }
                }
            }
        }

        "list_backups" -> {
            val e = env
            if (e == null) {
                "error: 工具环境没准备好"
            } else {
                val list = BackupOps.list(e.context)
                if (list.isEmpty()) {
                    "还没有备份。让作者在 设置 → 备份与恢复 导出一份"
                } else {
                    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                    list.joinToString("\n") { b ->
                        "· " + b.name + "｜" + (b.size / 1024) + " KB｜" + fmt.format(java.util.Date(b.dateAdded))
                    }
                }
            }
        }

        "open_item" -> {
            val e = env
            val kind = args.optString("kind").ifBlank { "chapter" }
            val title = args.optString("title").trim()
            if (e == null) {
                "error: 工具环境没准备好"
            } else {
                when (kind) {
                    "book" -> {
                        val b = findBook(store, title)
                        if (b == null) "没有叫「" + title + "」的书" else {
                            e.openBook(b.id)
                            "已打开《" + b.title + "》"
                        }
                    }
                    "note" -> {
                        val n = findNote(store, title)
                        if (n == null) "没有叫「" + title + "」的笔记" else {
                            e.openNote(n.id)
                            "已打开笔记「" + (n.title.ifBlank { "无标题笔记" }) + "」"
                        }
                    }
                    else -> {
                        val b = if (args.optString("book").isNotBlank()) {
                            findBook(store, args.optString("book")) ?: book
                        } else book
                        val c = findChapter(b, title)
                        if (c == null) {
                            "《" + b.title + "》没有叫「" + title + "」的章节。现有：" +
                                b.chapters.joinToString("、") { it.title }
                        } else {
                            e.openChapter(b.id, c.id)
                            "已打开《" + b.title + "》" + c.title
                        }
                    }
                }
            }
        }

        "export_item" -> {
            val kind = args.optString("kind").ifBlank { "book" }
            val title = args.optString("title").trim()
            val format = args.optString("format")
                .ifBlank { if (kind == "note") "txt" else "epub" }.lowercase()
            if (kind == "note") {
                val n = findNote(store, title)
                when {
                    n == null -> "没有叫「" + title + "」的笔记"
                    format !in listOf("txt", "md", "json") -> "笔记只能导出 txt / md / json"
                    else -> {
                        pending += AiPending(
                            label = "导出笔记「" + (n.title.ifBlank { "无标题笔记" }) + "」" + format.uppercase(),
                            kind = AiPendingKind.Export,
                            noteId = n.id,
                            payload = JSONObject()
                                .put("kind", "note")
                                .put("format", format)
                                .put("name", n.title.ifBlank { "无标题笔记" })
                                .toString(),
                        )
                        "已导出到 下载/字隅"
                    }
                }
            } else {
                val b = findBook(store, title)
                when {
                    b == null -> "没有叫「" + title + "」的书。导出要写全书名"
                    format !in listOf("epub", "txt") -> "书只能导出 epub / txt"
                    else -> {
                        pending += AiPending(
                            label = "导出《" + b.title + "》" + format.uppercase(),
                            kind = AiPendingKind.Export,
                            bookId = b.id,
                            payload = JSONObject()
                                .put("kind", "book")
                                .put("format", format)
                                .put("name", b.title)
                                .toString(),
                        )
                        "已导出到 下载/字隅"
                    }
                }
            }
        }

        "backup_now" -> {
            pending += AiPending(label = "导出 JSON 备份", kind = AiPendingKind.Backup)
            "已写入文件"
        }

        "import_attachment" -> {
            val e = env
            val wanted = args.optString("name").trim()
            val asKind = args.optString("as").ifBlank { "book" }
            if (e == null) {
                "error: 工具环境没准备好"
            } else {
                val a = findAttachment(e.attachments, wanted)
                if (a == null) {
                    "没有叫「" + wanted + "」的附件。" +
                        if (e.attachments.isEmpty()) "这条对话还没有附件"
                        else "现有：" + e.attachments.joinToString("、") { it.name }
                } else {
                    val base = a.name.substringBeforeLast('.', a.name)
                    pending += AiPending(
                        label = if (asKind == "note") "把附件导入成笔记「" + base + "」"
                        else "把附件导入成书《" + base + "》",
                        kind = AiPendingKind.Import,
                        text = asKind,
                        payload = JSONObject()
                            .put("name", a.name)
                            .put("as", asKind)
                            .put("text", a.text)
                            .toString(),
                    )
                    "已导入"
                }
            }
        }

        "set_reminder" -> {
            val n = findNote(store, args.optString("note_title"))
            val wanted = args.optString("todo_text").trim()
            val at = parseLocalTime(args.optString("at"))
            val hit = n?.todos?.firstOrNull {
                !it.done && (it.text == wanted || (wanted.isNotBlank() && it.text.contains(wanted)))
            }
            when {
                n == null -> "没有叫「" + args.optString("note_title") + "」的笔记"
                wanted.isBlank() -> "没说要给哪条待办设提醒"
                hit == null -> "这条笔记里没找到未完成的待办「" + wanted + "」。现有：" +
                    n.todos.filter { !it.done }.take(10).joinToString("、") { it.text }
                at == null -> "时间看不懂，用 2026-11-13 09:00 这种格式"
                at <= System.currentTimeMillis() -> "这个时间已经过去了，换一个"
                else -> {
                    pending += AiPending(
                        label = "给「" + hit.text.take(20) + "」设提醒 " + formatDateTime(at),
                        kind = AiPendingKind.Reminder,
                        noteId = n.id,
                        payload = JSONObject()
                            .put("todoId", hit.id)
                            .put("at", at)
                            .put("text", hit.text)
                            .toString(),
                    )
                    "已设好提醒"
                }
            }
        }

        "set_daily_goal" -> {
            val w = args.optInt("words", -1)
            if (w < 100 || w > 100_000) {
                "每日目标要在 100 到 100000 字之间"
            } else {
                pending += AiPending(
                    label = "每日目标改为 " + w + " 字",
                    kind = AiPendingKind.Goal,
                    payload = JSONObject().put("words", w).toString(),
                )
                "已修改"
            }
        }

        "restore_backup" -> {
            val e = env
            val name = args.optString("name").trim()
            if (e == null) {
                "error: 工具环境没准备好"
            } else {
                val text = BackupOps.readText(e.context, name)
                if (text == null) {
                    "没找到备份「" + name + "」。先用 list_backups 看有哪些"
                } else {
                    val parsed = runCatching { BackupOps.parse(text) }.getOrNull()
                    if (parsed == null) {
                        "这份备份解析不了，没有动当前数据"
                    } else {
                        pending += AiPending(
                            label = "用备份「" + name + "」覆盖全部数据（" + parsed.books.size + " 本书 / " +
                                parsed.notes.size + " 条笔记）",
                            kind = AiPendingKind.Restore,
                            payload = JSONObject().put("name", name).toString(),
                            danger = true,
                        )
                        "已记下这次恢复，等你在下面确认；它会覆盖当前全部数据"
                    }
                }
            }
        }


        "read_story_bible" -> {
            val b = findBook(store, args.optString("book")) ?: book
            val section = args.optString("section", "")
            if (section.isBlank()) AiBible.summary(b) else AiBible.readSection(b, section)
        }

        "read_chapter_deep" -> {
            val b = findBook(store, args.optString("book")) ?: book
            AiBible.deepChapter(b, args.optString("title"))
        }

        "write_story_bible" -> {
            val ctx = env?.context
            if (ctx == null) "当前没有运行环境，写不了故事圣经"
            else {
                val b = findBook(store, args.optString("book")) ?: book
                AiBible.mutate(
                    ctx = ctx,
                    store = store,
                    bookId = b.id,
                    kind = args.optString("kind"),
                    action = args.optString("action", "upsert").ifBlank { "upsert" },
                    args = args,
                    source = "ai",
                )
            }
        }

        "undo_bible_change" -> {
            val ctx = env?.context
            if (ctx == null) "当前没有运行环境"
            else {
                val b = findBook(store, args.optString("book")) ?: book
                AiBible.undoLast(ctx, store, b.id)
            }
        }

        "list_chapter_history" -> {
            val ctx = env?.context
            val b = findBook(store, args.optString("book")) ?: book
            val c = findChapter(b, args.optString("title"))
            when {
                ctx == null -> "当前没有运行环境，读不了历史"
                c == null -> "没有叫「" + args.optString("title") + "」的章节"
                else -> {
                    val vs = ChapterHistory.list(ctx, c.id)
                    if (vs.isEmpty()) "「" + c.title + "」还没有历史版本"
                    else "「" + c.title + "」有 " + vs.size + " 个历史版本（新的在前）：\n" +
                        vs.joinToString("\n") { "· " + stamp(it.ts) + "（" + it.words + " 字）· at=" + it.ts }
                }
            }
        }

        "read_chapter_history" -> {
            val ctx = env?.context
            val b = findBook(store, args.optString("book")) ?: book
            val c = findChapter(b, args.optString("title"))
            val at = args.optString("at").trim().toLongOrNull()
            when {
                ctx == null -> "当前没有运行环境，读不了历史"
                c == null -> "没有叫「" + args.optString("title") + "」的章节"
                at == null -> "at 要填 list_chapter_history 给的时间戳（一串数字）"
                else -> {
                    val body = ChapterHistory.read(ctx, c.id, at)
                    when {
                        body == null -> "「" + c.title + "」没有 " + stamp(at) + " 这一版"
                        body.length <= 6000 -> "「" + c.title + "」" + stamp(at) + " 的版本：\n\n" + body
                        else -> "「" + c.title + "」" + stamp(at) + " 的版本（只给前 6000 字）：\n\n" + body.take(6000)
                    }
                }
            }
        }

        "restore_chapter_history" -> {
            val ctx = env?.context
            val b = findBook(store, args.optString("book")) ?: book
            val c = findChapter(b, args.optString("title"))
            val at = args.optString("at").trim().toLongOrNull()
            when {
                ctx == null -> "当前没有运行环境，改不了历史"
                c == null -> "没有叫「" + args.optString("title") + "」的章节"
                at == null -> "at 要填 list_chapter_history 给的时间戳（一串数字）"
                else -> {
                    val body = ChapterHistory.read(ctx, c.id, at)
                    if (body == null) "「" + c.title + "」没有 " + stamp(at) + " 这一版"
                    else {
                        pending += AiPending(
                            label = "把「" + c.title + "」恢复到 " + stamp(at) + " 的版本",
                            kind = AiPendingKind.ChapterRestore,
                            chapterId = c.id,
                            bookId = b.id,
                            text = at.toString(),
                        )
                        "已排好：把「" + c.title + "」恢复到 " + stamp(at) + " 的版本，等作者确认"
                    }
                }
            }
        }

        else -> "没有这个工具"
        }
        // 询问档：正文改动只是排成补丁，别让模型回「已写入」。
        return if (env?.writeMode == 1 && name in PATCH_TOOLS) {
            "已排成补丁，等作者在回答下面点「接受」；现在还没写进正文。"
        } else {
            out
        }
    }

    private fun stamp(ts: Long): String =
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(ts))

    private fun readChapter(store: AppStore, args: JSONObject, book: Book): String {
        val b = findBook(store, args.optString("book")) ?: book
        val want = args.optString("title")
        val c = findChapter(b, want)
            ?: return "没有叫「" + want + "」的章节。现有：" + b.chapters.joinToString("、") { it.title }
        // A whole chapter in one blob either gets truncated (the old 3000-char
        // dump) or blows the reply budget. Long chapters are read like a book:
        // look at the paragraph index, then ask for the paragraphs needed.
        val paras = c.content.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
        val from = args.optInt("from", 0)
        val count = args.optInt("count", 0)
        return when {
            from > 0 -> {
                if (from > paras.size) {
                    "「" + c.title + "」只有 " + paras.size + " 段，没有第 " + from + " 段"
                } else {
                    val end = (from - 1 + (if (count > 0) count else 1)).coerceAtMost(paras.size)
                    val body = (from..end).joinToString("\n\n") { paras[it - 1] }
                    val head = "「" + c.title + "」第 " + from + "–" + end + " 段（共 " + paras.size + " 段）：\n\n"
                    if (body.length <= READ_MAX) head + body
                    else head + paras[from - 1].take(READ_MAX) +
                        "\n……这一段超过 " + READ_MAX + " 字，先读前半；要看后半把 from 改成同一段、count 不变再读一次"
                }
            }
            c.content.length <= READ_WHOLE -> c.content
            paras.size < 3 -> c.content.take(READ_WHOLE) +
                "\n……这章没有分段，先给前 " + READ_WHOLE + " 字；要找后面的内容用 search_book 定位关键词"
            else -> "「" + c.title + "」共 " + c.content.length + " 字、" + paras.size + " 段。\n" +
                "先看索引，再按段号读原文（read_chapter 带 from / count）：\n" +
                paras.mapIndexed { i, p ->
                    val one = p.replace("\n", " ").trim()
                    (i + 1).toString() + ". " + one.take(30) + if (one.length > 30) "…" else ""
                }.joinToString("\n")
        }
    }

    private fun findBook(store: AppStore, title: String): Book? {
        val q = title.trim()
        if (q.isBlank()) return null
        return store.books.firstOrNull { it.title == q }
            ?: store.books.firstOrNull { q.length >= 2 && it.title.contains(q) }
    }

    private fun findChapter(book: Book, title: String): Chapter? {
        val q = title.trim()
        if (q.isBlank()) return null
        return book.chapters.firstOrNull { it.title == q }
            ?: book.chapters.firstOrNull { q.length >= 2 && it.title.contains(q) }
    }

    private fun findNote(store: AppStore, title: String): Note? {
        val q = title.trim().ifBlank { return null }
        val shown: (Note) -> String = { it.title.ifBlank { "无标题笔记" } }
        return store.notes.firstOrNull { shown(it) == q }
            ?: store.notes.firstOrNull { it.title.isNotBlank() && q.length >= 2 && it.title.contains(q) }
            ?: store.notes.firstOrNull { q == "无标题笔记" && it.title.isBlank() }
    }

    private fun groupsOf(store: AppStore, kind: String): List<Group> =
        if (kind == "book") store.bookGroups else store.noteGroups

    private fun findAttachment(list: List<Attachment>, name: String): Attachment? {
        val q = name.trim()
        if (list.isEmpty()) return null
        if (q.isBlank()) return list.firstOrNull()
        return list.firstOrNull { it.name == q }
            ?: list.firstOrNull { it.name.contains(q, ignoreCase = true) }
            ?: list.firstOrNull { q.contains(it.name.substringBeforeLast('.'), ignoreCase = true) }
    }

    /** "2026-11-13 09:00" in the phone's own zone; null when unparseable or in the past. */
    private fun parseLocalTime(raw: String): Long? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm")
        for (p in patterns) {
            runCatching {
                val t = java.time.LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern(p))
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                return t
            }
        }
        return null
    }

    /** How many times [needle] occurs in [hay] (literal, not regex). */
    private fun countOf(hay: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var at = 0
        var n = 0
        while (true) {
            val i = hay.indexOf(needle, at)
            if (i < 0) break
            n++
            at = i + needle.length
        }
        return n
    }
}
