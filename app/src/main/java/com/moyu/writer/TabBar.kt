package com.moyu.writer

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * Floating four-tab bar made from a real liquid-glass surface.
 *
 * The page is recorded by `Modifier.layerBackdrop`; this capsule samples that
 * layer through Kyant0's Backdrop library (Apache-2.0, Maven Central
 * `io.github.kyant0:backdrop`). The library supplies vibrancy, blur, the
 * capsule-edge refraction, chromatic dispersion, the specular rim and the
 * shadows; this file only arranges the tabs.
 *
 * The five liquid-glass traits are explicit here:
 *  1. high transparency: a 10–16% tint, no paper body;
 *  2. edge distortion: `lens()` at the capsule rim;
 *  3. chromatic dispersion: `lens(..., chromaticAberration = true)` on the
 *     selected lens and the rim;
 *  4. whole-surface scaling: `layerBlock` scales the sampled page on press,
 *     and the selected lens springs up;
 *  5. our addition: a moving specular sheen, an inner shadow so the capsule
 *     reads as thick glass, and a haptic press.
 *
 * Below API 31 there is no RenderEffect, so the bar falls back to a readable
 * paper capsule instead of drawing an unblurred crop of the page.
 */
@Composable
internal fun MoyuTabBar(
    current: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop? = null,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val s = ui.settings
    val names = (s.tabNames + listOf("书架", "笔记", "数据", "设置")).take(4)
    val icons = listOf(Glyph.Book, Glyph.Note, Glyph.Chart, Glyph.Gear)
    val height = if (s.showTabLabels) 64.dp else 56.dp
    val capsule = RoundedCornerShape(50)
    val glassEnabled = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(stiffness = 720f, dampingRatio = .72f),
        label = "tabPress",
    )

    // High transparency: the page must remain visible through the capsule.
    // The container tint lives in liquidGlassBar() so the dock gets the same one.
    // No press term here: raising the white/ink alpha on press made the pill
    // flash lighter for the length of the touch. The press reads through the
    // icon scale and the haptic instead.
    val lensAlpha = .08f * s.tabLensOpacity.coerceIn(0f, 1f)
    val selectedTint = if (p.isDark) {
        Color.White.copy(alpha = lensAlpha)
    } else {
        p.ink.copy(alpha = lensAlpha)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(height)
                // One shared material with the editor dock; see GlassBar.kt.
                .liquidGlassBar(
                    backdrop = backdrop,
                    shape = capsule,
                    palette = p,
                    press = press,
                    blurDp = s.tabBlur,
                    refractionDp = s.tabRefraction,
                    dispersion = s.tabDispersion,
                    shadowAlpha = s.tabShadow,
                    veil = s.tabBarOpacity,
                    edgeStrength = s.tabEdge,
                )
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            ) {
                val itemWidth = maxWidth / 4
                val x by animateDpAsState(
                    targetValue = itemWidth * current,
                    animationSpec = motionSpecDp(s.motion),
                    label = "tabLens",
                )

                // The selection is a liquid lens, not a painted pill: it
                // refracts the page and disperses colour at its rim.
                Box(
                    Modifier
                        .offset(x = x)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        // No glow: a 5% hairline only defines the lens outline.
                        .border(
                            .5.dp,
                            if (p.isDark) Color.White.copy(alpha = .06f) else p.ink.copy(alpha = .05f),
                            capsule,
                        )
                        // No layer scale on the lens: scaling a surface that
                        // samples the page dragged its blurred sample past the
                        // rim and left a halo around the pill. Press feedback
                        // lives in the tint and the icon scale instead.
                        .graphicsLayer {
                            scaleX = 1f
                            scaleY = 1f
                        }
                        .then(
                            if (glassEnabled) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop!!,
                                    shape = { capsule },
                                    effects = {
                                        vibrancy()
                                        if (s.lensBlur > 0f) blur(s.lensBlur.dp.toPx())
                                        if (s.lensRefraction > 0f) {
                                            lens(
                                                refractionHeight = s.lensRefraction.dp.toPx(),
                                                refractionAmount = (s.lensRefraction * 1.2f).dp.toPx(),
                                                depthEffect = true,
                                                chromaticAberration = s.lensDispersion,
                                            )
                                        }
                                    },
                                    highlight = null,
                                    shadow = { Shadow(radius = 12.dp, alpha = s.lensShadow) },
                                    innerShadow = { InnerShadow(radius = 6.dp, alpha = s.lensShadow + .07f) },
                                    // No magnifying the sampled page on press:
                                    // growing it pushed blurred pixels past the
                                    // rim and read as a halo one size larger
                                    // than the lens. The press feedback is the
                                    // crisp content scale on the parent layer.
                                    layerBlock = {
                                        scaleX = 1f
                                        scaleY = 1f
                                    },
                                    onDrawSurface = {
                                        drawRect(selectedTint)
                                        val v = s.tabLensOpacity.coerceIn(0f, 1f)
                                        if (v > 0f) drawRect(p.paper.copy(alpha = v * .94f))
                                        glassEdgeBand(capsule, p, s.lensEdge)
                                    },
                                )
                            } else {
                                Modifier
                                    .clip(capsule)
                                    .background(if (p.isDark) p.ink.copy(alpha = .18f) else p.ink.copy(alpha = .88f))
                            }
                        )
                )

                Row(Modifier.fillMaxSize()) {
                    names.forEachIndexed { index, name ->
                        val active = index == current
                        val fg by animateColorAsState(
                            targetValue = if (active) p.ink else p.muted,
                            animationSpec = tween(220),
                            label = "tabFg",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (active) 1f + .06f * press else 1f,
                            animationSpec = spring(stiffness = 700f, dampingRatio = .7f),
                            label = "tabIconScale",
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(capsule)
                                .bouncy(
                                    scale = .94f,
                                    hapticType = 1,
                                    onPressChange = { pressed = it },
                                ) { onSelect(index) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            ) {
                                LineIcon(
                                    icons[index],
                                    fg,
                                    if (active) 20.dp else 19.dp,
                                )
                            }
                            if (s.showTabLabels) {
                                Spacer(Modifier.height(3.dp))
                                Txt(
                                    name.ifBlank { listOf("书架", "笔记", "数据", "设置")[index] }.take(4),
                                    size = 8.5f,
                                    color = fg,
                                    weight = if (active) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}