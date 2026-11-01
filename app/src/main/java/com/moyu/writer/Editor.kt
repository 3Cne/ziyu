package com.moyu.writer

import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private data class EditorSnapshot(
    val title: String,
    val body: TextFieldValue,
    val todos: List<Todo> = emptyList(),
)

data class SelectionBar(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

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
)

@Composable
private fun SelectionOverlay(
    geometry: SelectionGeometry?,
    handleDrawable: android.graphics.drawable.Drawable?,
    color: Color,
    onTopInWindow: (Int) -> Unit,
) {
    if (geometry == null || !geometry.active) return
    Canvas(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onGloballyPositioned { onTopInWindow(it.boundsInWindow().top.toInt()) },
    ) {
        val stroke = 2.5.dp.toPx()
        drawLine(
            color = color,
            start = Offset(geometry.startCaretX, geometry.startTop),
            end = Offset(geometry.startCaretX, geometry.startBottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(geometry.endCaretX, geometry.endTop),
            end = Offset(geometry.endCaretX, geometry.endBottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val d = handleDrawable ?: return@Canvas
        fun drawHandle(box: HandleBox, flipVertically: Boolean) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
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
            }
        }
        drawHandle(geometry.startHandle, flipVertically = true)
        drawHandle(geometry.endHandle, flipVertically = false)
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
    var showSymbols by remember { mutableStateOf(true) }
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
        var newText = text
        var caret = lineEnd

        if (at == null) {
            if (hasReminderLine) {
                val from = if (nextStart > 0 && text[nextStart - 1] == '\n') nextStart - 1 else nextStart
                val to = if (nextEnd < text.length && text[nextEnd] == '\n') nextEnd + 1 else nextEnd
                newText = text.replaceRange(from, to, "")
                caret = from
            }
        } else {
            val ldt = java.time.Instant.ofEpochMilli(at)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            val line = "$REMINDER_LINE_MARK ${ldt.monthValue}月${ldt.dayOfMonth}日 " +
                String.format(java.util.Locale.US, "%02d:%02d:%02d", ldt.hour, ldt.minute, ldt.second)
            if (hasReminderLine) {
                newText = text.replaceRange(nextStart, nextEnd, line)
            } else if (lineEnd < text.length) {
                newText = text.substring(0, lineEnd) + "\n$line" + text.substring(lineEnd)
            } else {
                newText = text + "\n$line"
            }
            caret = lineEnd + 1 + line.length
        }

        if (at != null) ensureReminderPermission()
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
    val navPx = WindowInsets.navigationBars.getBottom(density)
    val imeNowState = rememberUpdatedState(imePx)
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
                // Consume IME and navigation insets directly instead of a manual
                // latched height. The latched path could leave the dock behind the
                // IME on a real keyboard transition (the dock disappeared in the
                // user's recording while text ran under the keyboard).
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            EditorTopBar(
                heading = heading,
                meta = buildString {
                    if (metaPrefix.isNotBlank()) append(metaPrefix).append(" · ")
                    append(if (isNote) "笔记" else "写作中")
                    if (pendingTodos > 0) append(" · $pendingTodos 项待办")
                },
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
            Column(Modifier.weight(1f).fillMaxWidth()) {
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
                        .padding(top = 14.dp, bottom = 4.dp)
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
                            if (state.isFocused) {
                                // The native body owns a separate selection
                                // overlay. Tapping the title must end that
                                // selection before the title keyboard opens.
                                collapseBodySelection()
                            }
                        },
                    textStyle = TextStyle(
                        color = p.ink,
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = settings.pageMargin.dp)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(ui.accent.copy(alpha = .7f)))
                    Spacer(Modifier.width(7.dp))
                    Txt(
                        if (isNote) "字隅笔记" else heading,
                        size = 9.5f,
                        color = p.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Txt(
                        if (saved) "已保存" else "保存中…",
                        size = 8.5f,
                        color = p.muted.copy(alpha = .75f),
                    )
                }
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
                            onTodoSwipe = { _, _ -> },
                        ).apply {
                            background = null
                            val side = maxOf(
                                with(density) { settings.pageMargin.dp.toPx() },
                                (selectHandleWidth.div(2f)) +
                                    with(density) { 4.dp.toPx() },
                            )
                            setPadding(side.toInt(), 0, side.toInt(), panelBottomInset)
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
                        et.setPadding(side.toInt(), 0, side.toInt(), et.panelBottomInset)
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
            if (showSymbols) {
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
                    onHideKeyboard = {
                        hideEditorKeyboard()
                        showSymbols = true
                    },
                    onCollapse = { showSymbols = false },
                    onSymbol = { insertRaw(it) },
                    onExpand = { showSymbols = true },
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
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    GlassIconButton(Glyph.Keyboard, { showSymbols = true }, size = 40.dp)
                }
            }
        }

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
        MoyuSurface(
            SurfaceRole.BAR,
            Modifier.height(48.dp),
            radius = 24f,
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
    var handleColor = 0xFF457F85.toInt()
    private var selectionAnchor = 0
    private var selectionPopup: PopupWindow? = null
    private val longPressRunnable = Runnable { performLongPress() }
    private var downSelectionStart = 0
    private var downSelectionEnd = 0
    private var downCursorVisible = true
    var autoIndent = false
    var indentGuard = false
    private var redirectingSelection = false
    var lastTextLength = 0
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
    private var inputLocked = false

    fun setInputLocked(locked: Boolean) {
        inputLocked = locked
    }

    fun setPanelInset(px: Int) {
        panelBottomInset = px.coerceAtLeast(0)
        setPadding(paddingLeft, 0, paddingRight, panelBottomInset)
    }
    private var todoDownSpan: TodoBoxSpan? = null
    private var todoSwipeSpan: TodoBoxSpan? = null
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

    private fun toggleTodoBox(box: TodoBoxSpan) {
        val editable = editable() ?: return
        val start = editable.getSpanStart(box)
        if (start < 0 || start >= editable.length) return
        val next = if (editable[start] == '☑') '☐' else '☑'
        val target = next == '☑'
        editable.replace(start, start + 1, next.toString())
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
        super.onLayout(changed, left, top, right, bottom)
        val loc = IntArray(2)
        getLocationInWindow(loc)
        bodyTopInWindow = loc[1]
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
        emitGeometry()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) clearSelectionMenu()
    }

    override fun onDetachedFromWindow() {
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

    private fun contentMaxScroll(): Int =
        (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)

    override fun scrollTo(x: Int, y: Int) {
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

    private fun performLongPress() {
        android.util.Log.d("SelDebug", "performLongPress drag=$dragging consumed=$longPressConsumed")
        if (dragging || longPressConsumed) return
        val value = text ?: return
        val len = value.length
        if (len == 0) return
        val offset = getOffsetForPosition(downX, downY).coerceIn(0, len)
        val delimiters = "，。！？；：、,.!?;:\n\"'“”‘’（）()【】[] \t"
        var start = offset.coerceAtMost(len - 1)
        var end = offset.coerceAtMost(len)
        while (start > 0 && value[start - 1] !in delimiters) start--
        while (end < len && value[end] !in delimiters) end++
        if (start == end) {
            start = offset.coerceAtMost(len - 1)
            end = (start + 1).coerceAtMost(len)
        }
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
        val sb = buildHandleBox(sg.x, sg.top, sg.bottom, above = true) ?: return
        val eb = buildHandleBox(eg.x, eg.top, eg.bottom, above = false) ?: return
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
        val top = if (above) {
            caretTop - h - gap
        } else {
            caretBottom + gap
        }.coerceIn(0f, (height - h).coerceAtLeast(0).toFloat())
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

    private fun drawSelfCursor(canvas: Canvas) {
        if (!hasFocus() || !isCursorVisible || selectionStart != selectionEnd) return
        val layout = layout ?: return
        val offset = selectionStart.coerceIn(0, layout.text.length)
        val line = layout.getLineForOffset(offset)
        val step = paperLineStep.takeIf { it > 0f } ?: return
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
        if ((android.os.SystemClock.uptimeMillis() / 500L) % 2L == 0L) {
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
        postInvalidateDelayed(500L - (android.os.SystemClock.uptimeMillis() % 500L))
    }

    override fun onDraw(canvas: Canvas) {
        drawPaper(canvas)
        super.onDraw(canvas)
        drawSelfCursor(canvas)
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
                if (selectionBarActive) {
                    val a = handleBounds(selectionStart.coerceAtLeast(0))
                    val b = handleBounds(selectionEnd.coerceAtLeast(0))
                    android.util.Log.d(
                        "SelDebug",
                        "DOWN ${event.x},${event.y} a=$a b=$b active=$selectionBarActive",
                    )
                    if (a != null && b != null) {
                        val d = resources.displayMetrics.density
                        val pad = 24f * d
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
                            handleDrag = 0
                            android.util.Log.d("SelDebug", "HIT start")
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        if (hit(b)) {
                            handleDrag = 1
                            android.util.Log.d("SelDebug", "HIT end")
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        val ac = PointF(a.centerX, a.centerY)
                        val bc = PointF(b.centerX, b.centerY)
                        val halfW = 44f * d
                        val above = 64f * d
                        val below = 72f * d
                        fun hitCenter(c: PointF): Boolean =
                            kotlin.math.abs(event.x - c.x) <= halfW &&
                                event.y >= c.y - above && event.y <= c.y + below
                        if (hitCenter(ac)) {
                            handleDrag = 0
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        if (hitCenter(bc)) {
                            handleDrag = 1
                            parent?.requestDisallowInterceptTouchEvent(true)
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
                                handleDrag = if (
                                    kotlin.math.hypot(event.x - ac.x, event.y - ac.y) <
                                    kotlin.math.hypot(event.x - bc.x, event.y - bc.y)
                                ) 0 else 1
                                parent?.requestDisallowInterceptTouchEvent(true)
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
                    android.util.Log.d("SelDebug", "MOVE drag=$handleDrag ${event.x},${event.y}")
                    val offset = getOffsetForPosition(event.x, event.y).coerceIn(0, text.length)
                    val s = selectionStart.coerceAtLeast(0)
                    val e = selectionEnd.coerceAtLeast(0)
                    if (handleDrag == 0) setSelection(minOf(offset, e), maxOf(offset, e))
                    else setSelection(minOf(s, offset), maxOf(s, offset))
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
                if (todoSwipeSpan != null && !todoSwipeConsumed &&
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
                        return true
                    }
                    val offset = getOffsetForPosition(event.x, event.y).coerceIn(0, text.length)
                    val reminderBand = reminderLineVerticalBounds(offset)
                    if (reminderBand != null &&
                        event.y >= reminderBand.first - touchSlop &&
                        event.y <= reminderBand.second + touchSlop
                    ) {
                        activeTodoAt(offset)?.let { onReminderTap?.invoke(it) }
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
                    isCursorVisible = true
                    return true
                }
                if (tappedTodo != null && !wasDragging &&
                    kotlin.math.abs(event.x - downX) <= touchSlop &&
                    kotlin.math.abs(event.y - downY) <= 2f
                ) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    tappedTodo.click()
                    return true
                }
                if (wasDragging) {
                    isCursorVisible = false
                    cancelLongPress()
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.onTouchEvent(cancel)
                    cancel.recycle()
                    startFling(-vy)
                    return true
                }
                isCursorVisible = true
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
                isCursorVisible = downCursorVisible
                velocity?.recycle()
                velocity = null
                return super.onTouchEvent(event)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startFling(velocityY: Float) {
        if (kotlin.math.abs(lastY - downY) < 150f) return
        if (kotlin.math.abs(velocityY) < minFling) return
        val range = contentMaxScroll()
        // At either edge (with a 2px tolerance), do not start any fling at all.
        // This is the hard stop: no second swipe jumping back to the top.
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
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val maxScroll = contentMaxScroll()
            if ((scrollY <= 0 && scroller.currY <= 0) ||
                (scrollY >= (maxScroll - 2).coerceAtLeast(0) && scroller.currY >= maxScroll)
            ) {
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
    meta: String,
    words: Int,
    saved: Boolean,
    showWords: Boolean,
    indentOn: Boolean,
    onToggleIndent: () -> Unit,
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.paper)
            .clipToBounds()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Solid paper back button: a GlassSurface here could be captured at the
        // wrong size during the editor push and leave a white rounded ghost.
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
            Txt(
                heading,
                size = 12.5f,
                weight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Txt(meta, size = 8.5f, color = p.muted, maxLines = 1)
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
            size = 36.dp,
            tint = if (indentOn) ui.accent else p.muted,
            strength = .45f,
        )
        Spacer(Modifier.width(5.dp))
        GlassIconButton(Glyph.More, onMore, size = 40.dp, strength = .45f)
    }
}

private val symbols = listOf(
    "，", "。", "、", "；", "：", "？", "！", "“", "”", "‘", "’",
    "（", "）", "《", "》", "〈", "〉", "【", "】", "……", "——", "·", "～", "　",
)

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
    onHideKeyboard: () -> Unit,
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
) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp)
            .padding(bottom = 6.dp)
    ) {
        MoyuSurface(
            SurfaceRole.BAR,
            Modifier.fillMaxWidth(),
            radius = 26f,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp)) {
                AnimatedContent(
                    targetState = selectionMode,
                    transitionSpec = {
                        (slideInHorizontally(tween(170)) { it / 3 } + fadeIn(tween(150))) togetherWith
                            (slideOutHorizontally(tween(150)) { -it / 3 } + fadeOut(tween(120)))
                    },
                    label = "dockSelection",
                ) { selecting ->
                    if (selecting) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            DockTool(Glyph.Share, "复制", true, onCopy)
                            DockTool(Glyph.Check, "全选", true, onSelectAll)
                            DockTool(Glyph.Edit, "剪切", true, onCut)
                            DockTool(Glyph.Download, "粘贴", true, onPaste)
                            DockTool(Glyph.Trash, "删除", true, onDelete)
                            Spacer(Modifier.weight(1f))
                            DockTool(Glyph.Close, "取消", true, onCancelSelection)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            DockTool(Glyph.Undo, "撤销", canUndo, onUndo)
                            DockTool(Glyph.Redo, "重做", canRedo, onRedo)
                            DockTool(Glyph.Indent, "缩进", true, onIndent)
                            DockTool(Glyph.Quote, "引号", true, onQuote)
                            DockTool(Glyph.Divider, "分节", true, onDivider)
                            if (isNote && onTodos != null) {
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
                                        DockTool(Glyph.Clock, "提醒", true, onTodos)
                                    } else {
                                        DockTool(
                                            Glyph.List,
                                            if (pendingTodos > 0) "待办·$pendingTodos" else "待办",
                                            true,
                                            onTodos,
                                        )
                                    }
                                }
                            }
                            DockTool(Glyph.Font, "排版", true, onType)
                            Spacer(Modifier.weight(1f))
                            DockTool(Glyph.Keyboard, "收起", true, onHideKeyboard)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                LazyRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(symbols) { symbol ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .bouncy(scale = .86f, hapticType = 1) { onSymbol(symbol) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Txt(symbol, size = 15f, color = p.ink, align = TextAlign.Center, maxLines = 1)
                        }
                    }
                    item {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .bouncy(scale = .86f) { onCollapse() },
                            contentAlignment = Alignment.Center,
                        ) {
                            LineIcon(Glyph.ChevronDown, p.muted, 15.dp)
                        }
                    }
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
) {
    val ui = LocalInk.current
    Column(
        Modifier
            .width(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .bouncy(enabled, scale = .88f) { onClick() }
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineIcon(glyph, if (enabled) ui.palette.ink else ui.palette.muted.copy(alpha = .3f), iconSize)
        Spacer(Modifier.height(2.dp))
        Txt(label, size = 7.4f, color = if (enabled) ui.palette.muted else ui.palette.muted.copy(alpha = .3f), maxLines = 1)
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
    ModalSheet(onDismiss) {
        DialogTitle("排版", "改动会立刻作用到编辑器和提笔的手感。", onClose = onDismiss)
        Spacer(Modifier.height(16.dp))
        MoyuSurface(SurfaceRole.CARD, Modifier.fillMaxWidth(), radius = 20f) {
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
) {
    val book = store.book(bookId)
    val chapter = book?.chapters?.firstOrNull { it.id == chapterId } ?: book?.chapters?.firstOrNull()
    if (book == null || chapter == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var pendingMenuAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    Box(Modifier.fillMaxSize()) {
        WritingScreen(
            seedKey = chapter.id,
            heading = book.title,
            metaPrefix = "第 ${book.chapters.indexOf(chapter) + 1} 章",
            initialTitle = chapter.title,
            initialContent = chapter.content,
            placeholder = "从这里开始。写下第一句，故事就有了方向。",
            inputBlocked = menuOpen,
            onSave = { title, content ->
                store.updateChapter(book.id, chapter.id, title = title.ifBlank { "第${book.chapters.indexOf(chapter) + 1}章" }, content = content)
            },
            onBack = onBack,
            onMore = { menuOpen = true },
        )

        if (menuOpen) {
            InlineModalSheet(onDismiss = {
                menuOpen = false
                pendingMenuAction?.invoke()
                pendingMenuAction = null
            }) { close ->
                DialogTitle("写作选项", "${book.title} · ${chapter.title}", onClose = close)
                Spacer(Modifier.height(14.dp))
                SettingRow("书籍详情与目录", "查看章节、封面和统计", Glyph.Book, onClick = {
                    pendingMenuAction = { onOpenBook() }
                    close()
                })
                RowDivider()
                SettingRow("导出这本书", "EPUB / TXT", Glyph.Export, onClick = {
                    pendingMenuAction = { exportOpen = true }
                    close()
                })
                RowDivider()
                PaperStyleMenu()
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
    var pendingMenuAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val pending = note.todos.count { !it.done }

    Box(Modifier.fillMaxSize()) {
        WritingScreen(
            seedKey = note.id,
            heading = note.title,
            initialTitle = note.title,
            initialContent = note.content,
            placeholder = "随便写。灵感、片段、今天没说完的话。",
            isNote = true,
            inputBlocked = menuOpen,
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
                        title = title.ifBlank { "无标题笔记" },
                        content = content,
                    )
                }
            },
            onBack = onBack,
            onMore = { menuOpen = true },
        )

        if (menuOpen) {
            InlineModalSheet(onDismiss = {
                menuOpen = false
                pendingMenuAction?.invoke()
                pendingMenuAction = null
            }) { close ->
                DialogTitle("笔记选项", "${note.todos.size} 项待办 · ${formatWords(countWords(note.content))} 字", onClose = close)
                Spacer(Modifier.height(14.dp))
                SettingRow("固定到顶部", if (note.pinned) "已固定" else "按更新时间排序", Glyph.Pin, trailing = {
                    InkToggle(note.pinned) { on -> store.updateNote(note.id) { it.copy(pinned = on) } }
                })
                RowDivider()
                SettingRow(
                    "移动到分组",
                    store.noteGroups.firstOrNull { it.id == note.groupId }?.name ?: "未分组",
                    Glyph.Layers,
                    onClick = {
                        pendingMenuAction = { groupPickerOpen = true }
                        close()
                    },
                )
                RowDivider()
                PaperStyleMenu()
                RowDivider()
                SettingRow("删除笔记", "正文和待办会一起删除", Glyph.Trash, onClick = {
                    pendingMenuAction = { deleteOpen = true }
                    close()
                })
            }
        }
    }

    if (groupPickerOpen) {
        GroupPickerSheet(
            kind = GroupKind.NOTE,
            current = note.groupId,
            store = store,
            onSelect = { group -> store.setNoteGroup(note.id, group) },
            onDismiss = { groupPickerOpen = false },
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

    ModalSheet(onDismiss, imeAware = true) {
        DialogTitle("待办与提醒", "和正文一起保存，到点会在手机上提醒。", onClose = onDismiss)
        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) {
                InkField(text, { text = it.take(80) }, "下一件要做的事", minHeight = 48.dp, textSize = 14f, onDone = { addTodo() })
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp)) {
                GlassIconButton(Glyph.Plus, { addTodo() }, size = 48.dp)
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
    val shape = RoundedCornerShape(22.dp)
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

    MoyuSurface(
        SurfaceRole.POPUP,
        modifier,
        radius = 22f,
        shape = shape,
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
                        .clip(RoundedCornerShape(23.dp))
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
        Modifier.clip(RoundedCornerShape(14.dp)).background(ui.palette.faint),
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
                .clip(RoundedCornerShape(18.dp))
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
                .clip(RoundedCornerShape(18.dp))
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
        Spacer(Modifier.height(10.dp))
        Txt("提醒会遵守系统的免打扰设置。", size = 9f, color = ui.palette.muted)
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
                .clip(RoundedCornerShape(20.dp))
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

