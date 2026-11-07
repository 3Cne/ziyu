package com.moyu.writer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Test branch: the native BodyEditText rewritten as a Compose field.
 *
 * Modifier.layerBackdrop records Compose draw content only; a native EditText
 * hosted through AndroidView draws itself in the View tree, so the glass dock
 * could never sample the real glyphs (3.8.3 / 3.9.9 / 3.9.12). With the body
 * inside the Compose tree the dock's liquidGlassBar samples the text directly.
 */
@Composable
internal fun ComposeBodyHost(
    seedKey: String,
    initialText: String,
    body: TextFieldValue,
    onBodyChange: (TextFieldValue) -> Unit,
    inlineTodos: List<Todo>,
    onTodoToggle: (String) -> Unit,
    placeholder: String,
    settings: AppSettings,
    palette: InkPalette,
    accent: Color,
    bodySize: Float,
    dockBottomInsetPx: Int,
    onScroll: () -> Unit,
    onController: (ComposeBodyController) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val state = remember(seedKey) { TextFieldState(initialText) }
    val scrollState = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportH by remember { mutableStateOf(0) }
    val latestBody by rememberUpdatedState(body)
    val latestChange by rememberUpdatedState(onBodyChange)
    var applyingExternal by remember { mutableStateOf(false) }
    var suppressRangeClear by remember { mutableStateOf(false) }
    // Last value this field itself published. When \`body\` comes back equal to
    // it, do not write into the field again: a redundant full replace resets
    // the IME composition and the keyboard re-commits the word.
    var lastPublished by remember { mutableStateOf(TextFieldValue(initialText, TextRange.Zero)) }

    val controller = remember(state) { ComposeBodyController(state, context) }
    LaunchedEffect(controller) { onController(controller) }

    // Per-todo check animation: 0 = empty box, 1 = filled + check.
    val checkAnims = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }
    LaunchedEffect(inlineTodos) {
        inlineTodos.forEach { todo ->
            val anim = checkAnims.getOrPut(todo.id) {
                Animatable(if (todo.done) 1f else 0f)
            }
            anim.animateTo(
                targetValue = if (todo.done) 1f else 0f,
                animationSpec = tween(190),
            )
        }
    }

    LaunchedEffect(body) {
        val current = state.text.toString()
        if (current == body.text && state.selection == body.selection) return@LaunchedEffect
        if (body == lastPublished) return@LaunchedEffect
        applyingExternal = true
        runCatching {
            state.edit {
                replace(0, length, body.text)
                selection = TextRange(
                    body.selection.start.coerceIn(0, body.text.length),
                    body.selection.end.coerceIn(0, body.text.length),
                )
            }
        }
        applyingExternal = false
    }

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() to state.selection }
            .collect { (text, selection) ->
                if (applyingExternal) return@collect
                if (!selection.collapsed) {
                    // Native long-press selection: move it into the controller
                    // and collapse the field so the system panel disappears.
                    controller.captureNativeSelection(selection)
                    suppressRangeClear = true
                    val collapsed = TextFieldValue(text, TextRange(selection.end.coerceIn(0, text.length)))
                    lastPublished = collapsed
                    latestChange(collapsed)
                    return@collect
                }
                if (suppressRangeClear) {
                    suppressRangeClear = false
                    return@collect
                }
                if (controller.range != null) controller.clearRange()
                val previous = latestBody
                if (text == previous.text && selection == previous.selection) return@collect
                if (settings.indent && selection.collapsed &&
                    text.length == previous.text.length + 1 && selection.start > 0 &&
                    text[selection.start - 1] == '\n'
                ) {
                    state.edit {
                        replace(selection.start, selection.start, "\u3000\u3000")
                        this.selection = TextRange((selection.start + 2).coerceAtMost(length))
                    }
                    return@collect
                }
                val published = TextFieldValue(text, selection)
                lastPublished = published
                latestChange(published)
            }
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { onScroll() }
    }

    // The dock overlays the body; when the IME opens the viewport shrinks and
    // BasicTextField only keeps the caret inside its own bounds, which still
    // extends under the dock. Push the caret line above the dock explicitly.
    LaunchedEffect(state.selection, dockBottomInsetPx, viewportH, layout) {
        val result = layout ?: return@LaunchedEffect
        if (viewportH <= 0) return@LaunchedEffect
        val limit = viewportH - dockBottomInsetPx - with(density) { 8.dp.toPx() }
        suspend fun clamp() {
            val caret = state.selection.end.coerceIn(0, state.text.length)
            val line = result.getLineForOffset(caret)
            val bottomInView = result.getLineBottom(line) - scrollState.value
            if (bottomInView > limit) {
                scrollState.scrollTo((scrollState.value + bottomInView - limit).toInt())
            }
        }
        clamp()
        // BasicTextField's own bring runs after the IME resize; re-check once it
        // has settled, otherwise the caret lands at the viewport edge behind
        // the dock again.
        kotlinx.coroutines.delay(90)
        clamp()
    }

    val style = TextStyle(
        color = palette.ink,
        fontSize = bodySize.sp,
        lineHeight = (bodySize * settings.lineHeight).sp,
        fontFamily = fontFamilyFor(settings.font),
        letterSpacing = 0.sp,
    )
    val dockInsetDp = with(density) { dockBottomInsetPx.toDp() }
    val selectionColors = TextSelectionColors(
        handleColor = accent,
        backgroundColor = accent.copy(alpha = .22f),
    )

    val decorator = remember(state, settings, palette, bodySize, placeholder, dockInsetDp) {
        object : androidx.compose.foundation.text.input.TextFieldDecorator {
            @Composable
            override fun Decoration(innerTextField: @Composable () -> Unit) {
                Column {
                    Box {
                        if (state.text.isEmpty()) {
                            Txt(
                                placeholder,
                                size = bodySize,
                                color = palette.muted.copy(alpha = .42f),
                                fontFamily = fontFamilyFor(settings.font),
                                lineHeight = bodySize * settings.lineHeight,
                            )
                        }
                        innerTextField()
                    }
                    Spacer(Modifier.height(dockInsetDp))
                }
            }
        }
    }

    Box(modifier.fillMaxSize().onSizeChanged { viewportH = it.height }) {
        CompositionLocalProvider(
            LocalTextToolbar provides NoTextToolbar,
            LocalTextSelectionColors provides selectionColors,
        ) {
            BasicTextField(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = settings.pageMargin.dp)
                    .drawWithContent {
                        drawContent()
                        drawCustomSelection(layout, scrollState.value, controller.range, accent)
                        drawPaperLines(layout, scrollState.value, settings, palette, density.density)
                        drawTodoBoxes(layout, scrollState.value, state, inlineTodos, checkAnims, palette, accent)
                    }
                    .pointerInput(state, layout, inlineTodos) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val result = layout ?: return@awaitEachGesture
                            val text = state.text.toString()
                            val scroll = scrollState.value
                            val slop = 12.dp.toPx()
                            // Hit the exact rectangle the checkbox is drawn in.
                            // A line-band lookup drifted one row from the drawn
                            // box because the canvas and tap spaces differ.
                            var lineStart = -1
                            var hitId: String? = null
                            var boxIndex = 0
                            var cursor = 0
                            while (cursor < text.length) {
                                val next = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
                                if (text[cursor] == '☐' || text[cursor] == '☑') {
                                    val r = todoBoxRect(result, scroll, cursor, 1f)
                                    if (down.position.x in (r.left - slop)..(r.right + slop) &&
                                        down.position.y in (r.top - slop)..(r.bottom + slop)
                                    ) {
                                        lineStart = cursor
                                        hitId = inlineTodos.getOrNull(boxIndex)?.id
                                        break
                                    }
                                    boxIndex++
                                }
                                cursor = next + 1
                            }
                            if (lineStart < 0) return@awaitEachGesture
                            down.consume()
                            var upChange: androidx.compose.ui.input.pointer.PointerInputChange? = null
                            var cancelled = false
                            while (upChange == null && !cancelled) {
                                val event = awaitPointerEvent(
                                    androidx.compose.ui.input.pointer.PointerEventPass.Initial
                                )
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUpIgnoreConsumed()) {
                                    upChange = change
                                } else if ((change.position - down.position).getDistance() >
                                    viewConfiguration.touchSlop
                                ) {
                                    cancelled = true
                                } else {
                                    change.consume()
                                }
                            }
                            val up = upChange
                            if (cancelled || up == null) return@awaitEachGesture
                            up.consume()
                            val id = hitId
                                ?: inlineTodos.firstOrNull { it.start == lineStart }?.id
                            if (id != null) {
                                val next = if (text[lineStart] == '☑') '☐' else '☑'
                                state.edit { replace(lineStart, lineStart + 1, next.toString()) }
                                val modelDone = inlineTodos.firstOrNull { it.id == id }?.done
                                if (modelDone != (next == '☑')) onTodoToggle(id)
                            }
                        }
                    },
                textStyle = style,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default,
                ),
                lineLimits = TextFieldLineLimits.MultiLine(),
                onTextLayout = { getResult -> layout = getResult() },
                cursorBrush = SolidColor(accent),
                scrollState = scrollState,
                inputTransformation = null as InputTransformation?,
                decorator = decorator,
            )
        }
    }
}

/** Hides Compose's floating selection panel; the editor dock carries the actions. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
    override fun hide() = Unit
}

/** The rectangle a todo checkbox is painted in, shared by draw and hit-test. */
private fun todoBoxRect(
    layout: TextLayoutResult,
    scroll: Int,
    offset: Int,
    progress: Float,
): Rect {
    val b = layout.getBoundingBox(offset)
    val top = b.top - scroll
    val bottom = b.bottom - scroll
    val glyphW = (b.right - b.left).coerceAtLeast(1f)
    val glyphH = (bottom - top).coerceAtLeast(1f)
    val side = minOf(glyphW * .92f, glyphH * .58f) * (0.92f + 0.08f * progress)
    val cx = b.left + glyphW / 2f
    val cy = (top + bottom) / 2f
    return Rect(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
}

/** Highlight + accent handles for the controller-owned selection. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCustomSelection(
    result: TextLayoutResult?,
    scroll: Int,
    range: TextRange?,
    accent: Color,
) {
    if (range == null || range.collapsed) return
    val layout = result ?: return
    val start = range.min.coerceIn(0, layout.layoutInput.text.length)
    val end = range.max.coerceIn(0, layout.layoutInput.text.length)
    if (end <= start) return
    val firstLine = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset((end - 1).coerceAtLeast(start))
    val fill = accent.copy(alpha = .20f)
    for (line in firstLine..lastLine) {
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        val from = maxOf(start, lineStart)
        val to = minOf(end, lineEnd)
        if (to <= from) continue
        val x1 = layout.getBoundingBox(from).left
        val x2 = layout.getBoundingBox((to - 1).coerceAtLeast(from)).right
        val top = layout.getLineTop(line) - scroll
        val bottom = layout.getLineBottom(line) - scroll
        drawRect(fill, Offset(x1, top), Size((x2 - x1).coerceAtLeast(2f), bottom - top))
    }
    val handleRadius = 5.5.dp.toPx()
    val startBox = layout.getBoundingBox(start)
    val endBox = layout.getBoundingBox((end - 1).coerceAtLeast(start))
    drawCircle(accent, handleRadius, Offset(startBox.left, startBox.bottom - scroll + handleRadius))
    drawCircle(accent, handleRadius, Offset(endBox.right, endBox.bottom - scroll + handleRadius))
}

/** Paper rules across the whole page, not only where text happens to sit. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPaperLines(
    result: TextLayoutResult?,
    scroll: Int,
    settings: AppSettings,
    palette: InkPalette,
    densityScale: Float,
) {
    if (settings.paperStyle == "plain") return
    val layout = result ?: return
    if (layout.lineCount == 0) return
    val step = if (layout.lineCount > 1) {
        layout.getLineTop(1) - layout.getLineTop(0)
    } else {
        (layout.layoutInput.style.fontSize.value * densityScale * settings.lineHeight)
    }
    if (step <= 0f) return
    val lineColor = palette.ink.copy(alpha = (settings.paperLineAlpha * .38f).coerceIn(0f, 1f))
    val solid = settings.paperStyle == "solid"
    val stroke = 1.dp.toPx()
    fun rule(atY: Float) {
        if (solid) {
            drawLine(lineColor, Offset(0f, atY), Offset(size.width, atY), stroke)
        } else {
            val dash = 5.dp.toPx()
            val gap = 7.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    lineColor,
                    Offset(x, atY),
                    Offset((x + dash).coerceAtMost(size.width), atY),
                    stroke,
                )
                x += dash + gap
            }
        }
    }
    // Same anchoring as the stable BodyEditText.drawPaper(): first baseline +
    // descent + a third of the leftover space, then a fixed grid. Anchoring on
    // line boxes put the rules at the next line's top, which read as misaligned.
    val em = (step / settings.lineHeight.coerceAtLeast(.1f)).coerceAtLeast(1f)
    val descent = em * .25f
    val glyphHeight = em * 1.16f
    val gap = ((step - glyphHeight) * .35f).coerceIn(2f, 6f * densityScale)
    var y = layout.getLineBaseline(0) + descent + gap - scroll
    while (y > -step) y -= step
    while (y < size.height + step) {
        rule(y)
        y += step
    }
}

/** Rounded checkboxes drawn over the ☐/☑ characters, with a spring-ish fill. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTodoBoxes(
    result: TextLayoutResult?,
    scroll: Int,
    state: TextFieldState,
    todos: List<Todo>,
    anims: Map<String, Animatable<Float, AnimationVector1D>>,
    palette: InkPalette,
    accent: Color,
) {
    val layout = result ?: return
    val text = state.text
    if (text.isEmpty()) return
    var boxIndex = 0
    var cursor = 0
    while (cursor < text.length) {
        val next = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        val isBox = cursor < text.length && (text[cursor] == '☐' || text[cursor] == '☑')
        if (isBox) {
            val todo = todos.getOrNull(boxIndex)
            val glyphDone = text[cursor] == '☑'
            val progress = todo?.let { (anims[it.id]?.value ?: if (glyphDone) 1f else 0f) }
                ?: if (glyphDone) 1f else 0f
            val bounds = layout.getBoundingBox(cursor)
            val top = bounds.top - scroll
            val bottom = bounds.bottom - scroll
            if (bottom > -24f && top < size.height + 24f) {
                val rect = todoBoxRect(layout, scroll, cursor, progress)
                val side = rect.width
                val left = rect.left
                val boxTop = rect.top
                val cy = rect.center.y
                // Opaque paper hides the raw glyph underneath.
                drawRect(
                    color = palette.paper,
                    topLeft = Offset(bounds.left - 2f, top - 1f),
                    size = Size(bounds.width + 4f, bounds.height + 2f),
                )
                val radius = side * .34f
                drawRoundRect(
                    color = lerp(Color.Transparent, accent, progress),
                    topLeft = Offset(left, boxTop),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(radius, radius),
                )
                drawRoundRect(
                    color = palette.ink.copy(alpha = .38f * (1f - progress)),
                    topLeft = Offset(left, boxTop),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = 1.4.dp.toPx()),
                )
                if (progress > .02f) {
                    val path = Path().apply {
                        moveTo(left + side * .26f, cy + side * .01f)
                        lineTo(left + side * .43f, cy + side * .18f)
                        lineTo(left + side * .75f, cy - side * .19f)
                    }
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = progress.coerceIn(0f, 1f)),
                        style = Stroke(
                            width = (2.dp.toPx() * (0.5f + progress * .5f)),
                            cap = StrokeCap.Round,
                        ),
                    )
                }
            }
            boxIndex++
        }
        cursor = next + 1
    }
}

/** Dock-side text actions for the Compose body (the native menuAction is gone). */
internal class ComposeBodyController(
    private val state: TextFieldState,
    private val context: Context,
) {
    // Selection lives here, not in the text field: a non-collapsed
    // TextFieldState selection makes MIUI/Sogou show their own floating text
    // panel, which the dock replaces. The field keeps a collapsed caret.
    var range by mutableStateOf<TextRange?>(null)
        private set
    private val clipboard: ClipboardManager?
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    fun captureNativeSelection(selection: TextRange) {
        range = selection
        // Collapse the field's own selection so the system floating panel has
        // nothing to anchor to.
        state.edit { this.selection = TextRange(selection.end.coerceIn(0, length)) }
    }

    fun clearRange() {
        if (range != null) range = null
    }

    private fun chosen(): String {
        val r = range
        if (r != null && !r.collapsed) {
            return state.text.substring(r.min.coerceIn(0, state.text.length), r.max.coerceIn(0, state.text.length))
        }
        val start = minOf(state.selection.start, state.selection.end).coerceIn(0, state.text.length)
        val end = maxOf(state.selection.start, state.selection.end).coerceIn(0, state.text.length)
        return state.text.substring(start, end)
    }

    fun selectAll() {
        val len = state.text.length
        range = TextRange(0, len)
        state.edit { selection = TextRange(len) }
    }

    fun collapse() {
        range = null
        state.edit { selection = TextRange(selection.end.coerceIn(0, length)) }
    }

    fun copy() {
        val text = chosen()
        if (text.isNotEmpty()) {
            clipboard?.setPrimaryClip(ClipData.newPlainText("字隅", text))
        }
    }

    fun cut() {
        if (chosen().isNotEmpty()) {
            copy()
            deleteSelection()
        }
    }

    fun paste() {
        val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isEmpty()) return
        state.edit {
            val start = minOf(selection.start, selection.end).coerceIn(0, length)
            val end = maxOf(selection.start, selection.end).coerceIn(0, length)
            replace(start, end, text)
            selection = TextRange((start + text.length).coerceAtMost(length))
        }
    }

    fun deleteSelection() {
        val r = range
        val start = (r?.min ?: minOf(state.selection.start, state.selection.end)).coerceIn(0, state.text.length)
        val end = (r?.max ?: maxOf(state.selection.start, state.selection.end)).coerceIn(0, state.text.length)
        range = null
        state.edit {
            if (end > start) replace(start, end, "")
        }
    }
}

/** Compose-only editor switches for the test branch. */
internal object EditorTest {
    const val composeBody = true
    const val glassDock = true
}
