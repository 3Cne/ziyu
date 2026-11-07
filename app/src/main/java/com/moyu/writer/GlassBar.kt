package com.moyu.writer

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * The one liquid-glass bar material.
 *
 * The floating tab bar and the editor dock are the same material on purpose:
 * both call this function, so a change to the tint, the blur, the rim
 * refraction, the dispersion or the shadows lands on both surfaces and they
 * can never drift apart again.
 *
 * The sampled layer is the page, never the window: the caller records its own
 * content through `Modifier.layerBackdrop` first, so neither bar can sample
 * itself and leave ghost icons inside its own glass.
 *
 * Below API 31 there is no RenderEffect, so both bars fall back to a readable
 * paper surface instead of drawing an unblurred crop of the page.
 */
internal fun Modifier.liquidGlassBar(
    backdrop: Backdrop?,
    shape: Shape,
    palette: InkPalette,
    press: Float = 0f,
    magnification: Float = .006f,
    // Changes whenever the surface must re-sample a moving layer (the editor
    // dock bumps it on every body scroll). It is folded into the layer scale
    // at a size nobody can see, purely so the draw node records a new frame.
    epoch: Int = 0,
    blurDp: Float = 8f,
    refractionDp: Float = 30f,
    dispersion: Boolean = true,
    shadowAlpha: Float = .30f,
    /** 0..1 paper veil over the glass. 0 = pure glass (blur/refraction
     *  only), 1 = an almost solid paper bar. This is what "不透明度" means
     *  to a reader; scaling the 4.5% tint was invisible. */
    veil: Float = 0f,
    /** 0..1 strength of the inner bevel band. Reads as glass thickness; it is
     *  a soft band, not a glow. */
    edgeStrength: Float = 1f,
): Modifier {
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val fallback = if (palette.isDark) {
            palette.paper.copy(alpha = .94f)
        } else {
            palette.paper.copy(alpha = .95f)
        }
        return clip(shape)
            .background(fallback)
            .border(.6.dp, palette.ink.copy(alpha = .10f), shape)
    }
    // High transparency: the page stays visible through the bar. A tenth of
    // the old paper fill, so the glass reads as glass and not as a panel.
    val tint = if (palette.isDark) {
        Color.White.copy(alpha = .06f)
    } else {
        palette.ink.copy(alpha = .045f)
    }
    val paperVeil = palette.paper.copy(alpha = veil.coerceIn(0f, 1f) * .94f)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            if (blurDp > 0f) blur(blurDp.dp.toPx())
            if (refractionDp > 0f) {
                lens(
                    refractionHeight = refractionDp.dp.toPx(),
                    refractionAmount = refractionDp.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = dispersion,
                )
            }
        },
        // No outer rim: the outermost glow ring was removed on request. The
        // shadow and inner shadow still give the bar its thickness.
        highlight = null,
        shadow = { Shadow(radius = 20.dp, alpha = shadowAlpha) },
        innerShadow = { InnerShadow(radius = 12.dp, alpha = shadowAlpha) },
        layerBlock = {
            // Whole-surface magnification: the page under the glass is always
            // a touch larger than outside, then grows further while pressed.
            val scale = 1f + magnification + epoch * 1e-7f + press * (8.dp.toPx() / size.width)
            scaleX = scale
            scaleY = scale
        },
        onDrawSurface = {
            drawRect(tint)
            if (veil > 0f) drawRect(paperVeil)
            if (edgeStrength > 0f) {
                glassEdgeBand(shape, palette, edgeStrength)
            }
        },
    )
}


/** Soft milk band just inside the glass edge: the thickness cue. Shared by the
 *  bar and the selected lens so each can be tuned on its own. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.glassEdgeBand(
    shape: Shape,
    palette: InkPalette,
    strength: Float,
) {
    val s = strength.coerceIn(0f, 1f)
    if (s <= 0f) return
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val top = Color.White.copy(alpha = (if (palette.isDark) .16f else .40f) * s)
    val bottom = if (palette.isDark) {
        Color.White.copy(alpha = .08f * s)
    } else {
        palette.ink.copy(alpha = .10f * s)
    }
    val androidPath = path.asAndroidPath()
    clipPath(path) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 4.6.dp.toPx()
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, size.height,
                    intArrayOf(top.toArgb(), Color.Transparent.toArgb(), bottom.toArgb()),
                    floatArrayOf(0f, .42f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
                maskFilter = android.graphics.BlurMaskFilter(
                    2.4.dp.toPx(),
                    android.graphics.BlurMaskFilter.Blur.NORMAL,
                )
            }
            canvas.nativeCanvas.drawPath(androidPath, paint)
        }
        drawPath(
            path = path,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.White.copy(alpha = (if (palette.isDark) .18f else .55f) * s),
                0.35f to Color.Transparent,
                1f to if (palette.isDark) Color.White.copy(alpha = .05f * s) else palette.ink.copy(alpha = .10f * s),
            ),
            style = Stroke(width = 1.1.dp.toPx()),
        )
    }
}
