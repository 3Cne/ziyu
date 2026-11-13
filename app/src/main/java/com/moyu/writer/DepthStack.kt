package com.moyu.writer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay

/**
 * Depth navigation with the tab pager's maths, one position per layer.
 *
 * A freshly pushed page starts one screen to the right and animates in; the
 * page that is popping animates out to the right and only then leaves the
 * list. Because each layer owns an Animatable, two rapid pops keep two layers
 * moving instead of cancelling each other, and nothing is ever added in an
 * effect (which used to leave one blank frame).
 */
@Composable
internal fun DepthStack(
    screens: List<Any>,
    perPageMs: Int,
    maxLayers: Int = 4,
    content: @Composable (Any, Boolean) -> Unit,
) {
    val top = screens.lastOrNull()
    val retained = screens.takeLast(maxLayers)
    val leaving = remember { mutableStateListOf<Any>() }
    // Returning to a page that is still sliding out (tap the same book twice)
    // must not compose two layers with one key: drop the leaving copy first.
    if (leaving.contains(top)) leaving.remove(top)
    val prevTop = remember { mutableStateOf<Any?>(null) }
    val previous = prevTop.value
    if (previous != null && previous != top && !retained.contains(previous) && !leaving.contains(previous)) {
        leaving.add(previous)
    }
    SideEffect { prevTop.value = top }
    val layers = retained + leaving.filterNot { retained.contains(it) }
    val topIndex = layers.indexOf(top)
    val firstEver = layers.size == 1
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        layers.forEachIndexed { i, screen ->
            key(screen) {
                val target = (i - topIndex).toFloat()
                // New layers begin one screen to the right, otherwise
                // animateFloatAsState would start already at the target and
                // the page would pop in with no entrance at all.
                val anim = remember(screen) { Animatable(if (firstEver) target else 1f) }
                LaunchedEffect(target) {
                    anim.animateTo(target, spring(MOYU_DAMPING, pageStiffness(perPageMs), PAGE_THRESHOLD))
                    // Remove a popped page only after its own spring has really
                    // stopped. The old fixed perPageMs+40 delay unmounted it
                    // while a few pixels were still moving, which read as a
                    // small teleport at the end of every back navigation.
                    if (!screens.contains(screen)) leaving.remove(screen)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        // graphicsLayer keeps sub-pixel positions; a Dp offset is
                        // rounded by layout and steps 1px on the last frame.
                        .graphicsLayer { translationX = anim.value * widthPx },
                ) {
                    content(screen, screen == top)
                }
            }
        }
    }
}
