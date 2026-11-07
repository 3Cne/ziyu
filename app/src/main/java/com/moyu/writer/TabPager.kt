package com.moyu.writer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableIntStateOf
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
    modifier: Modifier = Modifier,
    page: @Composable (Int) -> Unit,
) {
    val offset by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = tween(
            // One duration for the whole jump: the distance already carries the
            // "how far" part, multiplying it here made a 3-tab hop crawl.
            durationMillis = perPageMs.coerceAtLeast(0),
            easing = FastOutSlowInEasing,
        ),
        label = "tabPager",
    )
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
                    .offset(x = pageWidth * d)
                    .graphicsLayer {
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
