package com.moyu.writer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/** A chapter a note is bound to, resolved with its book for display and navigation. */
data class NoteChapterRef(
    val bookId: String,
    val bookTitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val chapterIndex: Int,
)

/**
 * Resolve a note's [Note.chapterIds] against the current data. Titles are read
 * live, so renaming a chapter or a book updates every note label without
 * storing a second copy of the title.
 */
fun AppData.noteChapterRefs(note: Note): List<NoteChapterRef> {
    if (note.chapterIds.isEmpty()) return emptyList()
    val out = mutableListOf<NoteChapterRef>()
    note.chapterIds.forEach { chapterId ->
        for (book in books) {
            val index = book.chapters.indexOfFirst { it.id == chapterId }
            if (index >= 0) {
                val chapter = book.chapters[index]
                out += NoteChapterRef(
                    bookId = book.id,
                    bookTitle = book.title,
                    chapterId = chapter.id,
                    chapterTitle = chapter.title.ifBlank { "第${index + 1}章" },
                    chapterIndex = index,
                )
                break
            }
        }
    }
    return out
}

fun AppData.notesOfChapter(chapter: Chapter): List<Note> =
    if (chapter.noteIds.isEmpty()) emptyList()
    else chapter.noteIds.mapNotNull { id -> notes.firstOrNull { it.id == id } }

fun AppData.noteCountOfChapter(chapter: Chapter): Int = notesOfChapter(chapter).size

/**
 * The toolbar sheet behind the dock's 「笔记」 entry: pick which notes this
 * chapter carries, and whether they float while writing.
 */
@Composable
fun ChapterNotesSheet(
    store: AppStore,
    bookId: String,
    chapterId: String,
    onDismiss: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val data = store.data
    val book = data.books.firstOrNull { it.id == bookId }
    val chapter = book?.chapters?.firstOrNull { it.id == chapterId }
    if (book == null || chapter == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var query by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    val linked = chapter.noteIds.mapNotNull { id -> data.notes.firstOrNull { it.id == id } }
    val linkedIds = linked.map { it.id }.toSet()
    val candidates = data.notes
        .filter { it.id !in linkedIds }
        .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
        .sortedByDescending { it.updatedAt }
    val floatOn = ui.settings.noteFloat

    fun setLinked(noteId: String, on: Boolean) {
        store.setNoteLinked(book.id, chapter.id, noteId, on)
    }

    fun createAndBind() {
        val note = Note(title = newTitle.trim())
        store.addNote(note)
        store.setNoteLinked(book.id, chapter.id, note.id, true)
        newTitle = ""
        showCreate = false
        ToastBus.show("已绑定新笔记")
    }

    InlineModalSheet(onDismiss = onDismiss) { close ->
        DialogTitle(
            "章节笔记",
            "${book.title} · ${chapter.title}，已绑定 ${linked.size} 条",
            onClose = close,
        )
        Spacer(Modifier.height(12.dp))
        SettingRow(
            "行文时悬浮",
            if (floatOn) "写作页右侧浮动显示本章笔记" else "关闭后只在工具栏里查看",
            Glyph.Note,
            trailing = {
                InkToggle(floatOn) { on ->
                    store.updateSettings { it.copy(noteFloat = on) }
                    ToastBus.show(if (on) "行文时把笔记悬浮在正文旁" else "已关闭笔记悬浮")
                }
            },
        )
        RowDivider()
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("已绑定")
            Spacer(Modifier.weight(1f))
            GlassChip(if (showCreate) "取消新建" else "新建笔记", showCreate) {
                showCreate = !showCreate
            }
        }
        if (linked.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Txt(
                "还没有绑定笔记。选一条已有笔记，或新建一条只属于这一章的笔记。",
                size = 10.5f,
                color = p.muted,
                lineHeight = 16f,
            )
        } else {
            Spacer(Modifier.height(6.dp))
            linked.forEach { note ->
                RowDivider()
                NotePickRow(note, checked = true) { setLinked(note.id, false) }
            }
        }
        if (showCreate) {
            Spacer(Modifier.height(10.dp))
            NoteTitleField(newTitle, { newTitle = it }, onConfirm = { createAndBind() })
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("可选笔记")
        Spacer(Modifier.height(6.dp))
        NoteTitleField(query, { query = it }, onConfirm = null)
        Spacer(Modifier.height(6.dp))
        if (candidates.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Txt(
                if (query.isBlank()) "没有其他笔记了。" else "没有匹配「${query.trim()}」的笔记。",
                size = 10f,
                color = p.muted.copy(alpha = .8f),
            )
        } else {
            Column(Modifier.heightInForLinks()) {
                candidates.forEach { note ->
                    RowDivider()
                    NotePickRow(note, checked = false) { setLinked(note.id, true) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun Modifier.heightInForLinks(maxHeightDp: Int = Int.MAX_VALUE): Modifier =
    if (maxHeightDp == Int.MAX_VALUE) this else this.heightIn(max = maxHeightDp.dp)

/** Small section label with its own quiet chip, so it never sits on bare paper. */
@Composable
private fun SectionLabel(text: String) {
    val ui = LocalInk.current
    Box(
        Modifier
            .clip(CircleShape)
            .background(ui.palette.faint)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Txt(text, size = 10f, color = ui.palette.muted, weight = FontWeight.Medium)
    }
}

/** A single note row: title + one-line preview + a bind toggle. */
@Composable
private fun NotePickRow(note: Note, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    val preview = note.content.replace("\n", " ").trim()
    Row(
        Modifier
            .fillMaxWidth()
            .bouncy(scale = .99f, hapticType = if (checked) 0 else 1) { onToggle(!checked) }
            .padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (checked) ui.accent.copy(alpha = .12f) else p.faint),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(Glyph.Note, if (checked) ui.accent else p.muted, 15.dp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Txt(
                note.title.ifBlank { "无标题笔记" },
                size = 13.5f,
                weight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Txt(
                if (preview.isBlank()) "空白笔记 · ${formatWords(note.words)} 字"
                else "$preview · ${formatWords(note.words)} 字",
                size = 9.5f,
                color = p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (checked) ui.accent else p.faint),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(
                if (checked) Glyph.Check else Glyph.Plus,
                if (checked) (if (p.isDark) p.bgTop else Color.White) else p.muted,
                14.dp,
            )
        }
    }
}

/** One-line text entry used for the note title and the filter. */
@Composable
private fun NoteTitleField(value: String, onChange: (String) -> Unit, onConfirm: (() -> Unit)?) {
    val ui = LocalInk.current
    val p = ui.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.faint)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(if (onConfirm != null) Glyph.Edit else Glyph.Search, p.muted, 15.dp)
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { if (it.length <= 80) onChange(it) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = p.ink,
                fontSize = 13f.sp,
                fontFamily = fontFamilyFor(ui.settings.font),
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(ui.accent),
            decorationBox = { inner ->
                Box(Modifier.weight(1f)) {
                    if (value.isBlank()) {
                        Txt(
                            if (onConfirm != null) "新笔记标题" else "搜索笔记标题",
                            size = 13f,
                            color = p.muted.copy(alpha = .55f),
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier.weight(1f),
        )
        if (onConfirm != null) {
            Spacer(Modifier.width(8.dp))
            GlassChip("创建", false) { onConfirm() }
        }
    }
}

/**
 * Read-only chapter labels under a note's title. Tapping one opens that chapter.
 */
@Composable
fun NoteChapterTags(
    refs: List<NoteChapterRef>,
    modifier: Modifier = Modifier,
    onOpen: (NoteChapterRef) -> Unit,
) {
    if (refs.isEmpty()) return
    val ui = LocalInk.current
    val p = ui.palette
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon(Glyph.Link, p.muted, 11.dp)
            Spacer(Modifier.width(5.dp))
            Txt(
                if (refs.size == 1) "绑定章节" else "绑定 ${refs.size} 个章节",
                size = 8.5f,
                color = p.muted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            refs.forEach { ref ->
                NoteChapterTag(ref) { onOpen(ref) }
            }
        }
    }
}

@Composable
private fun NoteChapterTag(ref: NoteChapterRef, onClick: () -> Unit) {
    val ui = LocalInk.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(ui.accent.copy(alpha = .10f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onClick()
            }
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(Glyph.Book, ui.accent, 10.dp)
        Spacer(Modifier.width(5.dp))
        Txt(
            "${ref.bookTitle} · ${ref.chapterTitle}",
            size = 8.5f,
            color = ui.palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 190.dp),
        )
        Spacer(Modifier.width(3.dp))
        LineIcon(Glyph.Chevron, ui.accent.copy(alpha = .7f), 9.dp)
    }
}

/**
 * Side notes panel used while writing.
 *
 * The chapter's bound notes slide in from one side and the writing area gives up
 * the same width, so the panel never covers the text and never jumps around over
 * it. The divider on the panel's leading edge is draggable to make it narrower or
 * wider, and that width is remembered. The notes scroll inside the panel; that is
 * the only place a drag scrolls content, dragging the divider only resizes.
 */
@Composable
fun NoteSidePanel(
    links: NoteLinkUi,
    visible: Boolean,
    onOpenNote: (Note) -> Unit,
    /** Drops the body editor's focus so a swipe on the panel cannot move its caret. */
    clearEditorFocus: (() -> Unit)? = null,
    /** Fires once the open/close width animation settles. */
    onWidthSettled: (() -> Unit)? = null,
    /** Fires when the width starts changing, so the body can hold its place. */
    onWidthAnimating: (() -> Unit)? = null,
    /** Fires when a drag on the divider starts, before the width changes. */
    onResizeStart: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val density = LocalDensity.current
    val screenPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val minPx = with(density) { 132.dp.toPx() }
    val maxPx = screenPx * .60f
    // Which note is expanded inside the panel. Null shows the list, so tapping a
    // row browses here and only the 「打开笔记」 button leaves the page.
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    // Seeded once per screen: keying on the stored share would let every
    // persisted value snap the width back in the middle of a drag.
    var widthPx by remember {
        mutableFloatStateOf((screenPx * links.floatW.coerceIn(.34f, .60f)).coerceIn(minPx, maxPx))
    }
    // Writing on open would overwrite the stored share with a sanitized copy
    // and make the panel creep wider on every visit, so the width is saved
    // only when a drag actually ends.
    val persistWidth: () -> Unit = {
        val share = ((widthPx / screenPx).coerceIn(.34f, .60f) * 100f).roundToInt() / 100f
        links.onFloatPlacement(0f, 0f, share, links.floatH)
    }

    // The panel's own width animates from 0 to the stored width. That is what
    // makes the writing column reflow smoothly: the body sits in the same Row
    // with weight(1f), so every frame the panel grows the text becomes exactly
    // that much narrower, instead of jumping once when a slide animation ends.
    val animatedWidthPx by animateFloatAsState(
        targetValue = if (visible) widthPx else 0f,
        animationSpec = if (visible) spring(dampingRatio = .9f, stiffness = 380f)
        else tween(200, easing = FastOutSlowInEasing),
        label = "notePanelWidth",
    )
    // The body holds a scroll anchor while the width is still moving; release
    // it once the panel settles so the reader can scroll freely again.
    val latestSettled = rememberUpdatedState(onWidthSettled)
    val latestAnimating = rememberUpdatedState(onWidthAnimating)
    val latestResizeStart = rememberUpdatedState(onResizeStart)
    // Suppression has to cover BOTH directions. It used to run only when the
    // panel opened, so closing it left TextView free to yank the page back to
    // the caret while the body re-wrapped. Every visible change now brackets the
    // width animation, and the settle delay matches the longer of the two specs.
    LaunchedEffect(visible) {
        latestAnimating.value?.invoke()
        // The final frame of the close re-wraps once more; that single
        // re-wrap was enough to send the page back to the caret, so the
        // suppression has to outlive the width tween itself.
        kotlinx.coroutines.delay(if (visible) 620 else 460)
    }
    AnimatedVisibility(
        visible = visible || animatedWidthPx > 1f,
        modifier = modifier,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(140)),
    ) {
        Row(Modifier.fillMaxHeight()) {
            // Gap between text and panel. It owns the drag rail, so the rail is
            // never drawn on top of the panel's own edge.
            Box(
                Modifier
                    .width(if (animatedWidthPx > 1f) 20.dp else 0.dp)
                    .fillMaxHeight()
                    .pointerInput(screenPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // The first drag frame already re-wraps the body:
                            // snapshot the anchor from the layout the writer is
                            // looking at before the width moves.
                            latestResizeStart.value?.invoke()
                            down.consume()
                            var lastX = down.position.x
                            var moved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dx = change.position.x - lastX
                                lastX = change.position.x
                                if (dx != 0f) {
                                    moved = true
                                    change.consume()
                                    widthPx = (widthPx - dx).coerceIn(minPx, maxPx)
                                }
                            }
                            if (moved) persistWidth()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight(.76f)
                        .clip(CircleShape)
                        .background(p.line),
                )
            }
            MoyuSurface(Modifier
                    .width(with(density) { animatedWidthPx.toDp() })
                    .fillMaxHeight()
                    // Touches that start on the panel belong to it: the note rows
                    // own their taps and the list owns its scroll, so the body
                    // editor behind never receives them.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                radius = 20f,
                // Flat card, same material family as the shelf/note cards: no
                // drop shadow, gradient, rim or bloom. The panel sits right next
                // to body text, and the floating shadow read as a second layer
                // over the page.
                flat = true,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LineIcon(Glyph.Link, ui.accent, 12.dp)
                        Spacer(Modifier.width(5.dp))
                        Txt("本章笔记", size = 10.5f, color = p.muted, weight = FontWeight.Medium)
                        Spacer(Modifier.width(6.dp))
                        Txt("${links.notes.size}", size = 9f, color = p.muted.copy(alpha = .7f))
                    }
                    RowDivider(inset = 14.dp)
                    RowDivider(inset = 14.dp)
                    // Two states in one panel: the note list, and the text of
                    // the note the writer tapped. Opening the note editor from
                    // here is an explicit button, so browsing never navigates
                    // the page away by accident.
                    val selected = links.notes.firstOrNull { it.id == selectedNoteId }
                    if (selected == null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            links.notes.forEach { note ->
                                NoteFloatItem(note) { selectedNoteId = note.id }
                            }
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .bouncy(scale = .88f) { selectedNoteId = null },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.graphicsLayer { scaleX = -1f }) {
                                        LineIcon(Glyph.Chevron, p.muted, 12.dp)
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                                Txt(
                                    selected.title.ifBlank { "无标题笔记" },
                                    size = 11.5f,
                                    color = p.ink,
                                    weight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Txt(
                                    selected.content.ifBlank { "这条笔记还是空的。" },
                                    size = 11f,
                                    color = p.ink,
                                    lineHeight = 19f,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ui.accent)
                                    .bouncy(scale = .97f) { onOpenNote(selected) }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Txt(
                                    "打开笔记",
                                    size = 11f,
                                    color = if (p.isDark) p.paper else Color.White,
                                    weight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Small note card inside the floating panel. */
@Composable
private fun NoteFloatItem(note: Note, onClick: () -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.faint)
            .bouncy(scale = .985f) { onClick() }
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Txt(
            note.title.ifBlank { "无标题笔记" },
            size = 12f,
            weight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val preview = note.content.replace("\n", " ").trim()
        Spacer(Modifier.height(3.dp))
        Txt(
            if (preview.isBlank()) "空白笔记，点开继续写" else preview,
            size = 9.5f,
            color = p.muted,
            lineHeight = 14f,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("${formatWords(note.words)} 字", size = 8f, color = p.muted.copy(alpha = .7f))
            Spacer(Modifier.weight(1f))
            Txt("点开", size = 8f, color = ui.accent.copy(alpha = .8f))
            LineIcon(Glyph.Chevron, ui.accent.copy(alpha = .8f), 8.dp)
        }
    }
}

/**
 * Chapter picker shown from the note editor. Lists every chapter of every book
 * so a note can be tied to a scene it belongs to, not just the current chapter.
 */
@Composable
fun NoteChapterSheet(
    store: AppStore,
    noteId: String,
    onDismiss: () -> Unit,
    onOpenChapter: (String, String) -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val data = store.data
    val note = data.notes.firstOrNull { it.id == noteId }
    if (note == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    InlineModalSheet(onDismiss = onDismiss) { close ->
        DialogTitle(
            "关联章节",
            "已关联 ${note.chapterIds.size} 个章节。点章节名直接去那一章写作。",
            onClose = close,
        )
        Spacer(Modifier.height(12.dp))
        if (data.books.isEmpty()) {
            Txt("书架还是空的。先建一本书，才能关联章节。", size = 11f, color = p.muted)
            Spacer(Modifier.height(14.dp))
        }
        data.books.forEach { book ->
            Txt(book.title, size = 10f, color = p.muted, weight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            book.chapters.forEachIndexed { index, chapter ->
                RowDivider()
                val bound = chapter.id in note.chapterIds
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 11.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f).bouncy(scale = .99f) {
                            store.setNoteLinked(book.id, chapter.id, note.id, !bound)
                        },
                    ) {
                        Txt(
                            chapter.title.ifBlank { "第${index + 1}章" },
                            size = 13.5f,
                            weight = if (bound) FontWeight.Medium else FontWeight.Normal,
                            color = if (bound) ui.accent else p.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Txt(
                            if (chapter.noteIds.contains(note.id)) "已关联 · 点名字取消"
                            else "第 ${index + 1} 章 · 点名字关联",
                            size = 9f,
                            color = p.muted,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (chapter.content.isNotBlank()) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(p.faint)
                                .bouncy(scale = .9f) {
                                    close()
                                    onOpenChapter(book.id, chapter.id)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Txt("去写", size = 9f, color = p.ink)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    InkToggleForLinks(bound) {
                        store.setNoteLinked(book.id, chapter.id, note.id, it)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun InkToggleForLinks(checked: Boolean, onChange: (Boolean) -> Unit) {
    InkToggle(checked, onChange)
}

/** Badge text for a note card, e.g. 「灼石 · 第一章」. */
fun noteChapterBadge(store: AppStore, note: Note): String? {
    if (note.chapterIds.isEmpty()) return null
    val data = store.data
    for (chapterId in note.chapterIds) {
        for (book in data.books) {
            val index = book.chapters.indexOfFirst { it.id == chapterId }
            if (index >= 0) {
                val title = book.chapters[index].title.ifBlank { "第${index + 1}章" }
                return if (note.chapterIds.size > 1) "$title 等 ${note.chapterIds.size} 章" else title
            }
        }
    }
    return null
}

