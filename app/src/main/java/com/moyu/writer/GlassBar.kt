package com.moyu.writer

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
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
    /** 0..1 strength of the whole rim stack: bright ring + inner bevel + inner
     *  highlight. 0 disables it; it is an edge, not a glow. */
    edgeStrength: Float = 1f,
): Modifier {
    // 3.26.50 的约定：一切「昼夜不同」的量都按 darkAmount 连续插值，不在绘制里
    // 用 if (isDark) 硬翻。静止态 darkAmount 恰为 0/1，像素与旧版完全一致；
    // 过渡的 220ms 里玻璃 tint 与回退纸底跟着整屏一起淡过去。
    val d = palette.darkAmount.coerceIn(0f, 1f)
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val fallback = palette.paper.copy(alpha = .95f - .01f * d)
        return clip(shape)
            .background(fallback)
            .border(.6.dp, palette.ink.copy(alpha = .10f), shape)
    }
    // High transparency: the page stays visible through the bar. A tenth of
    // the old paper fill, so the glass reads as glass and not as a panel.
    val tint = lerp(
        palette.ink.copy(alpha = .045f),
        Color.White.copy(alpha = .06f),
        d,
    )
    val paperVeil = palette.paper.copy(alpha = veil.coerceIn(0f, 1f) * .94f)
    // Backdrop's lens effect walks a CornerBasedShape's SDF. `SmoothCorner` is a
    // custom continuous-corner shape, so asking for refraction on it throws
    // "Only CornerBasedShape is supported in lens effects" at draw time — it
    // crashed the whole app the first time an in-page field used glass.
    val canRefract = shape is androidx.compose.foundation.shape.CornerBasedShape && refractionDp > 0f
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            if (blurDp > 0f) blur(blurDp.dp.toPx())
            if (canRefract) {
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
                // 3.26.52：边缘按参考图重做（亮环 + 暗斜面 + 内层次高光，见
                // glassEdgeBand 的注释）。3.26.9 / 3.26.13 / 3.26.14 三次都只是
                // 把一条模糊奶白带加宽、调亮（6.4→11dp、blur 3.2→5.5、亮度 ×1.5），
                // 用户三次都说「偏平、偏淡、太窄看不出来」：糊的宽带只会把边糊平，
                // 玻璃厚度必须靠贴边的明暗分层，不是靠一条更粗的柔光。
                glassEdgeBand(
                    shape,
                    palette,
                    edgeStrength,
                    brightness = 1.08f,
                )
            }
        },
    )
}


/**
 * 1 型玻璃的边缘：亮环 + 暗斜面 + 内层次高光（3.26.52 按参考图重做）。
 *
 * 参考像素特征（小米式胶囊）：
 *  ① 贴着轮廓一圈 1–1.5dp 明亮细高光，顶部最亮 → 左右次之 → 底部最低；
 *  ② 亮边内侧紧接一道更暗的内阴影，贴边最暗、向内 2–3dp 渐隐 = 玻璃厚度斜面；
 *  ③ 斜面下沿再一道 1dp 次高光，厚度才收得干净；
 *  ④ 不画外发光 / 厚投影：轮廓靠这圈明暗对比立起来。
 *
 * 三层都靠「同一 outline 上的 STROKE + clipPath 裁掉外侧一半」画出来：线宽决定
 * 这一层从轮廓往里占多深，竖向 LinearGradient 天然给出「顶亮、侧中、底暗」，
 * 不用按方向分档，也没有一处纯色描边。暗斜面是把三道等 alpha、不同线宽的 stroke
 * 叠起来（贴边三道重叠、外沿只剩一道），合成后就是一条向内渐隐的厚度梯度。
 * 内层次高光先由 [insetOutline] 求出「往里 deep」的内缩轮廓，再把它当裁剪区画一条
 * 2×宽的白线，可见部分正好落在 deep..deep+1dp 这一条环上——这样它不会在暗斜面上
 * 蒙一层白，暗斜面才真的比玻璃体暗。
 *
 * [bandDp] 缩放厚度（4.6 = 参考档），[brightness] 只乘亮环与次高光、不动暗斜面。
 * 小的表面（开关圆点、设置页小镜片）会再按自身短边自动收窄，避免读成厚边框。
 * 所有明暗按 palette.darkAmount 连续插值（3.26.50 约定）：昼夜过渡时这里不硬翻。
 */
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
    val d = palette.darkAmount.coerceIn(0f, 1f)
    fun m(light: Float, dark: Float) = light + (dark - light) * d

    // 厚度跟表面尺寸走：56dp 以上（TabBar 胶囊、镜片、玻璃条）用参考档，
    // 二十几 dp 的开关圆点自动收窄到 0.62×。
    val sizeScale = (minOf(size.width, size.height) / 56.dp.toPx()).coerceIn(.62f, 1f)
    val u = sizeScale * (bandDp / 4.6f).coerceIn(.5f, 2f)
    val ring = 1.2f.dp.toPx() * u      // 亮环：参考 1–1.5dp
    val bevel = 2.6f.dp.toPx() * u     // 亮环内侧可见暗斜面：参考 2–3dp
    val inner = 1f.dp.toPx() * u       // 内层次高光：1dp
    val deep = ring + bevel            // 暗面从轮廓量起的总深度
    val soft = (blurDp * .3f).dp.toPx().coerceIn(.35f, 2f)

    val path = Path().apply {
        addOutline(shape.createOutline(size, layoutDirection, this@glassEdgeBand))
    }
    val androidPath = path.asAndroidPath()
    val h = size.height

    fun vertical(c0: Color, c1: Color, c2: Color) = android.graphics.LinearGradient(
        0f, 0f, 0f, h,
        intArrayOf(c0.toArgb(), c1.toArgb(), c2.toArgb()),
        floatArrayOf(0f, .45f, 1f),
        android.graphics.Shader.TileMode.CLAMP,
    )

    fun white(top: Float, mid: Float, low: Float) = vertical(
        Color.White.copy(alpha = top.coerceIn(0f, 1f)),
        Color.White.copy(alpha = mid.coerceIn(0f, 1f)),
        Color.White.copy(alpha = low.coerceIn(0f, 1f)),
    )

    // ① 亮环：深色顶 .96 / 侧 .52 / 底 .20。浅色纸底上白环天生弱，压到 .62/.30/.12，
    //    浅色主题的对比交给下面那道暗斜面。
    val ringShader = white(
        top = m(.62f, .96f) * s * b,
        mid = m(.30f, .52f) * s * b,
        low = m(.12f, .20f) * s * b,
    )
    // ② 暗斜面：贴边合成 alpha ≈ .30(浅)/.36(深)，底边比顶边重一档（光从上面来）。
    val bevelC = lerp(palette.ink, Color.Black, d)
    val passA = (m(.30f, .36f) * s / 2.7f).coerceIn(0f, .5f)
    val bevelShader = vertical(
        bevelC.copy(alpha = (passA * .62f).coerceIn(0f, 1f)),
        bevelC.copy(alpha = passA),
        bevelC.copy(alpha = (passA * 1.18f).coerceIn(0f, 1f)),
    )
    // ③ 内层次高光：比亮环弱一档，同样顶亮底暗。
    val hiA = (m(.16f, .22f) * s * b).coerceIn(0f, 1f)
    val hiShader = white(hiA, hiA * .72f, hiA * .34f)

    val insetPath = insetOutline(shape, size, deep, layoutDirection, this)
    val insetAndroidPath = insetPath?.asAndroidPath()

    clipPath(path) {
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
            }
            fun pass(
                widthPx: Float,
                shader: android.graphics.Shader,
                blurPx: Float,
                target: android.graphics.Path,
            ) {
                paint.strokeWidth = widthPx
                paint.shader = shader
                paint.maskFilter = android.graphics.BlurMaskFilter(
                    blurPx,
                    android.graphics.BlurMaskFilter.Blur.NORMAL,
                )
                nc.drawPath(target, paint)
            }
            // 暗斜面：由外向内三道，越贴边 alpha 叠得越高。
            pass(2f * deep, bevelShader, soft, androidPath)
            pass(2f * (ring + bevel * .62f), bevelShader, soft, androidPath)
            pass(2f * (ring + bevel * .24f), bevelShader, soft, androidPath)
            // 亮环：只留 stroke 靠内的那一半。
            pass(2f * ring, ringShader, soft * .45f, androidPath)
            if (insetAndroidPath != null) {
                // 内层次高光：裁到内缩轮廓之内，于是 2×inner 的白线只剩
                // deep..deep+inner 这一条可见。
                nc.save()
                nc.translate(deep, deep)
                nc.clipPath(insetAndroidPath)
                pass(2f * inner, hiShader, soft * .7f, insetAndroidPath)
                nc.restore()
            }
        }
    }
}

/**
 * 「从轮廓往里缩 [inset] px」的内缩轮廓。
 *
 * 不去采样或腐蚀任意 Path：Shape 本身就能在小一圈的尺寸上重新生成轮廓，只要把
 * 圆角半径同步减掉 [inset]，得到的正是精确内缩——胶囊（RoundedCornerShape(50)）、
 * 圆、固定 dp 圆角、连续曲率圆角（SmoothCornerShape）都适用。其它任意 Shape 返回
 * null，调用方就不画内层次高光（它只是最里面那 1dp，缺了不影响亮环与斜面）。
 */
private fun insetOutline(
    shape: Shape,
    size: Size,
    inset: Float,
    layoutDirection: LayoutDirection,
    density: Density,
): Path? {
    if (inset <= 0f) return null
    val w = size.width - inset * 2f
    val h = size.height - inset * 2f
    if (w <= 2f || h <= 2f) return null
    fun r(corner: CornerSize): Dp =
        ((corner.toPx(size, density) - inset).coerceAtLeast(0f) / density.density).dp
    val shrunk: Shape = when (shape) {
        is SmoothCornerShape -> SmoothCornerShape(
            CornerSize(r(shape.topStart)),
            CornerSize(r(shape.topEnd)),
            CornerSize(r(shape.bottomEnd)),
            CornerSize(r(shape.bottomStart)),
        )
        is CornerBasedShape -> RoundedCornerShape(
            topStart = r(shape.topStart),
            topEnd = r(shape.topEnd),
            bottomEnd = r(shape.bottomEnd),
            bottomStart = r(shape.bottomStart),
        )
        else -> return null
    }
    return Path().apply {
        addOutline(shrunk.createOutline(Size(w, h), layoutDirection, density))
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