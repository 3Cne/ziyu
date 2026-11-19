package com.moyu.writer

import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.ActionMode
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.text.style.LineHeightSpan
import android.text.style.ReplacementSpan
import android.text.style.UpdateAppearance
import android.text.method.ArrowKeyMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class EditorSnapshot(
    val title: String,
    val body: TextFieldValue,
    val todos: List<Todo> = emptyList(),
)

/**
 * Everything the writing screen needs to show the bound notes and to open the
 * binding sheet. Kept as one value so the editor never reaches into the store
 * for chapter identity.
 */
data class NoteLinkUi(
    val notes: List<Note>,
    val floatOn: Boolean,
    val onOpenSheet: () -> Unit,
    val onToggleFloat: (Boolean) -> Unit,
    val onOpenNote: (Note) -> Unit,
    /** Saves an edit made inside the floating panel (title, body, todos). */
    val onSaveNote: (id: String, title: String, content: String, todos: List<Todo>) -> Unit =
        { _, _, _, _ -> },
    val floatX: Float = .62f,
    val floatY: Float = 0f,
    val floatW: Float = .60f,
    val floatH: Float = .52f,
    val onFloatPlacement: (x: Float, y: Float, w: Float, h: Float) -> Unit = { _, _, _, _ -> },
)

data class SelectionBar(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

/**
 * 3.26.41：工具栏横滑移光标时的状态。
 *
 * 手势在 [EditorDock] 里（非选区工具行），状态提到 [WritingScreen] 页面层：
 * 进度条 HUD（[DockScrubHud]）画在能力栏正上方，是覆盖层，不参与测量，
 * 所以既不挤压正文、也不抬高能力栏。读 ratio/chars/total 的只有 HUD 那一小块
 * （见 ScrubHudLayer），每跨一个字不会重组整页。
 */
private class ScrubHudState {
    var visible by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var ratio by mutableFloatStateOf(0f)
    var chars by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
}

private data class TodoAction(val id: String, val bounds: Rect)

data class HandleBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class SelectionGeometry(
    val startCaretX: Float,
    val startTop: Float,
    val startBottom: Float,
    val startHandle: HandleBox,
    val endCaretX: Float,
    val endTop: Float,
    val endBottom: Float,
    val endHandle: HandleBox,
    val active: Boolean,
    /** 正在被拖的那只（0 起点 / 1 终点 / -1 没有）。 */
    val dragSide: Int = -1,
    /** 被拖那只的水滴形变强度：0 = 原形，越大拉得越长。 */
    val dragPull: Float = 0f,
    /**
     * 手指移动方向（单位向量）。水滴沿这条轴拉长、垂直方向收窄——
     * 2026-11-15 用户报「怎么都是一个形变形状」，根因是以前不管往哪拖都写死
     * `scaleX=1+.55p / scaleY=1-.22p`（永远横向拉长）。现在方向由手指决定：
     * 竖直拖就竖直拉长，斜拖按分量混合。
     */
    val dragDirX: Float = 0f,
    val dragDirY: Float = 0f,
)

@Composable
private fun SelectionOverlay(
    geometry: SelectionGeometry?,
    handleDrawable: android.graphics.drawable.Drawable?,
    color: Color,
    onTopInWindow: (Int) -> Unit,
) {
    val active = geometry != null && geometry.active
    // 手柄收起也要有过渡：几何一变成 null 就立刻不画的话，看起来是「瞬消」（用户报的手感）。
    var last by remember { mutableStateOf<SelectionGeometry?>(null) }
    LaunchedEffect(active, geometry) {
        if (active && geometry != null) last = geometry
    }
    val vis = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) vis.animateTo(1f, spring(dampingRatio = .52f, stiffness = 300f))
        else vis.animateTo(0f, tween(150))
    }
    val g: SelectionGeometry = geometry?.takeIf { it.active } ?: last ?: return
    val v = vis.value
    if (!active && v <= .01f) return
    Canvas(
        Modifier
            .fillMaxSize()
            // 不裁：手柄要能画到正文容器上边界之外（用户报「上端被挤压」）。
            // 层级仍在正文之上，只是不再受正文高度约束。
            .onGloballyPositioned { onTopInWindow(it.boundsInWindow().top.toInt()) },
    ) {
        val stroke = 2.5.dp.toPx()
        val lineAlpha = v.coerceIn(0f, 1f)
        if (lineAlpha > .02f) {
            drawLine(
                color = color,
                start = Offset(g.startCaretX, g.startTop),
                end = Offset(g.startCaretX, g.startBottom),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                alpha = lineAlpha,
            )
            drawLine(
                color = color,
                start = Offset(g.endCaretX, g.endTop),
                end = Offset(g.endCaretX, g.endBottom),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                alpha = lineAlpha,
            )
        }
        val d = handleDrawable ?: return@Canvas
        // 弹簧会出现轻微过冲（v 略大于 1），手柄据此弹一下再落定。
        val scale = (.55f + .45f * v).coerceIn(0f, 1.25f)
        // 沿手指位移方向拉长、垂直方向收窄（体积守恒的读感）。
        // 纯水平拖 → 1+.55p / 1-.22p（旧手感）；纯竖直拖 → 竖直拉长；
        // 斜拖按 |dx|、|dy| 分量混合。锚点是水滴尖端，所以身体总是拖在指尖后面。
        val pull = g.dragPull.coerceIn(0f, 1f)
        val wx = kotlin.math.abs(g.dragDirX).coerceIn(0f, 1f)
        val wy = kotlin.math.abs(g.dragDirY).coerceIn(0f, 1f)
        // 粘滞水滴（ViscousDrop）：与标签栏镜片同一套读数——沿运动方向最多拉长
        // 75%、垂直压 28%，松手后指数衰减（不再一帧切回圆形）。
        val dirScaleX = ViscousDrop.scaleX(pull * wx, pull * wy)
        val dirScaleY = ViscousDrop.scaleY(pull * wx, pull * wy)
        fun drawHandle(box: HandleBox, flipVertically: Boolean, ax: Float, ay: Float) {
            if (v <= .02f) return
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val cx = (box.left + box.right) / 2f
                // 水滴的「尖」贴着字：起点柄在上方、尖朝下；终点柄在下方、尖朝上。
                val cy = if (flipVertically) box.bottom else box.top
                native.save()
                native.scale(scale * ax, scale * ay, cx, cy)
                // 用 alpha 让「正浮现的手柄」不靠描边硬出现。
                val oldAlpha = d.alpha
                d.alpha = (255 * v.coerceIn(0f, 1f)).toInt()
                if (flipVertically) {
                    native.save()
                    // Mirror around the handle's own horizontal centre so the
                    // start handle sits above the line and points down.
                    native.translate(0f, box.top + box.bottom)
                    native.scale(1f, -1f)
                }
                d.setBounds(
                    box.left.toInt(),
                    box.top.toInt(),
                    box.right.toInt(),
                    box.bottom.toInt(),
                )
                d.draw(native)
                if (flipVertically) native.restore()
                d.alpha = oldAlpha
                native.restore()
            }
        }
        val dragStart = g.dragSide == 0
        val dragEnd = g.dragSide == 1
        drawHandle(
            g.startHandle,
            flipVertically = true,
            ax = if (dragStart) dirScaleX else 1f,
            ay = if (dragStart) dirScaleY else 1f,
        )
        drawHandle(
            g.endHandle,
            flipVertically = false,
            ax = if (dragEnd) dirScaleX else 1f,
            ay = if (dragEnd) dirScaleY else 1f,
        )
    }
}

@Composable
fun WritingScreen(
    seedKey: String,
    heading: String,
    initialTitle: String,
    initialContent: String,
    placeholder: String,
    onSave: (String, String) -> Unit,
    onBack: () -> Unit,
    onMore: () -> Unit,
    isNote: Boolean = false,
    inputBlocked: Boolean = false,
    pendingTodos: Int = 0,
    onTodos: (() -> Unit)? = null,
    initialTodos: List<Todo> = emptyList(),
    onTodosChange: ((List<Todo>) -> Unit)? = null,
    metaPrefix: String = "",
    noteLinks: NoteLinkUi? = null,
    /** 笔记所在的笔记夹名：顶栏第一行（用户 2026-11-15 定）。 */
    noteFolderName: String = "",
    chapterTags: (@Composable () -> Unit)? = null,
    /** Book whose memory supplies name-consistency checks; null for a note. */
    proofBookId: String? = null,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val store = LocalStore.current
    val settings = ui.settings
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var bodyEditor by remember { mutableStateOf<BodyEditText?>(null) }

    fun hideEditorKeyboard() {
        focusManager.clearFocus(force = true)
        bodyEditor?.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(bodyEditor?.windowToken ?: view.windowToken, 0)
        keyboard?.hide()
    }

    var title by remember(seedKey) { mutableStateOf(initialTitle) }
    var inlineTodos by remember(seedKey) { mutableStateOf(initialTodos) }
    var todoPanel by remember { mutableStateOf<TodoAction?>(null) }
    var lastTodoPanel by remember { mutableStateOf<TodoAction?>(null) }
    var activeTodoId by remember { mutableStateOf<String?>(null) }
    var editingReminderId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(editingReminderId) {
        bodyEditor?.setInputLocked(editingReminderId != null)
    }
    var body by remember(seedKey) {
        // Start at the first line. Putting the initial caret at the end makes
        // BasicTextField's own internal scroller jump to the last page as soon
        // as the editor is laid out, before the user has touched anything.
        mutableStateOf(TextFieldValue(initialContent, TextRange.Zero))
    }
    fun collapseBodySelection() {
        val caret = body.selection.end.coerceIn(0, body.text.length)
        // Update the Compose mirror first: otherwise its LaunchedEffect writes
        // the old range back into the native EditText after the title focuses.
        body = body.copy(selection = TextRange(caret))
        bodyEditor?.collapseSelection()
        bodyEditor?.clearFocus()
    }
    LaunchedEffect(seedKey) {
        if (isNote && settings.indent && body.text.isEmpty()) {
            body = TextFieldValue("　　", TextRange(2))
        }
    }
    val undo = remember(seedKey) { mutableStateListOf<EditorSnapshot>() }
    val redo = remember(seedKey) { mutableStateListOf<EditorSnapshot>() }
    var saveTick by remember(seedKey) { mutableIntStateOf(0) }
    var saved by remember(seedKey) { mutableStateOf(true) }
    var dirty by remember(seedKey) { mutableStateOf(false) }
    var showType by remember { mutableStateOf(false) }
    // The dock's 「笔记」 toggles this one panel. It starts closed so the page
    // opens on full-width text: the 行文时悬浮 setting decides whether the panel
    // may be shown, not whether it is already open.
    var floatNotePanel by remember(seedKey) { mutableStateOf(false) }
    // 3.26.23: floating story bible opened from the dock (chapter pages only).
    var storyFloat by remember(seedKey) { mutableStateOf(false) }
    var storyJournalTick by remember { mutableIntStateOf(0) }
    // 能力栏（工具 + 符号栏）的展开态：左下角的球收起/展开整块能力栏。
    // 2026-11-15 用户要求「展开和关闭要有记忆性」：初值取设置里的 dockOpen
    // （首次与旧备份默认收起），每次开合写回设置。
    var abilityOpen by remember { mutableStateOf(settings.dockOpen) }
    val abilityT = remember { Animatable(1f) }
    // 3.26.11：能力栏开合前先记住正文滚动位置。
    // 上一版在 LaunchedEffect(abilityOpen) 里才读 editor.scrollY——那时
    // 底部让位已按新值重排、EditText 的滚动已被清零，于是把 0 当成
    // 「要守住的位置」钉死：滚到后段一点能力栏就整页回顶（用户录屏
    // 6c8b2f2b，5.0s 处先跳顶再被光标拉回来）。
    var abilityScrollKeep by remember { mutableIntStateOf(0) }
    LaunchedEffect(abilityOpen) {
        // 3.26.10：改成 300ms 的 tween。上一版用临界阻尼 spring(1f, 300f)：
        // 不过冲、但尾部极长——最后 5% 要爬 200ms 以上，能力栏的 alpha 直接
        // 等于 t，于是整块在「发灰」里停一下才变实（用户读作「顿一下」）。
        // tween 有确定的结束点，形变仍由 ViscousDrop.lagged(t) 给出。
        abilityT.animateTo(
            if (abilityOpen) 1f else 0f,
            tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        )
    }
    // 记忆：开合一变就写回设置；同值不重写，避免每次进编辑器都落盘。
    LaunchedEffect(abilityOpen) {
        store.updateSettings { if (it.dockOpen == abilityOpen) it else it.copy(dockOpen = abilityOpen) }
    }
    // 3.26.15：钉子挪进 BodyEditText.scrollTo（armScrollPin）。
    // 3.26.9–3.26.13 用的是这里的每帧循环：它最早下一帧才跑，而那一帧的
    // 原生重排已经把 scrollY 拉回 0 并画出去了，所以还剩「闪一下」。
    // 现在点击处先 arm，这一层只做兜底与取证日志。
    LaunchedEffect(abilityOpen) {
        val editor = bodyEditor ?: return@LaunchedEffect
        val keep = abilityScrollKeep
        android.util.Log.d("MOYU_JUMP", "keep=$keep editorY=${editor.scrollY}")
        editor.armScrollPin(keep)
    }
    // The editor dock overlays the body instead of sitting below it. The body
    // layer is recorded separately so the dock can sample the real text under
    // its glass, and dockHeight keeps the caret/last line above the dock.
    var dockHeightPx by remember { mutableIntStateOf(0) }
    // 能力栏的**实际占位**：收起时只剩左下角那个球（0），展开时才是整块高度。
    // 浮窗让位必须跟这个值，不能直接用 dockHeightPx——收起只是 graphicsLayer 缩放，
    // 布局高度不变，dockHeightPx 永远是展开时的值，于是「关掉能力栏后浮窗不回位」
    // （2026-11-15 用户报的第四条）。
    // 必须 coerce 到 0..1：abilityT 是 spring(.52) 会过冲，收起时会冲到 0 以下，
    // 乘出来的负 Dp 会让 padding 抛 "Padding must be non-negative" 直接闪退
    // （2026-11-15 实测崩在 Editor.kt:1588）。
    val densityNow = androidx.compose.ui.platform.LocalDensity.current
    val dockReserve = with(densityNow) { dockHeightPx.toDp() } *
        abilityT.value.coerceIn(0f, 1f)
    // 3.26.41：工具栏横滑移光标。手势在 EditorDock 的非选区工具行，状态放在页面层，
    // 这样 HUD（进度条 + 当前/总字数）能画在能力栏正上方的同一段布局里。
    val scrub = remember { ScrubHudState() }
    // 抬手后 220ms 再卸载；这 220ms 里又开始滑就取消这次卸载（dragging 重新为 true）。
    // 能力栏收起（abilityT < .5）时由下面的挂载条件立即卸载，不需要等这个计时。
    var scrubEndTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(scrubEndTick) {
        if (scrubEndTick == 0) return@LaunchedEffect
        delay(220)
        if (!scrub.dragging) scrub.visible = false
    }
    // 章节头收起（2026-11-15 用户定）：收起比例**实时跟着正文滚动量走**（不是过阈值跳变）。
    // 只有标题正在编辑时保持展开。
    var bodyScrollY by remember { mutableIntStateOf(0) }
    var titleFocused by remember { mutableStateOf(false) }
    val headerRangePx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.roundToPx() }
    // 2026-11-16 用户最终定：章节头**不再随滚动收起**，固定展开。
    // 试过的三条路都留了副作用，全部作废：
    //   ① 标题只缩放 + 正文整块上移 → 底部跟着被抬（用户录屏 1192→1156）；
    //   ② 标题区真变矮 → 正文容器高度变 → 原生 EditText 重排，历史三次弹跳；
    //   ③ 正文区向上长（layout 溢出）→ 可视范围确实变大，但溢出量与标题收缩量对不齐，
    //      正文压到章名上。
    // 现在一条位移/缩放都不做，所以底部绝对稳定；代价是不再「一屏多两行」。
    // 保留常量而不是删字段：正文区和标题区都还在读它，删了要牵一串。
    val chapterCollapse = if (titleFocused) 0f
    else (bodyScrollY.toFloat() / headerRangePx.coerceAtLeast(1)).coerceIn(0f, 1f)
    // 标题框的实测高度：用来算正文要往上让多少，纯位移、不改布局。
    var titleBoxH by remember { mutableIntStateOf(0) }

    // A Compose layerBackdrop records Compose draw content only, so it cannot
    // see the native BodyEditText (AndroidView). This backdrop draws the real
    // view into a software bitmap that the dock's 1型玻璃 samples; see
    // EditorBodyBackdrop at the bottom of this file.
    val editorBodyBackdrop = remember(p.paper) {
        EditorBodyBackdrop({ bodyEditor }, p.paper)
    }
    // 1型玻璃 only re-samples when its draw node records a new frame. The body
    // is a native EditText, so Compose never sees its scroll. Bump this tick on
    // every body scroll / text change and pass it as the glass epoch.
    val bodyScrollTick = remember { mutableIntStateOf(0) }

    val latestSave by androidx.compose.runtime.rememberUpdatedState(onSave)
    val latestTitle by androidx.compose.runtime.rememberUpdatedState(title)
    val latestBody by androidx.compose.runtime.rememberUpdatedState(body.text)
    val latestDirty by androidx.compose.runtime.rememberUpdatedState(dirty)
    val latestTodos by androidx.compose.runtime.rememberUpdatedState(inlineTodos)
    val latestTodosChange by androidx.compose.runtime.rememberUpdatedState(onTodosChange)
    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()

    fun snapshot() = EditorSnapshot(title, body, inlineTodos)
    fun doSave() {
        if (!dirty) {
            saved = true
            return
        }
        bodyEditor?.let { editor ->
            val collected = editor.collectTodos(inlineTodos)
            if (collected != inlineTodos) {
                inlineTodos = collected
                onTodosChange?.invoke(collected)
            }
        }
        onSave(title, body.text)
        dirty = false
        saved = true
    }
    LaunchedEffect(saveTick) {
        if (saveTick == 0) return@LaunchedEffect
        saved = false
        delay(settings.autoSave.toLong().coerceIn(250, 3000))
        doSave()
    }
    DisposableEffect(seedKey, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && latestDirty) {
                latestSave(latestTitle, latestBody)
                dirty = false
                saved = true
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            if (latestDirty) latestSave(latestTitle, latestBody)
        }
    }
    BackHandler {
        hideEditorKeyboard()
        doSave()
        onBack()
    }

    fun pushUndo() {
        undo.add(snapshot())
        if (undo.size > 100) undo.removeAt(0)
        redo.clear()
        dirty = true
        saveTick++
    }

    fun replaceSelection(left: String, right: String = "", onlyIfSelection: Boolean = false) {
        val start = min(body.selection.start, body.selection.end).coerceIn(0, body.text.length)
        val end = max(body.selection.start, body.selection.end).coerceIn(0, body.text.length)
        val selected = body.text.substring(start, end)
        if (onlyIfSelection && selected.isEmpty()) return
        pushUndo()
        val insert = if (selected.isEmpty()) "$left$right" else "$left$selected$right"
        val text = body.text.replaceRange(start, end, insert)
        val caret = if (selected.isEmpty()) start + left.length else start + left.length + selected.length + right.length
        body = TextFieldValue(text, TextRange(caret))
    }

    fun insertRaw(text: String) {
        val start = min(body.selection.start, body.selection.end).coerceIn(0, body.text.length)
        val end = max(body.selection.start, body.selection.end).coerceIn(0, body.text.length)
        pushUndo()
        // A section break ends with a fresh empty line. The native TextWatcher
        // only indents when a character is typed later, so the caret sat at
        // column 0 until then. Put the two ideographic spaces into the insert
        // itself, making the caret and the visual line agree immediately.
        val insert = if (settings.indent && text.endsWith('\n')) "$text　　" else text
        val newText = body.text.replaceRange(start, end, insert)
        body = TextFieldValue(newText, TextRange(start + insert.length))
    }

    // ── 校核：选中一段 → 本地规则 + 可选 AI → 逐条采纳 ─────────────────────
    var proofOpen by remember { mutableStateOf(false) }
    var proofIssues by remember { mutableStateOf<List<ProofIssue>>(emptyList()) }
    var proofBusy by remember { mutableStateOf(false) }
    var proofAsked by remember { mutableStateOf(false) }
    var proofError by remember { mutableStateOf<String?>(null) }
    var proofText by remember { mutableStateOf("") }
    var proofStatus by remember { mutableStateOf<String?>(null) }
    /** The passage the panel was opened for; closing puts the selection back. */
    var proofSelection by remember { mutableStateOf(0 to 0) }
    /** 浮窗此刻承载哪件事：校核（本地规则 + 可选 AI）还是修饰（只出顺句版本）。 */
    var polishMode by remember { mutableStateOf(false) }
    /** 校核打开后自动发一次请求（本地规则已停用，改成 AI 全量查）。 */
    var proofAutoAsk by remember { mutableStateOf(false) }

    // 3.26.32 选段「问」：右侧浮窗里的对话，回答可替换回选中那一段。
    // 只在内存；替换前写 ChapterHistory 快照。
    var askOpen by remember { mutableStateOf(false) }
    var askTurns by remember { mutableStateOf<List<AskTurn>>(emptyList()) }
    var askInput by remember { mutableStateOf("") }
    var askBusy by remember { mutableStateOf(false) }
    var askError by remember { mutableStateOf<String?>(null) }
    var askPreview by remember { mutableStateOf("") }
    var askRange by remember { mutableStateOf(0 to 0) }
    var askExpanded by remember { mutableStateOf(false) }
    var askSeq by remember { mutableIntStateOf(0) }

    fun startProof() {
        val a = min(body.selection.start, body.selection.end).coerceIn(0, body.text.length)
        val b = max(body.selection.start, body.selection.end).coerceIn(0, body.text.length)
        if (b - a < 4) {
            ToastBus.show("先选中一段正文再校核")
            return
        }
        // 2026-11-15 用户定：校核也不限字数。`askAiProof` 本来就按 1500 字切段、
        // 逐段累加、每段自带 quote，所以整章直接进来就行，不需要在门口拦。
        val text = body.text.substring(a, b)
        proofText = text
        proofSelection = a to b
        polishMode = false
        // Two right-side panels never stack: the notes window gives way, and the
        // body keeps its scroll position while the writing column re-wraps.
        floatNotePanel = false
        askOpen = false
        bodyEditor?.captureScrollAnchor()
        // 本地规则不再跑（2026-11-14 用户定：本地一条都不命中，等于没用，全交给 AI）。
        proofIssues = emptyList()
        proofBusy = false
        proofAsked = false
        proofError = null
        proofStatus = null
        proofOpen = true
        bodyEditor?.collapseSelection()
        proofAutoAsk = true
    }

    fun askAiProof() {
        val key = AiKey.load(context)
        if (!settings.aiEnabled || key.isNullOrBlank()) {
            proofError = "还没配置 AI：设置 → AI 与记忆"
            return
        }
        proofBusy = true
        proofError = null
        proofAsked = true
        scope.launch {
            val parts = Proofing.chunks(proofText)
            // 3.26.23: names and setting facts now come from the story bible (the AI memory layer).
            val proofBook = proofBookId?.let { store.book(it) }
            val names = proofBook?.bible?.characters
                ?.map { it.name.trim() }
                ?.filter { it.length in 2..6 }
                ?.distinct()
                .orEmpty()
            val bibleFacts = proofBook?.let { AiBible.summary(it) }?.take(2400).orEmpty()
            var foundAny = false
            for ((index, part) in parts.withIndex()) {
                proofStatus = if (parts.size == 1) null else "正在读第 " + (index + 1) + "/" + parts.size + " 段…"
                val (sys, usr) = Proofing.prompt(part, names, bibleFacts = bibleFacts)
                var r = AiClient.chat(
                    baseUrl = settings.aiBaseUrl,
                    model = settings.aiModel,
                    key = key,
                    temperature = .2f,
                    system = sys,
                    user = usr,
                    maxTokens = 3000,
                    disableThinking = true,
                )
                // 一段三千字时，思考模型会把 3000 token 全花在 reasoning 上，
                // content 为空（和问书 3.11.16 同一个坑）。空回答再给一次大预算。
                if (!r.ok && r.error?.contains("空内容") == true) {
                    r = AiClient.chat(
                        baseUrl = settings.aiBaseUrl,
                        model = settings.aiModel,
                        key = key,
                        temperature = .2f,
                        system = sys,
                        user = usr,
                        maxTokens = 8000,
                        disableThinking = true,
                    )
                }
                if (r.ok) {
                    val found = Proofing.parse(r.text)
                    if (found.isNotEmpty()) {
                        proofIssues = (proofIssues + found).distinctBy { it.quote + "\u0000" + it.suggestion }
                        foundAny = true
                    }
                } else {
                    proofError = r.error ?: "没拿到结果，再试一次"
                    break
                }
            }
            proofBusy = false
            proofStatus = null
            if (!foundAny && proofError == null) ToastBus.show("AI 没挑出新的问题")
        }
    }

    /**
     * 修饰的 AI 请求：按块切，一块一次请求、一块一张卡。
     *
     * 2026-11-14 v3.20.0：和校核同一条流水线（分段读、逐条出卡），但每张卡的
     * quote 是这一块的原文，采纳只替换它自己那块——所以不再需要 600 字上限。
     */
    fun askPolish() {
        val key = AiKey.load(context)
        if (!settings.aiEnabled || key.isNullOrBlank()) {
            proofError = "还没配置 AI：设置 → AI 与记忆"
            return
        }
        if (proofBusy) return
        proofBusy = true
        proofError = null
        proofAsked = true
        val base = proofSelection.first
        val source = proofText
        val parts = Polishing.chunks(source)
        scope.launch {
            var any = false
            for ((index, part) in parts.withIndex()) {
                proofStatus = if (parts.size == 1) null
                    else "正在改第 " + (index + 1) + "/" + parts.size + " 段…"
                val (sys, usr) = Polishing.prompt(
                    part.text,
                    previousTail = if (index > 0) parts[index - 1].text.takeLast(120) else "",
                )
                var r = AiClient.chat(
                    baseUrl = settings.aiBaseUrl,
                    model = settings.aiModel,
                    key = key,
                    temperature = .3f,
                    system = sys,
                    user = usr,
                    maxTokens = 4000,
                    disableThinking = true,
                )
                // 和校核同一个坑：思考模型把预算吃空时 content 为空，再给一次大预算。
                if (!r.ok && r.error?.contains("空内容") == true) {
                    r = AiClient.chat(
                        baseUrl = settings.aiBaseUrl,
                        model = settings.aiModel,
                        key = key,
                        temperature = .3f,
                        system = sys,
                        user = usr,
                        maxTokens = 8000,
                        disableThinking = true,
                    )
                }
                if (r.ok) {
                    val found = Polishing.parse(r.text, part.text, settings.indent).map {
                        it.copy(
                            start = base + part.start,
                            end = base + part.start + part.text.length,
                        )
                    }
                    if (found.isNotEmpty()) {
                        proofIssues = proofIssues + found
                        any = true
                    }
                } else {
                    // 单块失败就停在这块：已出的卡留着，作者可以先采纳。
                    proofError = r.error ?: "没拿到结果，再试一次"
                    break
                }
            }
            proofBusy = false
            proofStatus = null
            if (!any && proofError == null) ToastBus.show("这段读着已经顺了")
        }
    }

    /** 修饰：同一段选区、同一套右侧浮窗，只是换成「顺句」的版本。
     *  2026-11-14 v3.20.0：不再限字数——按块切、一块一张卡，采纳只换那一块。 */
    fun startPolish() {
        val a = min(body.selection.start, body.selection.end).coerceIn(0, body.text.length)
        val b = max(body.selection.start, body.selection.end).coerceIn(0, body.text.length)
        if (b - a < 4) {
            ToastBus.show("先选中一段正文再修饰")
            return
        }
        val text = body.text.substring(a, b)
        proofText = text
        proofSelection = a to b
        polishMode = true
        // 两块右侧浮窗不叠：打开修饰先让笔记窗退场，正文保持滚动位置。
        floatNotePanel = false
        askOpen = false
        bodyEditor?.captureScrollAnchor()
        proofIssues = emptyList()
        proofBusy = false
        proofAsked = false
        proofError = null
        proofStatus = null
        proofOpen = true
        bodyEditor?.collapseSelection()
        askPolish()
    }

    fun acceptProof(issue: ProofIssue) {
        // 修饰：一块一张卡，采纳只替换它自己那块。
        // 位置优先（同一段出现两次时才分得清）；正文已被前一张卡改过 → 位置旧了，
        // 退回「全文唯一匹配」；仍不确定就跳过，绝不猜着改。
        if (polishMode) {
            val src = body.text
            var at = -1
            if (issue.end > issue.start && issue.end <= src.length &&
                src.substring(issue.start, issue.end) == issue.quote
            ) {
                at = issue.start
            } else {
                val first = src.indexOf(issue.quote)
                if (first >= 0 && src.indexOf(issue.quote, first + 1) < 0) at = first
            }
            if (at < 0) {
                proofIssues = proofIssues - issue
                ToastBus.show("正文里找不到这一块了，已跳过")
                return
            }
            val editor = bodyEditor
            if (editor != null) {
                editor.replaceKeepingViewport(at, at + issue.quote.length, issue.suggestion)
            } else {
                pushUndo()
                body = body.copy(text = src.replaceRange(at, at + issue.quote.length, issue.suggestion))
            }
            // 浮窗留着：作者可以接着采纳下一块。全采纳完空态会说「没找到可以加强的描写。」。
            proofIssues = proofIssues - issue
            return
        }
        val src = body.text
        val at = src.indexOf(issue.quote)
        if (at < 0) {
            proofIssues = proofIssues - issue
            ToastBus.show("正文里找不到这句了，已跳过")
            return
        }
        val editor = bodyEditor
        if (editor != null) {
            // The native watcher records the undo entry, so 撤销 restores this.
            editor.replaceKeepingViewport(at, at + issue.quote.length, issue.suggestion)
        } else {
            pushUndo()
            body = body.copy(text = src.replaceRange(at, at + issue.quote.length, issue.suggestion))
        }
        proofIssues = proofIssues - issue
    }

    /** 关闭校核：右侧浮窗收起，选区回到打开校核时那一段。 */
    fun closeProof() {
        proofOpen = false
        val (a, b) = proofSelection
        if (b > a && b <= body.text.length) {
            body = body.copy(selection = TextRange(a, b))
            val ed = bodyEditor
            ed?.post { runCatching { ed.setSelection(a, b) } }
        }
    }

    /** 3.26.32：打开「问」——带着选中的那一段进浮窗。 */
    fun startAsk() {
        val a = min(body.selection.start, body.selection.end).coerceIn(0, body.text.length)
        val b = max(body.selection.start, body.selection.end).coerceIn(0, body.text.length)
        if (b - a < 2) {
            ToastBus.show("先选中一段正文再问")
            return
        }
        askPreview = body.text.substring(a, b)
        askRange = a to b
        askTurns = emptyList()
        askInput = ""
        askError = null
        askBusy = false
        askExpanded = false
        // 三块右侧浮窗不叠：开「问」先让校核和笔记窗退场。
        proofOpen = false
        floatNotePanel = false
        bodyEditor?.captureScrollAnchor()
        bodyEditor?.collapseSelection()
        body = body.copy(selection = TextRange(a, a))
        askOpen = true
    }

    /** 关「问」：选区放回这一段。 */
    fun closeAsk() {
        askOpen = false
        val (a, b) = askRange
        if (b > a && b <= body.text.length) {
            body = body.copy(selection = TextRange(a, b))
            val ed = bodyEditor
            ed?.post { runCatching { ed.setSelection(a, b) } }
        }
    }

    /** 发一条：上下文只带这一段 + 前后 200 字 + 章名 + 圣经摘要，不跑工具。 */
    fun sendAsk() {
        val q = askInput.trim()
        if (q.isEmpty() || askBusy) return
        if (askTurns.count { it.mine } >= 6) {
            ToastBus.show("这一段已经问了 6 次，换一段再问吧")
            return
        }
        val key = AiKey.load(context)
        if (!settings.aiEnabled || key.isNullOrBlank()) {
            askError = "还没配置 AI：设置 → 问书 AI"
            return
        }
        val (a, b) = askRange
        val book = proofBookId?.let { store.book(it) }
        val facts = book?.let { AiBible.summary(it) }?.take(2000).orEmpty()
        val before = body.text.substring(max(0, a - 200), a.coerceIn(0, body.text.length))
        val history = askTurns.joinToString("\n") { (if (it.mine) "作者：" else "AI：") + it.text }
        val user = buildString {
            append("【章节】").append(title.ifBlank { "未命名" }).append('\n')
            if (facts.isNotBlank()) append("【设定】").append(facts).append('\n')
            if (before.isNotBlank()) append("【紧接在上文】").append(before).append('\n')
            append("【选中】\n").append(askPreview).append('\n')
            if (history.isNotBlank()) append("【对话】\n").append(history).append('\n')
            append("作者：").append(q)
        }
        askInput = ""
        askError = null
        askBusy = true
        askSeq += 1
        askTurns = askTurns + AskTurn("q" + askSeq, true, q)
        scope.launch {
            val r = AiClient.chat(
                baseUrl = settings.aiBaseUrl,
                model = settings.aiModel,
                key = key,
                temperature = .6f,
                system = ASK_SYSTEM,
                user = user,
                maxTokens = 1600,
                disableThinking = false,
            )
            askBusy = false
            if (r.ok && r.text.isNotBlank()) {
                askSeq += 1
                askTurns = askTurns + AskTurn("q" + askSeq, false, r.text.trim())
            } else {
                askError = r.error ?: "没拿到回答，再试一次"
            }
        }
    }

    /**
     * 把一条回答替换回选中那一段。
     *
     * 乐观锁：替换瞬间原文必须还等于打开时的那段，否则作废（期间作者手改过）。
     * 替换前写一份 ChapterHistory 快照；撤销由正文自己的 undo 栈负责。
     */
    fun applyAsk(turn: AskTurn) {
        if (!settings.aiEnabled) return
        if (settings.aiWriteMode == 0) {
            ToastBus.show("只读档：不改正文")
            return
        }
        val (a, b) = askRange
        val src = body.text
        if (a < 0 || a > b || b > src.length || src.substring(a, b) != askPreview) {
            askTurns = askTurns.map { if (it.id == turn.id) it.copy(stale = true) else it }
            ToastBus.show("这段正文已经改过了，这条作废")
            return
        }
        // seedKey 在章节页就是 chapter.id；笔记页不写章节历史。
        if (!isNote && !ChapterHistory.record(context, seedKey, src)) {
            ToastBus.show("没能留底，这次不替换")
            return
        }
        val ed = bodyEditor
        if (ed != null) {
            ed.replaceKeepingViewport(a, b, turn.text)
        } else {
            pushUndo()
            body = body.copy(text = src.replaceRange(a, b, turn.text))
        }
        askPreview = turn.text
        askRange = a to (a + turn.text.length)
        askTurns = askTurns.map { if (it.id == turn.id) it.copy(applied = true) else it }
        ToastBus.show("已替换选中 ${turn.text.length} 字，可以撤销")
    }

    /** 3.26.23：修饰模式下一键采纳本章所有卡片（逐条走 acceptProof，位置判定与撤销都沿用）。 */
    fun acceptAllPolish() {
        val cards = proofIssues.toList()
        if (cards.isEmpty()) return
        scope.launch {
            cards.forEach { issue ->
                acceptProof(issue)
                delay(40)
            }
        }
    }

    /** 3.26.23：整本书对故事圣经做一致性巡检（人名/关系/时间/设定冲突），结果走同一个校核浮窗。 */
    fun askConsistencyCheck() {
        val book = proofBookId?.let { store.book(it) } ?: return
        val key = AiKey.load(context)
        if (!settings.aiEnabled || key.isNullOrBlank()) {
            proofOpen = true
            polishMode = false
            proofError = "还没配置 AI：设置 → 问书 AI"
            return
        }
        hideEditorKeyboard()
        bodyEditor?.setInputLocked(true)
        proofOpen = true
        polishMode = false
        proofIssues = emptyList()
        proofBusy = true
        proofAsked = true
        proofError = null
        proofStatus = "正在通读全书…"
        proofText = "全书"
        scope.launch {
            val system = "你在做整本书的一致性巡检：只找与故事圣经冲突的地方" +
                "（人名、关系、时间先后、已经发生/还没发生、既有设定）。" +
                "只输出 JSON 数组，每项 {\"quote\":\"正文原句\",\"suggestion\":\"改成什么\"," +
                "\"reason\":\"依据故事圣经里的哪条设定或事件\",\"level\":\"设定\"}。" +
                "没有冲突就输出 []。不要改文风，不要挑错别字，不要解释。"
            var r = AiClient.chat(
                baseUrl = settings.aiBaseUrl,
                model = settings.aiModel,
                key = key,
                temperature = .1f,
                system = system,
                user = AiBible.consistencyPrompt(book, 10000),
                maxTokens = 4000,
                disableThinking = true,
            )
            if (!r.ok && r.error?.contains("空内容") == true) {
                r = AiClient.chat(
                    baseUrl = settings.aiBaseUrl,
                    model = settings.aiModel,
                    key = key,
                    temperature = .1f,
                    system = system,
                    user = AiBible.consistencyPrompt(book, 10000),
                    maxTokens = 8000,
                    disableThinking = true,
                )
            }
            if (r.ok) {
                proofIssues = Proofing.parse(r.text)
                if (proofIssues.isEmpty()) ToastBus.show("没发现和故事圣经冲突的地方")
            } else {
                proofError = r.error ?: "没拿到结果，再试一次"
            }
            proofBusy = false
            proofStatus = null
            bodyEditor?.setInputLocked(false)
        }
    }

    // 系统返回先收校核浮窗，而不是直接离开这一章。
    BackHandler(enabled = proofOpen) { closeProof() }

    /** 点建议卡：正文滚到那句，并短暂标亮它。 */
    fun locateProof(issue: ProofIssue) {
        val src = body.text
        val at = src.indexOf(issue.quote)
        if (at < 0) {
            ToastBus.show("正文里找不到这句了")
            return
        }
        // 折叠光标要跟着挪到这一句：滚动会触发重组，AndroidView.update 会把
        // Compose 里的选区写回 EditText；光标还在原处时 TextView 又会把页面
        // 拉回光标（v3.13.0 的「先跳后拉回」）。用折叠光标而不是选区，
        // 这样底部保持工具行，不会跳成框选行。
        body = body.copy(selection = TextRange(at))
        val ed = bodyEditor ?: return
        ed.post {
            ed.setSelection(at)
            ed.highlightRange(at, at + issue.quote.length)
            val layout = ed.layout ?: return@post
            val line = layout.getLineForOffset(at)
            val y = layout.getLineTop(line)
            ed.scrollTo(0, (y - ed.height / 3).coerceAtLeast(0))
        }
    }

    fun findTodoLineStart(todo: Todo): Int {
        val text = body.text
        if (todo.start in 0 until text.length &&
            (text[todo.start] == '☐' || text[todo.start] == '☑')
        ) return todo.start
        var start = 0
        while (start < text.length) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            if (start < end && (text[start] == '☐' || text[start] == '☑')) {
                val bodyText = text.substring((start + 2).coerceAtMost(end), end).trim()
                if (bodyText == todo.text.trim()) return start
            }
            start = end + 1
        }
        return -1
    }

    var reminderPermissionAsked by remember { mutableStateOf(false) }
    val reminderPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) ToastBus.show("通知权限未开启，提醒不会弹出")
    }
    fun ensureReminderPermission() {
        if (!ReminderScheduler.canPost(context) && !reminderPermissionAsked) {
            reminderPermissionAsked = true
            reminderPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun setTodoReminder(todoId: String, at: Long?) {
        val todo = inlineTodos.firstOrNull { it.id == todoId } ?: return
        val text = body.text
        val start = findTodoLineStart(todo)
        if (start < 0) return
        val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        val nextStart = if (lineEnd < text.length) lineEnd + 1 else text.length
        val nextEnd = if (nextStart < text.length) {
            text.indexOf('\n', nextStart).let { if (it < 0) text.length else it }
        } else nextStart
        val nextLine = if (nextEnd > nextStart) text.substring(nextStart, nextEnd) else ""
        val hasReminderLine = isReminderLine(nextLine)
        // One (from, to, insert) edit per case. The change is applied to the live
        // editor in place: rebuilding the whole body through `setText` re-laid the
        // page out and dragged the viewport to the caret.
        var from = -1
        var to = -1
        var insert = ""

        if (at == null) {
            if (hasReminderLine) {
                from = if (nextStart > 0 && text[nextStart - 1] == '\n') nextStart - 1 else nextStart
                to = if (nextEnd < text.length && text[nextEnd] == '\n') nextEnd + 1 else nextEnd
            }
        } else {
            val ldt = java.time.Instant.ofEpochMilli(at)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            val line = "$REMINDER_LINE_MARK ${ldt.monthValue}月${ldt.dayOfMonth}日 " +
                String.format(java.util.Locale.US, "%02d:%02d:%02d", ldt.hour, ldt.minute, ldt.second)
            if (hasReminderLine) {
                from = nextStart
                to = nextEnd
                insert = line
            } else {
                from = lineEnd
                to = lineEnd
                insert = "\n$line"
            }
        }

        if (at != null) ensureReminderPermission()
        val editor = bodyEditor
        if (editor != null && from >= 0) {
            // The edit itself records the undo entry through onBodyChange, so the
            // snapshot still holds the pre-change body and todos.
            editor.replaceKeepingViewport(from, to, insert)
            inlineTodos = inlineTodos.map {
                if (it.id == todoId) it.copy(remindAt = at, notified = false) else it
            }
            onTodosChange?.invoke(inlineTodos)
            // The body text changed in place, so the Compose update block skips its
            // own span pass; restyle the new/removed reminder line here.
            editor.applyTodoSpans(inlineTodos)
            return
        }

        // Fallback for a body that is not attached yet: rebuild the text through
        // the Compose mirror.
        var newText = text
        var caret = lineEnd
        if (at == null) {
            if (hasReminderLine) {
                newText = text.replaceRange(from, to, "")
                caret = from
            }
        } else {
            newText = text.replaceRange(from, to, insert)
            caret = from + insert.length
        }
        pushUndo()
        body = TextFieldValue(newText, TextRange(caret.coerceIn(0, newText.length)))
        inlineTodos = inlineTodos.map {
            if (it.id == todoId) it.copy(remindAt = at, notified = false) else it
        }
        onTodosChange?.invoke(inlineTodos)
        dirty = true
        saveTick++
    }

    fun openReminderEditor(todoId: String) {
        editingReminderId = todoId
    }

    fun closeTodoPanel() {
        todoPanel = null
        bodyEditor?.setPanelInset(0)
    }

    fun insertTodoLine() {
        val start = min(body.selection.start, body.selection.end).coerceIn(0, body.text.length)
        val source = body.text
        val lineStart = if (start == 0) 0 else source.lastIndexOf('\n', start - 1) + 1
        val lineEnd = source.indexOf('\n', lineStart).let { if (it < 0) source.length else it }
        val line = source.substring(lineStart, lineEnd)
        val todoStart: Int
        val newText: String
        if (line.isBlank()) {
            // A blank/indented line becomes the todo itself; the indent is discarded.
            todoStart = lineStart
            newText = source.substring(0, lineStart) + "☐ " + source.substring(lineEnd)
        } else {
            // Existing prose stays intact; the todo starts on a fresh, unindented line.
            val insertAt = lineEnd
            todoStart = insertAt + 1
            newText = source.substring(0, insertAt) + "\n☐ " + source.substring(insertAt)
        }
        val todo = Todo(
            text = "",
            start = todoStart,
            end = todoStart + 2,
        )
        pushUndo()
        inlineTodos = inlineTodos + todo
        body = TextFieldValue(newText, TextRange(todoStart + 2))
        dirty = true
        saveTick++
        onTodosChange?.invoke(inlineTodos)
        bodyEditor?.requestFocus()
        bodyEditor?.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(bodyEditor, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun indentCurrentLine() {
        val caret = body.selection.start.coerceIn(0, body.text.length)
        val lineStart = if (caret == 0) 0 else body.text.lastIndexOf('\n', caret - 1) + 1
        if (body.text.startsWith("　　", lineStart)) return
        pushUndo()
        val newText = body.text.substring(0, lineStart) + "　　" + body.text.substring(lineStart)
        body = TextFieldValue(newText, TextRange((caret + 2).coerceAtMost(newText.length)))
    }

    fun onBodyChange(value: TextFieldValue) {
        if (value.text == body.text) {
            body = value
            return
        }
        pushUndo()
        var adjusted = value
        if (settings.indent && value.text.length == body.text.length + 1) {
            val caret = value.selection.start
            if (caret > 0 && value.text.getOrNull(caret - 1) == '\n') {
                val withIndent = value.text.substring(0, caret) + "　　" + value.text.substring(caret)
                adjusted = value.copy(text = withIndent, selection = TextRange(caret + 2))
            }
        }
        body = adjusted
    }

    fun undoAction() {
        if (undo.isEmpty()) return
        val prev = undo.removeAt(undo.lastIndex)
        redo.add(snapshot())
        title = prev.title
        body = prev.body
        inlineTodos = prev.todos
        onTodosChange?.invoke(prev.todos)
        dirty = true
        saveTick++
    }

    fun redoAction() {
        if (redo.isEmpty()) return
        val next = redo.removeAt(redo.lastIndex)
        undo.add(snapshot())
        title = next.title
        body = next.body
        inlineTodos = next.todos
        onTodosChange?.invoke(next.todos)
        dirty = true
        saveTick++
    }

    val density = LocalDensity.current
    val popupLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val imePx = WindowInsets.ime.getBottom(density)
    val imeNowState = rememberUpdatedState(imePx)
    // Tapping the body opens the IME and shortens the page. Feed the view both
    // the current and the last full IME height so the tap can reserve the
    // keyboard's space up front and land in one move.
    val imeOpen = imePx > 0
    SideEffect {
        val editor = bodyEditor ?: return@SideEffect
        editor.imeInsetPx = imePx
        // Remember the tallest IME seen, not the value of the current frame:
        // during the slide-in animation imePx grows every frame, so assigning
        // it directly kept lastIme == ime and the reservation at zero.
        if (imePx > editor.lastImePx) editor.lastImePx = imePx
    }
    LaunchedEffect(imeOpen) {
        if (imeOpen) {
            bodyEditor?.ensureCaretVisible()
        }
    }
    val bodySize = (17f * settings.fontScale).coerceIn(12f, 28f)
    LaunchedEffect(settings.paperStyle, settings.lineHeight, settings.fontScale, settings.paperLineAlpha) {
        bodyEditor?.let { editor ->
            editor.configureLineGrid(bodySize, settings.lineHeight)
            editor.setPaperStyle(
                settings.paperStyle,
                editor.paint.textSize * settings.lineHeight,
                settings.paperLineAlpha,
            )
        }
    }
    val words = countWords(body.text)
    var selectionMenu by remember { mutableStateOf<SelectionBar?>(null) }
    var selectionGeometry by remember { mutableStateOf<SelectionGeometry?>(null) }
    val selectionColors = TextSelectionColors(
        handleColor = ui.accent.copy(alpha = .58f),
        backgroundColor = ui.accent.copy(alpha = .20f),
    )

    var editorRootOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val editReminderTodo = inlineTodos.firstOrNull { it.id == editingReminderId }
    var reminderOverlayTodo by remember { mutableStateOf<Todo?>(null) }
    LaunchedEffect(editReminderTodo) {
        if (editReminderTodo != null) reminderOverlayTodo = editReminderTodo
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(p.paper)
            .onGloballyPositioned { editorRootOrigin = it.positionInWindow() }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // 正文铺满到屏幕底（含手势条区域）：只让 IME 顶起页面。
                // dock 内容自己吃 navigationBars，所以工具行不会压到小白条。
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            EditorTopBar(
                // 笔记页：上面一行笔记夹，下面一行笔记名，跟着标题框实时变。
                heading = if (isNote) title.ifBlank { "无标题笔记" } else heading,
                crumb = if (isNote) noteFolderName else "",
                chips = chapterTags,
                words = words,
                saved = saved,
                showWords = settings.showWordCount,
                indentOn = settings.indent,
                onToggleIndent = {
                    val on = !settings.indent
                    store.updateSettings { it.copy(indent = on) }
                    if (on) indentCurrentLine()
                    ToastBus.show(if (on) "已开启首行自动缩进" else "已关闭首行自动缩进")
                },
                onBack = {
                    hideEditorKeyboard()
                    doSave()
                    onBack()
                },
                onMore = {
                    doSave()
                    onMore()
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxSize()
            ) {
            Column(Modifier.weight(1f)) {
                BasicTextField(
                    value = title,
                    onValueChange = {
                        if (!inputBlocked && it.length <= 80) {
                            title = it
                            dirty = true
                            saved = false
                            saveTick++
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = settings.pageMargin.dp)
                        // 2026-11-15：整体往上贴——上留白 14→10dp，顶部呼吸靠顶栏本身。
                        .padding(top = 10.dp, bottom = 2.dp)
                        // 2026-11-16 用户定：回到「只做视觉缩放」这条老路——
                        // 布局高度**不动**，标题只按收起比例缩小；让出的空间由下面的
                        // 正文整块上移来吃（见正文区的 graphicsLayer）。
                        // 试过的两条岔路都作废：
                        //   ① 改布局高度真变矮 → 会改正文容器高度 → 原生 EditText 重排 → 滚动被重置；
                        //   ② 不动正文只补滚动上限 → 位移已经发生，补不出来。
                        .graphicsLayer {
                            val s = 1f - .54f * chapterCollapse
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .onGloballyPositioned { titleBoxH = it.size.height }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                // The body is a native EditText with its own
                                // focus and selection overlay. End that state
                                // before Compose's title field takes the tap.
                                collapseBodySelection()
                            }
                        }
                        .onFocusChanged { state ->
                            titleFocused = state.isFocused
                            if (state.isFocused) {
                                // The native body owns a separate selection
                                // overlay. Tapping the title must end that
                                // selection before the title keyboard opens.
                                collapseBodySelection()
                            }
                        },
                    textStyle = TextStyle(
                        // 颜色跟着收起比例走；字号不变（缩放交给图层，避免重排）。
                        color = lerp(p.ink, ui.accent, chapterCollapse),
                        fontSize = (bodySize * 1.55f).coerceIn(21f, 38f).sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = fontFamilyFor(settings.font),
                        lineHeight = (bodySize * 1.9f).sp,
                        letterSpacing = .4.sp,
                    ),
                    cursorBrush = SolidColor(ui.accent),
                    singleLine = false,
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    decorationBox = { inner ->
                        Box {
                            if (title.isBlank()) {
                                Txt(
                                    if (isNote) "无标题笔记" else "未命名章节",
                                    size = (bodySize * 1.55f).coerceIn(21f, 38f),
                                    color = p.muted.copy(alpha = .38f),
                                    weight = FontWeight.SemiBold,
                                    fontFamily = fontFamilyFor(settings.font),
                                )
                            }
                            inner()
                        }
                    },
                )
                // 2026-11-15：章节链接 chip 移到顶栏右侧按钮下方（用户定），
                // 不再占正文上方这一行。
                // Native EditText is the single scroll/caret/IME owner. Compose's
                // BasicTextField nested inside a custom viewport caused two scrollers
                // and a caret that could stop at the dock edge. Android's editor has
                // mature bring-into-view behavior for IME resizes; the Compose layer
                // only styles it and mirrors the text/selection into AppStore.
                val syncing = remember { mutableStateOf(false) }
                // The native view is created once. Without this, its TextWatcher
                // kept the first onBodyChange lambda, so toggling indent (and other
                // settings) did not affect subsequent newlines.
                val latestBodyChange = rememberUpdatedState<(TextFieldValue) -> Unit> { value ->
                    onBodyChange(value)
                }
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // 2026-11-16 用户定的终版（A）：章名保留缩小动画，正文**一步都不动**。
                        // 于是章名和正文之间会留出一条空白（标题占位不变、视觉缩小让出的那块），
                        // 用户明确接受这个留白，换底部绝对稳定。
                        // 作废的三种：翻译位移（底部被抬）、标题区真变矮（EditText 重排弹跳）、
                        // 正文 layout 向上长（可视范围确实变大，但溢出量与标题收缩量对不齐，
                        // 正文压到章名上）。
                ) {
                AndroidView(
                    factory = { ctx ->
                        BodyEditText(
                            context = ctx,
                            onSelectionChanged = { text, s, e ->
                                if (!syncing.value) {
                                    latestBodyChange.value(TextFieldValue(text, TextRange(s, e)))
                                }
                            },
                            onSelectionMenu = { x1, y1, x2, y2 ->
                                selectionMenu = if (y2 > y1) SelectionBar(x1, y1, x2, y2) else null
                            },
                            onGeometry = { selectionGeometry = it },
                            onTodoToggle = { id ->
                                val updated = latestTodos.map {
                                    if (it.id == id) it.copy(done = !it.done) else it
                                }
                                inlineTodos = updated
                                latestTodosChange?.invoke(updated)
                                dirty = true
                                saveTick++
                            },
                            onTodoCursor = { id -> activeTodoId = id },
                            onReminderTap = { id -> openReminderEditor(id) },
                            onTodoDeleted = { id ->
                                val updated = latestTodos.filterNot { it.id == id }
                                inlineTodos = updated
                                onTodosChange?.invoke(updated)
                                if (editingReminderId == id) editingReminderId = null
                                dirty = true
                                saveTick++
                            },
                            // No todo-pull handler in the note editor. Passing an
                            // empty lambda here made every downward drag on a todo
                            // paragraph get consumed (see ACTION_MOVE) with no effect.
                            onTodoSwipe = null,
                            onBodyScroll = {
                                bodyScrollTick.intValue++
                                val y = bodyEditor?.scrollY ?: 0
                                if (y != bodyScrollY) bodyScrollY = y
                            },
                        ).apply {
                            onProofRequest = { startProof() }
                            background = null
                            val side = maxOf(
                                with(density) { settings.pageMargin.dp.toPx() },
                                (selectHandleWidth.div(2f)) +
                                    with(density) { 4.dp.toPx() },
                            )
                            // Text must be able to draw under the dock so the glass can sample it.
                            // The last line still stops above the dock because contentMaxScroll()
                            // adds dockBottomInset back to the scroll range.
                            // 底部让位只给**能力栏实际占位**：收起时是 0，展开时才是整块。
                            // 用未加权的 dockHeightPx 会让 dock 收起后正文底部仍留一块空白。
                            baseDockInset = panelBottomInset +
                                (dockHeightPx * abilityT.value.coerceIn(0f, 1f)).toInt() +
                                with(density) { 6.dp.toPx() }.toInt()
                            setPadding(side.toInt(), 0, side.toInt(), 0)
                            gravity = Gravity.TOP or Gravity.START
                            inputType = InputType.TYPE_CLASS_TEXT or
                                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                            setSingleLine(false)
                            maxLines = Int.MAX_VALUE
                            // Force the IME's enter key to insert a newline
                            // instead of an action (Send/Search/Done).
                            imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
                            overScrollMode = View.OVER_SCROLL_NEVER
                            isVerticalScrollBarEnabled = false
                            // Keep the editable movement method so long-press selection
                            // and the copy/paste ActionMode still work. Fling is handled
                            // by BodyEditText's own VelocityTracker/OverScroller below.
                            movementMethod = ArrowKeyMovementMethod.getInstance()
                            setText(body.text)
                            configureTodoStyle(
                                accent = ui.accent.toArgb(),
                                ink = p.ink.toArgb(),
                                muted = p.muted.toArgb(),
                                paper = p.paper.toArgb(),
                                haptics = settings.haptics,
                            )
                            applyTodoSpans(inlineTodos)
                            setSelection(body.selection.start.coerceIn(0, text.length))
                            setHint(placeholder)
                            setHintTextColor(p.muted.copy(alpha = .42f).toArgb())
                            setTextColor(p.ink.toArgb())
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, bodySize)
                            typeface = if (settings.font == "serif") Typeface.SERIF else Typeface.SANS_SERIF
                            configureLineGrid(bodySize, settings.lineHeight)
                            setPaperStyle(
                                settings.paperStyle,
                                paint.textSize * settings.lineHeight,
                                settings.paperLineAlpha,
                            )
                            val cursorStep = paint.textSize * settings.lineHeight
                            val cursorFm = paint.fontMetrics
                            val cursorGap = ((cursorStep - (cursorFm.descent - cursorFm.ascent)) * .35f)
                                .coerceIn(2f * density.density, 6f * density.density)
                            applyAccentCursor(
                                color = ui.accent.toArgb(),
                                widthPx = with(density) { 2.5.dp.toPx() }.toInt(),
                                heightPx = (cursorStep * .75f).toInt(),
                                bottomInset = (cursorStep - (cursorFm.descent + cursorGap))
                                    .toInt().coerceAtLeast(0),
                            )
                            val self = this
                            self.autoIndent = settings.indent
                            self.lastTextLength = text?.length ?: 0
                            addTextChangedListener(object : TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                    if (count == 1 && s?.getOrNull(start) == '\n') {
                                        self.pendingNewline = true
                                    }
                                }
                                override fun afterTextChanged(s: Editable?) {
                                    self.renderRevision++
                                    bodyScrollTick.intValue++
                                    if (syncing.value) return
                                    if (s == null) return
                                    val caret = selectionStart
                                    val lineStart = if (caret == 0) 0 else {
                                        var i = caret - 1
                                        while (i >= 0 && s.getOrNull(i) != '\n') i--
                                        i + 1
                                    }
                                    var publishedSelection = selectionStart.coerceAtLeast(0)
                                    val canIndent = !self.indentGuard && self.autoIndent &&
                                        caret == selectionEnd &&
                                        !self.isTodoLineAt(s, lineStart) &&
                                        !self.isReminderLineAt(s, lineStart) &&
                                        !s.startsWith("　　", lineStart)
                                    if (canIndent && self.pendingNewline && caret == lineStart) {
                                        // Soft keyboards insert '\n' directly and
                                        // do not go through onKeyDown. Indent the
                                        // empty new line now, so the caret is
                                        // already after the two spaces; do not
                                        // wait for the first typed character.
                                        self.indentGuard = true
                                        s.insert(lineStart, "　　")
                                        publishedSelection = (caret + 2).coerceAtMost(s.length)
                                        setSelection(publishedSelection)
                                        self.indentGuard = false
                                    } else if (canIndent && caret > lineStart &&
                                        (lineStart == 0 || s.getOrNull(lineStart - 1) == '\n') &&
                                        s.substring(lineStart, caret).isNotBlank()
                                    ) {
                                        self.indentGuard = true
                                        s.insert(lineStart, "　　")
                                        publishedSelection = (caret + 2).coerceAtMost(s.length)
                                        setSelection(publishedSelection)
                                        self.indentGuard = false
                                    }
                                    self.pendingNewline = false
                                    self.lastTextLength = s.length
                                    self.applyLineHeightSpan()
                                    latestBodyChange.value(
                                        TextFieldValue(
                                            s.toString(),
                                            TextRange(publishedSelection.coerceIn(0, s.length)),
                                        ),
                                    )
                                }
                            })
                        }.also { bodyEditor = it }
                    },
                    update = { et ->
                        if (inputBlocked && et.isFocused) et.clearFocus()
                        et.autoIndent = settings.indent
                        et.configureTodoStyle(
                            accent = ui.accent.toArgb(),
                            ink = p.ink.toArgb(),
                            muted = p.muted.toArgb(),
                            paper = p.paper.toArgb(),
                            haptics = settings.haptics,
                        )
                        if (et.text.toString() != body.text) {
                            syncing.value = true
                            et.setText(body.text)
                            et.lastTextLength = et.text.length
                            et.applyTodoSpans(inlineTodos)
                            et.applyLineHeightSpan()
                            syncing.value = false
                        }
                        val start = body.selection.start.coerceIn(0, et.text.length)
                        val end = body.selection.end.coerceIn(0, et.text.length)
                        if (et.selectionStart != start || et.selectionEnd != end) {
                            syncing.value = true
                            et.setSelection(start, end)
                            syncing.value = false
                        }
                        val side = maxOf(
                            with(density) { settings.pageMargin.dp.toPx() },
                            (et.selectHandleWidth.div(2f)) +
                                with(density) { 4.dp.toPx() },
                        )
                        et.baseDockInset = et.panelBottomInset +
                            (dockHeightPx * abilityT.value.coerceIn(0f, 1f)).toInt() +
                            with(density) { 6.dp.toPx() }.toInt()
                        et.setPadding(side.toInt(), 0, side.toInt(), 0)
                        et.setTextColor(p.ink.toArgb())
                        et.setHintTextColor(p.muted.copy(alpha = .42f).toArgb())
                        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, bodySize)
                        et.configureLineGrid(bodySize, settings.lineHeight)
                        et.setPaperStyle(
                            settings.paperStyle,
                            et.paint.textSize * settings.lineHeight,
                            settings.paperLineAlpha,
                        )
                        val cursorStep = et.paint.textSize * settings.lineHeight
                        val cursorFm = et.paint.fontMetrics
                        val cursorGap = ((cursorStep - (cursorFm.descent - cursorFm.ascent)) * .35f)
                            .coerceIn(2f * density.density, 6f * density.density)
                        et.applyAccentCursor(
                            color = ui.accent.toArgb(),
                            widthPx = with(density) { 2.5.dp.toPx() }.toInt(),
                            heightPx = (cursorStep * .75f).toInt(),
                            bottomInset = (cursorStep - (cursorFm.descent + cursorGap))
                                .toInt().coerceAtLeast(0),
                        )
                    },
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                )
                SelectionOverlay(
                    geometry = selectionGeometry,
                    handleDrawable = bodyEditor?.selectHandleDrawable,
                    color = ui.accent,
                    onTopInWindow = { bodyEditor?.updateOverlayTop(it) },
                )
                AnimatedContent(
                    targetState = todoPanel,
                    transitionSpec = {
                        val shift = with(density) { 10.dp.toPx().toInt() }
                        (
                            fadeIn(tween(160)) + slideInVertically(tween(160)) { -shift }
                        ).togetherWith(
                            fadeOut(tween(160)) + slideOutVertically(tween(160)) { -shift },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "todoPanel",
                ) { panel ->
                    val todo = panel?.let { a -> inlineTodos.firstOrNull { it.id == a.id } }
                    if (panel != null && todo != null) {
                        val screenW = with(density) { maxWidth.toPx() }
                        val margin = with(density) { settings.pageMargin.dp.toPx() }
                        val cardW = minOf(with(density) { 340.dp.toPx() }, screenW - 2 * margin)
                        val x = (screenW - cardW) / 2f
                        val y = panel.bounds.bottom + with(density) { 6.dp.toPx() }
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                        closeTodoPanel()
                                    },
                            )
                            InlineReminderPanel(
                                todo = todo,
                                modifier = Modifier
                                    .offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) }
                                    .width(with(density) { cardW.toDp() }),
                                onDismiss = { closeTodoPanel() },
                                onConfirm = { at ->
                                    val updated = inlineTodos.map {
                                        if (it.id == todo.id) it.copy(remindAt = at, notified = false) else it
                                    }
                                    inlineTodos = updated
                                    onTodosChange?.invoke(updated)
                                    dirty = true
                                    saveTick++
                                    closeTodoPanel()
                                },
                                onDelete = {
                                    val updated = inlineTodos.map {
                                        if (it.id == todo.id) it.copy(remindAt = null, notified = false) else it
                                    }
                                    inlineTodos = updated
                                    onTodosChange?.invoke(updated)
                                    dirty = true
                                    saveTick++
                                    closeTodoPanel()
                                },
                            )
                        }
                    }
                }
            }
            }
            // Side notes: the writing column above gives up exactly this width,
            // so nothing is ever drawn over the text and nothing jumps.
            val sideLinks = noteLinks
            val sideOfNotes = sideLinks != null &&
                sideLinks.floatOn &&
                floatNotePanel &&
                sideLinks.notes.isNotEmpty()
            if (sideLinks != null) {
                NoteSidePanel(
                    links = sideLinks,
                    visible = sideOfNotes,
                    onOpenNote = { note ->
                        floatNotePanel = false
                        sideLinks.onOpenNote(note)
                    },
                    clearEditorFocus = {
                        // A swipe on the panel must never move the body caret.
                        val ed = bodyEditor
                        if (ed != null && ed.hasFocus()) {
                            ed.clearFocus()
                            collapseBodySelection()
                        }
                    },
                    onWidthSettled = {
                        bodyEditor?.releaseScrollAnchor()
                    },
                    onWidthAnimating = { bodyEditor?.captureScrollAnchorIfNone() },
                    onResizeStart = { bodyEditor?.captureScrollAnchor() },
                    // The panel shares this Row with the writing column while the
                    // dock is drawn over the Row's bottom edge, so the panel must
                    // reserve the measured dock height plus a gap. A flat 12dp
                    // padding left the panel's rounded bottom buried under the
                    // toolbar, and the IME only shortened the Row, which kept the
                    // panel cut off instead of riding above the keyboard.
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(bottom = dockReserve + 12.dp),
                )
            }
            // 校核浮窗：和「本章笔记」同一套右侧浮窗（平卡、20 圆角、标题行、
            // 竖排建议卡、可拖宽）。打开时笔记窗让位，关闭把选区放回去。
            // 本地规则已停用：打开即发一次 AI 请求。
            LaunchedEffect(proofAutoAsk, proofOpen) {
                if (proofAutoAsk && proofOpen && !polishMode) {
                    proofAutoAsk = false
                    askAiProof()
                }
            }
            ProofSidePanel(
                visible = proofOpen && !sideOfNotes,
                issues = proofIssues,
                busy = proofBusy,
                status = proofStatus,
                error = proofError,
                asked = proofAsked,
                chars = proofText.length,
                canAsk = settings.aiEnabled,
                // 同一套浮窗承载两件事：校核给问题清单，修饰给顺句版本。
                heading = if (polishMode) "修饰" else "校核",
                askLabel = if (polishMode) "再改一版 · " + proofText.length + " 字"
                    else "重新查一遍 · " + proofText.length + " 字",
                emptyText = if (polishMode) "没找到可以加强的描写。" else "没挑出问题。",
                headingIcon = if (polishMode) Glyph.Sparkles else Glyph.Check,
                onAsk = { if (polishMode) askPolish() else askAiProof() },
                onAccept = { acceptProof(it) },
                onIgnore = { proofIssues = proofIssues - it },
                onLocate = { locateProof(it) },
                onClose = { closeProof() },
                onAcceptAll = if (polishMode) ({ acceptAllPolish() }) else null,
                onConsistencyCheck = { askConsistencyCheck() },
                onWidthAnimating = { bodyEditor?.captureScrollAnchorIfNone() },
                onWidthSettled = { bodyEditor?.releaseScrollAnchor() },
                onResizeStart = { bodyEditor?.captureScrollAnchor() },
                onClearEditorFocus = {
                    val ed = bodyEditor
                    if (ed != null && ed.hasFocus()) {
                        ed.clearFocus()
                        collapseBodySelection()
                    }
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(bottom = dockReserve + 12.dp),
            )
            // 3.26.32 选段「问」：和校核/修饰同一块浮窗、同一套拖宽，
            // 内容换成对话；两块互斥（开一个就关另一个）。
            ProofSidePanel(
                visible = askOpen && !sideOfNotes,
                issues = emptyList(),
                busy = false,
                status = null,
                error = null,
                asked = true,
                chars = askPreview.length,
                canAsk = false,
                onAsk = {},
                onAccept = {},
                onIgnore = {},
                onLocate = {},
                onClose = { closeAsk() },
                heading = "问 · " + askPreview.length + " 字",
                headingIcon = Glyph.Sparkles,
                onClearEditorFocus = {
                    val ed = bodyEditor
                    if (ed != null && ed.hasFocus()) {
                        ed.clearFocus()
                        collapseBodySelection()
                    }
                },
                onWidthAnimating = { bodyEditor?.captureScrollAnchorIfNone() },
                onWidthSettled = { bodyEditor?.releaseScrollAnchor() },
                onResizeStart = { bodyEditor?.captureScrollAnchor() },
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(bottom = dockReserve + 12.dp),
            ) {
                AskChat(
                    preview = askPreview,
                    chars = askPreview.length,
                    expanded = askExpanded,
                    onToggleExpand = { askExpanded = !askExpanded },
                    turns = askTurns,
                    input = askInput,
                    onInput = { askInput = it },
                    busy = askBusy,
                    error = askError,
                    canWrite = settings.aiEnabled && settings.aiWriteMode != 0,
                    onSend = { sendAsk() },
                    onApply = { applyAsk(it) },
                )
            }
            // 3.26.23: floating story bible (same panes as the story page).
            val storyBook = proofBookId?.let { store.book(it) }
            val storyJournal = remember(storyFloat, storyBook?.id, storyJournalTick) {
                if (storyFloat && storyBook != null) {
                    AiBible.recent(context, 12).map { it.optString("label") to it.optLong("time") }
                } else emptyList()
            }
            if (storyFloat && storyBook != null) {
                StoryBibleFloatSheet(
                    book = storyBook,
                    onBible = { store.updateBible(storyBook.id, it) },
                    onDismiss = { storyFloat = false },
                    journal = storyJournal,
                    onUndo = {
                        ToastBus.show(AiBible.undoLast(context, store, storyBook.id))
                        storyJournalTick++
                    },
                )
            }
            }
            // 3.26.41：横滑移光标时的 HUD。挂在能力栏**之前**（先画、垫在栏体下面），
            // 弹出来那一段被栏顶压住，读作从能力栏里挤出来；它只是覆盖层，
            // 不参与测量，所以正文高度和能力栏位置都不动。
            if (scrub.visible && dockHeightPx > 0) {
                // 能力栏收起（< 50%）时立即卸载；展开过程也只在过半后出现。
                if (abilityT.value.coerceIn(0f, 1f) >= .5f) {
                    val hudGap = (with(densityNow) { dockHeightPx.toDp() } - 6.dp)
                        .coerceAtLeast(0.dp)
                    ScrubHudLayer(
                        state = scrub,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = hudGap),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 1型玻璃 only redraws when this node's draw is invalidated.
                    // Reading the scroll tick inside the draw scope subscribes the
                    // draw itself, so every body scroll re-samples the EditText
                    // bitmap instead of freezing the dock on its first frame.
                    .onGloballyPositioned { dockHeightPx = it.size.height }
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        // 收起后整块缩成左下角的球；原区域不保留吞触摸的层，
                        // 否则无法在能力栏位置滑动正文（v3.11.87）。
                        .graphicsLayer {
                            // 同柔展开/收回：整块能力栏从左下角的球里长出来，
                            // 收回时缩回球里。左下锚定，形状跟着缩放，曲率连续。
                            // 落点 = 球本身的大小（38dp）：以前缩到 56dp 的团，
                            // 与 40dp 的球本来就不重合，收尾两帧对不上。
                            val ball = 38.dp.toPx()
                            val sx = ball / maxOf(1f, size.width)
                            val sy = ball / maxOf(1f, size.height)
                            val t = abilityT.value
                            // 粘滞水滴（局部动效档）：两轴走同一条过渡的两个时间点，
                            // 横向先到、纵向滞后 15% —— 中间帧偏扁宽，读作被拉着长出来，
                            // 而不是一个四四方方的缩放。内容仍是整体缩放，不会糊。
                            val tLag = ViscousDrop.lagged(t)
                            scaleX = sx + (1f - sx) * t
                            scaleY = sy + (1f - sy) * tLag
                            alpha = t
                            transformOrigin = TransformOrigin(0f, 1f)
                        },
                ) {
                    EditorDock(
                    canUndo = undo.isNotEmpty(),
                    canRedo = redo.isNotEmpty(),
                    isNote = isNote,
                    pendingTodos = pendingTodos,
                    onUndo = { undoAction() },
                    onRedo = { redoAction() },
                    onIndent = { insertRaw("　　") },
                    onQuote = { replaceSelection("“", "”") },
                    onDivider = { insertRaw(sectionBreakText(settings)) },
                    onType = {
                        hideEditorKeyboard()
                        showType = true
                    },
                    onTodos = {
                        val todoId = activeTodoId
                        if (todoId != null) openReminderEditor(todoId) else insertTodoLine()
                    },
                    todoActive = activeTodoId != null,
                    boundNotes = noteLinks?.notes?.size ?: 0,
                    onNotes = noteLinks?.let { links ->
                        {
                            // One entry, one panel: open (or close) the side notes.
                            // The binding sheet stays behind a long press so the
                            // dock never opens two things at once. Do NOT dismiss
                            // the IME here: the re-wrap anchor needs the caret to
                            // stay active and visible while the width changes.
                            // 校核浮窗和笔记浮窗共用右侧，打开笔记先收起校核。
                            if (proofOpen) proofOpen = false
                            // Capture before the row is re-laid out: the first
                            // animation frame already re-wraps the body, and
                            // TextView's own bring-caret-into-view would have
                            // moved the page by then.
                            bodyEditor?.captureScrollAnchor()
                            if (links.notes.isEmpty()) {
                                // Nothing bound yet: the sheet is the only way
                                // to bind, so open it instead of an empty panel.
                                bodyEditor?.setInputLocked(true)
                                links.onOpenSheet()
                            } else if (!links.floatOn) {
                                // The side panel only exists while 行文时悬浮 is on.
                                // With it off, the old tap flipped an invisible flag and
                                // the dock button looked dead. One tap now turns the
                                // mode on and opens the panel it belongs to.
                                links.onToggleFloat(true)
                                floatNotePanel = true
                            } else {
                                floatNotePanel = !floatNotePanel
                            }
                        }
                    },
                    onNotesLongPress = noteLinks?.let { links ->
                        {
                            hideEditorKeyboard()
                            bodyEditor?.setInputLocked(true)
                            links.onOpenSheet()
                        }
                    },
                    onHideKeyboard = {
                        hideEditorKeyboard()
                    },
                    onStory = if (proofBookId != null) {
                        {
                            hideEditorKeyboard()
                            storyFloat = true
                        }
                    } else null,
                    onCollapse = {
                        // 3.26.11：先记住当前滚动位置，再翻状态。
                        // 3.26.15：同时把钉子装进 view，重排当帧就生效。
                        abilityScrollKeep = maxOf(bodyScrollY, bodyEditor?.scrollY ?: 0)
                        bodyEditor?.armScrollPin(abilityScrollKeep)
                        abilityOpen = false
                    },
                    onSymbol = { insertRaw(it) },
                    onExpand = {
                        abilityScrollKeep = maxOf(bodyScrollY, bodyEditor?.scrollY ?: 0)
                        bodyEditor?.armScrollPin(abilityScrollKeep)
                        abilityOpen = true
                    },
                    selectionMode = body.selection.start != body.selection.end,
                    onCopy = {
                        bodyEditor?.menuAction(android.R.id.copy)
                        bodyEditor?.collapseSelection()
                        selectionMenu = null
                    },
                    onCut = {
                        bodyEditor?.menuAction(android.R.id.cut)
                        bodyEditor?.collapseSelection()
                        selectionMenu = null
                    },
                    onPaste = {
                        bodyEditor?.menuAction(android.R.id.paste)
                        bodyEditor?.collapseSelection()
                        selectionMenu = null
                    },
                    onSelectAll = {
                        bodyEditor?.menuAction(android.R.id.selectAll)
                    },
                    onDelete = {
                        bodyEditor?.deleteSelection()
                        selectionMenu = null
                    },
                    onCancelSelection = {
                        bodyEditor?.collapseSelection()
                        selectionMenu = null
                    },
                    onProof = { startProof() },
                    onPolish = { startPolish() },
                    onAsk = { startAsk() },
                    backdrop = editorBodyBackdrop,
                    epoch = bodyScrollTick.intValue,
                    scrollTick = bodyScrollTick,
                    bodyEditor = bodyEditor,
                    // 3.26.41：工具栏横滑移光标。HUD 状态在页面层，这里只上报。
                    onScrubStart = {
                        scrub.dragging = true
                        scrub.visible = true
                        // 归零会重启下面那个延迟卸载的 effect（提前 return），
                        // 于是「上一次抬手才 100ms 又按下」不会把 HUD 卸掉。
                        scrubEndTick = 0
                    },
                    onScrubMove = { ratio, chars, total ->
                        scrub.ratio = ratio
                        scrub.chars = chars
                        scrub.total = total
                    },
                    onScrubEnd = {
                        scrub.dragging = false
                        scrubEndTick++
                    },
                )
                }
                // 常驻球：展开时在左下显示收起箭头，收起时显示键盘图标，点击切换。
                // 球和符号行共用同一个 navigationBars inset：键盘弹起时 nav
                // 会变，不能再拿一次性 navPx 算，否则球会比符号行高出一截。
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp)
                        .navigationBarsPadding()
                        // 与符号行同一格：底 10dp（列内 vertical padding 也是 10dp）、
                        // 38dp、11dp 连续曲率。展开时它就是符号行第一格，不再是
                        // 一个圆脑袋挂在左边；收起时同一形状留在原处。
                        .padding(bottom = 10.dp)
                        .size(38.dp)
                        .clip(SmoothCorner(11.dp))
                        .background(dockTone(ui))
                        .bouncy {
                            abilityScrollKeep = maxOf(bodyScrollY, bodyEditor?.scrollY ?: 0)
                            bodyEditor?.armScrollPin(abilityScrollKeep)
                            abilityOpen = !abilityOpen
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // 3.26.13：球的两态（键盘 / ∨）不再换图标，改成线段重组
                    // （用户 2026-11-17 点名的那种动效）。
                    val morphT by animateFloatAsState(
                        targetValue = if (abilityOpen) 1f else 0f,
                        animationSpec = spring(dampingRatio = .52f, stiffness = 420f),
                        label = "ballMorph",
                    )
                    MorphIcon(
                        progress = morphT,
                        a = MorphKeyboard,
                        b = MorphChevronDown,
                        modifier = Modifier.size(18.dp),
                        color = p.ink,
                    )
                }
            }
            }
        }

        // 正文已经铺到手势条下面，dock 自身背景负责盖住它；不再需要
        // 独立的一条 nav 背景，否则收起时会变成一条横带。


        AnimatedVisibility(
            visible = editReminderTodo != null,
            enter = fadeIn(tween(190)),
            exit = fadeOut(tween(190)),
            label = "reminderScrim",
        ) {
            val todo = editReminderTodo ?: reminderOverlayTodo ?: return@AnimatedVisibility
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .background(Color.Black.copy(alpha = if (p.isDark) .34f else .18f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { editingReminderId = null },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = editReminderTodo != null,
                    modifier = Modifier.align(Alignment.Center),
                    enter = slideInVertically(tween(190, easing = FastOutSlowInEasing)) { it / 10 } +
                        scaleIn(tween(190, easing = FastOutSlowInEasing), initialScale = .98f),
                    exit = slideOutVertically(tween(190, easing = FastOutSlowInEasing)) { it / 10 } +
                        scaleOut(tween(190, easing = FastOutSlowInEasing), targetScale = .98f),
                    label = "reminderCard",
                ) {
                    InlineReminderPanel(
                        todo = todo,
                        modifier = Modifier
                            .padding(horizontal = 18.dp)
                            .widthIn(max = 340.dp)
                            .fillMaxWidth(),
                        onDismiss = { editingReminderId = null },
                        onConfirm = { at ->
                            setTodoReminder(todo.id, at)
                            editingReminderId = null
                        },
                        onDelete = {
                            setTodoReminder(todo.id, null)
                            editingReminderId = null
                        },
                    )
                }
            }
        }
    }

    if (showType) {
        TypographySheet(store = LocalStore.current, onDismiss = { showType = false })
    }
}

/**
 * 3.26.41：HUD 的读取层。ratio/chars/total 只在这里读——每跨一个字只重组
 * 这一小块（以及它里面的 DockScrubHud），不把整页 WritingScreen 拖进重组。
 */
@Composable
private fun ScrubHudLayer(
    state: ScrubHudState,
    modifier: Modifier = Modifier,
) {
    DockScrubHud(
        ratio = state.ratio,
        chars = state.chars,
        total = state.total,
        modifier = modifier,
    )
}

@Composable
private fun SelectionToolbar(
    bar: SelectionBar?,
    maxWidth: Dp,
    maxHeight: Dp,
    onAction: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    var lastBar by remember { mutableStateOf<SelectionBar?>(null) }
    LaunchedEffect(bar) { if (bar != null) lastBar = bar }
    val b = bar ?: lastBar
    AnimatedVisibility(
        visible = bar != null,
        modifier = Modifier
            .offset {
                val value = b ?: return@offset androidx.compose.ui.unit.IntOffset.Zero
                val toolbarW = with(density) { 330.dp.toPx() }.toInt()
                val w = with(density) { maxWidth.toPx() }.toInt()
                val h = with(density) { maxHeight.toPx() }.toInt()
                val x = ((value.x1 + value.x2) / 2 - toolbarW / 2)
                    .coerceIn(4, (w - toolbarW - 4).coerceAtLeast(4))
                val below = value.y2 + with(density) { 32.dp.toPx() }.toInt()
                val y = if (below + with(density) { 56.dp.toPx() }.toInt() < h) {
                    below
                } else {
                    (value.y1 - with(density) { 56.dp.toPx() }).toInt().coerceAtLeast(4)
                }
                androidx.compose.ui.unit.IntOffset(x, y)
            },
        enter = fadeIn(tween(150)) + slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(180, easing = FastOutSlowInEasing),
        ),
        exit = fadeOut(tween(120)) + slideOutVertically(
            targetOffsetY = { it / 3 },
            animationSpec = tween(150, easing = FastOutSlowInEasing),
        ),
    ) {
        MoyuSurface(Modifier.height(48.dp),
            radius = 24f,
            flat = true,
            tone = dockTone(LocalInk.current),
        ) {
            Row(
                Modifier.fillMaxHeight().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionAction("全选") { onAction(android.R.id.selectAll) }
                SelectionAction("复制") { onAction(android.R.id.copy) }
                SelectionAction("剪切") { onAction(android.R.id.cut) }
                SelectionAction("粘贴") { onAction(android.R.id.paste) }
                SelectionAction("完成") { onDismiss() }
            }
        }
    }
}

@Composable
private fun SelectionAction(label: String, onClick: () -> Unit) {
    val ui = LocalInk.current
    Box(
        Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, size = 12.5f, color = ui.palette.ink, weight = FontWeight.Medium)
    }
}

private fun BodyEditText.applyAccentCursor(
    color: Int,
    widthPx: Int,
    heightPx: Int,
    bottomInset: Int = 0,
) {
    handleColor = color
    // The native caret is hidden; BodyEditText draws a fixed-size caret in
    // onDraw so TextView cannot resize it with the current line metrics.
    if (Build.VERSION.SDK_INT >= 29) {
        textCursorDrawable = android.graphics.drawable.ColorDrawable(
            android.graphics.Color.TRANSPARENT,
        )
        prepareSelectionHandle(color)
    }
    highlightColor = (color and 0x00FFFFFF) or 0x33000000
    setSelfDrawnCursor(color, widthPx, heightPx)
}

private class TodoBoxSpan(
    val todoId: String,
    var done: Boolean,
    private val sizePx: Float,
    private val accent: Int,
    private val ink: Int,
    private val paper: Int,
    private val haptics: Boolean,
    private val host: View,
    private val onToggle: (TodoBoxSpan) -> Unit,
) : ReplacementSpan() {
    var progress = if (done) 1f else 0f
        private set
    private var animator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private var pulse = 1f

    fun pulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(1f, .9f, 1f).apply {
            duration = 180
            addUpdateListener {
                pulse = it.animatedValue as Float
                host.invalidate()
            }
            start()
        }
    }

    fun syncDone(value: Boolean) {
        animator?.cancel()
        done = value
        progress = if (value) 1f else 0f
        host.invalidate()
    }

    fun animateDone(value: Boolean) {
        if (done == value) return
        done = value
        if (haptics) host.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, if (done) 1f else 0f).apply {
            duration = 200
            addUpdateListener {
                progress = it.animatedValue as Float
                host.invalidate()
            }
            start()
        }
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int = (sizePx + host.resources.displayMetrics.density * 8f).toInt()

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        baseline: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val d = host.resources.displayMetrics.density
        val total = sizePx + d * 8f
        val boxSize = sizePx * pulse
        val left = x + (total - boxSize) / 2f
        val fm = paint.fontMetrics
        val boxTop = baseline + fm.ascent + 2.5f * d + (sizePx - boxSize) / 2f
        val radius = 6f * d
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val fill = android.graphics.Color.argb(
            (255 * progress).toInt(),
            android.graphics.Color.red(accent),
            android.graphics.Color.green(accent),
            android.graphics.Color.blue(accent),
        )
        p.style = Paint.Style.FILL
        p.color = if (progress > .01f) fill else paper
        canvas.drawRoundRect(left, boxTop, left + boxSize, boxTop + boxSize, radius, radius, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * d
        p.color = if (progress > .01f) accent else
            android.graphics.Color.argb(
                (140 + 60 * (1f - progress)).toInt(),
                android.graphics.Color.red(ink),
                android.graphics.Color.green(ink),
                android.graphics.Color.blue(ink),
            )
        canvas.drawRoundRect(left, boxTop, left + boxSize, boxTop + boxSize, radius, radius, p)
        if (progress > .01f) {
            val path = Path().apply {
                moveTo(left + boxSize * .22f, boxTop + boxSize * .52f)
                lineTo(left + boxSize * .42f, boxTop + boxSize * .70f)
                lineTo(left + boxSize * .79f, boxTop + boxSize * .30f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), partial, true)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.1f * d
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
            p.color = android.graphics.Color.WHITE
            canvas.drawPath(partial, p)
        }
    }

    fun performHaptic() {
        if (haptics) host.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun click() = onToggle(this)
}

private class TodoDoneSpan(private val color: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) {
        tp.color = color
        tp.isStrikeThruText = true
    }
}

private class ExactLineHeightSpan(private val heightPx: Int) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        v: Int,
        fm: Paint.FontMetricsInt,
    ) {
        val extra = heightPx - (fm.descent - fm.ascent)
        if (extra == 0) return
        fm.ascent -= extra / 2
        fm.descent += extra - extra / 2
        fm.top = minOf(fm.top, fm.ascent)
        fm.bottom = maxOf(fm.bottom, fm.descent)
    }
}

private class ReminderLineSpan(private val color: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) {
        tp.textSize *= .78f
        tp.color = color
    }
}

private class BodyEditText(
    context: Context,
    onSelectionChanged: (String, Int, Int) -> Unit,
    private val onSelectionMenu: ((Int, Int, Int, Int) -> Unit)? = null,
    private val onGeometry: ((SelectionGeometry?) -> Unit)? = null,
    private val onTodoToggle: ((String) -> Unit)? = null,
    private val onTodoSwipe: ((String, Rect) -> Unit)? = null,
    private val onTodoCursor: ((String?) -> Unit)? = null,
    private val onReminderTap: ((String) -> Unit)? = null,
    private val onTodoDeleted: ((String) -> Unit)? = null,
    private val onBodyScroll: (() -> Unit)? = null,
) : EditText(context) {
    // TextView calls onSelectionChanged() from its own constructor, before this
    // property is initialized. Keep it nullable and register after super().
    private var selectionListener: ((String, Int, Int) -> Unit)? = null
    private var selectionMenuActive = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    // A small flick was being amplified into a full-screen jump. Keep real
    // inertia for fast swipes, but do not fling for a short drag.
    private val minFling = 800f
    private val scroller = OverScroller(context)
    private var velocity: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var longPressConsumed = false
    private var selectingAfterLongPress = false
    private var selectionBarActive = false
    private var handleDrag = -1
    private var draggingCaretX: Float? = null
    // ── 手柄的「水滴」跟随（2026-11-15，同柔曲线）─────────────────────────────
    // 拖手柄时不给 1:1 硬跟手，而是用 spring(.52, 300) 追手指：起步略滞、拖动中被拉着走、
    // 松手后过冲一次再落到那个字上。位置只算 x，y 由行决定（跨行时按行跳，不硬凑）。
    private var handleSpringX = Float.NaN
    private var handleSpringVel = 0f
    private var handleSpringTarget = Float.NaN
    private var handleSpringOn = false
    private var handleSpringSide = 0
    private var handleSpringLastAt = 0L
    /** 被拖那只手柄的水滴形变强度（0 = 原形）与方向（单位向量）。 */
    private var handleDragPull = 0f
    private var handleDragDirX = 0f
    private var handleDragDirY = 0f
    /** 拖动时手指离开边缘后还要继续滚的方向：-1 上，+1 下，0 停。 */
    private var autoScrollDir = 0
    private var autoScrollSpeed = 0f
    private var lastDragX = 0f
    private var lastDragY = 0f

    private val handleSpringDriver = object : Runnable {
        override fun run() {
            val now = android.os.SystemClock.uptimeMillis()
            val dt = ((now - handleSpringLastAt).coerceIn(1L, 32L)) / 1000f
            handleSpringLastAt = now
            if (autoScrollDir != 0 && handleDrag >= 0) stepAutoScroll(dt)
            if (!handleSpringOn) return
            if (handleSpringTarget.isNaN()) {
                // 目标丢了：别把状态挂在那里（会留下一个僵住的形变）。
                handleSpringOn = false
                handleDragPull = 0f
                return
            }
            // 跟手要「紧跟不晃」：k=600、ζ=0.86（c≈42）。0.52 的阻尼拖起来会来回摆，
            // 用户 2026-11-15 报「晃动太大」——同柔的过冲留给出现/收起，拖拽段不许荡。
            val k = 600f
            val c = 42f
            var steps = (dt / 0.004f).toInt().coerceIn(1, 8)
            val h = dt / steps
            while (steps-- > 0) {
                val f = -k * (handleSpringX - handleSpringTarget) - c * handleSpringVel
                handleSpringVel += f * h
                handleSpringX += handleSpringVel * h
            }
            // 水滴形变强度：被拉着走的时候按滞后距离/速度算，落在目标上就回到 0。
            // 方向由手指位移决定（见 ACTION_MOVE），这里只给强度。
            // 2026-11-15：滞后基准 34dp→24dp、速度 5200→4200，让横向拖也能看出
            // 形状在变（原来只有竖直拖明显，水平拖几乎读不出）。
            val px = resources.displayMetrics.density
            val lag = kotlin.math.abs(handleSpringTarget - handleSpringX)
            val pull = (lag / (24f * px)).coerceIn(0f, 1f)
                .coerceAtLeast((kotlin.math.abs(handleSpringVel) / 4200f).coerceIn(0f, 1f))
            handleDragPull = pull
            val settled = kotlin.math.abs(handleSpringX - handleSpringTarget) < .5f &&
                kotlin.math.abs(handleSpringVel) < 10f
            invalidate()
            emitGeometry()
            if (!settled || autoScrollDir != 0) {
                postOnAnimation(this)
            } else {
                handleSpringX = handleSpringTarget
                handleSpringVel = 0f
                // 粘滞水滴：落定后形变不立刻归零，按 ViscousDrop 的曲线指数衰减
                // （一帧切回圆形就只剩「弹」，读不出「粘」）。
                if (handleDragPull > .004f) {
                    handleDragPull *= ViscousDrop.decayFor(dt * 1000f)
                    postOnAnimation(this)
                } else {
                    handleDragPull = 0f
                    handleSpringOn = false
                    invalidate()
                    emitGeometry()
                }
            }
        }
    }

    /** 手柄贴到落点：拖动时每帧调一次，松手后也调一次（目标就是最终那个字）。 */
    private fun trackHandle(offset: Int) {
        val g = caretGeom(offset) ?: return
        handleSpringTarget = g.x
        if (handleSpringX.isNaN()) handleSpringX = g.x
        if (!handleSpringOn) {
            handleSpringOn = true
            handleSpringLastAt = android.os.SystemClock.uptimeMillis()
            removeCallbacks(handleSpringDriver)
            postOnAnimation(handleSpringDriver)
        }
    }

    /** 抓住手柄：弹簧从它现在的位置起步，不跳。 */
    private fun beginHandleDrag(index: Int) {
        handleDrag = index
        handleSpringSide = index
        val box = handleBounds(if (index == 0) selectionStart else selectionEnd)
        handleSpringX = box?.centerX ?: Float.NaN
        handleSpringTarget = handleSpringX
        handleSpringVel = 0f
        handleSpringOn = false
        autoScrollDir = 0
        autoScrollSpeed = 0f
        handleDragPull = 0f
        lastDragX = downX
        lastDragY = downY
        parent?.requestDisallowInterceptTouchEvent(true)
        invalidate()
    }

    private fun resetHandleSpring() {
        handleSpringOn = false
        handleSpringX = Float.NaN
        handleSpringVel = 0f
        handleSpringTarget = Float.NaN
        handleDragPull = 0f
        autoScrollDir = 0
        autoScrollSpeed = 0f
        removeCallbacks(handleSpringDriver)
    }

    /** 拖到正文上/下边缘时自己滚：越靠边越快，滚动时手柄继续跟着手指。 */
    private fun updateAutoScroll(viewY: Float) {
        val d = resources.displayMetrics.density
        val band = 56f * d
        val top = band
        val bottom = (height - dockBottomInset - band).coerceAtLeast(top + band)
        autoScrollDir = when {
            viewY < top -> -1
            viewY > bottom -> 1
            else -> 0
        }
        autoScrollSpeed = when (autoScrollDir) {
            -1 -> ((top - viewY) / band).coerceIn(0f, 1f) * 900f + 300f
            1 -> ((viewY - bottom) / band).coerceIn(0f, 1f) * 900f + 300f
            else -> 0f
        }
        if (autoScrollDir != 0 && !handleSpringOn) {
            handleSpringLastAt = android.os.SystemClock.uptimeMillis()
            handleSpringOn = true
            postOnAnimation(handleSpringDriver)
        }
    }

    private fun stepAutoScroll(dt: Float) {
        val before = scrollY
        val next = (scrollY + (autoScrollDir * autoScrollSpeed * dt)).toInt()
            .coerceIn(0, contentMaxScroll())
        if (next != before) scrollTo(scrollX, next)
        // 滚动后手指下面的字变了：重新算端点，选区跟着走。
        val offset = getOffsetForPosition(lastDragX, lastDragY).coerceIn(0, text.length)
        val s = selectionStart.coerceAtLeast(0)
        val e = selectionEnd.coerceAtLeast(0)
        if (handleDrag == 0 && offset < e) setSelection(offset, e)
        else if (handleDrag == 1 && offset > s) setSelection(s, offset)
        trackHandle(if (handleDrag == 0) selectionStart else selectionEnd)
        emitGeometry()
        invalidate()
    }
    var handleColor = 0xFF457F85.toInt()
    private var selectionAnchor = 0
    private var selectionPopup: PopupWindow? = null
    /** Set by the writing screen: the selection menu offers「校核」, which asks
     *  the screen to open its proofing panel for the selected passage. */
    var onProofRequest: (() -> Unit)? = null
    private val longPressRunnable = Runnable { performLongPress() }
    private var downSelectionStart = 0
    private var downSelectionEnd = 0
    private var downCursorVisible = true
    var autoIndent = false
    var indentGuard = false
    private var redirectingSelection = false
    /** Bumped on every text or scroll change; EditorBodyBackdrop caches its
     *  software bitmap against this instead of redrawing each frame. */
    var renderRevision = 0L
    var lastTextLength = 0

    /** Temporarily tints a range so a proofing suggestion can be found on the page. */
    fun highlightRange(from: Int, to: Int) {
        val editable = text as? android.text.Spannable ?: return
        if (from < 0 || to <= from || to > editable.length) return
        val span = android.text.style.BackgroundColorSpan(0x33C9A227)
        editable.setSpan(span, from, to, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        invalidate()
        postDelayed({
            editable.removeSpan(span)
            invalidate()
        }, 1600)
    }

    /** Window-space top of this view, used to align the dock slice. */
    fun windowTop(): Float {
        val loc = IntArray(2)
        getLocationInWindow(loc)
        return loc[1].toFloat()
    }

    /** Renders the slice of this view that sits under the dock, in view pixels. */
    fun renderDockSlice(topInView: Int, sliceHeight: Int): Bitmap? {
        if (width <= 0 || height <= 0 || sliceHeight <= 0) return null
        val top = topInView.coerceIn(0, maxOf(0, height - 1))
        val h = min(sliceHeight, height - top)
        if (h <= 0) return null
        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(0f, -top.toFloat())
        canvas.clipRect(0, top, width, top + h)
        draw(canvas)
        return bmp
    }
    var pendingNewline = false
    private var cachedSelectHandle: android.graphics.drawable.Drawable? = null
    val selectHandleDrawable: android.graphics.drawable.Drawable?
        get() = cachedSelectHandle
    val selectHandleWidth: Int
        get() = (cachedSelectHandle ?: textSelectHandle)?.intrinsicWidth ?: 0

    private fun clearNativeSelectionHandle(method: String) {
        runCatching {
            when (method) {
                "setTextSelectHandle" -> setTextSelectHandle(0)
                "setTextSelectHandleLeft" -> setTextSelectHandleLeft(0)
                "setTextSelectHandleRight" -> setTextSelectHandleRight(0)
            }
        }
    }

    private fun hideNativeSelectionHandles() {
        runCatching {
            val editorField = android.widget.TextView::class.java
                .getDeclaredField("mEditor")
                .apply { isAccessible = true }
            val editor = editorField.get(this) ?: return
            val hide = editor.javaClass
                .getDeclaredMethod("hideSelectionHandles")
                .apply { isAccessible = true }
            hide.invoke(editor)
        }
    }

    fun prepareSelectionHandle(color: Int): android.graphics.drawable.Drawable? {
        if (cachedSelectHandle == null) {
            val source = textSelectHandle
            // newDrawable gives the overlay its own alpha. mutate() alone kept
            // sharing the native handle state, so hiding the native handle also
            // hid the custom one.
            cachedSelectHandle = source?.constantState?.newDrawable(resources)?.mutate()
                ?: source?.mutate()
            cachedSelectHandle?.setTint(color)
            // Remove the native handle drawables entirely. Alpha 0 still left
            // a system handle touch target that flashed blue while dragging.
            clearNativeSelectionHandle("setTextSelectHandle")
            clearNativeSelectionHandle("setTextSelectHandleLeft")
            clearNativeSelectionHandle("setTextSelectHandleRight")
            hideNativeSelectionHandles()
        } else {
            cachedSelectHandle?.setTint(color)
        }
        return cachedSelectHandle
    }
    private var bodyTopInWindow = 0
    /** Character at the top of the viewport, kept across a width re-wrap. */
    private var scrollAnchorChar = -1
    private var scrollAnchorOffset = 0
    /**
     * Caret anchor. When the caret (or the selection's start) is fully visible,
     * a re-wrap must leave that line on the same screen row instead of holding
     * the first visible line. -1 means the caret anchor is not in use.
     */
    private var caretAnchorChar = -1
    private var caretAnchorTop = 0
    private var restoringScroll = false
    /** Width from the previous onLayout. View.width is already the new value
     * inside onLayout, so comparing it with right-left is always false. */
    private var lastLaidOutWidth = 0
    /** Re-applied after onLayout: TextView may run bring-caret-into-view
     * after the layout pass and undo the anchor restore. */
    private var pendingRestoreY = -1
    /** Until this time, a scroll-to-top from TextView's bring-caret-into-view
     * is redirected to the anchor: the caret is off screen (or was dropped to
     * 0 by focus loss) and must not drag the page away from the re-wrap anchor. */
    private var anchorGuardUntil = 0L
    // Character-flow animation: where each visible glyph was before a re-wrap,
    // where it goes, and the clock that interpolates between the two.
    private val flowFrom = HashMap<Int, FloatArray>()
    private val flowTarget = HashMap<Int, FloatArray>()
    /** 每个字错开多少（0..1，按离编辑点的行距分 3 带，同柔的带状错落）。 */
    private val flowLag = HashMap<Int, Float>()
    private var flowLayout: android.text.Layout? = null
    private var flowAnimating = false
    private var flowStartAt = -1L
    /** 一次替换已经拍过快照，等下一次 layout 再起动画（只起一次）。 */
    private var flowPending = false
    /** 替换发生的那一行，用来算带状错落。 */
    private var flowEditLine = 0
    private companion object {
        /** 主段时长（ms）；带延迟最多再占 18%。 */
        const val FLOW_MS = 300f
        /** 同柔弹簧：ζ=.52、ω=√300。 */
        const val FLOW_ZETA = .52f
        const val FLOW_OMEGA = 17.3205f
        /** Temporary fling diagnostics (both scroll directions). */
        const val INFO_FLING = true
    const val INFO_CARET = false
    }
    private var overlayTopInWindow = 0

    fun updateOverlayTop(y: Int) {
        overlayTopInWindow = y
    }

    private var todoAccent = 0xFF457F85.toInt()
    private var todoInk = 0xFF191813.toInt()
    private var todoMuted = 0xFF77736A.toInt()
    private var todoPaper = 0xFFF7F3EA.toInt()
    private var todoBoxPx = 18f * resources.displayMetrics.density
    private var todoHaptics = true
    private var paperStyle = "plain"
    private var paperLineStep = 0f
    private var paperLineAlpha = .5f
    private var applyingLineHeight = false
    private var selfCursorColor = 0
    private var selfCursorWidth = 0
    private var selfCursorHeight = 0

    fun setSelfDrawnCursor(color: Int, widthPx: Int, heightPx: Int) {
        selfCursorColor = color
        selfCursorWidth = widthPx.coerceAtLeast(2)
        selfCursorHeight = heightPx.coerceAtLeast(2)
        invalidate()
    }

    private fun applyEmptyLineSpacing() {
        val target = paperLineStep.takeIf { it > 0f } ?: return
        val fm = paint.fontMetrics
        setLineSpacing((target - (fm.bottom - fm.top)).coerceAtLeast(0f), 1f)
    }

    fun configureLineGrid(textSizeSp: Float, lineHeightFactor: Float) {
        includeFontPadding = false
        val target = Math.round(
            textSizeSp * resources.displayMetrics.scaledDensity * lineHeightFactor,
        ).toFloat()
        setLineSpacing(0f, 1f)
        paperLineStep = target
        applyLineHeightSpan()
    }

    fun applyLineHeightSpan() {
        val editable = editable() ?: return
        if (applyingLineHeight) return
        applyingLineHeight = true
        try {
            editable.getSpans(0, editable.length, ExactLineHeightSpan::class.java)
                .forEach { editable.removeSpan(it) }
            if (editable.isEmpty() || paperLineStep <= 0f) {
                applyEmptyLineSpacing()
                return
            }
            setLineSpacing(0f, 1f)
            editable.setSpan(
                ExactLineHeightSpan(Math.round(paperLineStep)),
                0,
                editable.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
        } finally {
            applyingLineHeight = false
        }
    }

    fun setPaperStyle(style: String, lineStepPx: Float = 0f, lineAlpha: Float = .5f) {
        val roundedStep = if (lineStepPx > 0f) Math.round(lineStepPx).toFloat() else lineStepPx
        if (paperStyle != style || paperLineStep != roundedStep || paperLineAlpha != lineAlpha) {
            paperStyle = style
            paperLineStep = roundedStep
            paperLineAlpha = lineAlpha
            invalidate()
        }
    }
    private var todoModel: List<Todo> = emptyList()
    var panelBottomInset = 0
    /** Extra scroll room kept below the last line so it stops above the dock;
     *  the view itself has no bottom padding so body text may draw under it. */
    var baseDockInset = 0

    /** 底部让位：Compose 写入基础量，滚动上限（computeVerticalScrollRange）
     *  与画光标都实时读它。 */
    val dockBottomInset: Int get() = baseDockInset
    private var inputLocked = false

    fun setInputLocked(locked: Boolean) {
        inputLocked = locked
    }

    fun setPanelInset(px: Int) {
        panelBottomInset = px.coerceAtLeast(0)
        setPadding(paddingLeft, 0, paddingRight, 0)
    }
    private var todoDownSpan: TodoBoxSpan? = null
    private var todoSwipeSpan: TodoBoxSpan? = null
    /**
     * Scroll offset a local body edit must keep, or -1.
     *
     * TextView brings the caret into view TWICE per text change: once inline from
     * the text watcher and once from onPreDraw, i.e. after the edit already
     * returned. Ticking a box or writing a reminder line is a local edit and the
     * caret can be pages away, so the page used to leap to the caret. Re-assert
     * the offset from scrollTo. The guard dies on the next touch and is
     * time-boxed, so it can never brake the reader's own scrolling.
     */
    private var keepViewportY = -1
    private var keepViewportAt = 0L
    /**
     * 能力栏开合期间要钉住的可视位置，-1 表示没有钉。
     *
     * 为什么必须在 view 里、而不是 Compose 的每帧循环里：开合会改 `baseDockInset`
     * （底部让位），TextView 跟着重排，`onLayout` 里的 bringPointIntoView 会把
     * 光标拉进视野——光标停在开头时就是整页回顶。Compose 的 `withFrameNanos`
     * 最早也要下一帧才执行，而那一帧的布局已经跑完并且画出去了，于是「闪一下」。
     * 这里在 system 调 `scrollTo` 的那一刻就把落点换掉，第 0 帧就落不了地。
     */
    private var pinScrollY = -1
    private var pinUntil = 0L
    private var todoSwipeConsumed = false
    private var lastTodoDeleteAt = 0L

    fun configureTodoStyle(accent: Int, ink: Int, muted: Int, paper: Int, haptics: Boolean) {
        todoAccent = accent
        todoInk = ink
        todoMuted = muted
        todoPaper = paper
        todoHaptics = haptics
        todoBoxPx = 18f * resources.displayMetrics.density
    }

    private fun editable(): Editable? = text

    private fun drawPaper(canvas: Canvas) {
        if (paperStyle == "plain") return
        val layout = layout ?: return
        val density = resources.displayMetrics.density
        val solid = paperStyle == "solid"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = todoMuted
            alpha = (paperLineAlpha * 255f).toInt().coerceIn(0, 255)
            style = Paint.Style.STROKE
            strokeWidth = (if (solid) .9f else .8f) * density
            pathEffect = if (solid) null else android.graphics.DashPathEffect(
                floatArrayOf(5f * density, 4f * density),
                0f,
            )
        }
        val left = compoundPaddingLeft.toFloat()
        val right = (width - compoundPaddingRight).toFloat()
        val fm = getPaint().fontMetrics
        val glyphHeight = fm.descent - fm.ascent
        val gap = ((paperLineStep - glyphHeight) * .35f)
            .coerceIn(2f * density, 6f * density)
        val visibleTop = scrollY.toFloat()
        val visibleBottom = scrollY + height.toFloat()
        val step = paperLineStep.takeIf { it > 0f } ?: (textSize * 1.82f)
        if (step <= 0f) return
        val firstBaseline = if (layout.lineCount > 0) {
            layout.getLineBaseline(0)
        } else {
            compoundPaddingTop
        }

        // This is a background grid, not a per-line decoration. It is anchored
        // once and repeated for the whole scrollable content, so wrapping or
        // cursor movement can never change the grid step.
        var y = firstBaseline + fm.descent + gap
        while (y < visibleTop) y += step
        while (y < visibleBottom) {
            canvas.drawLine(left, y, right, y, paint)
            y += step
        }
    }

    private fun applyReminderSpans() {
        val editable = editable() ?: return
        editable.getSpans(0, editable.length, ReminderLineSpan::class.java)
            .forEach { editable.removeSpan(it) }
        var start = 0
        while (start < editable.length) {
            val end = editable.indexOf('\n', start).let { if (it < 0) editable.length else it }
            if (end > start && isReminderLine(editable.subSequence(start, end).toString())) {
                editable.setSpan(
                    ReminderLineSpan(todoMuted),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            start = end + 1
        }
    }

    fun isTodoLineAt(editable: CharSequence, lineStart: Int): Boolean {
        var index = lineStart.coerceIn(0, editable.length)
        while (index < editable.length && (editable[index] == '　' || editable[index] == ' ')) index++
        return editable.getOrNull(index) == '☐' || editable.getOrNull(index) == '☑'
    }

    fun isReminderLineAt(editable: CharSequence, lineStart: Int): Boolean {
        val start = lineStart.coerceIn(0, editable.length)
        val end = editable.indexOf('\n', start).let { if (it < 0) editable.length else it }
        return end > start && isReminderLine(editable.subSequence(start, end).toString())
    }

    private fun lastLineStart(editable: Spannable): Int {
        if (editable.isEmpty()) return -1
        val end = editable.length
        val start = if (editable[end - 1] == '\n') {
            end - 1
        } else {
            editable.lastIndexOf('\n', end - 1) + 1
        }
        return if (start < end) start else -1
    }

    private fun lastLineIsReminder(): Boolean {
        val editable = editable() ?: return false
        val start = lastLineStart(editable)
        return start >= 0 && isReminderLine(editable.subSequence(start, editable.length).toString())
    }

    private fun todoHasReminderAtEnd(): Boolean {
        val editable = editable() ?: return false
        if (selectionStart != selectionEnd) return false
        val caret = selectionStart.coerceIn(0, editable.length)
        val (start, end) = todoLineBounds(editable, caret)
        if (caret != end || start >= editable.length) return false
        if (editable[start] != '☐' && editable[start] != '☑') return false
        val nextStart = end + 1
        if (nextStart >= editable.length) return false
        val nextEnd = editable.indexOf('\n', nextStart).let { if (it < 0) editable.length else it }
        return nextEnd > nextStart &&
            isReminderLine(editable.subSequence(nextStart, nextEnd).toString())
    }

    private fun appendLineAfterLastReminder(): Boolean {
        val editable = editable() ?: return false
        if (!lastLineIsReminder()) return false
        val insert = if (autoIndent) "\n　　" else "\n"
        editable.insert(editable.length, insert)
        setSelection(editable.length)
        return true
    }

    private fun tapIsBelowLastReminder(y: Float): Boolean {
        if (!lastLineIsReminder()) return false
        val layout = layout ?: return false
        val editable = text ?: return false
        val line = layout.getLineForOffset((editable.length - 1).coerceAtLeast(0))
        val bottom = layout.getLineBottom(line) - scrollY
        return y > bottom + touchSlop
    }

    private fun reminderLineVerticalBounds(offset: Int): Pair<Float, Float>? {
        val editable = editable() ?: return null
        val layout = layout ?: return null
        val (start, end) = todoLineBounds(editable, offset.coerceIn(0, editable.length))
        if (!isReminderLine(editable.subSequence(start, end).toString())) return null
        val line = layout.getLineForOffset(start)
        return (layout.getLineTop(line) - scrollY).toFloat() to
            (layout.getLineBottom(line) - scrollY).toFloat()
    }

    private fun reminderLineAt(offset: Int): Boolean {
        val editable = editable() ?: return false
        if (offset < 0 || offset > editable.length) return false
        val (start, end) = todoLineBounds(editable, offset.coerceAtMost(editable.length))
        return isReminderLine(editable.subSequence(start, end).toString())
    }

    fun activeTodoAt(offset: Int): String? {
        val editable = editable() ?: return null
        if (editable.isEmpty()) return null
        val caret = offset.coerceIn(0, editable.length)
        val (lineStart, lineEnd) = todoLineBounds(editable, caret)
        if (lineStart >= editable.length) return null
        if (editable[lineStart] == '☐' || editable[lineStart] == '☑') {
            return editable.getSpans(lineStart, (lineStart + 1).coerceAtMost(editable.length), TodoBoxSpan::class.java)
                .firstOrNull()?.todoId
        }
        if (!isReminderLine(editable.subSequence(lineStart, lineEnd).toString())) return null
        if (lineStart == 0) return null
        var previousStart = lineStart - 1
        while (previousStart > 0 && editable[previousStart - 1] != '\n') previousStart--
        if (previousStart >= editable.length) return null
        if (editable[previousStart] != '☐' && editable[previousStart] != '☑') return null
        return editable.getSpans(
            previousStart,
            (previousStart + 1).coerceAtMost(editable.length),
            TodoBoxSpan::class.java,
        ).firstOrNull()?.todoId
    }

    private fun todoLineBounds(editable: Spannable, start: Int): Pair<Int, Int> {
        val lineStart = if (start == 0) 0 else {
            var i = start - 1
            while (i >= 0 && editable[i] != '\n') i--
            i + 1
        }
        var lineEnd = start
        while (lineEnd < editable.length && editable[lineEnd] != '\n') lineEnd++
        return lineStart to lineEnd
    }

    private fun findTodoStart(editable: Spannable, todo: Todo, used: MutableSet<Int>): Int {
        if (todo.start in 0 until editable.length &&
            (editable[todo.start] == '☐' || editable[todo.start] == '☑') &&
            todo.start !in used
        ) return todo.start
        var start = 0
        var first = -1
        while (start < editable.length) {
            val lineEnd = editable.indexOf('\n', start).let { if (it < 0) editable.length else it }
            if (start < lineEnd && (editable[start] == '☐' || editable[start] == '☑') &&
                start !in used
            ) {
                if (first < 0) first = start
                val body = editable.subSequence(
                    (start + 2).coerceAtMost(lineEnd),
                    lineEnd,
                ).toString().trim()
                if (body == todo.text.trim()) return start
            }
            start = lineEnd + 1
        }
        return first
    }

    fun applyTodoSpans(todos: List<Todo>) {
        todoModel = todos
        val editable = editable() ?: return
        editable.getSpans(0, editable.length, TodoBoxSpan::class.java)
            .forEach { editable.removeSpan(it) }
        editable.getSpans(0, editable.length, CharacterStyle::class.java)
            .filter { it !is TodoBoxSpan && it !is ReminderLineSpan }
            .forEach { editable.removeSpan(it) }
        val used = mutableSetOf<Int>()
        todos.forEach { todo ->
            val start = findTodoStart(editable, todo, used)
            if (start < 0 || start >= editable.length) return@forEach
            if (editable[start] != '☐' && editable[start] != '☑') return@forEach
            used += start
            val done = editable[start] == '☑'
            val box = TodoBoxSpan(
                todoId = todo.id,
                done = done,
                sizePx = todoBoxPx,
                accent = todoAccent,
                ink = todoInk,
                paper = todoPaper,
                haptics = todoHaptics,
                host = this,
                onToggle = { toggleTodoBox(it) },
            )
            editable.setSpan(box, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        refreshTodoDoneSpans()
    }

    private fun refreshTodoDoneSpans() {
        val editable = editable() ?: return
        editable.getSpans(0, editable.length, CharacterStyle::class.java)
            .filter { it !is TodoBoxSpan && it !is ReminderLineSpan }
            .forEach { editable.removeSpan(it) }
        setTextColor(todoInk)
        editable.getSpans(0, editable.length, TodoBoxSpan::class.java).forEach { box ->
            val start = editable.getSpanStart(box)
            if (start < 0 || start >= editable.length) return@forEach
            val checked = editable[start] == '☑'
            if (box.done != checked) box.syncDone(checked)
            if (!checked) return@forEach
            val lineEnd = todoLineBounds(editable, start).second
            val from = (start + 2).coerceAtMost(lineEnd)
            if (from < lineEnd) {
                editable.setSpan(
                    android.text.style.StrikethroughSpan(),
                    from,
                    lineEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                editable.setSpan(
                    android.text.style.ForegroundColorSpan(todoMuted),
                    from,
                    lineEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        applyReminderSpans()
        invalidate()
    }

    /**
     * Rewrite the body in place without letting the page follow the caret.
     *
     * Every programmatic text change makes TextView run bring-caret-into-view.
     * Ticking a box or writing a reminder line is a local edit and the caret
     * can be pages away from it, so the viewport used to leap to the caret the
     * moment a box was ticked. The edited line is already on screen; keep the
     * exact scroll offset instead.
     */
    fun replaceKeepingViewport(from: Int, to: Int, insert: CharSequence, animate: Boolean = true) {
        val editable = editable() ?: return
        val start = from.coerceIn(0, editable.length)
        val end = to.coerceIn(start, editable.length)
        keepViewportY = scrollY
        keepViewportAt = android.os.SystemClock.uptimeMillis()
        // 替换前拍一遍字形位置，落地后由 onLayout 起一次「水流」重排（2026-11-15）。
        // 只在程序改写走这里；打字走的不是这条路，所以打字不会带动画。
        if (animate && layout != null) {
            flowEditLine = layout?.getLineForOffset(start) ?: 0
            captureGlyphPositions()
            flowPending = true
        } else {
            flowPending = false
        }
        editable.replace(start, end, insert)
    }

    private fun toggleTodoBox(box: TodoBoxSpan) {
        val editable = editable() ?: return
        val start = editable.getSpanStart(box)
        if (start < 0 || start >= editable.length) return
        val next = if (editable[start] == '☑') '☐' else '☑'
        val target = next == '☑'
        replaceKeepingViewport(start, start + 1, next.toString(), animate = false)
        box.syncDone(target)
        box.pulse()
        if (todoHaptics) box.performHaptic()
        refreshTodoDoneSpans()
        onTodoToggle?.invoke(box.todoId)
    }

    fun collectTodos(base: List<Todo>): List<Todo> {
        val editable = editable() ?: return base
        val spans = editable.getSpans(0, editable.length, TodoBoxSpan::class.java)
            .sortedBy { editable.getSpanStart(it) }
        if (spans.isEmpty()) return emptyList()
        return spans.mapNotNull { box ->
            val start = editable.getSpanStart(box)
            if (start < 0 || start >= editable.length) return@mapNotNull null
            val old = base.firstOrNull { it.id == box.todoId }
            val lineEnd = todoLineBounds(editable, start).second
            val textStart = (start + 2).coerceAtMost(lineEnd)
            old?.copy(
                text = editable.subSequence(textStart, lineEnd).toString(),
                done = editable[start] == '☑',
                start = start,
                end = lineEnd,
            )
        }
    }

    fun todoBoundsById(id: String): Rect? {
        val editable = editable() ?: return null
        val span = editable.getSpans(0, editable.length, TodoBoxSpan::class.java)
            .firstOrNull { it.todoId == id } ?: return null
        val start = editable.getSpanStart(span)
        if (start < 0) return null
        val end = todoLineBounds(editable, start).second
        val startLine = layout?.getLineForOffset(start) ?: return null
        val endLine = layout?.getLineForOffset((end - 1).coerceAtLeast(start)) ?: startLine
        val top = (layout?.getLineTop(startLine) ?: 0) - scrollY
        val bottom = (layout?.getLineBottom(endLine) ?: 0) - scrollY
        return Rect(0, top, width, bottom)
    }

    private fun notifyTodoSwipe(span: TodoBoxSpan) {
        val bounds = todoBoundsById(span.todoId) ?: return
        parent?.requestDisallowInterceptTouchEvent(true)
        onTodoSwipe?.invoke(span.todoId, bounds)
    }

    private fun todoSpanForPoint(x: Float, y: Float): TodoBoxSpan? {
        val editable = editable() ?: return null
        val offset = getOffsetForPosition(x, y).coerceIn(0, editable.length)
        val from = (offset - 1).coerceAtLeast(0)
        val to = (offset + 1).coerceAtMost(editable.length)
        return editable.getSpans(from, to, TodoBoxSpan::class.java).firstOrNull()
    }

    private fun todoLineSpanForPoint(x: Float, y: Float): TodoBoxSpan? {
        val editable = editable() ?: return null
        val offset = getOffsetForPosition(x, y).coerceIn(0, editable.length)
        val lineStart = todoLineBounds(editable, offset).first
        return editable.getSpans(
            lineStart,
            (lineStart + 1).coerceAtMost(editable.length),
            TodoBoxSpan::class.java,
        ).firstOrNull()
    }

    private fun deleteTodoLineIfNeeded(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTodoDeleteAt < 80L) return true
        if (selectionStart != selectionEnd) return false
        val editable = editable() ?: return false
        val caret = selectionStart.coerceIn(0, editable.length)
        val (lineStart, lineEnd) = todoLineBounds(editable, caret.coerceAtMost(editable.length))
        android.util.Log.d(
            "TodoDel",
            "caret=$caret line=$lineStart-$lineEnd marker=${editable.getOrNull(lineStart)} content=${editable.subSequence((lineStart + 2).coerceAtMost(lineEnd), lineEnd)}",
        )
        if (lineStart >= editable.length) return false
        if (editable[lineStart] != '☐' && editable[lineStart] != '☑') return false
        val contentStart = (lineStart + 2).coerceAtMost(lineEnd)
        val content = editable.subSequence(contentStart, lineEnd).toString()
        if (caret > contentStart && content.isNotBlank()) return false
        val todoId = editable.getSpans(
            lineStart,
            (lineStart + 1).coerceAtMost(editable.length),
            TodoBoxSpan::class.java,
        ).firstOrNull()?.todoId
        val from = if (lineStart > 0 && editable[lineStart - 1] == '\n') lineStart - 1 else lineStart
        var to = if (lineEnd < editable.length && editable[lineEnd] == '\n') lineEnd + 1 else lineEnd
        val nextStart = lineEnd + 1
        if (nextStart < editable.length) {
            val nextEnd = editable.indexOf('\n', nextStart).let { if (it < 0) editable.length else it }
            if (nextEnd > nextStart && isReminderLine(editable.subSequence(nextStart, nextEnd).toString())) {
                to = if (nextEnd < editable.length) nextEnd + 1 else nextEnd
            }
        }
        editable.replace(from, to, "")
        setSelection(from.coerceAtMost(editable.length))
        todoId?.let { onTodoDeleted?.invoke(it) }
        lastTodoDeleteAt = now
        return true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // A re-wrap changes how many characters fit on a line, so "line 40" is a
        // different piece of text before and after. Anchor on a CHARACTER
        // instead, then put the line holding that character back where it was.
        // Two rules share this one entry point:
        //  - caret (or selection start) fully visible -> keep its line on the
        //    same screen row, so the writer's eye does not follow the re-wrap;
        //  - otherwise -> keep the first visible line's first character on the
        //    first line, so the reader stays on the same sentence.
        val widthChanged = lastLaidOutWidth > 0 && (right - left) != lastLaidOutWidth
        lastLaidOutWidth = right - left
        val heightBefore = height
        if (widthChanged) {
            val l = layout
            if (l != null && l.lineCount > 0 && !restoringScroll &&
                caretAnchorChar < 0 && scrollAnchorChar < 0
            ) {
                // No capture ran before this frame (direct width change): take
                // the anchor from the current layout before super re-wraps it.
                anchorOnTopLine(l)
            }
        }
        super.onLayout(changed, left, top, right, bottom)
        if (widthChanged) {
            val l = layout
            if (l != null && l.lineCount > 0) {
                var restoreY = -1
                if (caretAnchorChar >= 0) {
                    val target = caretAnchorChar.coerceIn(0, (text?.length ?: 0))
                    val line = l.getLineForOffset(target).coerceIn(0, l.lineCount - 1)
                    restoreY = l.getLineTop(line) - caretAnchorTop
                } else if (scrollAnchorChar >= 0) {
                    // Find the line that now holds the anchored character.
                    val target = scrollAnchorChar.coerceIn(0, (text?.length ?: 0).coerceAtLeast(0))
                    val line = l.getLineForOffset(target).coerceIn(0, l.lineCount - 1)
                    restoreY = l.getLineTop(line) - paddingTop + scrollAnchorOffset
                }
                if (restoreY >= 0) {
                    restoringScroll = true
                    scrollTo(0, restoreY)
                    restoringScroll = false
                    // TextView runs bring-caret-into-view after the layout pass.
                    // When the caret sits off screen (or was reset to 0 by a
                    // focus drop) that scrolls the page away from the anchor;
                    // re-apply once on the next message.
                    pendingRestoreY = restoreY
                    anchorGuardUntil = android.os.SystemClock.uptimeMillis() + 600L
                    post {
                        val target = pendingRestoreY
                        if (target >= 0 && !restoringScroll) {
                            scrollTo(0, target)
                        }
                    }
                }
            }
            // No per-glyph flow on a re-wrap. The panel width animates over many
            // frames, and kicking the flow off inside onLayout reset its start
            // clock every frame: hundreds of glyphs hung around their old
            // positions while their targets kept moving, and during the flight
            // super.onDraw is skipped, so the page read as loose, half-misplaced
            // characters instead of text. The anchor above already keeps the
            // line the reader is on; the wrap itself now lands as a clean relayout.
        }
        // 程序替换落地：只在这里起一次「水流」重排。历史上把这段放进造宽动画里
        // 每帧重启，结果几百个字挂在旧位置上散成一片；现在只在替换那一次触发。
        if (flowPending) {
            flowPending = false
            beginFlowIfReady()
        }
        val loc = IntArray(2)
        getLocationInWindow(loc)
        bodyTopInWindow = loc[1]
        if (heightBefore != height) emitGeometry()
    }

    /** First visible line's first character, aligned to the top of the viewport. */
    private fun anchorOnTopLine(l: android.text.Layout) {
        val topInText = scrollY + paddingTop
        val line = l.getLineForVertical(topInText)
        scrollAnchorChar = l.getLineStart(line)
        // Snap the line to the viewport top instead of keeping a partial
        // overhang: when the first line was half cut off, keeping that overhang
        // pushes it out of view after the re-wrap and the next line becomes the
        // first one. The requirement is that the same character stays on the
        // first visible line, not at the same pixel.
        scrollAnchorOffset = 0
    }

    /** Width settled: the anchor has done its job, let later scrolls be live. */
    fun releaseScrollAnchor() {
        scrollAnchorChar = -1
        scrollAnchorOffset = 0
        caretAnchorChar = -1
        caretAnchorTop = 0
        // Must drop the guard too: while it stays on, scrollTo pins every attempt
        // back onto the anchor and the page cannot be scrolled at all.
    }

    /**
     * Snapshot the re-wrap position before the panel starts animating. The first
     * animation frame already re-wraps the text, so anchoring from inside
     * onLayout would capture the position AFTER the change and drift.
     *
     * A visible caret (or the start of a selection) wins: its line must stay on
     * the same screen row. Only when it is not fully visible — or the body has
     * no caret at all — does the first visible line's first character anchor
     * the page.
     */
    fun captureScrollAnchor() {
        val l = layout
        if (l == null || l.lineCount == 0) {
            releaseScrollAnchor()
            return
        }
        val caret = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
            .coerceAtMost(l.text.length)
        val caretLine = l.getLineForOffset(caret)
        val lineTop = l.getLineTop(caretLine) - scrollY
        val lineBottom = l.getLineBottom(caretLine) - scrollY
        val viewportBottom = height - paddingBottom
        val caretVisible = caretAnchorAllowed() && lineTop >= 0 && lineBottom <= viewportBottom
        if (caretVisible) {
            caretAnchorChar = caret
            caretAnchorTop = lineTop
            scrollAnchorChar = -1
            scrollAnchorOffset = 0
        } else {
            caretAnchorChar = -1
            caretAnchorTop = 0
            anchorOnTopLine(l)
        }
    }

    /**
     * The click that opens/closes the panel already took the anchor before the
     * row re-laid out. Only fill in when nothing was captured (e.g. the panel
     * was opened by another path), otherwise the first animation frame would
     * overwrite it with a position TextView has already scrolled away from.
     */
    fun captureScrollAnchorIfNone() {
        if (caretAnchorChar < 0 && scrollAnchorChar < 0) captureScrollAnchor()
    }

    /** The body owns a caret the writer can actually see. */
    private fun caretAnchorAllowed(): Boolean {
        if (!hasFocus()) return false
        // A selection has no blinking caret, but its start is still the point
        // the writer is looking at; a plain caret must also be visible.
        return selectionStart != selectionEnd || isCursorVisible
    }

    /**
     * Snapshot where every visible character sits before a re-wrap, so the flow
     * layer can interpolate each one to its new place. Only the rows on screen
     * take part; text above and below is off-canvas anyway.
     */
    private fun captureGlyphPositions() {
        val l = layout ?: run { flowFrom.clear(); return }
        val startLine = l.getLineForVertical(scrollY).coerceAtLeast(0)
        val endLine = l.getLineForVertical(scrollY + height).coerceAtMost(l.lineCount - 1)
        flowFrom.clear()
        for (line in startLine..endLine) {
            val lineStart = l.getLineStart(line)
            val lineEnd = l.getLineEnd(line)
            val baseline = l.getLineBaseline(line).toFloat() + paddingTop
            for (i in lineStart until lineEnd) {
                if (Character.isWhitespace(text?.getOrNull(i) ?: ' ')) continue
                flowFrom[i] = floatArrayOf(l.getPrimaryHorizontal(i) + paddingLeft, baseline)
            }
        }
        flowTarget.clear()
        flowAnimating = false
    }

    /** New positions after the wrap, plus the start of the interpolation. */
    private fun beginFlowIfReady() {
        if (flowFrom.isEmpty()) return
        val l = layout ?: return
        flowTarget.clear()
        flowLag.clear()
        var moved = false
        for ((index, from) in flowFrom) {
            if (index >= (text?.length ?: 0)) continue
            val line = l.getLineForOffset(index)
            val x = l.getPrimaryHorizontal(index) + paddingLeft
            val y = l.getLineBaseline(line).toFloat() + paddingTop
            flowTarget[index] = floatArrayOf(x, y)
            // 带状错落：离编辑点越远，起跑越晚（0 / .06 / .12 / .18）。
            flowLag[index] = ((line - flowEditLine) / 2).coerceIn(0, 3) * .06f
            if (abs(x - from[0]) > 0.5f || abs(y - from[1]) > 0.5f) moved = true
        }
        if (!moved) {
            flowAnimating = false
            return
        }
        flowStartAt = -1L
        flowAnimating = true
        flowLayout = l
        invalidate()
    }

    /**
     * 同柔落位曲线：欠阻尼阶跃响应（ζ=.52、ω=√300），会过冲一次再收回，
     * 和项目里 spring(.52, 300) 同一套参数，落点回到 1。
     */
    private fun flowSpringAt(elapsedMs: Float): Float {
        val t = (elapsedMs / 1000f).coerceAtLeast(0f)
        val wd = FLOW_OMEGA * kotlin.math.sqrt(1f - FLOW_ZETA * FLOW_ZETA)
        val e = kotlin.math.exp(-FLOW_ZETA * FLOW_OMEGA * t)
        return 1f - e * (
            kotlin.math.cos(wd * t) + (FLOW_ZETA * FLOW_OMEGA / wd) * kotlin.math.sin(wd * t)
            )
    }

    /** 主时钟：返回已经过去的毫秒数（延迟算在各字身上）。 */
    private fun flowElapsed(): Float {
        if (flowStartAt < 0L) flowStartAt = android.os.SystemClock.uptimeMillis()
        return (android.os.SystemClock.uptimeMillis() - flowStartAt).toFloat()
    }

    /** 全部跑完（含最晚那条带）就收工。 */
    private fun flowDone(elapsed: Float): Boolean = elapsed >= FLOW_MS * 1.25f

    private fun drawFlowingText(canvas: Canvas, elapsed: Float) {
        val textValue = text ?: return
        canvas.save()
        canvas.translate(0f, -scrollY.toFloat())
        for ((index, from) in flowFrom) {
            if (index >= textValue.length) continue
            val to = flowTarget[index] ?: continue
            val lag = flowLag[index] ?: 0f
            val progress = flowSpringAt(elapsed - lag * FLOW_MS).coerceIn(0f, 1.12f)
            val x = from[0] + (to[0] - from[0]) * progress
            val y = from[1] + (to[1] - from[1]) * progress
            canvas.drawText(textValue, index, index + 1, x, y, paint)
        }
        canvas.restore()
    }

    init {
        selectionListener = onSelectionChanged
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isLongClickable = false
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            post { emitGeometry() }
        }
    }

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
        renderRevision++
        onBodyScroll?.invoke()
        emitGeometry()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) clearSelectionMenu()
    }

    override fun onDetachedFromWindow() {
        resetHandleSpring()
        clearSelectionMenu()
        super.onDetachedFromWindow()
    }

    override fun performLongClick(): Boolean = false

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? = null

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        // TextView recreates native handles when the selection moves; clear
        // them again so drag never paints a second, system-coloured handle.
        clearNativeSelectionHandle("setTextSelectHandle")
        clearNativeSelectionHandle("setTextSelectHandleLeft")
        clearNativeSelectionHandle("setTextSelectHandleRight")
        hideNativeSelectionHandles()
        val value = text
        if (!redirectingSelection && value != null && selStart >= 0 && selEnd >= 0) {
            val touchesReminder = if (selStart == selEnd) {
                reminderLineAt(selStart)
            } else {
                reminderLineAt(selStart) || reminderLineAt((selEnd - 1).coerceAtLeast(0))
            }
            if (touchesReminder) {
                val anchor = selStart.coerceIn(0, value.length)
                val lineStart = todoLineBounds(value, anchor).first
                val target = (lineStart - 1).coerceIn(0, value.length)
                redirectingSelection = true
                setSelection(target)
                redirectingSelection = false
                return
            }
        }
        markCaretActive()
        selectionListener?.invoke(text.toString(), selStart, selEnd)
        onTodoCursor?.invoke(activeTodoAt(selStart))
        emitGeometry()
        // A transient collapsed state while dragging a handle must not close
        // the selection toolbar. The toolbar follows the real body selection.
        if (handleDrag < 0 && selectionMenuActive && selEnd <= selStart) {
            selectionMenuActive = false
            selectionBarActive = false
            onSelectionMenu?.invoke(0, 0, 0, 0)
            emitGeometry()
        }
    }

    private fun notifySelectionGeometry() {
        val l = layout ?: return
        val s = selectionStart
        val e = selectionEnd
        if (s < 0 || e <= s) return
        val startLine = l.getLineForOffset(s)
        val endLine = l.getLineForOffset(e)
        // getLineBounds is in view coordinates and already accounts for scroll,
        // unlike Layout.getLineTop/Bottom.
        val x1 = l.getPrimaryHorizontal(s).toInt()
        val x2 = l.getPrimaryHorizontal(e).toInt()
        val bounds = android.graphics.Rect()
        getLineBounds(startLine, bounds)
        val top = bounds.top
        getLineBounds(endLine, bounds)
        val bottom = bounds.bottom
        onSelectionMenu?.invoke(x1, top, x2, bottom)
    }

    fun clearSelectionMenu() {
        selectionBarActive = false
        selectionMenuActive = false
        handleDrag = -1
        resetHandleSpring()
        invalidate()
        emitGeometry()
    }

    fun collapseSelection() {
        val caret = selectionEnd.coerceIn(0, text.length)
        setSelection(caret)
        clearSelectionMenu()
    }

    fun deleteSelection() {
        val s = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val e = maxOf(selectionStart, selectionEnd).coerceAtMost(text.length)
        if (s < e) {
            text.replace(s, e, "")
            setSelection(s.coerceAtMost(text.length))
        }
        clearSelectionMenu()
    }

    fun refreshSelectionMenu() {
        if (selectionBarActive) notifySelectionGeometry()
    }

    fun menuAction(id: Int) {
        onTextContextMenuItem(id)
    }

    /** True from a tap until ~1.2 s later: height changes in that window (the
     *  IME sliding in) re-apply the caret scroll instead of leaving the caret
     *  where the shorter viewport put it. */
    private var caretScrollPending = false
    /** IME heights mirrored from Compose: used to reserve the keyboard's space
     *  on the very first frame of a tap, so the page lands in one move instead
     *  of following the keyboard in for 300 ms. */
    var imeInsetPx: Int = 0
    // Seeded with a rough keyboard estimate so the first tap after a cold
    // start can reserve its space; the real height replaces it on first open.
    var lastImePx: Int = (resources.displayMetrics.heightPixels * .40f).toInt()

    /** Puts the caret line comfortably inside the area above the dock. Called
     *  on a tap and while the IME resizes, never on every keystroke: the
     *  native bring-into-view is enough for typing. */
    private var caretAnimator: android.animation.ValueAnimator? = null

    /** A tap asked for a scroll the page cannot do yet: while the keyboard is
     *  still closed the scroll range does not include the space the keyboard is
     *  about to take, so the target clamps to the current end and nothing
     *  moves. Wait for the resize to finish, then scroll once. */
    private var caretSettlePending = false
    /** While >= 0, the page follows the shrinking viewport instead of waiting
     *  for the keyboard to land: tapping the last line has no scroll range at
     *  all until the IME takes its space, so the only "immediate" scroll is one
     *  glued to the keyboard's top edge. It always ends at the same clamp, so
     *  there is no rebound. */
    private var caretFollowTarget = -1
    /** True when the tap started with the keyboard closed, i.e. the page is
     *  about to shrink and a target past the current range becomes reachable. */
    private var caretWindowWillResize = false
    private val caretSettle = Runnable {
        if (!caretSettlePending) return@Runnable
        caretSettlePending = false
        caretFollowTarget = -1
        if (INFO_CARET) android.util.Log.d("MOYU_CARET", "settle scrollY=$scrollY h=$height")
        ensureCaretVisible(settled = true)
        // Hold the native bring off until the ~190 ms scroll has landed.
        removeCallbacks(caretFinish)
        postDelayed(caretFinish, 260L)
    }
    /** Ends the tap's suppression window; the resize is over by then. */
    private val caretFinish = Runnable { caretScrollPending = false }

    /** Starts the tap window. The IME takes ~1 s to settle on this device, so
     *  the window outlives it: ending it earlier let the native bring-into-view
     *  jump the page before the settle scroll could run. */
    private fun beginCaretScrollWindow() {
        caretWindowWillResize = imeInsetPx <= 0
        caretScrollPending = true
        caretSettlePending = false
        caretFollowTarget = -1
        removeCallbacks(caretFinish)
        removeCallbacks(caretSettle)
        postDelayed({
            caretScrollPending = false
            removeCallbacks(caretSettle)
            caretSettlePending = false
            caretFollowTarget = -1
        }, 1500L)
    }

    /** Scrolls to [target] over ~140 ms so a tap at the end reads as a slide
     *  rather than a one-frame jerk ("抽一下"). */
    private fun animateCaretScroll(target: Int) {
        caretAnimator?.cancel()
        val from = scrollY
        val distance = kotlin.math.abs(target - from)
        if (INFO_CARET) android.util.Log.d("MOYU_CARET", "anim from=$from target=$target dist=$distance h=$height maxY=${contentMaxScroll()}")
        if (distance <= 4) return
        val duration = (70L + distance / 6L).coerceIn(90L, 190L)
        caretAnimator = android.animation.ValueAnimator.ofInt(from, target).apply {
            this.duration = duration
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { scrollTo(scrollX, it.animatedValue as Int) }
            start()
        }
    }

    fun ensureCaretVisible() = ensureCaretVisible(settled = false)

    private fun ensureCaretVisible(settled: Boolean) {
        if (!hasFocus()) return
        // Once a tap is waiting for the page to stop resizing, the intermediate
        // calls (IME opening, first frames) must not move the page: they would
        // scroll a little now and leave the rest for the settle pass.
        if (!settled && caretSettlePending) return
        // A follow already owns the scroll until the resize ends.
        if (!settled && caretFollowTarget >= 0) return
        val l = layout ?: return
        val len = text?.length ?: 0
        val caret = selectionEnd.coerceIn(0, len)
        val line = l.getLineForOffset(caret)
        val top = l.getLineTop(line)
        val bottom = l.getLineBottom(line)
        val reserved = if (caretScrollPending) {
            (lastImePx - imeInsetPx).coerceAtLeast(0)
        } else {
            0
        }
        val visibleBottom = (height - dockBottomInset - reserved).coerceAtLeast(0)
        if (visibleBottom <= 0) return
        val comfortableBottom = (visibleBottom - visibleBottom * .28f).toInt()
        val maxY = contentMaxScroll()
        if (INFO_CARET) android.util.Log.d(
            "MOYU_CARET",
            "ensure focus=${hasFocus()} h=$height dock=$dockBottomInset reserved=$reserved " +
                "ime=$imeInsetPx last=$lastImePx pending=$caretScrollPending caret=$caret " +
                "top=$top bottom=$bottom scrollY=$scrollY visBottom=$visibleBottom " +
                "comf=$comfortableBottom maxY=$maxY",
        )
        // Same content-vs-screen fix as bringPointIntoView: compare the caret's
        // on-screen position, not its raw layout offset, or the 320 ms check
        // scrolls again after an already-correct tap scroll.
        if (bottom - scrollY > comfortableBottom) {
            val wanted = (top - visibleBottom * .34f).toInt()
            // Keyboard closed and the target is past the current end of the
            // scroll range: the range only grows when the IME takes its space
            // (a short chapter fits the tall page, or the tap is on the last
            // lines). Follow the resize instead of waiting for it to land.
            if (!settled && caretScrollPending && caretWindowWillResize && wanted > maxY) {
                caretFollowTarget = wanted
                return
            }
            animateCaretScroll(wanted.coerceIn(0, maxY))
        } else if (top - scrollY < 0) {
            animateCaretScroll(top.coerceIn(0, maxY))
        }
        caretSettlePending = false
        caretFollowTarget = -1
    }

    /** TextView's own bring scrolls the caret to the very bottom edge, which
     *  undid the comfortable position a tap had just set. Keep the native
     *  behavior (minimal scroll) but leave breathing room above the dock. */
    override fun bringPointIntoView(offset: Int): Boolean {
        // A tap already scrolled once with the keyboard's space reserved. Any
        // bring during the IME slide-in would move the page a second time,
        // which is the visible "twitch" on a tap at the end of the text.
        if (caretScrollPending) {
            if (INFO_CARET) android.util.Log.d("MOYU_CARET", "bring suppressed offset=$offset scrollY=$scrollY h=$height")
            return false
        }
        val l = layout ?: return super.bringPointIntoView(offset)
        // Same reserved-IME arithmetic as ensureCaretVisible: while the tap's
        // caret scroll is pending, the viewport the caret is judged against is
        // held at its final size, so the page does not crawl in with the IME.
        val reserved = if (caretScrollPending) {
            (lastImePx - imeInsetPx).coerceAtLeast(0)
        } else {
            0
        }
        val visibleBottom = (height - dockBottomInset - reserved).coerceAtLeast(0)
        if (visibleBottom <= 0) return super.bringPointIntoView(offset)
        val o = offset.coerceIn(0, text?.length ?: 0)
        val line = l.getLineForOffset(o)
        val top = l.getLineTop(line)
        val bottom = l.getLineBottom(line)
        val margin = (visibleBottom * .16f).toInt()
        val limit = (visibleBottom - margin).coerceAtLeast(0)
        val maxY = contentMaxScroll()
        // Layout coordinates are content coordinates: the caret's on-screen
        // bottom is (bottom - scrollY). Scroll so that it sits at the limit.
        if (bottom - scrollY > limit) {
            val target = (bottom - limit).coerceIn(0, maxY)
            if (target != scrollY) {
                scrollTo(scrollX, target)
                return true
            }
        } else if (top - scrollY < 0) {
            scrollTo(scrollX, top.coerceIn(0, maxY))
            return true
        }
        return false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // 高度随章节头实时收起而变化时，TextView 会把光标 bring 回视口、
        // 整页被拽回顶部（2026-11-15 实测「滚不动 + 光标跳回开头」）。
        // 记住旧的 scrollY，布局落定后放回去。
        val keepScroll = scrollY
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldh > 0 && h != oldh && !caretScrollPending && keepScroll > 0) {
            pendingRestoreY = keepScroll
            anchorGuardUntil = android.os.SystemClock.uptimeMillis() + 400L
            post { scrollTo(0, keepScroll.coerceIn(0, contentMaxScroll())) }
        }
        // One correction after the resize stops, never one per frame: following
        // the keyboard frame by frame is what made the page crawl for 300 ms.
        if (caretScrollPending && h != oldh) {
            removeCallbacks(caretSettle)
            postDelayed(caretSettle, 70L)
            val follow = caretFollowTarget
            if (follow >= 0) {
                val goal = follow.coerceAtMost(contentMaxScroll())
                if (goal != scrollY) {
                    if (INFO_CARET) android.util.Log.d("MOYU_CARET", "follow target=$follow goal=$goal h=$h scrollY=$scrollY")
                    scrollTo(scrollX, goal)
                }
            }
        }
        // The keyboard's space is gone again: a short chapter fits the tall
        // page, so do not leave it scrolled into blank space under the text.
        // TextView does not clamp the offset on a grow by itself.
        val maxNow = contentMaxScroll()
        if (scrollY > maxNow) {
            if (INFO_CARET) android.util.Log.d("MOYU_CARET", "clamp scrollY=$scrollY max=$maxNow h=$h")
            scrollTo(scrollX, maxNow)
        }
        if (INFO_CARET) android.util.Log.d(
            "MOYU_CARET",
            "size h=$h oldh=$oldh scrollY=$scrollY pending=$caretScrollPending ime=$imeInsetPx last=$lastImePx",
        )
        // No per-frame re-scroll here: following the keyboard as it slides in
        // made the page crawl for 300 ms after the tap. The tap already lands
        // with the keyboard space reserved, and one correction runs after the
        // inset settles.
    }

    /**
     * 开合能力栏前由 Editor 调用：钉住当前可视位置。
     *
     * 时限只覆盖开合动画（300ms）与收尾那次重排；下一次按下立刻解钉，
     * 所以它永远拦不到读者自己的滚动。
     */
    fun armScrollPin(y: Int, holdMs: Long = 700L) {
        pinScrollY = y.coerceAtLeast(0)
        pinUntil = android.os.SystemClock.uptimeMillis() + holdMs
        if (scrollY != pinScrollY) scrollTo(scrollX, pinScrollY)
    }

    private fun contentMaxScroll(): Int =
        (computeVerticalScrollRange() - computeVerticalScrollExtent() + dockBottomInset)
            .coerceAtLeast(0)

    override fun scrollTo(x: Int, y: Int) {
        // 能力栏开合：重排会把 scrollY 拉回 0，这里当场换回钉住的位置。
        // 夹取只在 contentMaxScroll() 已经算得出来时才做——重排最早那一两次
        // 调用里它可能还是 0，直接 coerce 会把位置按到页首，正好是要修的那个 bug。
        val pin = pinScrollY
        if (pin >= 0 && android.os.SystemClock.uptimeMillis() < pinUntil && y != pin) {
            val max = contentMaxScroll()
            super.scrollTo(x, if (max > 0) pin.coerceIn(0, max) else pin)
            return
        }
        // A local body edit (ticking a box, writing a reminder line) must not
        // move the page, but TextView defers the caret bring to onPreDraw and
        // flushes it a frame or two after the edit. Hold the offset until the
        // next touch, with a hard time box.
        val keepY = keepViewportY
        if (keepY >= 0 && y != keepY &&
            android.os.SystemClock.uptimeMillis() - keepViewportAt < 600L
        ) {
            super.scrollTo(x, keepY.coerceIn(0, contentMaxScroll()))
            return
        }
        // No directional guard here. An earlier attempt to block TextView's
        // bringPointIntoView re-wrap scroll lived at this exact spot and left a
        // flag that was never reset on every path, which braked normal scrolling
        // just short of the top. The panel's own anchor logic in onLayout is
        // enough; the scroll position must never be gated here.
        //
        // The one exception is a yanked-to-0 scroll while the re-wrap anchor is
        // live: TextView does that when the caret sat off screen, and it would
        // undo the anchor. It is time-boxed to the layout window, never a
        // permanent gate.
        val anchor = pendingRestoreY
        if (y == 0 && anchor > 0 &&
            android.os.SystemClock.uptimeMillis() < anchorGuardUntil
        ) {
            super.scrollTo(x, anchor.coerceIn(0, contentMaxScroll()))
            return
        }
        super.scrollTo(x, y.coerceIn(0, contentMaxScroll()))
    }

    private fun insertNewlineAtCursor() {
        if (reminderLineAt(selectionStart)) return
        if (todoHasReminderAtEnd()) return
        val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val end = maxOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val editable = text
        val lineStart = if (start == 0) 0 else editable?.lastIndexOf('\n', start - 1)?.plus(1) ?: 0
        val onTodoLine = editable != null && isTodoLineAt(editable, lineStart)
        val insert = if (autoIndent && !onTodoLine) "\n　　" else "\n"
        text?.replace(start, end, insert)
        setSelection((start + insert.length).coerceAtMost(text?.length ?: 0))
        markCaretActive()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (inputLocked) return true
        if (reminderLineAt(selectionStart) &&
            (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_ENTER)
        ) return true
        if (keyCode == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed) {
            insertNewlineAtCursor()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL && todoHasReminderAtEnd()) return true
        if (keyCode == KeyEvent.KEYCODE_DEL && deleteTodoLineIfNeeded()) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, false) {
            override fun performEditorAction(actionCode: Int): Boolean {
                if (inputLocked) return true
                insertNewlineAtCursor()
                return true
            }

            override fun sendKeyEvent(event: KeyEvent?): Boolean {
                if (inputLocked) return true
                if (event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    insertNewlineAtCursor()
                    return true
                }
                if (event?.keyCode == KeyEvent.KEYCODE_FORWARD_DEL && todoHasReminderAtEnd()) {
                    return true
                }
                if (event?.keyCode == KeyEvent.KEYCODE_DEL && deleteTodoLineIfNeeded()) {
                    return true
                }
                return super.sendKeyEvent(event)
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (inputLocked) return true
                if (reminderLineAt(selectionStart)) return true
                if (text?.toString() == "\n") {
                    insertNewlineAtCursor()
                    return true
                }
                return super.commitText(text, newCursorPosition)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (inputLocked) return true
                if (reminderLineAt(selectionStart)) return true
                if (afterLength > 0 && todoHasReminderAtEnd()) return true
                if (beforeLength > 0 && deleteTodoLineIfNeeded()) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
    }

    /**
     * 长按选中一个词（2026-11-15 用户定：「先选一个词，再拖」）。
     *
     * 以前按标点切，长句会一下吞一大块（用户报「选中的范围不对」）。改成走系统的
     * 词边界：中文一次给 2–4 个字的词，落在标点上就退到相邻的词。拖手柄再扩句。
     */
    private fun wordRangeAt(offset: Int): Pair<Int, Int> {
        val value = text?.toString() ?: return offset to offset
        val len = value.length
        if (len == 0) return 0 to 0
        val at = offset.coerceIn(0, len - 1)
        val delimiters = "，。！？；：、,.!?;:\n\"'“”‘’（）()【】[] \t　"
        val word = runCatching {
            val it = android.icu.text.BreakIterator.getWordInstance(java.util.Locale.CHINESE)
            it.setText(value)
            var s = it.preceding(at + 1)
            var e = it.following(at + 1)
            if (s < 0) s = 0
            if (e <= s) e = (at + 1).coerceAtMost(len)
            // 手指落在标点/空白上：词是一个符号，退到它前面那个真词。
            val token = value.substring(s, e)
            if (token.all { it in delimiters }) {
                val ps = it.preceding(s)
                if (ps >= 0 && ps < s) {
                    val pe = s
                    s = ps
                    e = pe
                }
            }
            s to e
        }.getOrNull()
        var (start, end) = word ?: (at to (at + 1).coerceAtMost(len))
        if (end <= start) {
            start = at.coerceAtMost(len - 1)
            end = (start + 1).coerceAtMost(len)
        }
        // 兜底：ICU 偶尔把一个长串当单词，收着点，最多 12 字。
        if (end - start > 12) end = start + 12
        return start.coerceIn(0, len) to end.coerceIn(0, len)
    }

    private fun performLongPress() {
        android.util.Log.d("SelDebug", "performLongPress drag=$dragging consumed=$longPressConsumed")
        if (dragging || longPressConsumed) return
        val value = text ?: return
        val len = value.length
        if (len == 0) return
        val offset = getOffsetForPosition(downX, downY).coerceIn(0, len)
        val (start, end) = wordRangeAt(offset)
        selectionAnchor = start
        setSelection(start, end)
        isCursorVisible = true
        longPressConsumed = true
        selectingAfterLongPress = false
        selectionBarActive = true
        selectionMenuActive = true
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        notifySelectionGeometry()
        invalidate()
        emitGeometry()
    }

    private data class CaretGeom(val x: Float, val top: Float, val bottom: Float)

    private fun caretGeom(offset: Int): CaretGeom? {
        val l = layout ?: return null
        val fm = getPaint().fontMetrics
        val baseline = l.getLineBaseline(l.getLineForOffset(offset)) - scrollY
        val top = baseline + fm.ascent
        val x = totalPaddingLeft + l.getPrimaryHorizontal(offset)
        return CaretGeom(x, top, baseline + fm.descent)
    }

    private fun emitGeometry() {
        if (!selectionBarActive) {
            onGeometry?.invoke(null)
            return
        }
        val s = selectionStart
        val e = selectionEnd
        if (s < 0 || e <= s) {
            onGeometry?.invoke(null)
            return
        }
        val sg = caretGeom(s) ?: return
        val eg = caretGeom(e) ?: return
        // 手柄可以落后于真实边界（水滴被拉着走），竖线永远画在真位置上。
        val sx = if (handleSpringOn && !handleSpringX.isNaN() && handleSpringSide == 0) handleSpringX else sg.x
        val ex = if (handleSpringOn && !handleSpringX.isNaN() && handleSpringSide == 1) handleSpringX else eg.x
        val sb = buildHandleBox(sx, sg.top, sg.bottom, above = true) ?: return
        val eb = buildHandleBox(ex, eg.top, eg.bottom, above = false) ?: return
        onGeometry?.invoke(
            SelectionGeometry(
                startCaretX = sg.x,
                startTop = sg.top,
                startBottom = sg.bottom,
                startHandle = sb,
                endCaretX = eg.x,
                endTop = eg.top,
                endBottom = eg.bottom,
                endHandle = eb,
                active = true,
                // 形变只在「手指还按着手柄」时存在：松手后位置继续弹到落点，
                // 但形状立刻归位，否则会留下一个被压扁的水滴（2026-11-15 实测）。
                dragSide = if (handleSpringOn && handleDrag >= 0) handleSpringSide else -1,
                dragPull = if (handleDrag >= 0) handleDragPull else 0f,
                dragDirX = handleDragDirX,
                dragDirY = handleDragDirY,
            ),
        )
    }

    private fun buildHandleBox(
        x: Float,
        caretTop: Float,
        caretBottom: Float,
        above: Boolean,
    ): HandleBox? {
        val drawable = selectHandleDrawable ?: return null
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w <= 0 || h <= 0) return null
        val d = resources.displayMetrics.density
        val gap = 5f * d
        val left = x - w / 2f
        // 不再钳进正文容器：选到第一行时手柄本来会坐到文字上（用户报「被挤压、上端位置不对」）。
        // 允许画到正文上边界之外，由整页浮层接住它。
        val top = if (above) {
            caretTop - h - gap
        } else {
            caretBottom + gap
        }
        return HandleBox(left, top, left + w, top + h)
    }

    private fun handleBounds(offset: Int): HandleBox? {
        val geom = caretGeom(offset) ?: return null
        return buildHandleBox(
            geom.x,
            geom.top,
            geom.bottom,
            above = offset == selectionStart,
        )
    }

    private fun handleCenters(): Pair<PointF, PointF>? {
        val s = selectionStart
        val e = selectionEnd
        if (s < 0 || e <= s) return null
        val a = handleBounds(s) ?: return null
        val b = handleBounds(e) ?: return null
        return PointF(a.centerX, a.centerY) to PointF(b.centerX, b.centerY)
    }

    /** Uptime until which the caret is drawn solid, ignoring the blink phase.
     *  Native editors restart the blink whenever the caret moves; this custom
     *  caret only rode a global 500 ms clock, so pressing Enter could land in
     *  the "off" half and the caret vanished right after the newline. */
    private var caretSolidUntil = 0L

    private fun markCaretActive() {
        if (!isAttachedToWindow) return
        caretSolidUntil = android.os.SystemClock.uptimeMillis() + 650L
        invalidate()
    }

    /**
     * 让光标马上可见（2026-11-15，用户报「光标莫名其妙消失」）。
     *
     * 以前各处只写 `isCursorVisible = true`，不重绘；自绘光标的闪烁循环一旦在
     * 某次早退里停掉，就得等下一次打字/滚动把它叫醒——看起来就是「光标没了」。
     * 统一走这一个入口：置可见 + 650ms 常亮 + 立刻重绘。
     */
    fun revealCaret() {
        isCursorVisible = true
        markCaretActive()
    }

    private fun drawSelfCursor(canvas: Canvas) {
        // 早退也要排下一次重绘：闪烁循环停摆后没有任何东西能把它叫醒，
        // 这是「滑动/打字后光标一直不亮」的另一半根因。只在还有焦点时续排，
        // 免得失焦的编辑器每 500ms 空跑一次。
        if (!hasFocus() || !isCursorVisible || selectionStart != selectionEnd) {
            if (hasFocus() && isAttachedToWindow) postInvalidateDelayed(500L)
            return
        }
        val layout = layout ?: run {
            if (isAttachedToWindow) postInvalidateDelayed(120L)
            return
        }
        val offset = selectionStart.coerceIn(0, layout.text.length)
        val line = layout.getLineForOffset(offset)
        val step = paperLineStep.takeIf { it > 0f } ?: run {
            if (isAttachedToWindow) postInvalidateDelayed(120L)
            return
        }
        val fm = paint.fontMetrics
        val gap = ((step - (fm.descent - fm.ascent)) * .35f)
            .coerceIn(2f * resources.displayMetrics.density, 6f * resources.displayMetrics.density)
        val bottom = layout.getLineBaseline(line) + fm.descent + gap
        val top = bottom - selfCursorHeight
        val x = compoundPaddingLeft + layout.getPrimaryHorizontal(offset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = selfCursorColor
            style = Paint.Style.FILL
        }
        val now = android.os.SystemClock.uptimeMillis()
        val solid = now < caretSolidUntil
        if (solid || (now / 500L) % 2L == 0L) {
            canvas.drawRoundRect(
                x,
                top,
                x + selfCursorWidth,
                bottom,
                selfCursorWidth / 2f,
                selfCursorWidth / 2f,
                paint,
            )
        }
        // While solid, wake up exactly when the solid window ends (to start
        // blinking); otherwise at the next blink boundary.
        val delay = if (solid) {
            (caretSolidUntil - now).coerceIn(16L, 500L)
        } else {
            500L - (now % 500L)
        }
        postInvalidateDelayed(delay)
    }

    override fun onDraw(canvas: Canvas) {
        drawPaper(canvas)
        if (flowAnimating) {
            // Every glyph flies from where it was to where it now belongs, so the
            // re-wrap reads as flowing text instead of a jump. The caret is not
            // drawn during this window: it would otherwise sit still while its
            // own character slides away from it.
            val elapsed = flowElapsed()
            if (flowDone(elapsed)) {
                flowAnimating = false
                super.onDraw(canvas)
                drawSelfCursor(canvas)
            } else {
                drawFlowingText(canvas, elapsed)
                postInvalidateOnAnimation()
            }
        } else {
            super.onDraw(canvas)
            drawSelfCursor(canvas)
        }
        // Selection carets/handles are drawn by SelectionOverlay, outside the
        // TextView's scroll transform.
    }

    private fun drawSelectionHandle(
        canvas: Canvas,
        bounds: android.graphics.RectF?,
        x: Float,
        caretBottom: Float,
    ) {
        val drawable = selectHandleDrawable ?: return
        if (bounds == null) return
        drawable.setBounds(
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt(),
            bounds.bottom.toInt(),
        )
        drawable.draw(canvas)
    }

    private fun showSelectionPopup() {
        selectionPopup?.dismiss()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(android.graphics.Color.rgb(30, 32, 30))
                setStroke(dp(1), android.graphics.Color.argb(45, 255, 255, 255))
            }
        }
        val actions = buildList {
            add("复制" to android.R.id.copy)
            add("剪切" to android.R.id.cut)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard?.hasPrimaryClip() == true) add("粘贴" to android.R.id.paste)
            add("全选" to android.R.id.selectAll)
        }
        val popup = PopupWindow(context)
        if (onProofRequest != null) {
            row.addView(TextView(context).apply {
                text = "校核"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(11), dp(7), dp(11), dp(7))
                setOnClickListener {
                    popup.dismiss()
                    onProofRequest?.invoke()
                }
            })
        }
        actions.forEach { (label, action) ->
            row.addView(TextView(context).apply {
                text = label
                setTextColor(android.graphics.Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(11), dp(7), dp(11), dp(7))
                setOnClickListener {
                    onTextContextMenuItem(action)
                    popup.dismiss()
                }
            })
        }
        popup.contentView = row
        popup.isOutsideTouchable = true
        popup.isFocusable = false
        popup.elevation = dp(10).toFloat()
        selectionPopup = popup
        val location = IntArray(2)
        getLocationOnScreen(location)
        val x = (location[0] + downX).toInt().coerceAtLeast(dp(8))
        val y = (location[1] + downY - dp(58)).toInt().coerceAtLeast(dp(8))
        popup.showAtLocation(this, Gravity.TOP or Gravity.START, x, y)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // A new gesture owns the scroll from here on.
                keepViewportY = -1
                pinScrollY = -1
                removeCallbacks(flingDriver)
                if (selectionBarActive) {
                    val a = handleBounds(selectionStart.coerceAtLeast(0))
                    val b = handleBounds(selectionEnd.coerceAtLeast(0))
                    android.util.Log.d(
                        "SelDebug",
                        "DOWN ${event.x},${event.y} a=$a b=$b active=$selectionBarActive",
                    )
                    if (a != null && b != null) {
                        val d = resources.displayMetrics.density
                        // 命中区 24dp → 40dp：手柄视觉大小不变，但手指偏 1–2 毫米也抓得住。
                        val pad = 40f * d
                        // handleBounds is in layout/content coordinates. MotionEvent
                        // y is in view coordinates, so add scrollY back before hit
                        // testing; otherwise an IME scroll makes the handles unhittable.
                        // Overlay and TextView have different window origins; map
                        // the touch into the overlay's local coordinate space.
                        val hitY = event.y + bodyTopInWindow - overlayTopInWindow
                        fun hit(r: HandleBox): Boolean =
                            event.x >= r.left - pad && event.x <= r.right + pad &&
                                hitY >= r.top - pad && hitY <= r.bottom + pad
                        if (hit(a)) {
                            beginHandleDrag(0)
                            return true
                        }
                        if (hit(b)) {
                            beginHandleDrag(1)
                            return true
                        }
                        val ac = PointF(a.centerX, a.centerY)
                        val bc = PointF(b.centerX, b.centerY)
                        val halfW = 52f * d
                        val above = 64f * d
                        val below = 72f * d
                        fun hitCenter(c: PointF): Boolean =
                            kotlin.math.abs(event.x - c.x) <= halfW &&
                                event.y >= c.y - above && event.y <= c.y + below
                        if (hitCenter(ac)) {
                            beginHandleDrag(0)
                            return true
                        }
                        if (hitCenter(bc)) {
                            beginHandleDrag(1)
                            return true
                        }
                        // A tap inside the selected paragraph should grab the
                        // nearest handle instead of dismissing the handles.
                        val s = selectionStart.coerceAtLeast(0)
                        val e = selectionEnd.coerceAtLeast(0)
                        val sg = caretGeom(s)
                        val eg = caretGeom(e)
                        if (sg != null && eg != null) {
                            val minX = minOf(sg.x, eg.x) - 28f * d
                            val maxX = maxOf(sg.x, eg.x) + 28f * d
                            val top = minOf(sg.top, eg.top) - 10f * d
                            val bottom = maxOf(sg.bottom, eg.bottom) + below
                            if (event.x in minX..maxX && hitY in top..bottom) {
                                val near = if (
                                    kotlin.math.hypot(event.x - ac.x, event.y - ac.y) <
                                    kotlin.math.hypot(event.x - bc.x, event.y - bc.y)
                                ) 0 else 1
                                beginHandleDrag(near)
                                return true
                            }
                        }
                    }
                    // Do not clear the handles on a miss: a normal tap below
                    // will collapse the selection and close them in
                    // onSelectionChanged. Clearing here left only the highlight.
                }
                if (selectionMenuActive) onSelectionMenu?.invoke(0, 0, 0, 0)
                selectionMenuActive = false
                selectionPopup?.dismiss()
                selectionPopup = null
                scroller.forceFinished(true)
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                downX = event.x
                downY = event.y
                lastY = event.y
                dragging = false
                longPressConsumed = false
                selectionMenuActive = false
                removeCallbacks(longPressRunnable)
                removeCallbacks(caretSettle)
                caretSettlePending = false
                caretFollowTarget = -1
                caretAnimator?.cancel()
                downSelectionStart = selectionStart.coerceAtLeast(0)
                downSelectionEnd = selectionEnd.coerceAtLeast(0)
                downCursorVisible = isCursorVisible
                selectingAfterLongPress = false
                todoDownSpan = todoSpanForPoint(event.x, event.y)
                todoSwipeSpan = todoDownSpan ?: todoLineSpanForPoint(event.x, event.y)
                android.util.Log.d(
                    "TodoPull",
                    "down x=${event.x.toInt()} y=${event.y.toInt()} direct=${todoDownSpan != null} line=${todoSwipeSpan != null}",
                )
                todoSwipeConsumed = false
                removeCallbacks(longPressRunnable)
                // Beat the platform long-press so the in-app glass toolbar wins.
                postDelayed(longPressRunnable, 350L)
                // The first tap after the IME is hidden used to only focus the
                // view and restore the previous caret. Place the caret at the
                // touched line before the native tap handling runs.
                // Do not change selection on DOWN: a short move was starting a
                // native bring-into-view and scrolling the viewport to the top.
                // Selection is placed on UP only when this is a real tap.
                // Keep the native editor out of the gesture entirely. Letting
                // it see DOWN starts its own long-press/caret timer, which races
                // our selection and breaks handle dragging when the IME is open.
                requestFocus()
                isCursorVisible = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (handleDrag >= 0) {
                    val offset = getOffsetForPosition(event.x, event.y).coerceIn(0, text.length)
                    var s = selectionStart.coerceAtLeast(0)
                    var e = selectionEnd.coerceAtLeast(0)
                    // 拖过另一端：把手柄身份换到手指这一侧，而不是让手指「抓空」。
                    if (handleDrag == 0 && offset > e) handleDrag = 1
                    else if (handleDrag == 1 && offset < s) handleDrag = 0
                    s = selectionStart.coerceAtLeast(0)
                    e = selectionEnd.coerceAtLeast(0)
                    if (handleDrag == 0) setSelection(minOf(offset, e).coerceAtLeast(0), maxOf(offset, e))
                    else setSelection(minOf(s, offset), maxOf(s, offset).coerceAtMost(text.length))
                    lastDragX = event.x
                    lastDragY = event.y
                    // 水滴往手指走的方向拉长。方向用「相对按下点的位移」算，
                    // 比逐帧位移稳（逐帧抖动会让形变左右乱晃）。
                    val dragDx = event.x - downX
                    val dragDy = event.y - downY
                    val dragLen = kotlin.math.hypot(dragDx, dragDy)
                    if (dragLen > 2f) {
                        handleDragDirX = dragDx / dragLen
                        handleDragDirY = dragDy / dragLen
                    }
                    updateAutoScroll(event.y)
                    trackHandle(if (handleDrag == 0) selectionStart else selectionEnd)
                    draggingCaretX = event.x.coerceIn(0f, width.toFloat())
                    invalidate()
                    return true
                }
                if (selectingAfterLongPress) {
                    val offset = getOffsetForPosition(event.x, event.y).coerceIn(0, text.length)
                    setSelection(minOf(selectionAnchor, offset), maxOf(selectionAnchor, offset))
                    return true
                }
                val todoDx = event.x - downX
                val todoDy = event.y - downY
                if (todoSwipeSpan != null) {
                    android.util.Log.d("TodoPull", "move dx=$todoDx dy=$todoDy consumed=$todoSwipeConsumed")
                }
                // Consume the drag only when someone actually listens. With an
                // empty handler this branch ate the whole gesture: the body never
                // scrolled and startFling() was never reached, then the ACTION_UP
                // caret restore jumped the page a few lines. That is the "this
                // direction has no inertia" report.
                if (todoSwipeSpan != null && onTodoSwipe != null && !todoSwipeConsumed &&
                    todoDy > touchSlop * 2f &&
                    todoDy > kotlin.math.abs(todoDx) * 1.2f
                ) {
                    removeCallbacks(longPressRunnable)
                    cancelLongPress()
                    todoSwipeConsumed = true
                    todoSwipeSpan?.let { notifyTodoSwipe(it) }
                    isCursorVisible = false
                    return true
                }
                velocity?.addMovement(event)
                if (!dragging) {
                    // A long press naturally drifts a little. Cancelling the
                    // selection timer at the normal scroll slop made IME
                    // long-presses intermittently scroll instead of selecting.
                    val cancelSlop = touchSlop * 2.5f
                    if (kotlin.math.abs(event.y - downY) > cancelSlop) {
                        dragging = true
                        removeCallbacks(longPressRunnable)
                        cancelLongPress()
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.onTouchEvent(cancel)
                        cancel.recycle()
                        parent?.requestDisallowInterceptTouchEvent(true)
                        // super.onTouchEvent(DOWN) may have moved the caret to the
                        // touch point; put the original selection back for scrolling.
                        setSelection(
                            downSelectionStart.coerceIn(0, text.length),
                            downSelectionEnd.coerceIn(0, text.length),
                        )
                        isCursorVisible = false
                        lastY = event.y
                        return true
                    }
                    if (kotlin.math.abs(event.y - downY) > 2f) {
                        // Consume small moves; the custom long-press timer is
                        // still alive and will select the paragraph.
                        return true
                    }
                    return true
                }
                val dy = lastY - event.y
                if (dy != 0f) {
                    scrollTo(scrollX, (scrollY + dy.toInt()).coerceIn(0, contentMaxScroll()))
                    lastY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                velocity?.addMovement(event)
                velocity?.computeCurrentVelocity(1000)
                val vy = velocity?.yVelocity ?: 0f
                val wasDragging = dragging
                dragging = false
                removeCallbacks(longPressRunnable)
                if (handleDrag >= 0) {
                    // 松手：弹簧继续把手柄送到最后一个字上（过冲一次），自动滚停下。
                    autoScrollDir = 0
                    autoScrollSpeed = 0f
                    handleDrag = -1
                    draggingCaretX = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
                if (selectingAfterLongPress) {
                    selectingAfterLongPress = false
                    velocity?.recycle()
                    velocity = null
                    return true
                }
                if (longPressConsumed) {
                    velocity?.recycle()
                    velocity = null
                    return true
                }
                velocity?.recycle()
                velocity = null
                if (!wasDragging &&
                    kotlin.math.abs(event.x - downX) <= touchSlop &&
                    kotlin.math.abs(event.y - downY) <= 2f
                ) {
                    if (tapIsBelowLastReminder(event.y) && appendLineAfterLastReminder()) {
                        requestFocus()
                        // DOWN hid the caret; a tap that edits the text must not
                        // leave the body without one until the next touch.
                        revealCaret()
                        return true
                    }
                    val offset = getOffsetForPosition(event.x, event.y).coerceIn(0, text.length)
                    val reminderBand = reminderLineVerticalBounds(offset)
                    if (reminderBand != null &&
                        event.y >= reminderBand.first - touchSlop &&
                        event.y <= reminderBand.second + touchSlop
                    ) {
                        activeTodoAt(offset)?.let { onReminderTap?.invoke(it) }
                        revealCaret()
                        return true
                    }
                }
                val tappedTodo = todoDownSpan
                val swipedTodo = todoSwipeConsumed
                todoDownSpan = null
                todoSwipeSpan = null
                todoSwipeConsumed = false
                if (swipedTodo) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    revealCaret()
                    return true
                }
                if (tappedTodo != null && !wasDragging &&
                    kotlin.math.abs(event.x - downX) <= touchSlop &&
                    kotlin.math.abs(event.y - downY) <= 2f
                ) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    tappedTodo.click()
                    // Ticking a box rewrites the line; the caret used to stay
                    // hidden (DOWN hid it) until the next tap in the body.
                    revealCaret()
                    return true
                }
                if (wasDragging) {
                    cancelLongPress()
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.onTouchEvent(cancel)
                    cancel.recycle()
                    startFling(-vy)
                    // 滚动结束必须把光标请回来：以前这里只置 hidden，之后没人恢复，
                    // 滑完光标就一直不亮（用户 2026-11-15 报「滑动的时候也是」）。
                    revealCaret()
                    return true
                }
                revealCaret()
                val moved = kotlin.math.abs(event.y - downY) > 2f
                // A small move is a scroll attempt, not a tap. Letting super
                // handle it can start a native long-press/selection and jump the
                // viewport to that character (the "back to top" footage).
                if (moved && kotlin.math.abs(event.y - downY) < touchSlop) {
                    requestFocus()
                    post {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    }
                    // The caret is placed by the tap; the scroll waits for the
                    // page to stop resizing when the keyboard's space is what
                    // makes the scroll possible in the first place.
                    beginCaretScrollWindow()
                    post { ensureCaretVisible() }
                    return true
                }
                if (!moved) {
                    var offset = getOffsetForPosition(event.x, event.y).coerceIn(0, text.length)
                    val lineStart = if (offset == 0) 0 else {
                        var i = offset - 1
                        while (i >= 0 && text.getOrNull(i) != '\n') i--
                        i + 1
                    }
                    var indentEnd = lineStart
                    while (indentEnd < text.length && (text[indentEnd] == '　' || text[indentEnd] == ' ')) {
                        indentEnd++
                    }
                    if (offset >= lineStart && offset < indentEnd) offset = indentEnd
                    setSelection(offset.coerceAtMost(text.length))
                }
                requestFocus()
                post {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
                // Typing keeps the native bring-into-view; a tap is the moment
                // the user wants the caret brought into the viewport on purpose.
                if (INFO_CARET) android.util.Log.d(
                    "MOYU_CARET",
                    "tap caret=${selectionStart} scrollY=$scrollY h=$height maxY=${contentMaxScroll()} " +
                        "ime=$imeInsetPx last=$lastImePx dock=$dockBottomInset",
                )
                beginCaretScrollWindow()
                post { ensureCaretVisible() }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                selectingAfterLongPress = false
                todoDownSpan = null
                todoSwipeSpan = null
                todoSwipeConsumed = false
                parent?.requestDisallowInterceptTouchEvent(false)
                removeCallbacks(longPressRunnable)
                handleDrag = -1
                // 手势被上层抢走（例如换到别的页面）：弹簧与自动滚一起停，
                // 恢复到按下之前的光标状态，而且必须重绘。
                resetHandleSpring()
                isCursorVisible = downCursorVisible
                invalidate()
                velocity?.recycle()
                velocity = null
                return super.onTouchEvent(event)
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 焦点回来时把光标重新点亮。
     *
     * 浮窗 / 标题 / 别的控件把焦点抢走一次之后，闪烁循环早就停在早退里；
     * 光靠「下次打字」叫醒就是用户看到的「莫名其妙消失」。
     */
    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) revealCaret()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // 从别的应用切回来：窗口焦点恢复，光标应当立刻在。
        if (hasWindowFocus && hasFocus()) revealCaret()
    }

    private fun startFling(velocityY: Float) {
        // Any gesture that actually dragged the body may fling. The old fixed
        // 150px gate silently killed short, quick flicks: the page moved only as
        // far as the finger did and then stopped dead, which reads as "no
        // inertia" — worst in short notes, where a flick is only a few hundred
        // px. The drag threshold (cancelSlop) already keeps taps out of here.
        if (kotlin.math.abs(lastY - downY) < touchSlop * 2f) {
            if (INFO_FLING) android.util.Log.d("MoyuFling", "fling skipped: short drag")
            return
        }
        if (kotlin.math.abs(velocityY) < minFling) {
            if (INFO_FLING) android.util.Log.d("MoyuFling", "fling skipped: v=$velocityY < $minFling")
            return
        }
        val range = contentMaxScroll()
        // At either edge (with a 2px tolerance), do not start any fling at all.
        // This is the hard stop: no second swipe jumping back to the top.
        android.util.Log.d("MOYU_SCROLL", "startFling v=$velocityY scrollY=$scrollY range=$range")
        if (INFO_FLING) {
            android.util.Log.d(
                "MoyuFling",
                "start v=$velocityY dir=${if (velocityY < 0) "up" else "down"} " +
                    "scrollY=$scrollY range=$range",
            )
        }
        if (scrollY <= 0 || scrollY >= (range - 2).coerceAtLeast(0)) {
            scroller.forceFinished(true)
            return
        }
        scroller.fling(
            0,
            scrollY,
            0,
            velocityY.toInt(),
            0,
            0,
            0,
            range,
        )
        // Compose hosts this view through AndroidView, so the parent never calls
        // the three-argument View.draw() path that would invoke computeScroll().
        // Left to computeScroll alone the OverScroller would never advance and
        // every fling would die on the first frame — no inertia in either
        // direction. Drive it from a frame callback instead.
        removeCallbacks(flingDriver)
        postOnAnimation(flingDriver)
    }

    /**
     * Advances the OverScroller one frame at a time and re-posts itself until the
     * fling finishes or hits an edge. See startFling() for why computeScroll()
     * cannot be relied on here.
     */
    private val flingDriver = object : Runnable {
        override fun run() {
            if (!scroller.computeScrollOffset()) return
            val maxScroll = contentMaxScroll()
            if ((scrollY <= 0 && scroller.currY <= 0) ||
                (scrollY >= (maxScroll - 2).coerceAtLeast(0) && scroller.currY >= maxScroll)
            ) {
                scroller.forceFinished(true)
                return
            }
            scrollTo(scroller.currX, scroller.currY)
            postOnAnimation(this)
        }
    }

    override fun computeScroll() {
        if (INFO_FLING) android.util.Log.d("MoyuFling", "computeScroll called")
        if (scroller.computeScrollOffset()) {
            val maxScroll = contentMaxScroll()
            if (INFO_FLING) {
                android.util.Log.d(
                    "MoyuFling",
                    "cs scrollY=$scrollY curr=${scroller.currY} final=${scroller.finalY}",
                )
            }
            if ((scrollY <= 0 && scroller.currY <= 0) ||
                (scrollY >= (maxScroll - 2).coerceAtLeast(0) && scroller.currY >= maxScroll)
            ) {
                if (INFO_FLING) android.util.Log.d("MoyuFling", "cs stopped at edge scrollY=$scrollY")
                scroller.forceFinished(true)
                return
            }
            scrollTo(scroller.currX, scroller.currY)
            postInvalidateOnAnimation()
        }
    }
}

@Composable
private fun EditorTopBar(
    heading: String,
    words: Int,
    saved: Boolean,
    showWords: Boolean,
    indentOn: Boolean,
    onToggleIndent: () -> Unit,
    onBack: () -> Unit,
    onMore: () -> Unit,
    /** 笔记所在笔记夹。笔记页两行：夹名在上、笔记名在下（2026-11-15 用户定）。 */
    crumb: String = "",
    /** 章节链接 chip，放在右侧按钮下方（用户定）。 */
    chips: (@Composable () -> Unit)? = null,
    /** When set, this bar reports its heading position as the landing point of
     *  the note card that opened this editor. */
    flightKey: String? = null,
) {
    val ui = LocalInk.current
    val p = ui.palette
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.paper)
            .clipToBounds()
            .statusBarsPadding()
            // 顶栏压薄（7→4dp）：整页上移，但保留基本呼吸。
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Solid paper back button: a GlassSurface here could be captured at
            // the wrong size during the editor push and leave a white rounded ghost.
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(p.paper)
                    .bouncy { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(Glyph.Back, p.ink, 21.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (crumb.isNotBlank()) {
                    Txt(
                        crumb,
                        size = 9.5f,
                        color = p.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                }
                Txt(
                    heading,
                    modifier = if (flightKey != null) {
                        Modifier
                            .onGloballyPositioned { Flight.land(flightKey, it) }
                            .alpha(if (Flight.isCarrying(flightKey)) 0f else 1f)
                    } else {
                        Modifier
                    },
                    size = 12.5f,
                    weight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showWords) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(p.faint)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = saved,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                        label = "saved",
                    ) { isSaved ->
                        Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSaved) Color(0xFF4D8A64) else ui.accent))
                    }
                    Spacer(Modifier.width(5.dp))
                    Txt("$words", size = 9.5f, color = p.muted, weight = FontWeight.Medium)
                }
                Spacer(Modifier.width(6.dp))
            }
            GlassIconButton(
                Glyph.Indent,
                onToggleIndent,
                size = 40.dp,
                tint = if (indentOn) ui.accent else p.muted,
                strength = .45f,
            )
            Spacer(Modifier.width(5.dp))
            GlassIconButton(Glyph.More, onMore, size = 40.dp, strength = .45f)
        }
        if (chips != null) {
            // 呼吸：chip 行与右侧按钮之间留 8dp、与底边留 6dp。
            // 2026-11-15 用户点名：贴按钮太紧，以后同类位置都要留一点空隙。
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                chips()
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private val symbols = listOf(
    "，", "。", "、", "；", "：", "？", "！", "“", "”", "‘", "’",
    "（", "）", "《", "》", "〈", "〉", "【", "】", "……", "——", "·", "～", "　",
)

/** Every toolbar mode (tools, selection) uses exactly this row height, so the
 *  dock never changes height when the writer selects a passage. */
private val DockRowHeight = 52.dp

/** One tool cell: eight of these fit a 360dp screen without paging, and the
 *  row spreads them evenly instead of packing them left with wide gaps. */
private val DockToolWidth = 36.dp

@Composable
private fun EditorDock(
    canUndo: Boolean,
    canRedo: Boolean,
    isNote: Boolean,
    pendingTodos: Int,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onIndent: () -> Unit,
    onQuote: () -> Unit,
    onDivider: () -> Unit,
    onType: () -> Unit,
    onTodos: (() -> Unit)?,
    todoActive: Boolean = false,
    boundNotes: Int = 0,
    onNotes: (() -> Unit)? = null,
    onNotesLongPress: (() -> Unit)? = null,
    onHideKeyboard: () -> Unit,
    onStory: (() -> Unit)? = null,
    onCollapse: () -> Unit,
    onSymbol: (String) -> Unit,
    onExpand: () -> Unit,
    selectionMode: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancelSelection: () -> Unit,
    /** The selection toolbar's「校核」entry: checks the selected passage. */
    onProof: (() -> Unit)? = null,
    /** AI 入口里的「修饰」：只顺句，出改写版本；点一个才替换正文。 */
    onPolish: (() -> Unit)? = null,
    /** 3.26.32：AI 入口里的「问」：就选中的这一段对话，回答可一键替换回选区。 */
    onAsk: (() -> Unit)? = null,
    /** 1型玻璃 sampling source. Pass the body's software backdrop so the real
     *  EditText text shows through the dock; null falls back to a paper bar. */
    backdrop: Backdrop? = null,
    /** Bumped on every body scroll / text change so the glass re-samples the
     *  native EditText instead of freezing on its first frame. */
    epoch: Int = 0,
    /** Read inside the dock's draw scope; invalidates the same draw chain that
     *  contains liquidGlassBar, so the backdrop is re-sampled on every scroll. */
    scrollTick: androidx.compose.runtime.MutableIntState? = null,
    bodyEditor: BodyEditText? = null,
    /** 3.26.41：非选区工具行横向拖动 = 移正文光标。开始 / 进度 / 结束上报给页面层，
     *  页面层据此在能力栏正上方挂 HUD。 */
    onScrubStart: () -> Unit = {},
    onScrubMove: (Float, Int, Int) -> Unit = { _, _, _ -> },
    onScrubEnd: () -> Unit = {},
) {
    val ui = LocalInk.current
    val p = ui.palette
    // Solid bar: one straight full-width strip, a touch deeper than the theme
    // paper, so the tools and symbols own their surface. No glass, no rounding.
    // The bar is the theme paper nudged towards the accent, not the raw accent:
    // a full-strength accent turned the dock into a separate coloured slab and
    // read as a different app. 10% light / 14% dark keeps the accent legible as
    // a tint while the bar still belongs to the page.
    // One tone for the toolbar and every flat button (see dockTone).
    val bar = dockTone(ui)
    val onBar = p.ink
    val onBarDim = p.muted
    // 3.26.41：工具栏横滑移光标（手势挂在非选区工具行上）。
    // 手势器只在第一次组合时启动，用 rememberUpdatedState 保证每次都能读到最新的
    // editor 与回调——bodyEditor 是 AndroidView 的 factory 跑完才有的，一开始是 null。
    val latestEditor by rememberUpdatedState(bodyEditor)
    val latestScrubStart by rememberUpdatedState(onScrubStart)
    val latestScrubMove by rememberUpdatedState(onScrubMove)
    val latestScrubEnd by rememberUpdatedState(onScrubEnd)
    val haptic = LocalHaptic.current
    var dockTopInWindow by remember { mutableStateOf(0f) }
    // AGSL lens for the dock: real per-pixel refraction + RGB dispersion on the
    // sampled body slice. null below API 33, where the bar falls back to blur.
    val dockShader = remember {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            runCatching { android.graphics.RuntimeShader(DOCK_REFRACTION_AGSL) }.getOrNull()
        } else null
    }
    Box(
        Modifier
            .fillMaxWidth()
            // 一整块：左右不留白，上缘圆角、下缘压到底。
            .fillMaxWidth()
            .onGloballyPositioned { dockTopInWindow = it.positionInWindow().y }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(
                    // 连续曲率：上缘提前起弯，不做「直边→圆弧」的曲率跳变。
                    SmoothCorner(topStart = 22.dp, topEnd = 22.dp),
                )
                // 1型玻璃 under the dock: the body is a native EditText, so the
                // Backdrop library cannot sample it and its draw node freezes.
                // Drawing the real view slice here every frame is what makes the
                // text under the bar follow the scroll.
                // Solid bar: one straight full-width strip, a touch deeper than the
                // theme paper (dockTone), so the tools own their surface again.
                .background(bar)
        ) {
            CompositionLocalProvider(
                LocalDockFg provides onBar,
                LocalDockDim provides onBarDim,
            ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    // 正文已经铺到手势条下面；工具行/符号栏要抬到小白条上方。
                    .navigationBarsPadding()
            ) {
                // 三种工具栏形态共用同一行高：普通工具 / 框选工具 / 校核。
                // 框选后行高不变，dock 不会因为换了一行内容而跳一下。
                Box(
                    Modifier.fillMaxWidth().height(DockRowHeight).clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                AnimatedContent(
                    targetState = selectionMode,
                    transitionSpec = {
                        (slideInHorizontally(tween(170)) { it / 3 } + fadeIn(tween(150))) togetherWith
                            (slideOutHorizontally(tween(150)) { -it / 3 } + fadeOut(tween(120)))
                    },
                    label = "dockSelection",
                ) { selecting ->
                    if (selecting) {
                        // AI 入口：点开横向展开成「校核 | 修饰」两格。这份 state
                        // 随选框行一起销毁——选择结束时自然收起，不用手动复位。
                        var aiOpen by remember { mutableStateOf(false) }
                        // 3.26.38：这一行最多 9 格（AI 展开成 3 个 + 6 个动作）。以前每格固定宽、
                        // 整行 SpaceBetween，9 格加起来超过屏宽，最右边的「取消」被裁成「取」。
                        // 改成按格数分配权重：AI 展开时占 3 格宽、其余各 1 格，都按屏宽均分，
                        // 格子自己收窄，文字不会再被切掉。
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (onProof != null || onPolish != null || onAsk != null) {
                                val aiT by animateFloatAsState(
                                    targetValue = if (aiOpen) 1f else 0f,
                                    animationSpec = spring(dampingRatio = .6f, stiffness = 380f),
                                    label = "aiT",
                                )
                                DockCell(1f + 2f * aiT) {
                                AnimatedContent(
                                    targetState = aiOpen,
                                    transitionSpec = {
                                        (fadeIn(tween(150)) + slideInHorizontally(tween(180)) { -it / 5 }) togetherWith
                                            (fadeOut(tween(110))) using
                                            SizeTransform(clip = true) { _, _ ->
                                                spring(dampingRatio = .84f, stiffness = 380f)
                                            }
                                    },
                                    label = "aiTool",
                                ) { open ->
                                    if (!open) {
                                        DockTool(Glyph.Sparkles, "AI", true, onClick = { aiOpen = true })
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // 3.26.41：三格内部错层（0 / 12% / 24% 起步、76% 追平）。
                                            // 整组宽度仍是外层 DockCell(1f + 2f*aiT) 的平滑摊开，
                                            // 这里只让三格依次出现/收回，不再整排同时闪一下。
                                            AiCellStagger(aiT, 0) {
                                                DockTool(Glyph.Check, "校核", true, onClick = {
                                                    aiOpen = false
                                                    onProof?.invoke()
                                                })
                                            }
                                            AiCellStagger(aiT, 1) {
                                                DockTool(Glyph.Edit, "修饰", true, onClick = {
                                                    aiOpen = false
                                                    onPolish?.invoke()
                                                })
                                            }
                                            if (onAsk != null) {
                                                AiCellStagger(aiT, 2) {
                                                    DockTool(Glyph.Sparkles, "问", true, onClick = {
                                                        aiOpen = false
                                                        onAsk?.invoke()
                                                    })
                                                }
                                            }
                                        }
                                    }
                                }
                                }
                            }
                            DockCell { DockTool(Glyph.Share, "复制", true, onCopy) }
                            DockCell { DockTool(Glyph.Check, "全选", true, onSelectAll) }
                            DockCell { DockTool(Glyph.Edit, "剪切", true, onCut) }
                            DockCell { DockTool(Glyph.Download, "粘贴", true, onPaste) }
                            DockCell { DockTool(Glyph.Trash, "删除", true, onDelete) }
                            DockCell { DockTool(Glyph.Close, "取消", true, onCancelSelection) }
                        }
                    } else {
                        // 工具行：8 格固定 36dp，整行 SpaceEvenly 均分。
                        // 8×36=288dp，360dp 屏去掉两边 12dp 还剩 336dp，一屏
                        // 放得下就不再横滑翻页；窄屏只收紧格间空隙，格宽不变。
                        // 3.26.41：在这一行上横向拖动 = 移正文光标（每 8dp 一个字）。
                        // 手势器自带水平 slop——位移不到 slop 时根本不启动，工具的点按照旧；
                        // 纵向位移走 cross-axis 取消，不会被这里抢（由正文自己滚）。
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .dockScrubGesture(
                                    editor = { latestEditor },
                                    haptic = haptic,
                                    onStart = { latestScrubStart() },
                                    onMove = { r, c, t -> latestScrubMove(r, c, t) },
                                    onEnd = { latestScrubEnd() },
                                )
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DockTool(Glyph.Undo, "撤销", canUndo, onUndo, width = DockToolWidth)
                            DockTool(Glyph.Redo, "重做", canRedo, onRedo, width = DockToolWidth)
                            DockTool(Glyph.Indent, "缩进", true, onIndent, width = DockToolWidth)
                            DockTool(Glyph.Quote, "引号", true, onQuote, width = DockToolWidth)
                            DockTool(Glyph.Divider, "分节", true, onDivider, width = DockToolWidth)
                            if (isNote && onTodos != null) {
                                Box(Modifier.width(DockToolWidth)) {
                                    AnimatedContent(
                                        targetState = todoActive,
                                        transitionSpec = {
                                            if (targetState) {
                                                (slideInVertically(tween(180)) { it } + fadeIn(tween(180))) togetherWith
                                                    (slideOutVertically(tween(180)) { -it } + fadeOut(tween(180)))
                                            } else {
                                                (slideInVertically(tween(180)) { -it } + fadeIn(tween(180))) togetherWith
                                                    (slideOutVertically(tween(180)) { it } + fadeOut(tween(180)))
                                            }
                                        },
                                        label = "todoTool",
                                    ) { active ->
                                        if (active) {
                                            DockTool(Glyph.Clock, "提醒", true, onTodos, width = DockToolWidth)
                                        } else {
                                            DockTool(
                                                Glyph.List,
                                                if (pendingTodos > 0) "待办·$pendingTodos" else "待办",
                                                true,
                                                onTodos,
                                                width = DockToolWidth,
                                            )
                                        }
                                    }
                                }
                            }
                            if (onStory != null) {
                                DockTool(Glyph.Book, "故事", true, onStory, width = DockToolWidth)
                            }
                            DockTool(Glyph.Font, "排版", true, onType, width = DockToolWidth)
                            if (onNotes != null) {
                                DockTool(
                                    Glyph.Link,
                                    if (boundNotes > 0) "笔记·$boundNotes" else "笔记",
                                    true,
                                    onNotes,
                                    onLongPress = onNotesLongPress,
                                    width = DockToolWidth,
                                )
                            }
                            DockTool(Glyph.Keyboard, "收起", true, onHideKeyboard, width = DockToolWidth)
                        }
                    }
                }
                }
                Spacer(Modifier.height(7.dp))
                LazyRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    // 左下角那一格（展开/收起球）占的宽度：38dp + 1dp 间隔，
                    // 符号从它右边接上，中间不再留 48dp 的空槽。
                    contentPadding = PaddingValues(start = 39.dp),
                ) {
                    items(symbols) { symbol ->
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(SmoothCorner(11.dp))
                                .bouncy(scale = .86f, hapticType = 1) { onSymbol(symbol) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Txt(
                                symbol,
                                size = 15f,
                                color = LocalDockFg.current.takeIf { it != Color.Unspecified } ?: p.ink,
                                align = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

private val LocalDockFg = androidx.compose.runtime.staticCompositionLocalOf { Color.Unspecified }
private val LocalDockDim = androidx.compose.runtime.staticCompositionLocalOf { Color.Unspecified }

@Composable
private fun androidx.compose.foundation.layout.RowScope.DockCell(
    weight: Float = 1f,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        Modifier.weight(weight),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/**
 * 3.26.41：AI 三格展开时的内部错层。
 *
 * 3.26.39 已经把整组的宽度做成 aiT 驱动的动画（外层 DockCell 权重 1f + 2f*aiT），
 * 这里只补三格内部先后推出的顺序感：第 i 格在 aiT 走到 i*12% 之后才开始动，
 * 到 76% 追平——位移 12dp、透明度同步，所以三格读完是一条「依次被挤出来」的带。
 */
@Composable
private fun AiCellStagger(
    aiT: Float,
    index: Int,
    content: @Composable () -> Unit,
) {
    val ti = ((aiT - index * .12f) / .76f).coerceIn(0f, 1f)
    Box(
        Modifier.graphicsLayer {
            alpha = ti
            translationX = -(1f - ti) * 12.dp.toPx()
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 3.26.41：非选区工具行上的横向拖动 = 移正文光标。
 *
 * 用 [detectHorizontalDragGestures]（自带水平 slop）：
 *  - 点按：位移不到 slop，手势器根本不启动，`DockTool` 的点击照旧；
 *  - 纵向拖动：cross-axis 超 slop 就取消这个手势器，不会误移光标。
 *    （代价：起点正好落在工具行这条窄带上的纵向拖动不再穿透给下面的正文——
 *    与 3.26.34 那一版的行为一致，正文区本身不受影响。）
 *
 * 手感：每 8dp 一个字；同一帧里横向速度越快，位移放大越多（6000dp/s 封顶 6 倍）；
 * 拉到 0 / 末尾之后继续拖，越界部分只按 0.35 记进虚拟位置（橡皮筋），
 * 手指回头要先走完这段才重新带动光标。每跨 8 个字补一下轻触感。
 */
private fun Modifier.dockScrubGesture(
    editor: () -> BodyEditText?,
    haptic: (Int) -> Unit,
    onStart: () -> Unit,
    onMove: (Float, Int, Int) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    val pxPerChar = 8.dp.toPx()
    var pos = 0f
    var lastAt = 0L
    var lastChunk = 0
    var live = false
    fun finish() {
        if (live) {
            live = false
            onEnd()
        }
    }
    detectHorizontalDragGestures(
        onDragStart = {
            val ed = editor()
            live = ed != null
            if (ed != null) {
                val total = ed.text?.length ?: 0
                pos = ed.selectionStart.coerceIn(0, total).toFloat()
                lastAt = 0L
                lastChunk = (pos / 8f).toInt()
                haptic(1)
                onStart()
                onMove(if (total > 0) pos / total else 0f, pos.toInt(), total)
            }
        },
        onDragEnd = { finish() },
        onDragCancel = { finish() },
    ) { change, dx ->
        if (live) {
            val ed = editor()
            if (ed != null) {
                change.consume()
                val total = ed.text?.length ?: 0
                if (total > 0) {
                    // 快滑加速：按这一帧的横向速度放大位移，最多 6 倍。
                    val now = change.uptimeMillis
                    val dt = if (lastAt == 0L) 16L else (now - lastAt).coerceIn(1L, 80L)
                    lastAt = now
                    val speed = abs(dx) / density / (dt / 1000f)
                    val boost = 1f + 5f * (speed / 6000f).coerceIn(0f, 1f)
                    var next = pos + dx * boost / pxPerChar
                    val maxPos = total.toFloat()
                    val clamped = next.coerceIn(0f, maxPos)
                    // 两端橡皮筋：越界的部分只走 0.35。
                    next = clamped + (next - clamped) * .35f
                    pos = next
                    val caret = next.roundToInt().coerceIn(0, total)
                    if (caret != ed.selectionStart) {
                        ed.setSelection(caret)
                        runCatching { ed.bringPointIntoView(caret) }
                    }
                    val chunk = caret / 8
                    if (chunk != lastChunk) {
                        lastChunk = chunk
                        haptic(0)
                    }
                    onMove(caret.toFloat() / total, caret, total)
                }
            }
        }
    }
}

@Composable
private fun DockTool(
    glyph: Glyph,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    iconSize: Dp = 18.dp,
    onLongPress: (() -> Unit)? = null,
    width: Dp = 42.dp,
) {
    val ui = LocalInk.current
    var pressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLong by rememberUpdatedState(onLongPress)
    Column(
        Modifier
            .width(width)
            // No clip: at the system's largest font scale a 36dp cell would cut
            // the last character of 「笔记·1」; the background already draws with
            // the rounded shape, so nothing needs clipping.
            .background(
                if (pressed) lerp(ui.palette.paper, ui.accent, .10f) else Color.Transparent,
                SmoothCorner(12.dp),
            )
            // One gesture owner for tap and long press (two detectors would
            // race for the same down), and the same bounce for every tool.
            .bouncy(
                enabled,
                scale = .88f,
                overshoot = true,
                onLongPress = currentOnLong,
                onPressChange = { pressed = it },
            ) { currentOnClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fg = LocalDockFg.current.takeIf { it != Color.Unspecified } ?: ui.palette.muted
        val ink = LocalDockFg.current.takeIf { it != Color.Unspecified } ?: ui.palette.ink
        LineIcon(glyph, if (enabled) ink else fg.copy(alpha = .45f), iconSize)
        Spacer(Modifier.height(3.dp))
        Txt(label, size = 7.6f, color = if (enabled) fg else fg.copy(alpha = .45f), maxLines = 1)
    }
}

@Composable
private fun PaperStyleMenu() {
    val s = LocalInk.current.settings
    val store = LocalStore.current
    SettingRow(
        "纸面",
        when (s.paperStyle) {
            "ruled" -> "虚线"
            "solid" -> "实线"
            else -> "无"
        },
        Glyph.Rows,
    )
    Segmented(
        listOf("无", "虚线", "实线"),
        when (s.paperStyle) {
            "ruled" -> 1
            "solid" -> 2
            else -> 0
        },
        { index ->
            store.updateSettings {
                it.copy(
                    paperStyle = when (index) {
                        1 -> "ruled"
                        2 -> "solid"
                        else -> "plain"
                    },
                )
            }
        },
        Modifier.fillMaxWidth(),
    )
    SettingRow(
        "线条浓度",
        "${(s.paperLineAlpha * 100).toInt()}%",
        Glyph.Rows,
    )
    InkSlider(
        s.paperLineAlpha,
        { value -> store.updateSettings { it.copy(paperLineAlpha = value) } },
        .1f,
        1f,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun TypographySheet(store: AppStore, onDismiss: () -> Unit) {
    val ui = LocalInk.current
    val s = ui.settings
    val themes = listOf("paper" to "纸白", "mist" to "雾灰", "white" to "纯白", "sepia" to "茶褐", "night" to "夜黑")
    InlineModalSheet(onDismiss) { _ ->
        DialogTitle("排版", "改动会立刻作用到编辑器和提笔的手感。", onClose = onDismiss)
        Spacer(Modifier.height(16.dp))
        MoyuSurface(Modifier.fillMaxWidth(), radius = 20f, flat = true) {
            Column(Modifier.padding(16.dp)) {
                Txt("预览", size = 8.5f, color = ui.palette.muted, letterSpacing = 1f)
                Spacer(Modifier.height(7.dp))
                Txt(
                    "雨落在旧城的屋檐上，故事从这里开始。",
                    size = (17f * s.fontScale).coerceIn(12f, 28f),
                    lineHeight = (17f * s.fontScale * s.lineHeight).coerceIn(20f, 52f),
                    fontFamily = fontFamilyFor(s.font),
                )
            }
        }
        Spacer(Modifier.height(13.dp))
        Segmented(
            options = listOf("衬线", "无衬线", "等宽"),
            selected = when (s.font) { "sans" -> 1; "mono" -> 2; else -> 0 },
            onSelect = { index ->
                store.updateSettings { it.copy(font = when (index) { 1 -> "sans"; 2 -> "mono"; else -> "serif" }) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(13.dp))
        SettingRow("字号", "${(17f * s.fontScale).toInt()} sp", Glyph.Font, trailing = {})
        InkSlider(
            value = s.fontScale,
            onChange = { value -> store.updateSettings { it.copy(fontScale = (value * 100).toInt() / 100f) } },
            min = .82f,
            max = 1.4f,
            modifier = Modifier.padding(horizontal = 15.dp),
        )
        SettingRow("行距", String.format("%.2f 倍", s.lineHeight), Glyph.Rows, trailing = {})
        InkSlider(
            value = s.lineHeight,
            onChange = { value -> store.updateSettings { it.copy(lineHeight = (value * 100).toInt() / 100f) } },
            min = 1.4f,
            max = 2.3f,
            modifier = Modifier.padding(horizontal = 15.dp),
        )
        SettingRow("页边距", "${s.pageMargin.toInt()} dp", Glyph.Indent, trailing = {})
        InkSlider(
            value = s.pageMargin,
            onChange = { value -> store.updateSettings { it.copy(pageMargin = (value * 10).toInt() / 10f) } },
            min = 14f,
            max = 36f,
            modifier = Modifier.padding(horizontal = 15.dp),
        )
        Spacer(Modifier.height(13.dp))
        Txt("纸色", size = 10.5f, color = ui.palette.muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(themes) { (key, label) ->
                val pal = paletteFor(key, key == "night")
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(if (s.theme == key) ui.palette.ink else ui.palette.faint)
                        .bouncy { store.updateSettings { it.copy(theme = key) } }
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(pal.paper)
                            .border(.7.dp, ui.palette.line, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Txt(label, size = 9.5f, color = if (s.theme == key) ui.palette.bgTop else ui.palette.muted)
                }
            }
        }
        Spacer(Modifier.height(15.dp))
        Txt("分节", size = 10.5f, color = ui.palette.muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf("asterisk" to "星号", "line" to "短横", "dots" to "圆点", "blank" to "留白", "custom" to "自定义")) { (key, label) ->
                GlassChip(label, s.sectionStyle == key) {
                    store.updateSettings { it.copy(sectionStyle = key) }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Txt(
            "分节时插入：${sectionBreakText(s).trim().ifBlank { "（留白）" }}",
            size = 9f,
            color = ui.palette.muted,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        GlassButton("恢复默认排版", {
            store.updateSettings {
                it.copy(font = "serif", fontScale = 1f, lineHeight = 1.82f, pageMargin = 22f)
            }
            ToastBus.show("已恢复默认排版")
        }, Modifier.fillMaxWidth(), glyph = Glyph.Undo)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun BookEditorScreen(
    store: AppStore,
    bookId: String,
    chapterId: String,
    onBack: () -> Unit,
    onOpenBook: () -> Unit,
    onOpenNote: (String) -> Unit = {},
) {
    val book = store.book(bookId)
    val chapter = book?.chapters?.firstOrNull { it.id == chapterId } ?: book?.chapters?.firstOrNull()
    if (book == null || chapter == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var notesOpen by remember { mutableStateOf(false) }
    var pendingMenuAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val boundNotes = store.data.notesOfChapter(chapter)
    val noteLinks = NoteLinkUi(
        notes = boundNotes,
        floatOn = store.settings.noteFloat,
        onOpenSheet = { notesOpen = true },
        onToggleFloat = { on -> store.updateSettings { it.copy(noteFloat = on) } },
        onOpenNote = { note -> onOpenNote(note.id) },
        onSaveNote = { id, title, content, todos ->
            store.updateNote(id) { it.copy(title = title, content = content, todos = todos) }
        },
        floatX = store.settings.noteFloatX,
        floatY = store.settings.noteFloatY,
        floatW = store.settings.noteFloatW,
        floatH = store.settings.noteFloatH,
        onFloatPlacement = { x, y, w, h ->
            store.updateSettings {
                it.copy(noteFloatX = x, noteFloatY = y, noteFloatW = w, noteFloatH = h)
            }
        },
    )
    Box(Modifier.fillMaxSize()) {
        WritingScreen(
            seedKey = chapter.id,
            heading = book.title,
            metaPrefix = chapterFallbackTitle(book.chapters.indexOf(chapter)),
            initialTitle = chapter.title,
            initialContent = chapter.content,
            placeholder = "从这里开始。写下第一句，故事就有了方向。",
            inputBlocked = menuOpen || notesOpen,
            noteLinks = noteLinks,
            onSave = { title, content ->
                store.updateChapter(book.id, chapter.id, title = title.ifBlank { chapterFallbackTitle(book.chapters.indexOf(chapter)) }, content = content)
            },
            onBack = onBack,
            onMore = { menuOpen = true },
            proofBookId = book.id,
        )

        if (notesOpen) {
            ChapterNotesSheet(
                store = store,
                bookId = book.id,
                chapterId = chapter.id,
                onDismiss = { notesOpen = false },
            )
        }

        if (menuOpen) {
            // 3.26.11：章节笔记 / 导出这本书收进同一面板（横向弹簧切换）；
            // 书籍详情是页面跳转，等面板退场后再走。
            var level by remember(chapter.id) { mutableIntStateOf(0) }
            InlineModalSheet(onDismiss = {
                menuOpen = false
                pendingMenuAction?.invoke()
                pendingMenuAction = null
            }) { close ->
                AnimatedContent(
                    targetState = level,
                    transitionSpec = {
                        val forward = targetState > initialState
                        // 3.26.18：整宽位移。原来两层各移 w/3，全程重叠；旧层比新层高时
                        // 会从新层纸底下方露出来（用户录屏 v1b），两个标题也同屏。
                        (slideInHorizontally(spring(MOYU_DAMPING, 380f)) { w ->
                            if (forward) w else -w
                        }) togetherWith
                            (slideOutHorizontally(spring(MOYU_DAMPING, 380f)) { w ->
                                if (forward) -w else w
                            })
                    },
                    label = "writingOptionLevel",
                ) { step ->
                    when (step) {
                        0 -> SheetPane {
                            DialogTitle("写作选项", "${book.title} · ${chapter.title}", onClose = close)
                            Spacer(Modifier.height(14.dp))
                            SettingRow("书籍详情与目录", "查看章节、封面和统计", Glyph.Book, onClick = {
                                pendingMenuAction = { onOpenBook() }
                                close()
                            })
                            RowDivider()
                            SettingRow("章节笔记", "绑定笔记、行文时悬浮", Glyph.Link, onClick = { level = 1 })
                            RowDivider()
                            SettingRow("导出这本书", "EPUB / TXT", Glyph.Export, onClick = { level = 2 })
                            RowDivider()
                            PaperStyleMenu()
                        }
                        1 -> SheetPane {
                            ChapterNotesSheet(
                                store = store,
                                bookId = book.id,
                                chapterId = chapter.id,
                                onDismiss = { close() },
                                embedded = true,
                                onBack = { level = 0 },
                            )
                        }
                        else -> SheetPane {
                            ExportSheet(
                                book = book,
                                onDismiss = { close() },
                                embedded = true,
                                onBack = { level = 0 },
                            )
                        }
                    }
                }
            }
        }
    }
    if (exportOpen) ExportSheet(book, onDismiss = { exportOpen = false })
}

@Composable
fun NoteEditorScreen(
    store: AppStore,
    noteId: String,
    onBack: () -> Unit,
    onOpenChapter: (String, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val note = store.data.notes.firstOrNull { it.id == noteId }
    if (note == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var groupPickerOpen by remember { mutableStateOf(false) }
    var chapterSheetOpen by remember { mutableStateOf(false) }
    var pendingMenuAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val pending = note.todos.count { !it.done }
    val chapterRefs = store.data.noteChapterRefs(note)
    val folderName = note.groupId?.let { id ->
        store.noteGroups.firstOrNull { it.id == id }?.name
    } ?: "未归类"

    Box(Modifier.fillMaxSize()) {
        WritingScreen(
            seedKey = note.id,
            heading = note.title,
            initialTitle = note.title,
            initialContent = note.content,
            placeholder = "随便写。灵感、片段、今天没说完的话。",
            isNote = true,
            noteFolderName = folderName,
            inputBlocked = menuOpen || chapterSheetOpen,
            chapterTags = if (chapterRefs.isEmpty()) null else {
                {
                    NoteChapterTags(refs = chapterRefs) { ref ->
                        onOpenChapter(ref.bookId, ref.chapterId)
                    }
                }
            },
            pendingTodos = pending,
            initialTodos = note.todos,
            onTodosChange = { list ->
                ReminderScheduler.cancelNote(context, note)
                store.updateNote(note.id) { it.copy(todos = list) }
                list.filter { !it.done && it.remindAt != null && it.remindAt > System.currentTimeMillis() }
                    .forEach { ReminderScheduler.schedule(context, note.copy(todos = list), it) }
            },
            onSave = { title, content ->
                store.updateNote(note.id) { n ->
                    if (n.title == title && n.content == content) n else n.copy(
                        // Keep it empty when the user cleared it: an empty title
                        // is what makes the editor show its muted placeholder.
                        // Writing the literal string turned a hint into content.
                        title = title.trim(),
                        content = content,
                    )
                }
            },
            onBack = onBack,
            onMore = { menuOpen = true },
        )

        if (menuOpen) {
            // 3.26.11：关联章节 / 移动到笔记夹收进同一面板（横向弹簧切换）；
            // 删除是居中确认框，仍等面板退场后再开。
            var level by remember(note.id) { mutableIntStateOf(0) }
            InlineModalSheet(onDismiss = {
                menuOpen = false
                pendingMenuAction?.invoke()
                pendingMenuAction = null
            }) { close ->
                AnimatedContent(
                    targetState = level,
                    transitionSpec = {
                        val forward = targetState > initialState
                        // 3.26.18：整宽位移（同上，避免两层重叠透底）。
                        (slideInHorizontally(spring(MOYU_DAMPING, 380f)) { w ->
                            if (forward) w else -w
                        }) togetherWith
                            (slideOutHorizontally(spring(MOYU_DAMPING, 380f)) { w ->
                                if (forward) -w else w
                            })
                    },
                    label = "noteOptionLevel",
                ) { step ->
                    when (step) {
                        0 -> SheetPane {
                            DialogTitle("笔记选项", "${note.todos.size} 项待办 · ${formatWords(countWords(note.content))} 字", onClose = close)
                            Spacer(Modifier.height(14.dp))
                            SettingRow(
                                "关联章节",
                                if (chapterRefs.isEmpty()) "还没有关联章节" else "已关联 ${chapterRefs.size} 个章节",
                                Glyph.Link,
                                onClick = { level = 1 },
                            )
                            RowDivider()
                            SettingRow("固定到顶部", if (note.pinned) "已固定" else "按更新时间排序", Glyph.Pin, trailing = {
                                InkToggle(note.pinned) { on -> store.updateNote(note.id) { it.copy(pinned = on) } }
                            })
                            RowDivider()
                            SettingRow(
                                "移动到笔记夹",
                                store.noteGroups.firstOrNull { it.id == note.groupId }?.name ?: "未归类",
                                Glyph.Folder,
                                onClick = { level = 2 },
                            )
                            RowDivider()
                            PaperStyleMenu()
                            RowDivider()
                            SettingRow("删除笔记", "正文和待办会一起删除", Glyph.Trash, onClick = {
                                pendingMenuAction = { deleteOpen = true }
                                close()
                            })
                        }
                        1 -> SheetPane {
                            NoteChapterSheet(
                                store = store,
                                noteId = note.id,
                                onDismiss = { close() },
                                onOpenChapter = { bookId, chapterId -> onOpenChapter(bookId, chapterId) },
                                embedded = true,
                                onBack = { level = 0 },
                            )
                        }
                        else -> SheetPane {
                            NoteFolderPickerSheet(
                                current = note.groupId,
                                store = store,
                                onSelect = { group -> store.setNoteGroup(note.id, group) },
                                onDismiss = { close() },
                                embedded = true,
                                onBack = { level = 0 },
                            )
                        }
                    }
                }
            }
        }
    }

    if (groupPickerOpen) {
        NoteFolderPickerSheet(
            current = note.groupId,
            store = store,
            onSelect = { group -> store.setNoteGroup(note.id, group) },
            onDismiss = { groupPickerOpen = false },
        )
    }
    if (chapterSheetOpen) {
        NoteChapterSheet(
            store = store,
            noteId = note.id,
            onDismiss = { chapterSheetOpen = false },
            onOpenChapter = { bookId, chapterId ->
                chapterSheetOpen = false
                onOpenChapter(bookId, chapterId)
            },
        )
    }
    if (deleteOpen) {
        ConfirmDialog(
            "删除这条笔记？",
            "正文和 ${note.todos.size} 项待办会被删除，无法恢复。",
            "删除",
            {
                ReminderScheduler.cancelNote(context, note)
                store.deleteNote(note.id)
                deleteOpen = false
                onBack()
                ToastBus.show("笔记已删除")
            },
            { deleteOpen = false },
            destructive = true,
        )
    }
}

@Composable
fun TodoSheet(store: AppStore, noteId: String, onDismiss: () -> Unit) {
    val ui = LocalInk.current
    val context = LocalContext.current
    val note = store.data.notes.firstOrNull { it.id == noteId } ?: return
    var text by remember { mutableStateOf("") }
    var remindAt by remember { mutableStateOf<Long?>(null) }
    var permissionAsked by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) ToastBus.show("通知权限未开启，提醒不会弹出")
    }
    fun requestPermissionIfNeeded() {
        if (!ReminderScheduler.canPost(context) && !permissionAsked) {
            permissionAsked = true
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun addTodo() {
        val value = text.trim()
        if (value.isEmpty()) {
            ToastBus.show("先写下要做的事。")
            return
        }
        val todo = Todo(text = value, remindAt = remindAt)
        val updated = note.copy(todos = note.todos + todo)
        store.updateNote(note.id) { it.copy(todos = it.todos + todo) }
        if (remindAt != null) {
            ReminderScheduler.schedule(context, updated, todo)
            requestPermissionIfNeeded()
        }
        text = ""
        remindAt = null
        ToastBus.show(if (todo.remindAt != null) "待办已添加，提醒已设置" else "待办已添加")
    }

    InlineModalSheet(onDismiss) { _ ->
        DialogTitle("待办与提醒", "和正文一起保存，到点会在手机上提醒。", onClose = onDismiss)
        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) {
                InkField(text, { text = it.take(80) }, "下一件要做的事", minHeight = 48.dp, textSize = 14f, onDone = { addTodo() })
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp)) {
                GlassIconButton(Glyph.Plus, { addTodo() })
            }
        }
        Spacer(Modifier.height(11.dp))
        Txt("提醒时间", size = 9.5f, color = ui.palette.muted, modifier = Modifier.padding(start = 4.dp, bottom = 7.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val slots = reminderSlots()
            items(slots) { slot ->
                GlassChip(slot.second, remindAt == slot.first) { remindAt = if (remindAt == slot.first) null else slot.first }
            }
            item {
                GlassChip("自定义", false) { customOpen = true }
            }
        }
        if (remindAt != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(Glyph.Clock, ui.accent, 13.dp)
                Spacer(Modifier.width(5.dp))
                Txt("将在 ${formatDateTime(remindAt!!)} 提醒", size = 9.5f, color = ui.palette.muted)
            }
        }
        Spacer(Modifier.height(17.dp))
        val active = note.todos
        if (active.isEmpty()) {
            Txt("还没有待办。加一条，让今天有落点。", size = 10.5f, color = ui.palette.muted, modifier = Modifier.padding(vertical = 14.dp))
        } else {
            active.forEach { todo ->
                TodoRow(
                    todo = todo,
                    onToggle = { done ->
                        store.updateNote(note.id) { n ->
                            n.copy(todos = n.todos.map { if (it.id == todo.id) it.copy(done = done) else it })
                        }
                        if (done) {
                            ReminderScheduler.cancel(context, note.id, todo.id)
                        } else if (todo.remindAt != null && todo.remindAt > System.currentTimeMillis()) {
                            ReminderScheduler.schedule(context, note, todo.copy(done = false))
                        }
                    },
                    onDelete = {
                        ReminderScheduler.cancel(context, note.id, todo.id)
                        store.updateNote(note.id) { n -> n.copy(todos = n.todos.filterNot { it.id == todo.id }) }
                    },
                )
                RowDivider(inset = 0.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (customOpen) {
        ReminderTimeDialog(
            initial = remindAt,
            onDismiss = { customOpen = false },
            onPick = {
                remindAt = it
                customOpen = false
            },
        )
    }
}

@Composable
private fun InlineReminderPanel(
    todo: Todo,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    val ui = LocalInk.current
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val shape = SmoothCorner(22.dp)
    val base = remember(todo.id, todo.remindAt) {
        todo.remindAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
            ?: LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
    }
    var date by remember(todo.id, todo.remindAt) { mutableStateOf(base.toLocalDate()) }
    var hour by remember(todo.id, todo.remindAt) { mutableIntStateOf(base.hour) }
    var minute by remember(todo.id, todo.remindAt) { mutableIntStateOf(base.minute) }
    var second by remember(todo.id, todo.remindAt) { mutableIntStateOf(base.second) }

    fun openDatePicker() {
        android.app.DatePickerDialog(
            context,
            { _, year, month, day -> date = LocalDate.of(year, month + 1, day) },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
        ).show()
    }

    MoyuSurface(modifier,
        radius = 22f,
        shape = shape,
        flat = true,
    ) {
    Column(
        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(todo.id) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        if (dragAmount < -6f) onDismiss()
                    }
                }
                .clickable { openDatePicker() }
                .padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(Glyph.Clock, ui.accent, 14.dp)
            Spacer(Modifier.width(7.dp))
            Txt("${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日", size = 13f)
            Spacer(Modifier.weight(1f))
            Txt("改日期", size = 9f, color = ui.palette.muted)
        }
        Box(Modifier.fillMaxWidth().height(.8.dp).background(ui.palette.line.copy(alpha = .55f)))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            MiniStepper(String.format(Locale.US, "%02d", hour), { hour = (hour + 23) % 24 }, { hour = (hour + 1) % 24 })
            Txt(":", size = 16f, weight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 3.dp))
            MiniStepper(String.format(Locale.US, "%02d", minute), { minute = (minute + 59) % 60 }, { minute = (minute + 1) % 60 })
            Txt(":", size = 16f, weight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 3.dp))
            MiniStepper(String.format(Locale.US, "%02d", second), { second = (second + 59) % 60 }, { second = (second + 1) % 60 })
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (todo.remindAt != null) {
                GlassButton("删除提醒", onDelete, Modifier.weight(1f).height(46.dp))
            } else {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(SmoothCorner(23.dp))
                        .background(ui.palette.faint),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("删除提醒", size = 12f, color = ui.palette.muted.copy(alpha = .35f))
                }
            }
            PrimaryButton("确定", {
                val target = LocalDateTime.of(date, LocalTime.of(hour, minute, second))
                    .atZone(zone).toInstant().toEpochMilli()
                if (target <= System.currentTimeMillis()) {
                    ToastBus.show("提醒时间已经过去了，换一个吧。")
                } else {
                    onConfirm(target)
                }
            }, Modifier.weight(1.08f))
        }
    }
    }
}

@Composable
private fun MiniStepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val ui = LocalInk.current
    Row(
        Modifier.clip(SmoothCorner(14.dp)).background(ui.palette.faint),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clickable { onMinus() }, contentAlignment = Alignment.Center) {
            Txt("－", size = 16f, color = ui.palette.ink)
        }
        Txt(value, size = 12f, weight = FontWeight.Medium, modifier = Modifier.widthIn(min = 26.dp), align = TextAlign.Center)
        Box(Modifier.size(28.dp).clickable { onPlus() }, contentAlignment = Alignment.Center) {
            Txt("+", size = 15f, color = ui.palette.ink)
        }
    }
}

@Composable
private fun TodoReminderDialog(
    initial: Long?,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val ui = LocalInk.current
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val base = remember(initial) {
        initial?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
            ?: LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
    }
    var date by remember { mutableStateOf(base.toLocalDate()) }
    var hour by remember { mutableIntStateOf(base.hour) }
    var minute by remember { mutableIntStateOf(base.minute) }
    var second by remember { mutableIntStateOf(base.second) }

    fun openDatePicker() {
        android.app.DatePickerDialog(
            context,
            { _, year, month, day -> date = LocalDate.of(year, month + 1, day) },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
        ).show()
    }

    GlassDialog(onDismiss) {
        DialogTitle("提醒", "自定义年月日和时分，精确到分钟。", onClose = onDismiss)
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(SmoothCorner(18.dp))
                .background(ui.palette.faint)
                .clickable { openDatePicker() }
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(Glyph.Clock, ui.accent, 16.dp)
            Spacer(Modifier.width(9.dp))
            Txt("${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日", size = 13f)
            Spacer(Modifier.weight(1f))
            Txt("改日期", size = 9f, color = ui.palette.muted)
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SmoothCorner(18.dp))
                .background(ui.palette.faint)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(String.format(Locale.US, "%02d", hour), { hour = (hour + 23) % 24 }, { hour = (hour + 1) % 24 })
                Txt(":", size = 20f, weight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp))
                Stepper(String.format(Locale.US, "%02d", minute), { minute = (minute + 59) % 60 }, { minute = (minute + 1) % 60 })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("秒", size = 9f, color = ui.palette.muted)
                Spacer(Modifier.width(8.dp))
                Stepper(String.format(Locale.US, "%02d", second), { second = (second + 59) % 60 }, { second = (second + 1) % 60 })
            }
        }
        // The do-not-disturb note lives once, in the new-reminder dialog; the
        // edit panel repeated it verbatim right above the delete button.
        if (onDelete != null) {
            Spacer(Modifier.height(12.dp))
            GlassButton("删除提醒", onDelete, Modifier.fillMaxWidth().height(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", onDismiss, Modifier.weight(1f))
            PrimaryButton("设为提醒", {
                val target = LocalDateTime.of(date, LocalTime.of(hour, minute, second))
                    .atZone(zone).toInstant().toEpochMilli()
                if (target <= System.currentTimeMillis()) {
                    ToastBus.show("提醒时间已经过去了，换一个吧。")
                } else {
                    onPick(target)
                }
            }, Modifier.weight(1.08f))
        }
    }
}

@Composable
private fun ReminderTimeDialog(initial: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val ui = LocalInk.current
    val zone = ZoneId.systemDefault()
    val base = remember(initial) {
        initial?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
            ?: LocalDateTime.now().plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0)
    }
    var date by remember { mutableStateOf(base.toLocalDate()) }
    var hour by remember { mutableIntStateOf(base.hour) }
    var minute by remember { mutableIntStateOf(base.minute / 5 * 5) }
    val dates = listOf(
        LocalDate.now() to "今天",
        LocalDate.now().plusDays(1) to "明天",
        LocalDate.now().plusDays(2) to "后天",
        LocalDate.now().plusDays(7) to "一周后",
    )
    GlassDialog(onDismiss) {
        DialogTitle("自定义提醒", "选择日期和时间，到点会通知你。", onClose = onDismiss)
        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(dates) { (d, label) ->
                GlassChip(label, date == d) { date = d }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(SmoothCorner(20.dp))
                .background(ui.palette.faint)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Txt("${date.monthValue} 月 ${date.dayOfMonth} 日", size = 12f, color = ui.palette.muted)
                Spacer(Modifier.height(6.dp))
                Txt(String.format(Locale.US, "%02d:%02d", hour, minute), size = 32f, weight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Stepper(
                    String.format(Locale.US, "%02d", hour),
                    { hour = (hour + 23) % 24 },
                    { hour = (hour + 1) % 24 },
                )
                Spacer(Modifier.height(6.dp))
                Stepper(
                    String.format(Locale.US, "%02d", minute),
                    { minute = (minute + 55) % 60 },
                    { minute = (minute + 5) % 60 },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Txt("提醒会遵守系统的免打扰设置。", size = 9f, color = ui.palette.muted)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", onDismiss, Modifier.weight(1f))
            PrimaryButton("设为提醒", {
                val target = LocalDateTime.of(date, LocalTime.of(hour, minute))
                    .atZone(zone).toInstant().toEpochMilli()
                if (target <= System.currentTimeMillis()) {
                    ToastBus.show("提醒时间已经过去了，换一个吧。")
                } else {
                    onPick(target)
                }
            }, Modifier.weight(1.08f))
        }
    }
}

@Composable
private fun TodoRow(todo: Todo, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val ui = LocalInk.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedTodoBox(done = todo.done, onToggle = { onToggle(it) })
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val textColor by animateColorAsState(
                if (todo.done) ui.palette.muted else ui.palette.ink,
                tween(160),
                label = "todoText",
            )
            Txt(
                todo.text,
                size = 12.5f,
                color = textColor,
                maxLines = 2,
                decoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (todo.remindAt != null) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(Glyph.Clock, if (todo.done) ui.palette.muted else ui.accent, 10.dp)
                    Spacer(Modifier.width(3.dp))
                    Txt(
                        formatDateTime(todo.remindAt),
                        size = 8.5f,
                        color = if (todo.done) ui.palette.muted.copy(alpha = .6f) else ui.palette.muted,
                    )
                }
            }
        }
        Box(Modifier.size(32.dp).bouncy { onDelete() }, contentAlignment = Alignment.Center) {
            LineIcon(Glyph.Close, ui.palette.muted.copy(alpha = .6f), 13.dp)
        }
    }
}

private fun reminderSlots(): List<Pair<Long?, String>> {
    val now = LocalDateTime.now()
    val zone = ZoneId.systemDefault()
    fun at(date: LocalDate, time: LocalTime): Long = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    val slots = mutableListOf<Pair<Long?, String>>()
    slots += (now.plusHours(1).atZone(zone).toInstant().toEpochMilli()) to "1 小时后"
    slots += (now.plusHours(3).atZone(zone).toInstant().toEpochMilli()) to "3 小时后"
    val tonight = at(now.toLocalDate(), LocalTime.of(20, 0))
    if (tonight > System.currentTimeMillis()) slots += tonight to "今晚 20:00"
    slots += at(now.toLocalDate().plusDays(1), LocalTime.of(8, 0)) to "明天 08:00"
    slots += at(now.toLocalDate().plusDays(1), LocalTime.of(20, 0)) to "明天 20:00"
    slots += at(now.toLocalDate().plusDays(3), LocalTime.of(9, 0)) to "3 天后"
    slots += at(now.toLocalDate().plusWeeks(1), LocalTime.of(9, 0)) to "一周后"
    return slots
}

// ---------------------------------------------------------------------------
// Preview. The dock is the surface whose material is easiest to judge wrongly by
// eye, so it gets a tile of its own. Haze still falls back to static glass in
// layoutlib; real blur is only visible on a device.
// ---------------------------------------------------------------------------

@Preview(name = "写作 Dock", widthDp = 412, heightDp = 210, showBackground = true)
@Composable
private fun EditorDockPreview() {
    MoyuTheme(AppSettings(theme = "night", motion = "none")) {
        CompositionLocalProvider(LocalHazeHandle provides null) {
            Box(
                Modifier.fillMaxSize().background(LocalInk.current.palette.bgTop),
                contentAlignment = Alignment.BottomCenter,
            ) {
                EditorDock(
                    canUndo = true,
                    canRedo = false,
                    isNote = true,
                    pendingTodos = 2,
                    onUndo = {},
                    onRedo = {},
                    onIndent = {},
                    onQuote = {},
                    onDivider = {},
                    onType = {},
                    onTodos = {},
                    boundNotes = 3,
                    onNotes = {},
                    onNotesLongPress = {},
                    onHideKeyboard = {},
                    onCollapse = {},
                    onSymbol = {},
                    onExpand = {},
                    selectionMode = false,
                    onCopy = {},
                    onCut = {},
                    onPaste = {},
                    onSelectAll = {},
                    onDelete = {},
                    onCancelSelection = {},
                )
            }
        }
    }
}

/**
 * Draws the native editor view into the dock's 1型玻璃 backdrop.
 *
 * A Compose `layerBackdrop` records Compose draw content only, so it cannot see
 * the `AndroidView` `BodyEditText`; `View.draw` into a software bitmap can. The
 * bitmap is cached against [BodyEditText.renderRevision] so scrolling and
 * typing re-record it, while a still page costs one cached draw per frame.
 */
private class EditorBodyBackdrop(
    private val body: () -> BodyEditText?,
    private val paper: Color,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    private var cache: Bitmap? = null
    private var cacheWidth = 0
    private var cacheHeight = 0
    private var cacheRevision = -1L
    private var cachePaper = 0uL

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val view = body() ?: return
        val dock = coordinates ?: return
        if (!view.isAttachedToWindow || !view.isLaidOut || view.width == 0) return
        val viewLoc = IntArray(2)
        view.getLocationInWindow(viewLoc)
        val dockLoc = dock.positionInWindow()
        // Opaque paper first, otherwise the sharp text underneath ghosts through
        // the blurred copy.
        drawRect(paper)
        clipRect(0f, 0f, size.width, size.height) {
            // Re-record the body every draw. renderRevision stayed 0 in practice
            // (BodyEditText.onScrollChanged does not fire for this viewport), so
            // a revision-keyed cache froze the glass on its first frame. The
            // dock's draw is already invalidated by bodyScrollTick, so redrawing
            // here is what makes the text follow the scroll.
            val w = view.width
            val h = view.height
            var bitmap = cache
            if (bitmap == null || bitmap.width != w || bitmap.height != h ||
                cachePaper != paper.value
            ) {
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                cache = bitmap
                cacheWidth = w
                cacheHeight = h
                cachePaper = paper.value
            } else {
                bitmap.eraseColor(0)
            }
            view.draw(Canvas(bitmap))
            cacheRevision = view.renderRevision
            // Crop only the slice under the dock instead of drawing the whole
            // bitmap at a large negative offset.
            val srcX = (dockLoc.x - viewLoc[0]).toInt().coerceAtLeast(0)
            val srcY = (dockLoc.y - viewLoc[1]).toInt().coerceAtLeast(0)
            val srcW = min(size.width.toInt(), bitmap.width - srcX)
            val srcH = min(size.height.toInt(), bitmap.height - srcY)
            if (srcW > 0 && srcH > 0) {
                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcOffset = androidx.compose.ui.unit.IntOffset(srcX, srcY),
                    srcSize = androidx.compose.ui.unit.IntSize(srcW, srcH),
                    dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                    dstSize = androidx.compose.ui.unit.IntSize(srcW, srcH),
                )
            }
        }
    }
}

/** Dock lens: edge refraction + RGB dispersion over the sampled body slice. */
private const val DOCK_REFRACTION_AGSL = """
uniform shader content;
uniform float2 size;
uniform float amount;
uniform float dispersion;
uniform float edge;

half4 main(float2 coord) {
    float d = clamp(coord.y / edge, 0.0, 1.0);
    float k = (1.0 - d) * (1.0 - d);
    float2 base = clamp(coord, float2(0.0), size - float2(1.0));
    float2 off = float2(0.0, -k * amount);
    float2 cr = clamp(base + off * (1.0 + dispersion), float2(0.0), size - float2(1.0));
    float2 cb = clamp(base + off * (1.0 - dispersion), float2(0.0), size - float2(1.0));
    return half4(content.eval(cr).r, content.eval(base).g, content.eval(cb).b, 1.0);
}
"""
