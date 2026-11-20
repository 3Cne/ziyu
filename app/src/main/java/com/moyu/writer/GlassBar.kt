package com.moyu.writer

import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf/**
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
    // Backdrop's lens effect walks a CornerBasedShape's SDF. `SmoothCorner` is a
    // custom continuous-corner shape, so asking for refraction on it throws
    // "Only CornerBasedShape is supported in lens effects" at draw time — it
    // crashed the whole app the first time an in-page field used glass.
    val canRefract = shape is CornerBasedShape && refractionDp > 0f
    // 3.26.59: real thickness-field lens (AGSL, see AGSL_LIQUID_LENS).
    // API 33+ only — RuntimeShader is 33. API 31–32 keeps the Backdrop
    // `lens()` call, which itself returns early below 33, so those versions
    // (no refraction) and below 31 (paper fallback, returned above) are
    // untouched.
    // The shader's SDF is an exact rounded rect, so only *bar-shaped*
    // rounded-rect surfaces take the new path: the TabBar capsule and the
    // settings preview that mirrors it. Small circular pills are also
    // CircleShape, so they keep Backdrop's lens — the black-top/Fresnel
    // geometry must not be stretched over a 40dp chip.
    val canAgsLens = canRefract &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        shape is RoundedCornerShape
    // Surface size is only known at draw time: 200dp keeps the new path on
    // bars and off on chips/circles.
    fun agsSurface(widthPx: Float, density: Float): Boolean =
        canAgsLens && widthPx >= 200f * density
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            if (blurDp > 0f) blur(blurDp.dp.toPx())
            if (canRefract) {
                // If the AGSL shader cannot be built (compile failure on an
                // odd OEM), fall back to Backdrop's lens — never draw nothing.
                val agsEffect = if (agsSurface(size.width, density)) {
                    runCatching {
                        buildLensEffect(
                            bevelDp = refractionDp,
                            edgeStrength = edgeStrength,
                            veilK = veil,
                            dispersion = dispersion,
                        )
                    }.getOrNull()
                } else {
                    null
                }
                if (agsEffect != null) {
                    effect(agsEffect)
                } else {
                    lens(
                        refractionHeight = refractionDp.dp.toPx(),
                        refractionAmount = refractionDp.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = dispersion,
                    )
                }
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
            // The AGSL lens draws its own Fresnel / specular / equator rim
            // inside the shader. Keeping the old wide band on top would be a
            // second bright ring (the one users rejected three times), so on
            // the AGSL path the band strength is effectively 0. The function
            // and the other paths are untouched.
            if (edgeStrength > 0f && !agsSurface(size.width, density)) {
                // 3.26.13：再放宽一档（用户第二次报「太窄，根本看不出来」）。
                // 6.4dp 在 1220px 宽的条上只有 ~17px，而且 blur 3.2 把它糊平了，
                // 读不出玻璃厚度。现在 11dp + blur 5.5 让带成为一条看得见的光，
                // 亮度拉到 1.5。镜片与开关的小圆仍用默认 4.6dp——它们尺寸小，
                // 跟着加宽会糊成厚边框。
                glassEdgeBand(
                    shape,
                    palette,
                    edgeStrength,
                    bandDp = 11f,
                    blurDp = 5.5f,
                    brightness = 1.5f,
                )
            }
        },
    )
}


// ---------------------------------------------------------------------------
// Real thickness-field lens (API 33+ / AGSL)
//
// Backdrop's `lens()` is a screen-space circleMap approximation: one blur
// radius, a uniform displacement scaled only by distance to the rounded-rect
// edge, and no surface model. This shader replaces it for bar-shaped surfaces
// with the complete algorithm: thickness field -> meniscus -> variable blur ->
// optical-path displacement -> Fresnel with two lights -> black top + equator.
//
// It deliberately reuses the existing material knobs instead of adding new
// settings:
//   * `refractionDp`  -> bevel width of the curved edge, px
//   * edge height (the algorithm's `H`) has no setting, so it follows the same
//     slider: H = LENS_HEIGHT_PER_BEVEL * bevel. 1.5 keeps the peak lateral
//     displacement around one bevel width, i.e. about what `lens()` did.
//   * `edgeStrength`  -> rim highlights + extra edge blur; 0 = silently off
//     (same "0 = disabled" convention the old band used)
//   * `veil`          -> damps the optical highlights (paper over glass)
//   * `blurDp`        -> base blur radius; the shader only adds edge blur
//   * `meniscus`      -> fixed at 1 for now (not exposed)
//
// Everything below is API 31-safe to *compile*; the runtime call is gated to
// API 33 because `RuntimeShader` only exists there.
// ---------------------------------------------------------------------------

/** The bevel-height / bevel-width ratio of the glass edge (the spec's H/bevel). */
private const val LENS_HEIGHT_PER_BEVEL = 1.5f
/** Extra blur the shader adds at the rim, in px, at edgeStrength = 1. */
private const val LENS_EDGE_BLUR_PX = 6.5f
/** R/B sample split at the rim when dispersion is on (0..1). */
private const val LENS_DISPERSION = .06f
/**
 * Base rim strength added to `edgeStrength`. 0 keeps the "edgeStrength == 0 =
 * silently disabled" convention; raise to 0.5 if the Fresnel rim / equator
 * line should stay visible with the setting at 0.
 */
private const val LENS_RIM_BASE = 0f
private const val LENS_SHADER_KEY = "moyu.liquid.lens"

private const val AGSL_LIQUID_LENS = """
uniform shader content;

uniform float2 size;         // surface size, px (node without padding)
uniform float2 center;       // surface centre inside the padded layer, px
uniform float2 padSize;      // padded layer size, px
uniform float cornerRadius;  // rounded-rect corner radius, px
uniform float bevel;         // width of the curved edge, px
uniform float edgeHeight;    // height of the glass edge, px (the spec's H)
uniform float meniscus;      // 1 = concave meniscus (default), 0 = convex
uniform float edgeK;         // 0..1 edge treatment strength (Fresnel + equator)
uniform float veil;          // 0..1 paper veil over the glass
uniform float dispersion;    // R/B split at the rim, 0 = off
uniform float blurExtra;     // extra blur radius in the bevel band, px

float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float2 sdGrad(float2 p, float2 b, float r) {
    float e = 1.0;
    float gx = sdRoundBox(p + float2(e, 0.0), b, r) - sdRoundBox(p - float2(e, 0.0), b, r);
    float gy = sdRoundBox(p + float2(0.0, e), b, r) - sdRoundBox(p - float2(0.0, e), b, r);
    return float2(gx, gy);
}

half4 main(float2 coord) {
    float2 b = size * 0.5;
    float r = min(cornerRadius, min(b.x, b.y));
    float2 p = coord - center;
    float d = sdRoundBox(p, b, r);
    float2 layerMax = padSize - float2(1.0);

    // Outside the surface the layer is clipped by the shape; hand the blurred
    // backdrop through so the antialiased clip still has pixels to cut.
    if (d > bevel) {
        return content.eval(clamp(coord, float2(0.0), layerMax));
    }

    // 1. thickness field: circular-arc bevel profile.
    //    q = |p| - b + r is the rounded-box SDF; t is the inward depth across
    //    the bevel; h is the normalised edge height; slope its analytic
    //    derivative (H / bevel is the aspect of the edge).
    float t = clamp(-d / max(bevel, 1.0), 0.0, 1.0);
    float h = sqrt(max(0.0, 1.0 - (1.0 - t) * (1.0 - t)));
    float slope = (edgeHeight / max(bevel, 1.0)) * ((1.0 - t) / max(h, 0.001));
    float2 outward = normalize(sdGrad(p, b, r) + float2(0.0001, 0.0001));

    // 2. meniscus: with meniscus = 1 the surface is concave, so the normal
    //    leans inward and the sampling runs outward, compressing the
    //    surroundings into the rim.
    float curveSign = 1.0 - 2.0 * meniscus;
    float3 n = normalize(float3(curveSign * slope * outward, 1.0));

    // 4. displacement is scaled by the optical path, not uniform:
    //    0.25x at the rim, 1x on the flat top.
    float path = edgeHeight * mix(0.25, 1.0, h);
    float2 disp = -path * n.xy;

    // 3. variable blur: one base radius, extra taps only in the bevel band.
    float rimW = 1.0 - smoothstep(0.0, 0.9, t);
    float extra = blurExtra * rimW;
    float2 base = clamp(coord + disp, float2(0.0), layerMax);
    float3 col = float3(0.0);
    if (dispersion > 0.0) {
        float2 ca = disp * dispersion;
        col = float3(
            content.eval(clamp(coord + disp - ca, float2(0.0), layerMax)).r,
            content.eval(base).g,
            content.eval(clamp(coord + disp + ca, float2(0.0), layerMax)).b
        );
    } else if (extra > 0.75) {
        float2 ox = float2(extra, 0.0);
        float2 oy = float2(0.0, extra);
        half4 c0 = content.eval(base);
        half4 c1 = content.eval(clamp(base + ox, float2(0.0), layerMax));
        half4 c2 = content.eval(clamp(base - ox, float2(0.0), layerMax));
        half4 c3 = content.eval(clamp(base + oy, float2(0.0), layerMax));
        half4 c4 = content.eval(clamp(base - oy, float2(0.0), layerMax));
        col = float3(
            (c0.r + c0.r + c1.r + c2.r + c3.r + c4.r) / 6.0,
            (c0.g + c0.g + c1.g + c2.g + c3.g + c4.g) / 6.0,
            (c0.b + c0.b + c1.b + c2.b + c3.b + c4.b) / 6.0
        );
    } else {
        half4 c0 = content.eval(base);
        col = float3(c0.r, c0.g, c0.b);
    }

    // 5. Fresnel (Schlick, F0 = 0.04) + key specular + opposite fill light.
    float nz = clamp(n.z, 0.0, 1.0);
    float F = 0.04 + 0.96 * pow(1.0 - nz, 5.0);
    float3 V = float3(0.0, 0.0, 1.0);
    float3 Lp = normalize(float3(-0.42, -0.68, 0.72));
    float3 Ls = normalize(float3(0.46, 0.70, 0.62));
    float3 Hp = normalize(Lp + V);
    float3 Hs = normalize(Ls + V);
    float specP = 0.58 * pow(max(dot(n, Hp), 0.0), 29.5);
    float specS = 0.48 * pow(max(dot(n, Hs), 0.0), 20.0);
    float rimMask = 1.0 - smoothstep(0.05, 0.92, t);
    float highlight = (F * 0.62 + specP + specS) * rimMask * edgeK;
    col += float3(0.97, 0.985, 1.0) * min(highlight, 1.2) * 0.62;

    // 6. black top + equator line. The upper edge mirrors the dark room
    //    (ny -> +1 at the top); the bright equator line sits on the boundary
    //    of that dark mirror, one bevel below the top.
    float ny = -p.y / max(b.y, 1.0);
    float bevelNy = clamp(2.0 * bevel / max(size.y, 1.0), 0.02, 0.9);
    float b0 = 1.0 - bevelNy;
    float b1 = min(1.0, b0 + 0.30 * bevelNy + 0.04);
    float blackMask = smoothstep(b0, b1, ny);
    col = mix(col, float3(0.012, 0.015, 0.025), blackMask);
    float eqW = max(0.012, bevelNy * 0.09);
    float eq = (ny - b0) / eqW;
    float equator = exp(-eq * eq);
    col += float3(0.78, 0.82, 0.90) * equator * 0.42 * edgeK * (1.0 - 0.72 * veil);

    return half4(
        clamp(col.r, 0.0, 1.0),
        clamp(col.g, 0.0, 1.0),
        clamp(col.b, 0.0, 1.0),
        1.0
    );
}
"""

/**
 * Builds the AGSL thickness-field lens for this surface.
 *
 * Runs inside a `drawBackdrop` effects block, so the surface `size`, `padding`,
 * `shape` and `density` are the real draw-time values. All lengths are
 * converted to px here; the shader itself never sees dp.
 *
 * The layer is widened when the current padding cannot hold the rim
 * displacement, otherwise the compressed surroundings at the edge would be
 * sampled from outside the recorded layer.
 */
internal fun BackdropEffectScope.buildLensEffect(
    bevelDp: Float,
    edgeStrength: Float,
    veilK: Float,
    dispersion: Boolean,
): android.graphics.RenderEffect {
    val minDim = minOf(size.width, size.height)
    val bevel = (bevelDp * density).coerceIn(1f, minDim * .5f)
    val edgeHeight = bevel * LENS_HEIGHT_PER_BEVEL
    val corner = ((shape as? CornerBasedShape)?.topStart?.toPx(size, this))
        ?.coerceIn(0f, minDim * .5f)
        ?: (minDim * .5f)
    val edgeRaw = edgeStrength.coerceIn(0f, 1f)
    val edgeK = (edgeRaw + LENS_RIM_BASE).coerceIn(0f, 1f)
    val blurExtra = if (edgeRaw > 0f) LENS_EDGE_BLUR_PX * edgeRaw else 0f
    // The compressed surroundings are sampled up to ~one bevel outside the
    // edge, so the recorded layer must reach at least that far.
    padding = maxOf(padding, bevel * 1.15f + blurExtra + 2f)

    val shader = obtainRuntimeShader(LENS_SHADER_KEY, AGSL_LIQUID_LENS)
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("center", padding + size.width * .5f, padding + size.height * .5f)
    shader.setFloatUniform("padSize", size.width + padding * 2f, size.height + padding * 2f)
    shader.setFloatUniform("cornerRadius", corner)
    shader.setFloatUniform("bevel", bevel)
    shader.setFloatUniform("edgeHeight", edgeHeight)
    shader.setFloatUniform("meniscus", 1f)
    shader.setFloatUniform("edgeK", edgeK)
    shader.setFloatUniform("veil", veilK.coerceIn(0f, 1f))
    shader.setFloatUniform("dispersion", if (dispersion) LENS_DISPERSION else 0f)
    shader.setFloatUniform("blurExtra", blurExtra)
    if (BuildConfig.DEBUG) {
        Log.d("MOYU_GLASS", "agsl=${Build.VERSION.SDK_INT} bevel=$bevel H=$edgeHeight")
    }
    return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
}


/** Soft milk band just inside the glass edge: the thickness cue. Shared by the
 *  bar and the selected lens so each can be tuned on its own.
 *
 *  [bandDp] / [blurDp] scale the band without touching small surfaces: the
 *  bars pass a wider band (6.4dp) than the lens or the toggle knob (4.6dp).
 *  [brightness] multiplies both ends of the vertical gradient. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.glassEdgeBand(
    shape: Shape,
    palette: InkPalette,
    strength: Float,
    bandDp: Float = 4.6f,
    blurDp: Float = 2.4f,
    brightness: Float = 1f,
) {
    val s = strength.coerceIn(0f, 1f)
    if (s <= 0f) return
    val b = brightness.coerceIn(0f, 1.6f)
    // 3.26.14：深色主题下这条带几乎看不见（暗底 + 白 0.16×1.5 = 0.24）。
    // 两端都抬一档：深色 .26 / .12，浅色 .48 / .12。
    val topAlpha = ((if (palette.isDark) .26f else .48f) * s * b).coerceIn(0f, 1f)
    val bottomAlpha = ((if (palette.isDark) .12f else .12f) * s * b).coerceIn(0f, 1f)
    val hairline = (bandDp / 4.6f * 1.1f).dp
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val top = Color.White.copy(alpha = topAlpha)
    val bottom = if (palette.isDark) {
        Color.White.copy(alpha = bottomAlpha)
    } else {
        palette.ink.copy(alpha = bottomAlpha)
    }
    val androidPath = path.asAndroidPath()
    clipPath(path) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = bandDp.dp.toPx()
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, size.height,
                    intArrayOf(top.toArgb(), Color.Transparent.toArgb(), bottom.toArgb()),
                    floatArrayOf(0f, .42f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
                maskFilter = android.graphics.BlurMaskFilter(
                    blurDp.dp.toPx(),
                    android.graphics.BlurMaskFilter.Blur.NORMAL,
                )
            }
            canvas.nativeCanvas.drawPath(androidPath, paint)
        }
        drawPath(
            path = path,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.White.copy(alpha = (((if (palette.isDark) .18f else .55f) * s * b)).coerceIn(0f, 1f)),
                0.35f to Color.Transparent,
                1f to if (palette.isDark) Color.White.copy(alpha = ((.05f * s * b)).coerceIn(0f, 1f))
                else palette.ink.copy(alpha = ((.10f * s * b)).coerceIn(0f, 1f)),
            ),
            style = Stroke(width = hairline.toPx()),
        )
    }
}

/**
 * Where the AI tab's in-page glass finds the page it should sample.
 *
 * The tab bar and the dock get their backdrop handed to them by `MoyuRoot`;
 * a field inside a page has no such argument, so the same recorded layer is
 * published here once and every in-page glass reads it.
 */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * 同窗口面板打开时把根级 TabBar 收起来（3.26.9）。
 *
 * `InlineModalSheet` 是页面内的层，而 TabBar 是 root 的兄弟节点、绘制在页面之上，
 * 面板底部会被玻璃条盖住、点不到（笔记夹菜单的「删除」行实测被挡）。
 * 这里只做一件事：面板存活期间让 root 把 TabBar 收起来；面板退出后恢复。
 */
val LocalSheetSuppressed = compositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * In-page glass: the input row and the pinned-quote chips.
 *
 * Same material as the tab bar (`liquidGlassBar`), but tuned separately: an
 * input sits over moving text, so it needs a little more veil and less
 * refraction than a bar at the bottom edge. The three knobs live in
 * 设置 → 问书 → 玻璃.
 */
@Composable
fun GlassField(
    modifier: Modifier = Modifier,
    shape: Shape = SmoothCorner(20.dp),
    shadowAlpha: Float = .16f,
    press: Float = 0f,
    /** Paper veil for this surface; null = the shared 问书 setting. Buttons sit
     *  on top of scrolling text, so they need to be denser than the input. */
    veil: Float? = null,
    /** "field" = the 问书 input, "button" = the link / send pills and the tool
     *  bubble, "notice" = the motion notification. */
    kind: String = "field",
    content: @Composable BoxScope.() -> Unit,
) {
    val ui = LocalInk.current
    val s = ui.settings
    val notice = kind == "notice"
    val button = kind == "button"
    val blur = when {
        notice -> s.bubbleGlassBlur
        button -> s.btnGlassBlur
        else -> s.fieldGlassBlur
    }
    val refraction = when {
        notice -> s.bubbleGlassRefraction
        button -> s.btnGlassRefraction
        else -> s.fieldGlassRefraction
    }
    val dispersion = when {
        notice -> s.bubbleGlassDispersion
        button -> s.btnGlassDispersion
        else -> s.fieldGlassDispersion
    }
    val edge = when {
        notice -> s.bubbleGlassEdge
        button -> s.btnGlassEdge
        else -> s.fieldGlassEdge
    }
    val baseVeil = when {
        notice -> s.bubbleGlassOpacity
        button -> s.btnGlassOpacity
        else -> s.fieldGlassOpacity
    }
    // Callers still pass a shadow for emphasis (idle vs ready); the setting is
    // the base, .16 being the neutral the old call sites were written against.
    val baseShadow = when {
        notice -> s.bubbleGlassShadow
        button -> s.btnGlassShadow
        else -> s.fieldGlassShadow
    }
    val shadow = baseShadow * (shadowAlpha / .16f)
    // Real 1型玻璃: the page records its scrolling list into a layer, this node
    // sits OUTSIDE that layer and samples it — the same relationship the tab bar
    // has with the page. Sampling a layer from inside it is the self-reference
    // that crashed the render thread.
    Box(
        modifier.liquidGlassBar(
            backdrop = LocalGlassBackdrop.current,
            shape = shape,
            palette = ui.palette,
            press = press,
            blurDp = blur,
            refractionDp = refraction,
            dispersion = dispersion,
            shadowAlpha = shadow,
            veil = veil ?: baseVeil,
            edgeStrength = edge,
        ),
        content = content,
    )
}