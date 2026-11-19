package com.moyu.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * AI 回答的排版（3.26.33）。
 *
 * 模型回来的文本带 markdown：`**重点**`、`- 一条`、`1. 步骤`、`> 引用`。
 * 之前两处（问书气泡、编辑器「问」浮窗）都是 `Txt(msg.text)` 原样画，用户看到
 * 的是字面的星号和短横（用户原话：「** 和 - 没有换成实际的文字效果」）。
 *
 * 这里只做阅读需要的四种：粗体 / 无序列表 / 有序列表 / 引用，外加标题降级成粗体。
 * 不做完整 markdown：没有表格、没有链接、没有代码块——AI 在本 App 里产出的
 * 文本就是这几类；多做的每一种都要在窄气泡里重新排版一次，不划算。
 */
@Composable
fun AiMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    size: Float = 13.5f,
    color: Color = LocalInk.current.palette.ink,
    lineHeight: Float = size * 1.55f,
) {
    val p = LocalInk.current.palette
    val lines = text.trim().split('\n')
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (raw in lines) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(2.dp))
                HEADING.matches(line) -> Txt(
                    inline(line.replace(HEADING, "").trim(), FontWeight.SemiBold),
                    size = size + 1.5f,
                    color = color,
                    lineHeight = lineHeight + 2f,
                )
                line.startsWith("> ") -> Row(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(16.dp)
                            .clip(CircleShape)
                            .background(p.line),
                    )
                    Spacer(Modifier.width(8.dp))
                    Txt(
                        inline(line.removePrefix("> "), FontWeight.Normal),
                        size = size - .5f,
                        color = p.muted,
                        lineHeight = lineHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
                BULLET.matches(line) -> Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Txt("·", size = size, color = p.muted, modifier = Modifier.width(12.dp))
                    Txt(
                        inline(line.replace(BULLET, ""), FontWeight.Normal),
                        size = size,
                        color = color,
                        lineHeight = lineHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
                ORDERED.find(line) != null -> {
                    val m = ORDERED.find(line)!!
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Txt(
                            m.groupValues[1] + ".",
                            size = size,
                            color = p.muted,
                            modifier = Modifier.width(20.dp),
                        )
                        Txt(
                            inline(line.substring(m.range.last + 1).trim(), FontWeight.Normal),
                            size = size,
                            color = color,
                            lineHeight = lineHeight,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                else -> Txt(
                    inline(line, FontWeight.Normal),
                    size = size,
                    color = color,
                    lineHeight = lineHeight,
                )
            }
        }
    }
}

private val HEADING = Regex("^#{1,6}\\s+")
private val BULLET = Regex("^[-*·]\\s+")
private val ORDERED = Regex("^(\\d{1,2})[.)]\\s+")

/** 行内 `**粗体**`；`*斜体*` 与反引号交给 [mdPlain] 在写回正文时去掉，这里只去标记。 */
private fun inline(raw: String, base: FontWeight): AnnotatedString {
    val s = raw.replace("`", "")
    if (!s.contains("**")) return AnnotatedString(s, spanStyle = SpanStyle(fontWeight = base))
    return buildAnnotatedString {
        var i = 0
        while (i < s.length) {
            val open = s.indexOf("**", i)
            if (open < 0) {
                withStyle(SpanStyle(fontWeight = base)) { append(s.substring(i)) }
                break
            }
            val close = s.indexOf("**", open + 2)
            if (close < 0) {
                withStyle(SpanStyle(fontWeight = base)) { append(s.substring(i)) }
                break
            }
            if (open > i) withStyle(SpanStyle(fontWeight = base)) { append(s.substring(i, open)) }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(s.substring(open + 2, close))
            }
            i = close + 2
        }
    }
}

/**
 * 去掉 markdown 标记，得到能写进正文的纯文字。
 *
 * 「替换选中」必须用它：把 `**他推开门**` 原样写进小说，等于把标记留在稿子里。
 */
fun mdPlain(raw: String): String = raw.lineSequence()
    .map { l ->
        var s = l.trim()
        s = s.replace(HEADING, "")
        s = s.replace(BULLET, "")
        s = s.replace(ORDERED, "")
        s = s.replace(Regex("^>\\s*"), "")
        s
    }
    .filter { it.isNotBlank() }
    .joinToString("\n")
    .replace("**", "")
    .replace("`", "")
    .trim()
