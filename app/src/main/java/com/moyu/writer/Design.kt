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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.Dp
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

data class InkPalette(
    val bgTop: Color,
    val bgBottom: Color,
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val isDark: Boolean,
) {
    val glassTint: Color get() = if (isDark) Color.White else Color.White
    val glassBase: Color get() = if (isDark) Color(0x2A1C1E1C) else Color(0x55FFFFFF)
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
            isDark = false,
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
    var id by mutableStateOf(0L)
        private set
    private val handler = Handler(Looper.getMainLooper())
    fun show(text: String, long: Boolean = false) {
        val token = toastId.incrementAndGet()
        id = token
        message = text
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
    val accent = accentFor(settings.accent, dark)
    val view = LocalView.current
    val haptics = settings.haptics
    LaunchedEffect(dark) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
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
        LocalInk provides InkUi(palette, settings, accent, settings.font != "sans"),
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
        isDark = true,
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
            isDark = false,
        )
        "white" -> InkPalette(
            bgTop = Color(0xFFFDFDFB),
            bgBottom = Color(0xFFF2F2EE),
            paper = Color(0xFFFFFEFB),
            ink = Color(0xFF161613),
            muted = Color(0xFF8B8B85),
            faint = Color(0x0D161613),
            line = Color(0x17161613),
            isDark = false,
        )
        "sepia" -> InkPalette(
            bgTop = Color(0xFFF7F0E1),
            bgBottom = Color(0xFFEADFC9),
            paper = Color(0xFFF8F1E2),
            ink = Color(0xFF211B11),
            muted = Color(0xFF8D806A),
            faint = Color(0x12281E0D),
            line = Color(0x1E281E0D),
            isDark = false,
        )
        "night" -> InkPalette(
            bgTop = Color(0xFF111312),
            bgBottom = Color(0xFF080909),
            paper = Color(0xFF151716),
            ink = Color(0xFFF0EDE5),
            muted = Color(0xFF8C8E87),
            faint = Color(0x14FFFFFF),
            line = Color(0x18FFFFFF),
            isDark = true,
        )
        else -> InkPalette(
            bgTop = Color(0xFFF8F6F1),
            bgBottom = Color(0xFFEFEAE0),
            paper = Color(0xFFF7F3EA),
            ink = Color(0xFF191813),
            muted = Color(0xFF8A857B),
            faint = Color(0x0F1B1710),
            line = Color(0x1A1B1710),
            isDark = false,
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
        val frostColor = if (p.isDark) Color(0xFF191C1A) else tint
        val bodyTint = lerp(frostColor, if (p.isDark) Color.White else p.ink, fillAmount * .12f)
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
        val fill = if (p.isDark) {
            val stops = if (fillAmount >= 1f) {
                floatArrayOf(1f, 1f, 1f)
            } else {
                floatArrayOf(.045f, .86f, .96f)
            }
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (solid) .06f else bodyAlpha * stops[0]),
                    frostColor.copy(alpha = if (solid) .985f else bodyAlpha * stops[1]),
                    bodyTint.copy(alpha = if (solid) .99f else bodyAlpha * stops[2]),
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
        } else {
            // Stops no longer fade to 74% at the far corner once fill is 1: that fade is
            // what made a solid control look like a ghost of itself.
            val stops = if (fillAmount >= 1f) {
                floatArrayOf(1f, 1f, 1f)
            } else {
                floatArrayOf(.90f, .84f, .74f)
            }
            Brush.linearGradient(
                colors = listOf(
                    bodyTint.copy(alpha = if (solid) .96f else bodyAlpha * stops[0]),
                    bodyTint.copy(alpha = if (solid) .985f else bodyAlpha * stops[1]),
                    bodyTint.copy(alpha = if (solid) .99f else bodyAlpha * stops[2]),
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
        }
        val caustic = Brush.radialGradient(
            colors = listOf(
                // 折射 is the only thing that may push these two glows around.
                // Scaling them by the fill as well meant that raising 填充 made
                // the surface look hazier instead of more opaque, which is the
                // opposite of what the slider promises.
                Color.White.copy(
                    alpha = if (p.isDark) .06f * refractionStrength else .52f * refractionStrength,
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
        val edgeTone = if (p.isDark) Color.White else p.ink
        val rim = Brush.linearGradient(
            colors = listOf(
                edgeTone.copy(alpha = rimAlpha(if (p.isDark) .24f else .22f)),
                Color.White.copy(alpha = rimAlpha(if (p.isDark) .06f else .78f)),
                if (iridescent) accentColor.copy(
                    alpha = rimAlpha((if (p.isDark) .24f else .42f) * refractionStrength),
                ) else Color.Transparent,
                edgeTone.copy(alpha = rimAlpha(if (p.isDark) .16f else .16f)),
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        )
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
                if (borderColor != null) {
                    drawPath(outlinePath, borderColor, style = Stroke(width = frameWidth))
                } else if (rimStrength > 0f) {
                    drawPath(outlinePath, rim, style = Stroke(width = frameWidth))
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
            } else if (rimStrength > 0f) {
                drawRoundRect(
                    rim,
                    cornerRadius = corner,
                    style = Stroke(width = frameWidth),
                )
            }
            if (specular && specularStrength > 0f) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(
                                alpha = (if (p.isDark) .20f else .94f) * specularStrength,
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
                        Color.White.copy(alpha = if (p.isDark) .022f else .05f),
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
    rimStrength: Float = 1f,
    specularStrength: Float = 1f,
    refractionStrength: Float = 1f,
    borderColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedShape = shape ?: SmoothCorner(radius.dp)
    val clipped = modifier.clip(resolvedShape)
    // Haze used to hard-code a 25% white tint, which turned the dark TabBar into
    // a flat gray slab. Tint the blur toward the paper color in each theme.
    val hazeTint = if (LocalInk.current.palette.isDark) 0x4D101312L else 0x40FFFFFFL
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
    scale: Float = .97f,
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
            scaleX = anim.value + stretch
            scaleY = anim.value - stretch * .6f
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
    val bg = if (dark || !ui.palette.isDark) ui.palette.ink else Color(0xFFF3EFE6)
    val fg = if (dark || !ui.palette.isDark) ui.palette.bgTop else Color(0xFF191813)
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
    lerp(ui.palette.paper, ui.accent, if (ui.palette.isDark) .14f else .10f)

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
            .clip(CircleShape),
        radius = 999f,
        shape = CircleShape,
        flat = true,
        tone = dockTone(ui),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .bouncy { onClick() }
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
            .clip(CircleShape),
        radius = size.value / 2f,
        shape = CircleShape,
        flat = true,
        tone = dockTone(ui),
    ) {
        Box(Modifier.fillMaxSize().bouncy { onClick() }, contentAlignment = Alignment.Center) {
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
    val bg by animateColorAsState(
        if (selected) accentTint(ui.palette, ui.accent) else Color.Transparent,
        animationSpec = spring(MOYU_DAMPING, 420f),
        label = "chip",
    )
    val fg by animateColorAsState(
        if (selected) ui.palette.ink else ui.palette.muted,
        animationSpec = spring(MOYU_DAMPING, 520f),
        label = "chipFg",
    )
    if (selected) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(bg)
                .bouncy { onClick() }
                .padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) Txt(leading, size = 10f, color = fg, modifier = Modifier.padding(end = 5.dp))
            Txt(label, size = 11.5f, color = fg, weight = FontWeight.Medium)
        }
    } else {
        MoyuSurface(Modifier.clip(CircleShape),
            radius = 20f,
            shape = CircleShape,
            flat = true,
            tone = dockTone(ui),
        ) {
            Row(
                Modifier.bouncy { onClick() }.padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) Txt(leading, size = 10f, color = fg, modifier = Modifier.padding(end = 5.dp))
                Txt(label, size = 11.5f, color = fg)
            }
        }
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
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val ui = LocalInk.current
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
    val ui = LocalInk.current
    val motion = ui.settings.motion != "none"
    val progress by animateFloatAsState(
        if (done) 1f else 0f,
        if (motion) spring(MOYU_DAMPING, 520f, PROGRESS_THRESHOLD) else tween(0),
        visibilityThreshold = PROGRESS_THRESHOLD,
        label = "todoBox",
    )
    val border = lerp(ui.palette.muted.copy(alpha = .55f), ui.accent, progress)
    val fill = lerp(Color.Transparent, ui.accent, progress)
    val checkColor = if (ui.palette.isDark) ui.palette.bgTop else Color.White
    Box(
        Modifier
            .size(boxSize)
            .bouncy(scale = .92f, hapticType = 1) { onToggle(!done) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val radius = 6.dp.toPx()
            drawRoundRect(fill, cornerRadius = CornerRadius(radius, radius))
            drawRoundRect(
                border,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = 1.2.dp.toPx()),
            )
            if (progress > .01f) {
                val path = Path().apply {
                    moveTo(w * .22f, h * .52f)
                    lineTo(w * .42f, h * .70f)
                    lineTo(w * .79f, h * .30f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), partial, true)
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
    val off = if (ui.palette.isDark) Color(0xFF3C403D) else Color(0xFFE4DFD4)
    val knob = if (ui.palette.isDark) Color(0xFFF3F1EA) else Color(0xFFFDFCF8)
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
    lerp(p.paper, accent, if (p.isDark) .20f else .13f)

/** The edge of a selected block: the same tint, denser, so a 1 dp line reads. */
fun accentLine(p: InkPalette, accent: Color): Color =
    lerp(p.paper, accent, if (p.isDark) .42f else .30f)

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
                .background(if (ui.palette.isDark) Color(0xFF313532) else Color(0xFFDDD7CC))
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
                .background(if (ui.palette.isDark) Color(0xFFF4F1E9) else Color.White)
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
                .background(if (ui.palette.isDark) Color(0xFFF4F1E9) else Color.White)
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val ui = LocalInk.current
    Dialog(
        onDismissRequest = onDismiss,
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
                .background(Color.Black.copy(alpha = if (ui.palette.isDark) .34f else .18f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (dismissOnOutsideClick) onDismiss()
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
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(spring(MOYU_DAMPING, 500f)) +
                        scaleIn(initialScale = .965f, animationSpec = spring(MOYU_DAMPING, 420f, PRESS_THRESHOLD)),
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
                                content = content,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogBlur() {
    val view = LocalView.current
    val ui = LocalInk.current
    LaunchedEffect(Unit) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.setDimAmount(0.08f)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        androidx.core.view.WindowCompat.getInsetsController(window, view)
            .isAppearanceLightNavigationBars = !ui.palette.isDark
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                window.setBackgroundBlurRadius(58)
            } catch (_: Throwable) {
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
    GlassDialog(onDismiss) {
        DialogTitle(title)
        Spacer(Modifier.height(12.dp))
        Txt(message, size = 12.5f, color = ui.palette.muted, lineHeight = 20f)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", onDismiss, Modifier.weight(1f))
            Box(Modifier.weight(1f)) {
                if (destructive) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(SmoothCorner(999.dp))
                            .background(Color(0xFFB3453D))
                            .bouncy { onConfirm() },
                        contentAlignment = Alignment.Center,
                    ) { Txt(confirmLabel, size = 14f, color = Color.White, weight = FontWeight.Medium) }
                } else {
                    PrimaryButton(confirmLabel, { onConfirm() }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

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
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val ui = LocalInk.current
    var closing by remember { mutableStateOf(false) }
    var dismissReady by remember { mutableStateOf(false) }
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
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visibleState.targetState) {
            if (ui.palette.isDark) .38f else .22f
        } else {
            0f
        },
        animationSpec = spring(MOYU_DAMPING, 520f),
        visibilityThreshold = .002f,
        label = "inlineSheetScrim",
    )

    BackHandler { close() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = dismissReady && !closing,
            ) { close() }
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
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
                        .heightIn(max = panelMax),
                    radius = sheetCorner,
                    shape = panelShape,
                    flat = true,
                ) {
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
                            .padding(horizontal = 20.dp)
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

@Composable
fun ModalSheet(
    onDismiss: () -> Unit,
    imeAware: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ui = LocalInk.current
    val sheetCorner = GlassStyle.corner
    val panelShape = SmoothCorner(
        topStart = sheetCorner.dp,
        topEnd = sheetCorner.dp,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogBlur()
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (ui.palette.isDark) .38f else .22f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
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
                    visible = true,
                    // No spring overshoot here: the old 330f spring made the
                    // sheet top edge bounce/閳ユ竾lash閳?while it was entering.
                    // Slide only: a fading opaque sheet shows the page through
                    // itself while it enters.
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(MOYU_DAMPING, 380f),
                    ),
                ) {
                    MoyuSurface(Modifier
                            .fillMaxWidth()
                            .heightIn(max = panelMax),
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
                            .padding(horizontal = 20.dp)
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
                        content()
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
    // Theme switches now cross-fade the paper colour instead of snapping.
    val bg by animateColorAsState(
        targetValue = p.bgTop,
        animationSpec = spring(MOYU_DAMPING, 420f),
        label = "ambientBg",
    )
    Canvas(modifier.fillMaxSize()) {
        if (!overlayOnly) {
            drawRect(bg)
        }
    }
}

@Composable
fun ToastLayer(modifier: Modifier = Modifier) {
    val ui = LocalInk.current
    val message = ToastBus.message
    // Keep the last text alive while AnimatedVisibility plays its exit. Reading
    // ToastBus.message directly inside the content made the row disappear the
    // instant the bus was cleared, so only an empty container faded out.
    var display by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) display = message
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(spring(MOYU_DAMPING, 500f)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring(MOYU_DAMPING, 420f)),
        exit = fadeOut(spring(MOYU_DAMPING, 520f)) + slideOutVertically(
            targetOffsetY = { it / 3 },
            animationSpec = spring(MOYU_DAMPING, 460f),
        ),
        modifier = modifier.imePadding(),
    ) {
        val text = display
        if (text != null) {
            MoyuSurface(Modifier.padding(horizontal = 28.dp),
                radius = 999f,
                flat = true,
                tone = dockTone(ui),
            ) {
                Row(
                    Modifier.padding(horizontal = 17.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(ui.accent))
                    Spacer(Modifier.width(9.dp))
                    Txt(text, size = 11.5f, color = ui.palette.ink, maxLines = 2)
                }
            }
        }
    }
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
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(2.dp, ui.palette.ink, CircleShape)
                else Modifier.border(
                    .8.dp,
                    if (ui.palette.isDark) Color.White.copy(alpha = .22f) else Color.Black.copy(alpha = .12f),
                    CircleShape,
                )
            )
            .bouncy { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            LineIcon(Glyph.Check, if (color.red * .3f + color.green * .6f + color.blue * .1f > .6f) Color(0xFF171713) else Color.White, 16.dp, 2f)
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



