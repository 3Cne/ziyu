package com.moyu.writer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.animation.togetherWith

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
                    chapterTitle = chapter.title.ifBlank { chapterFallbackTitle(index) },
                    chapterIndex = index,
                )
                break
            }
        }
    }
    return out
}

/**
 * Every note a chapter carries: the ones bound one by one, plus everything that
 * currently sits in a linked 笔记夹. This is the single resolution point — the
 * notes panel count, the floating card and the AI readers all go through it, so
 * a note dropped into a linked folder shows up everywhere without extra wiring.
 */
fun AppData.notesOfChapter(chapter: Chapter): List<Note> {
    val folderIds = chapter.folderIds.toSet()
    val direct = if (chapter.noteIds.isEmpty()) emptyList()
    else chapter.noteIds.mapNotNull { id -> notes.firstOrNull { it.id == id } }
    if (folderIds.isEmpty()) return direct
    val fromFolders = notes.filter { it.groupId != null && it.groupId in folderIds }
    val seen = HashSet<String>(direct.size + fromFolders.size)
    return buildList {
        direct.forEach { if (seen.add(it.id)) add(it) }
        fromFolders.forEach { if (seen.add(it.id)) add(it) }
    }
}

/** Notes that belong to one 笔记夹. 未归类 (folderId 为 null) 不在任何夹里。 */
fun AppData.notesOfFolder(folderId: String?): List<Note> =
    notes.filter { it.groupId == folderId }

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
    /** true = 作为「写作选项 → 章节笔记」同框层级的第二层画在面板里。 */
    embedded: Boolean = false,
    onBack: (() -> Unit)? = null,
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
    var showCreate by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    // 3.24.1：链接改成两步——先只出「笔记夹」这一层，点整行链接整个夹、点右侧箭头
    // 进夹再挑单条。原来把「笔记夹 chips + 已绑定 + 可选笔记」三块同时摊开，
    // 菜单一下就撑满了（用户报「菜单会很大」），而且夹和笔记混在一屏里分不清。
    var picking by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf(false) }
    var browseId by remember { mutableStateOf<String?>(null) }

    // 单条绑定的 + 链接夹里的，都算「这一章的笔记」。
    val linked = data.notesOfChapter(chapter)
    val linkedFolderIds = chapter.folderIds.toSet()
    val folders = data.noteGroups
    val singleIds = chapter.noteIds.toSet()
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

    /** 解除一条已绑定的笔记：单条绑定的直接解；来自笔记夹的只能去解那个夹。 */
    fun unlink(note: Note) {
        if (note.id in singleIds) {
            setLinked(note.id, false)
            return
        }
        val folder = folders.firstOrNull { it.id == note.groupId && it.id in linkedFolderIds }
        ToastBus.show(
            if (folder != null) "这条在《${folder.name}》里，取消链接整个夹才会移出"
            else "这条笔记不在单条绑定里"
        )
    }

    val body: @Composable androidx.compose.foundation.layout.ColumnScope.((() -> Unit)) -> Unit = { close ->
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = Triple(picking, browsing, browseId),
            transitionSpec = {
                val forward = targetState.first &&
                    (!initialState.first || (targetState.second && !initialState.second))
                val dir = if (forward) 1 else -1
                // 3.26.21：只滑不淡 + 整宽（与故事面板同口径），每层自带 SheetPane 纸底。
                slideInHorizontally(spring(MOYU_DAMPING, 420f)) { it * dir } togetherWith
                    slideOutHorizontally(spring(MOYU_DAMPING, 420f)) { -it * dir }
            },
            label = "linkPicker",
        ) { state ->
            val isPicking = state.first
            val inFolder = state.second
            val folderId = state.third
            when {
                !isPicking -> SheetPane(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("已绑定")
                        Spacer(Modifier.weight(1f))
                        GlassChip(if (showCreate) "取消新建" else "新建", showCreate) {
                            showCreate = !showCreate
                        }
                        Spacer(Modifier.width(6.dp))
                        GlassChip("+ 链接", false) { picking = true; browsing = false }
                    }
                    if (linked.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Txt(
                            "还没有绑定的笔记或笔记夹。点「+ 链接」去挑。",
                            size = 10.5f,
                            color = p.muted,
                            lineHeight = 16f,
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        linked.forEach { note ->
                            RowDivider()
                            NotePickRow(note, checked = true) { unlink(note) }
                        }
                    }
                    if (showCreate) {
                        Spacer(Modifier.height(10.dp))
                        NoteTitleField(newTitle, { newTitle = it }, onConfirm = { createAndBind() })
                    }
                }

                !inFolder -> SheetPane(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassChip("取消", false) { picking = false }
                        Spacer(Modifier.weight(1f))
                        Txt("点夹链接整个夹", size = 9.5f, color = p.muted)
                    }
                    Spacer(Modifier.height(8.dp))
                    folders.forEach { folder ->
                        RowDivider()
                        FolderPickRow(
                            name = folder.name,
                            count = data.notesOfFolder(folder.id).size,
                            linked = folder.id in linkedFolderIds,
                            onToggle = {
                                store.setChapterFolder(book.id, chapter.id, folder.id, folder.id !in linkedFolderIds)
                            },
                            onEnter = { browseId = folder.id; browsing = true },
                        )
                    }
                    // 「未归类」不是夹，不能整夹链接，只能进去挑单条。
                    RowDivider()
                    FolderPickRow(
                        name = "未归类",
                        count = data.notesOfFolder(null).size,
                        linked = null,
                        onToggle = null,
                        onEnter = { browseId = null; browsing = true },
                    )
                }

                else -> SheetPane(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassChip("‹ 笔记夹", false) { browsing = false }
                        Spacer(Modifier.width(9.dp))
                        Txt(
                            folders.firstOrNull { it.id == folderId }?.name ?: "未归类",
                            size = 13f,
                            weight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val inThis = data.notesOfFolder(folderId)
                    // 进夹后，夹已整体链接时每条都应显示「已包含」，否则用户会以为
                    // 勾选没生效（计数据本来就没涨——它们已经在里面了）。
                    val folderLinked = folderId != null && folderId in linkedFolderIds
                    if (inThis.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Txt("这个笔记夹还是空的。", size = 10.5f, color = p.muted)
                    } else {
                        inThis.forEach { note ->
                            RowDivider()
                            val single = note.id in singleIds
                            NotePickRow(note, checked = single || folderLinked) { on ->
                                if (folderLinked && !single) {
                                    ToastBus.show("这一夹已经链接了，取消夹才会移出")
                                } else {
                                    setLinked(note.id, on)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
    val subtitle = "${book.title} · ${chapter.title}，已绑定 ${linked.size} 条"
    if (embedded) {
        Column(Modifier.fillMaxWidth()) {
            SheetPageHeader("章节笔记", subtitle, onBack ?: onDismiss)
            body { onDismiss() }
        }
    } else {
        InlineModalSheet(onDismiss = onDismiss) { close ->
            DialogTitle("章节笔记", subtitle, onClose = close)
            body(close)
        }
    }
}

/**
 * 一个笔记夹：点整行 = 链接/取消整个夹，点右侧箭头 = 进夹挑单条。
 * `linked = null` 表示这条不能整夹链接（「未归类」），整行只用来进夹。
 */
@Composable
private fun FolderPickRow(
    name: String,
    count: Int,
    linked: Boolean?,
    onToggle: (() -> Unit)?,
    onEnter: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(SmoothCorner(14.dp))
                .then(
                    if (onToggle != null) Modifier.bouncy(scale = .99f) { onToggle() }
                    else Modifier
                )
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(SmoothCorner(10.dp))
                    .background(if (linked == true) ui.accent.copy(alpha = .12f) else p.faint),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(Glyph.Folder, if (linked == true) ui.accent else p.muted, 15.dp)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Txt(name, size = 13.5f, weight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Txt("$count 条笔记", size = 9.5f, color = p.muted)
            }
            if (linked != null) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (linked) ui.accent else p.faint),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(
                        if (linked) Glyph.Check else Glyph.Plus,
                        if (linked) (if (p.isDark) p.bgTop else Color.White) else p.muted,
                        14.dp,
                    )
                }
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .bouncy(scale = .92f) { onEnter() },
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(Glyph.Chevron, p.muted.copy(alpha = .6f), 15.dp)
        }
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
                .clip(SmoothCorner(10.dp))
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
        // 3.26.9：底色从中心扩散，+ 缩掉、勾弹出——和 SelectMark 同一套。
        // 未选必须保留「加号」：它才是「点我绑定」的提示，不能只剩一个空圈。
        val v by animateFloatAsState(
            targetValue = if (checked) 1f else 0f,
            animationSpec = if (ui.settings.motion == "none") tween(0) else spring(MOYU_DAMPING, 520f),
            label = "pickMark",
        )
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .drawBehind {
                    val side = size.minDimension / 2f
                    drawCircle(p.faint, radius = side)
                    if (v > .001f) drawCircle(ui.accent, radius = side * v.coerceIn(0f, 1f))
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.graphicsLayer {
                    alpha = 1f - v
                    scaleX = 1f - .25f * v
                    scaleY = 1f - .25f * v
                }
            ) {
                LineIcon(Glyph.Plus, p.muted, 14.dp)
            }
            if (v > .001f) {
                Box(
                    Modifier.graphicsLayer {
                        alpha = v.coerceIn(0f, 1f)
                        val s = .55f + .45f * v.coerceIn(0f, 1f)
                        scaleX = s
                        scaleY = s
                    }
                ) {
                    LineIcon(Glyph.Check, if (p.isDark) p.bgTop else Color.White, 14.dp)
                }
            }
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
            .clip(SmoothCorner(14.dp))
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
 * Read-only chapter labels for a note. Tapping one opens that chapter.
 *
 * 2026-11-15：这排 chip 从正文上方移到顶栏右侧按钮下方，所以不再自己占一整行
 * 宽度，也不再带「绑定章节」那行小标签字（删掉不影响使用，§J）。
 */
@Composable
fun NoteChapterTags(
    refs: List<NoteChapterRef>,
    modifier: Modifier = Modifier,
    onOpen: (NoteChapterRef) -> Unit,
) {
    if (refs.isEmpty()) return
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        refs.forEach { ref ->
            NoteChapterTag(ref) { onOpen(ref) }
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
    // Inline editing: the panel keeps its own drafts so a half-typed note never
    // touches the store until 保存 (or the writer leaves the note/panel).
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var panelTitle by remember { mutableStateOf("") }
    var panelBody by remember { mutableStateOf("") }
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
        animationSpec = if (visible) spring(dampingRatio = .78f, stiffness = 340f, visibilityThreshold = .5f)
        else tween(200, easing = FastOutSlowInEasing),
        visibilityThreshold = .5f,
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
    // Closing the panel while a draft is open keeps the writing instead of
    // dropping it. Same rule as the 保存 button, without the extra tap.
    LaunchedEffect(visible) {
        if (visible) return@LaunchedEffect
        val id = editingNoteId ?: return@LaunchedEffect
        val note = links.notes.firstOrNull { it.id == id }
        if (note != null) {
            links.onSaveNote(id, panelTitle.trim(), panelBody, rebuildNoteTodos(panelBody, note.todos))
        }
        editingNoteId = null
    }
    AnimatedVisibility(
        visible = visible || animatedWidthPx > 1f,
        modifier = modifier,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(140)),
    ) {
        // 内容和宽度一起长出来：开头 14dp 的位移把「从右边进来」说清楚，
        // 收起时反向缩回去，内容不会在宽度还很小的时候先整块闪出来。
        val reveal = (animatedWidthPx / widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        Row(
            Modifier
                .fillMaxHeight()
                .graphicsLayer {
                    // 抽出 / 收回：从右侧滑入，左缘横向轻拉 3%；收起反向。
                    // 粘滞水滴（局部动效档）：横向比纵向的时间点再滞后 15%，
                    // 中间帧宽度先冲出去、再收平 —— 读作被从右边拽出来，
                    // 而不是一个规规矩矩的宽度 tween。幅度控制在 4.5% 内，文字不糊。
                    val revealLag = ViscousDrop.lagged(reveal)
                    alpha = reveal
                    translationX = (1f - reveal) * 22.dp.toPx()
                    scaleX = 1f + .03f * (1f - reveal) + .30f * (reveal - revealLag)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, .5f)
                },
        ) {
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
                    // the page away by accident. 列表和正文左右滑过去，返回反向。
                    AnimatedContent(
                        targetState = selectedNoteId,
                        transitionSpec = {
                            val opening = targetState != null
                            val dir = if (opening) 1 else -1
                            (
                                slideInHorizontally(spring(MOYU_DAMPING, 420f)) { dir * it / 3 } +
                                    fadeIn(tween(160))
                            ).togetherWith(
                                slideOutHorizontally(tween(150)) { -dir * it / 3 } +
                                    fadeOut(tween(120)),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        label = "notePanelPage",
                    ) { openId ->
                    val selected = links.notes.firstOrNull { it.id == openId }
                    if (selected == null) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            links.notes.forEach { note ->
                                NoteFloatItem(note) { selectedNoteId = note.id }
                            }
                        }
                    } else {
                        val editing = editingNoteId == selected.id

                        fun persistDraft() {
                            val note = links.notes.firstOrNull { it.id == selected.id } ?: return
                            val body = panelBody
                            links.onSaveNote(note.id, panelTitle.trim(), body, rebuildNoteTodos(body, note.todos))
                        }

                        // Ticking a ☐/☑ in the read-only view saves that box
                        // immediately: it is a one-tap action, not an edit.
                        fun toggleTodo(todo: Todo) {
                            val source = selected.content
                            if (todo.start !in source.indices) return
                            val mark = source[todo.start]
                            if (mark != '☐' && mark != '☑') return
                            val next = if (mark == '☑') '☐' else '☑'
                            val body = source.substring(0, todo.start) + next + source.substring(todo.start + 1)
                            links.onSaveNote(
                                selected.id,
                                selected.title,
                                body,
                                selected.todos.map { if (it.id == todo.id) it.copy(done = next == '☑') else it },
                            )
                        }

                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .bouncy(scale = .88f) {
                                            if (editing) persistDraft()
                                            editingNoteId = null
                                            selectedNoteId = null
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.graphicsLayer { scaleX = -1f }) {
                                        LineIcon(Glyph.Chevron, p.muted, 12.dp)
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                                if (editing) {
                                    BasicTextField(
                                        value = panelTitle,
                                        onValueChange = { if (it.length <= 80) panelTitle = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = p.ink,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = fontFamilyFor(ui.settings.font),
                                        ),
                                        cursorBrush = SolidColor(ui.accent),
                                        decorationBox = { inner ->
                                            Box(Modifier.fillMaxWidth()) {
                                                if (panelTitle.isBlank()) {
                                                    Txt("无标题笔记", size = 11.5f, color = p.muted.copy(alpha = .55f))
                                                }
                                                inner()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
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
                            }
                            Spacer(Modifier.height(6.dp))
                            if (editing) {
                                BasicTextField(
                                    value = panelBody,
                                    onValueChange = { panelBody = it },
                                    textStyle = TextStyle(
                                        color = p.ink,
                                        fontSize = 11.sp,
                                        lineHeight = 19.sp,
                                        fontFamily = fontFamilyFor(ui.settings.font),
                                    ),
                                    cursorBrush = SolidColor(ui.accent),
                                    decorationBox = { inner ->
                                        Box(Modifier.fillMaxSize()) {
                                            if (panelBody.isBlank()) {
                                                Txt("这条笔记还是空的。", size = 11f, color = p.muted.copy(alpha = .5f))
                                            }
                                            inner()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            } else {
                                var textLayout by remember(selected.id, selected.content) {
                                    mutableStateOf<TextLayoutResult?>(null)
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    if (selected.content.isBlank()) {
                                        Txt("这条笔记还是空的。", size = 11f, color = p.muted.copy(alpha = .7f))
                                    } else {
                                        BasicText(
                                            text = selected.content,
                                            style = TextStyle(
                                                color = p.ink,
                                                fontSize = 11.sp,
                                                lineHeight = 19.sp,
                                                fontFamily = fontFamilyFor(ui.settings.font),
                                            ),
                                            onTextLayout = { textLayout = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pointerInput(selected.id, selected.content) {
                                                    detectTapGestures { pos ->
                                                        val layout = textLayout ?: return@detectTapGestures
                                                        val offset = layout.getOffsetForPosition(pos)
                                                        val todo = selected.todos.firstOrNull {
                                                            offset >= it.start && offset < it.end
                                                        }
                                                        if (todo != null) toggleTodo(todo)
                                                    }
                                                },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (editing) {
                                    PanelAction("取消", primary = false) {
                                        editingNoteId = null
                                    }
                                    PanelAction("保存", primary = true) {
                                        persistDraft()
                                        editingNoteId = null
                                    }
                                } else {
                                    PanelAction("编辑", primary = true) {
                                        clearEditorFocus?.invoke()
                                        panelTitle = selected.title
                                        panelBody = selected.content
                                        editingNoteId = selected.id
                                    }
                                    PanelAction("打开笔记", primary = false) {
                                        editingNoteId = null
                                        onOpenNote(selected)
                                    }
                                }
                            }
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
            .clip(SmoothCorner(14.dp))
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
    /** true = 作为「笔记选项 → 关联章节」同框层级的第二层画在面板里。 */
    embedded: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val data = store.data
    val note = data.notes.firstOrNull { it.id == noteId }
    if (note == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.((() -> Unit)) -> Unit = { close ->
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
                    // Tapping the name opens that chapter, exactly as the
                    // subtitle promises. It used to toggle the binding: the
                    // writer checked two chapters, tapped a name to go write,
                    // and silently unlinked the chapter they just added.
                    Column(
                        Modifier.weight(1f).bouncy(scale = .99f) {
                            close()
                            onOpenChapter(book.id, chapter.id)
                        },
                    ) {
                        Txt(
                            chapter.title.ifBlank { chapterFallbackTitle(index) },
                            size = 13.5f,
                            weight = if (bound) FontWeight.Medium else FontWeight.Normal,
                            color = if (bound) ui.accent else p.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Txt(
                            if (chapter.noteIds.contains(note.id)) "已关联 · 点名字去写作"
                            else "未关联 · 右侧开关添加",
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
    val subtitle = "已关联 ${note.chapterIds.size} 个章节。点章节名去那一章写作，右侧开关切换关联。"
    if (embedded) {
        Column(Modifier.fillMaxWidth()) {
            SheetPageHeader("关联章节", subtitle, onBack ?: onDismiss)
            body { onDismiss() }
        }
    } else {
        InlineModalSheet(onDismiss = onDismiss) { close ->
            DialogTitle("关联章节", subtitle, onClose = close)
            body(close)
        }
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
                val title = book.chapters[index].title.ifBlank { chapterFallbackTitle(index) }
                return if (note.chapterIds.size > 1) "$title 等 ${note.chapterIds.size} 章" else title
            }
        }
    }
    return null
}

/**
 * Rebuild the todo spans after an edit inside the floating panel.
 *
 * The panel edits plain text, so offsets cannot be patched one by one: every
 * line that starts with ☐/☑ becomes a todo again, and the old ids/reminders are
 * kept by order so a ticked box keeps its reminder.
 */
fun rebuildNoteTodos(content: String, previous: List<Todo>): List<Todo> {
    val out = mutableListOf<Todo>()
    var lineStart = 0
    var order = 0
    while (lineStart <= content.length) {
        val lineEnd = content.indexOf('\n', lineStart).let { if (it < 0) content.length else it }
        val mark = content.getOrNull(lineStart)
        if (mark == '☐' || mark == '☑') {
            val old = previous.getOrNull(order)
            out += Todo(
                id = old?.id ?: uuid(),
                text = content.substring((lineStart + 1).coerceAtMost(lineEnd), lineEnd).trim(),
                done = mark == '☑',
                remindAt = old?.remindAt,
                notified = old?.notified ?: false,
                start = lineStart,
                end = lineEnd,
            )
            order++
        }
        if (lineEnd >= content.length) break
        lineStart = lineEnd + 1
    }
    return out
}

/** One of the two bottom actions inside the floating note panel. */
@Composable
private fun RowScope.PanelAction(label: String, primary: Boolean, onClick: () -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    Box(
        Modifier
            .weight(1f)
            .clip(SmoothCorner(12.dp))
            .background(if (primary) ui.accent else p.faint)
            .bouncy(scale = .97f) { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(
            label,
            size = 11f,
            color = if (primary) (if (p.isDark) p.paper else Color.White) else p.ink,
            weight = FontWeight.Medium,
        )
    }
}

