package com.moyu.writer

import org.json.JSONArray

/**
 * One suggested fix for a selected passage.
 *
 * [quote] is the exact text copied out of the body; [suggestion] replaces it
 * verbatim. Nothing is applied automatically — the writer accepts each line.
 */
data class ProofIssue(
    val kind: String,
    val quote: String,
    val suggestion: String,
    val why: String,
    val local: Boolean,
    val id: String = java.util.UUID.randomUUID().toString().take(8),
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
    /** One model request covers a passage of this size (the design's 1500–2500;
     *  the writer's own chapters run to ~2900, so the whole chapter fits). */
    const val MAX_INPUT = 3000

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
    fun prompt(text: String, names: List<String> = emptyList()): Pair<String, String> {
        val roster = if (names.isEmpty()) "" else
            "\n这本书的人名/地名（以这些写法为准，写成别的就是错别字）：" + names.joinToString("、")
        val system = """
            你替作者把这一段查一遍，只做三件事：
            1 错别字：同音或形近写错（在/再、做/作、那/哪、已/以、帐/账），以及成语里的错字。
            2 符号：标点用错——半角标点混进中文、重复标点、行首标点、「...」代替省略号、
              引号或括号不配对、句末漏标点。
            3 语义：病句、指代不清（谁、哪个人）、逻辑跳跃、同一个意思说两遍、上下句接不上。$roster
            只输出 JSON 数组，不要输出任何别的文字。每项格式：
            {"quote":"原文片段","suggestion":"改后片段","kind":"错别字|符号|语义","why":"一句话说明"}
            规则：quote 必须从原文逐字复制，2 到 40 个字，不能含换行；suggestion 只改最少的字，保持原意；
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
    fun parse(raw: String): List<ProofIssue> {        val start = raw.indexOf('[')
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
            out += ProofIssue(
                kind = kind,
                quote = quote,
                suggestion = suggestion,
                why = o.optString("why").trim().take(80),
                local = false,
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
