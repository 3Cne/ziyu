package com.moyu.writer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Root-tab pager: all pages stay composed side by side and the whole strip
 * translates, so 书架 -> 设置 really drags 笔记 and 数据 across the screen
 * instead of cross-fading two neighbours. Pages are opaque, so nothing
 * double-exposes mid-flight.
 */
@Composable
internal fun TabPager(
    index: Int,
    pageCount: Int,
    perPageMs: Int,
    motion: String = "slide",
    /**
     * 长按标签栏拖动镜片时的连续位置（0..pageCount-1）。传 [State] 而不是拆包值：
     * 拖动时每个触摸事件都会写它，若在组合阶段读，整页（四个页面）会被重组，
     * 长按拖动就卡。这里在 `graphicsLayer` 里读（绘制阶段），只重绘不重组。
     * 镜片（TabBar）也在绘制阶段读同一个 State，所以两者仍然同帧。
     */
    dragOffset: State<Float?>? = null,
    modifier: Modifier = Modifier,
    page: @Composable (Int) -> Unit,
) {
    // Animatable instead of animateFloatAsState: the strip must be able to
    // follow a finger and then continue the same spring from wherever it left.
    val anim = remember { Animatable(index.toFloat()) }
    val lastDrag = remember { mutableFloatStateOf(index.toFloat()) }
    // 只有「刚松开拖动」这一下才需要把弹簧对齐到手指停下的位置；
    // 普通点击不能对齐，否则页面会先跳回上次拖动的落点再弹过去。
    val wasDragging = remember { mutableStateOf(false) }
    // derivedStateOf 只在 true/false 切换时通知，拖动中不会每帧重组。
    val dragging by remember(dragOffset) {
        derivedStateOf { dragOffset?.value != null }
    }
    // 拖动值用 snapshotFlow 跟上：组合阶段不读它，写 State 只 invalidate 绘制。
    // 这里只需要把「手指停下的位置」记下来，供松手后的弹簧对齐。
    LaunchedEffect(dragOffset) {
        val s = dragOffset ?: return@LaunchedEffect
        snapshotFlow { s.value }.collect { v ->
            if (v != null) {
                lastDrag.floatValue = v
                wasDragging.value = true
            }
        }
    }
    LaunchedEffect(index, dragging, pageCount) {
        if (!dragging) {
            // 拖动落定不过冲：.52 阻尼会让页面先冲过目标再回来，读作「抽动一下」。
            // 点击切页仍保留回弹（灵动感来自那里，不来自落定）。
            val fromDrag = wasDragging.value
            if (fromDrag) {
                anim.snapTo(lastDrag.floatValue.coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat()))
                wasDragging.value = false
            }
            // Spring instead of tween: stiffness follows the motion preset.
            // The distance already carries the "how far" part.
            anim.animateTo(
                index.toFloat(),
                spring(
                    dampingRatio = if (fromDrag) 1f else MOYU_DAMPING,
                    stiffness = pageStiffness(perPageMs),
                    visibilityThreshold = PAGE_THRESHOLD,
                ),
            )
        }
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(LocalInk.current.palette.paper),
    ) {
        val pageWidth = maxWidth
        val widthPx = with(LocalDensity.current) { pageWidth.toPx() }
        // The row must be pageCount screens wide: with fillMaxSize() it was
        // clamped to one screen, so page 0 ate all the space and every other
        // page measured 0 wide (they rendered, but nowhere).
        // Each page is offset on its own instead of translating one wide Row:
        // a single translated layer rasterised the strip once and the pages
        // that were off-screen stayed blank when they slid in. Per-page offset
        // makes every page draw at its place on every frame.
        for (i in 0 until pageCount) {
            // Every page stays composed, even the intermediate ones: dropping
            // them made the incoming page compose fresh on the switch frame and
            // that single empty frame was the flash. They are simply invisible.
            Box(
                Modifier
                    .width(pageWidth)
                    .fillMaxHeight()
                    // Float translation, not Modifier.offset(Dp): layout offsets
                    // round to whole pixels, so the last sub-pixel of a spring
                    // was held then stepped 1px at the end ("小小的瞬移").
                    // 位置在这里读 State：拖动 / 弹簧落定都只重绘这一层。
                    .graphicsLayer {
                        val live = dragOffset?.value
                            ?: if (wasDragging.value) lastDrag.floatValue else anim.value
                        translationX = widthPx * (i - live)
                        alpha = 1f
                    },
            ) {
                page(i)
            }
        }
    }
}
