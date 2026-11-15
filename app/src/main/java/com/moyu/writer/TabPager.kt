package com.moyu.writer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
     * 长按标签栏拖动镜片时的连续位置（0..pageCount-1）。非空时页面直接跟手，
     * 手一松回 null，从当前位置继续弹到 `index`（不会跳）。
     */
    dragOffset: Float? = null,
    modifier: Modifier = Modifier,
    page: @Composable (Int) -> Unit,
) {
    // Animatable instead of animateFloatAsState: the strip must be able to
    // follow a finger and then continue the same spring from wherever it left.
    val anim = remember { Animatable(index.toFloat()) }
    // 拖动中直接用 dragOffset（1:1 跟手，不留动画延迟）；松手才交给弹簧。
    // 原来把每帧的 dragOffset 都 snapTo 进 Animatable，等于每帧重启一次协程。
    val dragging = dragOffset != null
    val lastDrag = remember { mutableFloatStateOf(index.toFloat()) }
    // 只有「刚松开拖动」这一下才需要把弹簧对齐到手指停下的位置；
    // 普通点击不能对齐，否则页面会先跳回上次拖动的落点再弹过去。
    val wasDragging = remember { mutableStateOf(false) }
    if (dragOffset != null) {
        lastDrag.floatValue = dragOffset
        wasDragging.value = true
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
    // 松手那一帧 dragOffset 变 null，但上面的 effect 还没跑、anim 仍停在旧 index。
    // 直接 `dragOffset ?: anim.value` 会让页面先渲染回旧页，下一帧再弹到新页——
    // 用户看到的就是「拖完页面抽动一下」。所以在 wasDragging 撤销之前继续用
    // 手指放下的位置，交接期间 offset 连续。
    val offset = dragOffset
        ?: if (wasDragging.value) lastDrag.floatValue else anim.value
    // For fade/scale only the two ends are shown: intermediate tabs must not
    // appear, so we remember where the strip came from.
    var fromIndex by remember { mutableIntStateOf(index) }
    LaunchedEffect(index) {
        fromIndex = kotlin.math.round(offset).toInt().coerceIn(0, pageCount - 1)
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(LocalInk.current.palette.paper),
    ) {
        val pageWidth = maxWidth
        val widthPx = with(LocalDensity.current) { pageWidth.toPx() }
        val shift = -offset * widthPx
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
            val d = i - offset
            // Hand-off instead of a cross-fade: the old page is gone before the
            // new one starts, so the two never sit on top of each other (that
            // overlap read as a flash).
            val span = kotlin.math.abs(index - fromIndex).coerceAtLeast(1).toFloat()
            val done = ((kotlin.math.abs(offset - fromIndex) / span).coerceIn(0f, 1f))
            // Material "fade-through": outgoing leaves in the first third,
            // incoming arrives in the rest, so the two are never both half
            // transparent on top of each other.
            // Tabs always slide. The fade / scale variants were removed: on a
            // pager whose pages are all composed, any alpha hand-off between
            // two full screens either double-exposed or flashed.
            val near = 1f
            val scale = 1f
            // Pages that are not on screen must not be drawn: drawing all four
            // every frame kept rewriting the page layer the TabBar samples, and
            // the glass flickered. They stay composed and measured, only the
            // pixels are skipped, so the incoming page is already warm.
            // Slide keeps drawing every page (that path never flashed). Only
            // fade/scale skip off-screen draws, and even then they warm up
            // while settled, so the incoming page is already rasterised when
            // the transition starts instead of blanking on its first visible
            // frame.
            Box(
                Modifier
                    .width(pageWidth)
                    .fillMaxHeight()
                    // Float translation, not Modifier.offset(Dp): layout offsets
                    // round to whole pixels, so the last sub-pixel of a spring
                    // was held then stepped 1px at the end ("小小的瞬移").
                    .graphicsLayer {
                        translationX = widthPx * d
                        alpha = 1f
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                page(i)
            }
        }
    }
}
