package com.moyu.writer

import org.json.JSONArray

/**
 * 3.26.45：工具归组。原来是 53 条平铺的工具表，模型要在一长串里自己认「这个该不该读/写」；
 * 现在每条都有组，提示词（Ai.kt 的 GUIDE）按组写阶梯，工具表也按组排。
 */
enum class AiToolGroup(val label: String) {
    READ("读正文"),
    WRITE("写正文"),
    BIBLE("故事圣经"),
    STRUCTURE("结构手术"),
    SEARCH("搜索"),
    OTHER("其他"),
}

/** 一条工具的身份：名字 + 归组 + 会不会改东西（只读档下后者一律拒绝）。 */
data class AiToolMeta(
    val name: String,
    val group: AiToolGroup,
    val write: Boolean = false,
)

/**
 * 工具目录：唯一一张「有哪些工具、各是哪组、哪些会写」的清单。
 *
 * - [tools] 的顺序就是 `AiTools.DEFS` 里的顺序（读正文 → 写正文 → 故事圣经 →
 *   结构手术 → 搜索 → 其他），`audit` 会核对两边是否一一对上；
 * - [WRITE_TOOLS] 由这里的 `write` 标志派生，`AiTools` 用它做只读档拦截，
 *   不再手写一份容易和工具表漂移的集合；
 * - 3.26.23 曾因为工具表 JSON 少一个 `}` 导致运行时抛错，[audit] 就是那次的防线：
 *   结构、重名、required 是否在 properties 里、归组有没有漏，一次全查。
 */
object AiToolCatalog {
    val tools: List<AiToolMeta> = listOf(
        // ── 读正文（6）
        AiToolMeta("list_books", AiToolGroup.READ),
        AiToolMeta("list_chapters", AiToolGroup.READ),
        AiToolMeta("read_chapter", AiToolGroup.READ),
        AiToolMeta("list_notes", AiToolGroup.READ),
        AiToolMeta("read_note", AiToolGroup.READ),
        AiToolMeta("read_chapter_history", AiToolGroup.READ),

        // ── 写正文（3）
        AiToolMeta("append_to_chapter", AiToolGroup.WRITE, write = true),
        AiToolMeta("replace_in_chapter", AiToolGroup.WRITE, write = true),
        AiToolMeta("restore_chapter_history", AiToolGroup.WRITE, write = true),

        // ── 故事圣经（4）
        AiToolMeta("read_story_bible", AiToolGroup.BIBLE),
        AiToolMeta("write_story_bible", AiToolGroup.BIBLE, write = true),
        AiToolMeta("set_story_time", AiToolGroup.BIBLE, write = true),
        AiToolMeta("undo_bible_change", AiToolGroup.BIBLE, write = true),

        // ── 结构手术（7）
        AiToolMeta("create_chapter", AiToolGroup.STRUCTURE, write = true),
        AiToolMeta("rename_chapter", AiToolGroup.STRUCTURE, write = true),
        AiToolMeta("move_chapter", AiToolGroup.STRUCTURE, write = true),
        AiToolMeta("split_chapter", AiToolGroup.STRUCTURE, write = true),
        AiToolMeta("merge_chapters", AiToolGroup.STRUCTURE, write = true),
        AiToolMeta("chapters_from_outline", AiToolGroup.STRUCTURE, write = true),
        AiToolMeta("delete_chapter", AiToolGroup.STRUCTURE, write = true),

        // ── 搜索（1）
        AiToolMeta("search_book", AiToolGroup.SEARCH),

        // ── 其他（29）：书籍 / 笔记 / 分组 / 设置 / 导出备份 / 附件联网 / 反问
        AiToolMeta("create_book", AiToolGroup.OTHER, write = true),
        AiToolMeta("edit_book", AiToolGroup.OTHER, write = true),
        AiToolMeta("delete_book", AiToolGroup.OTHER, write = true),
        AiToolMeta("open_item", AiToolGroup.OTHER),
        AiToolMeta("create_note", AiToolGroup.OTHER, write = true),
        AiToolMeta("edit_note", AiToolGroup.OTHER, write = true),
        AiToolMeta("delete_note", AiToolGroup.OTHER, write = true),
        AiToolMeta("bind_note", AiToolGroup.OTHER, write = true),
        AiToolMeta("add_todo", AiToolGroup.OTHER, write = true),
        AiToolMeta("complete_todo", AiToolGroup.OTHER, write = true),
        AiToolMeta("set_reminder", AiToolGroup.OTHER, write = true),
        AiToolMeta("list_groups", AiToolGroup.OTHER),
        AiToolMeta("create_group", AiToolGroup.OTHER, write = true),
        AiToolMeta("rename_group", AiToolGroup.OTHER, write = true),
        AiToolMeta("delete_group", AiToolGroup.OTHER, write = true),
        AiToolMeta("move_to_group", AiToolGroup.OTHER, write = true),
        AiToolMeta("get_settings", AiToolGroup.OTHER),
        AiToolMeta("set_style", AiToolGroup.OTHER, write = true),
        AiToolMeta("get_stats", AiToolGroup.OTHER),
        AiToolMeta("set_daily_goal", AiToolGroup.OTHER, write = true),
        AiToolMeta("export_item", AiToolGroup.OTHER, write = true),
        AiToolMeta("backup_now", AiToolGroup.OTHER, write = true),
        AiToolMeta("list_backups", AiToolGroup.OTHER),
        AiToolMeta("restore_backup", AiToolGroup.OTHER, write = true),
        AiToolMeta("read_attachment", AiToolGroup.OTHER),
        AiToolMeta("import_attachment", AiToolGroup.OTHER, write = true),
        AiToolMeta("web_search", AiToolGroup.OTHER),
        AiToolMeta("web_fetch", AiToolGroup.OTHER),
        // 3.26.60：带选项反问。不改数据，所以不是 write；只读档下也能用。
        AiToolMeta("ask_user", AiToolGroup.OTHER),
    )

    val byName: Map<String, AiToolMeta> by lazy { tools.associateBy { it.name } }

    /** 只读档下要拦的工具名（由上面的 `write` 派生，不再手抄一份）。 */
    val WRITE_TOOLS: Set<String> by lazy { tools.filter { it.write }.map { it.name }.toSet() }

    fun groupOf(name: String): AiToolGroup? = byName[name]?.group

    /** 某个组里的工具名，按目录顺序。 */
    fun namesIn(group: AiToolGroup): List<String> = tools.filter { it.group == group }.map { it.name }

    private val NAME_RE = Regex("^[a-z][a-z0-9_]*$")
    private val PROP_TYPES = setOf("string", "integer", "number", "boolean", "array", "object")

    /**
     * 工具表结构自检。返回空表示合法；有字就是每一条具体问题。
     * 不执行工具、不看 store，只查这张表本身能不能被 OpenAI 接受。
     */
    fun audit(defsRaw: String): List<String> {
        val problems = ArrayList<String>()
        val arr: JSONArray = runCatching { JSONArray(defsRaw) }.getOrElse { e ->
            return listOf("工具表 JSON 解析失败：" + (e.message ?: e.javaClass.simpleName))
        }
        if (arr.length() == 0) problems += "工具表是空的"
        val seen = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i)
            if (row == null) {
                problems += "#$i 不是对象"
                continue
            }
            if (row.optString("type") != "function") problems += "#$i 的 type 不是 function"
            val fn = row.optJSONObject("function")
            if (fn == null) {
                problems += "#$i 没有 function"
                continue
            }
            val name = fn.optString("name").trim()
            if (name.isEmpty()) {
                problems += "#$i 没有 name"
                continue
            }
            if (!NAME_RE.matches(name)) problems += "$name 不是小写下划线"
            if (!seen.add(name)) problems += "$name 重名"
            if (fn.optString("description").isBlank()) problems += "$name 没有 description"
            val params = fn.optJSONObject("parameters")
            if (params == null) {
                problems += "$name 没有 parameters"
                continue
            }
            if (params.optString("type") != "object") problems += "$name 的 parameters.type 不是 object"
            val props = params.optJSONObject("properties")
            if (props == null) {
                problems += "$name 的 parameters.properties 不是对象"
                continue
            }
            val propNames = props.keys().asSequence().toSet()
            for (key in propNames) {
                val p = props.optJSONObject(key)
                if (p == null) {
                    problems += "$name.$key 不是对象"
                    continue
                }
                val type = p.optString("type")
                if (type !in PROP_TYPES) problems += "$name.$key 的 type 不认识：$type"
                if (type == "array" && p.optJSONObject("items") == null) {
                    problems += "$name.$key 是 array 但没写 items"
                }
            }
            val required = params.optJSONArray("required")
            if (required != null) {
                for (j in 0 until required.length()) {
                    val key = required.optString(j)
                    if (key !in propNames) problems += "$name 的 required 里 $key 不在 properties"
                }
            }
            if (byName[name] == null) problems += "$name 没有归组（AiToolCatalog 缺这一条）"
        }
        for (meta in tools) {
            if (!seen.contains(meta.name)) problems += meta.name + " 只在归组表里、工具表里没有"
        }
        return problems
    }
}
