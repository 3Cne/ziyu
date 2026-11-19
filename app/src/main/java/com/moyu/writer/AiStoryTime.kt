package com.moyu.writer

/**
 * 3.26.45：故事内时间的规范化（`set_story_time` 用）。
 *
 * 时间线（故事圣经 → 时间线）只按事件里的 `whenText` 排序，而 `AiBibleSync` 只认
 * `YYYY-MM-DD` / `M月D日` / `N年前|后` / `第N天` 这几种形态；「当天夜里」「傍晚」
 * 这类解析不了的说法一律不写、界面显示「时间不详」。这个工具走同一套规则，
 * 免得 AI 一行人话把时间线写成乱序。
 *
 * 注意：`AiBibleSync.normalizeWhenText` 与 `StoryBible.normalizeStoryTime` 都是
 * private，且这两个文件不归本轮改，所以这里各自实现同一套判定；改规则时三处一起看。
 */
object AiStoryTime {
    /** 写进提示词/错误message 的可用形态。 */
    const val FORMS = "只接受 YYYY-MM-DD、M月D日、N年前|后 或 第N天"

    private val isoDate = Regex("(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*[日号]?")
    private val monthDay = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?")
    private val relative = Regex("([0-9]+|[零一二两三四五六七八九十百千]+)\\s*(个月|年|月|天|日|周|星期)\\s*(?:以|之)?\\s*(前|后)")
    private val dayIndex = Regex("第\\s*([0-9]+|[零一二两三四五六七八九十百千]+)\\s*[天日]")

    /**
     * 把模型给的时间压成可排序的规范形态；解析不了返回 null（调用方不写这个字段）。
     */
    fun normalize(raw: String): String? {
        val text = normalizeWidth(raw.trim())
        if (text.isEmpty()) return null
        isoDate.find(text)?.let { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return@let
            val month = m.groupValues[2].toIntOrNull() ?: return@let
            val day = m.groupValues[3].toIntOrNull() ?: return@let
            if (year < 1 || month !in 1..12 || day !in 1..31) return@let
            return "%04d-%02d-%02d".format(java.util.Locale.US, year, month, day)
        }
        monthDay.find(text)?.let { m ->
            val month = m.groupValues[1].toIntOrNull() ?: return@let
            val day = m.groupValues[2].toIntOrNull() ?: return@let
            if (month !in 1..12 || day !in 1..31) return@let
            return month.toString() + "月" + day + "日"
        }
        relative.find(text)?.let { m ->
            val n = numberValue(m.groupValues[1]) ?: return@let
            if (n <= 0) return@let
            return n.toString() + m.groupValues[2] + m.groupValues[3]
        }
        dayIndex.find(text)?.let { m ->
            val n = numberValue(m.groupValues[1]) ?: return@let
            if (n <= 0) return@let
            return "第" + n + "天"
        }
        return null
    }

    /** 中文输入法常把 `-` `/` `.` 和数字打成全角，判定前先归一成 ASCII。 */
    private fun normalizeWidth(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            sb.append(
                when (c) {
                    '－', '—', '–', '−' -> '-'
                    '／' -> '/'
                    '．', '。' -> '.'
                    '　' -> ' '
                    in '０'..'９' -> '0' + (c - '０')
                    else -> c
                },
            )
        }
        return sb.toString()
    }

    private fun numberValue(raw: String): Int? = raw.trim().toIntOrNull() ?: chineseNumber(raw.trim())

    private fun chineseNumber(text: String): Int? {
        if (text.isEmpty()) return null
        val digits = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        var section = 0
        var number = 0
        for (c in text) {
            when {
                digits.containsKey(c) -> number = digits.getValue(c)
                c == '十' -> {
                    section += (if (number == 0) 1 else number) * 10
                    number = 0
                }
                c == '百' -> {
                    section += (if (number == 0) 1 else number) * 100
                    number = 0
                }
                c == '千' -> {
                    section += (if (number == 0) 1 else number) * 1000
                    number = 0
                }
                else -> return null
            }
        }
        return section + number
    }
}
