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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "MOYU_TUTORIAL"

/** 组 id。页内教学组以后在这里补常量，不要散落字符串。 */
object TutorialGroups {
    /**
     * 首次启动的引导组。
     *
     * 3.26.53 起它的内容是自动播放的 PPT（全屏纸底、一页一个功能、自动翻页），
     * 实现见 Onboarding.kt 的 [PptOnboarding]，不再走挖洞教练模式。
     * 组 id 改成 `tutorial.ppt`（用户否掉了教练式，旧的 `tutorial.landing` 不再写回）；
     * 写回 `AppSettings.tutorialDone` 与 `onboardingDone=true` 的语义不变（App.kt 的 finish 回调照旧）。
     *
     * **不要**为了「页内分步教学」复用这个 id（复用会被 [TutorialOverlay] 路由到 PPT）；
     * 页内教学组在这里新增自己的常量，例如「第一次进正文」之类，走教练层 [CoachOverlay]。
     */
    const val LANDING = "tutorial.ppt"
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
 * [Anchor] = 指向页面用 [tutorialAnchor] 注册的控件。
 *
 * 3.26.56 起「找不到」分两种情况（见 [AnchorWait]）：
 * - **暂时没有**（跨页的真点步骤：上一步刚点完，新页面还在挂控件）→ 进入 waiting，
 *   给 [AnchorWait.GRACE_MS] 宽限，目标一出现、位置一停稳就把洞接上去；
 * - **宽限用完仍然没有** → 才按旧规则跳过这一步。
 * 组起手时例外：起组那一帧不会为了「可能等会儿才出现」的目标压屏 2.5 秒，
 * 起组仍然只找当前能显示的步（见 [findPlayable]）。
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
 *
 * 3.26.56：OnTargetTap 的 [target] 可以落在**下一个页面**（例如上一步点完打开的编辑器里）——
 * 锚点暂缺时引擎先进 waiting 等宽限（[AnchorWait]），不再一缺锚点就把这一步跳成「下一步」。
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

    /**
     * 锚点此刻是否可用：已注册、且量到了起码 1px 的尺寸。
     *
     * 只看「现在有没有」，**不判断这一步该不该等**：带 target 的步要等多久由
     * [CoachOverlay] 的宽限窗口决定（见 [AnchorWait]）；这里供起组挑步与轮询共用。
     */
    fun isUsable(id: String): Boolean {
        val rect = anchorRect(id) ?: return false
        return rect.width > 1f && rect.height > 1f
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
 * 3.26.56：锚点等待（B16「跨页真点」的引擎侧）。
 *
 * 背景：引导组里「必须真点」的步，目标常常在另一个页面（书里、编辑器里）。用户点完上一洞、
 * 页面过场还没把目标控件挂上时，旧引擎只等 8 帧就判「锚点缺失」跳步 —— 于是只有第 1 步是真点，
 * 后面的步全退化成「下一步」大卡。现在带 target 的步锚点暂时不存在时进入 waiting，给一段宽限：
 * 目标出现、并且在屏幕上停稳之后才落洞，**不打断页面自己的过场动画**；宽限用完仍没有才按旧规则跳过。
 *
 * 注意：这是「等」不是「猜」。宽限期满就照旧跳过，所以没接线的步骤不会把整组卡死；
 * 连续多步都等不到时还有 [MAX_WAIT_STREAK] 安全阀，避免整组没接线时压屏 N × 2.5s。
 */
private object AnchorWait {
    /** 宽限：锚点暂缺最多等这么久；超时才按旧规则跳过这一步。 */
    const val GRACE_MS = 2500L

    /** 锚点首次出现后，位置连续这么多帧不变才算「布局稳定」（过场/滚动停稳），可以落洞。 */
    const val STABLE_FRAMES = 2

    /**
     * 等稳定的上限：目标出现了但一直在动（长动画/页面滚动）也不能一直停在 waiting。
     * 超过这么久就按当前量到的位置落洞，**不再等**。
     */
    const val STABLE_TIMEOUT_MS = 900L

    /** 稳定判据（像素）：过场弹簧尾部的亚像素抖动不算「还在动」。 */
    const val STABLE_EPS_PX = 1.5f

    /** 已经亮过洞的目标忽然取不到：连续这么多帧才算真的没了（≈130ms，防重组/懒加载误跳）。 */
    const val LOST_FRAMES = 8

    /** 宽限内连续这么多个步都等不到锚点 → 后面的缺失立刻跳过（安全阀）。 */
    const val MAX_WAIT_STREAK = 2
}

/**
 * 引导层唯一入口：按组 id 分发，两套实现互不干扰（3.26.56 把这条边界写明）。
 *
 * - [TutorialGroups.LANDING]（首次引导）= 3.26.53 起用户点名要的 **PPT 自动演示**（[PptOnboarding]）；
 * - 其余任何组 = **页内分步教学**走教练层 [CoachOverlay]（挖洞 + 真点才能过 + 等锚点宽限）。
 *
 * 引擎对外的能力只有一件事：`TutorialController.start(一组 TutorialGroup)`。landing 用不用教练层
 * 由 [TutorialGroups.LANDING] 这个 id 决定，PPT 与教练两套共存；以后页面内首次教学（首次进正文教
 * 球/能力栏/符号行/选段这类）起自己的组 id 就走 [CoachOverlay]，同一个 id 不会同时命中两边。
 */
@Composable
fun TutorialOverlay(
    group: TutorialGroup,
    registry: TutorialRegistry,
    onFinished: () -> Unit,
) {
    if (group.id == TutorialGroups.LANDING) {
        PptOnboarding(onFinished = onFinished)
        return
    }
    CoachOverlay(group = group, registry = registry, onFinished = onFinished)
}

/**
 * 教练式引导层：半透明蒙层 + 目标控件挖洞高亮 + 自动避让的气泡/居中大卡。
 *
 * 3.26.52 起的规则：
 * - **真点才能过**：`advanceOn = OnTargetTap` 的步必须点到洞里；点错（洞外）只抖一下 + 触感，
 *   不换步。洞外全部触摸被吃掉，不会误触下面的页面（气泡里的「退出教学」永远可点；
 *   waiting 期间气泡还没铺，用系统返回键退出，见 [AnchorWait]）。
 * - **触摸穿透**：洞内命中后先执行页面登记的真实动作，再播完成反馈、进下一步；
 *   没有登记动作时只算命中，步照过（老页面不改也能用）。
 * - 目标控件不存在 → 宽限内先等（3.26.56，见 [AnchorWait]），超时才跳过该步并记日志；
 *   已经亮过洞又取不到时连续 8 帧才算真的没了（避免重组时闪没）。
 * - 目标 rect 每帧重读（不写入构图），布局/绘制跟着走；页面滚动不会重组整个引导层。
 * - 动效：洞从目标中心 `scaleIn(.86→1)`、环一圈 300ms 高光扫过、切步时旧洞 120ms 收起、
 *   新洞从旧位置 spring 滑到新目标（位置插值，不跳）、气泡从洞的最近边缘 `scaleIn(.94→1)`。
 *   `motion = none` 全部直接切，但**等待逻辑不受影响**（那是时间/布局判据，不是动画）。
 */
@Composable
private fun CoachOverlay(
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
    // 3.26.56：当前步的洞/气泡能不能亮。带 target 的步要等锚点出现并停稳（见 [AnchorWait]），
    // false = waiting 状态：蒙层照常压屏、页面过场照常播，但洞与气泡先不铺，也不接受任何点击
    // （否则一个「看不见的下一步按钮」会被点到，把正事跳过去）。
    var holeArmed by remember(group.id) { mutableStateOf(false) }
    // 宽限内连续几步都没等到锚点：超过安全阀后，后续缺失立刻按旧规则跳过。
    var waitStreak by remember(group.id) { mutableIntStateOf(0) }
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
        // 3.26.56：不再在切步时「跳过缺锚点的步」（那是旧的 findPlayable 行为）。
        // 下一步即使是跨页目标，也先进 waiting 等它的锚点（[AnchorWait]），超时才跳；
        // 没有下一步才收尾。这样「真点」的步能跨页面接力，而不是被静默跳过。
        val next = stepIndex + 1
        if (next >= group.steps.size) {
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
            // 带 target 的下一步先把内容收住：等锚点出现并停稳（由轮询 effect 置 true）才播入场；
            // 没有 target 的步立刻亮。motion=none 只影响动画时长，不影响这套等待逻辑。
            holeArmed = nextStep.target !is TutorialTarget.Anchor
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
        // waiting 期间（洞/气泡还没亮）一个点击都不接：既不抖也不触感，页面正在过场，
        // 用户的点击是噪声；更不能让「看不见的下一步」把这一步跳过去。
        if (!holeArmed) return
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
    // 3.26.56：起组这一下**不进 waiting**（不能让首屏压暗等 2.5 秒），仍然只挑当前能显示的步；
    // 宽限只服务「组已经在跑、切到下一步」的跨页接力。
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
        // 带 target 的首步也交给轮询保持「先停稳再落洞」的同一套节奏；没有 target 的立刻亮。
        holeArmed = group.steps[index].target !is TutorialTarget.Anchor
        waitStreak = 0
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
    // 3.26.56：带 target 的步要等锚点出现并停稳（holeArmed）才开始播，否则洞/气泡会在目标
    // 出现之前就播完，等真正出现时是「啪」地硬落上去。motion=none 仍然全部 snapTo。
    LaunchedEffect(stepIndex, holeArmed) {
        if (stepIndex < 0 || !holeArmed) return@LaunchedEffect
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
    //
    // 3.26.56：带 target 的步先在 waiting 里等锚点（[AnchorWait]）：
    // - 锚点暂时没有 → 不判「缺失」，蒙层压屏（不挖洞）等宽限，页面自己的过场动画照常播；
    // - 锚点出现 → 先等它连续几帧不动（过场弹簧/滚动停稳）再落洞，然后才播挖洞入场；
    //   位置一直在动也最多等 [AnchorWait.STABLE_TIMEOUT_MS]，不会永远不亮；
    // - 宽限用完 / 已经亮过洞又丢了 → 才按旧规则跳过这一步。
    LaunchedEffect(stepIndex) {
        val step = group.steps.getOrNull(stepIndex) ?: return@LaunchedEffect
        val anchor = step.target as? TutorialTarget.Anchor ?: run {
            targetRect = null
            return@LaunchedEffect
        }
        var appeared = false
        var missingFrames = 0
        var lastSeen: Rect? = null
        var stableFrames = 0
        // 已经量到锚点、但位置还在动时累计的时间：有上限（[AnchorWait.STABLE_TIMEOUT_MS]）。
        var stableWaitMs = 0L
        var lastFrame = 0L
        var waitedMs = 0L
        // 切进这一步时锚点就不在（典型的跨页：上一步点完才打开下一页）= 这一步是「等来的」。
        // 等来的目标不从上一页的洞滑过来（那个位置在上一页，没有意义），直接在新目标上展开。
        var waitedForAnchor = !registry.isUsable(anchor.id)
        // 安全阀已经生效（连续几步都没等到）时不再压屏等，日志也别写成「等了 2.5s」。
        if (waitedForAnchor && waitStreak < AnchorWait.MAX_WAIT_STREAK) logWait(step, anchor.id)
        while (true) {
            val frame = withFrameNanos { it }
            val dtMs = if (lastFrame == 0L) 0L else (frame - lastFrame) / 1_000_000L
            lastFrame = frame
            val raw = registry.anchorRect(anchor.id)
            val local = if (raw != null && raw.width > 1f && raw.height > 1f) {
                raw.translate(-overlayOrigin.x, -overlayOrigin.y)
            } else {
                null
            }
            if (local == null) {
                // 又变回「暂时没有」：稳定计时作废。
                stableWaitMs = 0L
                // 宽限期内停在 waiting，不判缺失、不打断页面过场。
                if (!appeared && waitStreak < AnchorWait.MAX_WAIT_STREAK) {
                    waitedMs += dtMs
                    if (waitedMs <= AnchorWait.GRACE_MS) {
                        if (!waitedForAnchor) {
                            waitedForAnchor = true
                            logWait(step, anchor.id)
                        }
                        missingFrames = 0
                        continue
                    }
                }
                // 宽限用完（或连续多步都等不到）：先保留上一个位置别让洞闪没，
                // 连续 [AnchorWait.LOST_FRAMES] 帧都没有才算真的没了，跳过这一步继续。
                missingFrames++
                if (missingFrames > AnchorWait.LOST_FRAMES) {
                    targetRect = null
                    if (!transitioning && !closing && !tapBusy) {
                        if (!appeared) waitStreak++
                        logSkip(
                            step,
                            if (appeared) {
                                "anchor '${anchor.id}' lost while showing"
                            } else {
                                "anchor '${anchor.id}' still missing, skipped (waited ${waitedMs}ms)"
                            },
                        )
                        advance()
                    }
                    return@LaunchedEffect
                }
            } else {
                if (!appeared) {
                    // 先等位置停稳（过场弹簧 / 滚动里先不落洞）再开始挖洞入场；
                    // 但「一直在动」也要有上限，否则这一步会永远停在 waiting、永远不亮。
                    stableWaitMs += dtMs
                    val prev = lastSeen
                    stableFrames = if (prev != null && nearSame(prev, local)) stableFrames + 1 else 1
                    lastSeen = local
                    if (stableFrames < AnchorWait.STABLE_FRAMES &&
                        stableWaitMs <= AnchorWait.STABLE_TIMEOUT_MS
                    ) {
                        continue
                    }
                    appeared = true
                    waitStreak = 0
                    if (waitedForAnchor) fromRect = null
                    holeArmed = true
                }
                targetRect = local
                missingFrames = 0
            }
        }
    }

    BackHandler { requestClose() }

    val step = group.steps.getOrNull(stepIndex) ?: return

    // 3.26.56：末步按**步表**算，不再按「下一个能显示的步」算——下一步的锚点可能只是在等
    // 页面过场（waiting 期间仍会亮），把按钮提前写成「完成」会在锚点出现后对不上。
    val isLast = stepIndex >= group.steps.lastIndex
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
                armed = holeArmed,
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

/**
 * 从 [from] 起找第一个**当前就能显示**的步；目标不在的步记日志后跳过，全部不可显示返回 -1。
 *
 * 3.26.56 起只给「起组」用（见 [CoachOverlay] 的起组 effect）：起组不能为了等一个还没挂上的
 * 跨页目标先把首屏压暗。组跑起来之后的切步**不再来这里跳步**——带 target 的下一步先进
 * waiting 宽限（[AnchorWait]），超时才跳。
 */
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
        if (target is TutorialTarget.Anchor && !registry.isUsable(target.id)) {
            if (!quiet) logSkip(step, "anchor '${target.id}' missing")
            continue
        }
        return i
    }
    return -1
}

private fun logSkip(step: TutorialStep, reason: String) {
    Log.d(TAG, "skip step ${step.id} (${step.group}): $reason")
}

/** 进入 waiting：锚点暂时没有，等宽限窗口（[AnchorWait.GRACE_MS]）。 */
private fun logWait(step: TutorialStep, anchorId: String) {
    Log.d(
        TAG,
        "wait step ${step.id} (${step.group}): anchor '$anchorId' not ready, " +
            "grace ${AnchorWait.GRACE_MS}ms",
    )
}

/** 两次量到的锚点是不是「同一处、没在动」（亚像素抖动容忍，见 [AnchorWait.STABLE_EPS_PX]）。 */
private fun nearSame(a: Rect, b: Rect): Boolean =
    abs(a.left - b.left) <= AnchorWait.STABLE_EPS_PX &&
        abs(a.top - b.top) <= AnchorWait.STABLE_EPS_PX &&
        abs(a.width - b.width) <= AnchorWait.STABLE_EPS_PX &&
        abs(a.height - b.height) <= AnchorWait.STABLE_EPS_PX

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

/**
 * 一步的表面：蒙层 + 气泡/居中大卡。气泡按目标位置避让，落点规则见 [placeNear]。
 *
 * [armed] = 这一步的内容可以亮了吗。3.26.56 的 waiting 期间它是 false：蒙层照常压屏（页面过场
 * 看得见），但**洞不挖、气泡/大卡一个 composable 都不铺**——`graphicsLayer(alpha=0)` 仍然能接点击，
 * 铺了就会出现「看不见的下一步按钮被点到、这一步被跳过」。
 */
@Composable
private fun TutorialStage(
    step: TutorialStep,
    isLast: Boolean,
    needsTap: Boolean,
    armed: Boolean,
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
                armed = armed,
                holeTravel = holeTravel,
                holeScale = holeScale,
                ringIn = ringIn,
                sweep = sweep,
                success = success,
                onTap = onTap,
            )
            when {
                // waiting：目标还没出现 —— 只压屏，不铺气泡（占位项保证 measurables 数量不变）。
                anchored && !armed -> Box(Modifier)
                anchored -> TutorialBubble(step, isLast, needsTap, contentMotion, onNext, onExit)
                else -> TutorialCard(step, isLast, contentMotion, onNext, onExit)
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
 * waiting（[armed] = false）期间整屏压暗、不挖洞：页面自己的过场看得见，也不会在旧位置留
 * 一个上一页的洞；点击照旧被吃掉（[onTap] 会忽略）。
 *
 * 触摸没法「原样转发」给下层控件（命中测试只会落到最上面的引导层），所以洞内命中的语义
 * 是「引擎按坐标判定点中了洞 → 调页面登记的真实动作」，见 [handleTap]。
 */
@Composable
private fun TutorialScrim(
    target: () -> Rect?,
    from: () -> Rect?,
    armed: Boolean,
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
                // waiting（armed=false）不挖洞：整屏压暗，页面自己的过场看得见，
                // 也不会在旧位置留一个上一页的洞。
                val hole = if (armed) {
                    scaledHole(
                        target = target(),
                        from = from(),
                        travel = holeTravel.value,
                        scale = holeScale.value,
                        pad = padPx,
                    )
                } else {
                    null
                }
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
