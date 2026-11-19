package com.moyu.writer

import android.content.Context
import org.json.JSONArray
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
    /** 3.26.31：结构手术——拆章 / 合章 / 移章 / 按大纲建章。 */
    ChapterSplit, ChapterMerge, ChapterMove, ChapterFromOutline,
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

    /** 自由大纲节点的 id 前缀（与 StoryBible 的 FREE_OUTLINE_PREFIX 同一约定）：不绑章，也不能建章。 */
    private const val FREE_OUTLINE_PREFIX = "free_"

    /**
     * 3.26.49：`chapters_from_outline` 碰上「大纲里只有自由节点」时的错误码。
     * 自由节点不绑章，一个都不能建，所以不是「都落地了」。
     * 错误码的集中登记处是 `AiToolError`（AiToolResult.kt），这里沿用同一套小写下划线。
     */
    private const val NO_CHAPTER_NODES = "no_chapter_nodes"

    /**
     * 3.26.29：会改东西的工具。只读档下这些一律不执行——
     * 作者选「只读」就是只要回答，不要任何写盘动作（含圣经、导出、备份）。
     * 3.26.45：名单搬进 [AiToolCatalog]（每条工具自带 write 标志），这里只做别名；
     * 以后新增工具改目录表即可，不会再出现「工具表加了、拦截表忘了」的漂移。
     */
    private val WRITE_TOOLS: Set<String> = AiToolCatalog.WRITE_TOOLS

    /** 询问档下只排补丁、当场不落地的三个工具。 */
    private val PATCH_TOOLS = setOf(
        "append_to_chapter", "replace_in_chapter", "restore_chapter_history",
    )

    /**
     * 3.26.56：`write_story_bible` 认识的 kind / action，与 [AiBible.mutate] 的分支一一对上。
     * 不认识的在调用前就退回——以前 action 是自由文本，拼错（例如 delete 写成 remove）会被
     * 当成 upsert 静默走「新增」，正是「写错的角色/事件清不掉」的一部分成因。
     */
    private val BIBLE_KINDS = setOf("character", "relation", "event", "outline")

    private val BIBLE_ACTIONS = setOf(
        "upsert", "delete",
        "add_beat", "update_beat", "delete_beat", "clear_beats", "delete_node",
    )

    /** 3.26.56：只对 outline 有意义的动作；别的 kind 带上它们一律退回，别再静默走 upsert。 */
    private val OUTLINE_BIBLE_ACTIONS = setOf(
        "add_beat", "update_beat", "delete_beat", "clear_beats", "delete_node",
    )

    /**
     * 3.26.56：删除（以及作者条目的改动）不落库，只进故事页「待同意」。
     * 对模型来说这是「成功排队」而不是错误，回话必须让模型告诉作者去点同意，且不要重复提交。
     */
    private fun queuedBibleReply(reply: String): String =
        reply + "。这次没有直接改或删任何东西：作者在故事页的待同意卡里点「同意」才生效" +
            "（删除同意后也能用 undo_bible_change 撤回）。把这句话告诉作者即可，不要重复提交同一条"

    /**
     * 3.26.45：工具表按组排（读正文 → 写正文 → 故事圣经 → 结构手术 → 搜索 → 其他），
     * 53 条并成 49 条：
     * · read_chapter_deep 并进 read_chapter(deep=true)
     * · search_all 并进 search_book(scope=all)
     * · list_all_notes 并进 list_notes(scope=all)
     * · get_progress 并进 list_books（它本来就是「书 + 最近在读」）
     * · list_chapter_history 并进 read_chapter_history（不带 at 就是列版本）
     * 新增 set_story_time。
     *
     * 归组与 write 标志在 [AiToolCatalog] 里；两边的名字/顺序由 [AiToolCatalog.audit] 核对。
     * 改这张表时必须跑一次结构自检（3.26.23 就因为少一个 `}` 让运行时抛错）：
     * app 启动后看 logcat `MOYU_AI`，或离线 `ConvertFrom-Json` + 必填项是否都在 properties 里。
     */
    val DEFS: String = """
    [
      {"type":"function","function":{"name":"list_books","description":"列出所有书：标题、章节数、字数、最近在读的那一章。要操作别的书先看这里","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"list_chapters","description":"列出某本书的章节标题和字数；不填 book 就是当前书","parameters":{"type":"object","properties":{"book":{"type":"string","description":"书名，不填=当前书"}}}}},
      {"type":"function","function":{"name":"read_chapter","description":"读某一章。不带 from 时：短章（1200 字内）给全文，长章只给段落索引；带 from/count 时按段号读原文，单次最多 1200 字；deep=true 一次读完（正文＋情节点＋本章事件＋出场角色）","parameters":{"type":"object","properties":{"title":{"type":"string","description":"章节标题，例如 第二章"},"book":{"type":"string","description":"书名，不填=当前书"},"from":{"type":"integer","description":"从第几段开始，1 起；只在逐段补读时用"},"count":{"type":"integer","description":"读几段，默认 1"},"deep":{"type":"boolean","description":"true=整章深读：正文＋情节点＋事件＋出场角色；默认 false"}},"required":["title"]}}},
      {"type":"function","function":{"name":"list_notes","description":"列笔记。scope=bound（默认）只列本书绑定到章节的笔记；scope=all 列全部笔记的标题、字数、是否绑定章节（跨书）","parameters":{"type":"object","properties":{"book":{"type":"string","description":"书名，不填=当前书（scope=bound 时生效）"},"scope":{"type":"string","enum":["bound","all"],"description":"bound=本书绑定的笔记；all=全部笔记"}}}}},
      {"type":"function","function":{"name":"read_note","description":"读一条笔记的正文（最多 2400 字）","parameters":{"type":"object","properties":{"title":{"type":"string","description":"笔记标题，或其中几个字"}},"required":["title"]}}},
      {"type":"function","function":{"name":"read_chapter_history","description":"读一章的历史版本：不给 at 就列出所有版本（时间与字数），给 at 读那一版的原文（最多 6000 字）；改坏了先用它找回来","parameters":{"type":"object","properties":{"title":{"type":"string","description":"章节标题"},"at":{"type":"string","description":"版本时间戳（列版本时返回的那串数字）；不填=只列版本"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["title"]}}},
      {"type":"function","function":{"name":"append_to_chapter","description":"在某章末尾追加一段；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"章节标题"},"text":{"type":"string"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["title","text"]}}},
      {"type":"function","function":{"name":"replace_in_chapter","description":"改某一章里已经写好的一小段：给出原文片段和改后片段，章节里必须能原样找到这段原文；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"章节标题"},"find":{"type":"string","description":"要替换掉的原文，必须原样抄，标点和换行都不能改"},"replace":{"type":"string","description":"改后的文字"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["title","find","replace"]}}},
      {"type":"function","function":{"name":"restore_chapter_history","description":"把某一章恢复成某个历史版本；要等作者确认（询问档出确认卡，自动档直接恢复）。版本时间戳用 read_chapter_history 取","parameters":{"type":"object","properties":{"title":{"type":"string"},"at":{"type":"string","description":"时间戳"},"book":{"type":"string"}},"required":["title","at"]}}},
      {"type":"function","function":{"name":"read_story_bible","description":"读故事圣经（AI 的长期记忆）：角色/关系/事件/大纲；不传 section 给全貌","parameters":{"type":"object","properties":{"book":{"type":"string","description":"书名，不填=当前书"},"section":{"type":"string","enum":["characters","relations","events","outline"],"description":"只看一类"}}}}},
      {"type":"function","function":{"name":"write_story_bible","description":"写故事圣经（留一条可撤销记录）。kind=character/relation/event/outline；action=upsert（默认，新增或改）/ delete（删一整条，按 id 或精确名称定位）。删除是危险操作：不会直接写进圣经，只提交到故事页的待同意卡，需要作者确认（点「同意」）后才删（删了也能用 undo_bible_change 撤回）；作者设定里的条目 AI 也不能自己删。outline 另支持 add_beat/update_beat/delete_beat/clear_beats/delete_node","parameters":{"type":"object","properties":{"book":{"type":"string"},"kind":{"type":"string","enum":["character","relation","event","outline"]},"action":{"type":"string","enum":["upsert","delete","add_beat","update_beat","delete_beat","clear_beats","delete_node"],"description":"默认 upsert。delete=删一整条（角色/关系/事件/大纲节点）；删除需要作者在故事页确认，工具本身不落库。add_beat/update_beat/delete_beat/clear_beats/delete_node 只用于 outline，delete_node 等于 delete"},"id":{"type":"string","description":"条目 id，可选；删除时一般给名称就够（角色名 / from+to / 事件标题 / 章名或大纲节点名）"},"name":{"type":"string","description":"角色名（删角色时按它精确匹配）"},"role":{"type":"string"},"note":{"type":"string"},"from":{"type":"string","description":"关系起点角色名"},"to":{"type":"string","description":"关系终点角色名"},"relation":{"type":"string","description":"关系类型：内置类型名（亲人/恋人/朋友/同盟/对手/仇敌/师徒/其他）或自定义名称；「亲近/对立」是档位不是类型名"},"kindTier":{"type":"integer","enum":[0,1,2],"description":"0 亲近 / 1 对立 / 2 其他，可选，不给则由类型派生"},"whenText":{"type":"string","description":"事件故事时间，自由文本；同名事件有多条时用它指定删哪一条"},"title":{"type":"string","description":"事件标题（删同名事件时配 whenText）/ 大纲节点标题"},"chapters":{"type":"array","items":{"type":"string"},"description":"事件所属章节标题，可多个"},"characters":{"type":"array","items":{"type":"string"},"description":"事件涉及角色名"},"chapter":{"type":"string","description":"大纲所在章节标题（删章级大纲节点可用；自由节点给 id 或 title）"},"text":{"type":"string","description":"情节点文字 / update_beat 的原文字"},"to_text":{"type":"string","description":"update_beat 改后的文字"},"beats":{"type":"array","items":{"type":"string"},"description":"整章情节点（替换）"}},"required":["kind"]}}},
      {"type":"function","function":{"name":"set_story_time","description":"把某一章的故事内时间规范化后写进故事圣经的时间线（写的是以章名为标题的那条事件）；只接受 YYYY-MM-DD、M月D日、N年前|后、第N天，其它说法会被拒","parameters":{"type":"object","properties":{"chapterId":{"type":"string","description":"章节标题或章节 id"},"timeText":{"type":"string","description":"故事内时间，例如 11月7日 / 三年前 / 第12天"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["chapterId","timeText"]}}},
      {"type":"function","function":{"name":"undo_bible_change","description":"撤销最近一次对故事圣经的改动（含章节保存后的自动整理，也含作者在故事页同意过的删除）","parameters":{"type":"object","properties":{"book":{"type":"string"}}}}},
      {"type":"function","function":{"name":"create_chapter","description":"新建一章：不给 afterChapterId 就接在全书最后（要等作者确认）；给了就当场插在那一章后面并返回新章 id。标题对上还没落地的大纲节点会自动挂上","parameters":{"type":"object","properties":{"title":{"type":"string","description":"新章标题"},"book":{"type":"string","description":"书名，不填=当前书"},"afterChapterId":{"type":"string","description":"插到哪一章后面：章节标题或 id；不填=全书最后"}},"required":["title"]}}},
      {"type":"function","function":{"name":"rename_chapter","description":"改某一章的标题；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"现在的标题"},"new_title":{"type":"string"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["title","new_title"]}}},
      {"type":"function","function":{"name":"move_chapter","description":"把一章挪到目录里的第 N 位（1 起）","parameters":{"type":"object","properties":{"title":{"type":"string"},"to":{"type":"integer","description":"目标位次，1 起"},"book":{"type":"string"}},"required":["title","to"]}}},
      {"type":"function","function":{"name":"split_chapter","description":"把一章从断点拆成两章：前半保留原章，后半新建一章。断点给 before（一段原文）或 at（第几段，1 起）","parameters":{"type":"object","properties":{"title":{"type":"string"},"before":{"type":"string","description":"在这一句原文前面断开"},"at":{"type":"integer","description":"在第几段前面断开，1 起"},"new_title":{"type":"string","description":"后半的新章名，不填=原名（下）"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["title"]}}},
      {"type":"function","function":{"name":"merge_chapters","description":"把相邻的两章合成一章，保留前一章的章名与 id；笔记、事件、大纲会跟着指到前一章","parameters":{"type":"object","properties":{"first":{"type":"string"},"second":{"type":"string"},"book":{"type":"string"}},"required":["first","second"]}}},
      {"type":"function","function":{"name":"chapters_from_outline","description":"按故事圣经的大纲，把还没落地的章级节点各建一章（标题用节点标题，正文空）。只处理章级节点，free_ 开头的自由节点不建章","parameters":{"type":"object","properties":{"book":{"type":"string"}}}}},
      {"type":"function","function":{"name":"delete_chapter","description":"删除一章及其正文；不可恢复，要等作者二次确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"章节标题，必须写全"},"book":{"type":"string","description":"书名，不填=当前书"}},"required":["title"]}}},
      {"type":"function","function":{"name":"search_book","description":"搜一个关键词（人名、物件、伏笔、地点），返回命中的章节和前后文；scope=all 连所有书和笔记一起搜，跨书找伏笔用这个","parameters":{"type":"object","properties":{"keyword":{"type":"string","description":"要搜的词，两到六个字最好"},"book":{"type":"string","description":"书名，不填=当前书（scope=book 时生效）"},"scope":{"type":"string","enum":["book","all"],"description":"book=只搜当前这本书；all=所有书和笔记"},"limit":{"type":"integer","description":"最多几处，默认 12"}},"required":["keyword"]}}},
      {"type":"function","function":{"name":"create_book","description":"新建一本书；要等作者确认才写入","parameters":{"type":"object","properties":{"title":{"type":"string"},"author":{"type":"string"},"genre":{"type":"string"},"synopsis":{"type":"string"}},"required":["title"]}}},
      {"type":"function","function":{"name":"edit_book","description":"改一本书的书名/作者/类型/简介；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"现有书名"},"new_title":{"type":"string"},"author":{"type":"string"},"genre":{"type":"string"},"synopsis":{"type":"string"}},"required":["title"]}}},
      {"type":"function","function":{"name":"delete_book","description":"删除一本书及其全部章节；不可恢复，要等作者二次确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"书名，必须写全"}},"required":["title"]}}},
      {"type":"function","function":{"name":"open_item","description":"直接打开一本书、一章或一条笔记；不改内容","parameters":{"type":"object","properties":{"kind":{"type":"string","enum":["book","chapter","note"]},"title":{"type":"string"},"book":{"type":"string","description":"章节所属书名，不填=当前书"}},"required":["kind","title"]}}},
      {"type":"function","function":{"name":"create_note","description":"建一条笔记，可指定分组名；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string"},"body":{"type":"string"},"group":{"type":"string","description":"笔记分组名，可不填"}},"required":["title","body"]}}},
      {"type":"function","function":{"name":"edit_note","description":"改一条笔记的标题/正文/置顶；要等作者确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"现有标题"},"new_title":{"type":"string"},"body":{"type":"string"},"pinned":{"type":"boolean"}},"required":["title"]}}},
      {"type":"function","function":{"name":"delete_note","description":"删除一条笔记（正文和待办一起删）；不可恢复，要等作者二次确认","parameters":{"type":"object","properties":{"title":{"type":"string","description":"笔记标题，必须写全"}},"required":["title"]}}},
      {"type":"function","function":{"name":"bind_note","description":"把一条笔记绑定到某一章，或解绑；要等作者确认","parameters":{"type":"object","properties":{"note_title":{"type":"string"},"chapter_title":{"type":"string"},"book":{"type":"string","description":"书名，不填=当前书"},"on":{"type":"boolean","description":"true=绑定，false=解绑，默认 true"}},"required":["note_title","chapter_title"]}}},
      {"type":"function","function":{"name":"add_todo","description":"给一条笔记加一条待办；要等作者确认","parameters":{"type":"object","properties":{"note_title":{"type":"string"},"text":{"type":"string"}},"required":["note_title","text"]}}},
      {"type":"function","function":{"name":"complete_todo","description":"把一条笔记里的某项待办标为完成；要等作者确认","parameters":{"type":"object","properties":{"note_title":{"type":"string"},"text":{"type":"string","description":"待办文字，或其中几个字"}},"required":["note_title","text"]}}},
      {"type":"function","function":{"name":"set_reminder","description":"给某条笔记里的待办设提醒；要等作者确认","parameters":{"type":"object","properties":{"note_title":{"type":"string"},"todo_text":{"type":"string"},"at":{"type":"string","description":"本地时间，如 2026-11-13 09:00"}},"required":["note_title","todo_text","at"]}}},
      {"type":"function","function":{"name":"list_groups","description":"列出书籍分组和笔记分组","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"create_group","description":"新建书籍分组或笔记分组；要等作者确认","parameters":{"type":"object","properties":{"kind":{"type":"string","enum":["book","note"]},"name":{"type":"string"}},"required":["kind","name"]}}},
      {"type":"function","function":{"name":"rename_group","description":"改分组名；要等作者确认","parameters":{"type":"object","properties":{"kind":{"type":"string","enum":["book","note"]},"name":{"type":"string"},"new_name":{"type":"string"}},"required":["kind","name","new_name"]}}},
      {"type":"function","function":{"name":"delete_group","description":"删除一个分组（里面的书/笔记回到未分组，不会删内容）；要等作者确认","parameters":{"type":"object","properties":{"kind":{"type":"string","enum":["book","note"]},"name":{"type":"string"}},"required":["kind","name"]}}},
      {"type":"function","function":{"name":"move_to_group","description":"把一本书或一条笔记移到某个分组；要等作者确认","parameters":{"type":"object","properties":{"kind":{"type":"string","enum":["book","note"]},"name":{"type":"string","description":"目标分组名"},"item_title":{"type":"string","description":"书名或笔记标题"}},"required":["kind","name","item_title"]}}},
      {"type":"function","function":{"name":"get_settings","description":"读当前排版与外观：主题、字体、字号、行距、页边距、纸张、分节样式、每日目标","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"set_style","description":"改排版或外观：只传要改的项。font 用 sans/serif/kai；theme 用 paper/eye/ink/sand；paper_style 用 plain/solid/lines/grid；section_style 用 line/blank/star/ellipsis。要等作者确认","parameters":{"type":"object","properties":{"font_size":{"type":"number","description":"0.82 到 1.3，1 是默认"},"line_height":{"type":"number","description":"1.3 到 2.4"},"page_margin":{"type":"number","description":"12 到 40 dp"},"font":{"type":"string"},"theme":{"type":"string"},"paper_style":{"type":"string"},"section_style":{"type":"string"}},"required":[]}}},
      {"type":"function","function":{"name":"get_stats","description":"读统计：总字数、今日字数、连续天数、书数、笔记数","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"set_daily_goal","description":"改每日写作目标（100–100000 字）；要等作者确认","parameters":{"type":"object","properties":{"words":{"type":"integer"}},"required":["words"]}}},
      {"type":"function","function":{"name":"export_item","description":"把一本书导出 EPUB/TXT，或把一条笔记导出 TXT/MD/JSON；要等作者确认","parameters":{"type":"object","properties":{"kind":{"type":"string","enum":["book","note"]},"title":{"type":"string"},"format":{"type":"string","description":"book: epub/txt；note: txt/md/json"}},"required":["kind","title"]}}},
      {"type":"function","function":{"name":"backup_now","description":"把全部数据导出成一份 JSON 备份；要等作者确认","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"list_backups","description":"列出 App 自己导出的最近备份：时间、大小","parameters":{"type":"object","properties":{}}}},
      {"type":"function","function":{"name":"restore_backup","description":"用某份备份覆盖当前全部数据；不可撤销。作者明确同意后才会执行，执行前还会再确认一次","parameters":{"type":"object","properties":{"name":{"type":"string","description":"list_backups 返回的文件名"}},"required":["name"]}}},
      {"type":"function","function":{"name":"read_attachment","description":"读作者在这条对话里传的附件；一次最多 6000 字，用 from 翻页","parameters":{"type":"object","properties":{"name":{"type":"string","description":"附件名，或其中几个字"},"from":{"type":"integer","description":"从第几页开始，1 起"}},"required":["name"]}}},
      {"type":"function","function":{"name":"import_attachment","description":"把附件导入成一本新书或一条新笔记；要等作者确认","parameters":{"type":"object","properties":{"name":{"type":"string"},"as":{"type":"string","enum":["book","note"]}},"required":["name"]}}},
      {"type":"function","function":{"name":"web_search","description":"联网搜索，返回标题、网址和摘要；要作者先打开联网","parameters":{"type":"object","properties":{"query":{"type":"string"},"count":{"type":"integer","description":"几条，默认 5"}},"required":["query"]}}},
      {"type":"function","function":{"name":"web_fetch","description":"抓一个网页的正文（Markdown），最多 6000 字","parameters":{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}}}
    ]
    """.trimIndent()

    /** Short Chinese name for the per-step trace under an answer. */
    fun label(name: String): String = when (name) {
        // 读正文
        "list_books" -> "列书"
        "list_chapters" -> "列章"
        "read_chapter" -> "读章"
        "list_notes" -> "列笔记"
        "read_note" -> "读笔记"
        "read_chapter_history" -> "读历史"
        // 写正文
        "append_to_chapter" -> "追加正文"
        "replace_in_chapter" -> "替换正文"
        "restore_chapter_history" -> "恢复历史"
        // 故事圣经
        "read_story_bible" -> "读圣经"
        "write_story_bible" -> "改圣经"
        "set_story_time" -> "写时间"
        "undo_bible_change" -> "撤销圣经"
        // 结构手术
        "create_chapter" -> "新建章"
        "rename_chapter" -> "改章名"
        "move_chapter" -> "移章"
        "split_chapter" -> "拆章"
        "merge_chapters" -> "合章"
        "chapters_from_outline" -> "建章"
        "delete_chapter" -> "删章"
        // 搜索
        "search_book" -> "搜书"
        // 其他
        "create_book" -> "新建书"
        "edit_book" -> "改书"
        "delete_book" -> "删书"
        "open_item" -> "打开"
        "create_note" -> "新建笔记"
        "edit_note" -> "改笔记"
        "delete_note" -> "删笔记"
        "bind_note" -> "绑定笔记"
        "add_todo" -> "加待办"
        "complete_todo" -> "完成待办"
        "set_reminder" -> "设提醒"
        "list_groups" -> "列分组"
        "create_group" -> "新建分组"
        "rename_group" -> "改分组"
        "delete_group" -> "删分组"
        "move_to_group" -> "移动分组"
        "get_settings" -> "读设置"
        "set_style" -> "改排版"
        "get_stats" -> "读统计"
        "set_daily_goal" -> "改目标"
        "export_item" -> "导出"
        "backup_now" -> "备份"
        "list_backups" -> "列备份"
        "restore_backup" -> "恢复备份"
        "read_attachment" -> "读附件"
        "import_attachment" -> "导入附件"
        "web_search" -> "联网搜索"
        "web_fetch" -> "抓网页"
        // 已下线的旧工具名：模型偶尔还会调，trace 里仍给个人话名字
        "save_memory" -> "存记忆"
        "delete_memory" -> "删记忆"
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

    /**
     * 3.26.45：所有工具的唯一入口，返回 [AiToolResult] 信封（不再各回一句中文）。
     *
     * 顺序：① 工具存在 → ② 只读档能不能写 → ③ 必填参数（按工具表 required）→
     * ④ 目标书存在性 → ⑤ 分派。工具内部只负责给出 ok / 失败码；任何异常由最外层
     * 接住变成 internal_error，不再往 AiTab 抛。
     */
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
        if (AiToolCatalog.byName[name] == null) {
            return bad(
                AiToolError.UNKNOWN_TOOL,
                "没有「" + name + "」这个工具。工具按组排：读正文 list_books / list_chapters / read_chapter / " +
                    "list_notes / read_note / read_chapter_history；搜索 search_book；故事圣经 read_story_bible；" +
                    "写正文 append_to_chapter / replace_in_chapter；结构 create_chapter / move_chapter / " +
                    "split_chapter / merge_chapters / chapters_from_outline。",
            )
        }
        if (env?.writeMode == 0 && name in WRITE_TOOLS) {
            return bad(
                AiToolError.READ_ONLY,
                "现在是只读档：只回答，不改稿。请直接用文字告诉作者要怎么改。",
            )
        }
        val spec = defSpecs[name]
        if (spec != null) {
            val missing = missingRequiredParam(name, args, spec.required)
            if (missing != null) {
                return bad(
                    AiToolError.MISSING_PARAM,
                    "「" + name + "」缺必填参数 " + missing + "（必填：" + spec.required.joinToString("、") +
                        "）。补上再调一次，别空着参数硬调。",
                )
            }
            val bookArg = args.optString("book").trim()
            if (spec.usesBook && bookArg.isNotBlank() && findBook(store, bookArg) == null) {
                return bad(
                    AiToolError.BOOK_NOT_FOUND,
                    "没有叫「" + bookArg + "」的书。现有：" +
                        (if (store.books.isEmpty()) "还没有书" else store.books.joinToString("、") { "《" + it.title + "》" }),
                )
            }
        }
        val reply = try {
            when (name) {

                // ───────────────────────── 读正文 ─────────────────────────
                "list_books" -> {
                    val sb = StringBuilder()
                    val lastBook = store.book(store.data.lastBookId)
                    val lastChapter = lastBook?.chapter(store.data.lastChapterId)
                    if (lastBook == null) {
                        sb.append("还没有在读的书")
                    } else {
                        sb.append("最近在读：《").append(lastBook.title).append('》')
                        if (lastChapter != null) sb.append("「").append(lastChapter.title).append('」')
                    }
                    if (store.books.isEmpty()) {
                        sb.append("\n还没有书")
                    } else {
                        sb.append("\n共 ").append(store.books.size).append(" 本：")
                        for (b in store.books) {
                            sb.append("\n· 《").append(b.title).append('》')
                                .append(b.chapters.size).append(" 章 · ").append(b.words).append(" 字 · 最近写：")
                                .append(b.lastChapter()?.title ?: "还没写")
                                .append(if (b.id == book.id) "（当前）" else "")
                        }
                    }
                    ok(sb.toString())
                }

                "list_chapters" -> {
                    val b = pickBook(store, args, book)
                    when {
                        b.chapters.isEmpty() -> ok("《" + b.title + "》还没有章节")
                        else -> {
                            val loose = looseChapterNodes(b).size
                            val hint = if (loose > 0) {
                                "\n（大纲里还有 " + loose + " 个节点没落地；要一次建完用 chapters_from_outline，" +
                                    "单建一章用 create_chapter）"
                            } else {
                                ""
                            }
                            ok(
                                "《" + b.title + "》" + b.chapters.size + " 章：\n" +
                                    b.chapters.joinToString("\n") { c -> "· " + c.title + "（" + countWords(c.content) + " 字）" } +
                                    hint,
                            )
                        }
                    }
                }

                "read_chapter" -> {
                    val b = pickBook(store, args, book)
                    val want = args.optString("title").trim()
                    val c = findChapter(b, want)
                    when {
                        c == null -> chapterMissing(b, want)
                        args.optBoolean("deep", false) -> ok(AiBible.deepChapter(b, c.title))
                        else -> ok(readChapter(store, args, b))
                    }
                }

                "list_notes" -> {
                    val b = pickBook(store, args, book)
                    val scopeAll = args.optString("scope").ifBlank { "bound" } == "all"
                    when {
                        notes.isEmpty() -> ok("还没有笔记")
                        scopeAll -> ok(
                            notes.joinToString("\n") { n ->
                                val todos = n.todos.count { !it.done }
                                "· " + (n.title.ifBlank { "无标题笔记" }) + "（" + n.words + " 字" +
                                    (if (n.chapterIds.isEmpty()) "，未绑定章节" else "，已绑定") +
                                    (if (todos > 0) "，$todos 项待办未完成" else "") + "）"
                            },
                        )
                        else -> {
                            val mine = b.chapters.map { it.id }.toSet()
                            val visible = notes.filter { n -> n.chapterIds.any { it in mine } }
                            if (visible.isEmpty()) {
                                ok("《" + b.title + "》还没有绑定笔记（散笔记不算；要看全部用 scope=all，或先 bind_note 绑到某一章）")
                            } else {
                                ok(
                                    visible.take(30).joinToString("\n") { n ->
                                        val todos = n.todos.count { !it.done }
                                        "· " + (n.title.ifBlank { "无标题笔记" }) +
                                            (if (todos > 0) "（" + todos + " 项待办未完成）" else "")
                                    },
                                )
                            }
                        }
                    }
                }

                "read_note" -> {
                    val q = args.optString("title").trim()
                    val n = findNote(store, q)
                    if (n == null) {
                        noteMissing(store, q)
                    } else {
                        val body = n.content.trim()
                        ok(
                            "《" + (n.title.ifBlank { "无标题笔记" }) + "》" +
                                if (body.isEmpty()) {
                                    "（正文为空）"
                                } else {
                                    "\n" + body.take(READ_NOTE_MAX) +
                                        (if (body.length > READ_NOTE_MAX) "\n……这条超过 " + READ_NOTE_MAX + " 字，只给前半" else "")
                                },
                        )
                    }
                }

                "read_chapter_history" -> {
                    val ctx = env?.context
                    val b = pickBook(store, args, book)
                    val want = args.optString("title").trim()
                    val c = findChapter(b, want)
                    val atRaw = args.optString("at").trim()
                    val at = atRaw.toLongOrNull()
                    when {
                        ctx == null -> bad(AiToolError.NO_RUNTIME, "当前没有运行环境，读不了历史")
                        c == null -> chapterMissing(b, want)
                        atRaw.isNotEmpty() && at == null -> bad(
                            AiToolError.INVALID_ARG,
                            "at 要填列版本时给出的时间戳（一串数字）；不填 at 就只列版本",
                        )
                        at == null -> {
                            val versions = ChapterHistory.list(ctx, c.id)
                            if (versions.isEmpty()) {
                                ok("「" + c.title + "」还没有历史版本")
                            } else {
                                ok(
                                    "「" + c.title + "」有 " + versions.size + " 个历史版本（新的在前）：\n" +
                                        versions.joinToString("\n") { "· " + stamp(it.ts) + "（" + it.words + " 字）· at=" + it.ts },
                                )
                            }
                        }
                        else -> {
                            val body = ChapterHistory.read(ctx, c.id, at)
                            if (body == null) {
                                bad(
                                    AiToolError.HISTORY_NOT_FOUND,
                                    "「" + c.title + "」没有 " + stamp(at) + " 这一版。先不带 at 调一次看有哪些版本",
                                )
                            } else {
                                ok(
                                    "「" + c.title + "」" + stamp(at) + " 的版本" +
                                        (if (body.length > 6000) "（只给前 6000 字）：" else "：") +
                                        "\n\n" + body.take(6000),
                                )
                            }
                        }
                    }
                }

                // ───────────────────────── 写正文 ─────────────────────────
                "append_to_chapter" -> {
                    val b = pickBook(store, args, book)
                    val want = args.optString("title").trim()
                    val text = args.optString("text")
                    val c = findChapter(b, want)
                    if (c == null) {
                        chapterMissing(b, want)
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
                        ok("已在「" + c.title + "」末尾写入 " + countWords(fitted) + " 字（排版按这一章现有的段落与缩进）")
                    }
                }

                "replace_in_chapter" -> {
                    val b = pickBook(store, args, book)
                    val want = args.optString("title").trim()
                    val find = args.optString("find")
                    val repl = args.optString("replace")
                    val c = findChapter(b, want)
                    when {
                        c == null -> chapterMissing(b, want)
                        find.isBlank() -> bad(AiToolError.MISSING_PARAM, "没给要改的原文（find）")
                        else -> {
                            val n = countOf(c.content, find)
                            when {
                                n == 0 -> bad(
                                    AiToolError.STATE_CONFLICT,
                                    "「" + c.title + "」里没有这段原文。把它原样抄一遍（标点、换行都要一样），或者换更短、更有辨识度的一段",
                                )
                                n > 1 -> bad(
                                    AiToolError.STATE_CONFLICT,
                                    "这段在「" + c.title + "」里出现了 " + n + " 次，给更长的上下文，只改一处",
                                )
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
                                    ok("已改「" + c.title + "」")
                                }
                            }
                        }
                    }
                }

                "restore_chapter_history" -> {
                    val ctx = env?.context
                    val b = pickBook(store, args, book)
                    val want = args.optString("title").trim()
                    val c = findChapter(b, want)
                    val at = args.optString("at").trim().toLongOrNull()
                    when {
                        ctx == null -> bad(AiToolError.NO_RUNTIME, "当前没有运行环境，改不了历史")
                        c == null -> chapterMissing(b, want)
                        at == null -> bad(AiToolError.INVALID_ARG, "at 要填 read_chapter_history 给出的时间戳（一串数字）")
                        else -> {
                            val body = ChapterHistory.read(ctx, c.id, at)
                            if (body == null) {
                                bad(AiToolError.HISTORY_NOT_FOUND, "「" + c.title + "」没有 " + stamp(at) + " 这一版")
                            } else {
                                pending += AiPending(
                                    label = "把「" + c.title + "」恢复到 " + stamp(at) + " 的版本",
                                    kind = AiPendingKind.ChapterRestore,
                                    chapterId = c.id,
                                    bookId = b.id,
                                    text = at.toString(),
                                )
                                ok("已排好：把「" + c.title + "」恢复到 " + stamp(at) + " 的版本，等作者确认")
                            }
                        }
                    }
                }

                // ───────────────────────── 故事圣经 ─────────────────────────
                "read_story_bible" -> {
                    val b = pickBook(store, args, book)
                    val section = args.optString("section").trim()
                    if (section.isBlank()) ok(AiBible.summary(b)) else ok(AiBible.readSection(b, section))
                }

                "write_story_bible" -> {
                    val ctx = env?.context
                    val b = pickBook(store, args, book)
                    val kind = args.optString("kind").trim()
                    val action = args.optString("action").trim().ifBlank { "upsert" }
                    when {
                        ctx == null -> bad(AiToolError.NO_RUNTIME, "当前没有运行环境，写不了故事圣经")
                        kind !in BIBLE_KINDS -> bad(
                            AiToolError.INVALID_ARG,
                            "kind 只能是 character / relation / event / outline",
                        )
                        action !in BIBLE_ACTIONS -> bad(
                            AiToolError.INVALID_ARG,
                            "action 只能是 upsert / delete（outline 另支持 add_beat / update_beat / delete_beat / clear_beats / delete_node）",
                        )
                        action in OUTLINE_BIBLE_ACTIONS && kind != "outline" -> bad(
                            AiToolError.INVALID_ARG,
                            "action=$action 只能用于 outline；删角色 / 关系 / 事件用 action=delete",
                        )
                        else -> {
                            val reply = AiBible.mutate(
                                ctx = ctx,
                                store = store,
                                bookId = b.id,
                                kind = kind,
                                action = action,
                                args = args,
                                source = "ai",
                            )
                            when {
                                reply.startsWith("已写入") || reply == "没有变化" -> ok(reply)
                                // 3.26.56：删除 / 作者条目改动只排队，等作者在故事页点同意。
                                reply.startsWith("已提交待同意") -> ok(queuedBibleReply(reply))
                                else -> bad(AiToolError.BIBLE_REJECTED, reply)
                            }
                        }
                    }
                }

                "set_story_time" -> {
                    val ctx = env?.context
                    val b = pickBook(store, args, book)
                    val want = args.optString("chapterId").trim()
                    val raw = args.optString("timeText").trim()
                    val c = b.chapters.firstOrNull { it.id == want } ?: findChapter(b, want)
                    val time = AiStoryTime.normalize(raw)
                    when {
                        ctx == null -> bad(AiToolError.NO_RUNTIME, "当前没有运行环境，写不了故事圣经")
                        c == null -> chapterMissing(b, want)
                        time == null -> bad(
                            AiToolError.INVALID_ARG,
                            "「" + raw + "」写不进时间线：" + AiStoryTime.FORMS +
                                "。原文只有「当天夜里」「傍晚」这种说法就先别写，宁可留空",
                        )
                        else -> {
                            // 时间线的主轴是事件，所以「这一章的时间」落成一条以章名为标题、
                            // 只挂这一章的事件；第二次调用按 id 更新同一条，不会堆出重复事件。
                            val anchor = b.bible.events.firstOrNull { e ->
                                c.id in e.chapterIds && e.title.trim() == c.title.trim()
                            }
                            val payload = JSONObject().apply {
                                if (anchor != null) put("id", anchor.id)
                                put("title", c.title)
                                put("whenText", time)
                                put("chapterIds", org.json.JSONArray().put(c.id))
                            }
                            val reply = AiBible.mutate(
                                ctx = ctx,
                                store = store,
                                bookId = b.id,
                                kind = "event",
                                action = "upsert",
                                args = payload,
                                source = "ai",
                            )
                            if (reply.startsWith("已写入") || reply == "没有变化") {
                                ok(
                                    "「" + c.title + "」的故事时间定为 " + time +
                                        "（时间线上是一条以章名为标题的事件；这一章其它事件的时间不受影响）",
                                )
                            } else {
                                bad(AiToolError.BIBLE_REJECTED, reply)
                            }
                        }
                    }
                }

                "undo_bible_change" -> {
                    val ctx = env?.context
                    val b = pickBook(store, args, book)
                    if (ctx == null) {
                        bad(AiToolError.NO_RUNTIME, "当前没有运行环境")
                    } else {
                        val reply = AiBible.undoLast(ctx, store, b.id)
                        if (reply.startsWith("已撤销") || reply == "没有可撤销的改动") ok(reply)
                        else bad(AiToolError.STATE_CONFLICT, reply)
                    }
                }

                // ───────────────────────── 结构手术 ─────────────────────────
                "create_chapter" -> {
                    val b = pickBook(store, args, book)
                    val title = args.optString("title").trim()
                    val anchorRaw = args.optString("afterChapterId").trim()
                    val same = b.chapters.firstOrNull { it.title == title }
                    when {
                        title.isBlank() -> bad(AiToolError.MISSING_PARAM, "新章标题不能为空")
                        same != null -> bad(
                            AiToolError.STATE_CONFLICT,
                            "《" + b.title + "》已经有「" + title + "」这一章，没有重复建。要写它就直接用 append_to_chapter / replace_in_chapter",
                        )
                        anchorRaw.isNotBlank() -> {
                            val anchor = b.chapters.firstOrNull { it.id == anchorRaw } ?: findChapter(b, anchorRaw)
                            if (anchor == null) {
                                bad(
                                    AiToolError.CHAPTER_NOT_FOUND,
                                    "《" + b.title + "》里没有「" + anchorRaw + "」这一章，没法插在它后面。现有：" + chapterNames(b),
                                )
                            } else {
                                val chapter = Chapter(title = title)
                                val at = insertChapterAfter(store, b.id, anchor.id, chapter)
                                if (at < 0) {
                                    bad(
                                        AiToolError.STATE_CONFLICT,
                                        "「" + anchor.title + "」刚被改动过，没插进去；先 list_chapters 确认目录再试",
                                    )
                                } else {
                                    val linked = linkOutlineNode(store, b, chapter.id, title)
                                    ok(
                                        "已在《" + b.title + "》「" + anchor.title + "」后面新建「" + title +
                                            "」（第 " + (at + 2) + " 章" + (if (linked) "，挂上了同名的大纲节点" else "") +
                                            "）。要写正文就用 append_to_chapter",
                                    )
                                }
                            }
                        }
                        else -> {
                            val loose = looseChapterNodes(b).size
                            pending += AiPending(
                                label = "在《" + b.title + "》新建「" + title + "」",
                                kind = AiPendingKind.Chapter,
                                bookId = b.id,
                                bookTitle = b.title,
                                chapterTitle = title,
                            )
                            ok(
                                "已排好：在《" + b.title + "》末尾新建「" + title + "」，等作者确认" +
                                    (if (loose > 0) "（大纲里还有 " + loose + " 个节点没落地，要按大纲建章用 chapters_from_outline）" else ""),
                            )
                        }
                    }
                }

                "rename_chapter" -> {
                    val b = pickBook(store, args, book)
                    val want = args.optString("title").trim()
                    val newTitle = args.optString("new_title").trim()
                    val c = findChapter(b, want)
                    when {
                        c == null -> chapterMissing(b, want)
                        newTitle.isBlank() -> bad(AiToolError.MISSING_PARAM, "新标题不能为空")
                        else -> {
                            pending += AiPending(
                                label = "改章名：「" + c.title + "」→「" + newTitle + "」",
                                kind = AiPendingKind.ChapterEdit,
                                bookId = b.id,
                                chapterId = c.id,
                                text = newTitle,
                            )
                            ok("已修改")
                        }
                    }
                }

                "move_chapter" -> {
                    val b = pickBook(store, args, book)
                    val want = args.optString("title").trim()
                    val to = args.optInt("to", 0)
                    val c = findChapter(b, want)
                    when {
                        c == null -> chapterMissing(b, want)
                        to <= 0 -> bad(AiToolError.INVALID_ARG, "to 是目标位次，1 起")
                        to > b.chapters.size -> bad(
                            AiToolError.INVALID_ARG,
                            "这本书只有 " + b.chapters.size + " 章，挪不到第 " + to + " 位",
                        )
                        else -> {
                            pending += AiPending(
                                label = "把「" + c.title + "」挪到第 " + to + " 位",
                                kind = AiPendingKind.ChapterMove,
                                chapterId = c.id,
                                bookId = b.id,
                                text = to.toString(),
                            )
                            ok("已排好：把「" + c.title + "」挪到第 " + to + " 位")
                        }
                    }
                }

                "split_chapter" -> {
                    val b = pickBook(store, args, book)
                    val want = args.optString("title").trim()
                    val c = findChapter(b, want)
                    val before = args.optString("before").trim()
                    val at = args.optInt("at", 0)
                    when {
                        c == null -> chapterMissing(b, want)
                        before.isBlank() && at <= 0 -> bad(
                            AiToolError.MISSING_PARAM,
                            "要给出断点：before=一段原文，或 at=第几段（1 起）",
                        )
                        else -> {
                            pending += AiPending(
                                label = "把「" + c.title + "」拆成两章",
                                kind = AiPendingKind.ChapterSplit,
                                chapterId = c.id,
                                bookId = b.id,
                                text = args.optString("new_title").trim(),
                                payload = JSONObject().put("before", before).put("at", at).toString(),
                                danger = true,
                            )
                            ok("已排好：把「" + c.title + "」拆成两章，等作者确认")
                        }
                    }
                }

                "merge_chapters" -> {
                    val b = pickBook(store, args, book)
                    val first = args.optString("first").trim()
                    val second = args.optString("second").trim()
                    val a = findChapter(b, first)
                    val z = findChapter(b, second)
                    when {
                        a == null || z == null -> bad(
                            AiToolError.CHAPTER_NOT_FOUND,
                            "有一章找不到：「" + (if (a == null) first else second) + "」。现有：" + chapterNames(b),
                        )
                        a.id == z.id -> bad(AiToolError.STATE_CONFLICT, "不能把一章和自己合并")
                        else -> {
                            val i = b.chapters.indexOfFirst { it.id == a.id }
                            val j = b.chapters.indexOfFirst { it.id == z.id }
                            if (j != i + 1) {
                                bad(
                                    AiToolError.STATE_CONFLICT,
                                    "「" + a.title + "」和「" + z.title + "」不相邻（中间隔了别的章），先把它们挪到一起",
                                )
                            } else {
                                pending += AiPending(
                                    label = "把「" + a.title + "」和「" + z.title + "」合成一章",
                                    kind = AiPendingKind.ChapterMerge,
                                    chapterId = a.id,
                                    bookId = b.id,
                                    text = z.id,
                                    danger = true,
                                )
                                ok("已排好：把「" + a.title + "」和「" + z.title + "」合成一章，等作者确认")
                            }
                        }
                    }
                }

                "chapters_from_outline" -> {
                    val b = pickBook(store, args, book)
                    // 3.26.49：只处理章级节点。free_ 自由节点不绑章，拿去建章会让它变成章节、
                    // 从自由分组里消失，所以这里先按 id 前缀滤掉。
                    val chapterNodes = outlineChapterNodes(b)
                    val loose = looseChapterNodes(b).size
                    when {
                        b.bible.outline.isEmpty() -> ok("《" + b.title + "》的故事圣经还没有大纲，没有东西可建。先用 write_story_bible（kind=outline）写大纲")
                        chapterNodes.isEmpty() -> bad(
                            NO_CHAPTER_NODES,
                            "《" + b.title + "》的大纲只有自由节点（id 以 free_ 开头、不绑章），没有可建章的章级节点。自由节点不会建章，要建章先用 write_story_bible（kind=outline）写章级大纲",
                        )
                        loose == 0 -> ok("大纲里的章节都已经落地了")
                        else -> {
                            pending += AiPending(
                                label = "按大纲建 " + loose + " 章",
                                kind = AiPendingKind.ChapterFromOutline,
                                bookId = b.id,
                            )
                            ok("已排好：按大纲建 " + loose + " 章，等作者确认")
                        }
                    }
                }

                "delete_chapter" -> {
                    val b = pickBook(store, args, book)
                    val want = args.optString("title").trim()
                    val c = findChapter(b, want)
                    if (c == null) {
                        chapterMissing(b, want)
                    } else {
                        pending += AiPending(
                            label = "删章《" + b.title + "》·「" + c.title + "」",
                            kind = AiPendingKind.ChapterOff,
                            bookId = b.id,
                            chapterId = c.id,
                            chapterTitle = c.title,
                            danger = true,
                        )
                        ok("已记下这次删除，等作者在下面确认；删掉不可恢复")
                    }
                }

                // ───────────────────────── 搜索 ─────────────────────────
                "search_book" -> {
                    val kw = args.optString("keyword").trim()
                    val scopeAll = args.optString("scope").ifBlank { "book" } == "all"
                    val limit = args.optInt("limit", if (scopeAll) 12 else 20).coerceIn(1, 30)
                    when {
                        kw.length < 2 -> bad(
                            AiToolError.INVALID_ARG,
                            "关键词太短，给两个字以上（人名、物件、地点最好），不要拿整句话去搜",
                        )
                        scopeAll -> {
                            val hits = ArrayList<String>()
                            books@ for (b in store.books) {
                                for (c in b.chapters) {
                                    var from = 0
                                    var shown = 0
                                    while (shown < 2 && hits.size < limit) {
                                        val at = c.content.indexOf(kw, from, ignoreCase = true)
                                        if (at < 0) break
                                        val start = (at - 40).coerceAtLeast(0)
                                        val end = (at + kw.length + 40).coerceAtMost(c.content.length)
                                        hits += "《" + b.title + "》" + c.title + "｜…" +
                                            c.content.substring(start, end).replace("\n", " ") + "…"
                                        from = at + kw.length
                                        shown++
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
                            if (hits.isEmpty()) ok("全部书和笔记里都没搜到「" + kw + "」")
                            else ok("搜到 " + hits.size + " 处：\n" + hits.joinToString("\n"))
                        }
                        else -> {
                            val b = pickBook(store, args, book)
                            val hits = ArrayList<String>()
                            for (c in b.chapters) {
                                var from = 0
                                var shown = 0
                                while (shown < 3 && hits.size < limit) {
                                    val at = c.content.indexOf(kw, from)
                                    if (at < 0) break
                                    val start = (at - 60).coerceAtLeast(0)
                                    val end = (at + kw.length + 60).coerceAtMost(c.content.length)
                                    hits += c.title + "｜…" + c.content.substring(start, end).replace("\n", " ") + "…"
                                    from = at + kw.length
                                    shown++
                                }
                                if (hits.size >= limit) break
                            }
                            if (hits.isEmpty()) {
                                ok("《" + b.title + "》里没搜到「" + kw + "」。要跨书搜用 scope=all，或换个更短的词")
                            } else {
                                ok("搜到 " + hits.size + " 处：\n" + hits.joinToString("\n"))
                            }
                        }
                    }
                }

                // ───────────────────────── 其他：书籍 ─────────────────────────
                "create_book" -> {
                    val title = args.optString("title").trim()
                    if (title.isBlank()) {
                        bad(AiToolError.MISSING_PARAM, "书名不能为空")
                    } else {
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
                        ok("已新建《" + title + "》")
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
                        b == null -> bad(
                            AiToolError.BOOK_NOT_FOUND,
                            "没有叫「" + args.optString("title") + "」的书。现有：" +
                                (if (store.books.isEmpty()) "还没有书" else store.books.joinToString("、") { "《" + it.title + "》" }),
                        )
                        changes.length() == 0 -> bad(
                            AiToolError.MISSING_PARAM,
                            "没给要改的项目（new_title / author / genre / synopsis 至少给一个）",
                        )
                        else -> {
                            pending += AiPending(
                                label = "改书《" + b.title + "》",
                                kind = AiPendingKind.BookEdit,
                                bookId = b.id,
                                bookTitle = b.title,
                                payload = changes.toString(),
                            )
                            ok("已修改")
                        }
                    }
                }

                "delete_book" -> {
                    val b = findBook(store, args.optString("title"))
                    if (b == null) {
                        bad(
                            AiToolError.BOOK_NOT_FOUND,
                            "没有叫「" + args.optString("title") + "」的书。删除必须写全书名；现有：" +
                                (if (store.books.isEmpty()) "还没有书" else store.books.joinToString("、") { "《" + it.title + "》" }),
                        )
                    } else {
                        pending += AiPending(
                            label = "删书《" + b.title + "》（" + b.chapters.size + " 章）",
                            kind = AiPendingKind.BookOff,
                            bookId = b.id,
                            bookTitle = b.title,
                            danger = true,
                        )
                        ok("已记下这次删除，等作者在下面确认；删掉不可恢复")
                    }
                }

                "open_item" -> {
                    val e = env
                    val kind = args.optString("kind").ifBlank { "chapter" }
                    val title = args.optString("title").trim()
                    when {
                        e == null -> bad(AiToolError.NO_RUNTIME, "当前没有运行环境，打不开")
                        kind !in setOf("book", "chapter", "note") -> bad(
                            AiToolError.INVALID_ARG,
                            "kind 只能是 book / chapter / note",
                        )
                        kind == "book" -> {
                            val b = findBook(store, title)
                            if (b == null) {
                                bad(AiToolError.BOOK_NOT_FOUND, "没有叫「" + title + "」的书")
                            } else {
                                e.openBook(b.id)
                                ok("已打开《" + b.title + "》")
                            }
                        }
                        kind == "note" -> {
                            val n = findNote(store, title)
                            if (n == null) {
                                noteMissing(store, title)
                            } else {
                                e.openNote(n.id)
                                ok("已打开笔记「" + (n.title.ifBlank { "无标题笔记" }) + "」")
                            }
                        }
                        else -> {
                            val b = pickBook(store, args, book)
                            val c = findChapter(b, title)
                            if (c == null) {
                                chapterMissing(b, title)
                            } else {
                                e.openChapter(b.id, c.id)
                                ok("已打开《" + b.title + "》" + c.title)
                            }
                        }
                    }
                }

                // ───────────────────────── 其他：笔记与待办 ─────────────────────────
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
                    ok("已写入")
                }

                "edit_note" -> {
                    val n = findNote(store, args.optString("title"))
                    val changes = JSONObject().apply {
                        if (args.has("new_title")) put("new_title", args.optString("new_title"))
                        if (args.has("body")) put("body", args.optString("body"))
                        if (args.has("pinned")) put("pinned", args.optBoolean("pinned"))
                    }
                    when {
                        n == null -> noteMissing(store, args.optString("title"))
                        changes.length() == 0 -> bad(
                            AiToolError.MISSING_PARAM,
                            "没给要改的项目（new_title / body / pinned 至少给一个）",
                        )
                        else -> {
                            pending += AiPending(
                                label = "改笔记「" + (n.title.ifBlank { "无标题笔记" }) + "」",
                                kind = AiPendingKind.NoteEdit,
                                noteId = n.id,
                                noteTitle = n.title,
                                payload = changes.toString(),
                            )
                            ok("已修改")
                        }
                    }
                }

                "delete_note" -> {
                    val n = findNote(store, args.optString("title"))
                    if (n == null) {
                        bad(AiToolError.NOTE_NOT_FOUND, "没有叫「" + args.optString("title") + "」的笔记。删除必须写全标题")
                    } else {
                        pending += AiPending(
                            label = "删笔记「" + (n.title.ifBlank { "无标题笔记" }) + "」",
                            kind = AiPendingKind.NoteOff,
                            noteId = n.id,
                            noteTitle = n.title,
                            danger = true,
                        )
                        ok("已记下这次删除，等作者在下面确认；删掉不可恢复")
                    }
                }

                "bind_note" -> {
                    val b = pickBook(store, args, book)
                    val n = findNote(store, args.optString("note_title"))
                    val c = findChapter(b, args.optString("chapter_title"))
                    when {
                        n == null -> noteMissing(store, args.optString("note_title"))
                        c == null -> chapterMissing(b, args.optString("chapter_title"))
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
                            ok("已" + (if (on) "绑定" else "解绑"))
                        }
                    }
                }

                "add_todo" -> {
                    val n = findNote(store, args.optString("note_title"))
                    val text = args.optString("text").trim()
                    when {
                        n == null -> noteMissing(store, args.optString("note_title"))
                        text.isBlank() -> bad(AiToolError.MISSING_PARAM, "待办内容不能为空")
                        else -> {
                            pending += AiPending(
                                label = "给笔记「" + (n.title.ifBlank { "无标题笔记" }) + "」加待办：" + text.take(20),
                                kind = AiPendingKind.Todo,
                                noteId = n.id,
                                text = text,
                            )
                            ok("已写入")
                        }
                    }
                }

                "complete_todo" -> {
                    val n = findNote(store, args.optString("note_title"))
                    val text = args.optString("text").trim()
                    val hit = n?.todos?.firstOrNull { !it.done && (it.text == text || (text.isNotBlank() && it.text.contains(text))) }
                    when {
                        n == null -> noteMissing(store, args.optString("note_title"))
                        hit == null -> bad(
                            AiToolError.TODO_NOT_FOUND,
                            "这条笔记里没找到未完成的待办「" + text + "」。现有：" +
                                n.todos.filter { !it.done }.take(10).joinToString("、") { it.text },
                        )
                        else -> {
                            pending += AiPending(
                                label = "完成待办：" + hit.text.take(24),
                                kind = AiPendingKind.TodoDone,
                                noteId = n.id,
                                text = hit.id,
                            )
                            ok("已标记完成")
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
                        n == null -> noteMissing(store, args.optString("note_title"))
                        wanted.isBlank() -> bad(AiToolError.MISSING_PARAM, "没说要给哪条待办设提醒")
                        hit == null -> bad(
                            AiToolError.TODO_NOT_FOUND,
                            "这条笔记里没找到未完成的待办「" + wanted + "」。现有：" +
                                n.todos.filter { !it.done }.take(10).joinToString("、") { it.text },
                        )
                        at == null -> bad(AiToolError.INVALID_ARG, "时间看不懂，用 2026-11-13 09:00 这种格式")
                        at <= System.currentTimeMillis() -> bad(AiToolError.INVALID_ARG, "这个时间已经过去了，换一个")
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
                            ok("已设好提醒")
                        }
                    }
                }

                // ───────────────────────── 其他：分组 ─────────────────────────
                "list_groups" -> {
                    val bookGroups = store.bookGroups
                    val noteGroups = store.noteGroups
                    ok(
                        "书籍分组：" + (if (bookGroups.isEmpty()) "无" else bookGroups.joinToString("、") { it.name }) +
                            "\n笔记分组：" + (if (noteGroups.isEmpty()) "无" else noteGroups.joinToString("、") { it.name }),
                    )
                }

                "create_group" -> {
                    val kind = args.optString("kind").ifBlank { "note" }
                    val name = args.optString("name").trim()
                    when {
                        kind !in setOf("book", "note") -> bad(AiToolError.INVALID_ARG, "kind 只能是 book / note")
                        name.isBlank() -> bad(AiToolError.MISSING_PARAM, "分组名不能为空")
                        else -> {
                            pending += AiPending(
                                label = "新建" + (if (kind == "book") "书籍" else "笔记") + "分组「" + name + "」",
                                kind = AiPendingKind.Group,
                                groupKind = kind,
                                groupName = name,
                            )
                            ok("已新建")
                        }
                    }
                }

                "rename_group" -> {
                    val kind = args.optString("kind").ifBlank { "note" }
                    val name = args.optString("name").trim()
                    val newName = args.optString("new_name").trim()
                    val g = groupsOf(store, kind).firstOrNull { it.name == name }
                    when {
                        g == null -> bad(
                            AiToolError.GROUP_NOT_FOUND,
                            "没有叫「" + name + "」的" + (if (kind == "book") "书籍" else "笔记") + "分组。现有：" +
                                (groupsOf(store, kind).joinToString("、") { it.name }.ifBlank { "无" }),
                        )
                        newName.isBlank() -> bad(AiToolError.MISSING_PARAM, "新分组名不能为空")
                        else -> {
                            pending += AiPending(
                                label = "改分组名：「" + g.name + "」→「" + newName + "」",
                                kind = AiPendingKind.GroupEdit,
                                groupKind = kind,
                                groupId = g.id,
                                groupName = newName,
                            )
                            ok("已修改")
                        }
                    }
                }

                "delete_group" -> {
                    val kind = args.optString("kind").ifBlank { "note" }
                    val name = args.optString("name").trim()
                    val g = groupsOf(store, kind).firstOrNull { it.name == name }
                    if (g == null) {
                        bad(AiToolError.GROUP_NOT_FOUND, "没有叫「" + name + "」的分组")
                    } else {
                        pending += AiPending(
                            label = "删分组「" + g.name + "」（内容回到未分组）",
                            kind = AiPendingKind.GroupOff,
                            groupKind = kind,
                            groupId = g.id,
                            groupName = g.name,
                            danger = true,
                        )
                        ok("已记下，等你在下面确认后才会执行")
                    }
                }

                "move_to_group" -> {
                    val kind = args.optString("kind").ifBlank { "note" }
                    val name = args.optString("name").trim()
                    val item = args.optString("item_title").trim()
                    val g = groupsOf(store, kind).firstOrNull { it.name == name }
                    val target = if (kind == "book") findBook(store, item) else findNote(store, item)
                    when {
                        g == null -> bad(AiToolError.GROUP_NOT_FOUND, "没有叫「" + name + "」的分组")
                        target == null -> {
                            if (kind == "book") {
                                bad(AiToolError.BOOK_NOT_FOUND, "没有叫「" + item + "」的书")
                            } else {
                                noteMissing(store, item)
                            }
                        }
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
                            ok("已移动")
                        }
                    }
                }

                // ───────────────────────── 其他：设置与统计 ─────────────────────────
                "get_settings" -> {
                    val s = store.data.settings
                    ok(
                        "主题：" + s.theme + "　字体：" + s.font + "　字号：" +
                            "%.2f".format(java.util.Locale.US, s.fontScale) +
                            "　行距：" + "%.2f".format(java.util.Locale.US, s.lineHeight) +
                            "　页边距：" + s.pageMargin.toInt() + "dp" +
                            "\n纸张：" + s.paperStyle + "　分节：" + s.sectionStyle + "　目标：" + s.dailyGoal + " 字/天",
                    )
                }

                "set_style" -> {
                    val keys = listOf("font_size", "line_height", "page_margin", "font", "theme", "paper_style", "section_style")
                    val changes = JSONObject()
                    for (k in keys) if (args.has(k)) changes.put(k, args.opt(k))
                    if (changes.length() == 0) {
                        bad(AiToolError.MISSING_PARAM, "没给要改的项")
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
                        ok("已生效")
                    }
                }

                "get_stats" -> {
                    val total = store.books.sumOf { b -> b.chapters.sumOf { countWords(it.content) } }
                    ok(
                        "书 " + store.books.size + " 本、笔记 " + store.notes.size + " 条；总字数 " + total +
                            "，今日 " + store.todayWords() + " 字，连续 " + store.streak() + " 天",
                    )
                }

                "set_daily_goal" -> {
                    val w = args.optInt("words", -1)
                    if (w < 100 || w > 100_000) {
                        bad(AiToolError.INVALID_ARG, "每日目标要在 100 到 100000 字之间")
                    } else {
                        pending += AiPending(
                            label = "每日目标改为 " + w + " 字",
                            kind = AiPendingKind.Goal,
                            payload = JSONObject().put("words", w).toString(),
                        )
                        ok("已修改")
                    }
                }

                // ───────────────────────── 其他：导出与备份 ─────────────────────────
                "export_item" -> {
                    val kind = args.optString("kind").ifBlank { "book" }
                    val title = args.optString("title").trim()
                    val format = args.optString("format")
                        .ifBlank { if (kind == "note") "txt" else "epub" }.lowercase()
                    when {
                        kind !in setOf("book", "note") -> bad(AiToolError.INVALID_ARG, "kind 只能是 book / note")
                        kind == "note" -> {
                            val n = findNote(store, title)
                            when {
                                n == null -> noteMissing(store, title)
                                format !in listOf("txt", "md", "json") -> bad(AiToolError.INVALID_ARG, "笔记只能导出 txt / md / json")
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
                                    ok("已导出到 下载/字隅")
                                }
                            }
                        }
                        else -> {
                            val b = findBook(store, title)
                            when {
                                b == null -> bad(AiToolError.BOOK_NOT_FOUND, "没有叫「" + title + "」的书。导出要写全书名")
                                format !in listOf("epub", "txt") -> bad(AiToolError.INVALID_ARG, "书只能导出 epub / txt")
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
                                    ok("已导出到 下载/字隅")
                                }
                            }
                        }
                    }
                }

                "backup_now" -> {
                    pending += AiPending(label = "导出 JSON 备份", kind = AiPendingKind.Backup)
                    ok("已写入文件")
                }

                "list_backups" -> {
                    val e = env
                    if (e == null) {
                        bad(AiToolError.NO_RUNTIME, "当前没有运行环境，列不了备份")
                    } else {
                        val list = BackupOps.list(e.context)
                        if (list.isEmpty()) {
                            ok("还没有备份。让作者在 设置 → 备份与恢复 导出一份")
                        } else {
                            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                            ok(
                                list.joinToString("\n") { backup ->
                                    "· " + backup.name + "｜" + (backup.size / 1024) + " KB｜" +
                                        fmt.format(java.util.Date(backup.dateAdded))
                                },
                            )
                        }
                    }
                }

                "restore_backup" -> {
                    val e = env
                    val backupName = args.optString("name").trim()
                    if (e == null) {
                        bad(AiToolError.NO_RUNTIME, "当前没有运行环境，恢复不了备份")
                    } else {
                        val text = BackupOps.readText(e.context, backupName)
                        val parsed = if (text == null) null else runCatching { BackupOps.parse(text) }.getOrNull()
                        when {
                            text == null -> bad(
                                AiToolError.BACKUP_NOT_FOUND,
                                "没找到备份「" + backupName + "」。先用 list_backups 看有哪些",
                            )
                            parsed == null -> bad(AiToolError.STATE_CONFLICT, "这份备份解析不了，没有动当前数据")
                            else -> {
                                pending += AiPending(
                                    label = "用备份「" + backupName + "」覆盖全部数据（" + parsed.books.size + " 本书 / " +
                                        parsed.notes.size + " 条笔记）",
                                    kind = AiPendingKind.Restore,
                                    payload = JSONObject().put("name", backupName).toString(),
                                    danger = true,
                                )
                                ok("已记下这次恢复，等你在下面确认；它会覆盖当前全部数据")
                            }
                        }
                    }
                }

                // ───────────────────────── 其他：附件与联网 ─────────────────────────
                "read_attachment" -> {
                    val e = env
                    val wanted = args.optString("name").trim()
                    if (e == null) {
                        bad(AiToolError.NO_RUNTIME, "当前没有运行环境，读不了附件")
                    } else {
                        val a = findAttachment(e.attachments, wanted)
                        val page = args.optInt("from", 1).coerceAtLeast(1)
                        val start = (page - 1) * 6000
                        when {
                            a == null -> bad(
                                AiToolError.ATTACHMENT_NOT_FOUND,
                                "没有叫「" + wanted + "」的附件。" +
                                    if (e.attachments.isEmpty()) "这条对话还没有附件，让作者点输入框左边的回形针选一个"
                                    else "现有：" + e.attachments.joinToString("、") { it.name },
                            )
                            start >= a.text.length -> bad(
                                AiToolError.INVALID_ARG,
                                "《" + a.name + "》共 " + a.chars + " 字，没有第 " + page + " 页",
                            )
                            else -> {
                                val end = (start + 6000).coerceAtMost(a.text.length)
                                ok(
                                    "附件《" + a.name + "》（共 " + a.chars + " 字，第 " + (start + 1) + "–" + end + " 字）：\n\n" +
                                        a.text.substring(start, end),
                                )
                            }
                        }
                    }
                }

                "import_attachment" -> {
                    val e = env
                    val wanted = args.optString("name").trim()
                    val asKind = args.optString("as").ifBlank { "book" }
                    when {
                        e == null -> bad(AiToolError.NO_RUNTIME, "当前没有运行环境，导入不了附件")
                        asKind !in setOf("book", "note") -> bad(AiToolError.INVALID_ARG, "as 只能是 book / note")
                        else -> {
                            val a = findAttachment(e.attachments, wanted)
                            if (a == null) {
                                bad(
                                    AiToolError.ATTACHMENT_NOT_FOUND,
                                    "没有叫「" + wanted + "」的附件。" +
                                        if (e.attachments.isEmpty()) "这条对话还没有附件"
                                        else "现有：" + e.attachments.joinToString("、") { it.name },
                                )
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
                                ok("已导入")
                            }
                        }
                    }
                }

                "web_search" -> {
                    val e = env
                    when {
                        e == null -> bad(AiToolError.NO_RUNTIME, "当前没有运行环境，联不了网")
                        !e.webEnabled -> bad(AiToolError.WEB_DISABLED, "联网没开。作者可以在 设置 → 问书 → 联网与文件 打开")
                        e.searches >= e.webMax -> bad(
                            AiToolError.BUDGET_EXHAUSTED,
                            "这次已经搜了 " + e.searches + " 次，到上限了；先用已有结果回答",
                        )
                        else -> {
                            e.searches++
                            val query = args.optString("query").trim()
                            val r = AiWeb.search(e.baseUrl, e.key, query, args.optInt("count", 5).coerceIn(1, 8))
                            when {
                                r.error != null -> bad(AiToolError.WEB_FAILED, "搜索没成功（" + r.error + "）")
                                r.hits.isEmpty() -> ok("没搜到「" + query + "」的结果")
                                else -> ok(
                                    "搜到 " + r.hits.size + " 条：\n" + r.hits.joinToString("\n") { h ->
                                        "· " + h.title + "｜" + h.site.ifBlank { "网页" } +
                                            (if (h.date.isNotBlank()) "｜" + h.date.take(10) else "") + "\n  " +
                                            h.url + "\n  " + h.summary.ifBlank { h.snippet }.take(300)
                                    },
                                )
                            }
                        }
                    }
                }

                "web_fetch" -> {
                    val e = env
                    when {
                        e == null -> bad(AiToolError.NO_RUNTIME, "当前没有运行环境，联不了网")
                        !e.webEnabled -> bad(AiToolError.WEB_DISABLED, "联网没开。作者可以在 设置 → 问书 → 联网与文件 打开")
                        e.fetches >= 3 -> bad(AiToolError.BUDGET_EXHAUSTED, "这次已经抓了 " + e.fetches + " 页，到上限了")
                        else -> {
                            e.fetches++
                            val p = AiWeb.fetch(e.baseUrl, e.key, args.optString("url"))
                            if (p.error != null) {
                                bad(AiToolError.WEB_FAILED, "抓取没成功（" + p.error + "）")
                            } else {
                                ok(
                                    "《" + p.title.ifBlank { "网页" } + "》\n\n" + p.content.take(6000) +
                                        (if (p.content.length > 6000) "\n……原文 " + p.content.length + " 字，只给前 6000 字" else ""),
                                )
                            }
                        }
                    }
                }

                // facts 记忆层已下线：旧名字即使被模型调出来也不落盘（AiTab 还会再提示一次）。
                "save_memory", "delete_memory" -> bad(
                    AiToolError.UNSUPPORTED,
                    "没有写入任何东西：facts 记忆层已经下线，故事圣经是唯一的长期记忆层。" +
                        "要记设定、人物、关系、事件、大纲，用 write_story_bible。现有旧记忆 " +
                        (facts.size + globalFacts.size) + " 条都不会再写入。",
                )

                else -> bad(AiToolError.UNKNOWN_TOOL, "没有「" + name + "」这个工具")
            }
        } catch (t: Throwable) {
            bad(
                AiToolError.INTERNAL,
                "工具 " + name + " 没跑通（" + (t.message ?: t.javaClass.simpleName) +
                    "）。换个参数或换条路子，别把这句原样转给作者。",
            )
        }
        // 询问档：正文改动只是排成补丁，别让模型回「已写入」。
        return if (env?.writeMode == 1 && name in PATCH_TOOLS && AiToolResult.isOk(reply)) {
            ok("已排成补丁，等作者在回答下面点「接受」；现在还没写进正文。")
        } else {
            reply
        }
    }

    // ───────────────────────── 工具表解析与调用前校验 ─────────────────────────

    /** 工具表里一条的结构：必填项和全部参数名。 */
    private class DefSpec(val required: List<String>, val props: Set<String>) {
        /** 带 book 参数说明它的目标可以指到别的书。 */
        val usesBook: Boolean get() = "book" in props
    }

    /**
     * 工具表解析（懒加载一次）。解析前先跑 [AiToolCatalog.audit]，问题写进 logcat
     * `MOYU_AI`；即使表坏了也只当成「没有规范表」，不让每次工具调用都崩
     * （3.26.23 少一个 `}` 的教训）。
     */
    private val defSpecs: Map<String, DefSpec> by lazy { parseDefSpecs() }

    private fun parseDefSpecs(): Map<String, DefSpec> {
        val problems = AiToolCatalog.audit(DEFS)
        for (p in problems) android.util.Log.e("MOYU_AI", "工具表自检：" + p)
        return runCatching {
            val arr = JSONArray(DEFS)
            val out = LinkedHashMap<String, DefSpec>()
            for (i in 0 until arr.length()) {
                val fn = arr.optJSONObject(i)?.optJSONObject("function") ?: continue
                val toolName = fn.optString("name").trim()
                if (toolName.isEmpty()) continue
                val params = fn.optJSONObject("parameters") ?: continue
                val props = params.optJSONObject("properties")?.keys()?.asSequence()?.toSet() ?: emptySet()
                val required = params.optJSONArray("required")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }
                } ?: emptyList()
                out[toolName] = DefSpec(required, props)
            }
            out
        }.getOrElse { t ->
            android.util.Log.e("MOYU_AI", "工具表解析失败：" + (t.message ?: t.javaClass.simpleName))
            emptyMap()
        }
    }

    /** 参数缺了或只有空白：必填项判定用。 */
    private fun blankArg(args: JSONObject, key: String): Boolean {
        if (!args.has(key)) return true
        return when (val v = args.opt(key)) {
            null, JSONObject.NULL -> true
            is String -> v.isBlank()
            else -> false
        }
    }

    /**
     * 这几个工具的必填参数允许空串：空串本身是有意义的输入（把一段删掉 / 建一条空笔记），
     * 只要求参数「在」，不要求「非空」。
     */
    private fun blankOkOf(name: String): Set<String> = when (name) {
        "replace_in_chapter" -> setOf("replace")
        "create_note" -> setOf("body")
        else -> emptySet()
    }

    /** 必填参数里第一个「缺了或没意义」的键；没有就返回 null。 */
    private fun missingRequiredParam(name: String, args: JSONObject, required: List<String>): String? =
        required.firstOrNull { key ->
            if (!args.has(key)) true else key !in blankOkOf(name) && blankArg(args, key)
        }

    private fun ok(data: String): String = AiToolResult.ok(data)

    private fun bad(code: String, message: String): String = AiToolResult.fail(code, message)

    /** 有 book 参数就按它挑书；空=当前书（存在性已在 [run] 入口校验过）。 */
    private fun pickBook(store: AppStore, args: JSONObject, fallback: Book): Book {
        val want = args.optString("book").trim()
        if (want.isBlank()) return fallback
        return findBook(store, want) ?: fallback
    }

    private fun chapterNames(b: Book): String =
        if (b.chapters.isEmpty()) "这本书还没有章节" else b.chapters.joinToString("、") { it.title }

    private fun chapterMissing(b: Book, want: String): String = bad(
        AiToolError.CHAPTER_NOT_FOUND,
        "《" + b.title + "》没有叫「" + want + "」的章节。现有：" + chapterNames(b) +
            "。要确认目录先用 list_chapters",
    )

    private fun noteMissing(store: AppStore, want: String): String = bad(
        AiToolError.NOTE_NOT_FOUND,
        "没有叫「" + want + "」的笔记。现有：" +
            (if (store.notes.isEmpty()) "还没有笔记" else store.notes.take(12).joinToString("、") { it.title.ifBlank { "无标题笔记" } }),
    )

    /**
     * [create_chapter] 给了 afterChapterId 时用：把新章插到锚点后面。
     * 返回锚点下标（0 起，-1 = 锚点没了、什么都没插）。
     * 走 store.updateBook 而不是 addChapter：后者只会接在最后。
     */
    private fun insertChapterAfter(store: AppStore, bookId: String, anchorId: String, chapter: Chapter): Int {
        var at = -1
        store.updateBook(bookId) { b ->
            val i = b.chapters.indexOfFirst { it.id == anchorId }
            at = i
            if (i < 0) {
                b
            } else {
                val list = b.chapters.toMutableList()
                list.add(i + 1, chapter)
                b.copy(chapters = list, updatedAt = System.currentTimeMillis())
            }
        }
        return at
    }

    /**
     * 「按大纲建章」回填 chapterId 的那一步：新章标题对上还没落地的大纲节点就挂上
     * （与 chapters_from_outline 的落地行为一致）。返回是否挂上了。
     *
     * 3.26.50：只挂**章级**节点——`free_` 自由节点不参与（与 [chapters_from_outline] 同口径）。
     * 以前只判断「章节标题撞上节点标题」，撞上自由节点会回填 chapterId、让它不再是自由节点。
     */
    private fun linkOutlineNode(store: AppStore, b: Book, chapterId: String, title: String): Boolean {
        val node = outlineChapterNodes(b).firstOrNull { n ->
            b.chapters.none { it.id == n.chapterId } &&
                (n.title.trim() == title || (n.title.trim().length >= 2 && title.contains(n.title.trim())))
        } ?: return false
        store.updateBible(
            b.id,
            b.bible.copy(outline = b.bible.outline.map { if (it.id == node.id) it.copy(chapterId = chapterId) else it }),
        )
        return true
    }


    /**
     * 3.26.49：能拿去建章的**章级**大纲节点——排除 `free_` 自由节点。
     * 自由节点（3.26.46 起）不绑章、单独分组，只有 id 前缀可区分（OutlineNode 没有类型字段）。
     */
    private fun outlineChapterNodes(b: Book): List<OutlineNode> =
        b.bible.outline.filterNot { it.id.startsWith(FREE_OUTLINE_PREFIX) }

    /** 还没落地的章级节点；自由节点不算「待建章」，免得提示让模型去建它们。 */
    private fun looseChapterNodes(b: Book): List<OutlineNode> =
        outlineChapterNodes(b).filter { n -> b.chapters.none { it.id == n.chapterId } }

    fun lineStartAt(content: String, n: Int): Int {
        if (n <= 0) return -1
        var seen = 0
        var i = 0
        while (i < content.length) {
            val nl = content.indexOf('\n', i)
            val end = if (nl < 0) content.length else nl
            if (content.substring(i, end).isNotBlank()) {
                seen++
                if (seen == n) return i
            }
            i = end + 1
        }
        return -1
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
