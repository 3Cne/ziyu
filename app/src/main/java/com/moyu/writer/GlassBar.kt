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
    /** 0..1 boost on the glass rim (bevel band, Fresnel ring, equator line,
     *  extra edge blur). The rim itself is part of the material and is always
     *  drawn; 0 only drops the boost. It is a thickness cue, not a glow. */
    edgeStrength: Float = 1f,
    /** 3.26.61：AGSL 厚度场的凹面程度（1 = 凹月牙，0 = 平的凸面）。 */
    meniscus: Float = 1f,
    /** AGSL 厚度场 rim 的额外模糊（px）；null = 旧口径：由 [edgeStrength] 派生。 */
    rimBlurPx: Float? = null,
    /** AGSL 厚度场亮环强度 0..1；null = 旧口径：由 [edgeStrength] 派生。 */
    rimK: Float? = null,
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
    // CircleShape, so they keep Backdrop's lens — the thickness-field rim
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
                    // 3.26.61：TabBar 与设置预览把六项玻璃设置的换算值显式传进来；
                    // 没传（AI 输入框 / 按钮 / 通知的旧材质）保持原来的口径。
                    val legacyEdge = edgeStrength.coerceIn(0f, 1f)
                    runCatching {
                        buildLensEffect(
                            bevelDp = refractionDp,
                            meniscus = meniscus,
                            veilK = veil,
                            dispersion = dispersion,
                            blurExtraPx = rimBlurPx ?: (
                                LENS_EDGE_BLUR_PX *
                                    (LENS_EDGE_BLUR_BASE + (1f - LENS_EDGE_BLUR_BASE) * legacyEdge)
                                ),
                            rimK = rimK ?: (LENS_RIM_BASE + (1f - LENS_RIM_BASE) * legacyEdge),
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
            // inside the shader, and that rim now has a baseline that does not
            // depend on edgeStrength (the setting is only a boost). Keeping the
            // old wide band on top would be a second bright ring (the one users
            // rejected three times), so on the AGSL path the band strength is
            // effectively 0. The function and the other paths are untouched.
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

/**
 * 3.26.61：TabBar 整块玻璃（条 + 选中镜片）的材质参数。
 *
 * 六项设置的**唯一**换算入口。TabBar 的条、TabBar 的镜片、设置页的预览都读
 * 这一个函数的结果，任何地方都不许再写一遍 `2 + 16 × …` / `8 × …`——各写一份
 * 就是本轮要收掉的漂移源（3.26.60 的镜片倍率就是这么和栏分开的）。
 */
internal data class GlassMaterial(
    /** 曲面边宽度（dp）：折射强度 0..1 → 2..18dp。 */
    val bevelDp: Float,
    /** 凹面厚度：1 = 凹月牙，0 = 平的凸面。 */
    val meniscus: Float,
    /** 边缘额外模糊（px）：边缘模糊 0..1 → 0..8px。 */
    val edgeBlurPx: Float,
    /** 亮环强度 0..1（直接是 AGSL 的 edgeK）。 */
    val rim: Float,
    /** 色散开关。 */
    val dispersion: Boolean,
    /** 纸色覆盖 0..1。 */
    val veil: Float,
)

internal fun AppSettings.glassMaterial(): GlassMaterial = GlassMaterial(
    bevelDp = 2f + 16f * glassRefract.coerceIn(0f, 1f),
    meniscus = glassMeniscus.coerceIn(0f, 1f),
    edgeBlurPx = 8f * glassEdgeBlur.coerceIn(0f, 1f),
    rim = glassRim.coerceIn(0f, 1f),
    dispersion = glassDisperse,
    veil = glassVeil.coerceIn(0f, 1f),
)

/**
 * 3.26.61：选中镜片的那块厚度场玻璃。
 *
 * TabBar 的真镜片和设置页「镜片」预览都调这一个函数：材质只来自
 * [GlassMaterial]，tint / paperVeil / edge band / 厚度场不可能两边各画一套。
 * API 33+ 走 AGSL 厚度场；31–32 或 shader 构建失败退回 Backdrop `lens()`；
 * 没有 backdrop 或 < 31 时退回纸色胶囊（与旧镜片回退一致）。
 *
 * 阴影半径沿用镜片原来的 12dp / 6dp，阴影值由调用方给（TabBar 传 lensShadow）。
 */
internal fun Modifier.glassLens(
    backdrop: Backdrop?,
    shape: CornerBasedShape,
    palette: InkPalette,
    material: GlassMaterial,
    shadowAlpha: Float,
): Modifier {
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // 非玻璃回退底色：墨 88%（浅色）→ 墨 18%（深色），随 darkAmount 连续过渡。
        return clip(shape).background(palette.ink.copy(alpha = .88f - .70f * palette.darkAmount))
    }
    val tint = if (palette.isDark) Color.White.copy(alpha = .06f) else palette.ink.copy(alpha = .045f)
    val paperVeil = palette.paper.copy(alpha = material.veil.coerceIn(0f, 1f) * .94f)
    // AGSL 生效时 shader 自带 Fresnel / 赤道亮线，再叠一条宽边带就是第二道亮环
    // （3.26.13/14/52 三次被否的坑），与 liquidGlassBar 同一口径：跳过宽边带；
    // 31–32 的回退路径才画，强度 = 亮环强度。
    val useShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && material.bevelDp > 0f
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            if (material.bevelDp > 0f) {
                val ags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching {
                        buildLensEffect(
                            bevelDp = material.bevelDp,
                            meniscus = material.meniscus,
                            veilK = material.veil,
                            dispersion = material.dispersion,
                            blurExtraPx = material.edgeBlurPx,
                            rimK = material.rim,
                        )
                    }.getOrNull()
                } else {
                    null
                }
                if (ags != null) {
                    effect(ags)
                } else {
                    lens(
                        refractionHeight = material.bevelDp.dp.toPx(),
                        refractionAmount = material.bevelDp.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = material.dispersion,
                    )
                }
            }
        },
        highlight = null,
        shadow = { Shadow(radius = 12.dp, alpha = shadowAlpha) },
        innerShadow = { InnerShadow(radius = 6.dp, alpha = shadowAlpha) },
        layerBlock = {
            scaleX = 1f
            scaleY = 1f
        },
        onDrawSurface = {
            drawRect(tint)
            if (material.veil > 0f) drawRect(paperVeil)
            glassEdgeBand(shape, palette, if (useShader) 0f else material.rim)
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
// optical-path displacement -> Fresnel rim + equator line.
//
// It takes its knobs from the caller instead of reading the settings itself:
//   * `bevelDp`    -> bevel width of the curved edge (the TabBar maps the
//     "折射强度" setting to it: 2 + 16 × value, roughly 2–18dp)
//   * edge height (the algorithm's `H`) has no setting, so it follows the same
//     bevel: H = LENS_HEIGHT_PER_BEVEL * bevel. 1.5 keeps the peak lateral
//     displacement around one bevel width, i.e. about what `lens()` did.
//   * `meniscus`   -> 凹面厚度: 1 = concave meniscus, 0 = flat convex surface
//   * `rimK`       -> 亮环强度: rim / equator strength 0..1 (0 = no ring)
//   * `blurExtraPx`-> 边缘模糊: extra rim blur in px (0..8)
//   * `veilK`      -> 纸色覆盖: damps the optical highlights (paper over glass)
//   * `dispersion` -> 色散: R/B sample split at the rim
//
// The old `tab*` / lens multipliers are no longer read here. The legacy
// "derived from edgeStrength" values survive only for the AI glass surfaces,
// which still use the older per-surface settings.
//
// Everything below is API 31-safe to *compile*; the runtime call is gated to
// API 33 because `RuntimeShader` only exists there.
// ---------------------------------------------------------------------------

/** The bevel-height / bevel-width ratio of the glass edge (the spec's H/bevel). */
private const val LENS_HEIGHT_PER_BEVEL = 1.5f
/**
 * 旧口径：AI 输入框 / 按钮 / 通知的玻璃没有独立的边缘模糊设置，rim 额外模糊
 * 由它们的「边缘」值派生。TabBar 已改为直接传 glassEdgeBlur 的换算值。
 */
private const val LENS_EDGE_BLUR_PX = 6.5f
/**
 * 旧口径：上面那档 rim 模糊里始终存在的比例，让边缘在「边缘带 = 0」时仍读作
 * 玻璃厚度。TabBar 不再走这条派生。
 */
private const val LENS_EDGE_BLUR_BASE = .35f
/** R/B sample split at the rim when dispersion is on (0..1). */
private const val LENS_DISPERSION = .06f
/**
 * 旧口径：AI 玻璃的亮环基线强度（0..1），由 `edgeStrength` 做 boost。
 * TabBar 的「亮环强度」直接就是 `edgeK`，不再走基线 + boost。
 */
private const val LENS_RIM_BASE = .55f
private const val LENS_SHADER_KEY = "moyu.liquid.lens"

// 3.26.62: 亮环随背景自适应（参考实现口径）——只让 Fresnel 亮环与赤道线的
// 「强度 + 颜色」跟随背景；光向 Lp / Ls 与两个高光指数（29.5 / 20.0）保持固定，
// 因为用背景梯度去转光向会在凹面上产生斜向撕裂。
// 这四个口径直接以 `$` 插值进 AGSL 源码（与 LENS_* 常量同一处管理），
// 下一轮只改下面几行即可，不必进 shader 正文。
/** 颜色染色比：rim 色从背景色里取多少（0 = 纯白，1 = 全用背景色）。 */
private const val LENS_RIM_TINT = .42f
/** 亮度基线：背景接近全黑时亮环仍保留的强度（0.55 = 保住一半多）。 */
private const val LENS_RIM_GAIN_BASE = .55f
/** 亮度增益：背景变亮时亮环最多再涨多少（0 = 完全不随亮度）。 */
private const val LENS_RIM_GAIN = .45f
/** 背景亮度的过渡区间（smoothstep 起止）：0.06 以下取基线，0.62 以上取满。 */
private const val LENS_RIM_ENV_LO = .06f
private const val LENS_RIM_ENV_HI = .62f

private val AGSL_LIQUID_LENS = """
uniform shader content;

uniform float2 size;         // surface size, px (node without padding)
uniform float2 center;       // surface centre inside the padded layer, px
uniform float2 padSize;      // padded layer size, px
uniform float cornerRadius;  // rounded-rect corner radius, px
uniform float bevel;         // width of the curved edge, px
uniform float edgeHeight;    // height of the glass edge, px (the spec's H)
uniform float meniscus;      // 1 = concave meniscus (default), 0 = convex
uniform float edgeK;         // 0..1 rim strength: LENS_RIM_BASE + boost
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

    // 3b. environment probe: the rim follows the background's brightness and
    //     hue. `col` above is already the sampled backdrop, so no second
    //     content.eval is needed. Only intensity and tint adapt on purpose:
    //     deriving the light direction from the background gradient tears the
    //     rim diagonally on the concave surface, so Lp / Ls stay fixed.
    float envLum = dot(col, float3(0.2126, 0.7152, 0.0722));
    float3 envTint = clamp(col / max(envLum, 0.12), 0.35, 2.2);
    float3 rimTint = mix(float3(1.0), envTint, $LENS_RIM_TINT);
    float rimGain = $LENS_RIM_GAIN_BASE + $LENS_RIM_GAIN * smoothstep($LENS_RIM_ENV_LO, $LENS_RIM_ENV_HI, envLum);

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
    col += rimTint * min(highlight * rimGain, 1.2) * 0.62;

    // 6. equator line: one bright hairline just inside the upper bevel.
    //    Nothing dark is painted at the top edge any more: the old dark-top
    //    mask (smoothstep(b0, ...) + mix) is deleted, and with it b0. The line
    //    is pinned at a fixed ny (a few dp under the top edge, independent of
    //    the bevel ratio) instead of tracking b0, so a bevel change cannot move
    //    it. Width is a narrow Gaussian (~1px); its strength keeps the
    //    edgeStrength boost like the Fresnel rim, and it follows the same
    //    background gain / tint as the rim. No extra veil multiply: the paper
    //    veil layer already damps it, a second one would erase the ring.
    float ny = -p.y / max(b.y, 1.0);
    float eq = (ny - 0.89) / 0.022;
    float equator = exp(-eq * eq);
    float3 eqTint = mix(float3(0.78, 0.82, 0.90), envTint, 0.18);
    col += eqTint * equator * 0.42 * edgeK * rimGain * (1.0 - 0.72 * veil);

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
 * 3.26.61：六项玻璃设置由调用方换算后传进来（TabBar 与设置预览用
 * [glassMaterial] 这一处换算，镜片用 [glassLens]）。本函数内部不再写死
 * meniscus / 亮环 / rim 模糊——它们分别是 [meniscus] / [rimK] / [blurExtraPx]。
 *
 * The layer is widened when the current padding cannot hold the rim
 * displacement, otherwise the compressed surroundings at the edge would be
 * sampled from outside the recorded layer.
 */
internal fun BackdropEffectScope.buildLensEffect(
    bevelDp: Float,
    /** 凹面厚度：1 = 凹月牙，0 = 平的凸面。 */
    meniscus: Float,
    veilK: Float,
    dispersion: Boolean,
    /** rim 额外模糊，px（0 = 边缘不额外模糊）。 */
    blurExtraPx: Float,
    /** 亮环 / 赤道线强度 0..1（0 = 不画亮环）。 */
    rimK: Float,
): android.graphics.RenderEffect {
    val minDim = minOf(size.width, size.height)
    val bevel = (bevelDp * density).coerceIn(1f, minDim * .5f)
    val edgeHeight = bevel * LENS_HEIGHT_PER_BEVEL
    val corner = ((shape as? CornerBasedShape)?.topStart?.toPx(size, this))
        ?.coerceIn(0f, minDim * .5f)
        ?: (minDim * .5f)
    val edgeK = rimK.coerceIn(0f, 1f)
    val blurExtra = blurExtraPx.coerceAtLeast(0f)
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
    shader.setFloatUniform("meniscus", meniscus.coerceIn(0f, 1f))
    shader.setFloatUniform("edgeK", edgeK)
    shader.setFloatUniform("veil", veilK.coerceIn(0f, 1f))
    shader.setFloatUniform("dispersion", if (dispersion) LENS_DISPERSION else 0f)
    shader.setFloatUniform("blurExtra", blurExtra)
    if (BuildConfig.DEBUG) {
        Log.d(
            "MOYU_GLASS",
            "agsl=${Build.VERSION.SDK_INT} bevel=$bevel H=$edgeHeight meniscus=$meniscus rim=$edgeK blur=$blurExtra",
        )
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