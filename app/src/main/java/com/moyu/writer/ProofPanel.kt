package com.moyu.writer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 校核浮窗。和「本章笔记」共用同一套右侧浮窗形态：平卡、20 圆角、标题行、
 * 竖排卡片、可拖宽；不再是塞在底部能力栏里的两行小字。
 *
 * 打开时写作列让出浮窗宽度（正文按帧重排），关闭后选区回到打开时那一段。
 * 采纳 / 忽略是卡片底部两个等宽药丸，34dp 高，点完卡片先退场再落库。
 */
@Composable
fun ProofSidePanel(
    visible: Boolean,
    issues: List<ProofIssue>,
    busy: Boolean,
    status: String?,
    error: String?,
    asked: Boolean,
    chars: Int,
    canAsk: Boolean,
    onAsk: () -> Unit,
    onAccept: (ProofIssue) -> Unit,
    onIgnore: (ProofIssue) -> Unit,
    onLocate: (ProofIssue) -> Unit,
    onClose: () -> Unit,
    /** 拖浮窗边缘时清掉正文焦点，避免正文光标跟着选中。 */
    onClearEditorFocus: (() -> Unit)? = null,
    /** 宽度动画停稳后放开正文的滚动锚点。 */
    onWidthSettled: (() -> Unit)? = null,
    /** 宽度开始变化时先钉住正文位置，重排不把页面拽走。 */
    onWidthAnimating: (() -> Unit)? = null,
    /** 开始拖宽度前再抓一次锚点。 */
    onResizeStart: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val density = LocalDensity.current
    val motion = ui.settings.motion != "none"
    val screenPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    // 窄屏（<360dp）上 190dp 可能比 62% 还宽，coerceIn 会拿到空区间；
    // 先把下限压到屏宽的 45%，保证任何屏宽都有一档可用的宽度。
    val minPx = with(density) { 168.dp.toPx() }.coerceAtMost(screenPx * .45f)
    val maxPx = (screenPx * .62f).coerceAtLeast(minPx + 1f)
    // 校核的建议比笔记标题长，默认比笔记窗略宽；宽度只在本次会话里记，
    // 不新增设置字段。
    var widthPx by remember {
        mutableFloatStateOf((screenPx * .50f).coerceIn(minPx, maxPx))
    }
    val animatedWidthPx by animateFloatAsState(
        targetValue = if (visible) widthPx else 0f,
        animationSpec = if (!motion) {
            tween(0)
        } else if (visible) {
            spring(dampingRatio = .78f, stiffness = 340f, visibilityThreshold = .5f)
        } else {
            tween(200, easing = FastOutSlowInEasing)
        },
        visibilityThreshold = .5f,
        label = "proofPanelWidth",
    )
    val latestSettled = rememberUpdatedState(onWidthSettled)
    val latestAnimating = rememberUpdatedState(onWidthAnimating)
    val latestResizeStart = rememberUpdatedState(onResizeStart)
    // 正文重排在宽度动画的每一帧都会发生；锚点要活到最后一帧之后。
    LaunchedEffect(visible) {
        latestAnimating.value?.invoke()
        delay(if (visible) 620 else 460)
        latestSettled.value?.invoke()
    }
    // 采纳 / 忽略先让卡片退场（180ms），再真正改正文、收列表。
    val exiting = remember { mutableStateListOf<String>() }
    val proofList = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun leave(issue: ProofIssue, apply: (ProofIssue) -> Unit) {
        if (exiting.contains(issue.id)) return
        exiting += issue.id
        scope.launch {
            delay(if (motion) 170 else 0)
            apply(issue)
        }
    }
    LaunchedEffect(visible) {
        if (!visible) exiting.clear()
    }

    AnimatedVisibility(
        visible = visible || animatedWidthPx > 1f,
        modifier = modifier,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(140)),
    ) {
        // 内容和宽度一起长出来：开头 14dp 的位移动画把「从右边进来」说清楚，
        // 关的时候反向缩回去。
        val reveal = (animatedWidthPx / widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        Row(
            Modifier
                .fillMaxHeight()
                .graphicsLayer {
                    // 抽出 / 收回：从右侧滑入，左缘横向轻拉 3%；收起反向。
                    alpha = reveal
                    translationX = (1f - reveal) * 22.dp.toPx()
                    scaleX = 1f + .03f * (1f - reveal)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, .5f)
                },
        ) {
            // 和笔记窗同一条拖宽轨道：只在浮窗自己的左缘，不盖正文。
            Box(
                Modifier
                    .width(if (animatedWidthPx > 1f) 20.dp else 0.dp)
                    .fillMaxHeight()
                    .pointerInput(screenPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            latestResizeStart.value?.invoke()
                            onClearEditorFocus?.invoke()
                            down.consume()
                            var lastX = down.position.x
                            var moved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dx = change.position.x - lastX
                                lastX = change.position.x
                                if (dx != 0f) {
                                    moved = true
                                    change.consume()
                                    widthPx = (widthPx - dx).coerceIn(minPx, maxPx)
                                }
                            }
                            if (moved) {
                                val share = ((widthPx / screenPx).coerceIn(.30f, .62f) * 100f)
                                    .roundToInt() / 100f
                                widthPx = (screenPx * share).coerceIn(minPx, maxPx)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight(.76f)
                        .clip(CircleShape)
                        .background(p.line),
                )
            }
            MoyuSurface(
                Modifier
                    .width(with(density) { animatedWidthPx.toDp() })
                    .fillMaxHeight()
                    // 浮窗上的触摸归浮窗，正文编辑器收不到。
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                radius = 20f,
                flat = true,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 8.dp, top = 11.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LineIcon(Glyph.Check, ui.accent, 12.dp)
                        Spacer(Modifier.width(5.dp))
                        Txt("校核", size = 10.5f, color = p.muted, weight = FontWeight.Medium)
                        if (issues.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Txt(
                                "${issues.size} 处",
                                size = 9f,
                                color = p.muted.copy(alpha = .7f),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .bouncy(scale = .9f) { onClose() },
                            contentAlignment = Alignment.Center,
                        ) {
                            LineIcon(Glyph.Close, p.muted, 14.dp)
                        }
                    }
                    RowDivider(inset = 14.dp)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            issues.isNotEmpty() -> LazyColumn(
                                Modifier.fillMaxSize(),
                                state = proofList,
                                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(issues, key = { _, issue -> issue.id }) { index, issue ->
                                    // 采纳/忽略后下一张卡带同柔弹簧上移，轻微过冲，
                                    // 读作被水滴推上来；卡片自己的进出场仍由 AnimatedVisibility 管。
                                    Box(
                                        Modifier.animateItem(
                                            fadeInSpec = null,
                                            placementSpec = spring(
                                                dampingRatio = .78f,
                                                stiffness = 380f,
                                            ),
                                            fadeOutSpec = null,
                                        )
                                    ) {
                                        ProofIssueCard(
                                            issue = issue,
                                            index = index,
                                            shown = visible,
                                            exiting = exiting.contains(issue.id),
                                            onAccept = { leave(issue) { onAccept(it) } },
                                            onIgnore = { leave(issue) { onIgnore(it) } },
                                            onLocate = { onLocate(issue) },
                                        )
                                    }
                                }
                            }
                            busy -> ProofStatusText(
                                text = status ?: "正在读这一段…",
                                top = 14.dp,
                            )
                            error != null -> ProofStatusText(text = error, top = 14.dp)
                            else -> ProofStatusText(
                                text = if (asked) "没挑出问题。" else "本地没挑出问题。",
                                top = 14.dp,
                            )
                        }
                    }
                    if (!asked || busy || error != null) {
                        if (error != null && issues.isNotEmpty()) {
                            Txt(
                                error,
                                size = 9f,
                                color = p.muted,
                                lineHeight = 13f,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 13.dp)
                                    .padding(bottom = 6.dp),
                            )
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
                        ) {
                            if (!asked) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .clip(SmoothCorner(12.dp))
                                        .background(
                                            lerp(
                                                p.paper,
                                                ui.accent,
                                                if (canAsk) .18f else .06f,
                                            ),
                                        )
                                        .bouncy(enabled = canAsk) { onAsk() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Txt(
                                        if (busy) "正在读…" else "让 AI 查语义 · $chars 字",
                                        size = 11f,
                                        color = if (canAsk) p.ink else p.muted,
                                        maxLines = 1,
                                    )
                                }
                            } else {
                                Txt(
                                    status ?: "正在读…",
                                    size = 9.5f,
                                    color = p.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProofStatusText(text: String, top: androidx.compose.ui.unit.Dp) {
    val p = LocalInk.current.palette
    Txt(
        text,
        size = 11f,
        color = p.muted,
        lineHeight = 16f,
        modifier = Modifier.padding(horizontal = 13.dp, vertical = top),
    )
}

/**
 * 一条建议：类别 / 原文 / 改后 / 理由 + 底部两个等宽药丸。
 * 进场按序号错开 28ms；采纳 / 忽略先缩高淡出，再交给父层落库。
 */
@Composable
private fun ProofIssueCard(
    issue: ProofIssue,
    index: Int,
    shown: Boolean,
    exiting: Boolean,
    onAccept: () -> Unit,
    onIgnore: () -> Unit,
    onLocate: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val motion = ui.settings.motion != "none"
    var entered by remember(issue.id) { mutableStateOf(!motion) }
    LaunchedEffect(issue.id) {
        if (!motion) return@LaunchedEffect
        delay((index.coerceAtMost(6) * 28).toLong())
        entered = true
    }
    AnimatedVisibility(
        visible = shown && entered && !exiting,
        enter = fadeIn(tween(180)) +
            slideInVertically(spring(MOYU_DAMPING, 520f)) { it / 3 } +
            expandVertically(tween(180)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
        label = "proofCard",
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SmoothCorner(14.dp))
                .background(p.faint)
                .bouncy(scale = .99f) { onLocate() }
                .padding(horizontal = 11.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(issue.kind, size = 8.5f, color = ui.accent, weight = FontWeight.Medium)
                if (issue.local) {
                    Spacer(Modifier.width(5.dp))
                    Txt("本地", size = 8.5f, color = p.muted.copy(alpha = .8f))
                }
                Spacer(Modifier.weight(1f))
                Txt("定位", size = 8.5f, color = p.muted.copy(alpha = .7f))
            }
            Spacer(Modifier.height(4.dp))
            Txt(
                issue.quote,
                size = 9.5f,
                color = p.muted,
                lineHeight = 14f,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Txt(
                "→ " + issue.suggestion,
                size = 12f,
                color = p.ink,
                lineHeight = 18f,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (issue.why.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Txt(
                    issue.why,
                    size = 9f,
                    color = p.muted.copy(alpha = .8f),
                    lineHeight = 13f,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProofAction("采纳", primary = true) { onAccept() }
                ProofAction("忽略", primary = false) { onIgnore() }
            }
        }
    }
}

@Composable
private fun RowScope.ProofAction(label: String, primary: Boolean, onClick: () -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(
        Modifier
            .weight(1f)
            .height(36.dp)
            .clip(SmoothCorner(11.dp))
            .background(if (primary) ui.accent else p.paper)
            .bouncy(scale = .96f) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Txt(
            label,
            size = 12f,
            color = if (primary) {
                if (p.isDark) p.paper else Color.White
            } else {
                p.ink
            },
            weight = FontWeight.Medium,
        )
    }
}
