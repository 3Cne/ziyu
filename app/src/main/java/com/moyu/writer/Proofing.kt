package com.moyu.writer

import org.json.JSONArray

/**
 * One suggested fix for a selected passage.
 *
 * [quote] is the exact text copied out of the body; [suggestion] replaces it
 * verbatim. Nothing is applied automatically — the writer accepts each line.
 *
 * 3.26.23 增加两个字段，都带默认值：旧调用、旧数据、没有设定摘要时行为不变。
 * [reason] 是一句话依据；[level] 是「字面」还是「设定」——设定类表示与故事圣经
 * 里已有的角色 / 关系 / 事件冲突，reason 写明依据哪一条。浮窗只显示 reason，
 * level 留给调用方判断（例如设定冲突单独归一组）。
 */
data class ProofIssue(
    val kind: String,
    val quote: String,
    val suggestion: String,
    val why: String,
    val local: Boolean,
    /** 修饰切块在正文里的绝对范围（含端点，end 不含）；校核的卡为 -1。
     *  只在本轮会话里有效：正文一改，后面的位置就旧了，采纳时按 quote 回退。 */
    val start: Int = -1,
    val end: Int = -1,
    val id: String = java.util.UUID.randomUUID().toString().take(8),
    /** 为什么这么改 / 错在哪：字面错一句话；设定冲突写依据哪条设定或事件。
     *  旧格式没有这个字段，解析后留空，卡片不显示这一行。 */
    val reason: String = "",
    /** "字面" 或 "设定"；旧格式 / 没有故事圣经摘要时一律按字面。 */
    val level: String = "字面",
)

/**
 * 校核（2026-11-14 改版）：**全部交给模型**。
 *
 * 3.16.0 之前有本地规则层（重复标点 / 半角标点 / 行首标点 / 16 条错别字固定搭配 / 人名比对）。
 * 用作者真实的 2405 字章测过：五类合计命中 **0**，等于没有。用户当场定：
 * 「不要本地校核了，全交给 AI」。
 *
 * 所以现在只有一条路：选中 → 直接发模型 → 错别字 / 符号 / 语义三类一起查。
 * `local()` 与 `names()` 保留在下面但**不再被调用**（留着是为了对照历史结论，
 * 接线时别再把它们挂回去，除非有人拿真实正文证明它们能命中）。
 *
 * 定位仍是本地：每条建议带着要替换的原文，编辑器只改能找到且唯一的那一处。
 */
object Proofing {
    // 不再有字数上限（2026-11-15 用户定）：整章选进来也照样跑，`chunks()` 按 1500 字切段。

    private val HAN = '\u4e00'..'\u9fa5'
    private val DUP = Regex("([，。、；：])\\1+")
    private val HALF = Regex("([\\u4e00-\\u9fa5])([,.;:?!])([\\u4e00-\\u9fa5])")
    private const val LINE_STARTS = "，。、；：？！"

    /**
     * 高置信度错别字：只收「写了就是错」的固定搭配，不收要判断语境的
     * 的/地/得、在/再。误报一次，作者就不再信这个检查。
     */
    private val TYPO = listOf(
        "既使" to "即使",
        "按装" to "安装",
        "部份" to "部分",
        "做为" to "作为",
        "以经" to "已经",
        "因该" to "应该",
        "迫不急待" to "迫不及待",
        "再接再励" to "再接再厉",
        "一如继往" to "一如既往",
        "一股作气" to "一鼓作气",
        "不径而走" to "不胫而走",
        "默守成规" to "墨守成规",
        "金榜提名" to "金榜题名",
        "名符其实" to "名副其实",
        "帐号" to "账号",
        "坐位" to "座位",
    )

    /** Local, zero-cost findings. Only items that can be replaced exactly. */
    fun local(text: String): List<ProofIssue> {
        val out = ArrayList<ProofIssue>()
        // 错别字：固定搭配，直接给出正确写法。
        for ((wrong, right) in TYPO) {
            var at = text.indexOf(wrong)
            while (at >= 0) {
                out += ProofIssue(
                    kind = "错别字",
                    quote = wrong,
                    suggestion = right,
                    why = "应为「$right」",
                    local = true,
                )
                at = text.indexOf(wrong, at + wrong.length)
            }
        }
        DUP.findAll(text).forEach { m ->
            out += ProofIssue(
                kind = "符号",
                quote = m.value,
                suggestion = m.groupValues[1],
                why = "标点重复",
                local = true,
            )
        }
        HALF.findAll(text).forEach { m ->
            val full = when (m.groupValues[2]) {
                "," -> "，"
                "." -> "。"
                ";" -> "；"
                ":" -> "："
                "?" -> "？"
                else -> "！"
            }
            out += ProofIssue(
                kind = "符号",
                quote = m.value,
                suggestion = m.groupValues[1] + full + m.groupValues[3],
                why = "中文里用了半角标点",
                local = true,
            )
        }
        var lineAt = 0
        while (lineAt < text.length) {
            val lineEnd = text.indexOf('\n', lineAt).let { if (it < 0) text.length else it }
            val line = text.substring(lineAt, lineEnd)
            val trimmed = line.trimStart()
            if (trimmed.length > 1 && LINE_STARTS.contains(trimmed[0])) {
                val quote = trimmed.take(40)
                out += ProofIssue(
                    kind = "符号",
                    quote = quote,
                    suggestion = quote.drop(1),
                    why = "行首不该有标点",
                    local = true,
                )
            }
            lineAt = lineEnd + 1
        }
        return dedupe(out)
    }

    /**
     * Variants of names the writer already stored (3+ characters, so a single
     * wrong character is a real signal rather than a common short word).
     */
    fun names(text: String, facts: List<MemoFact>): List<ProofIssue> {
        val names = facts
            .filter { it.enabled && it.name.length in 3..6 }
            .map { it.name.trim() }
            .filter { n -> n.all { it in HAN } }
            .distinct()
        if (names.isEmpty()) return emptyList()
        val out = ArrayList<ProofIssue>()
        for (name in names) {
            val n = name.length
            var i = 0
            while (i + n <= text.length) {
                val win = text.substring(i, i + n)
                if (win != name && win.all { it in HAN }) {
                    var diff = 0
                    for (k in 0 until n) if (win[k] != name[k]) diff++
                    if (diff == 1) {
                        out += ProofIssue(
                            kind = "错别字",
                            quote = win,
                            suggestion = name,
                            why = "记忆里的名字是「$name」",
                            local = true,
                        )
                    }
                }
                i++
            }
        }
        return dedupe(out)
    }

    /** The semantic pass. Sending the passage is the writer's explicit choice.
     *  Names are deliberately out of scope: the model has no authority over a
     *  character's spelling, and with no list it "fixed" 赵意年 into 赵意念 by
     *  frequency. 人名 is checked locally against the memory table instead. */
    fun prompt(
        text: String,
        names: List<String> = emptyList(),
        /** 故事圣经摘要（由调用方用 AiBible.summary 之类的入口拼好）。
         *  空 = 没有设定可比对，模型按提示词跳过「设定冲突」这一类，只做字面校核。 */
        bibleFacts: String = "",
    ): Pair<String, String> {
        val roster = if (names.isEmpty()) "" else
            "\n这本书的人名/地名（以这些写法为准，写成别的就是错别字）：" + names.joinToString("、")
        // 设定优先（3.26.23）：先拿故事圣经核对，对不上的算「设定」冲突，其余才是字面错。
        // 摘要为空时这一段整块不出现，调用方只传名字时校核仍是原来的字面三条。
        val bible = if (bibleFacts.isBlank()) "" else
            "\n\n这本书记下的设定（故事圣经摘要，以此为准）：\n" +
                bibleFacts.trim() + "\n" +
                "核对顺序：先判这一段和上面的设定 / 事件有没有冲突（人物、关系、时间、地点、已经发生过的事）；\n" +
                "有冲突的 level 填「设定」，reason 写明依据哪一条设定或事件；没有冲突的再按字面错报，\n" +
                "level 填「字面」，reason 一句话说清错在哪里。"
        val system = """
            你替作者把这一段查一遍，只做四件事：
            1 设定冲突：和给出来的故事圣经对不上的地方——人物写错、关系写反、时间或地点对不上、
              已经发生的事被当成没发生。没有给出设定就不报这一类。
            2 错别字：同音或形近写错（在/再、做/作、那/哪、已/以、帐/账），以及成语里的错字。
            3 符号：标点用错——半角标点混进中文、重复标点、行首标点、「...」代替省略号、
              引号或括号不配对、句末漏标点。
            4 语义：病句、指代不清（谁、哪个人）、逻辑跳跃、同一个意思说两遍、上下句接不上。$roster$bible
            每条只报一个层级：先判设定冲突（level=设定），不是冲突的按字面错（level=字面）。
            只输出 JSON 数组，不要输出任何别的文字。每项格式：
            {"quote":"原文片段","suggestion":"改后片段","kind":"错别字|符号|语义","level":"字面|设定","reason":"一句话"}
            规则：quote 必须从原文逐字复制，2 到 40 个字，不能含换行；suggestion 只改最少的字，保持原意；
            reason 不超过 40 字——设定冲突写依据哪条设定 / 事件，字面错写错在哪里；
            拿不准的不要报；没有问题就输出 []。
        """.trimIndent()
        val user = "原文：\n$text\n\n请输出 JSON 数组。"
        return system to user
    }

    /**
     * Split a passage for the model without cutting a paragraph in half when
     * it fits. A whole chapter is three requests, not one budget-burning one.
     */
    fun chunks(text: String, limit: Int = 1500): List<String> {
        if (text.length <= limit) return listOf(text)
        val out = ArrayList<String>()
        val buf = StringBuilder()
        for (p in text.split(Regex("\\n\\s*\\n"))) {
            if (p.isBlank()) continue
            if (buf.isNotEmpty() && buf.length + p.length + 2 > limit) {
                out += buf.toString()
                buf.setLength(0)
            }
            if (p.length > limit) {
                var from = 0
                while (from < p.length) {
                    val end = (from + limit).coerceAtMost(p.length)
                    out += p.substring(from, end)
                    from = end
                }
            } else {
                if (buf.isNotEmpty()) buf.append("\n\n")
                buf.append(p)
            }
        }
        if (buf.isNotEmpty()) out += buf.toString()
        return out.ifEmpty { listOf(text) }
    }

    /** Tolerant parse: models sometimes wrap the array in prose or fences. */
    fun parse(raw: String): List<ProofIssue> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arr = runCatching { JSONArray(raw.substring(start, end + 1)) }.getOrNull()
            ?: return emptyList()
        val out = ArrayList<ProofIssue>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val quote = o.optString("quote").trim()
            val suggestion = o.optString("suggestion").trim()
            if (quote.length !in 2..60 || suggestion.isEmpty() || quote == suggestion) continue
            val rawKind = o.optString("kind")
            val kind = when {
                rawKind.contains("字") -> "错别字"
                rawKind.contains("符") -> "符号"
                else -> "语义"
            }
            // 设定冲突（3.26.23）：level 缺失 / 乱写时按字面处理，旧格式照常解析。
            val rawLevel = o.optString("level")
            val level = if (
                rawLevel.contains("设定") || rawLevel.contains("圣经") ||
                rawLevel.contains("冲突")
            ) "设定" else "字面"
            out += ProofIssue(
                kind = kind,
                quote = quote,
                suggestion = suggestion,
                why = o.optString("why").trim().take(80),
                local = false,
                reason = o.optString("reason").trim().take(120),
                level = level,
            )
        }
        return dedupe(out)
    }

    private fun dedupe(list: List<ProofIssue>): List<ProofIssue> {
        val seen = HashSet<String>()
        val out = ArrayList<ProofIssue>(list.size)
        for (i in list) {
            // Same quote fixed the same way twice is noise; a different fix for
            // the same quote is a real alternative the writer may want.
            val key = i.quote + "\u0000" + i.suggestion
            if (seen.add(key)) out += i
        }
        return out
    }
}
