package com.moyu.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 「问」的系统提示：只针对选中那一段，不越界、不假装改过正文。
 */
const val ASK_SYSTEM =
    "你是作者的写作助手，只处理他选中的那一小段正文。回答用中文，简短、具体，不要说客套话。\n" +
        "作者问问题时，直接答；作者要一版改写时，只输出改写后的那一段文字本身，" +
        "不要前言、不要解释、不要引号、不要用「好的，以下是…」开头。\n" +
        "你看不到这本书的其他章节，只看到【选中】和【紧接在上文】。被问到别处时，直说这一轮看不到。" +
        "你从来不自己改正文：改不改由作者点「替换选中」决定。"

/**
 * 选段「问」的一条消息（3.26.32）。
 *
 * `applied` = 这条回答已经替换回正文；`stale` = 正文在这期间被改过，回答作废。
 * 只活在内存里，不进 moyu.json。
 */
data class AskTurn(
    val id: String,
    val mine: Boolean,
    val text: String,
    val applied: Boolean = false,
    val stale: Boolean = false,
)

/**
 * 选段「问」的内容区：塞进校核/修饰那块右侧浮窗里（`ProofSidePanel(content = …)`）。
 *
 * 顶部是选区预览（三行，点一下展开），中间是消息流，底部是输入行。
 * 每条 AI 回答下面只有「替换选中」——默认不写正文，写了也能一键撤销。
 */
@Composable
fun ColumnScope.AskChat(
    preview: String,
    chars: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    turns: List<AskTurn>,
    input: String,
    onInput: (String) -> Unit,
    busy: Boolean,
    error: String?,
    canWrite: Boolean,
    onSend: () -> Unit,
    onApply: (AskTurn) -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val list = rememberLazyListState()
    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) list.animateScrollToItem(turns.lastIndex)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggleExpand() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Txt(
            preview,
            size = 10.5f,
            color = p.muted,
            lineHeight = 15f,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Txt(
            if (expanded) "收起" else "$chars 字",
            size = 9f,
            color = p.muted.copy(alpha = .7f),
        )
    }
    RowDivider(inset = 13.dp)

    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        state = list,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 13.dp,
            vertical = 10.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (turns.isEmpty()) {
            item {
                Txt(
                    "就这一段问一句：更冷一点 / 换个开头 / 这里节奏是不是太快",
                    size = 10.5f,
                    color = p.muted,
                    lineHeight = 16f,
                )
            }
        }
        items(turns, key = { it.id }) { t ->
            if (t.mine) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier
                            .fillMaxWidth(.88f)
                            .clip(SmoothCorner(12.dp))
                            .background(p.faint)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Txt(t.text, size = 11f, color = p.ink, lineHeight = 16f)
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Txt(t.text, size = 11.5f, color = p.ink, lineHeight = 17f)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            t.applied -> Txt("已替换选中", size = 9f, color = p.muted)
                            t.stale -> Txt("这段已经改过，这条作废", size = 9f, color = p.muted)
                            else -> AskPill("替换选中", canWrite) { onApply(t) }
                        }
                    }
                }
            }
        }
        if (busy) {
            item { Txt("正在想…", size = 10f, color = p.muted) }
        }
        if (error != null) {
            item { Txt(error, size = 10f, color = p.muted, lineHeight = 15f) }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            InkField(
                value = input,
                onValueChange = onInput,
                placeholder = "问问这一段…",
                singleLine = false,
                minHeight = 38.dp,
                textSize = 12f,
                maxLines = 3,
            )
        }
        Spacer(Modifier.width(7.dp))
        val on = !busy && input.isNotBlank()
        Box(
            Modifier
                .height(34.dp)
                .clip(SmoothCorner(12.dp))
                .background(if (on) ui.accent else p.faint)
                .bouncy(enabled = on) { if (on) onSend() }
                .padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Txt(
                if (busy) "…" else "发送",
                size = 10.5f,
                color = if (on) p.paper else p.muted,
            )
        }
    }
}

@Composable
private fun AskPill(text: String, enabled: Boolean, onClick: () -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(
        Modifier
            .height(26.dp)
            .clip(SmoothCorner(10.dp))
            .background(if (enabled) p.faint else p.faint.copy(alpha = .5f))
            .bouncy(enabled = enabled, scale = .94f) { if (enabled) onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, size = 9.5f, color = if (enabled) p.ink else p.muted)
    }
}
