package com.moyu.writer

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow
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
    /** 显示哪些页（0 书架 / 1 笔记 / 2 数据 / 3 问书 / 4 设置），顺序固定；默认全显示。 */
    visible: List<Int> = listOf(0, 1, 2, 3, 4),
) {
    val ui = LocalInk.current
    val p = ui.palette
    val s = ui.settings
    val defaults = listOf("书架", "笔记", "数据", "问书", "设置")
    // Five tabs since 3.10.0: 问书 sits between 数据 and 设置. The name list is
    // read through `defaults` so a data file written by an older build (four
    // names) still lines up after normalize() inserts the fifth slot.
    val allNames = List(defaults.size) { i ->
        s.tabNames.getOrNull(i)?.ifBlank { defaults[i] } ?: defaults[i]
    }
    val allIcons = listOf(Glyph.Book, Glyph.Note, Glyph.Chart, Glyph.Sparkles, Glyph.Gear)
    // 关掉某些页后只画可见项，`current` 是可见序列里的位置。
    val shown = visible.ifEmpty { listOf(4) }
    val names = shown.map { allNames.getOrElse(it) { defaults[it] } }
    val icons = shown.map { allIcons[it] }
    val height = if (s.showTabLabels) 64.dp else 56.dp
    val capsule = SmoothCorner(50)
    val glassEnabled = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(stiffness = 720f, dampingRatio = MOYU_DAMPING, visibilityThreshold = PRESS_THRESHOLD),
        visibilityThreshold = PRESS_THRESHOLD,
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
                val itemWidth = maxWidth / names.size
                // 形变要按「这一帧真的走了多少像素」算，不能按 tab 数：
                // 同样 0.01 tab，在 72dp 一格的条上是 0.72dp，乘 36 直接
                // 顶到上限，于是远近快慢看起来全一样。这里用一格的像素宽
                // 归一，慢移只有 0.1 左右，快甩才吃满 0.38。
                val itemPx = with(LocalDensity.current) { itemWidth.toPx() }
                // Lens travel is an Animatable instead of animateDpAsState so the
                // stretch can be scheduled around the trip: grow along the
                // direction of travel, then settle back to its own width.
                val lensPos = remember { Animatable(current.toFloat()) }
                var lensStretch by remember { mutableFloatStateOf(0f) }
                // 0 = 静止，1 = 刚撞到底：横向压扁、纵向鼓起，随即弹回。
                var lensImpact by remember { mutableFloatStateOf(0f) }
                var lensRightward by remember { mutableStateOf(true) }
                var lensLast by remember { mutableFloatStateOf(current.toFloat()) }
                var lensLastNs by remember { mutableLongStateOf(0L) }
                LaunchedEffect(current) {
                    lensRightward = current >= lensPos.value
                    lensLast = lensPos.value
                    lensLastNs = 0L
                    lensPos.animateTo(
                        targetValue = current.toFloat(),
                        animationSpec = spring(
                            // 0.78：冲过一次就停，不再左右回荡（水滴落地一下就稳）。
                            dampingRatio = if (s.motion == "none") 1f else .78f,
                            stiffness = if (s.motion == "none") 2000f else 250f,
                            visibilityThreshold = 1e-3f,
                        ),
                    ) {
                        // 用真实帧间隔折算回 60Hz：120Hz 上每帧位移只有一半，
                        // 折算后形变强度与 60Hz 一致；掉帧时也不会突然变长。
                        val step = abs(value - lensLast)
                        lensLast = value
                        val now = System.nanoTime()
                        val dt = if (lensLastNs == 0L) {
                            1f / 60f
                        } else {
                            ((now - lensLastNs) / 1_000_000_000f).coerceIn(.002f, .05f)
                        }
                        lensLastNs = now
                        val step60 = step * (1f / 60f) / dt
                        // 像素/帧 ÷ 40、上限 .62：比 3.14.11 再放大约 55%，
                        // 普通切换中段就接近满格，慢滑仍有 0.12 左右。
                        val target = (step60 * itemPx / 40f).coerceIn(0f, .62f)
                        // 与 3.12.4 定稿同一条曲线：60Hz 时 .32，120Hz 按时间
                        // 折算成 .175；不再额外乘收尾系数，形变随步长自然归零，
                        // 避免最后几帧「退一下」造成的一顿一顿。
                        val follow = 1f - (1f - .32f).pow(dt * 60f)
                        // 收尾不再单独做一段 120ms。形变本身按剩余距离退零：
                        // 最后 0.12 tab 用 smoothstep 从 1 收到 0，位置走到
                        // 终点时形变同步就是 0——「放松」属于同一段动作，
                        // 不会在停住之后再收一下。
                        val settle = (abs(value - current) / .12f).coerceIn(0f, 1f)
                            .let { it * it * (3f - 2f * it) }
                        lensStretch = (lensStretch + (target - lensStretch) * follow) * settle
                        lensImpact = (abs(value - current) * 5f).coerceIn(0f, .55f) * settle
                        if (abs(value - current) < .0008f && abs(lensStretch) < .002f) {
                            lensStretch = 0f
                            lensImpact = 0f
                        }
                    }
                    lensStretch = 0f
                    lensImpact = 0f
                }

                // The selection is a liquid lens, not a painted pill: it
                // refracts the page and disperses colour at its rim.
                Box(
                    Modifier
                        // Float translation: a Dp offset is rounded by layout and
                        // the lens stepped 1px at the end of every tab switch.
                        // The stretch grows toward the direction of travel
                        // (origin on the trailing edge), so the lens is pulled
                        // forward instead of ballooning on both sides.
                        .graphicsLayer {
                            translationX = lensPos.value * itemWidth.toPx()
                            // 形变原点放在运动方向的**前端**：向右时右缘钉住、
                            // 左缘被拖着往后拉，才是「前端先到、后端拖尾」。
                            // 之前反过来（后端固定），所以像前端自己冲出去。
                            transformOrigin = TransformOrigin(if (lensRightward) 1f else 0f, .5f)
                            // 速度拉伸 + 过冲挤压，两者都来自位移本身。
                            scaleX = (1f + lensStretch) * (1f - lensImpact * .24f)
                            scaleY = (1f - lensStretch * .32f) * (1f + lensImpact * .28f)
                        }
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        // No glow: a 5% hairline only defines the lens outline.
                        .border(
                            .5.dp,
                            if (p.isDark) Color.White.copy(alpha = .06f) else p.ink.copy(alpha = .05f),
                            capsule,
                        )
                        // The lens is scaled in X only while travelling (see the
                        // graphicsLayer above); at rest scaleX is exactly 1, so
                        // the blurred sample never drifts past the rim.
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
                            animationSpec = spring(stiffness = 700f, dampingRatio = MOYU_DAMPING, visibilityThreshold = PRESS_THRESHOLD),
                            visibilityThreshold = PRESS_THRESHOLD,
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
                                    name.take(4),
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