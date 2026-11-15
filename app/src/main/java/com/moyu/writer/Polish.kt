package com.moyu.writer

import org.json.JSONArray

/**
 * 修饰：优化描写，不是挑错。
 *
 * 用户的定调（2026-11-14 修正）：校核管错别字与符号；修饰管「把描写写出画面」。
 * 允许重组整段、增删句子，但事件与人不能变。作者点一个版本才替换正文。
 *
 * 2026-11-14 v3.20.0：改成和校核同一条流水线。原来一次只肯收 600 字，而且
 * 一张卡＝整段替换——全章 2311 字时模型只改写第一段，采纳就把整章换成一段
 * （3.15.0 实测踩过）。现在按块切：一块一次请求、一块一张卡，采纳只替换
 * 它自己那块。所以不再限字数，风险也不随字数长。
 */
object Polishing {

    /** 一次请求覆盖的字数。600 是「描写优化」能稳定改完的长度。 */
    const val CHUNK = 600

    /** 切块结果：text 是原文的逐字子串，start 是它在这段里的偏移。 */
    data class Chunk(val start: Int, val text: String)

    /**
     * 切块：在安全边界上切，绝不从句子中间断开。
     *
     * 优先段间空行（`\n\n`），其次句末标点（连同紧跟的右引号/右括号），
     * 再其次换行，实在找不到才硬切。块与块之间的空白留给正文，不改。
     */
    fun chunks(text: String, limit: Int = CHUNK): List<Chunk> {
        if (text.isBlank()) return emptyList()
        if (text.length <= limit) return listOf(Chunk(0, text))
        val out = ArrayList<Chunk>()
        var start = 0
        while (start < text.length) {
            if (text.length - start <= limit) {
                out += Chunk(start, text.substring(start))
                break
            }
            val until = (start + limit).coerceAtMost(text.length)
            val cut = safeCut(text, start, until)
            out += Chunk(start, text.substring(start, cut))
            start = cut
        }
        return out
    }

    private fun safeCut(text: String, from: Int, until: Int): Int {
        // 1) 段间空行：切在第二个 \n 之后，下一块从新段开头起。
        var i = until
        while (i > from + 1) {
            if (text[i - 1] == '\n' && text[i - 2] == '\n') return i
            i--
        }
        // 2) 句末标点：切在标点之后，并把紧跟的右引号/右括号带上。
        val ends = "。！？…”』」）)!?.…"
        i = until
        while (i > from) {
            if (text[i - 1] in ends) {
                var j = i
                while (j < text.length && text[j] in "”』」）)\"") j++
                return j
            }
            i--
        }
        // 3) 换行。
        i = until
        while (i > from) {
            if (text[i - 1] == '\n') return i
            i--
        }
        // 4) 一整块都没有边界（没有标点的长句）：硬切。until > from，所以一定前进。
        return until
    }

    fun prompt(text: String): Pair<String, String> {
        val system = """
你是中文小说编辑。这一趟不挑错，只做一件事：把这段的描写写出来。
把该看见的东西写出来：器物、光、温度、声音、气味、手上的动作、身体的反应、空间的远近。
把形容词堆砌换成能看见的东西；把空泛的概括（「他很悲伤」「气氛很压抑」）换成动作与细节；
把一口气的长句断开，让句子的长短有起伏。
可以重组这一段、可以增删句子、可以调段落内部的顺序。但：事件与因果不能变，人物、时间、地点不能变，
对话内容不能改（可以改对话前后的动作与神态）。
不要写成华丽散文：宁可具体，不要漂亮；不堆比喻，不抒情总结，不在段尾升华，不用「仿佛」「宛如」起头的比喻。
判断标准是「读完能看见那个场面」，不是「文笔好不好」。
只改这一段，不要顺带改别的段落，也不要缩短到只剩一句。
输出 JSON 数组，1 个版本；每项：{"suggestion":"改写后的整段","why":"一句为什么这么改"}。
规则：不要自己加换行——段落之间只用一个换行，行首不要加空格或全角空格（缩进由程序按原文补回）；
不要用 markdown，不要代码块。
没有可改的就输出 []。不要解释，不要代码块。
""".trimIndent()
        val user = "原文：\n$text\n\n请输出 JSON 数组。"
        return system to user
    }

    /**
     * 把模型输出套回原文的格式（2026-11-14 用户报：修饰擅自加换行、行首缩进全丢）。
     *
     * 原文一段 → 全部并回一段，消灭句中硬换行；
     * 原文多段 → 保留模型的分段，但每段补回原文的行首前缀（通常是两个全角空格）；
     * 原文没有统一前缀时，按「自动缩进」设置决定补 `　　` 还是什么都不补。
     */
    fun fitFormat(original: String, suggestion: String, autoIndent: Boolean): String {
        val origParas = original.split('\n').filter { it.isNotBlank() }
        val prefix = origParas
            .map { line -> line.takeWhile { it.isWhitespace() } }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key
            ?: if (autoIndent) "\u3000\u3000" else ""
        val blocks = suggestion
            .replace("\r\n", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (blocks.isEmpty()) return suggestion.trim()
        val paras = if (origParas.size <= 1) listOf(blocks.joinToString("")) else blocks
        return paras.joinToString("\n") { prefix + it }
    }

    /**
     * 把模型输出变成浮窗里的卡片。一块只收一张：quote 是这一块原文，
     * 采纳只替换这一块；位置由调用方补（相对正文的绝对偏移）。
     */
    fun parse(raw: String, original: String, autoIndent: Boolean = false): List<ProofIssue> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arr = runCatching { JSONArray(raw.substring(start, end + 1)) }.getOrNull() ?: return emptyList()
        val out = ArrayList<ProofIssue>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val rawSuggestion = o.optString("suggestion").trim()
            if (rawSuggestion.isEmpty()) continue
            // 先套回原文格式，再算长度比例：换行与缩进不该影响「有没有被截断」的判断。
            val suggestion = fitFormat(original, rawSuggestion, autoIndent)
            if (suggestion == original.trim()) continue
            // 长度闸门：只拦「截断」。描写优化本来就会变长，所以上限放到 3 倍；
            // 下限 0.45 是防模型只交半段（那会吃掉正文）。
            val ratio = suggestion.length.toFloat() / original.length.coerceAtLeast(1)
            if (ratio < .45f || ratio > 3f) continue
            out += ProofIssue(
                kind = "修饰",
                quote = original,
                suggestion = suggestion,
                why = o.optString("why").trim(),
                local = false,
            )
        }
        return out.distinctBy { it.suggestion }.take(1)
    }
}
