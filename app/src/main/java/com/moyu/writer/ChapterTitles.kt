package com.moyu.writer

/**
 * Auto-generated chapter names use Chinese numerals, like a printed book:
 * 第一章 / 第二章 / 第十章 / 第十一章. The old code used the list index for the
 * first chapter (`第一章`) but a plain number afterwards (`第2章`), so a book
 * jumped from Chinese to Arabic at chapter two.
 *
 * Only used for names the app generates. A title the writer typed is never
 * rewritten.
 */
fun chineseNumber(value: Int): String {
    if (value <= 0 || value >= 10000) return value.toString()
    val digits = "零一二三四五六七八九"
    val units = arrayOf("", "十", "百", "千")
    val out = StringBuilder()
    var pendingZero = false
    var started = false
    var scale = 1000
    var pos = 3
    while (scale >= 1) {
        val digit = (value / scale) % 10
        if (digit == 0) {
            if (started) pendingZero = true
        } else {
            if (pendingZero) {
                out.append('零')
                pendingZero = false
            }
            out.append(digits[digit]).append(units[pos])
            started = true
        }
        scale /= 10
        pos--
    }
    val text = out.toString()
    // 10–19 read 十/十一, not 一十/一十一.
    return if (text.startsWith("一十")) text.substring(1) else text
}

/** Fallback title for the chapter at zero-based [index]. */
fun chapterFallbackTitle(index: Int): String = "第${chineseNumber(index + 1)}章"
