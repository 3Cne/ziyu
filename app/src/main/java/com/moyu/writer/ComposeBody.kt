package com.moyu.writer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Test branch: the native `BodyEditText` rewritten as a Compose field.
 *
 * Why: `Modifier.layerBackdrop` records Compose draw content only. A native
 * EditText hosted through `AndroidView` draws itself in the View tree, so the
 * glass dock could never sample the real glyphs (3.8.3 / 3.9.9 / 3.9.12 all
 * hit this). With the body inside the Compose tree the same
 * `rememberLayerBackdrop()` that the TabBar uses now sees the text.
 *
 * The TextFieldState overload of BasicTextField owns its scroll range and
 * keeps the caret above the IME; the trailing spacer in `decorationBox` adds
 * the dock height to that range, so the last line still stops above the dock
 * while text keeps drawing under it for the glass to sample.
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
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val state = remember(seedKey) { TextFieldState(initialText) }
    val scrollState = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val latestBody by rememberUpdatedState(body)
    val latestChange by rememberUpdatedState(onBodyChange)
    var applyingExternal by remember { mutableStateOf(false) }
    // Last value this field itself published. When `body` comes back equal to
    // it the effect must not write into the field again: a redundant full
    // replace resets the IME composition region and the keyboard re-commits
    // the word (this showed up as doubled words and dropped spaces).
    var lastPublished by remember { mutableStateOf(TextFieldValue(initialText, TextRange.Zero)) }

    // Compose mirror -> field. Undo, the symbol row and reminder-line edits all
    // change `body` from outside, so the field has to accept them too.
    LaunchedEffect(body) {
        val current = state.text.toString()
        if (current == body.text && state.selection == body.selection) return@LaunchedEffect
        // Echo of our own publish: the field already holds it.
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

    // Field -> Compose mirror. This is also where the automatic indent lands:
    // a single trailing newline typed at the caret gets the two ideographic
    // spaces immediately, matching the native TextWatcher branch.
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() to state.selection }
            .collect { (text, selection) ->
                if (applyingExternal) return@collect
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

    val lineStep = (bodySize * settings.lineHeight).sp
    val style = TextStyle(
        color = palette.ink,
        fontSize = bodySize.sp,
        lineHeight = (bodySize * settings.lineHeight).sp,
        fontFamily = fontFamilyFor(settings.font),
        letterSpacing = 0.sp,
    )
    val paperStepPx = with(density) { lineStep.toPx() }
    val dockInsetDp = with(density) { dockBottomInsetPx.toDp() }

    val indentTransformation = remember(settings.indent) {
        // The indent is applied in the snapshot collector above, not here: the
        // transformation also runs for external replaces (undo / symbol row),
        // where inserting spaces would double-indent.
        null as InputTransformation?
    }

    // The 1.9 TextFieldState overload takes a [TextFieldDecorator] instead of a
    // decorationBox. Wrapping the inner field in a Column leaves room for the
    // dock height as trailing content, so the last line still stops above the
    // dock while the glyphs keep drawing under it.
    val currentSettings = settings
    val currentPalette = palette
    val currentSize = bodySize
    val currentHint = placeholder
    val dockDp = dockInsetDp
    val decorator = remember(state, currentSettings, currentPalette, currentSize, currentHint, dockDp) {
        object : androidx.compose.foundation.text.input.TextFieldDecorator {
            @Composable
            override fun Decoration(innerTextField: @Composable () -> Unit) {
                Column {
                    Box {
                        if (state.text.isEmpty()) {
                            Txt(
                                currentHint,
                                size = currentSize,
                                color = currentPalette.muted.copy(alpha = .42f),
                                fontFamily = fontFamilyFor(currentSettings.font),
                                lineHeight = currentSize * currentSettings.lineHeight,
                            )
                        }
                        innerTextField()
                    }
                    Spacer(Modifier.height(dockDp))
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        BasicTextField(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = settings.pageMargin.dp)
                .drawBehind {
                    val result = layout ?: return@drawBehind
                    val scroll = scrollState.value.toFloat()
                    val textNow = state.text
                    // Done todos get the native TodoDoneSpan equivalent: a
                    // thin strike across the line. The box glyph itself is the
                    // source of truth; the model supplies the done flag.
                    if (textNow.isNotEmpty()) {
                        val strikeColor = palette.ink.copy(alpha = .42f)
                        var boxIndex = 0
                        var cursor = 0
                        while (cursor < textNow.length) {
                            val next = textNow.indexOf('\n', cursor).let { if (it < 0) textNow.length else it }
                            val isBox = cursor < textNow.length &&
                                (textNow[cursor] == '☐' || textNow[cursor] == '☑')
                            if (isBox && textNow[cursor] == '☑' &&
                                inlineTodos.getOrNull(boxIndex)?.done == true
                            ) {
                                val firstLine = result.getLineForOffset(cursor)
                                val lastLine = result.getLineForOffset((next - 1).coerceAtLeast(cursor))
                                for (line in firstLine..lastLine) {
                                    val top = result.getLineTop(line) - scroll
                                    val bottom = result.getLineBottom(line) - scroll
                                    val mid = (top + bottom) / 2f
                                    val lineStart = result.getLineStart(line)
                                    val lineEnd = result.getLineEnd(line)
                                    val from = if (line == firstLine) cursor else lineStart
                                    var to = (lineEnd - 1).coerceAtLeast(from)
                                    if (to < textNow.length && textNow[to] == '\n') {
                                        to = (to - 1).coerceAtLeast(from)
                                    }
                                    val left = result.getBoundingBox(from).left
                                    val right = result.getBoundingBox(to).right
                                    if (right > left) {
                                        drawLine(
                                            strikeColor,
                                            Offset(left, mid),
                                            Offset(right, mid),
                                            1.2.dp.toPx(),
                                        )
                                    }
                                }
                            }
                            if (isBox) boxIndex++
                            cursor = next + 1
                        }
                    }
                    if (settings.paperStyle == "plain") return@drawBehind
                    val lineColor = palette.ink.copy(alpha = (settings.paperLineAlpha * .38f).coerceIn(0f, 1f))
                    val solid = settings.paperStyle == "solid"
                    val stroke = 1.dp.toPx()
                    var index = 0
                    while (index < result.lineCount) {
                        val y = result.getLineBottom(index) - scroll
                        if (y > -4f && y < size.height + 4f) {
                            if (solid) {
                                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke)
                            } else {
                                val dash = 5.dp.toPx()
                                val gap = 7.dp.toPx()
                                var x = 0f
                                while (x < size.width) {
                                    drawLine(
                                        lineColor,
                                        Offset(x, y),
                                        Offset((x + dash).coerceAtMost(size.width), y),
                                        stroke,
                                    )
                                    x += dash + gap
                                }
                            }
                        }
                        index++
                    }
                }
                .pointerInput(state, layout, inlineTodos) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val result = layout ?: return@awaitEachGesture
                        val text = state.text.toString()
                        // Hit the box glyph itself. getOffsetForPosition only
                        // gives a character, so the box's own bounds decide:
                        // a tap on the space after it used to start a text
                        // selection instead of toggling.
                        val scroll = scrollState.value
                        val y = down.position.y + scroll
                        // Which visual line contains the tap, by its vertical
                        // band. getOffsetForPosition returns the nearest caret
                        // and, for a tap at the left edge, picked the next
                        // line, so the box was never recognised.
                        var line = 0
                        while (line < result.lineCount - 1 && y > result.getLineBottom(line)) line++
                        val lineStart = result.getLineStart(line)
                        val isBoxChar = lineStart < text.length &&
                            (text[lineStart] == '☐' || text[lineStart] == '☑')
                        val slop = 12.dp.toPx()
                        val box = if (isBoxChar) result.getBoundingBox(lineStart) else null
                        val isBox = box != null &&
                            down.position.x in (box.left - slop)..(box.right + slop) &&
                            y in (box.top - slop)..(box.bottom + slop)
                        if (!isBox) return@awaitEachGesture
                        // Claim the down before BasicTextField does; otherwise
                        // its handler consumes the up and waitForUpOrCancellation
                        // returns null (tap moved the caret but never toggled).
                        down.consume()
                        // Wait for the up in the Initial pass. BasicTextField's
                        // inner handler consumes the up in the Main pass, which
                        // made waitForUpOrCancellation return null and the box
                        // only moved the caret.
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
                        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
                        val content = text.substring((lineStart + 2).coerceAtMost(lineEnd), lineEnd).trim()
                        // Todo lines follow inlineTodos order; the live text can
                        // drift from the stored todo.text, so index first.
                        var boxIndex = 0
                        var cursor = 0
                        while (cursor < lineStart) {
                            val next = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
                            if (next > cursor && (text[cursor] == '☐' || text[cursor] == '☑')) boxIndex++
                            cursor = next + 1
                        }
                        val id = inlineTodos.getOrNull(boxIndex)?.id
                            ?: inlineTodos.firstOrNull { it.start == lineStart }?.id
                            ?: inlineTodos.firstOrNull { it.text.trim() == content }?.id
                        if (id != null) {
                            // The native path flipped the span glyph and the todo
                            // model together; here the text is the source of the
                            // box, so flip it before reporting the toggle.
                            val next = if (text[lineStart] == '☑') '☐' else '☑'
                            state.edit { replace(lineStart, lineStart + 1, next.toString()) }
                            // Model follows the glyph. The stored todo may
                            // already be out of sync (the old native span and
                            // the model could disagree); only toggle when the
                            // model is different from the glyph's new state.
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
            inputTransformation = indentTransformation,
            decorator = decorator,
        )
    }
}

/** Compose-only editor switches for the test branch. */
internal object EditorTest {
    const val composeBody = true
    const val glassDock = true
}
