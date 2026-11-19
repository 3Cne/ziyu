package com.moyu.writer

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val lightGlassBase = Color(0x55FFFFFF)
private val darkGlassBase = Color(0x2A1C1E1C)

data class InkPalette(
    val bgTop: Color,
    val bgBottom: Color,
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    /**
     * 0 = light, 1 = dark. MoyuTheme animates this with the same 220 ms spec as the
     * palette colours, so a day/night switch can cross-fade everything derived from
     * "is the theme dark" instead of snapping it on the first frame.
     */
    val darkAmount: Float,
) {
    /**
     * Single flip instant for consumers that need a boolean (status-bar icon
     * appearance, structural branches). It changes at the middle of the day/night
     * cross-fade, so every boolean consumer flips at the same moment instead of on
     * frame 0.
     */
    val isDark: Boolean get() = darkAmount >= .5f
    /** Both themes used the same white tint; kept as one source instead of an isDark branch. */
    val glassTint: Color get() = Color.White
    /** Cross-fades with [darkAmount] instead of reading the raw theme flag. */
    val glassBase: Color get() = lerp(lightGlassBase, darkGlassBase, darkAmount)
}

data class InkUi(
    val palette: InkPalette,
    val settings: AppSettings,
    val accent: Color,
    val serif: Boolean,
)

val LocalInk = staticCompositionLocalOf {
    InkUi(
        palette = InkPalette(
            bgTop = Color(0xFFF8F6F1),
            bgBottom = Color(0xFFEFEAE0),
            paper = Color(0xFFF7F3EA),
            ink = Color(0xFF191813),
            muted = Color(0xFF8A857B),
            faint = Color(0x0F1B1710),
            line = Color(0x1A1B1710),
            darkAmount = 0f,
        ),
        settings = AppSettings(),
        accent = Color(0xFF22221E),
        serif = true,
    )
}

val LocalHaptic = compositionLocalOf<(Int) -> Unit> { {} }
val LocalHazeHandle = staticCompositionLocalOf<HazeBridge.Handle?> { null }

val LocalStore = staticCompositionLocalOf<AppStore> { error("AppStore missing") }

private val toastId = AtomicLong(0)
object ToastBus {
    var message by mutableStateOf<String?>(null)
        private set
    var glyph by mutableStateOf<Glyph?>(null)
        private set
    var id by mutableStateOf(0L)
        private set
    private val handler = Handler(Looper.getMainLooper())
    fun show(text: String, long: Boolean = false, glyph: Glyph? = null) {
        val token = toastId.incrementAndGet()
        id = token
        message = text
        this.glyph = glyph
        handler.postDelayed({
            if (id == token) message = null
        }, if (long) 3400L else 2100L)
    }
}

fun printToast(text: String) = ToastBus.show(text)

@Composable
fun MoyuTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        "night" -> true
        "system" -> androidx.compose.foundation.isSystemInDarkTheme()
        else -> false
    }
    val palette = paletteFor(settings.theme, dark)
    val accentTarget = accentFor(settings.accent, dark)
    // 3.26.50：昼夜/换主题的整屏颜色过渡，唯一出口仍然在这里（LocalInk 的出口）。
    // 3.26.49 只动画了 paper/ink/muted/line/accent，`isDark` 还是布尔硬翻，于是纸底
    // （bgTop/bgBottom/faint）、玻璃底色和所有 if (isDark) 分支在主题切换的第一帧就
    // 跳到目标值，观感是「文字渐变、背景跳」。现在：
    //  ① bgTop/bgBottom/faint 与其它颜色共用同一个 220ms spec（motion=none → tween(0)）；
    //  ② 额外动画 darkAmount，InkPalette.isDark 由它派生（中点翻转），glassBase /
    //     玻璃填充等由 isDark 派生的颜色改按 darkAmount 连续插值；
    //  ③ 全屏底色只在 MoyuTheme 这里动画一次：AmbientBackground 里原来那层
    //     spring(420) 的 animateColorAsState 已删除，避免两层追同一个目标。
    val colorSpec: FiniteAnimationSpec<Color> =
        if (settings.motion == "none") tween(0) else tween(220)
    val amountSpec: FiniteAnimationSpec<Float> =
        if (settings.motion == "none") tween(0) else tween(220)
    val paper by animateColorAsState(palette.paper, colorSpec, label = "themePaper")
    val ink by animateColorAsState(palette.ink, colorSpec, label = "themeInk")
    val muted by animateColorAsState(palette.muted, colorSpec, label = "themeMuted")
    val line by animateColorAsState(palette.line, colorSpec, label = "themeLine")
    val accent by animateColorAsState(accentTarget, colorSpec, label = "themeAccent")
    val bgTop by animateColorAsState(palette.bgTop, colorSpec, label = "themeBgTop")
    val bgBottom by animateColorAsState(palette.bgBottom, colorSpec, label = "themeBgBottom")
    val faint by animateColorAsState(palette.faint, colorSpec, label = "themeFaint")
    val darkAmount by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = amountSpec,
        label = "themeDarkAmount",
    )
    val animatedPalette = palette.copy(
        bgTop = bgTop,
        bgBottom = bgBottom,
        paper = paper,
        ink = ink,
        muted = muted,
        faint = faint,
        line = line,
        darkAmount = darkAmount,
    )
    val view = LocalView.current
    val haptics = settings.haptics
    // 系统栏图标跟着动画后的明暗翻转，而不是第一帧就翻：否则浅底白图标 / 深底黑图标
    // 会各占过渡的一半。翻转时刻与所有 isDark 布尔分支一致（中点）。
    LaunchedEffect(animatedPalette.isDark) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !animatedPalette.isDark
        controller.isAppearanceLightNavigationBars = !animatedPalette.isDark
    }
    val haptic: (Int) -> Unit = remember(haptics) {
        { type ->
            if (haptics) {
                try {
                    view.performHapticFeedback(
                        when (type) {
                            1 -> HapticFeedbackConstants.LONG_PRESS
                            2 -> HapticFeedbackConstants.CONFIRM
                            else -> HapticFeedbackConstants.CLOCK_TICK
                        }
                    )
                } catch (_: Throwable) {
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalInk provides InkUi(animatedPalette, settings, accent, settings.font != "sans"),
        LocalHaptic provides haptic,
    ) { content() }
}

fun paletteFor(theme: String, dark: Boolean): InkPalette {
    if (dark) return InkPalette(
        bgTop = Color(0xFF111312),
        bgBottom = Color(0xFF080909),
        paper = Color(0xFF151716),
        ink = Color(0xFFF0EDE5),
        muted = Color(0xFF8C8E87),
        faint = Color(0x14FFFFFF),
        line = Color(0x18FFFFFF),
        darkAmount = 1f,
    )
    return when (theme) {
        "mist" -> InkPalette(
            bgTop = Color(0xFFF5F8F8),
            bgBottom = Color(0xFFE6EDEE),
            paper = Color(0xFFF7FAFA),
            ink = Color(0xFF152022),
            muted = Color(0xFF788789),
            faint = Color(0x0D183033),
            line = Color(0x18183033),
            darkAmount = 0f,
        )
        "white" -> InkPalette(
            bgTop = Color(0xFFFDFDFB),
            bgBottom = Color(0xFFF2F2EE),
            paper = Color(0xFFFFFEFB),
            ink = Color(0xFF161613),
            muted = Color(0xFF8B8B85),
            faint = Color(0x0D161613),
            line = Color(0x17161613),
            darkAmount = 0f,
        )
        "sepia" -> InkPalette(
            bgTop = Color(0xFFF7F0E1),
            bgBottom = Color(0xFFEADFC9),
            paper = Color(0xFFF8F1E2),
            ink = Color(0xFF211B11),
            muted = Color(0xFF8D806A),
            faint = Color(0x12281E0D),
            line = Color(0x1E281E0D),
            darkAmount = 0f,
        )
        "night" -> InkPalette(
            bgTop = Color(0xFF111312),
            bgBottom = Color(0xFF080909),
            paper = Color(0xFF151716),
            ink = Color(0xFFF0EDE5),
            muted = Color(0xFF8C8E87),
            faint = Color(0x14FFFFFF),
            line = Color(0x18FFFFFF),
            darkAmount = 1f,
        )
        else -> InkPalette(
            bgTop = Color(0xFFF8F6F1),
            bgBottom = Color(0xFFEFEAE0),
            paper = Color(0xFFF7F3EA),
            ink = Color(0xFF191813),
            muted = Color(0xFF8A857B),
            faint = Color(0x0F1B1710),
            line = Color(0x1A1B1710),
            darkAmount = 0f,
        )
    }
}

fun fontFamilyFor(name: String): FontFamily = when (name) {
    "sans" -> FontFamily.SansSerif
    "mono" -> FontFamily.Monospace
    else -> FontFamily.Serif
}

/**
 * One damping for the whole app: 1.0 (critically damped, no overshoot).
 * "肉感" comes from low stiffness plus press deformation, not from bouncing
 * past the target. Only stiffness changes between the four motion presets.
 */
const val MOYU_DAMPING = 1f

/**
 * Compose springs stop as soon as they are within `visibilityThreshold` of the
 * target and then snap the remaining distance. The default 0.01 is huge for
 * page offsets (0.01 * 1220px = 12px) and for press scale (1% of the view), and
 * that snap is the "最后吸一下". These thresholds are expressed in each
 * animation's own unit so the spring actually settles instead of jumping.
 */
const val PAGE_THRESHOLD = 1e-4f
const val PRESS_THRESHOLD = 2e-4f
const val PROGRESS_THRESHOLD = 1e-3f

fun motionSpec(style: String): androidx.compose.animation.core.SpringSpec<Float> = when (style) {
    "gentle" -> spring(dampingRatio = MOYU_DAMPING, stiffness = 300f, visibilityThreshold = PROGRESS_THRESHOLD)
    "snappy" -> spring(dampingRatio = MOYU_DAMPING, stiffness = 700f, visibilityThreshold = PROGRESS_THRESHOLD)
    "none" -> spring(dampingRatio = MOYU_DAMPING, stiffness = 2000f, visibilityThreshold = PROGRESS_THRESHOLD)
    else -> spring(dampingRatio = MOYU_DAMPING, stiffness = 420f, visibilityThreshold = PROGRESS_THRESHOLD)
}

/**
 * Page-to-page stiffness. The four motion presets still change how long a
 * page transition reads: shorter perPageMs becomes a stiffer spring, but the
 * damping stays 1.0 so pages never overshoot their target.
 */
fun pageStiffness(perPageMs: Int): Float =
    if (perPageMs <= 0) 2000f else (90000f / perPageMs).coerceIn(240f, 900f)

fun motionSpecDp(style: String) = when (style) {
    "gentle" -> spring<Dp>(dampingRatio = MOYU_DAMPING, stiffness = 300f, visibilityThreshold = .001.dp)
    "snappy" -> spring<Dp>(dampingRatio = MOYU_DAMPING, stiffness = 700f, visibilityThreshold = .001.dp)
    "none" -> spring<Dp>(dampingRatio = MOYU_DAMPING, stiffness = 2000f, visibilityThreshold = .001.dp)
    else -> spring<Dp>(dampingRatio = MOYU_DAMPING, stiffness = 420f, visibilityThreshold = .001.dp)
}

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: Float = 14f,
    color: Color = LocalInk.current.palette.ink,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float = size * 1.45f,
    letterSpacing: Float = 0f,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontFamily: FontFamily? = null,
    decoration: TextDecoration = TextDecoration.None,
) {
    val ui = LocalInk.current
    BasicText(
        text = AnnotatedString(text, spanStyle = SpanStyle(textDecoration = decoration)),
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            lineHeight = lineHeight.sp,
            letterSpacing = letterSpacing.sp,
            textAlign = align,
            fontFamily = fontFamily ?: fontFamilyFor(ui.settings.font),
            textDecoration = decoration,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * 富文本版 [Txt]：样式与字符串版完全一致，只换承载。
 *
 * 3.26.33：AI 回答要画粗体/列表，需要 AnnotatedString；样式（字体族按设置、
 * 字号、行高、字色）必须和普通文字同源，否则气泡里会出现两种字距。
 */
@Composable
fun Txt(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    size: Float = 14f,
    color: Color = LocalInk.current.palette.ink,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float = size * 1.45f,
    letterSpacing: Float = 0f,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontFamily: FontFamily? = null,
    decoration: TextDecoration = TextDecoration.None,
) {
    val ui = LocalInk.current
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            lineHeight = lineHeight.sp,
            letterSpacing = letterSpacing.sp,
            textAlign = align,
            fontFamily = fontFamily ?: fontFamilyFor(ui.settings.font),
            textDecoration = decoration,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * One continuous liquid-glass layer.
 *
 * The important part here is what is *not* drawn: no flat backing layer, no inner
 * dark band, no second rounded rectangle. A single frosted gradient, one soft
 * caustic bloom, one refractive rim and grain keep the material reading as one
 * piece of glass instead of stacked translucent plates.
 */
@Composable
fun Modifier.liquidGlass(
    radius: Float = 26f,
    strength: Float = 1f,
    tint: Color = Color.White,
    frost: Float = .62f,
    iridescent: Boolean = true,
    specular: Boolean = true,
    shape: Shape? = null,
    fillOverride: Float? = null,
    blurOverride: Float? = null,
    grainOverride: Boolean? = null,
    edgeWidth: Float = 1.1f,
    innerEdgeWidth: Float = .6f,
    rimStrength: Float = 1f,
    specularStrength: Float = 1f,
    refractionStrength: Float = 1f,
    borderColor: Color? = null,
): Modifier {
    val ui = LocalInk.current
    // Read the live settings in composition, not only inside drawWithCache.
    // The draw cache was not reliably invalidated by a Slider change, so the
    // glass controls appeared disconnected from every existing surface.
    val p = ui.palette
    // Global glass/blur/brightness sliders were removed: every MoyuSurface
    // passes per-role overrides, so those controls were dead there. Keep the
    // old button defaults locally instead of exposing a setting that cannot
    // reach the real surfaces.
    val glassSetting = .64f
    val blurSetting = .55f
    val grainSetting = ui.settings.grain
    val brightnessSetting = 0f
    val accentColor = ui.accent
    return drawWithCache {
        val r = (radius * density).coerceAtMost(min(size.width, size.height) / 2f)
        val corner = CornerRadius(r, r)
        val outlinePath = shape?.let { s ->
            Path().apply { addOutline(s.createOutline(size, layoutDirection, this@drawWithCache)) }
        }
        val w = size.width
        val h = size.height
        val glass = glassSetting
        // Higher "通透度" must mean more transparent, not more opaque. The
        // old curve also varied too little to see on light paper.
        val blurFactor = .35f + (blurOverride ?: blurSetting) * 1.60f
        val opacity = fillOverride?.coerceIn(0f, 1f)
            ?: (frost * (1.45f - glass * 1.05f) * blurFactor * strength).coerceIn(0f, 1f)
        // Fill is a real visual range, not a tiny alpha nudge: 0 is almost
        // clear, 100 is a solid paper surface. A slight ink shift prevents the
        // high end from disappearing into white paper.
        val fillAmount = (fillOverride ?: opacity).coerceIn(0f, 1f)
        val bodyAlpha = (0.04f + 0.96f * fillAmount).coerceIn(0f, 1f)
        // 3.26.50：昼夜过渡时这些量不再读原始 isDark（主题切换第一帧硬翻），而是按
        // 动画后的 darkAmount 连续插值；静止态 darkAmount 恰为 0/1，像素与旧版一致。
        val d = p.darkAmount
        fun mix(light: Float, dark: Float): Float = light + (dark - light) * d
        val frostColor = lerp(tint, Color(0xFF191C1A), d)
        val bodyTint = lerp(frostColor, lerp(p.ink, Color.White, d), fillAmount * .12f)
        fun rimAlpha(alpha: Float): Float =
            (alpha * rimStrength).coerceIn(0f, 1f)
        // MoyuSurface always passes frost = 1f. Treating that as a solid
        // surface made fillOverride irrelevant: every card used the fixed
        // 96/98/99% branch and the Fill slider did nothing.
        val solid = frost >= .99f && fillOverride == null
        // The gradient used to keep a fixed 10/16/26 percent of transparency even at
// fill = 1, so a control set to 100 dispensed still let the page show through
// and its labels ghosted onto whatever sat behind. Interpolate towards fully
// opaque instead: at fill 1 the three stops are all alpha 1, and the old
// falloff only applies below that.
        // Dark and light used different gradient stops. Blend both the stop colours and
        // their alphas by darkAmount so a theme switch fades the surface instead of
        // swapping brushes in one frame; at d = 0 / 1 this is exactly the old branch.
        val darkStops = if (fillAmount >= 1f) {
            floatArrayOf(1f, 1f, 1f)
        } else {
            floatArrayOf(.045f, .86f, .96f)
        }
        val lightStops = if (fillAmount >= 1f) {
            floatArrayOf(1f, 1f, 1f)
        } else {
            floatArrayOf(.90f, .84f, .74f)
        }
        val fill = Brush.linearGradient(
            colors = listOf(
                lerp(bodyTint, Color.White, d).copy(
                    alpha = mix(if (solid) .96f else bodyAlpha * lightStops[0], if (solid) .06f else bodyAlpha * darkStops[0]),
                ),
                lerp(bodyTint, frostColor, d).copy(
                    alpha = mix(if (solid) .985f else bodyAlpha * lightStops[1], if (solid) .985f else bodyAlpha * darkStops[1]),
                ),
                bodyTint.copy(
                    alpha = mix(if (solid) .99f else bodyAlpha * lightStops[2], if (solid) .99f else bodyAlpha * darkStops[2]),
                ),
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        )
        val caustic = Brush.radialGradient(
            colors = listOf(
                // 折射 is the only thing that may push these two glows around.
                // Scaling them by the fill as well meant that raising 填充 made
                // the surface look hazier instead of more opaque, which is the
                // opposite of what the slider promises.
                Color.White.copy(
                    alpha = mix(.52f, .06f) * refractionStrength,
                ),
                Color.Transparent,
            ),
            center = Offset(w * .20f, h * .015f),
            radius = w * .86f,
        )
        val lowerCaustic = Brush.radialGradient(
            colors = listOf(
                (if (iridescent) accentColor else Color.White)
                    .copy(alpha = .14f * refractionStrength),
                Color.Transparent,
            ),
            center = Offset(w * .86f, h * .94f),
            radius = w * .72f,
        )
        // 3.26.53：卡片 / 弹窗的边缘厚度。旧 rim 是「墨色 → 白高光 → 缀色」的斜向
        // 渐变，而 MoyuSurface 传的 edgeWidth = 0f 让整条描边宽度为 0 —— 卡片边缘
        // 实际只剩 .95dp 的 specular，看不出厚度。现在改成贴轮廓的「外圈亮环 +
        // 内圈次高光」两道纯白竖渐变：顶亮、侧中、底暗，没有暗斜面、没有外发光。
        // 亮度只有 1 型玻璃条的一半（顶 .35 / 侧 .18 / 底 .08 级别）；昼夜按
        // darkAmount 连续插值（3.26.50 约定），过渡时不会硬翻。
        val rim = Brush.verticalGradient(
            0f to Color.White.copy(alpha = rimAlpha(mix(.35f, .30f))),
            .45f to Color.White.copy(alpha = rimAlpha(mix(.18f, .15f))),
            1f to Color.White.copy(alpha = rimAlpha(mix(.08f, .065f))),
        )
        // 次高光比亮环再弱一档（约一半），两张主题都保持同一层「纸面收边」的观感。
        val innerRim = Brush.verticalGradient(
            0f to Color.White.copy(alpha = rimAlpha(mix(.16f, .13f))),
            .45f to Color.White.copy(alpha = rimAlpha(mix(.09f, .07f))),
            1f to Color.White.copy(alpha = rimAlpha(mix(.04f, .03f))),
        )
        // 亮环沿「内缩轮廓」画：整条线都落在表面内部，不依赖外层圆角裁掉一半，
        // 也避免 0.8dp 细线被硬裁出锯齿。外圈可见宽度 = edgeWidth，内圈紧接其内。
        val ringPx = edgeWidth.dp.toPx()
        val innerRingPx = innerEdgeWidth.dp.toPx()
        val rimShape: Shape? = if (rimStrength > 0f && ringPx > 0f) {
            shape ?: SmoothCorner(radius.dp)
        } else null
        val outerRingPath = rimShape?.let {
            insetOutlinePath(it, size, ringPx / 2f, layoutDirection, this)
        }
        val innerRingPath = if (rimShape != null && innerRingPx > 0f) {
            insetOutlinePath(
                rimShape,
                size,
                ringPx + innerRingPx / 2f,
                layoutDirection,
                this,
            )
        } else null
        onDrawWithContent {
            fun fillLayer(brush: Brush) {
                if (outlinePath != null) drawPath(outlinePath, brush)
                else drawRoundRect(brush, cornerRadius = corner)
            }
            fillLayer(fill)
            fillLayer(caustic)
            fillLayer(lowerCaustic)
            val frameWidth = edgeWidth.dp.toPx()
            if (outlinePath != null) {
                // borderColor 是调用点显式要求的边框，语义不变，整条保留。
                if (borderColor != null) {
                    drawPath(outlinePath, borderColor, style = Stroke(width = frameWidth))
                }
            } else if (borderColor != null) {
                // A centred stroke loses its outer half to GlassSurface's
                // rounded clip, producing a thin outer line plus a second
                // inner ring. Inset the stroke by half its width so the whole
                // frame lives inside the card and reads as one solid edge.
                val inset = frameWidth / 2f
                drawRoundRect(
                    borderColor,
                    topLeft = Offset(inset, inset),
                    size = Size(
                        (w - inset * 2f).coerceAtLeast(0f),
                        (h - inset * 2f).coerceAtLeast(0f),
                    ),
                    cornerRadius = CornerRadius((r - inset).coerceAtLeast(0f)),
                    style = Stroke(width = frameWidth),
                )
            }
            if (borderColor == null && rimStrength > 0f) {
                if (outerRingPath != null) {
                    // 外圈亮环 + 内圈次高光各画在一条内缩轮廓上，两条相邻，
                    // 合计约 1.4dp 的收边，但没有任何暗色斜面。
                    translate(ringPx / 2f, ringPx / 2f) {
                        drawPath(outerRingPath, rim, style = Stroke(width = ringPx))
                    }
                    if (innerRingPath != null) {
                        translate(ringPx + innerRingPx / 2f, ringPx + innerRingPx / 2f) {
                            drawPath(innerRingPath, innerRim, style = Stroke(width = innerRingPx))
                        }
                    }
                } else if (outlinePath != null && frameWidth > 0f) {
                    // 任意 Shape（无法精确内缩）退回「双倍线宽 + 裁掉外侧一半」，
                    // 可见宽度仍等于 edgeWidth。
                    clipPath(outlinePath) {
                        drawPath(outlinePath, rim, style = Stroke(width = 2f * frameWidth))
                    }
                }
            }
            if (specular && specularStrength > 0f) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(
                                alpha = mix(.94f, .20f) * specularStrength,
                            ),
                            Color.Transparent,
                        ),
                        startX = w * .06f,
                        endX = w * .78f,
                    ),
                    start = Offset(w * .06f, .8.dp.toPx()),
                    end = Offset(w * .78f, .8.dp.toPx()),
                    strokeWidth = .95.dp.toPx(),
                )
            }
            if (grainOverride ?: grainSetting) {
                var i = 0
                val dots = 28
                while (i < dots) {
                    val n = i * 2.399f
                    val x = (sin(n) * .5f + .5f) * w
                    val y = (sin(n * 1.71f + .4f) * .5f + .5f) * h
                    drawCircle(
                        Color.White.copy(alpha = mix(.05f, .022f)),
                        .6f.dp.toPx(),
                        Offset(x, y),
                    )
                    i++
                }
            }
            if (brightnessSetting > 0f) {
                val brush = SolidColor(Color.White.copy(alpha = brightnessSetting * .30f))
                if (outlinePath != null) drawPath(outlinePath, brush)
                else drawRoundRect(brush, cornerRadius = corner, size = Size(w, h))
            } else if (brightnessSetting < 0f) {
                val brush = SolidColor(Color.Black.copy(alpha = -brightnessSetting * .32f))
                if (outlinePath != null) drawPath(outlinePath, brush)
                else drawRoundRect(brush, cornerRadius = corner, size = Size(w, h))
            }
            drawContent()
        }
    }
}

/**
 * 把 [shape] 的轮廓沿四边往里缩 [inset] px 后的路径。
 *
 * 不采样 / 腐蚀任意 Path：Shape 本身能在小一圈的尺寸上重新生成轮廓，只要把四个
 * 圆角半径同步减掉 [inset]——胶囊、圆、固定 dp 圆角、连续曲率圆角（SmoothCornerShape）
 * 都适用。只支持 [CornerBasedShape]；其它 Shape 返回 null，调用方退回「裁掉外侧
 * 一半」的画法。3.26.53 的卡片 / 弹窗边缘亮环沿这条内缩轮廓画，整条线都落在表面
 * 内部，不会被外层圆角硬裁；内圈次高光再往里缩一档，两圈相邻就是一条收边。
 */
private fun insetOutlinePath(
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
    fun radiusOf(corner: CornerSize): Dp =
        ((corner.toPx(size, density) - inset).coerceAtLeast(0f) / density.density).dp
    val shrunk: Shape = when (shape) {
        is SmoothCornerShape -> SmoothCornerShape(
            CornerSize(radiusOf(shape.topStart)),
            CornerSize(radiusOf(shape.topEnd)),
            CornerSize(radiusOf(shape.bottomEnd)),
            CornerSize(radiusOf(shape.bottomStart)),
        )
        is CornerBasedShape -> RoundedCornerShape(
            topStart = radiusOf(shape.topStart),
            topEnd = radiusOf(shape.topEnd),
            bottomEnd = radiusOf(shape.bottomEnd),
            bottomStart = radiusOf(shape.bottomStart),
        )
        else -> return null
    }
    return Path().apply {
        addOutline(shrunk.createOutline(Size(w, h), layoutDirection, density))
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Float = 26f,
    strength: Float = 1f,
    frost: Float = .68f,
    tint: Color = Color.White,
    haze: HazeBridge.Handle? = null,
    useHaze: Boolean = true,
    hazeBlur: Float = 58f,
    specular: Boolean = true,
    shape: Shape? = null,
    fillOverride: Float? = null,
    blurOverride: Float? = null,
    grainOverride: Boolean? = null,
    edgeWidth: Float = 1.1f,
    innerEdgeWidth: Float = .6f,
    rimStrength: Float = 1f,
    specularStrength: Float = 1f,
    refractionStrength: Float = 1f,
    borderColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedShape = shape ?: SmoothCorner(radius.dp)
    val clipped = modifier.clip(resolvedShape)
    // Haze used to hard-code a 25% white tint, which turned the dark TabBar into
    // a flat gray slab. Tint the blur toward the paper color in each theme, and
    // cross-fade that tint by darkAmount so the blur does not swap in one frame
    // while the rest of the palette is still animating.
    val hazeTint = lerp(
        Color(0x40FFFFFF),
        Color(0x4D101312),
        LocalInk.current.palette.darkAmount,
    ).toArgb().toLong()
    val blurScale = .55f
    val blurValue = (blurOverride ?: blurScale).coerceIn(0f, 1f)
    // 模糊 0 must mean no blurring at all. The old curve kept a 35% floor, so
    // pulling the slider to 0 still left the backdrop smeared and the setting
    // read as broken.
    val effectiveBlur = hazeBlur * (blurValue * 1.75f)
    val liveHaze = if (useHaze) haze ?: LocalHazeHandle.current else null
    val glass = if (liveHaze != null) HazeBridge.effect(clipped, liveHaze, effectiveBlur, hazeTint) else clipped
    Box(
        glass.liquidGlass(
            radius = radius,
            strength = strength,
            tint = tint,
            frost = frost,
            specular = specular,
            shape = shape,
            fillOverride = fillOverride,
            blurOverride = blurOverride,
            grainOverride = grainOverride,
            edgeWidth = edgeWidth,
            innerEdgeWidth = innerEdgeWidth,
            rimStrength = rimStrength,
            specularStrength = specularStrength,
            refractionStrength = refractionStrength,
            borderColor = borderColor,
        )
    ) {
        content()
    }
}

@Composable
fun Modifier.bouncy(
    enabled: Boolean = true,
    scale: Float = .95f,
    hapticType: Int = 0,
    /** Release with a small overshoot so even a quick tap reads as a bounce. */
    overshoot: Boolean = false,
    /** When present the same modifier owns tap and long press; two detectors
     *  would race for the same down. */
    onLongPress: (() -> Unit)? = null,
    onPressChange: (Boolean) -> Unit = {},
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHaptic.current
    val motion = LocalInk.current.settings.motion
    // Optional hook so a parent can react to the press (the liquid tab bar uses
    // it to squash its lens). Defaults to a no-op for every existing caller.
    LaunchedEffect(pressed) { onPressChange(pressed) }
    val anim = remember { Animatable(1f) }
    var stretch by remember { mutableFloatStateOf(0f) }
    var originX by remember { mutableFloatStateOf(.5f) }
    var last by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(pressed, enabled) {
        val down = pressed && enabled
        if (motion == "none") {
            anim.snapTo(if (down) scale else 1f)
            stretch = 0f
            return@LaunchedEffect
        }
        last = anim.value
        if (down) {
            // 水滴按压：帧位移驱动横向拉伸、纵向轻压，原点在按下的位置。
            // 位移按 (1-scale) 归一，dock 的 .88 和普通按钮的 .97 得到同一档
            // 形变，不会因为按得深就甩出去。
            anim.animateTo(scale, spring(MOYU_DAMPING, 620f, PRESS_THRESHOLD)) {
                val step = kotlin.math.abs(value - last)
                last = value
                val unit = (step / (1f - scale).coerceAtLeast(.02f)).coerceIn(0f, 1f)
                stretch += ((unit * .12f).coerceIn(0f, .06f) - stretch) * .5f
            }
        } else {
            // No overshoot: damping 1.0 settles exactly on 1.
            anim.animateTo(1f, spring(MOYU_DAMPING, 460f, PRESS_THRESHOLD)) {
                last = value
                stretch += (0f - stretch) * .35f
            }
        }
        stretch = 0f
    }
    val clicks = if (onLongPress != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onLongClick = { haptic(hapticType); onLongPress() },
            onClick = { haptic(hapticType); onClick() },
        )
    } else {
        Modifier.clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            haptic(hapticType)
            onClick()
        }
    }
    return this
        .pointerInput(enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                originX = (down.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                waitForUpOrCancellation()
            }
        }
        .graphicsLayer {
            // 形变按像素封顶：小按钮按比例甩，整行/大卡片最多 3.5dp，
            // 否则 300dp 的卡片按 6% 算会横甩 18dp。
            val px = (stretch * size.width).coerceAtMost(3.5.dp.toPx())
            val sx = if (size.width > 0f) px / size.width else 0f
            scaleX = anim.value + sx
            scaleY = anim.value - sx * .6f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(originX, .5f)
        }
        .then(clicks)
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    enabled: Boolean = true,
    dark: Boolean = false,
) {
    val ui = LocalInk.current
    // 3.26.53：按钮在昼夜之间是反相的（浅色主题 = 墨底浅字，深色主题 = 纸底墨字）。
    // 不能留 `if (isDark)` 布尔硬翻——换主题那一帧整块按钮突然反色；改成两套色按
    // darkAmount 插值。`dark` 是调用方指定的「强制暗底」变体（与主题无关），保持原样。
    // 静止态 darkAmount 恰为 0/1，像素与旧分支一致。
    val darkAmount = ui.palette.darkAmount
    val bg = if (dark) ui.palette.ink else lerp(ui.palette.ink, Color(0xFFF3EFE6), darkAmount)
    val fg = if (dark) ui.palette.bgTop else lerp(ui.palette.bgTop, Color(0xFF191813), darkAmount)
    Box(
        modifier
            .heightIn(min = 50.dp)
            .clip(SmoothCorner(999.dp))
            .background(if (enabled) bg else bg.copy(alpha = .35f))
            .then(
                if (enabled) Modifier.drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawLine(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = .24f), Color.Transparent),
                                startX = size.width * .1f,
                                endX = size.width * .9f,
                            ),
                            Offset(size.width * .1f, 1f),
                            Offset(size.width * .9f, 1f),
                            1f,
                        )
                    }
                } else Modifier
            )
            .bouncy(enabled) { onClick() }
            .padding(horizontal = 22.dp, vertical = 14.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (glyph != null) {
                LineIcon(glyph, fg, 18.dp, modifier = Modifier.padding(end = 8.dp))
            }
            Txt(label, size = 14.5f, color = fg, weight = FontWeight.Medium, letterSpacing = .3f)
        }
    }
}

/** The dock tone, shared by the editor toolbar and every flat button.
 *  Paper nudged towards the accent: low contrast, one solid colour, no shadow. */
internal fun dockTone(ui: InkUi): Color =
    // 3.26.53：混合量也按 darkAmount 连续走（.10 → .14），中点不再硬翻。
    lerp(ui.palette.paper, ui.accent, .10f + .04f * ui.palette.darkAmount)

@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    tint: Color? = null,
) {
    val ui = LocalInk.current
    MoyuSurface(modifier
            .heightIn(min = 50.dp)
            .clip(CircleShape)
            // Press lives on the SURFACE, not on the label row: the paper
            // fill must squish together with the text, otherwise only the
            // glyph moves and the button reads as "no water-drop".
            .bouncy { onClick() },
        radius = 999f,
        shape = CircleShape,
        flat = true,
        tone = dockTone(ui),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (glyph != null) {
                LineIcon(glyph, tint ?: ui.palette.ink, 18.dp, modifier = Modifier.padding(end = 8.dp))
            }
            Txt(label, size = 14f, color = tint ?: ui.palette.ink, weight = FontWeight.Medium)
        }
    }
}

@Composable
fun GlassIconButton(
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = LocalInk.current.palette.ink,
    strength: Float = .78f,
) {
    val ui = LocalInk.current
    MoyuSurface(modifier
            .size(size)
            .clip(CircleShape)
            .bouncy { onClick() },
        radius = size.value / 2f,
        shape = CircleShape,
        flat = true,
        tone = dockTone(ui),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LineIcon(glyph, tint, 21.dp)
        }
    }
}


@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    leading: String? = null,
    onClick: () -> Unit,
) {
    val ui = LocalInk.current
    val motion = ui.settings.motion != "none"
    // 3.26.9：选中底色从中心扩散铺满，取消时反向收回。
    // 3.26.8 是 if/else 两个容器直接换（选中=底色块、未选=MoyuSurface），
    // 切换那一帧没有过渡，读作「啪」地变黑。
    val p by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (motion) spring(MOYU_DAMPING, 420f) else tween(0),
        label = "chipFill",
    )
    val fg by animateColorAsState(
        if (selected) ui.palette.ink else ui.palette.muted,
        animationSpec = spring(MOYU_DAMPING, 520f),
        label = "chipFg",
    )
    val v = p.coerceIn(0f, 1f)
    val base = dockTone(ui)
    val fill = accentTint(ui.palette, ui.accent)
    val chipPath = remember { Path() }
    Row(
        Modifier
            .clip(CircleShape)
            .drawBehind {
                drawRect(base)
                if (v > .001f) {
                    chipPath.reset()
                    chipPath.addOutline(CircleShape.createOutline(size, layoutDirection, this))
                    clipPath(chipPath) {
                        drawCircle(fill, radius = size.minDimension * .75f * v, center = center)
                    }
                }
            }
            .bouncy { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Txt(leading, size = 10f, color = fg, modifier = Modifier.padding(end = 5.dp))
        Txt(label, size = 11.5f, color = fg, weight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val ui = LocalInk.current
    val height = 40.dp
    // The frame is painted LAST, over the moving pill (drawWithContent), not as
    // a background band underneath it. As a background the 1dp ring was simply
    // hidden wherever the black pill sat against the edge, which read as "the
    // frame fades when the selected block lands". The stroke is 2dp wide and
    // centred on the boundary, so the container's own clip leaves exactly the
    // inside 1dp visible.
    Box(
        modifier
            .height(height)
            .clip(SmoothCorner(14.dp))
            .background(ui.palette.faint)
            .drawWithContent {
                drawContent()
                val frame = Path().apply {
                    addOutline(SmoothCorner(14.dp).createOutline(size, layoutDirection, this@drawWithContent))
                }
                drawPath(frame, ui.palette.line, style = Stroke(2.dp.toPx()))
            }
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val itemW = maxWidth / options.size
            val clamped = selected.coerceIn(0, options.size - 1)
            // 水滴滑动：选中块被拖着走时沿方向拉长、到位回弹一次；原点在前端，
            // 所以是「前端先到、后端跟上来」。
            val pos = remember { androidx.compose.animation.core.Animatable(clamped.toFloat()) }
            var stretch by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            var last by remember { androidx.compose.runtime.mutableFloatStateOf(pos.value) }
            var rightward by remember { androidx.compose.runtime.mutableStateOf(true) }
            LaunchedEffect(clamped) {
                rightward = clamped >= pos.value
                last = pos.value
                pos.animateTo(
                    targetValue = clamped.toFloat(),
                    animationSpec = if (ui.settings.motion == "none") {
                        tween(0)
                    } else {
                        spring(dampingRatio = .8f, stiffness = 520f, visibilityThreshold = PROGRESS_THRESHOLD)
                    },
                ) {
                    val step = kotlin.math.abs(value - last)
                    last = value
                    // 有文字，幅度比镜片收一档，避免边缘发糊。
                    stretch += ((step * 22f).coerceIn(0f, .35f) - stretch) * .4f
                }
                stretch = 0f
            }
            Box(
                Modifier
                    // Same reason as the tab lens: sub-pixel Dp offsets are rounded
                    // by layout, so the segment indicator stepped on the last frame.
                    .graphicsLayer {
                        translationX = pos.value * itemW.toPx()
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            if (rightward) 1f else 0f,
                            .5f,
                        )
                        scaleX = 1f + stretch
                    }
                    .width(itemW)
                    .fillMaxHeight()
                    .clip(SmoothCorner(11.dp))
                    .background(accentTint(ui.palette, ui.accent))
            )
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { i, option ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .bouncy { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Txt(
                            option,
                            size = 11.5f,
                            color = if (i == selected) ui.palette.ink else ui.palette.muted,
                            weight = if (i == selected) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InkField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = 52.dp,
    textSize: Float = 15f,
    maxLines: Int = if (singleLine) 1 else 8,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
    onDone: (() -> Unit)? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    /** false = the caller already put this field on glass; only the focus ring
     *  is drawn here so the field does not paint a flat card over the glass. */
    surface: Boolean = true,
) {
    val ui = LocalInk.current
    val focus = remember { MutableInteractionSource() }
    val focused by focus.collectIsFocusedAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .heightIn(min = minHeight)
            .clip(SmoothCorner(16.dp))
            .then(
                if (surface) {
                    Modifier
                        .background(ui.palette.faint)
                        .border(
                            width = if (focused) 1.2.dp else .8.dp,
                            color = if (focused) ui.accent.copy(alpha = .55f) else ui.palette.line,
                            shape = SmoothCorner(16.dp),
                        )
                } else {
                    // On glass, a themed focus ring reads as a black box floating
                    // inside the capsule. The glass edge band already defines the
                    // field and the caret shows focus, so nothing is drawn here.
                    Modifier
                }
            )
            .padding(horizontal = 15.dp, vertical = 14.dp),
        enabled = enabled,
        interactionSource = focus,
        textStyle = TextStyle(
            color = ui.palette.ink,
            fontSize = textSize.sp,
            lineHeight = (textSize * 1.5f).sp,
            fontFamily = fontFamilyFor(ui.settings.font),
        ),
        cursorBrush = SolidColor(ui.accent),
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = {
            if (onDone != null) onDone() else {
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Txt(placeholder, size = textSize, color = ui.palette.muted.copy(alpha = .72f), maxLines = 1)
                }
                inner()
            }
        },
    )
}

@Composable
fun SettingGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ui = LocalInk.current
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Txt(
                title,
                size = 11f,
                color = ui.palette.muted,
                letterSpacing = 1.2f,
                modifier = Modifier.padding(start = 8.dp, bottom = 9.dp),
            )
        }
        MoyuSurface(Modifier.fillMaxWidth(), flat = true) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    glyph: Glyph? = null,
    onClick: (() -> Unit)? = null,
    /**
     * 3.26.30：宽控件（三档分段、多枚筹码）另起一行铺满。
     *
     * 默认的同一行排法里，右侧控件在一个未加权的 Row 里量宽度；Segmented 内部是
     * `BoxWithConstraints + fillMaxSize`，会吃满剩余宽度 → 左侧 `weight(1f)` 分到 0，
     * 中文标题被挤成一字一行（2026-11-19 用户截图：设置 → 问书 → 改稿方式）。
     * 这类控件用 stacked = true，标题在上、控件在下，两者都拿满宽。
     */
    stacked: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val ui = LocalInk.current
    if (stacked) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.bouncy(scale = .99f) { onClick() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (glyph != null) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(SmoothCorner(11.dp))
                            .background(ui.palette.faint),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(glyph, ui.palette.ink, 18.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Txt(title, size = 14.5f, color = ui.palette.ink, weight = FontWeight.Medium)
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Txt(subtitle, size = 10.5f, color = ui.palette.muted, lineHeight = 14f)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bouncy(scale = .99f) { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(SmoothCorner(11.dp))
                    .background(ui.palette.faint),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(glyph, ui.palette.ink, 18.dp)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(title, size = 14.5f, color = ui.palette.ink, weight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Txt(subtitle, size = 10.5f, color = ui.palette.muted, lineHeight = 14f)
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        if (onClick != null) {
            LineIcon(Glyph.Chevron, ui.palette.muted.copy(alpha = .7f), 16.dp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
fun RowDivider(inset: Dp = 16.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(.6.dp)
            .background(LocalInk.current.palette.line)
    )
}

@Composable
fun AnimatedTodoBox(
    done: Boolean,
    onToggle: (Boolean) -> Unit,
    boxSize: Dp = 20.dp,
) {
    SelectMark(checked = done, onToggle = onToggle, size = boxSize, corner = 6.dp)
}

/**
 * 统一选中标记（3.26.9）。
 *
 * 2026-11-16 用户定了这一套：系统确认框那种——未选是细描边圈，选中时底色
 * 从中心径向扩散铺满、勾在半程后弹出；取消时反向收缩。软件里所有
 * 「勾选 / 多选」都走这里：待办框、颜色点、导出的格式选择、链接笔记的选择圈。
 *
 * 不用在：TabBar 镜片（移动的块，用粘滞水滴）、Segmented（滑动块）、
 * 菜单里表示「当前项」的静态勾。
 */
@Composable
fun SelectMark(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    corner: Dp = 6.dp,
    accent: Color = LocalInk.current.accent,
    hapticType: Int = 1,
    /** false = 只画标记，点击由外层卡片 / 整行负责（导出格式卡、链接笔记行）。 */
    interactive: Boolean = true,
) {
    val ui = LocalInk.current
    val motion = ui.settings.motion != "none"
    val p by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (motion) spring(MOYU_DAMPING, 520f, PROGRESS_THRESHOLD) else tween(0),
        visibilityThreshold = PROGRESS_THRESHOLD,
        label = "selectMark",
    )
    val shape = SmoothCorner(corner)
    val v = p.coerceIn(0f, 1f)
    val borderColor = lerp(ui.palette.muted.copy(alpha = .55f), accent, v)
    // 3.26.53：勾色随昼夜连续从白过渡到纸底（原来 if (isDark) 在中点整条硬翻）。
    val checkColor = lerp(Color.White, ui.palette.bgTop, ui.palette.darkAmount)
    val base = Modifier.size(size)
    Canvas(
        (if (interactive) base.bouncy(scale = .92f, hapticType = hapticType) { onToggle(!checked) }
        else base).then(modifier)
    ) {
        val side = this.size.minDimension
        val outline = shape.createOutline(this.size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        drawPath(path, borderColor, style = Stroke(1.2.dp.toPx()))
        if (v > .001f) {
            clipPath(path) {
                // 半径取对角线一半以上，扩散到四角时已经铺满整个形状。
                drawCircle(accent, radius = side * .72f * v, center = center)
            }
            // 勾在后 70% 里描绘，同时从 .55 倍长到 1 倍，读作「弹出来」。
            val cp = ((v - .30f) / .70f).coerceIn(0f, 1f)
            if (cp > 0f) {
                val s = .55f + .45f * cp
                val check = Path().apply {
                    moveTo(center.x - side * .23f * s, center.y + side * .01f * s)
                    lineTo(center.x - side * .06f * s, center.y + side * .18f * s)
                    lineTo(center.x + side * .23f * s, center.y - side * .18f * s)
                }
                val measure = PathMeasure().apply { setPath(check, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * cp, partial, true)
                drawPath(
                    partial,
                    checkColor,
                    style = Stroke(
                        width = 2.1.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

@Composable
fun InkToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val ui = LocalInk.current
    // 3.26.53：关态轨道/滑块色按 darkAmount 连续插值，中点不再硬翻。
    val off = lerp(Color(0xFFE4DFD4), Color(0xFF3C403D), ui.palette.darkAmount)
    val knob = lerp(Color(0xFFFDFCF8), Color(0xFFF3F1EA), ui.palette.darkAmount)
    // 水滴滑动：圆点被拖着走时沿运动方向拉长、到位弹回正圆；
    // 体积感用镜片同款边缘带（内缘奶白渐变 + 1.1dp 亮线），不加任何阴影。
    val pos = remember { androidx.compose.animation.core.Animatable(if (checked) 1f else 0f) }
    var stretch by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var last by remember { androidx.compose.runtime.mutableFloatStateOf(pos.value) }
    LaunchedEffect(checked) {
        last = pos.value
        pos.animateTo(
            targetValue = if (checked) 1f else 0f,
            animationSpec = if (ui.settings.motion == "none") {
                tween(0)
            } else {
                spring(dampingRatio = .62f, stiffness = 520f, visibilityThreshold = PROGRESS_THRESHOLD)
            },
        ) {
            val step = kotlin.math.abs(value - last)
            last = value
            stretch += ((step * 26f).coerceIn(0f, .45f) - stretch) * .5f
        }
        stretch = 0f
    }
    val progress = pos.value
    Box(
        Modifier
            .width(48.dp)
            .height(29.dp)
            .clip(CircleShape)
            .background(lerp(off, ui.accent, progress))
            .drawWithContent {
                drawContent()
                glassEdgeBand(CircleShape, ui.palette, .55f)
            }
            .bouncy { onChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    translationX = 19.dp.toPx() * progress
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        if (checked) 1f else 0f,
                        .5f,
                    )
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * .10f
                }
                .size(23.dp)
                .clip(CircleShape)
                .background(knob)
                .drawWithContent {
                    drawContent()
                    glassEdgeBand(CircleShape, ui.palette, .7f)
                }
        )
    }
}

fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/**
 * The one fill for a selected block: paper with the accent mixed in.
 *
 * A solid accent block read as a black slab; a paper-accent tint is what the
 * reader reads as "this one is chosen" without the accent shouting. Every
 * selectable surface (segmented pill, chip, theme card, export format) must use
 * this single function — two different "selected" colours on one screen is what
 * made the app look assembled from parts.
 */
fun accentTint(p: InkPalette, accent: Color): Color =
    // 3.26.53：混合比例按 darkAmount 连续走（.13 → .20），中点不再跳一次。
    lerp(p.paper, accent, .13f + .07f * p.darkAmount)

/** The edge of a selected block: the same tint, denser, so a 1 dp line reads. */
fun accentLine(p: InkPalette, accent: Color): Color =
    lerp(p.paper, accent, .30f + .12f * p.darkAmount)

/**
 * One gesture owner for both tap-to-seek and horizontal drag.
 *
 * Two separate pointerInput detectors (tap + horizontal drag) fight over the
 * first down, and a diagonal drag can lose to the parent LazyColumn before
 * the slider ever sees a horizontal slop. This keeps the down unconsumed so
 * vertical page scroll still works, then claims the gesture once horizontal
 * movement wins.
 */
@Composable
private fun Modifier.sliderGesture(
    widthPx: Int,
    onGrabChanged: (Boolean) -> Unit = {},
    onUpdate: (Float) -> Unit,
): Modifier {
    // The callback changes when the role tab changes. Keeping the first
    // callback would make a drag update the previously selected role.
    val currentUpdate by rememberUpdatedState(onUpdate)
    val currentGrab by rememberUpdatedState(onGrabChanged)
    return pointerInput(widthPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val limit = widthPx.toFloat()
            // A slider only arms after a short hold. A finger that lands on a
            // row while scrolling moves within ~30 ms and is ignored, which is
            // what used to change a value by accident. Once armed, horizontal
            // movement follows the finger; a quick tap does nothing at all.
            val first = withTimeoutOrNull(120) {
                var ev: androidx.compose.ui.input.pointer.PointerEvent
                do {
                    ev = awaitPointerEvent()
                } while (ev.changes.none { it.id == down.id })
                ev
            }
            if (first == null) {
                currentGrab(true)
                try {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val change = ev.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        currentUpdate(change.position.x.coerceIn(0f, limit))
                    }
                } finally {
                    currentGrab(false)
                }
            } else {
                // Moved before the hold completed: a tap or a list scroll.
                // Swallow nothing so the LazyColumn keeps scrolling.
                if (first.changes.any { it.id == down.id && it.pressed }) {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val change = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                    }
                }
            }
        }
    }
}

@Composable
fun InkSlider(
    value: Float,
    onChange: (Float) -> Unit,
    min: Float = 0f,
    max: Float = 1f,
    steps: Int = 0,
    modifier: Modifier = Modifier,
    accent: Color = LocalInk.current.accent,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val ui = LocalInk.current
    fun snap(raw: Float): Float {
        val coerced = raw.coerceIn(min, max)
        if (steps <= 0) return coerced
        val step = (max - min) / steps
        return (min + ((coerced - min) / step).roundToInt() * step).coerceIn(min, max)
    }
    fun update(x: Float) {
        onChange(snap(min + (max - min) * (x / widthPx)))
    }
    val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f)
    var grabbed by remember { mutableStateOf(false) }
    val haptic = LocalHaptic.current
    val knob by animateFloatAsState(
        targetValue = if (grabbed) 1.35f else 1f,
        animationSpec = if (ui.settings.motion == "none") tween(0) else spring(MOYU_DAMPING, 520f, PROGRESS_THRESHOLD),
        visibilityThreshold = PROGRESS_THRESHOLD,
        label = "knobGrab",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .semantics { progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(value, min..max, 0) }
            .sliderGesture(widthPx, onGrabChanged = { on ->
                grabbed = on
                if (on) haptic(1)
            }) { x -> update(x) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(lerp(Color(0xFFDDD7CC), Color(0xFF313532), ui.palette.darkAmount))
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(5.dp)
                .clip(CircleShape)
                .background(accent)
        )
        val knobOffset = with(LocalDensity.current) {
            ((widthPx - 22.dp.toPx()).coerceAtLeast(0f) * fraction).toDp()
        }
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(22.dp)
                .graphicsLayer {
                    shadowElevation = 8f
                    shape = CircleShape
                    clip = true
                    scaleX = knob
                    scaleY = knob
                }
                .clip(CircleShape)
                .background(lerp(Color.White, Color(0xFFF4F1E9), ui.palette.darkAmount))
                .border(.8.dp, Color.Black.copy(alpha = .06f), CircleShape)
        )
    }
}

@Composable
fun HueSlider(hue: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    var widthPx by remember { mutableIntStateOf(1) }
    val ui = LocalInk.current
    fun update(x: Float) {
        onChange((x / widthPx).coerceIn(0f, 1f) * 360f)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .sliderGesture(widthPx) { x -> update(x) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFD24A43), Color(0xFFD28B3A), Color(0xFFC6B93B),
                            Color(0xFF4F9A65), Color(0xFF438A9B), Color(0xFF5168A9),
                            Color(0xFF8A5C93), Color(0xFFD24A43),
                        )
                    )
                )
                .border(.6.dp, ui.palette.line, CircleShape)
        )
        val fraction = (hue / 360f).coerceIn(0f, 1f)
        val knobOffset = with(LocalDensity.current) {
            ((widthPx - 22.dp.toPx()).coerceAtLeast(0f) * fraction).toDp()
        }
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(22.dp)
                .graphicsLayer { shadowElevation = 7f; shape = CircleShape; clip = true }
                .clip(CircleShape)
                .background(lerp(Color.White, Color(0xFFF4F1E9), ui.palette.darkAmount))
                .border(1.dp, Color.Black.copy(alpha = .12f), CircleShape)
        )
    }
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean = true, plusEnabled: Boolean = true) {
    val ui = LocalInk.current
    Row(
        Modifier
            .clip(SmoothCorner(999.dp))
            .background(ui.palette.faint)
            .border(.8.dp, ui.palette.line, SmoothCorner(999.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).bouncy(enabled = minusEnabled, scale = .92f) { onMinus() }, contentAlignment = Alignment.Center) {
            Txt("−", size = 18f, color = if (minusEnabled) ui.palette.ink else ui.palette.muted.copy(alpha = .4f))
        }
        Txt(value, size = 12f, color = ui.palette.ink, weight = FontWeight.Medium, modifier = Modifier.widthIn(min = 38.dp), align = TextAlign.Center)
        Box(Modifier.size(40.dp).bouncy(enabled = plusEnabled, scale = .92f) { onPlus() }, contentAlignment = Alignment.Center) {
            Txt("+", size = 17f, color = if (plusEnabled) ui.palette.ink else ui.palette.muted.copy(alpha = .4f))
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val ui = LocalInk.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Txt(title, size = 19f, color = ui.palette.ink, weight = FontWeight.SemiBold, letterSpacing = .2f)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Txt(subtitle, size = 10.5f, color = ui.palette.muted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun <T> AnimatedPane(
    targetState: T,
    label: String = "pane",
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(spring(MOYU_DAMPING, 500f)) +
                    slideInHorizontally(spring(MOYU_DAMPING, 420f)) { full ->
                        if (targetState != null) full / 4 else -full / 4
                    }
                ) togetherWith (
                fadeOut(spring(MOYU_DAMPING, 520f)) +
                    slideOutHorizontally(spring(MOYU_DAMPING, 460f)) { full ->
                        if (targetState != null) -full / 5 else full / 5
                    }
                )
        },
        label = label,
    ) { state ->
        Column(Modifier.fillMaxWidth()) {
            content(state)
        }
    }
}

@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnOutsideClick: Boolean = true,
    performanceMode: Boolean = false,
    /** 3.26.48：内容需要框架的 `close()` 时用这个（取消/保存/删除按钮先播退场、idle 后才回调 onDismiss）。
     *  与 [ModalSheet] 同一套写法：不写成同名 `content` 重载——两个重载都吃尾随 lambda 时
     *  `GlassDialog(onDismiss) { }` 会解析歧义，把现有调用点全部弄坏。 */
    closeableContent: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val ui = LocalInk.current
    // 3.26.47：补退场。宿主一律是 `if (flag) GlassDialog(...)`，onDismiss 一调宿主当帧就把整窗卸掉，
    // 所以关闭必须先播完退场、等 AnimatedVisibility idle 再回调（与 ModalSheet/InlineModalSheet 同一写法）。
    var closing by remember { mutableStateOf(false) }
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visibleState.targetState = true }
    fun close() {
        if (closing) return
        closing = true
        visibleState.targetState = false
    }
    LaunchedEffect(closing, visibleState.isIdle, visibleState.currentState) {
        if (closing && visibleState.isIdle && !visibleState.currentState) onDismiss()
    }
    // 3.26.26 的规矩沿用到这里：压暗随 closing 一起撤，退场结束时遮罩正好到 0，
    // 不会出现「面板已经没了、页面还灰着」的一段（窗口卸掉那一帧遮罩已经是 0）。
    val scrimAlpha by animateFloatAsState(
        targetValue = if (closing) 0f else .18f + .16f * ui.palette.darkAmount,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "glassDialogScrim",
    )
    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissOnOutsideClick,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        val dialogContext = LocalContext.current
        LaunchedEffect(Unit) {
            (dialogView.parent as? DialogWindowProvider)?.window
                ?.enableHighRefresh(dialogContext)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (dismissOnOutsideClick && !closing) close()
                },
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                val panelMax = maxHeight * .94f
                // 3.26.47：`visible = true` 那版其实没有进场（初始即可见、enter 从未播放）；
                // 换成 visibleState 后 enter 才真正跑起来，与 ModalSheet 的进出观感一致。
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = fadeIn(spring(MOYU_DAMPING, 500f)) +
                        scaleIn(initialScale = .965f, animationSpec = spring(MOYU_DAMPING, 420f, PRESS_THRESHOLD)),
                    exit = fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                        scaleOut(targetScale = .965f, animationSpec = tween(150, easing = FastOutSlowInEasing)),
                ) {
                    CompositionLocalProvider(LocalGlassLite provides performanceMode) {
                        MoyuSurface(modifier
                                .padding(horizontal = 18.dp)
                                .fillMaxWidth()
                                .widthIn(max = 560.dp)
                                .heightIn(max = panelMax),
                            flat = true,
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                                    .verticalScroll(rememberScrollState())
                                    .padding(22.dp),
                            ) {
                                // 3.26.48：带 close 的调用点走窗口自己的 close()（退场播完、idle 后才回调
                                // onDismiss）；旧调用点仍然只走 content()，行为零变化。
                                val withClose = closeableContent
                                val plain = content
                                if (withClose != null) {
                                    withClose(this, { close() })
                                } else {
                                    plain?.invoke(this)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogBlur(active: Boolean = true) {
    val view = LocalView.current
    val ui = LocalInk.current
    LaunchedEffect(active) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.setDimAmount(0.08f)
        } else {
            // 3.26.26: drop the dialog window dim the moment closing flips, before the exit spring ends.
            window.setDimAmount(0f)
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        androidx.core.view.WindowCompat.getInsetsController(window, view)
            .isAppearanceLightNavigationBars = !ui.palette.isDark
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                window.setBackgroundBlurRadius(if (active) 58 else 0)
            } catch (_: Throwable) {
            }
        }
    }
}

/**
 * 统一展开包装（3.26.47 新提供，本轮不批量套用）。
 *
 * 全项目此前零 `animateContentSize`：折叠/展开类区块（设置说明、列表详情、行内提示）
 * 要么突然出现、要么直接 `if/else` 换内容，没有「长出来」的过程。以后统一走这里：
 * 尺寸动画用 `expandVertically/shrinkVertically`（横向出现的内容传 `horizontal = true`），
 * 同时叠一层 `fadeIn/fadeOut`——只做尺寸展开时先出现的是「一条被压扁的内容」，
 * 带上淡入后观感是整块一起长出来。
 *
 * 曲线用空间类 spring `dampingRatio = .6f, stiffness = 380f`（项目内公认的柔曲线数值），
 * `motion = none` 档退化成 `tween(0)`，与其它动效的降级方式一致。
 *
 * @param expand 目标展开状态；首次组合时即按该状态显示，不额外补一段进场。
 * @param horizontal true = 横向展开（窄控件/行内元素），默认纵向（列表行、段落）。
 */
@Composable
fun MoyuExpand(
    modifier: Modifier = Modifier,
    expand: Boolean = true,
    horizontal: Boolean = false,
    content: @Composable () -> Unit,
) {
    val motion = LocalInk.current.settings.motion != "none"
    val spatial: FiniteAnimationSpec<IntSize> =
        if (motion) spring(dampingRatio = .6f, stiffness = 380f) else tween(0)
    val fade: FiniteAnimationSpec<Float> =
        if (motion) spring(dampingRatio = .6f, stiffness = 380f) else tween(0)
    AnimatedVisibility(
        visible = expand,
        modifier = modifier,
        enter = (if (horizontal) expandHorizontally(spatial) else expandVertically(spatial)) + fadeIn(fade),
        exit = (if (horizontal) shrinkHorizontally(spatial) else shrinkVertically(spatial)) + fadeOut(fade),
    ) {
        content()
    }
}

/** 面板内的子页头（3.26.9）：返回箭头 + 标题（+ 副标题）。
 *  用于「菜单 → 选项页」同框层级里的第 2 层，和第一层的 DialogTitle 区分开：
 *  第一层是「关掉」，第二层是「退回上一层」。 */
@Composable
fun SheetPageHeader(title: String, subtitle: String? = null, onBack: () -> Unit) {
    val ui = LocalInk.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        GlassIconButton(Glyph.Back, onBack, tint = ui.palette.muted)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Txt(
                title,
                size = 23f,
                color = ui.palette.ink,
                weight = FontWeight.SemiBold,
                letterSpacing = .3f,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(5.dp))
                Txt(subtitle, size = 11.5f, color = ui.palette.muted, lineHeight = 17f)
            }
        }
    }
}

@Composable
fun DialogTitle(title: String, subtitle: String? = null, onClose: (() -> Unit)? = null) {
    val ui = LocalInk.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Txt(title, size = 23f, color = ui.palette.ink, weight = FontWeight.SemiBold, letterSpacing = .3f)
            if (subtitle != null) {
                Spacer(Modifier.height(5.dp))
                Txt(subtitle, size = 11.5f, color = ui.palette.muted, lineHeight = 17f)
            }
        }
        if (onClose != null) {
            GlassIconButton(Glyph.Close, onClose, tint = ui.palette.muted)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val ui = LocalInk.current
    // 3.26.48：取消/确认都先走 GlassDialog 的 close()——先播退场、窗口 idle 后才回调 onDismiss。
    // 以前按钮直接调外层 onDismiss/onConfirm，宿主 if(flag) 当帧就把对话框卸掉，3.26.47 补的退场被跳过。
    // 确认路径不能丢动作：记一个 pending 标记，退场结束后在 GlassDialog 的 onDismiss 里执行。
    var confirmRequested by remember { mutableStateOf(false) }
    GlassDialog(
        onDismiss = {
            if (confirmRequested) {
                confirmRequested = false
                onConfirm()
            } else {
                onDismiss()
            }
        },
        closeableContent = { close ->
            DialogTitle(title)
            Spacer(Modifier.height(12.dp))
            Txt(message, size = 12.5f, color = ui.palette.muted, lineHeight = 20f)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                GlassButton("取消", close, Modifier.weight(1f))
                Box(Modifier.weight(1f)) {
                    if (destructive) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(SmoothCorner(999.dp))
                                .background(Color(0xFFB3453D))
                                .bouncy { confirmRequested = true; close() },
                            contentAlignment = Alignment.Center,
                        ) { Txt(confirmLabel, size = 14f, color = Color.White, weight = FontWeight.Medium) }
                    } else {
                        PrimaryButton(confirmLabel, { confirmRequested = true; close() }, Modifier.fillMaxWidth())
                    }
                }
            }
        },
    )
}

/**
 * 同窗口面板内容槽的默认横向留白（20dp）。
 *
 * `InlineModalSheet(bleedContent = true)` 会把这段留白从内容槽去掉，改由内容
 * 自己用本常量补回：这样整幅横移的面板能滑到面板本体边缘，静止态内边距不变。
 */
val SHEET_CONTENT_INSET: Dp = 20.dp

/**
 * A sheet that lives in the current window instead of a Dialog.
 *
 * Opening a Dialog moves window focus, which makes the IME disappear in one
 * frame while the note options are entering. This overlay keeps the focused
 * editor and the IME alive; the caller makes the editor read-only while it is
 * visible. The panel is lifted above the IME with imePadding.
 */
@Composable
fun InlineModalSheet(
    onDismiss: () -> Unit,
    /** 面板底色。3.26.21 起不再区分：所有同窗口面板统一铺与 `SheetPane` 同色的纸底，
     *  免得同一个 App 里「有的菜单偏灰、有的偏纸」，用户读作「各种分色」（用户原话）。
     *  传 null 时按 `palette.paper` 处理；保留参数只为兼容既有调用点。 */
    tone: Color? = null,
    /** true = 面板跟随 IME 抬高；纯展示、没有输入框的面板传 false。
     *  3.26.18：故事面板传 false——它的输入都在独立 GlassDialog 里，面板跟着 IME
     *  抬满反而会在「面板底边已到、键盘还没升到」的缝里露出下层页面（用户录屏 6570cc）。 */
    imeAware: Boolean = true,
    /** false = 点面板外的空白不关闭；返回键与右上角 X 仍然有效。
     *  3.26.18：故事面板与 4 个故事编辑框传 false（用户录屏：点空白处就返回）。 */
    dismissOnOutsideClick: Boolean = true,
    /** true = 面板铺满可用高度，内容自己滚（工作台类面板用；3.26.19 故事）。
     *  默认 false，其它面板保持「包内容高度 + 整块滚」的老行为。 */
    fillHeight: Boolean = false,
    /** true = 内容槽不加横向留白（默认见 `SHEET_CONTENT_INSET`），由内容自己补回。
     *  为什么存在这个开关：内容槽的横向 padding 相当于一层「内窗口」，整幅横移的
     *  面板（故事、菜单等逐页横滑）滑到页边时会被它切出两道纸色竖边，用户读作
     *  「边框把内容限制在窗口里」。默认 false = 老调用零变化。 */
    bleedContent: Boolean = false,
    /** 固定在内容上方、不随内容滚的头；只在 fillHeight 时生效。 */
    pinnedContent: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val ui = LocalInk.current
    var closing by remember { mutableStateOf(false) }
    var dismissReady by remember { mutableStateOf(false) }
    // 面板存活期间收起根级 TabBar：它是 root 的兄弟节点、画在页面之上，
    // 不收的话面板底部会被玻璃条盖住、点不到（3.26.9 笔记夹菜单实测）。
    val suppressBar = LocalSheetSuppressed.current
    DisposableEffect(Unit) {
        suppressBar(true)
        onDispose { suppressBar(false) }
    }
    // The sheet leaves the composition only when the exit spring is idle.
    // A fixed 250ms dismiss used to unmount it while the last pixels were
    // still sliding, which showed up as a tiny teleport on close.
    val visibleState = remember { MutableTransitionState(false) }

    LaunchedEffect(Unit) {
        visibleState.targetState = true
        delay(260)
        dismissReady = true
    }

    fun close() {
        if (closing) return
        closing = true
        visibleState.targetState = false
    }

    LaunchedEffect(closing, visibleState.isIdle, visibleState.currentState) {
        if (closing && visibleState.isIdle && !visibleState.currentState) onDismiss()
    }

    val sheetCorner = GlassStyle.corner
    val panelShape = SmoothCorner(
        topStart = sheetCorner.dp,
        topEnd = sheetCorner.dp,
    )
    // 3.26.11：压暗恢复淡入淡出 200ms（3.26.10 为了让背景「关掉不留灰」
    // 把它砍成瞬间出现/瞬间消失，用户明确说这是「头疼砍头」）。
    // 用 Animatable 由 closing 驱动：进入从 0 淡到 0.22，关闭 200ms 淡回 0，
    // 之后面板自己继续滑出——遮罩先收干净，页面上不会留灰底。
    val scrim = remember { Animatable(0f) }
    LaunchedEffect(closing) {
        scrim.animateTo(
            if (closing) 0f else .22f + .16f * ui.palette.darkAmount,
            tween(200),
        )
    }
    val scrimAlpha = scrim.value
    // 3.26.9：进出那一程的粘滞形变（与 ModalSheet 共用同一组数）。
    // 面板往上走时纵向被拉长一点，停稳回到 1；数值是 t 的函数，落位一定精确。
    val sheetShape by animateFloatAsState(
        targetValue = if (visibleState.targetState) 1f else 0f,
        animationSpec = spring(MOYU_DAMPING, 320f),
        label = "inlineSheetShape",
    )

    BackHandler { close() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = dismissOnOutsideClick && dismissReady && !closing,
            ) { close() }
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(if (imeAware) Modifier.imePadding() else Modifier),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val panelMax = maxHeight
            AnimatedVisibility(
                visibleState = visibleState,
                // Slide only. Fading the whole sheet makes its opaque paper
                // translucent mid-flight, so body text shows through the menu.
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(MOYU_DAMPING, 420f),
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(MOYU_DAMPING, 460f),
                ),
            ) {
                // Consume taps that land on the sheet itself so the scrim behind
                // does not close it, while leaving every child row's own tap
                // handling intact. This lives on the sheet container, not on the
                // scrolling content column: putting an empty `clickable` there
                // won the hit test ahead of the rows and made bound notes
                // impossible to tap.
                Box(Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Final).changes.forEach { it.consume() }
                        }
                    }
                }) {
                MoyuSurface(Modifier
                        .fillMaxWidth()
                        .heightIn(max = panelMax)
                        .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                        .graphicsLayer {
                            val v = sheetShape.coerceIn(0f, 1f)
                            scaleY = .94f + .06f * ViscousDrop.lagged(v)
                            scaleX = .988f + .012f * v
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
                        },
                    radius = sheetCorner,
                    shape = panelShape,
                    flat = true,
                    // 3.26.21：统一纸底——所有面板同色，卡片仍靠 paper+4% 分层。
                    tone = tone ?: ui.palette.paper,
                ) {
                    if (fillHeight) {
                        // 工作台：头固定、内容区填满剩余高度并自己滚（每个 pane 自己 verticalScroll）。
                        Column(
                            Modifier
                                .fillMaxSize()
                                .navigationBarsPadding(),
                        ) {
                            pinnedContent?.let { pinned ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .padding(top = 10.dp),
                                ) { pinned { close() } }
                            }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .then(
                                        if (bleedContent) Modifier
                                        else Modifier.padding(horizontal = SHEET_CONTENT_INSET),
                                    )
                                    .padding(top = 10.dp, bottom = 12.dp),
                            ) {
                                content { close() }
                            }
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                // No tap consumer on the content column at all. The
                                // scrim behind already ignores taps that land on the
                                // sheet: it is laid out first and the sheet paints
                                // over it, so Compose routes the tap to the sheet
                                // subtree. An empty `clickable` here used to win the
                                // hit test before every row inside it, which is why
                                // bound notes could never be tapped to unbind.
                                .navigationBarsPadding()
                                .verticalScroll(rememberScrollState())
                                .then(
                                    if (bleedContent) Modifier
                                    else Modifier.padding(horizontal = SHEET_CONTENT_INSET),
                                )
                                .padding(top = 10.dp, bottom = 24.dp),
                        ) {
                            content { close() }
                        }
                    }
                }
                }
            }
        }
    }
}

/**
 * 同框面板的每一层：自带**不透明纸底**。
 *
 * 3.26.12：3.26.9–3.26.11 的同框切换只做了横向滑动，没有给每一层铺底，
 * 于是切到第二层时两层的文字同时可见、互相叠在一起（用户录屏 e8a40d6b：
 * 「写作选项」和「导出书籍」两个标题上下摞着）。面板底色在 MoyuSurface 上，
 * 层内透明，滑动时不会遮挡——每一层必须自己铺纸。
 */
@Composable
fun SheetPane(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val ui = LocalInk.current
    // 面板内每一层都铺自己的纸底，否则横向切换时两层文字会互相透（3.26.12）。
    // 注意：这里的纸色比承载它的 MoyuSurface(flat) 暗 4%，所以**只适合内容自带卡片**
    // 的面板；整块都是留白的「故事」面板要由调用方把 surface 调成同一个纸色
    // （InlineModalSheet 的 tone 参数），否则会出现一块比面板暗的矩形（用户读作「灰框」）。
    Column(
        Modifier
            .fillMaxWidth()
            .then(modifier)
            .background(ui.palette.paper),
        content = content,
    )
}

/**
 * 元素重组图标：同一批线段在两个形态之间线性插值。
 *
 * 用户 2026-11-17 录屏里那种「按一下，原来有的元素重新组合变成另一种按钮」：
 * 不是换图标，而是线段从形态 A 的位置、角度直接走到形态 B 的位置。
 * 两组线段数量不等时，多出来的那条从中心淡入/淡出（alpha 由 progress 驱动）。
 * 归一化坐标（0..1）铺满传入的 modifier 尺寸。
 *
 * 判定「符合标准」：按钮有**两种形态**（不是选中/未选中）。单态按钮、
 * TabBar 镜片、InkToggle 不套这个（它们已有 `SelectMark` 的选中动画）。
 */
data class MorphStroke(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

@Composable
fun MorphIcon(
    progress: Float,
    a: List<MorphStroke>,
    b: List<MorphStroke>,
    modifier: Modifier = Modifier,
    color: Color,
    strokeWidthDp: Float = 1.7f,
) {
    Canvas(modifier) {
        val t = progress.coerceIn(0f, 1f)
        val sw = strokeWidthDp.dp.toPx()
        val w = size.width
        val h = size.height
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val sa = a.getOrNull(i)
            val sb = b.getOrNull(i)
            // 缺哪一侧，就用另一侧的位置当起点/终点，配合 alpha 让它原地淡入/淡出。
            val ax1 = sa?.x1 ?: sb!!.x1
            val ay1 = sa?.y1 ?: sb!!.y1
            val ax2 = sa?.x2 ?: sb!!.x2
            val ay2 = sa?.y2 ?: sb!!.y2
            val bx1 = sb?.x1 ?: ax1
            val by1 = sb?.y1 ?: ay1
            val bx2 = sb?.x2 ?: ax2
            val by2 = sb?.y2 ?: ay2
            val alpha = when {
                sa == null -> t
                sb == null -> 1f - t
                else -> 1f
            }
            if (alpha <= .01f) continue
            val sx = (ax1 + (bx1 - ax1) * t) * w
            val sy = (ay1 + (by1 - ay1) * t) * h
            val ex = (ax2 + (bx2 - ax2) * t) * w
            val ey = (ay2 + (by2 - ay2) * t) * h
            drawLine(
                color = color.copy(alpha = color.alpha * alpha),
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 「∨」收成两条斜线。 */
val MorphChevronDown = listOf(
    MorphStroke(.24f, .40f, .50f, .62f),
    MorphStroke(.50f, .62f, .76f, .40f),
)

/** 键盘：外框四边 + 三个键点。前两条与 [MorphChevronDown] 配对做变形，其余淡入。 */
val MorphKeyboard = listOf(
    MorphStroke(.20f, .34f, .20f, .72f),
    MorphStroke(.80f, .72f, .80f, .34f),
    MorphStroke(.20f, .72f, .80f, .72f),
    MorphStroke(.80f, .34f, .20f, .34f),
    MorphStroke(.30f, .47f, .40f, .47f),
    MorphStroke(.46f, .47f, .56f, .47f),
    MorphStroke(.38f, .60f, .62f, .60f),
)

@Composable
fun ModalSheet(
    onDismiss: () -> Unit,
    imeAware: Boolean = false,
    /** true = 内容槽不加横向留白（默认见 `SHEET_CONTENT_INSET`），由内容自己补回。
     *  语义同 [InlineModalSheet]：整幅横移的内容（如悬浮故事页的逐页横滑）滑到页边时，
     *  不会被内容槽的 20dp 内窗口切出两道纸色竖边。默认 false = 老调用点零变化。 */
    bleedContent: Boolean = false,
    /** 3.26.47：内容需要框架的 `close()`（先播退场、idle 后才回调 onDismiss）时用这个。
     *  面板右上角 X 以前直接调外层 `onDismiss`，宿主 `if(flag)` 当帧就把面板卸掉、退场被跳过；
     *  改成内容拿 close 后由面板自己关。
     *  说明：这里没写成同名 `content` 的重载——两个重载都接受尾随 lambda 时 `ModalSheet(onDismiss) { }`
     *  会因「零参 lambda 同时适配 (close)->Unit」解析歧义，会把现有 8 处调用点全部弄坏。 */
    closeableContent: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {    val ui = LocalInk.current
    val sheetCorner = GlassStyle.corner
    val panelShape = SmoothCorner(
        topStart = sheetCorner.dp,
        topEnd = sheetCorner.dp,
    )
    // 3.26.9：与 InlineModalSheet 用同一套进出（滑入/滑出 + 粘滞形变 + 遮罩淡入淡出），
    // 宿主仍是 Dialog。3.26.8 这里写的是 `AnimatedVisibility(visible = true)`——
    // 只有进入、没有退出，一 dismiss 窗口当场消失，和同窗口面板的观感对不上。
    var closing by remember { mutableStateOf(false) }
    var dismissReady by remember { mutableStateOf(false) }
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        visibleState.targetState = true
        delay(260)
        dismissReady = true
    }
    fun close() {
        if (closing) return
        closing = true
        visibleState.targetState = false
    }
    LaunchedEffect(closing, visibleState.isIdle, visibleState.currentState) {
        if (closing && visibleState.isIdle && !visibleState.currentState) onDismiss()
    }
    // 与 InlineModalSheet 同一套：200ms 淡入淡出，关闭后不留灰底。
    val scrim = remember { Animatable(0f) }
    LaunchedEffect(closing) {
        scrim.animateTo(
            if (closing) 0f else .22f + .16f * ui.palette.darkAmount,
            tween(200),
        )
    }
    val scrimAlpha = scrim.value
    val sheetShape by animateFloatAsState(
        targetValue = if (visibleState.targetState) 1f else 0f,
        animationSpec = spring(MOYU_DAMPING, 320f),
        label = "modalSheetShape",
    )
    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogBlur(active = !closing)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = dismissReady && !closing,
                    ) { close() }
            )
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .then(if (imeAware) Modifier.imePadding() else Modifier),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val panelMax = maxHeight
                AnimatedVisibility(
                    visibleState = visibleState,
                    // Slide only: a fading opaque sheet shows the page through
                    // itself while it enters.
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(MOYU_DAMPING, 420f),
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(MOYU_DAMPING, 460f),
                    ),
                ) {
                    MoyuSurface(Modifier
                            .fillMaxWidth()
                            .heightIn(max = panelMax)
                            .graphicsLayer {
                                val v = sheetShape.coerceIn(0f, 1f)
                                scaleY = .94f + .06f * ViscousDrop.lagged(v)
                                scaleX = .988f + .012f * v
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
                            },
                        radius = sheetCorner,
                        shape = panelShape,
                        flat = true,
                    ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .then(
                                if (bleedContent) Modifier
                                else Modifier.padding(horizontal = SHEET_CONTENT_INSET),
                            )
                            .padding(top = 10.dp, bottom = 24.dp),
                    ) {
                        Box(
                            Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(ui.palette.muted.copy(alpha = .28f))
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(18.dp))
                        // 3.26.47：带 close 的调用点走面板自己的 close()（退场完毕才回调 onDismiss）；
                        // 其余调用点仍走原来的 content()，行为零变化。
                        val withClose = closeableContent
                        val plain = content
                        if (withClose != null) {
                            withClose(this, { close() })
                        } else {
                            plain?.invoke(this)
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
fun AmbientBackground(modifier: Modifier = Modifier, overlayOnly: Boolean = false) {
    val p = LocalInk.current.palette
    // One flat page colour. The old layer was a top-to-bottom gradient plus a
    // white radial bloom at 12% width and 90 dots of grain; all three made the
    // paper read as uneven even on a still screen. A page is a sheet of paper,
    // not a lit surface.
    // The colour itself is already cross-faded one layer up (MoyuTheme animates
    // p.bgTop with the same 220 ms spec as the rest of the palette). Do not put an
    // animateColorAsState back here: two animations chasing the same target made
    // the page keep drifting after the switch.
    Canvas(modifier.fillMaxSize()) {
        if (!overlayOnly) {
            drawRect(p.bgTop)
        }
    }
}

@Composable
fun ToastLayer(modifier: Modifier = Modifier) {
    val ui = LocalInk.current
    val p = ui.palette
    val haptic = LocalHaptic.current
    val motion = ui.settings.motion != "none"
    // Keep the last text alive while the capsule retracts; the bus clears the
    // message first, and rendering straight from it would blank the row.
    var display by remember { mutableStateOf<String?>(null) }
    var glyph by remember { mutableStateOf<Glyph?>(null) }
    val t = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(ToastBus.id) {
        val incoming = ToastBus.message ?: return@LaunchedEffect
        display = incoming
        glyph = ToastBus.glyph
        haptic(1)
        ring.snapTo(0f)
        if (!motion) {
            t.snapTo(1f)
            ring.snapTo(1f)
            return@LaunchedEffect
        }
        t.animateTo(1f, spring(dampingRatio = .62f, stiffness = 360f))
        ring.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(ToastBus.message) {
        if (ToastBus.message == null && display != null) {
            if (motion) {
                t.animateTo(0f, spring(dampingRatio = .9f, stiffness = 320f))
            } else {
                t.snapTo(0f)
            }
            display = null
        }
    }
    val text = display ?: return
    val icon = glyph ?: noticeGlyph(text)
    Box(
        modifier.graphicsLayer {
            val v = t.value
            translationY = (v - 1f) * 24.dp.toPx()
            scaleX = .94f + .06f * v
            scaleY = .82f + .18f * v
            alpha = v.coerceIn(0f, 1f)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f)
        },
        contentAlignment = Alignment.Center,
    ) {
        GlassField(
            Modifier.widthIn(max = 340.dp),
            shape = SmoothCorner(20.dp),
            shadowAlpha = .18f,
            kind = "notice",
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                val r = ring.value
                                scaleX = .7f + .9f * r
                                scaleY = .7f + .9f * r
                                alpha = (1f - r).coerceIn(0f, 1f) * .45f
                            }
                            .border(1.2.dp, ui.accent, CircleShape)
                    )
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(accentTint(p, ui.accent)),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(icon, ui.accent, 13.dp, 1.8f)
                    }
                }
                Spacer(Modifier.width(9.dp))
                Txt(text, size = 12.5f, color = p.ink, maxLines = 2)
            }
        }
    }
}

/** 小通知没有逐个传图标；按文案挑一个，失败用叉、成功用勾、其余用 sparkles。 */
private fun noticeGlyph(text: String): Glyph = when {
    text.contains("失败") || text.contains("错误") || text.contains("无法") ||
        text.contains("不了") || text.contains("不在") || text.contains("没") -> Glyph.Close
    text.startsWith("已") || text.contains("成功") || text.contains("完成") -> Glyph.Check
    else -> Glyph.Sparkles
}

@Composable
fun EmptyState(
    glyph: Glyph,
    title: String,
    message: String,
    action: @Composable (() -> Unit)? = null,
) {
    val ui = LocalInk.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(78.dp)
                .clip(SmoothCorner(26.dp))
                .background(ui.palette.faint)
                .border(.8.dp, ui.palette.line, SmoothCorner(26.dp)),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(glyph, ui.palette.muted, 30.dp, 1.3f)
        }
        Spacer(Modifier.height(18.dp))
        Txt(title, size = 17f, weight = FontWeight.Medium, color = ui.palette.ink)
        Spacer(Modifier.height(6.dp))
        Txt(message, size = 11.5f, color = ui.palette.muted, lineHeight = 18f, align = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun FpsMeter(modifier: Modifier = Modifier) {
    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        var frames = 0
        while (true) {
            val t = withFrameNanos { it }
            frames++
            if (t - last >= 900_000_000L) {
                fps = (frames * 1_000_000_000L / (t - last)).toInt()
                frames = 0
                last = t
            }
        }
    }
    val ui = LocalInk.current
    Row(
        modifier
            .clip(CircleShape)
            .background(if (fps >= 110) Color(0xFF1D7A4B) else Color(0xFF8A5A1D))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(5.dp))
        Txt("$fps fps", size = 9f, color = Color.White, weight = FontWeight.Medium)
    }
}

@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val ui = LocalInk.current
    val motion = ui.settings.motion != "none"
    // 3.26.9：选中时外环与勾一起「长出来」，不再直接换描边和图标。
    val p by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (motion) spring(MOYU_DAMPING, 480f) else tween(0),
        label = "colorDot",
    )
    val v = p.coerceIn(0f, 1f)
    val checkColor = if (color.red * .3f + color.green * .6f + color.blue * .1f > .6f) {
        Color(0xFF171713)
    } else {
        Color.White
    }
    Canvas(Modifier.size(34.dp).bouncy { onClick() }) {
        drawCircle(color, radius = size.minDimension / 2)
        if (v > .001f) {
            drawCircle(
                ui.palette.ink.copy(alpha = v),
                radius = size.minDimension / 2 - 1.5.dp.toPx(),
                style = Stroke(2.dp.toPx()),
            )
            val s = .55f + .45f * v
            val check = Path().apply {
                moveTo(center.x - size.width * .22f * s, center.y + size.height * .01f * s)
                lineTo(center.x - size.width * .06f * s, center.y + size.height * .18f * s)
                lineTo(center.x + size.width * .23f * s, center.y - size.height * .18f * s)
            }
            val measure = PathMeasure().apply { setPath(check, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * v, partial, true)
            drawPath(
                partial,
                checkColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        } else {
            drawCircle(
                lerp(Color.Black.copy(alpha = .12f), Color.White.copy(alpha = .22f), ui.palette.darkAmount),
                radius = size.minDimension / 2 - .4.dp.toPx(),
                style = Stroke(.8.dp.toPx()),
            )
        }
    }
}

fun timeAgoShort(ms: Long): String = relativeTime(ms)

fun statusBarTop(): Dp = 8.dp

@Composable
fun TopInsetSpacer() {
    Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp))
}

@Composable
fun useScreenWidth(): Dp = LocalConfiguration.current.screenWidthDp.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalFadeRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier, content = content)
}

fun closeApp(activity: Activity) {
    activity.finish()
}

val Int.dpValue: Dp get() = this.dp

fun formatPercent(value: Float): String = "${(value * 100).roundToInt()}%"

fun clockAngle(hour: Int, minute: Int): Float = ((hour % 12) + minute / 60f) * 30f

fun polar(cx: Float, cy: Float, r: Float, angleDeg: Float): Offset {
    val rad = (angleDeg - 90f) * PI.toFloat() / 180f
    return Offset(cx + cos(rad) * r, cy + sin(rad) * r)
}



