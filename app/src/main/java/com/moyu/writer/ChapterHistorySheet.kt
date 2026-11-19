package com.moyu.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 章节历史面板（3.26.45）。
 *
 * 数据源是现成的 [ChapterHistory]：每章写前快照落在 `filesDir/history/<chapterId>/`，
 * 文件名就是 `<时间戳>_<字数>.txt`。这里只做三件事：
 * ① 列出这一章的版本（一行「时间 · 字数」）；
 * ② 点一行预览那一版正文；
 * ③ 「恢复这一版」——先把**当前正文**再留一份底，再交给调用方写回去。
 *
 * **恢复路径**：[ChapterHistory] 目前只有 `record / list / read / clear`，没有专门的
 * restore。所以恢复 = `read` 出目标版本 → `record` 当前正文（留底失败就放弃）→
 * `onRestore(text)` 由调用方走编辑器自己的替换/撤销栈写回。TODO(3.26.45)：如果以后
 * 给 `ChapterHistory` 加了 restore 入口，这段可以收进去，面板就只剩调它一行。
 *
 * **本轮不接线**（Editor/Library 不能动，入口由 Lead 之后接）：
 * ```
 * var historyOpen by remember { mutableStateOf(false) }
 * if (historyOpen) ChapterHistorySheet(
 *     chapterId = seedKey,                 // 章节页的 chapter.id
 *     chapterTitle = title,
 *     currentText = body.text,
 *     onRestore = { text ->
 *         // 整章替换：走正文自己的 undo 栈（原生 watcher 会记撤销条目）。
 *         bodyEditor?.replaceKeepingViewport(0, body.text.length, text)
 *     },
 *     onDismiss = { historyOpen = false },
 * )
 * ```
 * 注意：面板在调 `onRestore` **之前**已经用 [ChapterHistory.record] 把当前正文留了一份底，
 * 接线时不要在 onRestore 里再 record 一次，否则一次恢复会多出两条一样的版本。
 *
 * @param currentText 调用方此刻的正文（按编辑器当前内容传，未保存的改动也要算）。
 * @param canRestore false = 只看历史。只读档之类的策略由调用方决定。
 * @param onRestore 把这一版正文写回。面板只负责读历史 + 留底，真正的写入必须走调用方。
 */
@Composable
fun ChapterHistorySheet(
    chapterId: String,
    chapterTitle: String,
    currentText: String,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit,
    canRestore: Boolean = true,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val appCtx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    var versions by remember(chapterId) {
        mutableStateOf<List<ChapterHistory.Version>>(emptyList())
    }
    var loading by remember(chapterId) { mutableStateOf(true) }
    var selectedTs by remember { mutableStateOf<Long?>(null) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<ChapterHistory.Version?>(null) }
    var restoring by remember { mutableStateOf(false) }

    LaunchedEffect(chapterId) {
        versions = withContext(Dispatchers.IO) { ChapterHistory.list(appCtx, chapterId) }
        loading = false
    }

    // 点哪一版读哪一版；正文可能上万字，放 IO 读，不卡面板。
    LaunchedEffect(selectedTs) {
        val ts = selectedTs
        if (ts == null) {
            previewText = null
            previewLoading = false
            return@LaunchedEffect
        }
        previewLoading = true
        previewText = withContext(Dispatchers.IO) { ChapterHistory.read(appCtx, chapterId, ts) }
        previewLoading = false
    }

    InlineModalSheet(onDismiss = onDismiss) { close ->
        DialogTitle(
            "这一章的历史",
            "${chapterTitle.ifBlank { "未命名" }} · " +
                if (loading) "正在读…" else "共 ${versions.size} 版",
            onClose = close,
        )
        Spacer(Modifier.height(4.dp))
        Txt(
            "每一版都是改正文之前自动留的底。点一行看正文，确认后再写回去。",
            size = 10.5f,
            color = p.muted,
            lineHeight = 16f,
        )
        Spacer(Modifier.height(10.dp))

        if (!loading && versions.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Txt("这一章还没有历史版本。", size = 13f, color = p.ink)
            Spacer(Modifier.height(6.dp))
            Txt(
                "AI 改正文之前会自动在这里存一份（每章最多 20 份）；改过几次之后，" +
                    "就能从这里挑一版退回去。",
                size = 11f,
                color = p.muted,
                lineHeight = 18f,
            )
            Spacer(Modifier.height(6.dp))
        }

        versions.forEach { v ->
            val selected = v.ts == selectedTs
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(SmoothCorner(14.dp))
                    .background(if (selected) p.faint else Color.Transparent),
            ) {
                // 行点击用项目统一的 bouncy（按压水滴，同设置行/列表行）。
                SettingRow(
                    // 3.26.57：自动存档的条目在原有的「时间 · 字数」后面挂一个短标记，
                    // 复用同一行文字、不加图标/颜色块（自动条目不是作者主动留的底）。
                    title = historyTime(v.ts) + " · " + v.words + " 字" +
                        if (v.manual) "" else " · 自动",
                    glyph = Glyph.Clock,
                    onClick = { selectedTs = if (selected) null else v.ts },
                    trailing = {
                        Txt(
                            if (selected) "收起" else "预览",
                            size = 9.5f,
                            color = p.muted,
                        )
                    },
                )
                if (selected) {
                    MoyuSurface(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        radius = 14f,
                        flat = true,
                    ) {
                        Box(Modifier.padding(12.dp)) {
                            Txt(
                                when {
                                    previewLoading -> "正在读…"
                                    previewText == null -> "这一版读不出来了。"
                                    previewText!!.isBlank() -> "（这一版是空白）"
                                    else -> previewText.orEmpty()
                                },
                                size = 10.5f,
                                color = p.muted,
                                lineHeight = 16.5f,
                                maxLines = 14,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        if (versions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            RowDivider(inset = 0.dp)
            Spacer(Modifier.height(14.dp))
            val picked = versions.firstOrNull { it.ts == selectedTs }
            PrimaryButton(
                label = if (restoring) "正在恢复…" else "恢复这一版",
                onClick = { picked?.let { confirming = it } },
                modifier = Modifier.fillMaxWidth(),
                glyph = Glyph.Undo,
                enabled = canRestore && !restoring && picked != null,
            )
            Spacer(Modifier.height(7.dp))
            Txt(
                when {
                    !canRestore -> "只读档：这一步不改正文，只能看。"
                    picked == null -> "先点上面一行预览，再决定恢复哪一版。"
                    else -> "恢复前会先把现在的正文存一份，恢复错了还能再退回来。"
                },
                size = 9.5f,
                color = p.muted,
                lineHeight = 14f,
            )
        }

        // 恢复会覆盖整章正文，先确认一次（项目里拆/合章这类重动作都走确认框）。
        confirming?.let { v ->
            ConfirmDialog(
                title = "恢复这一版？",
                message = "把 ${historyTime(v.ts)} 那一版（${v.words} 字）写回正文。" +
                    "现在的正文会先存进历史，还能再退回来。",
                confirmLabel = "恢复",
                onConfirm = {
                    confirming = null
                    if (restoring) return@ConfirmDialog
                    restoring = true
                    scope.launch {
                        // 目标版正文 + 当前正文的留底，一起在 IO 上做。
                        val (text, saved) = withContext(Dispatchers.IO) {
                            val body = ChapterHistory.read(appCtx, chapterId, v.ts)
                            val ok = body != null &&
                                ChapterHistory.record(appCtx, chapterId, currentText)
                            body to ok
                        }
                        restoring = false
                        when {
                            text == null -> ToastBus.show("这一版读不出来了，没恢复")
                            !saved -> ToastBus.show("没能留底，这次不恢复")
                            else -> {
                                onRestore(text)
                                ToastBus.show("已恢复这一版，可以用撤销退回")
                                close()
                            }
                        }
                    }
                },
                onDismiss = { confirming = null },
            )
        }
    }
}

/**
 * 「时间」那一栏：今天 / 昨天 / 具体日期。
 *
 * 历史版本基本都是当天或前一两天的，写「今天 20:14」比「11-19 20:14」更容易读；
 * 跨年才补上年份（同一年内只给月-日）。
 */
private fun historyTime(ts: Long): String {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val that = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val days = ((today.timeInMillis - that.timeInMillis) / 86_400_000L).toInt()
    val clock = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ts))
    return when {
        days <= 0 -> "今天 $clock"
        days == 1 -> "昨天 $clock"
        that.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
            SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ts))
        else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ts))
    }
}
