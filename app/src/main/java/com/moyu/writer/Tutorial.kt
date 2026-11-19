package com.moyu.writer

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val TAG = "MOYU_TUTORIAL"

/** 教练组的 id。页内教学组以后在这里补常量，不要散落字符串。 */
object TutorialGroups {
    const val LANDING = "tutorial.landing"
}

/**
 * 一步要指哪块像素。
 *
 * [None] = 没有可指的控件，画居中大卡（上手 6 步里的说明步）。
 * [Anchor] = 指向页面用 [tutorialAnchor] 注册的控件；找不到就跳过这一步（见 [findPlayable]）。
 */
sealed interface TutorialTarget {
    data object None : TutorialTarget
    data class Anchor(val id: String) : TutorialTarget
}

/**
 * 教练式引导的一步。
 *
 * [group] 是组 id（写回 [AppSettings.tutorialDone]）；[section] 是卡顶的小字
 * （「上手 · 1 / 6」这类会变的位置信息，不是装饰）；[glyph] 只在居中大卡里画大图标。
 */
data class TutorialStep(
    val id: String,
    val group: String,
    val title: String,
    val body: String,
    val target: TutorialTarget = TutorialTarget.None,
    val glyph: Glyph? = null,
    val section: String? = null,
)

data class TutorialGroup(
    val id: String,
    val steps: List<TutorialStep>,
)

/**
 * 页面侧的锚点表。控件用 [tutorialAnchor] 把当前 `boundsInWindow` 登记进来，
 * 引导层按 id 取。
 *
 * 关键：读的时候**重新问一遍**控件当前坐标，不能只吃 `onGloballyPositioned` 的缓存。
 * DepthStack 的翻页是 `graphicsLayer.translationX`，只改绘制变换、不重跑布局回调，
 * 缓存一次就会让挖洞停在上一个位置。
 */
class TutorialRegistry {
    private class Entry(var rect: Rect, var coords: LayoutCoordinates?)

    private val entries = HashMap<String, Entry>()
    private val owners = HashMap<String, Any>()

    internal fun register(id: String, owner: Any, coords: LayoutCoordinates) {
        owners[id] = owner
        entries[id] = Entry(coords.boundsInWindow(), coords)
    }

    internal fun unregister(id: String, owner: Any) {
        if (owners[id] === owner) {
            owners.remove(id)
            entries.remove(id)
        }
    }

    /** 当前坐标；控件已销毁返回 null。每次调用都重算，允许逐帧调用。 */
    fun anchorRect(id: String): Rect? {
        val entry = entries[id] ?: return null
        val coords = entry.coords
        if (coords != null && coords.isAttached) {
            val fresh = runCatching { coords.boundsInWindow() }.getOrNull()
            if (fresh != null) {
                entry.rect = fresh
                return fresh
            }
        }
        return entry.rect
    }
}

val LocalTutorialRegistry = staticCompositionLocalOf<TutorialRegistry?> { null }

/**
 * 页面侧注册锚点的唯一入口。
 *
 * ```kotlin
 * GlassIconButton(Glyph.Plus, onClick = onAdd, modifier = Modifier.tutorialAnchor("shelf.addBook"))
 * ```
 *
 * 没有提供 [LocalTutorialRegistry] 时是空操作，页面不用感知引导是否在跑。
 */
@Composable
fun Modifier.tutorialAnchor(id: String): Modifier {
    val registry = LocalTutorialRegistry.current ?: return this
    val owner = remember(id, registry) { Any() }
    DisposableEffect(registry, id) {
        onDispose { registry.unregister(id, owner) }
    }
    return this.onGloballyPositioned { coords -> registry.register(id, owner, coords) }
}

/**
 * 当前在跑的引导组。页内首次教学（Lead 后续接线）从页面里 `start(group)`；
 * 同一时间只允许一组，完成/退出都会写回 `tutorialDone`。
 */
class TutorialController(private val onFinished: (String) -> Unit) {
    var current: TutorialGroup? by mutableStateOf(null)
        private set

    fun start(group: TutorialGroup) {
        if (current != null || group.steps.isEmpty()) return
        current = group
    }

    fun finish() {
        val group = current ?: return
        current = null
        onFinished(group.id)
    }
}

val LocalTutorialController = staticCompositionLocalOf<TutorialController?> { null }

/**
 * 教练式引导层：半透明蒙层 + 目标控件挖洞高亮 + 自动避让的气泡/居中大卡。
 *
 * 进度规则（照 3.26.51 的设计）：
 * - 目标控件不存在 → 跳过该步并记日志，继续下一步；整组都没有可玩的步就直接结束，不卡住。
 * - 目标 rect 每帧重读，键盘/旋转/字号/翻页图层位移都会让挖洞跟着走。
 * - 蒙层吃掉全部触摸，只有「下一步」「按钮」和「退出教学」可点；退出即把整组标记完成。
 * - `motion=none` 时蒙层和气泡直接出现，不做淡入。
 */
@Composable
fun TutorialOverlay(
    group: TutorialGroup,
    registry: TutorialRegistry,
    onFinished: () -> Unit,
) {
    val ui = LocalInk.current
    val motion = ui.settings.motion != "none"
    val visibility = remember(group.id) { MutableTransitionState(false) }
    var stepIndex by remember(group.id) { mutableIntStateOf(-1) }
    var closing by remember(group.id) { mutableStateOf(false) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var hole by remember(group.id) { mutableStateOf<Rect?>(null) }

    fun advance() {
        if (closing) return
        val next = findPlayable(group, stepIndex + 1, registry)
        if (next < 0) {
            closing = true
        } else {
            stepIndex = next
            hole = null
        }
    }

    fun requestClose() {
        if (!closing) closing = true
    }

    // 起组先等两帧：页面锚点在 onGloballyPositioned 里注册，早一帧会被误判成「目标不存在」。
    LaunchedEffect(group.id) {
        withFrameNanos { }
        withFrameNanos { }
        var index = findPlayable(group, 0, registry, quiet = true)
        // 整组目标暂时都没注册时再多等几帧（慢布局/懒列表），别一上来就空组。
        var waits = 0
        while (index < 0 && waits < 20) {
            withFrameNanos { }
            index = findPlayable(group, 0, registry, quiet = true)
            waits++
        }
        if (index < 0) {
            Log.d(TAG, "group ${group.id}: no playable step, skip tutorial")
            closing = true
        } else {
            for (i in 0 until index) logSkip(group.steps[i], "anchor missing")
            stepIndex = index
            visibility.targetState = true
        }
    }

    // 退场播完才回调（motion=none 立即）；回调后控制器把组卸掉、写 tutorialDone。
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        visibility.targetState = false
        if (motion) delay(180)
        onFinished()
    }

    BackHandler { requestClose() }

    if (stepIndex < 0) return

    val step = group.steps[stepIndex]
    val nextIndex = findPlayable(group, stepIndex + 1, registry, quiet = true)
    val isLast = nextIndex < 0

    // 每帧重读目标 rect：DepthStack 的 graphicsLayer 平移不触发布局回调，
    // 只靠 onGloballyPositioned 的缓存会让挖洞停在旧位置。
    LaunchedEffect(step.id) {
        val anchor = step.target as? TutorialTarget.Anchor ?: return@LaunchedEffect
        var missingFrames = 0
        while (true) {
            withFrameNanos { }
            val raw = registry.anchorRect(anchor.id)
            val local = if (raw != null && raw.width > 1f && raw.height > 1f) {
                raw.translate(-overlayOrigin.x, -overlayOrigin.y)
            } else {
                null
            }
            if (local != hole) {
                hole = local
                missingFrames = 0
            } else if (local == null) {
                missingFrames++
                if (missingFrames > 8) {
                    // 目标在步内被撤掉（页面重建/条件分支）也算正常情况：跳过继续。
                    logSkip(step, "anchor '${anchor.id}' lost while showing")
                    advance()
                    return@LaunchedEffect
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() },
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(if (motion) 180 else 0)),
            exit = fadeOut(tween(if (motion) 160 else 0)),
            modifier = Modifier.fillMaxSize(),
        ) {
            TutorialStage(
                step = step,
                isLast = isLast,
                hole = hole,
                onNext = { advance() },
                onExit = { requestClose() },
            )
        }
    }
}

/** 从 [from] 起找第一个能显示的步；目标不在的步记日志后跳过。全部不可显示返回 -1。 */
private fun findPlayable(
    group: TutorialGroup,
    from: Int,
    registry: TutorialRegistry,
    quiet: Boolean = false,
): Int {
    if (from >= group.steps.size) return -1
    for (i in from until group.steps.size) {
        val step = group.steps[i]
        val target = step.target
        if (target is TutorialTarget.Anchor) {
            val rect = registry.anchorRect(target.id)
            val found = rect != null && rect.width > 1f && rect.height > 1f
            if (!found) {
                if (!quiet) logSkip(step, "anchor '${target.id}' missing")
                continue
            }
        }
        return i
    }
    return -1
}

private fun logSkip(step: TutorialStep, reason: String) {
    Log.d(TAG, "skip step ${step.id} (${step.group}): $reason")
}

@Composable
private fun TutorialStage(
    step: TutorialStep,
    isLast: Boolean,
    hole: Rect?,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    val density = LocalDensity.current
    val anchored = hole != null
    val marginPx = with(density) { 16.dp.roundToPx() }
    val gapPx = with(density) { 14.dp.toPx() }
    val cornerPx = with(density) { 18.dp.toPx() }
    val topInsetPx = WindowInsets.statusBars.getTop(density)
    val bottomInsetPx = max(
        WindowInsets.navigationBars.getBottom(density),
        WindowInsets.ime.getBottom(density),
    )

    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            TutorialScrim(hole = hole, cornerPx = cornerPx)
            if (anchored) {
                TutorialBubble(step = step, isLast = isLast, onNext = onNext, onExit = onExit)
            } else {
                TutorialCard(step = step, isLast = isLast, onNext = onNext, onExit = onExit)
            }
        },
    ) { measurables, constraints ->
        val stageW = constraints.maxWidth
        val stageH = constraints.maxHeight
        val scrim = measurables[0].measure(constraints)
        val bubbleMaxW = with(density) { (if (anchored) 336.dp else 384.dp).roundToPx() }
            .coerceAtMost((stageW - marginPx * 2).coerceAtLeast(1))
        val bubble = measurables[1].measure(
            Constraints(
                minWidth = 0,
                maxWidth = bubbleMaxW,
                minHeight = 0,
                maxHeight = (stageH - topInsetPx - bottomInsetPx - marginPx * 2).coerceAtLeast(1),
            ),
        )
        val bw = bubble.width
        val bh = bubble.height
        val safeTop = topInsetPx + marginPx
        val safeBottom = stageH - bottomInsetPx - marginPx
        val rightLimit = (stageW - bw - marginPx).coerceAtLeast(marginPx)

        val pos: IntOffset = if (hole == null) {
            val x = ((stageW - bw) / 2).coerceIn(marginPx, rightLimit)
            val y = ((stageH - bh) / 2).coerceIn(safeTop, (safeBottom - bh).coerceAtLeast(safeTop))
            IntOffset(x, y)
        } else {
            placeNear(
                hole = hole,
                bw = bw,
                bh = bh,
                stageW = stageW,
                stageH = stageH,
                safeTop = safeTop,
                safeBottom = safeBottom,
                margin = marginPx,
                gap = gapPx,
            )
        }

        layout(stageW, stageH) {
            scrim.place(0, 0)
            bubble.place(pos.x, pos.y)
        }
    }
}

/**
 * 气泡落点：优先目标下方，其次上方，再试左右；四边都放不下时退到屏幕空的一侧。
 * 正常情况下气泡与目标 rect 不重叠（「不遮挡它」）；目标大到占满屏时才允许压到蒙层空白处。
 */
private fun placeNear(
    hole: Rect,
    bw: Int,
    bh: Int,
    stageW: Int,
    stageH: Int,
    safeTop: Int,
    safeBottom: Int,
    margin: Int,
    gap: Float,
): IntOffset {
    val x = ((hole.left + hole.right) / 2f - bw / 2f).roundToInt()
        .coerceIn(margin, (stageW - bw - margin).coerceAtLeast(margin))
    val below = hole.bottom + gap
    val above = hole.top - gap - bh
    if (below + bh <= safeBottom) return IntOffset(x, below.roundToInt())
    if (above >= safeTop) return IntOffset(x, above.roundToInt())

    // 上下都放不下：试右侧、左侧。侧放时纵向避开目标中心，优先贴着它。
    val centerY = ((hole.top + hole.bottom) / 2f - bh / 2f).roundToInt()
        .coerceIn(safeTop, (safeBottom - bh).coerceAtLeast(safeTop))
    val rightX = hole.right + gap
    if (rightX + bw <= stageW - margin) return IntOffset(rightX.roundToInt(), centerY)
    val leftX = hole.left - gap - bw
    if (leftX >= margin) return IntOffset(leftX.roundToInt(), centerY)

    // 目标占了大半屏：放在目标较远的一侧，宁可压到蒙层空白也不整个盖住目标。
    val y = if (hole.bottom < stageH / 2f) (safeBottom - bh).coerceAtLeast(safeTop) else safeTop
    return IntOffset(x, y)
}

@Composable
private fun TutorialScrim(hole: Rect?, cornerPx: Float) {
    val ui = LocalInk.current
    val p = ui.palette
    val dim = if (p.isDark) .62f else .55f
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // 蒙层吃掉全部触摸（含拖动），只有气泡里的按钮在它上面。
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        val base = Color.Black.copy(alpha = dim)
        if (hole == null) {
            drawRect(base)
            return@Canvas
        }
        val pad = 6.dp.toPx()
        val hr = Rect(
            hole.left - pad,
            hole.top - pad,
            hole.right + pad,
            hole.bottom + pad,
        )
        val holePath = Path().apply {
            // 用 Path 差集（EvenOdd）挖洞，不做真裁剪：高亮框之外的纸面仍然原样画。
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRoundRect(
                RoundRect(
                    left = hr.left,
                    top = hr.top,
                    right = hr.right,
                    bottom = hr.bottom,
                    topLeftCornerRadius = CornerRadius(cornerPx),
                    topRightCornerRadius = CornerRadius(cornerPx),
                    bottomRightCornerRadius = CornerRadius(cornerPx),
                    bottomLeftCornerRadius = CornerRadius(cornerPx),
                ),
            )
        }
        drawPath(holePath, base)
        // 目标四周一圈主题色：细环 + 外侧一层淡光晕，读作「这里可以点」。
        drawRoundRect(
            color = ui.accent.copy(alpha = .16f),
            topLeft = Offset(hr.left - 3.dp.toPx(), hr.top - 3.dp.toPx()),
            size = Size(hr.width + 6.dp.toPx(), hr.height + 6.dp.toPx()),
            cornerRadius = CornerRadius(cornerPx + 3.dp.toPx()),
            style = Stroke(width = 8.dp.toPx()),
        )
        drawRoundRect(
            color = ui.accent.copy(alpha = .9f),
            topLeft = Offset(hr.left, hr.top),
            size = Size(hr.width, hr.height),
            cornerRadius = CornerRadius(cornerPx),
            style = Stroke(width = 1.8.dp.toPx()),
        )
    }
}

/** 居中大卡：保留原来引导页「大图标 + 标题 + 说明」的视觉语言。 */
@Composable
private fun TutorialCard(
    step: TutorialStep,
    isLast: Boolean,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val shape = SmoothCorner(28.dp)
    Column(
        Modifier
            .widthIn(max = 384.dp)
            .clip(shape)
            .background(p.paper)
            .border(1.dp, p.line, shape)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt(
                step.section ?: "上手",
                size = 10f,
                color = ui.accent,
                letterSpacing = 1.8f,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            TutorialExitButton(onExit = onExit)
        }
        Spacer(Modifier.height(18.dp))
        TutorialGlyph(step.glyph ?: Glyph.Sparkles)
        Spacer(Modifier.height(18.dp))
        Txt(
            step.title,
            size = 30f,
            weight = FontWeight.SemiBold,
            color = p.ink,
            lineHeight = 36f,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Txt(
            step.body,
            size = 13.5f,
            color = p.muted,
            lineHeight = 23f,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            if (isLast) "完成" else "下一步",
            onNext,
            Modifier.fillMaxWidth(),
            glyph = if (isLast) Glyph.Check else Glyph.Chevron,
        )
    }
}

/** 指向控件的气泡：标题 + 说明 + 下一步，右上角退出。 */
@Composable
private fun TutorialBubble(
    step: TutorialStep,
    isLast: Boolean,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val shape = SmoothCorner(22.dp)
    Column(
        Modifier
            .widthIn(max = 336.dp)
            .clip(shape)
            .background(p.paper)
            .border(1.dp, p.line, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            step.glyph?.let { glyph ->
                LineIcon(glyph, ui.accent, 14.dp, 1.5f)
                Spacer(Modifier.size(6.dp))
            }
            Txt(
                step.section ?: "上手",
                size = 9.5f,
                color = ui.accent,
                letterSpacing = 1.4f,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            TutorialExitButton(onExit = onExit, compact = true)
        }
        Spacer(Modifier.height(8.dp))
        Txt(step.title, size = 17f, weight = FontWeight.SemiBold, color = p.ink, lineHeight = 23f)
        Spacer(Modifier.height(6.dp))
        Txt(step.body, size = 12.5f, color = p.muted, lineHeight = 20f)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            if (isLast) "完成" else "下一步",
            onNext,
            Modifier.fillMaxWidth(),
            glyph = if (isLast) Glyph.Check else Glyph.Chevron,
        )
    }
}

@Composable
private fun TutorialExitButton(onExit: () -> Unit, compact: Boolean = false) {
    val p = LocalInk.current.palette
    Box(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 44.dp)
            .clip(CircleShape)
            .bouncy(scale = .94f) { onExit() },
        contentAlignment = Alignment.Center,
    ) {
        Txt("退出教学", size = if (compact) 10f else 10.5f, color = p.muted)
    }
}

@Composable
private fun TutorialGlyph(glyph: Glyph) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        ui.accent.copy(alpha = if (p.isDark) .30f else .20f),
                        ui.accent.copy(alpha = if (p.isDark) .06f else .05f),
                    ),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
            drawCircle(
                color = ui.accent.copy(alpha = .22f),
                radius = r - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawArc(
                color = ui.accent.copy(alpha = .30f),
                startAngle = -60f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                size = Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        LineIcon(glyph, ui.accent, 34.dp, 1.6f)
    }
}
