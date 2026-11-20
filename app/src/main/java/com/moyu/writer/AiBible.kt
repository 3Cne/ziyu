package com.moyu.writer

/**
 * 3.26.64：问书页与全部 AI 工具下线后，故事圣经只剩两条「读」用途：
 * - [summary]：校核 / 修饰 / 问 拼上下文时的故事圣经摘要；
 * - [consistencyPrompt]：整本一致性巡检的用户消息（摘要 + 全部正文）。
 *
 * 写入口（mutate / journal / 待同意 / 同步到我的 / 撤销）随 AI 工具一起删除；
 * 数据里的 `origin` 字段保留（作者条目 = "author"），旧 AI 条目由 `AiCleanup` 一次性清掉。
 */
object AiBible {
    /**
     * 3.26.59：角色行尾追加「性格 / 所属」（非空才拼）。
     *
     * 返回空串 = 两个字段都没填，调用方直接拼在行尾也不会多出分隔符。
     */
    private fun characterDetail(c: StoryCharacter): String {
        val parts = ArrayList<String>(2)
        if (c.traits.isNotBlank()) parts += "性格：" + c.traits
        if (c.faction.isNotBlank()) parts += "所属：" + c.faction
        return if (parts.isEmpty()) "" else "；" + parts.joinToString("；")
    }

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
                sb.append(characterDetail(c))
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

    private fun charName(book: Book, id: String): String =
        book.bible.characters.firstOrNull { it.id == id }?.name ?: id
}
