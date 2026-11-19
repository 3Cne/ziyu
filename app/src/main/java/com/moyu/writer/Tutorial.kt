package com.moyu.writer

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "MOYU_TUTORIAL"

/** 组 id。页内教学组以后在这里补常量，不要散落字符串。 */
object TutorialGroups {
    const val LANDING = "tutorial.landing"
}

/**
 * 一步怎么算过。
 *
 * 3.26.52 起引导分两种过法：一种是「读文案 + 点下一步」，另一种是「必须真的点到目标控件」。
 * 后者是用户点名要的「教你点哪点哪、真点才能过」。
 */
sealed interface TutorialAdvance {
    /**
     * 必须真的点到挖洞里的目标控件才过：气泡不显示「下一步」，只显示指向洞的提示。
     * 洞内点击会先执行页面登记的真实动作（见 [TutorialRegistry.activate]），
     * 再播完成反馈（洞边缘一圈外扩光），然后才进下一步。
     */
    data object OnTargetTap : TutorialAdvance

    /** 读完文案点「下一步」。洞内点击仍会执行真实控件，但不会自己跳步。 */
    data object NextButton : TutorialAdvance
}

/**
 * 一步要指哪块像素。
 *
 * [None] = 没有可指的控件，画居中大卡。
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
 *
 * [advanceOn] 默认 [TutorialAdvance.NextButton]（3.26.51 的行为）；
 * 想让这一步「不点不让过」就设 [TutorialAdvance.OnTargetTap] 并给它一个 [TutorialTarget.Anchor]。
 * 没有锚点的步即使写了 OnTargetTap 也按 NextButton 走（没东西可点）。
 */
data class TutorialStep(
    val id: String,
    val group: String,
    val title: String,
    val body: String,
    val target: TutorialTarget = TutorialTarget.None,
    val advanceOn: TutorialAdvance = TutorialAdvance.NextButton,
    val glyph: Glyph? = null,
    val section: String? = null,
) {
    /** 目标不存在时 OnTargetTap 无从谈起：按「读文案 + 下一步」处理。 */
    internal fun requiresTargetTap(): Boolean =
        advanceOn is TutorialAdvance.OnTargetTap && target is TutorialTarget.Anchor
}

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
 *
 * 3.26.52：除了坐标，锚点还能登记一个 [activate] 用的真实动作。
 * 挖洞盖在页面之上，触摸命中测试只会落到最上层的引导层，事件没法原样转发给下面的控件；
 * 所以「洞内点一下」= 引擎按坐标命中洞之后，调用页面登记的那个动作（与控件自己的 onClick 同一个），
 * 再做完成反馈。没登记动作也能过步，只是不会真的执行控件。
 */
class TutorialRegistry {
    private class Entry(
        var rect: Rect,
        var coords: LayoutCoordinates?,
        var onActivate: (() -> Unit)?,
    )

    private val entries = HashMap<String, Entry>()
    private val owners = HashMap<String, Any>()

    internal fun register(
        id: String,
        owner: Any,
        coords: LayoutCoordinates,
        onActivate: (() -> Unit)? = null,
    ) {
        val fresh = runCatching { coords.boundsInWindow() }.getOrNull() ?: return
        owners[id] = owner
        entries[id] = Entry(fresh, coords, onActivate)
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

    /**
     * 洞被点中：执行页面登记的真实动作。
     *
     * 返回 false = 页面没登记动作（老页面不改也能过步，只是不执行控件）；
     * 动作自己抛异常只记日志，不让引导层崩。
     */
    fun activate(id: String): Boolean {
        val action = entries[id]?.onActivate ?: return false
        runCatching { action() }.onFailure { Log.w(TAG, "onActivate '${id}' failed", it) }
        return true
    }
}

val LocalTutorialRegistry = staticCompositionLocalOf<TutorialRegistry?> { null }

/**
 * 页面侧注册锚点的唯一入口。
 *
 * ```kotlin
 * GlassIconButton(
 *     Glyph.Plus,
 *     onClick = onAdd,
 *     modifier = Modifier.tutorialAnchor("shelf.addBook", onActivate = onAdd),
 * )
 * ```
 *
 * [onActivate] 是「洞被点中时」要执行的页面动作，通常就是同一个 `onClick`；不传也能过步
 * （只做命中判定，不执行控件）。没有提供 [LocalTutorialRegistry] 时是空操作，页面不用感知引导是否在跑。
 */
@Composable
fun Modifier.tutorialAnchor(id: String, onActivate: (() -> Unit)? = null): Modifier {
    val registry = LocalTutorialRegistry.current ?: return this
    val owner = remember(id, registry) { Any() }
    val latestActivate = rememberUpdatedState(onActivate)
    DisposableEffect(registry, id) {
        onDispose { registry.unregister(id, owner) }
    }
    return this.onGloballyPositioned { coords ->
        val action = latestActivate.value
        registry.register(id, owner, coords, action?.let { callback -> { callback() } })
    }
}

/**
 * 当前在跑的引导组。同一时间只允许一组，完成/退出都会写回 [AppSettings.tutorialDone]。
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

/** 3.26.52 的统一节奏：退 120 / 进 180 / 高光扫 300 / 完成反馈 240。`motion=none` 全部 tween(0)。 */
private object TutorialTiming {
    const val EXIT_MS = 120
    const val ENTER_MS = 180
    const val SWEEP_MS = 300
    const val SUCCESS_MS = 240
    const val WRONG_SHAKE_DP = 8f
}

/** 引导层统一用的空间弹簧：.6 / 380（项目里 StoryBible 的「高级」档就是这个）。 */
private fun tutorialSpring(): SpringSpec<Float> = spring(dampingRatio = .6f, stiffness = 380f)

/**
 * 教练式引导层：半透明蒙层 + 目标控件挖洞高亮 + 自动避让的气泡/居中大卡。
 *
 * 3.26.52 起的规则：
 * - **真点才能过**：`advanceOn = OnTargetTap` 的步必须点到洞里；点错（洞外）只抖一下 + 触感，
 *   不换步。洞外全部触摸被吃掉，不会误触下面的页面（「退出教学」永远可点）。
 * - **触摸穿透**：洞内命中后先执行页面登记的真实动作，再播完成反馈、进下一步；
 *   没有登记动作时只算命中，步照过（老页面不改也能用）。
 * - 目标控件不存在 → 跳过该步并记日志；连续 8 帧都取不到才算真的没了（避免重生时闪没）。
 * - 目标 rect 每帧重读（不写入构图），布局/绘制跟着走；页面滚动不会重组整个引导层。
 * - 动效：洞从目标中心 `scaleIn(.86→1)`、环一圈 300ms 高光扫过、切步时旧洞 120ms 收起、
 *   新洞从旧位置 spring 滑到新目标（位置插值，不跳）、气泡从洞的最近边缘 `scaleIn(.94→1)`。
 */
@Composable
fun TutorialOverlay(
    group: TutorialGroup,
    registry: TutorialRegistry,
    onFinished: () -> Unit,
) {
    val ui = LocalInk.current
    val motion = ui.settings.motion != "none"
    val haptic = LocalHaptic.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val holePadPx = with(density) { 6.dp.toPx() }

    val visibility = remember(group.id) { MutableTransitionState(false) }
    var stepIndex by remember(group.id) { mutableIntStateOf(-1) }
    var closing by remember(group.id) { mutableStateOf(false) }
    var transitioning by remember(group.id) { mutableStateOf(false) }
    // 已经点中目标、正在播完成反馈（这段时间不接受第二次点，也不让「锚点丢失」抢先跳步）。
    var tapBusy by remember(group.id) { mutableStateOf(false) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    // 目标矩形（overlay 本地坐标）。这个 State 只在布局/绘制/手势里读，不在本 composable 的
    // 构图里读，所以页面滚动时只重跑布局与绘制，不会重组整棵引导层。
    var targetRect by remember(group.id) { mutableStateOf<Rect?>(null) }
    // 切步时上一个洞的位置：新洞从这里 spring 滑到自己的目标（位置插值，不跳）。
    var fromRect by remember(group.id) { mutableStateOf<Rect?>(null) }

    // 洞的四段动画：入场缩放 / 切步位移 / 环的淡入 / 300ms 高光扫过。
    val holeTravel = remember(group.id) { Animatable(1f) }
    val holeScale = remember(group.id) { Animatable(1f) }
    val ringIn = remember(group.id) { Animatable(1f) }
    val sweep = remember(group.id) { Animatable(1f) }
    // 气泡入场（alpha + .94→1 缩放）。
    val bubbleIn = remember(group.id) { Animatable(1f) }
    // 点中目标的完成反馈 / 点错的 8dp 三段抖动。
    val success = remember(group.id) { Animatable(0f) }
    val shake = remember(group.id) { Animatable(0f) }

    fun requestClose() {
        if (!closing) closing = true
    }

    fun advance() {
        if (closing || transitioning) return
        val next = findPlayable(group, stepIndex + 1, registry)
        if (next < 0) {
            closing = true
            return
        }
        val nextStep = group.steps[next]
        transitioning = true
        scope.launch {
            // 旧洞退场（120ms）：环淡掉、洞缩回目标中心、气泡一起收，读作「这一步收掉」。
            if (motion) {
                coroutineScope {
                    launch { ringIn.animateTo(0f, tween(TutorialTiming.EXIT_MS)) }
                    launch { holeScale.animateTo(0f, tween(TutorialTiming.EXIT_MS)) }
                    launch { bubbleIn.animateTo(0f, tween(TutorialTiming.EXIT_MS)) }
                }
            } else {
                ringIn.snapTo(0f)
                holeScale.snapTo(0f)
                bubbleIn.snapTo(0f)
            }
            // 新洞的起点 = 退场前最后一个洞；新目标是锚点位置，两者之间 spring 插值（不跳）。
            val last = baseHoleRect(targetRect, fromRect, holeTravel.value)
            fromRect = if (nextStep.target is TutorialTarget.Anchor) last else null
            targetRect = null
            holeTravel.snapTo(0f)
            holeScale.snapTo(if (motion) .86f else 1f)
            ringIn.snapTo(0f)
            sweep.snapTo(0f)
            success.snapTo(0f)
            bubbleIn.snapTo(0f)
            tapBusy = false
            stepIndex = next
            transitioning = false
        }
    }

    /** 点错反馈：8dp 横向、三段（右 → 左 → 归位）+ 一次明显触感，不换步。 */
    fun shakeWrongTap() {
        haptic(1)
        if (!motion) return
        scope.launch {
            if (shake.isRunning) return@launch
            shake.animateTo(1f, tween(70, easing = FastOutSlowInEasing))
            shake.animateTo(-.6f, tween(80))
            shake.animateTo(0f, tween(90, easing = LinearOutSlowInEasing))
        }
    }

    fun handleTap(pos: Offset) {
        if (closing || transitioning || tapBusy) return
        val step = group.steps.getOrNull(stepIndex) ?: return
        val anchor = step.target as? TutorialTarget.Anchor
        val hole = targetRect
        if (anchor != null && hole != null && insideHole(hole, pos, holePadPx)) {
            // 洞内命中：先执行页面登记的真实动作（等价于点到了这个控件）。
            val activated = registry.activate(anchor.id)
            if (!activated) {
                Log.d(
                    TAG,
                    "step ${step.id}: anchor '${anchor.id}' has no onActivate; " +
                        "tap counts but the real action was not run",
                )
            }
            if (step.requiresTargetTap()) {
                tapBusy = true
                scope.launch {
                    if (motion) success.animateTo(1f, tween(TutorialTiming.SUCCESS_MS))
                    advance()
                }
            }
            return
        }
        // 洞外：触摸被吃掉（不误触下层）。只有「必须真点」的步给失败反馈。
        if (step.requiresTargetTap()) shakeWrongTap()
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
            return@LaunchedEffect
        }
        for (i in 0 until index) logSkip(group.steps[i], "anchor missing")
        // 首次出现的入场起点：洞 .86 → 1、环 0 → 1、高光从左边扫过、气泡从洞边长出来。
        holeTravel.snapTo(if (motion) 0f else 1f)
        holeScale.snapTo(if (motion) .86f else 1f)
        ringIn.snapTo(if (motion) 0f else 1f)
        sweep.snapTo(if (motion) 0f else 1f)
        bubbleIn.snapTo(if (motion) 0f else 1f)
        stepIndex = index
        visibility.targetState = true
    }

    // 退场播完才回调（motion=none 立即）；回调后控制器把组卸掉、写 tutorialDone。
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        visibility.targetState = false
        if (motion) delay(180)
        onFinished()
    }

    // 每一步的进场动画（首次出现与切步后共用）。
    LaunchedEffect(stepIndex) {
        if (stepIndex < 0) return@LaunchedEffect
        if (!motion) {
            holeTravel.snapTo(1f)
            holeScale.snapTo(1f)
            ringIn.snapTo(1f)
            sweep.snapTo(1f)
            bubbleIn.snapTo(1f)
            return@LaunchedEffect
        }
        coroutineScope {
            launch { holeTravel.animateTo(1f, tutorialSpring()) }
            launch { holeScale.animateTo(1f, tutorialSpring()) }
            launch { ringIn.animateTo(1f, tween(TutorialTiming.ENTER_MS)) }
            launch { sweep.animateTo(1f, tween(TutorialTiming.SWEEP_MS)) }
            launch { bubbleIn.animateTo(1f, tween(TutorialTiming.ENTER_MS)) }
        }
    }

    // 每帧重读目标 rect：DepthStack 的 graphicsLayer 平移不触发布局回调，
    // 只靠 onGloballyPositioned 的缓存会让挖洞停在旧位置。
    LaunchedEffect(stepIndex) {
        val step = group.steps.getOrNull(stepIndex) ?: return@LaunchedEffect
        val anchor = step.target as? TutorialTarget.Anchor ?: run {
            targetRect = null
            return@LaunchedEffect
        }
        var missingFrames = 0
        while (true) {
            withFrameNanos { }
            val raw = registry.anchorRect(anchor.id)
            val local = if (raw != null && raw.width > 1f && raw.height > 1f) {
                raw.translate(-overlayOrigin.x, -overlayOrigin.y)
            } else {
                null
            }
            if (local != null) {
                targetRect = local
                missingFrames = 0
            } else {
                // 短暂取不到（重组/懒加载）先保留上一个位置，别让洞闪没；
                // 连续 8 帧都没有才算真的没了，跳过这一步继续。
                missingFrames++
                if (missingFrames > 8) {
                    targetRect = null
                    if (!transitioning && !closing && !tapBusy) {
                        logSkip(step, "anchor '${anchor.id}' lost while showing")
                        advance()
                    }
                    return@LaunchedEffect
                }
            }
        }
    }

    BackHandler { requestClose() }

    val step = group.steps.getOrNull(stepIndex) ?: return

    val isLast = findPlayable(group, stepIndex + 1, registry, quiet = true) < 0
    val needsTap = step.requiresTargetTap()
    val targetInWindow: () -> Rect? = {
        targetRect?.translate(overlayOrigin.x, overlayOrigin.y)
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
                needsTap = needsTap,
                target = { targetRect },
                from = { fromRect },
                targetInWindow = targetInWindow,
                bubbleIn = bubbleIn,
                shake = shake,
                holeTravel = holeTravel,
                holeScale = holeScale,
                ringIn = ringIn,
                sweep = sweep,
                success = success,
                onTap = { pos -> handleTap(pos) },
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

/** 目标矩形与旧位置的插值；新目标还没查到时先停在旧位置（避免洞闪没）。 */
private fun baseHoleRect(target: Rect?, from: Rect?, travel: Float): Rect? = when {
    target == null -> from
    from == null || travel >= .999f -> target
    else -> lerpRect(from, target, travel)
}

private fun lerpRect(a: Rect, b: Rect, t: Float): Rect {
    val f = t.coerceIn(0f, 1f)
    return Rect(
        a.left + (b.left - a.left) * f,
        a.top + (b.top - a.top) * f,
        a.right + (b.right - a.right) * f,
        a.bottom + (b.bottom - a.bottom) * f,
    )
}

/** 目标矩形 → 画面上真正的洞：按 [scale] 以自身中心缩放，再四周各让 [pad]。 */
private fun scaledHole(target: Rect?, from: Rect?, travel: Float, scale: Float, pad: Float): Rect? {
    val base = baseHoleRect(target, from, travel) ?: return null
    val cx = base.center.x
    val cy = base.center.y
    val halfW = base.width * scale / 2f
    val halfH = base.height * scale / 2f
    val p = pad * scale
    return Rect(cx - halfW - p, cy - halfH - p, cx + halfW + p, cy + halfH + p)
}

private fun insideHole(hole: Rect, pos: Offset, pad: Float): Boolean =
    pos.x >= hole.left - pad && pos.x <= hole.right + pad &&
        pos.y >= hole.top - pad && pos.y <= hole.bottom + pad

/** 气泡从洞的最近边缘长出来：气泡在洞下方 → 上边缘为原点，其它方向同理。 */
private fun growOrigin(box: Rect, hole: Rect): TransformOrigin {
    val cx = box.center.x
    val cy = box.center.y
    return when {
        cy >= hole.bottom -> TransformOrigin(.5f, 0f)
        cy <= hole.top -> TransformOrigin(.5f, 1f)
        cx >= hole.right -> TransformOrigin(0f, .5f)
        else -> TransformOrigin(1f, .5f)
    }
}

/**
 * 蒙层与挖洞。用 Path 差集（EvenOdd）挖洞，不做真裁剪：高亮框之外的纸面仍然原样画。
 *
 * 3.26.52 的「高级质感」都在这一个函数里：
 * - 外圈柔光（渐变环，不是纯色描边）；
 * - 内侧柔光（从洞内缘往里的竖向渐变）；
 * - 1.5dp 亮环；
 * - 300ms 高光带扫过（渐变的 start/end 平移，不是旋转遮罩）；
 * - 点中后一圈外扩光 + 洞内极淡暖光（完成反馈）。
 */
private fun DrawScope.drawScrim(
    hole: Rect?,
    dim: Float,
    cornerPx: Float,
    accent: Color,
    ringAlpha: Float,
    sweepT: Float,
    successT: Float,
    isDark: Boolean,
) {
    val mask = Color.Black.copy(alpha = dim)
    if (hole == null || hole.width < 1f || hole.height < 1f) {
        drawRect(mask)
        return
    }
    val maskPath = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        addRoundRect(
            RoundRect(
                left = hole.left,
                top = hole.top,
                right = hole.right,
                bottom = hole.bottom,
                topLeftCornerRadius = CornerRadius(cornerPx),
                topRightCornerRadius = CornerRadius(cornerPx),
                bottomRightCornerRadius = CornerRadius(cornerPx),
                bottomLeftCornerRadius = CornerRadius(cornerPx),
            ),
        )
    }
    drawPath(maskPath, mask)
    if (ringAlpha <= .01f) return

    val corner = CornerRadius(cornerPx)

    // ① 内侧柔光：贴着洞内缘的一圈竖向渐变（上亮下淡），读作「洞里面有光」。
    val inset = 1.5.dp.toPx()
    val inner = Rect(
        hole.left + inset,
        hole.top + inset,
        hole.right - inset,
        hole.bottom - inset,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to accent.copy(alpha = (if (isDark) .30f else .20f) * ringAlpha),
            .55f to accent.copy(alpha = .06f * ringAlpha),
            1f to Color.Transparent,
            startY = inner.top,
            endY = inner.bottom,
        ),
        topLeft = inner.topLeft,
        size = inner.size,
        cornerRadius = CornerRadius((cornerPx - inset).coerceAtLeast(0f)),
        style = Stroke(width = 9.dp.toPx()),
    )

    // ② 外圈柔光：往外扩的一圈渐变，边缘不硬。
    val halo = 4.dp.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to accent.copy(alpha = .18f * ringAlpha),
            1f to accent.copy(alpha = .02f * ringAlpha),
            startY = hole.top - halo,
            endY = hole.bottom + halo,
        ),
        topLeft = Offset(hole.left - halo, hole.top - halo),
        size = Size(hole.width + halo * 2, hole.height + halo * 2),
        cornerRadius = CornerRadius(cornerPx + halo),
        style = Stroke(width = 10.dp.toPx()),
    )

    // ③ 1.5dp 亮环。
    drawRoundRect(
        color = accent.copy(alpha = .92f * ringAlpha),
        topLeft = hole.topLeft,
        size = hole.size,
        cornerRadius = corner,
        style = Stroke(width = 1.5.dp.toPx()),
    )

    // ④ 高光扫过：一条亮带 300ms 从左扫到右（渐变位移，不是描边变色）。
    // 扫完把亮度收掉，否则亮带会停在右边缘变成一颗固定的亮点。
    val sweepProgress = sweepT.coerceIn(0f, 1f)
    val sweepAlpha = (1f - sweepProgress) * ringAlpha
    if (sweepAlpha > .01f) {
        val band = hole.width.coerceAtLeast(1f)
        val sweepStart = hole.left - band + band * 2f * sweepProgress
        drawRoundRect(
            brush = Brush.linearGradient(
                0f to Color.Transparent,
                .36f to Color.White.copy(alpha = .55f * sweepAlpha),
                .5f to Color.White.copy(alpha = .82f * sweepAlpha),
                .64f to Color.White.copy(alpha = .55f * sweepAlpha),
                1f to Color.Transparent,
                start = Offset(sweepStart - band, hole.top - hole.height),
                end = Offset(sweepStart + band, hole.bottom + hole.height),
            ),
            topLeft = hole.topLeft,
            size = hole.size,
            cornerRadius = corner,
            style = Stroke(width = 2.2.dp.toPx()),
        )
    }

    // ⑤ 点中之后的完成反馈：一圈向外扩散的光 + 洞内一层极淡暖光。
    if (successT > .001f) {
        val grow = 7.dp.toPx() * successT
        drawRoundRect(
            color = accent.copy(alpha = .5f * (1f - successT)),
            topLeft = Offset(hole.left - grow, hole.top - grow),
            size = Size(hole.width + grow * 2, hole.height + grow * 2),
            cornerRadius = CornerRadius(cornerPx + grow),
            style = Stroke(width = (2.dp + 1.5.dp * successT).toPx()),
        )
        drawRoundRect(
            color = accent.copy(alpha = .10f * (1f - successT)),
            topLeft = hole.topLeft,
            size = hole.size,
            cornerRadius = corner,
        )
    }
}

/** 气泡底部一层很淡的投影：只往下压 5dp，读作「浮在页面上」，不是 Material 重阴影。 */
private fun DrawScope.drawBubbleHalo(isDark: Boolean) {
    val spread = 10.dp.toPx()
    val drop = 5.dp.toPx()
    val tint = Color.Black.copy(alpha = if (isDark) .32f else .15f)
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            .56f to Color.Transparent,
            .84f to tint,
            1f to Color.Transparent,
            startY = -spread + drop,
            endY = size.height + spread + drop,
        ),
        topLeft = Offset(-spread, -spread + drop),
        size = Size(size.width + spread * 2f, size.height + spread * 2f),
        cornerRadius = CornerRadius(22.dp.toPx() + spread),
    )
}

/** 一步的表面：蒙层 + 气泡/居中大卡。气泡按目标位置避让，落点规则见 [placeNear]。 */
@Composable
private fun TutorialStage(
    step: TutorialStep,
    isLast: Boolean,
    needsTap: Boolean,
    target: () -> Rect?,
    from: () -> Rect?,
    targetInWindow: () -> Rect?,
    bubbleIn: Animatable<Float, AnimationVector1D>,
    shake: Animatable<Float, AnimationVector1D>,
    holeTravel: Animatable<Float, AnimationVector1D>,
    holeScale: Animatable<Float, AnimationVector1D>,
    ringIn: Animatable<Float, AnimationVector1D>,
    sweep: Animatable<Float, AnimationVector1D>,
    success: Animatable<Float, AnimationVector1D>,
    onTap: (Offset) -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    val density = LocalDensity.current
    val marginPx = with(density) { 16.dp.roundToPx() }
    val gapPx = with(density) { 14.dp.toPx() }
    val topInsetPx = WindowInsets.statusBars.getTop(density)
    val bottomInsetPx = max(
        WindowInsets.navigationBars.getBottom(density),
        WindowInsets.ime.getBottom(density),
    )
    val anchored = step.target is TutorialTarget.Anchor
    val contentMotion = tutorialContentMotion(step.id, targetInWindow, bubbleIn, shake)

    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            TutorialScrim(
                target = target,
                from = from,
                holeTravel = holeTravel,
                holeScale = holeScale,
                ringIn = ringIn,
                sweep = sweep,
                success = success,
                onTap = onTap,
            )
            if (anchored) {
                TutorialBubble(step, isLast, needsTap, contentMotion, onNext, onExit)
            } else {
                TutorialCard(step, isLast, contentMotion, onNext, onExit)
            }
        },
    ) { measurables, constraints ->
        val stageW = constraints.maxWidth
        val stageH = constraints.maxHeight
        val scrim = measurables[0].measure(constraints)
        val contentMaxW = with(density) { (if (anchored) 336.dp else 384.dp).roundToPx() }
            .coerceAtMost((stageW - marginPx * 2).coerceAtLeast(1))
        val content = measurables[1].measure(
            Constraints(
                minWidth = 0,
                maxWidth = contentMaxW,
                minHeight = 0,
                maxHeight = (stageH - topInsetPx - bottomInsetPx - marginPx * 2).coerceAtLeast(1),
            ),
        )
        val cw = content.width
        val ch = content.height
        val safeTop = topInsetPx + marginPx
        val safeBottom = stageH - bottomInsetPx - marginPx
        val rightLimit = (stageW - cw - marginPx).coerceAtLeast(marginPx)

        // 只读、不写：目标每帧可能变，读在布局阶段，没变化就不重排。
        val holeNow = target()
        val pos: IntOffset = if (anchored && holeNow != null) {
            placeNear(
                hole = holeNow,
                bw = cw,
                bh = ch,
                stageW = stageW,
                stageH = stageH,
                safeTop = safeTop,
                safeBottom = safeBottom,
                margin = marginPx,
                gap = gapPx,
            )
        } else {
            val x = ((stageW - cw) / 2).coerceIn(marginPx, rightLimit)
            val y = ((stageH - ch) / 2).coerceIn(safeTop, (safeBottom - ch).coerceAtLeast(safeTop))
            IntOffset(x, y)
        }

        layout(stageW, stageH) {
            scrim.place(0, 0)
            content.place(pos.x, pos.y)
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

/**
 * 蒙层：吃掉所有触摸（洞外不误触下层），洞内命中交给 [onTap]。
 *
 * 触摸没法「原样转发」给下层控件（命中测试只会落到最上面的引导层），所以洞内命中的语义
 * 是「引擎按坐标判定点中了洞 → 调页面登记的真实动作」，见 [handleTap]。
 */
@Composable
private fun TutorialScrim(
    target: () -> Rect?,
    from: () -> Rect?,
    holeTravel: Animatable<Float, AnimationVector1D>,
    holeScale: Animatable<Float, AnimationVector1D>,
    ringIn: Animatable<Float, AnimationVector1D>,
    sweep: Animatable<Float, AnimationVector1D>,
    success: Animatable<Float, AnimationVector1D>,
    onTap: (Offset) -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val accent = ui.accent
    val isDark = p.isDark
    val dim = if (isDark) .62f else .55f
    val density = LocalDensity.current
    val padPx = with(density) { 6.dp.toPx() }
    val cornerPx = with(density) { 18.dp.toPx() }
    val latestTap by rememberUpdatedState(onTap)

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val hole = scaledHole(
                    target = target(),
                    from = from(),
                    travel = holeTravel.value,
                    scale = holeScale.value,
                    pad = padPx,
                )
                drawScrim(
                    hole = hole,
                    dim = dim,
                    cornerPx = cornerPx,
                    accent = accent,
                    ringAlpha = ringIn.value,
                    sweepT = sweep.value,
                    successT = success.value,
                    isDark = isDark,
                )
            }
            .pointerInput(Unit) {
                // 吃下所有触摸（含拖动），只有「抬手且没拖动」才算一次点击。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var moved = false
                    var multi = false
                    var up = false
                    while (!up) {
                        val event = awaitPointerEvent()
                        var pressed = 0
                        event.changes.forEach { change ->
                            if (change.pressed) pressed++
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                            }
                            change.consume()
                        }
                        if (pressed > 1) multi = true
                        up = pressed == 0
                    }
                    if (!moved && !multi) latestTap(down.position)
                }
            },
    )
}

/** 指向控件的运行中气泡：标题 / 说明 / 提示或「下一步」，右上角退出。 */
@Composable
private fun TutorialBubble(
    step: TutorialStep,
    isLast: Boolean,
    needsTap: Boolean,
    modifier: Modifier,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val shape = SmoothCorner(22.dp)
    Column(
        modifier
            .drawBehind { drawBubbleHalo(p.isDark) }
            .clip(shape)
            .background(p.paper)
            .border(1.dp, p.line, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            step.glyph?.let { glyph ->
                LineIcon(glyph, ui.accent, 14.dp, 1.5f)
                Spacer(Modifier.width(6.dp))
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
        Txt(step.title, size = 15f, weight = FontWeight.SemiBold, color = p.ink, lineHeight = 21f)
        Spacer(Modifier.height(5.dp))
        Txt(step.body, size = 13f, color = p.muted, lineHeight = 20f)
        if (needsTap) {
            // 「必须真点」的步不给下一步：只给一句指向洞的提示，点中目标才算过。
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TapDot(accent = ui.accent)
                Spacer(Modifier.width(7.dp))
                Txt(
                    "点一下这里",
                    size = 12.5f,
                    color = ui.accent,
                    weight = FontWeight.Medium,
                    letterSpacing = .2f,
                )
            }
        } else {
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                if (isLast) "完成" else "下一步",
                onNext,
                Modifier.fillMaxWidth(),
                glyph = if (isLast) Glyph.Check else Glyph.Chevron,
            )
        }
    }
}

/** 居中大卡：保留原来引导页「大图标 + 标题 + 说明」的视觉语言。 */
@Composable
private fun TutorialCard(
    step: TutorialStep,
    isLast: Boolean,
    modifier: Modifier,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val shape = SmoothCorner(28.dp)
    Column(
        modifier
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

/** 气泡/卡片共用的入场形变：从洞的最近边缘 scaleIn(.94→1) + fadeIn；点错时三段抖动。 */
@Composable
private fun tutorialContentMotion(
    stepId: String,
    targetInWindow: () -> Rect?,
    bubbleIn: Animatable<Float, AnimationVector1D>,
    shake: Animatable<Float, AnimationVector1D>,
): Modifier {
    var origin by remember(stepId) { mutableStateOf(TransformOrigin.Center) }
    return Modifier
        .onGloballyPositioned { coords ->
            val hole = targetInWindow() ?: return@onGloballyPositioned
            val raw = coords.positionInWindow()
            val size = coords.size
            val box = Rect(raw.x, raw.y, raw.x + size.width, raw.y + size.height)
            origin = growOrigin(box, hole)
        }
        .graphicsLayer {
            val v = bubbleIn.value
            alpha = v
            val scale = .94f + .06f * v
            scaleX = scale
            scaleY = scale
            transformOrigin = origin
            translationX = shake.value * TutorialTiming.WRONG_SHAKE_DP.dp.toPx()
        }
}

/** 指向洞的小圆点（内实外淡），配合「点一下这里」。 */
@Composable
private fun TapDot(accent: Color) {
    Canvas(Modifier.size(8.dp)) {
        drawCircle(accent.copy(alpha = .18f), radius = 4.dp.toPx())
        drawCircle(accent, radius = 2.2.dp.toPx())
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
