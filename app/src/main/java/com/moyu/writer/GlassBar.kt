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
 * 显式传 `bend` / `squeeze`。默认 2.2 / 2.2 与 3.26.66 的观感逐像素一致：A 的基准
 * 改成未乘 bend 的折射值后，squeeze 要从 1.0 重新标定到 2.2 才等于旧式
 * `A = H × 1.0 = 基准 × 2.2`。
 */

/** 折射带宽上限：不超过表面最小边的这个比例。小芯片（按钮 46dp、通知 36dp）的
 *  折射基准值本来就大（18dp / 15dp），放大后会比自身还大、采样点翻到对侧去
 *  （读成万花筒）。条 56dp：2.2×9.78=21.5dp < 30.8dp 不受影响；46dp 按钮的
 *  18×2.2=39.6dp 会被压到 25.3dp。3.26.66 起倍率可调到 4.0，这道封顶是防
 *  「预览/小表面变万花筒」的关键，预览同样受它约束（同一函数内封顶）。 */
private const val GLASS_LENS_MAX_SIDE = .55f

/** 3.26.67：弯折宽度 / 边缘压缩的量程。数据夹取（Model）、UI 映射（Settings）、
 *  换算（这里）共用一份，避免三处各写一份范围慢慢漂开。 */
internal const val GLASS_BEND_MIN = 1f
internal const val GLASS_BEND_MAX = 4f
internal const val GLASS_SQUEEZE_MIN = .5f
internal const val GLASS_SQUEEZE_MAX = 4f

/**
 * 未显式传 `bend` / `squeeze` 的调用点兜底：直接从数据类默认值派生，不另写字面量。
 * 3.26.67 起 [liquidGlassBar] 不再内部读设置（弯折按部件各一套，必须由调用方指定），
 * 默认值只服务「以后新增调用点漏传」这一种情况，取标签栏那对（2.2 / 2.2 = 3.26.66 观感）。
 */
private val GLASS_BEND_FALLBACK: Float = AppSettings().tabBend
private val GLASS_SQUEEZE_FALLBACK: Float = AppSettings().tabSqueeze

/**
 * 折射带宽度 H（px）的唯一换算入口（标签条 / 选中镜片 / 设置预览共用同一口径）。
 *
 * **只由「弯折宽度」控制**：拖这个滑杆只改带铺多宽，不改折得多狠（位移见
 * [glassLensAmount]）。[bend] 是「设置 → 玻璃 → 对应部件 → 弯折宽度」的倍率；
 * [minSidePx] 传表面最小边，小表面按 [GLASS_LENS_MAX_SIDE] 封顶。
 */
internal fun glassLensBand(
    refractionPx: Float,
    minSidePx: Float,
    bend: Float = GLASS_BEND_FALLBACK,
): Float =
    if (refractionPx <= 0f || minSidePx <= 0f) 0f
    else (refractionPx * bend).coerceAtMost(minSidePx * GLASS_LENS_MAX_SIDE)

/**
 * 最大位移 A（px）的唯一换算入口：**只由「边缘压缩」控制**，与 H 正交（基准取未乘
 * bend 的折射值，所以拖「弯折宽度」不会顺带把边缘内容拉得更狠）。同样按
 * [GLASS_LENS_MAX_SIDE] 封顶——小表面 A 太大会让采样点翻到对侧，读成万花筒。
 */
internal fun glassLensAmount(
    refractionPx: Float,
    minSidePx: Float,
    squeeze: Float = GLASS_SQUEEZE_FALLBACK,
): Float =
    if (refractionPx <= 0f || minSidePx <= 0f) 0f
    else (refractionPx * squeeze).coerceAtMost(minSidePx * GLASS_LENS_MAX_SIDE)

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
    refractionDp: Float = 30f,
    /** 3.26.67：弯折宽度倍率（[GLASS_BEND_MIN]–[GLASS_BEND_MAX]），按部件由调用方传。 */
    bend: Float = GLASS_BEND_FALLBACK,
    /** 3.26.67：边缘压缩倍率（[GLASS_SQUEEZE_MIN]–[GLASS_SQUEEZE_MAX]），只进 A；同上按部件传。 */
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
    // / 通知各读自己那对）；这里只做量程夹取，不再从 LocalInk 里读一对全局值。
    val bendNow = bend.coerceIn(GLASS_BEND_MIN, GLASS_BEND_MAX)
    val squeezeNow = squeeze.coerceIn(GLASS_SQUEEZE_MIN, GLASS_SQUEEZE_MAX)
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
                // 3.26.67：H 只由 bend、A 只由 squeeze 决定（两条式子互不出现对方的
                // 参数），所以两个滑杆的观感真正不同：宽度只改带多宽，压缩只改折多狠。
                val base = refractionDp.dp.toPx()
                val minSide = minOf(size.width, size.height)
                val band = glassLensBand(base, minSide, bendNow)
                val amount = glassLensAmount(base, minSide, squeezeNow)
                lens(
                    refractionHeight = band,
                    refractionAmount = amount,
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