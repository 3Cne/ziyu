package com.moyu.writer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ===== 3.26.53 新手引导：自动播放的 PPT 式全屏演示 =====
//
// 用户原话：「新手引导还是做成高级质感高级动效的 ppt 形式吧，尽量把所有功能都讲到，
// 一个一个来，滑动自动展示的 ppt。」
//
// 形态：全屏不透明纸底（沿用 3.26.25 那版全屏引导的纸底 + 静止背景），一页一个功能：
// 大标题 + 一句说明 + 一张用 Compose 画的示意图；不再用挖洞高亮真实控件的教练模式。
//
// 自动播放：每页 [PPT_DISPLAY_MS]（默认 3800ms），页底细线进度条 tween(3800, LinearEasing)
// 与自动翻页同一个源；手动滑动会暂停自动播放，松手后 [PPT_RESUME_MS]（6s）恢复。
//
// 动效：
// - 换页：两页并排、整幅横移、不淡（spring(MOYU_DAMPING, 420f)），背景静止
//   —— 3.26.25 的亮度闪烁教训：绝不能用 fade 叠层换页；
// - 页内分层入场：标题 → 说明（+60ms）→ 示意图（+120ms），各自 slideInVertically(12dp) + fadeIn；
// - 示意图元素逐件出现：每 [PPT_ART_STEP_MS]（220ms）一件，scaleIn(.94→1) + fade；
// - `motion = none`：全部 tween(0)、不自动播放，只留手动滑。

// ===== 3.26.54：设置里「重看功能引导」当场播放 + PPT 进出场淡入淡出 =====
//
// 3.26.53 留了两个缺口，这一版在 Onboarding.kt / App.kt 内部补掉：
// ① 设置 → 关于 →「重看功能引导」只清 tutorialDone / onboardingDone，而「要不要起引导」的
//    判断在 App.kt 的根组合期只跑一次 → 点了当场没反应，要清后台重进。现在 Settings 侧
//    再 bump 一下 [TutorialSignal]，App.kt 盯版本号，一变就立刻起同一组 PPT。
// ② 引导进出是一刀切。进场整层淡入 160ms；跳过 / 末页先淡出 200ms 再交回上层；
//    `motion = none` 仍然直接切。

// ===== 3.26.57：手动翻页判定收紧（用户报「要滑很长距离才翻页，短滑完全不动」） =====
//
// 3.26.53–3.26.56 的判定只有一条：`pos.roundToInt()` —— 必须拖过**半页（50% 页宽）**才翻，
// 且完全没看速度，所以快速轻扫也会被弹回去。现在两条任意一条成立就翻：
// ① 位移 > 页宽 [PPT_FLIP_FRACTION]（18%，≈ 屏宽 1/5.5）；
// ② 松手速度 > [PPT_FLING_DP_PER_SEC]（400 dp/s）——快速轻扫必须能给过去。
// 拖动期间页本来就逐帧跟手（`pos` 只在绘制阶段读），这块不动；自动播放 3.8s、
// 手动后暂停 6s、跳过、首末页不越界、`motion = none` 全部照旧。

/**
 * 「立即重播上手引导」信号（3.26.54）。
 *
 * 形态照 [ChatClearSignal]：只有一个自增版本号，不携带任何内容。生产者是设置页，
 * 消费者是根组合（App.kt 的 `snapshotFlow { TutorialSignal.version }`）。
 *
 * **Settings.kt 要加的一行**（「重看功能引导」的 onClick 里，清完两个标记之后）：
 * ```
 * store.updateSettings { it.copy(tutorialDone = emptyList(), onboardingDone = false) }
 * TutorialSignal.bump()
 * ```
 * 清标记仍然要留着：它保证「重看之后又中途杀掉 App」的下一次冷启动还会重播；
 * 冷启动那条路径（App.kt 的根组合期检查）不经过这个信号，两者互不替代。
 *
 * 版本号从 0 起。消费者以自己的收集起点为基线，所以已处理过的 bump 不会在
 * Activity 重建（例如改系统字号）后把引导再播一遍。
 */
internal object TutorialSignal {
    var version by mutableIntStateOf(0)

    fun bump() {
        version++
    }
}

/** 一页自动展示时长（含示意图逐件入场后的阅读停顿）。改这里即可整体调快慢。 */
private const val PPT_DISPLAY_MS = 3800L

/** 手动滑动后暂停多久恢复自动播放。 */
private const val PPT_RESUME_MS = 6000L

/** 示意图元素逐件出现的间隔。 */
private const val PPT_ART_STEP_MS = 220L

/** 页内分层入场的延迟：说明 +60ms、示意图 +120ms（标题 0）。 */
private const val PPT_BODY_DELAY_MS = 60L
private const val PPT_ART_DELAY_MS = 120L

/** 标题从下往上进的距离。 */
private const val PPT_RISE_DP = 12

/** 3.26.54 进出场：整层淡入 160ms、淡出 200ms（`motion = none` 不走这两段，直接切）。 */
private const val PPT_ENTER_MS = 160
private const val PPT_EXIT_MS = 200

/** 3.26.57 翻页判定①：位移超过页宽的这个比例就翻（18% ≈ 屏宽 1/5.5）。 */
private const val PPT_FLIP_FRACTION = .18f

/** 3.26.57 翻页判定②：松手速度超过这个值（dp/s）就翻，快速轻扫必须能给过去。 */
private const val PPT_FLING_DP_PER_SEC = 400f

/** 标题 / 说明固定占位高度：分层入场时不改变页面布局（否则整页会跳）。 */
private val PptTitleHeight = 40.dp
private val PptBodyHeight = 48.dp

/**
 * 组 id（[TutorialGroups.LANDING]）的组壳：内容在 [PPT_PAGES]，教练层不渲染它。
 *
 * 3.26.56 记录：教练引擎 [CoachOverlay] 补上了「等锚点宽限」（跨页真点可以接力，见
 * `Tutorial.kt` 的 `AnchorWait`），但**首次引导仍然是 PPT**——3.26.53 用户明确要的是
 * 「自动播放的 PPT 形式」，不能为了真点把 landing 改回教练组。等锚点这套留给**页内分步教学**
 * （第一次进正文/编辑器时教某个控件），那类组用新的组 id 走教练层，与本文件无关。
 */
val LANDING_TUTORIAL: TutorialGroup = TutorialGroup(
    id = TutorialGroups.LANDING,
    steps = listOf(
        TutorialStep(
            id = "landing.ppt",
            group = TutorialGroups.LANDING,
            section = "上手",
            title = "功能演示",
            body = "自动播放的 PPT 式引导。真正的页在 Onboarding.kt 的 PPT_PAGES 里，由 PptOnboarding 渲染。",
            glyph = Glyph.Sparkles,
        ),
    ),
)

/** 示意图种类。真正画法在 [PptArtView]，这里只留类型，避免把 Composable lambda 放进数据。 */
private enum class PptArt {
    Welcome, NewBook, NewChapter, Writing, Select, Ability, Symbols, Type,
    Todo, Notes, Export, Ask, AiTools, Story, Backup, Look, Stats, Done,
}

/** 一页：大标题 + 一句说明 + 一张示意图。 */
private data class PptPage(
    val title: String,
    val body: String,
    val art: PptArt,
)

private val PPT_PAGES: List<PptPage> = listOf(
    PptPage("欢迎来到素纸", "一个只放纸和字的写作空间，写下的内容都留在这台手机里。", PptArt.Welcome),
    PptPage("先建一本书", "书架右上角点 +，填个书名就能开始，其它可以留空。", PptArt.NewBook),
    PptPage("再开一章", "书的目录右上角点 +，新的一章会排在最后。", PptArt.NewChapter),
    PptPage("点一下就能写", "点进正文直接打字，写完自动保存，没有保存按钮。", PptArt.Writing),
    PptPage("选中一段字", "长按选词、拖两端手柄改范围，工具条只对这一段生效。", PptArt.Select),
    PptPage("能力栏", "键盘上方的一排：撤销、重做、缩进、引号、分节。", PptArt.Ability),
    PptPage("符号行", "逗号、句号、引号、省略号都在符号行，点一下写进正文。", PptArt.Symbols),
    PptPage("排版与纸面", "字体、字号、行距和纸面样式，都能按自己读着舒服来调。", PptArt.Type),
    PptPage("待办与提醒", "笔记里以方框开头的行会变成待办，还能给它设到点提醒。", PptArt.Todo),
    PptPage("笔记夹与章节", "笔记放进笔记夹，也能关联到某一章，写作时就在正文旁边。", PptArt.Notes),
    PptPage("文字带得走", "书能导出成 EPUB 或 TXT，文件会出现在下载目录里。", PptArt.Export),
    PptPage("问这本书", "直接提问，它会读正文和笔记，也能替你改、建章、设提醒。", PptArt.Ask),
    PptPage("校核、修饰、问", "选中一段再点 AI：校核挑错、修饰润色，问就聊这一段。", PptArt.AiTools),
    PptPage("故事圣经", "角色、关系、时间线和大纲收在一处，AI 整理的会单独标出来。", PptArt.Story),
    PptPage("备份与恢复", "全部内容能导出成一份备份，换手机时再恢复回来。", PptArt.Backup),
    PptPage("外观由你定", "主题、点缀色、字体和玻璃材质都在外观里，改完立刻生效。", PptArt.Look),
    PptPage("写了多少，一眼看到", "数据页按天统计字数，也能看到连续写作的天数。", PptArt.Stats),
    PptPage("可以开始写了", "剩下的边写边发现；想再看一遍，设置 → 关于里可以重看引导。", PptArt.Done),
)

/**
 * 首次启动的功能演示（PPT 式）。
 *
 * 由 [TutorialOverlay] 在组 id 是 [TutorialGroups.LANDING] 时调用；结束/跳过都走 [onFinished]，
 * 由上层把 `tutorial.ppt` 写进 `AppSettings.tutorialDone` 并把 `onboardingDone` 置 true（语义不变）。
 */
@Composable
fun PptOnboarding(onFinished: () -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    val motion = ui.settings.motion != "none"
    val pages = PPT_PAGES
    val lastIndex = pages.lastIndex
    val scope = rememberCoroutineScope()

    // 3.26.54 进出场：整层只有这一个不透明度在动。纸底和所有内容在同一个 graphicsLayer 里
    // 一起淡，不会出现「两份内容叠着淡」——3.26.25 的亮度闪烁就是两层内容叠出来的。
    // 进场 0 → 1（160ms）；跳过 / 末页 / 首页返回先把 1 → 0（200ms）播完，才把 onFinished
    // 交回上层（写 tutorialDone + 摘掉引导层），所以退场看得见。motion = none：恒为 1，直接切。
    val layerAlpha = remember { Animatable(if (motion) 0f else 1f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (motion && layerAlpha.value < 1f) layerAlpha.animateTo(1f, tween(PPT_ENTER_MS))
    }

    /** 跳过 / 末页「开始写」/ 首页返回：先淡出再交回上层；重复触发只算一次。 */
    fun dismiss() {
        if (closing) return
        closing = true
        if (!motion) {
            onFinished()
            return
        }
        scope.launch {
            layerAlpha.animateTo(0f, tween(PPT_EXIT_MS))
            onFinished()
        }
    }

    // 视觉页位置（0..lastIndex，拖动时允许一点橡皮筋）。只在绘制阶段读，换页不重组页面内容。
    val pos = remember { mutableFloatStateOf(0f) }
    // 顶栏页码：松手即更新（读起来跟手）。
    var pageIndex by remember { mutableIntStateOf(0) }
    // 落位页：滑到位之后才更新；进度条与自动播放都从「落位」开始算，不会在滑动途中就倒计时。
    var settledPage by remember { mutableIntStateOf(0) }
    // 自动播放暂停（手动滑动 / motion=none）。
    var autoPaused by remember { mutableStateOf(false) }
    var resumeJob by remember { mutableStateOf<Job?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val progress = remember { Animatable(0f) }

    /** 翻到某一页：整幅横移，不淡（两页并排）。退场淡出开始后不再接受翻页。 */
    fun settleTo(target: Int) {
        if (closing) return
        val t = target.coerceIn(0, lastIndex)
        if (pageIndex != t) pageIndex = t
        settleJob?.cancel()
        settleJob = scope.launch {
            val from = pos.floatValue
            if (motion && abs(from - t) > .001f) {
                animate(
                    initialValue = from,
                    targetValue = t.toFloat(),
                    animationSpec = spring(MOYU_DAMPING, 420f),
                ) { value, _ -> pos.floatValue = value }
            } else {
                pos.floatValue = t.toFloat()
            }
            if (pageIndex == t) settledPage = t
        }
    }

    /** 手动滑动结束后，等 6 秒再恢复自动播放。 */
    fun scheduleResume() {
        resumeJob?.cancel()
        if (!motion) return
        resumeJob = scope.launch {
            delay(PPT_RESUME_MS)
            autoPaused = false
        }
    }

    // 自动播放：与页底进度条同源——同一个 tween(3800, LinearEasing) 跑完就翻下一页。
    // 退场淡出开始后不再翻页（否则 200ms 的淡出里页面还在横移）。
    LaunchedEffect(settledPage, autoPaused, motion, closing) {
        if (closing) return@LaunchedEffect
        if (!motion) {
            // motion=none：不自动播放，进度条只显示「看到第几页」。
            progress.snapTo((settledPage + 1f) / pages.size)
            return@LaunchedEffect
        }
        if (autoPaused) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        if (settledPage >= lastIndex) {
            // 最后一页不再自动翻：进度条填满后停住，等「开始写」。
            progress.animateTo(1f, tween(PPT_DISPLAY_MS.toInt(), easing = LinearEasing))
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(PPT_DISPLAY_MS.toInt(), easing = LinearEasing))
        settleTo(settledPage + 1)
    }

    // 首页按返回退出（可退出）；其余页先回上一页。退场淡出期间不再响应。
    BackHandler {
        if (closing) return@BackHandler
        if (pageIndex > 0) settleTo(pageIndex - 1) else dismiss()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // 3.26.54 进出场：纸底 + 内容都在这一层里，只动这一个 alpha（单层合成，不叠第二份内容）。
            .graphicsLayer { alpha = layerAlpha.value }
            .background(p.paper)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 兜底：落在空白处的手势也被这层吃掉，不会漏到下层书架 / TabBar。
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { change -> change.consume() }
                        }
                    }
                },
        )
        PptBackdrop()
        Column(Modifier.fillMaxSize()) {
            PptTopBar(pageIndex = pageIndex, total = pages.size, onSkip = { dismiss() })
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(lastIndex) {
                        // 3.26.57 翻页判定（原来只有「拖过半页」→ 短滑完全不动）：
                        //   ① 位移 > 页宽 18%；② 松手速度 > 400 dp/s。两条任意一条成立就翻。
                        // 速度优先，与 ViewPager / AnchoredDraggable 同序（反向轻扫读作「收回」）；
                        // 首 / 末页越界由 settleTo 的 coerceIn 挡住，拖不出范围。
                        val flingPx = PPT_FLING_DP_PER_SEC.dp.toPx()
                        val velocity = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = {
                                velocity.resetTracking()
                                settleJob?.cancel()
                                resumeJob?.cancel()
                                autoPaused = true
                            },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                // 速度只取松手前 ~100ms 的样本；页位置照旧逐帧跟手（pos 在绘制阶段读）。
                                velocity.addPosition(change.uptimeMillis, change.position)
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                pos.floatValue = (pos.floatValue - delta / width)
                                    .coerceIn(-.14f, lastIndex + .14f)
                            },
                            onDragEnd = {
                                val vx = velocity.calculateVelocity().x // px/s，向右为正
                                val moved = pos.floatValue - pageIndex // 单位是「页」
                                val target = when {
                                    vx > flingPx -> pageIndex - 1 // 向右快扫 → 上一页
                                    vx < -flingPx -> pageIndex + 1 // 向左快扫 → 下一页
                                    // 慢速松手：过 18% 页宽才翻；整页以上的连续拖动按最近页落（保持老手感）。
                                    abs(moved) >= 1f -> pos.floatValue.roundToInt()
                                    moved > PPT_FLIP_FRACTION -> pageIndex + 1
                                    moved < -PPT_FLIP_FRACTION -> pageIndex - 1
                                    else -> pageIndex
                                }
                                settleTo(target)
                                scheduleResume()
                            },
                            onDragCancel = {
                                settleTo(pageIndex)
                                scheduleResume()
                            },
                        )
                    },
            ) {
                val pageWidth = maxWidth
                Box(Modifier.fillMaxSize()) {
                    pages.forEachIndexed { index, item ->
                        PptSlot(index = index, pos = pos, page = item, width = pageWidth)
                    }
                }
            }
            PptFooter(
                pageIndex = pageIndex,
                lastIndex = lastIndex,
                total = pages.size,
                motion = motion,
                autoPaused = autoPaused,
                settledPage = settledPage,
                progressValue = { progress.value },
                onStart = { dismiss() },
            )
        }
    }
}

/** 一页的槽位：所有页并排；只有相邻页参与构图（远处只占位），横移在绘制阶段完成。 */
@Composable
private fun PptSlot(index: Int, pos: MutableFloatState, page: PptPage, width: Dp) {
    val near by remember(index) {
        derivedStateOf { abs(pos.floatValue - index) <= 1.05f }
    }
    val entering by remember(index) {
        derivedStateOf { abs(pos.floatValue - index) <= .55f }
    }
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .graphicsLayer { translationX = (index - pos.floatValue) * size.width },
    ) {
        if (near) {
            // 入场一旦触发就不再收起：滑动出画时内容不会突然消失。
            var entered by remember(index) { mutableStateOf(false) }
            LaunchedEffect(entering) { if (entering) entered = true }
            PptPageView(page = page, entered = entered)
        }
    }
}

@Composable
private fun PptPageView(page: PptPage, entered: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    val motion = ui.settings.motion != "none"
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        val artHeight = (maxHeight - 212.dp).coerceIn(160.dp, 330.dp)
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            PptLayer(
                show = entered,
                delayMs = 0L,
                motion = motion,
                modifier = Modifier.fillMaxWidth().height(PptTitleHeight),
            ) {
                Txt(
                    page.title,
                    size = 29f,
                    weight = FontWeight.SemiBold,
                    lineHeight = 36f,
                    color = p.ink,
                )
            }
            Spacer(Modifier.height(10.dp))
            PptLayer(
                show = entered,
                delayMs = PPT_BODY_DELAY_MS,
                motion = motion,
                modifier = Modifier.fillMaxWidth().height(PptBodyHeight),
            ) {
                Txt(page.body, size = 14f, color = p.muted, lineHeight = 23f)
            }
            Spacer(Modifier.height(20.dp))
            PptLayer(
                show = entered,
                delayMs = PPT_ART_DELAY_MS,
                motion = motion,
                modifier = Modifier.fillMaxWidth().height(artHeight),
            ) {
                PptArtView(page.art, entered, Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * 页内分层入场的一层。
 *
 * 用 [AnimatedVisibility] 把 `slideInVertically + fadeIn` 放在常驻尺寸的 Box 里：
 * 外层 Box 一直占位，所以标题/说明/示意图依次出现时整页布局不会跳。
 */
@Composable
private fun PptLayer(
    show: Boolean,
    delayMs: Long,
    motion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val risePx = with(LocalDensity.current) { PPT_RISE_DP.dp.roundToPx() }
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        if (motion && delayMs > 0L) delay(delayMs)
        state.targetState = true
    }
    Box(modifier) {
        androidx.compose.animation.AnimatedVisibility(
            visibleState = state,
            enter = slideInVertically(
                animationSpec = if (motion) {
                    spring(dampingRatio = .6f, stiffness = 380f)
                } else {
                    tween(0)
                },
            ) { if (motion) risePx else 0 } + fadeIn(
                animationSpec = if (motion) {
                    spring(dampingRatio = .6f, stiffness = 380f)
                } else {
                    tween(0)
                },
            ),
            exit = fadeOut(tween(0)),
        ) {
            content()
        }
    }
}

/** 顶栏：字标 + 页码 + 跳过（右上角，48dp 热区；跳过立即结束并标记完成）。 */
@Composable
private fun PptTopBar(pageIndex: Int, total: Int, onSkip: () -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(p.ink),
            contentAlignment = Alignment.Center,
        ) {
            Txt("字", size = 11f, color = p.paper, weight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(10.dp))
        Txt("素纸", size = 11.5f, color = p.ink, weight = FontWeight.Medium, letterSpacing = .8f)
        Spacer(Modifier.weight(1f))
        Txt("${pageIndex + 1} / $total", size = 10f, color = p.muted, letterSpacing = .4f)
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(CircleShape)
                .bouncy(scale = .94f) { onSkip() },
            contentAlignment = Alignment.Center,
        ) {
            Txt("跳过", size = 10.5f, color = p.muted)
        }
    }
}

/** 页底：最后页「开始写」/ 提示一行 + 细线进度条（tween(3800, LinearEasing) 由自动播放驱动）。 */
@Composable
private fun PptFooter(
    pageIndex: Int,
    lastIndex: Int,
    total: Int,
    motion: Boolean,
    autoPaused: Boolean,
    settledPage: Int,
    progressValue: () -> Float,
    onStart: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val hint = when {
        !motion -> "左右轻扫"
        autoPaused -> "已暂停，6 秒后继续"
        else -> "左右轻扫 · 自动播放"
    }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (pageIndex >= lastIndex) {
                PrimaryButton("开始写", onStart, Modifier.fillMaxWidth(), glyph = Glyph.Check)
            } else {
                Txt(
                    hint,
                    size = 10f,
                    color = p.muted.copy(alpha = .85f),
                    letterSpacing = .6f,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(2.5.dp)) {
            val fraction = if (motion) {
                progressValue().coerceIn(0f, 1f)
            } else {
                (settledPage + 1f) / total
            }
            val y = size.height / 2f
            drawLine(p.line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            if (fraction > .001f) {
                drawLine(
                    color = ui.accent,
                    start = Offset(0f, y),
                    end = Offset(size.width * fraction, y),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(ui.accent, 2.2.dp.toPx(), Offset(size.width * fraction, y))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * 静止背景：纸底 + 一处极淡暖光 + 两道细环。
 *
 * 换页时这里一个像素都不动（3.26.25 的亮度闪烁教训：背景一参与过渡就会闪）。
 * 所有 alpha 用 `darkAmount` 连续插值，不在中点硬翻。
 */
@Composable
private fun PptBackdrop() {
    val ui = LocalInk.current
    val p = ui.palette
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val dark = p.darkAmount
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    ui.accent.copy(alpha = .10f + .06f * dark),
                    Color.Transparent,
                ),
                center = Offset(w * .82f, h * .14f),
                radius = w * .90f,
            ),
            radius = w * .90f,
            center = Offset(w * .82f, h * .14f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = .46f * (1f - dark) + .04f),
                    Color.Transparent,
                ),
                center = Offset(w * .10f, h * .86f),
                radius = w * .80f,
            ),
            radius = w * .80f,
            center = Offset(w * .10f, h * .86f),
        )
        drawCircle(
            color = ui.accent.copy(alpha = .07f),
            radius = w * .52f,
            center = Offset(w * .86f, h * .22f),
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = ui.accent.copy(alpha = .05f),
            radius = w * .52f * 1.22f,
            center = Offset(w * .86f, h * .22f),
            style = Stroke(width = .8.dp.toPx()),
        )
    }
}

// ===== 示意图 =====
//
// 全部用 Compose 画，不引用真实页面控件：纸卡 + 文字线占位 + 强调色小块 + 少量图标，
// 这样示意和真实 UI 不会一起漂移，也不需要给页面挂锚点。

@Composable
private fun PptArtView(art: PptArt, active: Boolean, modifier: Modifier = Modifier) {
    Box(modifier) {
        when (art) {
            PptArt.Welcome -> ArtWelcome(active)
            PptArt.NewBook -> ArtNewBook(active)
            PptArt.NewChapter -> ArtNewChapter(active)
            PptArt.Writing -> ArtWriting(active)
            PptArt.Select -> ArtSelect(active)
            PptArt.Ability -> ArtAbility(active)
            PptArt.Symbols -> ArtSymbols(active)
            PptArt.Type -> ArtType(active)
            PptArt.Todo -> ArtTodo(active)
            PptArt.Notes -> ArtNotes(active)
            PptArt.Export -> ArtExport(active)
            PptArt.Ask -> ArtAsk(active)
            PptArt.AiTools -> ArtAiTools(active)
            PptArt.Story -> ArtStory(active)
            PptArt.Backup -> ArtBackup(active)
            PptArt.Look -> ArtLook(active)
            PptArt.Stats -> ArtStats(active)
            PptArt.Done -> ArtDone(active)
        }
    }
}

/**
 * 示意图里的一件元素：逐件出现（每 [PPT_ART_STEP_MS] 一件），[scaleIn] .94→1 + 淡入。
 *
 * 外层 Box 一直占位（调用方给它固定尺寸），所以元素一件件长出来时布局不会推挤。
 */
@Composable
private fun ArtPiece(
    active: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = LocalInk.current.settings.motion != "none"
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        if (motion && index > 0) delay(PPT_ART_STEP_MS * index)
        shown = true
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedVisibility(
            visible = shown,
            enter = scaleIn(
                animationSpec = if (motion) {
                    spring(dampingRatio = .6f, stiffness = 380f)
                } else {
                    tween(0)
                },
                initialScale = .94f,
            ) + fadeIn(
                animationSpec = if (motion) tween(180) else tween(0),
            ),
            exit = fadeOut(tween(0)),
        ) {
            content()
        }
    }
}

/** 一张纸卡：纸色底 + 细描边（深色主题里用墨色微透明做底，不铺第二套配色）。 */
@Composable
private fun ArtCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    fill: Color? = null,
    bordered: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalInk.current.palette
    val shape = SmoothCorner(radius)
    Box(
        modifier
            .clip(shape)
            .background(fill ?: p.ink.copy(alpha = .04f))
            .then(if (bordered) Modifier.border(1.dp, p.line, shape) else Modifier),
        content = content,
    )
}

/** 一条文字占位线。 */
@Composable
private fun ArtBar(color: Color, modifier: Modifier = Modifier, height: Dp = 5.dp) {
    Box(
        modifier
            .height(height)
            .clip(SmoothCorner(height / 2))
            .background(color),
    )
}

/** 小标签（能力名 / 文件名 / 时间这类）。 */
@Composable
private fun ArtChip(
    text: String,
    fill: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    size: Float = 9.5f,
) {
    Box(
        modifier
            .clip(SmoothCorner(999.dp))
            .background(fill)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, size = size, color = fg, weight = FontWeight.Medium)
    }
}

/** 一个字符键（符号行 / 能力格）。 */
@Composable
private fun ArtKey(text: String, highlight: Boolean, modifier: Modifier = Modifier) {
    val ui = LocalInk.current
    val p = ui.palette
    val shape = SmoothCorner(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (highlight) dockTone(ui) else p.ink.copy(alpha = .05f))
            .border(1.dp, if (highlight) ui.accent.copy(alpha = .22f) else p.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        Txt(
            text,
            size = 12.5f,
            color = if (highlight) ui.accent else p.ink.copy(alpha = .78f),
        )
    }
}

/** 「字」印章，欢迎页 / 结束页共用。 */
@Composable
private fun ArtSeal(size: Dp) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(Modifier.size(size).clip(CircleShape).background(p.ink), contentAlignment = Alignment.Center) {
        Txt("字", size = 13f, color = p.paper, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun ArtWelcome(active: Boolean) {
    val p = LocalInk.current.palette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArtCard(
            Modifier.fillMaxHeight(.94f).aspectRatio(.72f),
            radius = 18.dp,
            fill = p.paper,
        ) {
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                ArtPiece(active, 0, Modifier.size(34.dp)) { ArtSeal(34.dp) }
                Spacer(Modifier.height(16.dp))
                ArtPiece(active, 1, Modifier.fillMaxWidth(.72f).height(8.dp)) {
                    ArtBar(p.ink.copy(alpha = .76f), Modifier.fillMaxWidth(), 8.dp)
                }
                Spacer(Modifier.height(10.dp))
                ArtPiece(active, 2, Modifier.fillMaxWidth(.94f).height(5.dp)) {
                    ArtBar(p.ink.copy(alpha = .24f), Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(7.dp))
                ArtPiece(active, 3, Modifier.fillMaxWidth(.88f).height(5.dp)) {
                    ArtBar(p.ink.copy(alpha = .20f), Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(7.dp))
                ArtPiece(active, 4, Modifier.fillMaxWidth(.64f).height(5.dp)) {
                    ArtBar(p.ink.copy(alpha = .16f), Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(18.dp))
                ArtPiece(active, 5, Modifier.fillMaxWidth(.46f).height(20.dp)) {
                    val ui = LocalInk.current
                    ArtChip("第一章", ui.accent.copy(alpha = .12f), ui.accent, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ArtNewBook(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(.10f, .18f, .26f).forEachIndexed { index, mix ->
            ArtPiece(
                active,
                index,
                Modifier.padding(end = 12.dp).width(56.dp).height(86.dp),
            ) {
                ArtCard(
                    Modifier.fillMaxSize(),
                    radius = 8.dp,
                    fill = lerp(p.paper, ui.accent, mix),
                    bordered = false,
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ArtBar(p.ink.copy(alpha = .28f), Modifier.fillMaxWidth(.62f), 3.dp)
                        ArtBar(p.ink.copy(alpha = .22f), Modifier.fillMaxWidth(.44f), 3.dp)
                    }
                }
            }
        }
        ArtPiece(active, 3, Modifier.width(46.dp).height(86.dp)) {
            ArtCard(Modifier.fillMaxSize(), radius = 10.dp) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LineIcon(Glyph.Plus, ui.accent, 20.dp)
                }
            }
        }
    }
}

@Composable
private fun ArtNewChapter(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArtCard(
            Modifier.fillMaxWidth(.84f).fillMaxHeight(.86f),
            radius = 18.dp,
            fill = p.paper,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                ArtPiece(active, 0, Modifier.fillMaxWidth(.52f).height(7.dp)) {
                    ArtBar(p.ink.copy(alpha = .7f), Modifier.fillMaxWidth(), 7.dp)
                }
                Spacer(Modifier.height(16.dp))
                ArtPiece(active, 1, Modifier.fillMaxWidth().height(34.dp)) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(18.dp).clip(CircleShape).background(ui.accent.copy(alpha = .14f)),
                            contentAlignment = Alignment.Center,
                        ) { Txt("1", size = 9f, color = ui.accent) }
                        Spacer(Modifier.width(10.dp))
                        ArtBar(p.ink.copy(alpha = .24f), Modifier.fillMaxWidth(.62f))
                    }
                }
                Spacer(Modifier.height(4.dp))
                ArtPiece(active, 2, Modifier.fillMaxWidth().height(34.dp)) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(18.dp).clip(CircleShape).background(p.ink.copy(alpha = .06f)),
                            contentAlignment = Alignment.Center,
                        ) { Txt("2", size = 9f, color = p.muted) }
                        Spacer(Modifier.width(10.dp))
                        ArtBar(p.ink.copy(alpha = .18f), Modifier.fillMaxWidth(.5f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                ArtPiece(active, 3, Modifier.fillMaxWidth().height(40.dp)) {
                    ArtCard(Modifier.fillMaxSize(), radius = 12.dp) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LineIcon(Glyph.Plus, ui.accent, 14.dp)
                            Spacer(Modifier.width(8.dp))
                            Txt("新章节", size = 10f, color = p.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtWriting(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArtCard(
            Modifier.fillMaxWidth(.90f).fillMaxHeight(.84f),
            radius = 18.dp,
            fill = p.paper,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                ArtPiece(active, 0, Modifier.fillMaxWidth(.86f).height(6.dp)) {
                    ArtBar(p.ink.copy(alpha = .30f), Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(11.dp))
                ArtPiece(active, 1, Modifier.fillMaxWidth(.94f).height(6.dp)) {
                    ArtBar(p.ink.copy(alpha = .26f), Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(11.dp))
                ArtPiece(active, 2, Modifier.fillMaxWidth(.6f).height(16.dp)) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        ArtBar(p.ink.copy(alpha = .24f), Modifier.fillMaxWidth(.62f))
                        Spacer(Modifier.width(2.dp))
                        Box(
                            Modifier
                                .width(1.5.dp)
                                .height(15.dp)
                                .clip(SmoothCorner(1.dp))
                                .background(ui.accent),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                ArtPiece(
                    active,
                    3,
                    Modifier.align(Alignment.End).width(74.dp).height(20.dp),
                ) {
                    ArtChip(
                        "已自动保存",
                        p.ink.copy(alpha = .05f),
                        p.muted,
                        Modifier.fillMaxWidth(),
                        size = 9f,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtSelect(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtPiece(active, 0, Modifier.fillMaxWidth(.90f).height(118.dp)) {
            ArtCard(Modifier.fillMaxSize(), radius = 16.dp, fill = p.paper) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    ArtBar(p.ink.copy(alpha = .16f), Modifier.fillMaxWidth(.9f))
                    Spacer(Modifier.height(12.dp))
                    // 被选中那一行：强调色底 + 两端手柄。
                    Box(
                        Modifier
                            .fillMaxWidth(.82f)
                            .height(22.dp)
                            .clip(SmoothCorner(6.dp))
                            .background(ui.accent.copy(alpha = .16f)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        ArtBar(ui.accent.copy(alpha = .45f), Modifier.fillMaxWidth(.86f))
                        Box(
                            Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(ui.accent),
                        )
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Box(Modifier.width(2.dp).fillMaxHeight().background(ui.accent))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    ArtBar(p.ink.copy(alpha = .16f), Modifier.fillMaxWidth(.6f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        ArtPiece(active, 1, Modifier.fillMaxWidth(.88f).height(38.dp)) {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(SmoothCorner(999.dp))
                    .background(dockTone(ui))
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("校核", "复制", "全选", "删除").forEach { label ->
                    Txt(label, size = 9.5f, color = p.ink.copy(alpha = .8f))
                }
            }
        }
    }
}

@Composable
private fun ArtAbility(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    val tools = listOf(
        Glyph.Undo to "撤销",
        Glyph.Redo to "重做",
        Glyph.Indent to "缩进",
        Glyph.Quote to "引号",
        Glyph.Divider to "分节",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(.96f)
                .height(78.dp)
                .clip(SmoothCorner(20.dp))
                .background(dockTone(ui)),
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tools.forEachIndexed { index, (glyph, label) ->
                    ArtPiece(active, index, Modifier.width(46.dp).height(60.dp)) {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LineIcon(glyph, p.ink.copy(alpha = .82f), 19.dp)
                            Spacer(Modifier.height(5.dp))
                            Txt(label, size = 8.5f, color = p.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtSymbols(active: Boolean) {
    val ui = LocalInk.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth(.96f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("，", "。", "？", "！", "……").forEachIndexed { index, symbol ->
                ArtPiece(active, index, Modifier.width(38.dp).height(40.dp)) {
                    ArtKey(symbol, highlight = index == 4, modifier = Modifier.fillMaxSize())
                }
            }
            ArtPiece(active, 5, Modifier.width(60.dp).height(40.dp)) {
                ArtKey("——", highlight = false, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ArtType(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArtCard(Modifier.fillMaxWidth(.88f).fillMaxHeight(.82f), radius = 18.dp) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                ArtPiece(active, 0, Modifier.fillMaxWidth().height(30.dp)) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Txt("字号", size = 9f, color = p.muted)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f).height(30.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                val y = size.height / 2f
                                drawLine(
                                    p.line,
                                    Offset(0f, y),
                                    Offset(size.width, y),
                                    1.5.dp.toPx(),
                                    StrokeCap.Round,
                                )
                                drawLine(
                                    ui.accent,
                                    Offset(0f, y),
                                    Offset(size.width * .62f, y),
                                    1.5.dp.toPx(),
                                    StrokeCap.Round,
                                )
                                drawCircle(ui.accent, 6.dp.toPx(), Offset(size.width * .62f, y))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ArtPiece(active, 1, Modifier.fillMaxWidth().height(30.dp)) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Txt("行距", size = 9f, color = p.muted)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f).height(30.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                val y = size.height / 2f
                                drawLine(
                                    p.line,
                                    Offset(0f, y),
                                    Offset(size.width, y),
                                    1.5.dp.toPx(),
                                    StrokeCap.Round,
                                )
                                drawLine(
                                    ui.accent,
                                    Offset(0f, y),
                                    Offset(size.width * .40f, y),
                                    1.5.dp.toPx(),
                                    StrokeCap.Round,
                                )
                                drawCircle(ui.accent, 6.dp.toPx(), Offset(size.width * .40f, y))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                ArtPiece(active, 2, Modifier.fillMaxWidth().height(46.dp)) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(.12f, .26f, .40f).forEach { mix ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(SmoothCorner(10.dp))
                                    .background(lerp(p.paper, ui.accent, mix))
                                    .border(1.dp, p.line, SmoothCorner(10.dp)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ArtPiece(active, 3, Modifier.fillMaxWidth(.4f).height(16.dp)) {
                    Txt("纸面", size = 9f, color = p.muted)
                }
            }
        }
    }
}

@Composable
private fun ArtTodo(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArtCard(Modifier.fillMaxWidth(.86f).fillMaxHeight(.80f), radius = 18.dp, fill = p.paper) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                ArtPiece(active, 0, Modifier.fillMaxWidth().height(24.dp)) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(SmoothCorner(4.dp))
                                .border(1.dp, p.muted, SmoothCorner(4.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        ArtBar(p.ink.copy(alpha = .26f), Modifier.fillMaxWidth(.66f))
                    }
                }
                Spacer(Modifier.height(14.dp))
                ArtPiece(active, 1, Modifier.fillMaxWidth().height(24.dp)) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(14.dp).clip(SmoothCorner(4.dp)).background(ui.accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            LineIcon(Glyph.Check, p.paper, 9.dp, 2.2f)
                        }
                        Spacer(Modifier.width(10.dp))
                        ArtBar(p.ink.copy(alpha = .16f), Modifier.fillMaxWidth(.5f))
                    }
                }
                Spacer(Modifier.height(20.dp))
                ArtPiece(
                    active,
                    2,
                    Modifier.align(Alignment.End).width(104.dp).height(24.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .clip(SmoothCorner(999.dp))
                            .background(ui.accent.copy(alpha = .12f))
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LineIcon(Glyph.Bell, ui.accent, 11.dp)
                        Spacer(Modifier.width(6.dp))
                        Txt("明天 9:00", size = 9f, color = ui.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtNotes(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Column(
        Modifier.fillMaxSize().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtPiece(active, 0, Modifier.fillMaxWidth(.56f).height(34.dp)) {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(SmoothCorner(999.dp))
                    .background(ui.accent.copy(alpha = .10f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineIcon(Glyph.Folder, ui.accent, 13.dp)
                Spacer(Modifier.width(7.dp))
                Txt("笔记夹", size = 9.5f, color = ui.accent, weight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(14.dp))
        ArtPiece(active, 1, Modifier.fillMaxWidth(.88f).height(88.dp)) {
            ArtCard(Modifier.fillMaxSize(), radius = 16.dp, fill = p.paper) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    ArtBar(p.ink.copy(alpha = .34f), Modifier.fillMaxWidth(.5f), 6.dp)
                    Spacer(Modifier.height(12.dp))
                    ArtBar(p.ink.copy(alpha = .18f), Modifier.fillMaxWidth(.9f))
                    Spacer(Modifier.height(8.dp))
                    ArtBar(p.ink.copy(alpha = .18f), Modifier.fillMaxWidth(.72f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        ArtPiece(active, 2, Modifier.width(132.dp).height(26.dp)) {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(SmoothCorner(999.dp))
                    .background(p.ink.copy(alpha = .05f))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineIcon(Glyph.Link, p.muted, 11.dp)
                Spacer(Modifier.width(6.dp))
                Txt("关联 · 第一章", size = 9f, color = p.muted)
            }
        }
    }
}

@Composable
private fun ArtExport(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtPiece(active, 0, Modifier.width(112.dp).height(84.dp)) {
            ArtCard(Modifier.fillMaxSize(), radius = 14.dp, fill = ui.accent.copy(alpha = .10f)) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LineIcon(Glyph.Export, ui.accent, 24.dp)
                    Spacer(Modifier.height(8.dp))
                    Txt("EPUB", size = 9f, color = ui.accent, letterSpacing = .8f)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ArtPiece(active, 1, Modifier.fillMaxWidth(.68f).height(34.dp)) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(SmoothCorner(999.dp))
                        .background(p.ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("EPUB", size = 9.5f, color = p.paper, weight = FontWeight.Medium)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(SmoothCorner(999.dp))
                        .background(p.ink.copy(alpha = .06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("TXT", size = 9.5f, color = p.ink.copy(alpha = .78f), weight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        ArtPiece(active, 2, Modifier.fillMaxWidth(.7f).height(16.dp)) {
            Txt(
                "下载 / 字隅",
                size = 9f,
                color = p.muted,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ArtAsk(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Column(
        Modifier.fillMaxSize().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtPiece(active, 0, Modifier.align(Alignment.End).fillMaxWidth(.66f).height(38.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(SmoothCorner(14.dp))
                    .background(dockTone(ui))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                ArtBar(p.ink.copy(alpha = .34f), Modifier.fillMaxWidth(.6f))
            }
        }
        Spacer(Modifier.height(12.dp))
        ArtPiece(active, 1, Modifier.fillMaxWidth(.90f).height(78.dp)) {
            ArtCard(Modifier.fillMaxSize(), radius = 14.dp) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    ArtBar(p.ink.copy(alpha = .26f), Modifier.fillMaxWidth(.94f))
                    Spacer(Modifier.height(9.dp))
                    ArtBar(p.ink.copy(alpha = .20f), Modifier.fillMaxWidth(.86f))
                    Spacer(Modifier.height(9.dp))
                    ArtBar(p.ink.copy(alpha = .16f), Modifier.fillMaxWidth(.6f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ArtPiece(active, 2, Modifier.width(124.dp).height(22.dp)) {
            ArtChip(
                "用了 2 个工具",
                p.ink.copy(alpha = .05f),
                p.muted,
                Modifier.fillMaxWidth(),
                size = 9f,
            )
        }
        Spacer(Modifier.height(14.dp))
        ArtPiece(active, 3, Modifier.fillMaxWidth(.94f).height(40.dp)) {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(SmoothCorner(999.dp))
                    .background(p.paper)
                    .border(1.dp, p.line, SmoothCorner(999.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtBar(p.ink.copy(alpha = .20f), Modifier.fillMaxWidth(.5f))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(ui.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(Glyph.Sparkles, p.paper, 12.dp)
                }
            }
        }
    }
}

@Composable
private fun ArtAiTools(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtPiece(active, 0, Modifier.fillMaxWidth(.90f).height(74.dp)) {
            ArtCard(Modifier.fillMaxSize(), radius = 14.dp, fill = p.paper) {
                Column(Modifier.fillMaxSize().padding(13.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(.82f)
                            .height(18.dp)
                            .clip(SmoothCorner(5.dp))
                            .background(ui.accent.copy(alpha = .16f)),
                    )
                    Spacer(Modifier.height(10.dp))
                    ArtBar(p.ink.copy(alpha = .18f), Modifier.fillMaxWidth(.6f))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(.94f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("校核", "修饰", "问").forEachIndexed { index, label ->
                ArtPiece(active, index + 1, Modifier.weight(1f).height(34.dp)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(SmoothCorner(999.dp))
                            .background(if (index == 0) ui.accent else p.ink.copy(alpha = .06f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Txt(
                            label,
                            size = 10f,
                            color = if (index == 0) p.paper else p.ink.copy(alpha = .8f),
                            weight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        ArtPiece(active, 4, Modifier.fillMaxWidth(.90f).height(58.dp)) {
            ArtCard(Modifier.fillMaxSize(), radius = 14.dp) {
                Row(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        ArtBar(p.ink.copy(alpha = .22f), Modifier.fillMaxWidth(.9f))
                        Spacer(Modifier.height(8.dp))
                        ArtBar(p.ink.copy(alpha = .16f), Modifier.fillMaxWidth(.66f))
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .width(52.dp)
                            .height(26.dp)
                            .clip(SmoothCorner(999.dp))
                            .background(ui.accent.copy(alpha = .12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Txt("采纳", size = 9f, color = ui.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtStory(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtPiece(active, 0, Modifier.fillMaxWidth(.8f).height(32.dp)) {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(SmoothCorner(999.dp))
                    .background(p.ink.copy(alpha = .05f))
                    .padding(3.dp),
            ) {
                listOf("角色", "时间线", "大纲").forEachIndexed { index, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(SmoothCorner(999.dp))
                            .background(if (index == 0) p.paper else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Txt(
                            label,
                            size = 9f,
                            color = if (index == 0) p.ink else p.muted,
                            weight = if (index == 0) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ArtPiece(active, 1, Modifier.fillMaxWidth(.92f).height(168.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width * .5f, size.height * .5f)
                val nodes = listOf(
                    Offset(size.width * .20f, size.height * .24f),
                    Offset(size.width * .82f, size.height * .30f),
                    Offset(size.width * .70f, size.height * .84f),
                )
                nodes.forEach { node ->
                    drawLine(
                        ui.accent.copy(alpha = .22f),
                        c,
                        node,
                        1.2.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
                drawCircle(ui.accent.copy(alpha = .12f), 27.dp.toPx(), c)
                drawCircle(ui.accent.copy(alpha = .30f), 27.dp.toPx(), c, style = Stroke(1.2.dp.toPx()))
                drawCircle(ui.accent.copy(alpha = .16f), 20.dp.toPx(), nodes[0])
                drawCircle(ui.accent.copy(alpha = .16f), 18.dp.toPx(), nodes[1])
                drawCircle(ui.accent.copy(alpha = .16f), 18.dp.toPx(), nodes[2])
            }
        }
        Spacer(Modifier.height(12.dp))
        ArtPiece(active, 2, Modifier.width(128.dp).height(22.dp)) {
            ArtChip(
                "AI 整理 · 待同步",
                p.ink.copy(alpha = .05f),
                p.muted,
                Modifier.fillMaxWidth(),
                size = 8.5f,
            )
        }
    }
}

@Composable
private fun ArtBackup(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtPiece(active, 0, Modifier.width(104.dp).height(78.dp)) {
            ArtCard(Modifier.fillMaxSize(), radius = 14.dp, fill = p.paper) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LineIcon(Glyph.Download, ui.accent, 22.dp)
                    Spacer(Modifier.height(7.dp))
                    Txt("JSON", size = 9f, color = ui.accent, letterSpacing = .8f)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(.86f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtPiece(active, 1, Modifier.weight(1f).height(34.dp)) {
                Box(
                    Modifier.fillMaxSize().clip(SmoothCorner(999.dp)).background(p.ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("导出备份", size = 9.5f, color = p.paper, weight = FontWeight.Medium)
                }
            }
            ArtPiece(active, 2, Modifier.weight(1f).height(34.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(SmoothCorner(999.dp))
                        .background(p.ink.copy(alpha = .06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("恢复备份", size = 9.5f, color = p.ink.copy(alpha = .78f), weight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ArtPiece(active, 3, Modifier.fillMaxWidth(.66f).height(16.dp)) {
            Txt(
                "最近备份 · 今天 09:12",
                size = 9f,
                color = p.muted,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ArtLook(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(.84f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(.10f, .55f, .90f).forEachIndexed { index, dark ->
                ArtPiece(active, index, Modifier.weight(1f).height(58.dp)) {
                    ArtCard(
                        Modifier.fillMaxSize(),
                        radius = 12.dp,
                        fill = lerp(p.paper, Color(0xFF191813), dark),
                        bordered = false,
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(9.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            ArtBar(
                                if (dark > .5f) Color.White.copy(alpha = .34f) else p.ink.copy(alpha = .30f),
                                Modifier.fillMaxWidth(.66f),
                                3.dp,
                            )
                            ArtBar(
                                if (dark > .5f) Color.White.copy(alpha = .18f) else p.ink.copy(alpha = .16f),
                                Modifier.fillMaxWidth(.44f),
                                3.dp,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(.84f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtPiece(active, 3, Modifier.width(52.dp).height(46.dp)) {
                ArtCard(Modifier.fillMaxSize(), radius = 12.dp) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Txt("Aa", size = 17f, color = p.ink, fontFamily = FontFamily.Serif)
                    }
                }
            }
            ArtPiece(active, 4, Modifier.weight(1f).height(46.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(SmoothCorner(23.dp))
                        .background(lerp(p.paper, ui.accent, .10f))
                        .border(1.dp, Color.White.copy(alpha = .5f), SmoothCorner(23.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("玻璃", size = 9.5f, color = p.muted)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ArtPiece(active, 5, Modifier.fillMaxWidth(.56f).height(16.dp)) {
            Txt(
                "主题 · 字体 · 材质",
                size = 9f,
                color = p.muted,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ArtStats(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArtCard(Modifier.fillMaxWidth(.86f).fillMaxHeight(.78f), radius = 18.dp) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                ArtPiece(active, 0, Modifier.fillMaxWidth(.42f).height(6.dp)) {
                    ArtBar(p.ink.copy(alpha = .26f), Modifier.fillMaxWidth(), 6.dp)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    listOf(34.dp, 56.dp, 44.dp, 70.dp, 50.dp).forEachIndexed { index, barHeight ->
                        ArtPiece(active, index + 1, Modifier.width(18.dp).height(barHeight)) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(SmoothCorner(9.dp))
                                    .background(
                                        if (index == 3) ui.accent else ui.accent.copy(alpha = .22f),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtDone(active: Boolean) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArtCard(
            Modifier.fillMaxHeight(.94f).aspectRatio(.72f),
            radius = 18.dp,
            fill = p.paper,
        ) {
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ArtPiece(active, 0, Modifier.size(46.dp)) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(ui.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(Glyph.Check, p.paper, 22.dp, 2.2f)
                    }
                }
                Spacer(Modifier.height(20.dp))
                ArtPiece(active, 1, Modifier.fillMaxWidth(.74f).height(7.dp)) {
                    ArtBar(p.ink.copy(alpha = .56f), Modifier.fillMaxWidth(), 7.dp)
                }
                Spacer(Modifier.height(12.dp))
                ArtPiece(active, 2, Modifier.fillMaxWidth(.90f).height(5.dp)) {
                    ArtBar(p.ink.copy(alpha = .18f), Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(7.dp))
                ArtPiece(active, 3, Modifier.fillMaxWidth(.78f).height(5.dp)) {
                    ArtBar(p.ink.copy(alpha = .14f), Modifier.fillMaxWidth())
                }
            }
        }
    }
}
