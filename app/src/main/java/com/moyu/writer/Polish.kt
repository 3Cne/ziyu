package com.moyu.writer

import org.json.JSONArray

/**
 * 修饰：优化描写，不是挑错。
 *
 * 用户的定调（2026-11-14 修正）：校核管错别字与符号；修饰管「把描写写出画面」。
 * 允许重组整段、增删句子，但事件与人不能变。作者点一个版本才替换正文。
 */
object Polishing {

    /**
     * 修饰一次最多 600 字。
     *
     * 不能给到 3000：实测全章 2311 字时模型只改写了第一段，而采纳是按
     * 「整段替换」落地的，等于把整章换成一段。修饰本来就是一两段的事，
     * 超出就请作者先选小一段，比冒险替换整章安全。
     */
    const val MAX_INPUT = 600

    /** 短段给 2 个版本（收着改 / 放开改），长段只留 1 个，免得输出超预算。 */
    fun versionCount(chars: Int): Int = if (chars <= 900) 2 else 1

    fun prompt(text: String, versions: Int): Pair<String, String> {
        val system = """
你是中文小说编辑。这一趟不挑错，只做一件事：把这段的描写写出来。
把该看见的东西写出来：器物、光、温度、声音、气味、手上的动作、身体的反应、空间的远近。
把形容词堆砌换成能看见的东西；把空泛的概括（「他很悲伤」「气氛很压抑」）换成动作与细节；
把一口气的长句断开，让句子的长短有起伏。
可以重组这一段、可以增删句子、可以调段落内部的顺序。但：事件与因果不能变，人物、时间、地点不能变，
对话内容不能改（可以改对话前后的动作与神态）。
不要写成华丽散文：宁可具体，不要漂亮；不堆比喻，不抒情总结，不在段尾升华，不用「仿佛」「宛如」起头的比喻。
判断标准是「读完能看见那个场面」，不是「文笔好不好」。
输出 JSON 数组，$versions 个版本，从改动小到大；每项：{"suggestion":"改写后的整段","why":"一句为什么这么改"}。
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

    /** 把模型输出变成浮窗里的卡片；quote 一律是原文整段，采纳时整段替换。 */
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
            // 下限 0.5 是防模型只交半段（那会吃掉正文）。
            val ratio = suggestion.length.toFloat() / original.length.coerceAtLeast(1)
            if (ratio < .5f || ratio > 3f) continue
            out += ProofIssue(
                kind = "修饰",
                quote = original,
                suggestion = suggestion,
                why = o.optString("why").trim(),
                local = false,
            )
        }
        return out.distinctBy { it.suggestion }.take(3)
    }
}
