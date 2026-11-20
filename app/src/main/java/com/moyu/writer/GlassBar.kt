package com.moyu.writer

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/**
 * 3.26.65：透镜的「弯折」与「边缘压缩」都来自 Backdrop 1.0.0 的 `lens()`，它只
 * 动边缘往里一圈的采样点（带内），带外的像素原样返回：
 *
 * ```
 * k   = -sd                                  // 离边缘的距离（sd 是圆角矩形 SDF）
 * d   = (1 - sqrt(1 - (1 - k/H)^2)) * A      // 圆弧剖面：边缘最大、带到头归零
 * dir = normalize(sdfNormal + depthEffect * normalize(centeredCoord))
 * 采样点 = coord - d * dir                    // Kotlin 侧把 A 取负，所以是「朝中心」采
 * ```
 *
 * H（refractionHeight）决定「弯折」铺多宽，A（refractionAmount）决定最外圈
 * 「压缩」多狠。3.26.58 原版把滑杆值同时当 H 和 A（9.78dp），在 56dp 高的条上
 * 只占一圈 17% 的窄边 → 只能读出「边上有点扭曲」，读不出整块玻璃被掰弯。
 *
 * 3.26.66：放大倍率与压缩强度不再写死，改成设置项。3.26.67 起这两个量**按部件各
 * 一套**（标签栏 [AppSettings.tabBend] / 镜片 [AppSettings.lensBend] / 通知
 * [AppSettings.bubbleBend]，各带自己的 squeeze），并且拆成**正交**的两个滑杆：
 * 「弯折宽度」只进 [glassLensBand]（H），「边缘压缩」只进 [glassLensAmount]（A）——
 * 3.26.66 的 `A = H × squeeze` 会让两个滑杆看起来都只在放大同一件事（用户实测
 * 「弯折宽度和边缘折射效果一样」）。`liquidGlassBar` 不再自己读设置，由调用方
 * 显式传 `bend` / `squeeze`。
 *
 * 3.26.67（第二次修正）：两个设置值改成「**占物理上限的百分比**」——设
 * `cap = 表面最小边 × [GLASS_LENS_MAX_SIDE]`，则 `H = cap × 宽度%`、`A = cap × 压缩%`。
 * 这样 0%→0、100%→物理上限，滑杆**全程有效、永不饱和**（旧「基准 × 倍率」在
 * 小表面上会被 cap 提前压住，通知组一半行程是死的）。数据类默认值按
 * `旧物理量 / cap` 标定，默认观感仍逐像素等于 3.26.66/3.26.67 前一版。
 */

/** 折射物理上限比例：`cap = minSide × 这个值`，H 与 A 的最大值都是它。
 *  0.55 是防「采样点翻到对侧变万花筒」的实测上限：条 64dp → 35.2dp、
 *  镜片 56dp → 30.8dp、通知 toast 42dp → 23.1dp。3.26.67 起它不再只是封顶，
 *  而是滑杆 100% 对应的物理值（设置值本身是该值的百分比）。 */
internal const val GLASS_LENS_MAX_SIDE = .55f

/** 3.26.67：弯折宽度 / 边缘压缩都是「占物理上限的百分比」，取值 0..1。
 *  数据夹取（Model）与换算（这里）共用一份，避免两处范围漂开。 */
internal const val GLASS_FRACTION_MIN = 0f
internal const val GLASS_FRACTION_MAX = 1f

/**
 * 未显式传 `bend` / `squeeze` 的调用点兜底：直接从数据类默认值派生，不另写字面量。
 * 3.26.67 起 [liquidGlassBar] 不再内部读设置（弯折按部件各一套，必须由调用方指定），
 * 默认值只服务「以后新增调用点漏传」这一种情况。
 */
private val GLASS_BEND_FALLBACK: Float = AppSettings().tabBend
private val GLASS_SQUEEZE_FALLBACK: Float = AppSettings().tabSqueeze

/** 物理上限（px）：H / A 都先取它，再乘各自的百分比。 */
internal fun glassLensCapPx(minSidePx: Float): Float =
    if (minSidePx <= 0f) 0f else minSidePx * GLASS_LENS_MAX_SIDE

/**
 * 折射带宽度 H（px）：**只由「弯折宽度」控制** —— H = 物理上限 × 宽度百分比。
 * 0% → 0（无弯折带）、100% → 上限，全程单调有效。[fraction] 就是设置里那个 0..1 的值，
 * [minSidePx] 传表面最小边。
 */
internal fun glassLensBand(
    minSidePx: Float,
    fraction: Float = GLASS_BEND_FALLBACK,
): Float = glassLensCapPx(minSidePx) * fraction.coerceIn(GLASS_FRACTION_MIN, GLASS_FRACTION_MAX)

/**
 * 最大位移 A（px）：**只由「边缘压缩」控制** —— A = 物理上限 × 压缩百分比。
 * 与 H 完全正交（式子里不出现 bend），同样 0%→0、100%→上限、全程有效。
 */
internal fun glassLensAmount(
    minSidePx: Float,
    fraction: Float = GLASS_SQUEEZE_FALLBACK,
): Float = glassLensCapPx(minSidePx) * fraction.coerceIn(GLASS_FRACTION_MIN, GLASS_FRACTION_MAX)

@Composable
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
    /** 3.26.67：存量「边缘折射」基准 B 现在只当**启用开关**（>0 才画折射）。
     *  H / A 已改成「物理上限百分比」，B 不再参与它们的换算。 */
    refractionDp: Float = 30f,
    /** 3.26.67：弯折宽度 = 物理上限（`minSide × .55`）的百分比，0..1；0%→无弯折带。 */
    bend: Float = GLASS_BEND_FALLBACK,
    /** 3.26.67：边缘压缩 = 物理上限的百分比，0..1；与弯折宽度正交。 */
    squeeze: Float = GLASS_SQUEEZE_FALLBACK,
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
    // 3.26.67：弯折宽度 / 边缘压缩按部件各一套，由调用方显式传（真标签栏 / 选中镜片
    // / 通知各读自己那对）；两者都是「物理上限的百分比」，只在这里夹到 0..1。
    val bendNow = bend.coerceIn(GLASS_FRACTION_MIN, GLASS_FRACTION_MAX)
    val squeezeNow = squeeze.coerceIn(GLASS_FRACTION_MIN, GLASS_FRACTION_MAX)
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
    val canRefract = shape is androidx.compose.foundation.shape.CornerBasedShape && refractionDp > 0f
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            if (blurDp > 0f) blur(blurDp.dp.toPx())
            if (canRefract) {
                // depthEffect = true 是「弯折」的另一半：它把每处的偏移方向从
                // 「垂直于最近这条边」拧向「从形状中心指向该点」（两项相加再归一），
                // 四条边的位移都朝中心收，才读成一块凸透镜；false 时四边各自笔直
                // 往里挤，中间还留着法线跳变的折痕（只像一圈斜面，不像弯）。
                // 3.26.67：H = cap × 宽度%、A = cap × 压缩%（两条式子互不出现对方
                // 参数），所以两个滑杆真正正交、且 0..100% 全程有效（不再饱和）。
                // band == 0 时直接不调 lens()：refractionHeight = 0 会让 shader 里
                // 的圆弧剖面除以 0（旧模型滑杆最小值是 1 倍率，不会出现 0）。
                val minSide = minOf(size.width, size.height)
                val band = glassLensBand(minSide, bendNow)
                if (band > 0f) {
                    lens(
                        refractionHeight = band,
                        refractionAmount = glassLensAmount(minSide, squeezeNow),
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
            if (edgeStrength > 0f) {
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
            // 3.26.67：弯折/压缩按部件各一套。这条链现在只剩「通知 / toast」还活着
            // （输入框与按钮表面已不用玻璃），所以统一读通知那一对。
            bend = s.bubbleBend,
            squeeze = s.bubbleSqueeze,
            dispersion = dispersion,
            shadowAlpha = shadow,
            veil = veil ?: baseVeil,
            edgeStrength = edge,
        ),
        content = content,
    )
}