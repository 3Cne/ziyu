package com.moyu.writer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * The one fixed glass material.
 *
 * The per-role material system (roles, editable specs, per-theme storage and
 * the settings page that edited them) is gone. Every surface that used to pick
 * a role now renders this same material: cards, menus, controls, panels and
 * buttons all share one fill / blur / edge / rim / specular / refraction /
 * shadow / grain contract. Only the corner radius stays a layout decision at
 * the call site because a full-width card and a small button cannot share one
 * radius without looking wrong.
 *
 * The values are deliberately mid-opaque. A purely transparent glass makes
 * popup text unreadable over a page of body copy, so each surface also gets a
 * paper underlay in the same draw chain; the glass still supplies the edge,
 * rim, highlight, refraction and grain.
 */
object GlassStyle {
    const val fill = .62f
    const val blur = .34f
    /** 卡片 / 弹窗边缘：外圈亮环的可见宽度（dp）。3.26.53 起不再为 0。 */
    const val edge = .8f
    /** 紧贴亮环内侧的次高光宽度（dp）：给纸面一点收边厚度，不是暗斜面。 */
    const val innerEdge = .6f
    /** 0..1 的边缘强度倍率；亮环 alpha 本身已经是克制值（顶 .35 级别，1 型玻璃一半）。 */
    const val rim = 1f
    const val specular = .10f
    const val refraction = .20f
    const val shadow = .14f
    const val shadowBlur = 20f
    const val shadowY = 6f
    const val corner = 26f
    const val grain = true

    /** Shared readability floor under the glass; not a per-surface parameter. */
    const val paperBase = .72f
}

val LocalGlassLite = staticCompositionLocalOf { false }

@Composable
fun MoyuSurface(
    modifier: Modifier = Modifier,
    radius: Float? = null,
    shape: Shape? = null,
    /** Cards: one flat paper tone, no gradient, no glow, no rim. */
    flat: Boolean = false,
    /** Flat fill override. Buttons pass the dock tone so one solid colour is
     *  shared by the toolbar and every button, with no gradient or shadow. */
    tone: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val ui = LocalInk.current
    val lite = LocalGlassLite.current
    val resolvedRadius = radius ?: GlassStyle.corner
    val resolvedShape = shape ?: SmoothCorner(resolvedRadius.dp)
    val density = LocalDensity.current
    val shadowBlurDp = GlassStyle.shadowBlur
    val shadowYDp = GlassStyle.shadowY

    // Paper backing + drop shadow, shared by both the glass and the flat card.
    fun backing(
        alpha: Float,
        tone: Color = ui.palette.paper,
        withShadow: Boolean = true,
    ) = Modifier.drawBehind {
            // The readable paper floor belongs in the same draw chain as the
            // glass. A child background would paint above the RenderEffect and
            // let page text bleed through.
            //
            // Draw the *actual* shape, never a radius-rounded rectangle: a
            // sheet with only its top corners rounded used to get a fill with
            // four round corners, so the bottom edge stayed round even though
            // the content was clipped square (the 3.9.36 menu bug).
            val outlinePath = Path().apply {
                addOutline(resolvedShape.createOutline(size, layoutDirection, this@drawBehind))
            }
            drawPath(outlinePath, tone.copy(alpha = alpha))
            if (withShadow && GlassStyle.shadow > 0f) {
                val elevation = with(density) { shadowBlurDp.dp.toPx() }.coerceAtLeast(1f)
                val dy = with(density) { shadowYDp.dp.toPx() }
                val shadowColor = ui.palette.ink
                    .copy(alpha = (GlassStyle.shadow * .34f).coerceIn(0f, 1f))
                    .toArgb()
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG,
                    ).apply {
                        color = android.graphics.Color.TRANSPARENT
                        setShadowLayer(elevation, 0f, dy, shadowColor)
                    }
                    canvas.nativeCanvas.drawPath(outlinePath.asAndroidPath(), paint)
                }
            }
        }

    if (flat) {
        // One flat tone: no diagonal gradient, no caustic bloom, no rim. The
        // card needs a few levels of separation from the page behind it or a
        // pure paper fill disappears into the background gradient.
        val fillTone = tone ?: if (ui.palette.isDark) {
            lerp(ui.palette.paper, Color.White, .04f)
        } else {
            lerp(ui.palette.paper, ui.palette.ink, .04f)
        }
        Box(
            modifier.then(backing(1f, fillTone, withShadow = false)).clip(resolvedShape),
            content = content,
        )
        return
    }

    GlassSurface(
        modifier = modifier.then(backing(GlassStyle.paperBase)),
        radius = resolvedRadius,
        strength = 1f,
        frost = 1f,
        tint = ui.palette.paper,
        haze = if (lite) null else LocalHazeHandle.current,
        useHaze = !lite,
        specular = GlassStyle.specular > 0f,
        // Pass the resolved shape, not the raw nullable parameter: a glass
        // surface created with only `radius` used to fall back to a circular
        // round-rect while every flat card around it had continuous corners.
        shape = resolvedShape,
        fillOverride = GlassStyle.fill,
        blurOverride = GlassStyle.blur,
        grainOverride = GlassStyle.grain,
        edgeWidth = GlassStyle.edge,
        innerEdgeWidth = GlassStyle.innerEdge,
        rimStrength = GlassStyle.rim,
        specularStrength = GlassStyle.specular,
        refractionStrength = if (lite) 0f else GlassStyle.refraction,
        // 没有调用点显式要求的描边：边缘来自上面那圈「亮环 + 次高光」内缩轮廓，
        // 不是画在裁切边界上的 borderColor 描边。
        borderColor = null,
        content = content,
    )
}