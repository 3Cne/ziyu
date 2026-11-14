package com.moyu.writer

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.flow.first
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Plain text styled by `Txt`, so the model's markdown leftovers read as noise:
 * a bubble showed a literal "**第一章**". Strip the emphasis markers and heading
 * hashes; keep the words.
 */
private fun cleanReply(text: String): String = text
    .replace("**", "")
    .replace("__", "")
    .replace(Regex("(?m)^#{1,6}\\s*"), "")
    .trim()

/** One compact line of tool arguments for the transcript strip. */
private fun traceArgs(raw: String): String {
    val o = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrNull() ?: return ""
    val keys = o.keys()
    val parts = ArrayList<String>()
    while (keys.hasNext() && parts.size < 2) {
        val k = keys.next()
        val v = o.opt(k)?.toString().orEmpty().replace(Regex("\\s+"), " ").trim()
        if (v.isBlank()) continue
        parts += if (v.length > 34) k + "=" + v.take(34) + "…" else k + "=" + v
    }
    return parts.joinToString("  ")
}

/**
 * The per-step tool strip: one collapsed line ("用了 3 个工具 · 读章、联网搜索"),
 * tap to see each step with its arguments and result. A failed step gets the
 * accent dot, so "it didn't work" is visible without opening anything.
 */
@Composable
private fun ToolTraceStrip(tools: List<ToolTrace>, ui: InkUi, p: InkPalette, id: String) {
    var open by remember(id) { mutableStateOf(false) }
    val failed = tools.any { !it.ok }
    // The chevron turns with the same spring the box expands on, so the two
    // read as one motion instead of "text appears, icon snaps".
    val chevron by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = spring(dampingRatio = .52f, stiffness = 300f),
        label = "toolChevron",
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(SmoothCorner(9.dp))
                .bouncy { open = !open }
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(if (failed) ui.accent else p.muted.copy(alpha = .55f), CircleShape)
            )
            Spacer(Modifier.width(7.dp))
            Txt(
                "用了 " + tools.size + " 个工具 · " + tools.take(3).joinToString("、") { it.label },
                size = 10.5f,
                color = p.muted,
                lineHeight = 14f,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            LineIcon(
                Glyph.ChevronDown,
                p.muted,
                10.dp,
                1.8f,
                modifier = Modifier.graphicsLayer { rotationZ = chevron },
            )
        }
        // 同框扩展：内容在同一个气泡里从摘要行向下长出来，收起时反向缩回，
        // 不是整块淡入/淡出，也不是另起一个面板。
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = .52f, stiffness = 300f),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = tween(160)),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = .78f, stiffness = 420f),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = tween(120)),
        ) {
            Column(Modifier.clipToBounds()) {
                Spacer(Modifier.height(2.dp))
                tools.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            Modifier
                                .padding(top = 5.dp)
                                .size(4.dp)
                                .background(if (t.ok) p.muted.copy(alpha = .55f) else ui.accent, CircleShape)
                        )
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(
                                t.label + if (t.args.isBlank()) "" else " · " + t.args,
                                size = 10f,
                                color = p.ink,
                                lineHeight = 14f,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Txt(
                                t.result.ifBlank { "没有返回内容" } + " · " + t.elapsedMs + " ms",
                                size = 9.5f,
                                color = p.muted,
                                lineHeight = 13f,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 深度思考的 reasoning，默认收起：回答上方一行「思考过程 · N 字」，点开才展开。
 * 只对本次会话里的回答有效（reasoning 不写历史），所以收起是默认状态。
 */
@Composable
private fun ThoughtBlock(id: String, reasoning: String) {
    val ui = LocalInk.current
    val p = ui.palette
    var open by remember(id) { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = spring(dampingRatio = .52f, stiffness = 300f),
        label = "thoughtChevron",
    )
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().bouncy(scale = .99f) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("思考过程 · ${reasoning.length} 字", size = 10f, color = p.muted)
            Spacer(Modifier.width(4.dp))
            LineIcon(
                Glyph.ChevronDown,
                p.muted,
                10.dp,
                1.8f,
                modifier = Modifier.graphicsLayer { rotationZ = chevron },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = .52f, stiffness = 300f),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(160)),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = .78f, stiffness = 420f),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(120)),
        ) {
            val shown = if (reasoning.length > 6_000) {
                reasoning.take(6_000) + "\n……（后面省略）"
            } else {
                reasoning
            }
            Txt(
                shown,
                size = 11f,
                color = p.muted.copy(alpha = .92f),
                lineHeight = 17f,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line.copy(alpha = .5f)))
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * 输入框上方的工具气泡：只有图标，没有文字。
 *
 * 展开/收起只有两件事：透明度 0↔1、整块在下方 8dp 与落点之间移动（收起反向）。
 * 宽度固定、5 个图标整排同时出现——之前「宽度从 5px 拉幕 + 每个图标错开 18ms
 * 滑入 14dp」三层一起演，读起来太夸张；删掉后玻璃只按最终尺寸栅格化一次，
 * 边缘也不会每帧重新采样。
 */
@Composable
private fun ToolsBubble(
    modifier: Modifier,
    open: Animatable<Float, AnimationVector1D>,
    deepThink: Boolean,
    webOn: Boolean,
    onFile: () -> Unit,
    onQuote: () -> Unit,
    onDeep: () -> Unit,
    onWeb: () -> Unit,
    onMemory: () -> Unit,
) {
    // 只在「从无到有」那一刻重组一次；之后透明度/位移在组合里读，
    // 但宽度从第一帧就是最终尺寸——玻璃只按最终尺寸栅格化一次，
    // 展开过程中不再重排，边缘也不会因为宽度变了又重采样一次。
    val visible by remember(open) { derivedStateOf { open.value > .001f } }
    if (!visible) return
    // 5 颗 42dp 图标 + 4 个 2dp 间隔 + 左右各 7dp 内边距。
    val bubbleWidth = 42.dp * 5f + 2.dp * 4f + 7.dp * 2f
    val t = open.value.coerceIn(0f, 1f)
    Box(
        modifier
            .width(bubbleWidth)
            .graphicsLayer {
                // 一次克制的出现：只有淡入和 8dp 上移，没有幕布、没有逐图标进位。
                alpha = t
                translationY = (1f - t) * 8.dp.toPx()
            }
    ) {
        GlassField(
            Modifier.fillMaxWidth(),
            shape = SmoothCorner(20.dp),
            shadowAlpha = .16f,
            kind = "button",
        ) {
            Row(
                Modifier.padding(7.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ToolIcon(Glyph.File, onClick = onFile)
                ToolIcon(Glyph.Link, onClick = onQuote)
                ToolIcon(Glyph.Sparkles, selected = deepThink, onClick = onDeep)
                ToolIcon(Glyph.Search, selected = webOn, onClick = onWeb)
                ToolIcon(Glyph.Book, onClick = onMemory)
            }
        }
    }
}

/** 气泡里的一颗图标按钮；跟随气泡一起出现，不再逐个错开滑入。 */
@Composable
private fun ToolIcon(
    glyph: Glyph,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    // 选中底从左侧长出：开关打开时图标自己先动一下，提示才接着落下。
    val fill = remember { Animatable(if (selected) 1f else 0f) }
    LaunchedEffect(selected) {
        if (ui.settings.motion == "none") {
            fill.snapTo(if (selected) 1f else 0f)
            return@LaunchedEffect
        }
        fill.animateTo(
            targetValue = if (selected) 1f else 0f,
            animationSpec = spring(
                dampingRatio = .62f,
                stiffness = 420f,
                visibilityThreshold = .001f,
            ),
        )
    }
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .bouncy(scale = .90f) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = fill.value
                    transformOrigin = TransformOrigin(0f, .5f)
                }
                .background(accentTint(p, ui.accent))
        )
        LineIcon(glyph, p.ink, 18.dp, 1.7f)
    }
}

private val FALLBACK_SUGGESTIONS = listOf(
    "这一章哪里最不像真的",
    "人物的动机站得住吗",
    "接下来三章该怎么走",
)

/**
 * 问书: the AI tab. One brain, three panes.
 *
 *  对话 — ask about the book; the pinned material is visible in one row;
 *  记忆 — what it remembers, one line per fact, editable on tap;
 *  设置 — service, key, budget, automatic memory.
 */
@Composable
fun AiScreen(
    store: AppStore,
    onOpenChapter: (String, String) -> Unit,
    onOpenBook: (String) -> Unit = {},
    onOpenNote: (String) -> Unit = {},
) {
    val ui = LocalInk.current
    val p = ui.palette
    val ctx = LocalContext.current
    val settings = store.settings

    val books = store.books
    var bookId by rememberSaveable { mutableStateOf(books.firstOrNull()?.id) }
    val book = books.firstOrNull { it.id == bookId } ?: books.firstOrNull()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var memo by remember(book?.id) {
        mutableStateOf(book?.let { MemoStore.load(ctx, it.id) } ?: BookMemo(""))
    }
    LaunchedEffect(book?.id, book?.chapters) {
        val b = book ?: return@LaunchedEffect
        val fresh = refreshLocalMemo(b, memo)
        if (fresh != memo) {
            memo = fresh
            MemoStore.save(ctx, fresh)
        }
    }
    val index = remember(book?.id, book?.chapters) {
        book?.let { BookIndex(BookText.passages(it)) }
    }

    if (book == null) {
        // 与书架空状态同一高度、同一排版：书架是「大标题 + 筛选行 + 空状态」，
        // 这里保留同样的大标题，再用上留白把图标顶对到同一条水平线。
        Box(Modifier.fillMaxSize()) {
            AmbientBackground()
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                ) {
                    Txt("问书", size = 34f, weight = FontWeight.SemiBold, letterSpacing = .4f)
                }
                Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 83.dp)) {
                        EmptyState(Glyph.Sparkles, "还没有书", "先在书架建一本书，问书才有内容可读。")
                    }
                }
            }
        }
        return
    }

    var question by remember(bookId) { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    // Which message currently has its action sheet open.
    var messageMenu by remember { mutableStateOf<String?>(null) }
    // The answer that just landed; its bubble plays the one-shot 同柔 pop.
    var popId by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorModel by remember { mutableStateOf<String?>(null) }
    // The transcript is one list of rows with stable ids; a reply is a row that
    // starts Running and flips to Settled in place. `streamText` is only the
    // typing target now — it is never a message of its own, which is what made
    // the same answer appear twice.
    val chat = remember { ChatSession() }
    var answerId by remember { mutableStateOf("") }
    var streamText by remember { mutableStateOf("") }
    var shown by remember { mutableIntStateOf(0) }
    val follow = remember { ScrollFollow() }
    var lastPrompt by remember { mutableStateOf<AiPrompt?>(null) }
    // 只剩危险操作（删除/覆盖）排在这里，等下面那个确认；安全写入走 applyDirectly。
    var pending by remember { mutableStateOf<List<AiPending>>(emptyList()) }
    // 2026-11-14 用户定：除删除/覆盖外，写操作直接落地，不再排队。
    // 落地时拍一张整库快照，给一步撤销用；只保留最近一轮。
    var undoSnapshot by remember { mutableStateOf<AppData?>(null) }
    var undoCount by remember { mutableIntStateOf(0) }
    // Deletions are queued like everything else, but the writer gets one more
    // question before they run: 「采纳」 alone is too small a click for an
    // irreversible action.
    var confirmDanger by remember { mutableStateOf(false) }
    var quotes by remember(bookId) { mutableStateOf<List<QuoteRef>>(emptyList()) }
    var quoteSheet by remember { mutableStateOf(false) }
    var bookPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoFact?>(null) }
    var suggestions by remember(bookId) { mutableStateOf<List<String>>(emptyList()) }
    // Author-level memory: preferences and habits that follow the writer across
    // books. Kept in its own file so "清空这本书" never wipes them.
    var globalFacts by remember { mutableStateOf(MemoStore.loadGlobal(ctx).facts) }
    // The conversation's scroll position must survive a visit to 记忆: keeping
    // it inside ChatPane meant the pane was rebuilt at item 0 on the way back
    // and the list visibly jumped to the bottom one frame later. It also lets
    // the pane skip its "scroll to newest" pass when nothing new arrived.
    val chatList = rememberLazyListState()
    LaunchedEffect(bookId) {
        chat.load(memo.turns)
        follow.reset()
        // Entering 问书 must land on the newest message. Without this the list
        // opened at whatever offset the previous book left and the first row
        // was cut in half at the top.
        repeat(4) {
            delay(70)
            runCatching { chatList.scrollBy(1_000_000f) }
        }
    }
    var chatSeen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Files live for this conversation only: switching the book drops them with
    // the rest of the thread. Their text is never written into moyu.json.
    var attachments by remember(bookId) { mutableStateOf<List<Attachment>>(emptyList()) }
    val attachPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { Attachments.read(ctx, uri) }
                    .onSuccess { a -> attachments = attachments.filterNot { it.name == a.name } + a }
                    .onFailure { ToastBus.show(it.message ?: "读不了这个文件") }
            }
        }
    }

    fun applyQueue(queue: List<AiPending>) {
        if (queue.isEmpty()) return
        var bookFacts = memo.facts
        var bookDirty = false
        var globalList = globalFacts
        var globalDirty = false
        // A restore replaces the whole store; saying "已采纳 1 处" afterwards
        // would understate what just happened.
        var restoredNow = false
        queue.forEach { p ->
            when (p.kind) {
                AiPendingKind.Note -> {
                    val group = p.groupName.trim().takeIf { it.isNotBlank() }
                        ?.let { name -> store.noteGroups.firstOrNull { it.name == name } }
                    store.addNote(Note(title = p.noteTitle, content = p.text, groupId = group?.id))
                }
                AiPendingKind.NoteEdit -> {
                    val o = JSONObject(p.payload.ifBlank { "{}" })
                    store.updateNote(p.noteId) { n ->
                        n.copy(
                            title = if (o.has("new_title")) o.optString("new_title").trim() else n.title,
                            content = if (o.has("body")) o.optString("body") else n.content,
                            pinned = if (o.has("pinned")) o.optBoolean("pinned") else n.pinned,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                }
                AiPendingKind.NoteOff -> store.deleteNote(p.noteId)
                AiPendingKind.Append -> store.updateBook(p.bookId.ifBlank { book.id }) { b ->
                    b.copy(
                        chapters = b.chapters.map { c ->
                            if (c.id != p.chapterId) c else c.copy(
                                content = if (c.content.isBlank()) p.text else c.content.trimEnd() + "\n\n" + p.text,
                                updatedAt = System.currentTimeMillis(),
                            )
                        }
                    )
                }
                AiPendingKind.Replace -> store.updateBook(p.bookId.ifBlank { book.id }) { b ->
                    b.copy(
                        chapters = b.chapters.map { c ->
                            if (c.id != p.chapterId) c else c.copy(
                                // Exact passage only: the tool already proved it
                                // occurs once, so this cannot hit the wrong line.
                                content = c.content.replaceFirst(p.findText, p.text),
                                updatedAt = System.currentTimeMillis(),
                            )
                        }
                    )
                }
                AiPendingKind.Book -> {
                    val o = JSONObject(p.payload.ifBlank { "{}" })
                    store.addBook(
                        Book(
                            title = p.bookTitle,
                            author = o.optString("author"),
                            genre = o.optString("genre").ifBlank { "小说" },
                            synopsis = o.optString("synopsis"),
                        )
                    )
                }
                AiPendingKind.BookEdit -> {
                    val o = JSONObject(p.payload.ifBlank { "{}" })
                    store.updateBook(p.bookId) { b ->
                        b.copy(
                            title = if (o.has("new_title")) o.optString("new_title").trim().ifBlank { b.title } else b.title,
                            author = if (o.has("author")) o.optString("author").trim() else b.author,
                            genre = if (o.has("genre")) o.optString("genre").trim().ifBlank { b.genre } else b.genre,
                            synopsis = if (o.has("synopsis")) o.optString("synopsis") else b.synopsis,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                }
                AiPendingKind.BookOff -> store.deleteBook(p.bookId)
                AiPendingKind.Chapter -> store.addChapter(p.bookId, Chapter(title = p.chapterTitle))
                AiPendingKind.ChapterEdit -> store.updateChapter(p.bookId, p.chapterId, title = p.text)
                AiPendingKind.ChapterOff -> store.deleteChapter(p.bookId, p.chapterId)
                AiPendingKind.BindNote -> {
                    val on = p.text == "1"
                    store.setNoteChapterBound(p.noteId, p.bookId, p.chapterId, on)
                }
                AiPendingKind.Group -> {
                    val kind = if (p.groupKind == "book") GroupKind.BOOK else GroupKind.NOTE
                    val order = (if (kind == GroupKind.BOOK) store.bookGroups else store.noteGroups)
                        .maxOfOrNull { it.order }?.plus(1) ?: 0
                    store.addGroup(kind, Group(name = p.groupName, color = groupColors.first(), order = order))
                }
                AiPendingKind.GroupEdit -> {
                    val kind = if (p.groupKind == "book") GroupKind.BOOK else GroupKind.NOTE
                    store.updateGroup(kind, p.groupId) { it.copy(name = p.groupName) }
                }
                AiPendingKind.GroupOff -> {
                    val kind = if (p.groupKind == "book") GroupKind.BOOK else GroupKind.NOTE
                    store.deleteGroup(kind, p.groupId)
                }
                AiPendingKind.GroupBind -> {
                    if (p.groupKind == "book") store.setBookGroup(p.bookId, p.groupId)
                    else store.setNoteGroup(p.noteId, p.groupId)
                }
                AiPendingKind.Todo -> store.updateNote(p.noteId) { n ->
                    n.copy(todos = n.todos + Todo(text = p.text), updatedAt = System.currentTimeMillis())
                }
                AiPendingKind.TodoDone -> store.updateNote(p.noteId) { n ->
                    n.copy(
                        todos = n.todos.map { if (it.id == p.text) it.copy(done = true) else it },
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                AiPendingKind.Style -> {
                    val o = JSONObject(p.payload.ifBlank { "{}" })
                    store.updateSettings { s ->
                        var next = s
                        if (o.has("font_size")) next = next.copy(fontScale = o.optDouble("font_size", s.fontScale.toDouble()).toFloat().coerceIn(.82f, 1.3f))
                        if (o.has("line_height")) next = next.copy(lineHeight = o.optDouble("line_height", s.lineHeight.toDouble()).toFloat().coerceIn(1.3f, 2.4f))
                        if (o.has("page_margin")) next = next.copy(pageMargin = o.optDouble("page_margin", s.pageMargin.toDouble()).toFloat().coerceIn(12f, 40f))
                        if (o.has("font")) next = next.copy(font = o.optString("font"))
                        if (o.has("theme")) next = next.copy(theme = o.optString("theme"))
                        if (o.has("paper_style")) next = next.copy(paperStyle = o.optString("paper_style"))
                        if (o.has("section_style")) next = next.copy(sectionStyle = o.optString("section_style"))
                        next
                    }
                }
                AiPendingKind.Memo -> {
                    val fact = MemoryOps.newFact(p.memoKind, p.memoName, p.memoDetail, "ai")
                    bookFacts = MemoryOps.merge(bookFacts, listOf(fact), settings.aiMemoryLimit)
                    bookDirty = true
                }
                AiPendingKind.GlobalMemo -> {
                    val fact = MemoryOps.newFact(p.memoKind, p.memoName, p.memoDetail, "ai")
                    globalList = MemoryOps.merge(globalList, listOf(fact), settings.aiMemoryLimit)
                    globalDirty = true
                }
                AiPendingKind.MemoOff -> {
                    // 停用，不是抹掉：记忆页里还看得见，能一键恢复。删除留给作者亲手做。
                    if (globalList.any { it.id == p.memoId }) {
                        globalList = globalList.map { if (it.id == p.memoId) it.copy(enabled = false) else it }
                        globalDirty = true
                    } else {
                        bookFacts = bookFacts.map { if (it.id == p.memoId) it.copy(enabled = false) else it }
                        bookDirty = true
                    }
                }
                AiPendingKind.Export -> {
                    val o = JSONObject(p.payload.ifBlank { "{}" })
                    val format = o.optString("format", "epub")
                    val targetBook = store.book(p.bookId)
                    val targetNote = store.notes.firstOrNull { it.id == p.noteId }
                    scope.launch {
                        runCatching {
                            if (targetBook != null) exportBook(ctx, targetBook, format, true)
                            else if (targetNote != null) exportNote(ctx, targetNote, format)
                            else error("这本书或笔记已经不在了")
                        }.onSuccess { ToastBus.show("已保存到 " + it.location) }
                            .onFailure { ToastBus.show("导出失败：" + (it.message ?: "未知错误")) }
                    }
                }
                AiPendingKind.Backup -> {
                    scope.launch {
                        runCatching { BackupOps.write(ctx, store.data) }
                            .onSuccess { ToastBus.show("备份已导出到 " + it.location) }
                            .onFailure { ToastBus.show("备份失败：" + (it.message ?: "未知错误")) }
                    }
                }
                AiPendingKind.Import -> {
                    val o = JSONObject(p.payload.ifBlank { "{}" })
                    val name = o.optString("name").ifBlank { "附件" }
                    val text = o.optString("text")
                    val base = name.substringBeforeLast('.', name).trim().ifBlank { "导入的书" }
                    if (text.isBlank()) {
                        ToastBus.show("附件内容不在了，重新选一次再导入")
                    } else if (o.optString("as") == "note") {
                        store.addNote(Note(title = base, content = text))
                    } else {
                        store.addBook(
                            Book(
                                title = base,
                                chapters = listOf(Chapter(title = "第一章", content = text)),
                            )
                        )
                    }
                }
                AiPendingKind.Reminder -> {
                    val o = JSONObject(p.payload.ifBlank { "{}" })
                    val note = store.notes.firstOrNull { it.id == p.noteId }
                    val todoId = o.optString("todoId")
                    val at = o.optLong("at", 0L)
                    val todo = note?.todos?.firstOrNull { it.id == todoId }
                    if (note == null || todo == null) {
                        ToastBus.show("这条待办已经不在了")
                    } else {
                        val next = note.copy(
                            todos = note.todos.map { if (it.id == todoId) it.copy(remindAt = at, notified = false) else it },
                            updatedAt = System.currentTimeMillis(),
                        )
                        store.updateNote(note.id) { next }
                        ReminderScheduler.schedule(ctx, next, next.todos.first { it.id == todoId })
                        ToastBus.show(
                            if (ReminderScheduler.canPost(ctx)) "提醒已设"
                            else "提醒已设，但通知权限没开"
                        )
                    }
                }
                AiPendingKind.Goal -> {
                    val o = JSONObject(p.payload.ifBlank { "{}" })
                    store.updateSettings {
                        it.copy(dailyGoal = o.optInt("words", it.dailyGoal).coerceIn(100, 100_000))
                    }
                }
                AiPendingKind.Restore -> {
                    val o = JSONObject(p.payload.ifBlank { "{}" })
                    val text = BackupOps.readText(ctx, o.optString("name"))
                    val parsed = text?.let { runCatching { BackupOps.parse(it) }.getOrNull() }
                    if (parsed == null) {
                        ToastBus.show("这份备份读不出来，当前数据没动")
                    } else {
                        store.replaceAll(parsed)
                        chat.load(emptyList())
                        val b = store.books.firstOrNull()
                        bookId = b?.id
                        // The restored file is the truth: anything the session
                        // still held (facts, turns, quotes) belongs to the data
                        // that was just replaced.
                        memo = b?.let { MemoStore.load(ctx, it.id) } ?: BookMemo("")
                        globalFacts = MemoStore.loadGlobal(ctx).facts
                        quotes = emptyList()
                        attachments = emptyList()
                        restoredNow = true
                        ToastBus.show("已恢复 " + parsed.books.size + " 本书 / " + parsed.notes.size + " 条笔记")
                    }
                }
            }
        }
        if (bookDirty) {
            val next = memo.copy(facts = bookFacts)
            memo = next
            MemoStore.save(ctx, next)
        }
        if (globalDirty) {
            globalFacts = globalList
            MemoStore.saveGlobal(ctx, MemoStore.loadGlobal(ctx).copy(facts = globalList))
        }
        if (!restoredNow) ToastBus.show("已写入 " + queue.size + " 处")
    }

    /** 直接落地：删除与整库恢复以外的写操作，一次回答结束就生效。 */
    fun applyDirectly(queue: List<AiPending>) {
        if (queue.isEmpty()) return
        val direct = ArrayList<AiPending>()
        val rest = ArrayList<AiPending>()
        for (p in queue) {
            if (p.danger || p.kind == AiPendingKind.Restore) rest += p else direct += p
        }
        if (direct.isNotEmpty()) {
            undoSnapshot = store.data
            undoCount = direct.size
            applyQueue(direct)
        }
        pending = rest
    }

    /** 一步撤销：整库回到本轮写入前那一刻。 */
    fun undoDirect() {
        val snap = undoSnapshot ?: return
        store.replaceAll(snap)
        undoSnapshot = null
        undoCount = 0
        ToastBus.show("已撤销")
    }

    /** 剩下的只有删除/覆盖类，落地前再问一次。 */
    fun requestApply() {
        if (pending.isEmpty()) return
        confirmDanger = true
    }

    fun persist(m: BookMemo) {
        memo = m
        MemoStore.save(ctx, m)
    }

    fun persistGlobal(list: List<MemoFact>) {
        globalFacts = list
        MemoStore.saveGlobal(ctx, MemoStore.loadGlobal(ctx).copy(facts = list))
    }

    fun send(text: String, echo: Boolean = true) {
        // 新一轮开始：上一轮的撤销口子作废（只保最近一轮）。
        undoCount = 0
        undoSnapshot = null
        pending = emptyList()
        // Picking a passage and pressing send IS the request; do not swallow it
        // just because the writer did not type a question on top of it.
        val q = text.trim().ifBlank {
            when {
                quotes.isNotEmpty() -> "看看我引用的这段"
                attachments.isNotEmpty() -> "看看我传的附件"
                else -> return
            }
        }
        if (sending || streamText.isNotEmpty()) return
        val key = AiKey.load(ctx)
        if (!settings.aiEnabled) {
            error = "先打开问书，并填好服务和 Key：本页「设置」"
            return
        }
        if (key.isNullOrBlank()) {
            error = "还没填 Key：本页「设置」→ Key"
            return
        }
        // An explicit quote does not need the retrieval index: expandQuotes reads
        // the chapter text directly. Requiring the index here silently swallowed
        // the whole request whenever the writer picked a passage before the
        // index finished building (the "quote sent, no answer" report).
        val idx = index ?: if (quotes.isEmpty()) {
            error = "原文索引还在建，稍等一下再发；或者先选一段引用，带引用可以直接发"
            return
        } else {
            BookIndex(emptyList())
        }
        // Both layers go into the prompt: the book's facts and the writer's own
        // habits, which apply to whatever book he opens next.
        // 深度思考是发送那一刻的状态：回答中途切开关不影响这一条。
        val deepThink = settings.aiDeepThink
        val prompt = AiPromptBuilder.build(
            book = book,
            memo = memo.copy(facts = memo.facts + globalFacts),
            index = idx,
            question = q,
            topK = settings.aiTopK,
            budget = settings.aiContextChars,
            quotes = quotes,
            quoteMax = settings.aiQuoteMaxChars,
            notes = store.notes,
            books = store.books,
            // Earlier settled turns: "那第二章呢" only means something if the
            // model has seen 第二章 come up.
            history = chat.toTurns(),
            attachments = attachments,
            deepThink = deepThink,
        )
        lastPrompt = prompt
        val kindOf = (memo.facts + globalFacts).associateBy { it.id }
        android.util.Log.d(
            "MOYU_AI",
            "send q=" + q.length + " quotes=" + quotes.size + " quoteChars=" + prompt.quoteChars +
                " user=" + prompt.user.length + " facts=" + prompt.usedFacts.size +
                " factsKinds=" + prompt.usedFacts.mapNotNull { kindOf[it]?.kind }.joinToString(",") +
                " history=" + prompt.historyChars +
                " deep=" + deepThink +
                " summaries=" + prompt.summaries.joinToString(",") { it.title } +
                " book=" + book.id + "/" + book.title +
                " tail=" + prompt.user.takeLast(60).replace("\n", " "),
        )
        // An empty chapter / blank note expands to nothing, and the model then
        // either answers "（没拿到回答）" or returns empty content. Say it here,
        // before a request is spent, so the writer knows what to fix.
        if (quotes.isNotEmpty() && prompt.quoteChars == 0) {
            error = "引用的这几段没有正文，换一段有字的章节或笔记"
            return
        }
        persist(
            memo.copy(
                facts = MemoryOps.touch(memo.facts, prompt.usedFacts),
                // Regenerating reuses the question already in the transcript, so
                // it must not add a second copy of that turn.
                turns = if (echo) (memo.turns + MemoTurn("user", q)).takeLast(40) else memo.turns,
            )
        )
        // One row per message, from now on: the writer's line is a settled row,
        // the answer is a running row that will be filled in place.
        if (echo) chat.addUser(q, emptyList())
        val id = chat.beginAnswer()
        answerId = id
        if (prompt.usedFacts.any { id -> globalFacts.any { it.id == id } }) {
            persistGlobal(MemoryOps.touch(globalFacts, prompt.usedFacts))
        }
        question = ""
        error = null
        sending = true
        // Re-arm follow BEFORE the rows land: the pane's size effect reads it
        // during recomposition, and a `launch` runs a frame later, so the first
        // pass used to see "not following" and skip the scroll entirely.
        follow.followNow()
        scope.launch {
            // The writer's own line and the new running row are not measured
            // yet; a few short passes land on the real bottom as they arrive.
            repeat(3) { pass ->
                delay(80)
                runCatching { chatList.scrollBy(1_000_000f) }

            }
            val messages = JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", prompt.system) })
                put(JSONObject().apply { put("role", "user"); put("content", prompt.user) })
            }
            // Tool events land on the running row as they happen, so the writer
            // can see what the assistant is doing instead of a bare "正在读".
            val traces = ArrayList<ToolTrace>()
            val toolEnv = AiToolEnv(
                context = ctx,
                attachments = attachments,
                webEnabled = settings.aiWeb,
                webMax = settings.aiWebMax,
                key = key,
                baseUrl = settings.aiBaseUrl,
                openBook = onOpenBook,
                openChapter = onOpenChapter,
                openNote = onOpenNote,
            )
            val queue = ArrayList<AiPending>()
            var last: AiReply = AiReply()
            var text = ""
            var rounds = 0
            // The model may ask to read a chapter before answering; up to eight
            // rounds is enough for real work and stops any runaway loop.
            // 1800: with history in the prompt the model has more to read and
            // more to say — at 1200 a good answer was cut mid-sentence
            // (`finish=length`, 768 chars, "…如果不是" and nothing after).
            // 深度思考要先把问题拆开、逐条核对，1800 token 留不出思考的余地；
            // 普通模式保持原样，短答手感不变。
            // 2026-11-14 用户定：按「看账 / 写账」两本账跑。
            //   第 1 轮：开思考（规划这一趟要干什么），4000；
            //   2–8 轮：只用来读材料/调工具，**关思考**、1500；
            //   写轮：关掉工具、开思考、6000，**不计入工具账**。
            // 之前 9 轮共用 30000 且全开思考，一次 4574 字推理就把首轮撑爆、
            // 读到第 8 轮又没粮写正文（真机日志 rounds=8 calls=2 text=0 tokens=32000）。
            var budget = 0
            var spentTokens = 0
            val toolCeiling = if (deepThink) 20_000 else 16_000
            val writeBudget = if (deepThink) 6_000 else 3_000
            // One blank-answer rescue per question, whatever round it happens on.
            var retriedBlank = false
            // With an explicit quote the chapter text is already in the prompt;
            // offering the read_chapter tools on top only makes a thinking model
            // spend its budget deciding whether to re-read, and the reply came
            // back empty (finish_reason=length, content=""). No quotes → tools
            // stay, because then they are how it finds the passage.
            val toolDefs = if (quotes.isEmpty() || attachments.isNotEmpty()) AiTools.DEFS else null
            // A model reading a long chapter asks for it segment by segment. On
            // "写完第三章" it spent all eight rounds on read_chapter and never got
            // a turn to write (`rounds=8 calls=1 text=0 finish=tool_calls`). The
            // ninth round is a closing one: only the write tools are offered,
            // and the model is told to stop reading and put the text down.
            val writeOnlyDefs = runCatching {
                val all = JSONArray(AiTools.DEFS)
                val out = JSONArray()
                val keep = setOf("append_to_chapter", "replace_in_chapter", "create_chapter")
                for (i in 0 until all.length()) {
                    val f = all.getJSONObject(i)
                    if (f.getJSONObject("function").optString("name") in keep) out.put(f)
                }
                if (out.length() == 0) null else out.toString()
            }.getOrNull()
            while (rounds < 9 && (spentTokens < toolCeiling || last.calls.isNotEmpty())) {
                rounds++
                val closing = rounds == 9 && toolDefs != null && last.calls.isNotEmpty()
                if (closing) {
                    messages.put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "content",
                                "材料够了。不要再调用读类工具；直接用 append_to_chapter 把正文写进对应的章，写完用一句话说明依据。",
                            )
                        }
                    )
                }
                // 第 1 轮开思考做规划；之后都是「取材料」，关掉思考省下的正是那 4574 字。
                budget = if (rounds == 1) (if (deepThink) 4_000 else 1800) else (if (deepThink) 1_500 else 1_200)
                spentTokens += budget
                val thinkOff = rounds > 1
                last = AiClient.chatTools(
                    baseUrl = settings.aiBaseUrl,
                    model = settings.aiModel,
                    key = key,
                    temperature = settings.aiTemperature,
                    messages = messages,
                    tools = if (closing) writeOnlyDefs else toolDefs,
                    maxTokens = budget,
                    readTimeoutMs = if (deepThink) 150_000 else 90_000,
                    disableThinking = thinkOff,
                )
                if (last.error != null) {
                    // Some endpoints/model names reject "tools" outright; a plain
                    // answer beats a red bubble, so retry once without them.
                    if (rounds == 1 && (last.error.contains("tool", true) || last.error.contains("function", true))) {
                        last = AiClient.chatTools(
                            baseUrl = settings.aiBaseUrl,
                            model = settings.aiModel,
                            key = key,
                            temperature = settings.aiTemperature,
                            messages = messages,
                            tools = null,
                            maxTokens = budget,
                            readTimeoutMs = if (deepThink) 150_000 else 90_000,
                        )
                    }
                    if (last.error != null) break
                }
                // Keep the model's own reasoning for the 「思考过程」 block. Only
                // in deep mode: normal answers stay one short bubble.
                if (deepThink) chat.setReasoning(id, last.reasoning)
                // A thinking model can spend the entire budget on reasoning and
                // return empty content with finish_reason=length. Measured: 5447
                // characters of reasoning on "write chapter one", which ate all
                // 1800 tokens and produced nothing — and because the retry below
                // only fired on round 1, a tool round followed by an empty answer
                // had no fallback at all. Retry every empty answer once, sized
                // from what the previous attempt actually spent on thinking.
                if (last.calls.isEmpty() && last.text.isBlank() && !retriedBlank) {
                    retriedBlank = true
                    budget = maxOf(if (deepThink) 8_000 else 4_000, last.reasoningChars * 3)
                    android.util.Log.d(
                        "MOYU_AI",
                        "empty answer, retry bigger: reason=" + last.finishReason +
                            " reasoning=" + last.reasoningChars + " budget=" + budget,
                    )
                    last = AiClient.chatTools(
                        baseUrl = settings.aiBaseUrl,
                        model = settings.aiModel,
                        key = key,
                        temperature = settings.aiTemperature,
                        messages = messages,
                        tools = toolDefs,
                        maxTokens = budget,
                        readTimeoutMs = if (deepThink) 150_000 else 90_000,
                    )
                    if (last.error != null) break
                }
                if (last.calls.isEmpty()) {
                    text = last.text
                    break
                }
                last.message?.let { messages.put(it) }
                for (call in last.calls) {
                    val started = System.currentTimeMillis()
                    val out = runCatching {
                        AiTools.run(
                            call.name,
                            JSONObject(call.args.ifBlank { "{}" }),
                            store,
                            book,
                            store.notes,
                            queue,
                            memo.facts,
                            globalFacts,
                            toolEnv,
                        )
                    }.getOrElse { "error: 工具 " + call.name + " 没跑通（" + (it.message ?: it.javaClass.simpleName) + "）。换个参数或换条路子，别把这句原样转给作者。" }
                    messages.put(
                        JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", call.id)
                            put("content", out)
                        }
                    )
                    if (settings.aiToolTrace) {
                        traces += ToolTrace(
                            id = call.id.ifBlank { "t" + traces.size },
                            name = call.name,
                            label = AiTools.label(call.name),
                            args = traceArgs(call.args),
                            result = out.replace(Regex("\\s+"), " ").trim().take(90),
                            ok = !out.startsWith("error:"),
                            elapsedMs = System.currentTimeMillis() - started,
                        )
                        chat.setTools(id, traces.toList())
                    }
                }
            }
            // 写轮（2026-11-14 用户定的结构）：
            // 只要这一趟用过工具（rounds > 1），或者正文是空的，就单独跑一次「写」：
            // 关工具、开思考、预算 6000，且**不计入工具账**。
            // 工具轮关掉思考后产出的是「该读哪章」这种判断，正文要在写轮里认真写。
            if (last.error == null && (text.isBlank() || rounds > 1)) {
                messages.put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            "材料都在上面了。不要调用任何工具，直接把最终结果写出来：" +
                                "该回答就完整回答，该写正文就完整写正文，不要复述过程、不要解释你查了什么。",
                        )
                    }
                )
                last = AiClient.chatTools(
                    baseUrl = settings.aiBaseUrl,
                    model = settings.aiModel,
                    key = key,
                    temperature = settings.aiTemperature,
                    messages = messages,
                    tools = null,
                    maxTokens = writeBudget,
                    readTimeoutMs = if (deepThink) 150_000 else 90_000,
                )
                if (last.error == null && last.text.isNotBlank()) text = last.text
                if (deepThink) chat.setReasoning(id, last.reasoning)
                android.util.Log.d(
                    "MOYU_AI",
                    "stage=write budget=" + writeBudget + " text=" + text.length +
                        " rounds=" + rounds + " finish=" + last.finishReason + " err=" + last.error,
                )
            }
            // 被截断（2026-11-14 用户报「输出被截到一半就不输出了」）：
            // finish=length 表示这一轮的 max_tokens 用完了，正文停在一半。
            // 以前的补丁只处理「正文为空」，写了一半的反而被当成完成。
            // 这里续写而不是重发：把已写的当上文，只让它接后半段。
            var cont = 0
            while (text.isNotBlank() && last.finishReason == "length" && cont < 2) {
                cont++
                messages.put(
                    JSONObject().apply {
                        put("role", "assistant")
                        put("content", text)
                    }
                )
                messages.put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            "上一段写到「" + text.takeLast(20) + "」就到长度上限断掉了。" +
                                "从断点直接接着往后写：不要重复已经写过的任何句子，不要重新开头，不要解释，不要加小标题。",
                        )
                    }
                )
                last = AiClient.chatTools(
                    baseUrl = settings.aiBaseUrl,
                    model = settings.aiModel,
                    key = key,
                    temperature = settings.aiTemperature,
                    messages = messages,
                    tools = null,
                    maxTokens = if (deepThink) 4_000 else 2_500,
                    readTimeoutMs = if (deepThink) 150_000 else 90_000,
                )
                if (last.error != null) break
                val more = last.text.trim()
                if (more.isEmpty()) break
                text = text.trimEnd() + more
                android.util.Log.d(
                    "MOYU_AI",
                    "continue after length: +" + more.length + " total=" + text.length +
                        " finish=" + last.finishReason,
                )
            }
            sending = false
            android.util.Log.d(
                "MOYU_AI",
                "rounds=" + rounds + " calls=" + last.calls.size + " text=" + text.length +
                    " err=" + last.error + " finish=" + last.finishReason +
                    " reasoning=" + last.reasoningChars + " tokens=" + spentTokens +
                    " deep=" + deepThink,
            )
            pending = queue
            // 除删除/覆盖外的写操作，这里就落地，不等「采纳」。
            applyDirectly(queue)
            if (last.error != null) {
                error = last.error
                errorModel = last.modelHint
                // The row stays where it is and turns into the failure, instead
                // of vanishing and dropping a separate error bubble underneath.
                chat.fail(id, last.error)
            } else {
                // The closing round may have queued the edit and said nothing.
                // That is a success, not "the model gave nothing": point at the
                // 采纳 row instead of asking the writer to send it again.
                if (text.isBlank() && last.text.isNotBlank()) text = last.text
                streamText = cleanReply(
                    text.ifBlank {
                        if (queue.isNotEmpty()) {
                            "这次有 " + queue.size + " 处删除或覆盖，等你在下面确认。"
                        } else {
                            "模型这次没给出内容（工具往返 " + rounds + " 轮）。再发一次，或换个说法。"
                        }
                    }
                )
            }
        }
    }

    // Starter questions come from the model, for what is pinned right now.
    LaunchedEffect(book.id, quotes, chat.messages.size, settings.aiEnabled, settings.aiModel) {
        if (!settings.aiEnabled || chat.messages.isNotEmpty()) return@LaunchedEffect
        val key = AiKey.load(ctx) ?: return@LaunchedEffect
        val (sys, usr) = AiSuggestions.request(book, memo, quotes)
        val r = AiClient.chat(
            baseUrl = settings.aiBaseUrl,
            model = settings.aiModel,
            key = key,
            temperature = .4f,
            system = sys,
            user = usr,
            maxTokens = 120,
        )
        if (r.ok) {
            val parsed = AiSuggestions.parse(r.text)
            if (parsed.isNotEmpty()) suggestions = parsed
        }
    }

    // Typewriter is gone on purpose: incremental growth made the bubble chase
    // the scroll and read as twitching. The answer now lands as one piece and
    // pops in with the same 同柔 spring the send flight uses.
    LaunchedEffect(streamText) {
        if (streamText.isEmpty()) return@LaunchedEffect
        val target = streamText
        val id = answerId
        chat.update(id, target)
        val refs = lastPrompt?.used?.map { it.chapter }?.distinct().orEmpty()
        chat.settle(id, refs)
        popId = id
        // One write, taken from the transcript itself: only settled rows reach
        // disk, so a restart cannot append the answer a second time.
        val next = memo.copy(turns = chat.toTurns().takeLast(40))
        persist(next)
        android.util.Log.d("MOYU_AI", "settle answer id=" + id + " len=" + target.length)
        streamText = ""

        // Automatic memory: one small request, 0–2 facts. Own coroutine, so a
        // recomposition or effect restart can neither lose it nor extend the
        // streaming bubble. Skipped when the model already maintained memory
        // through the tools this turn: running both wrote the same correction
        // twice ("赵乾是色盲" from the extractor + a tool-written version).
        val touchedMemory = chat.messages.firstOrNull { it.id == id }?.tools.orEmpty()
            .any { it.name == "save_memory" || it.name == "delete_memory" }
        if (settings.aiAutoMemory && !touchedMemory) {
            scope.launch {
                val key = AiKey.load(ctx)
                if (!key.isNullOrBlank()) {
                    val (sys, usr) = MemoryExtractor.request(next.turns, next.facts)
                    val r = AiClient.chat(
                        baseUrl = settings.aiBaseUrl,
                        model = settings.aiModel,
                        key = key,
                        temperature = .2f,
                        system = sys,
                        user = usr,
                        maxTokens = 320,
                    )
                    if (r.ok) {
                        val props = MemoryExtractor.parse(r.text)
                        if (props.isNotEmpty()) {
                            // 回顾 / 规划 both belong to the book; only the writer's
                            // own habits (偏好/习惯/…) follow them across books.
                            // update/drop name an id, so resolve the side by id first.
                            var bookFacts = next.facts
                            var globalNext = globalFacts
                            for (p in props) {
                                val inGlobal = p.id.isNotBlank() && globalNext.any { it.id == p.id }
                                val toGlobal = if (p.op != "new") inGlobal else MemoryOps.isGlobal(p.kind)
                                if (toGlobal) {
                                    globalNext = MemoryOps.applyProposals(globalNext, listOf(p), 60)
                                } else {
                                    bookFacts = MemoryOps.applyProposals(bookFacts, listOf(p), settings.aiMemoryLimit)
                                }
                            }
                            val added = (bookFacts.size - next.facts.size) + (globalNext.size - globalFacts.size)
                            val touched = added > 0 || props.any { it.op != "new" }
                            if (bookFacts != next.facts) persist(next.copy(facts = bookFacts))
                            if (globalNext != globalFacts) persistGlobal(globalNext)
                            if (touched) ToastBus.show(if (added > 0) "记住 $added 条" else "更新了一条记忆")
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("问书", size = 34f, weight = FontWeight.SemiBold, letterSpacing = .4f)
                    Spacer(Modifier.weight(1f))
                    // One conversation per book: the cover is the switch. The
                    // thread, the memory and the quotes all follow the book.
                    Row(
                        Modifier
                            .clip(SmoothCorner(13.dp))
                            .bouncy { bookPicker = true }
                            .padding(start = 5.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookCover(
                            book,
                            Modifier.width(23.dp).height(31.dp),
                            radius = 5f,
                            showText = false,
                        )
                        Spacer(Modifier.width(7.dp))
                        Txt(book.title, size = 12.5f, weight = FontWeight.Medium, maxLines = 1)
                        Spacer(Modifier.width(3.dp))
                        LineIcon(Glyph.ChevronDown, p.muted, 10.dp, 1.8f)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Segmented(
                    options = listOf("对话", "记忆"),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

            // 对话 / 记忆 slide by their relative position, the same gesture the
            // root tabs use: 0 → 1 travels left, 1 → 0 travels right.
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(260)) { it * dir } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(tween(260)) { -it * dir } + fadeOut(tween(160)))
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = "aiPane",
            ) { pane ->
            when (pane) {
                0 -> ChatPane(
                    book = book,
                    listState = chatList,
                    follow = follow,
                    isReentry = chatSeen,
                    onShown = { chatSeen = true },
                    notes = store.notes,
                    quotes = quotes,
                    onAddQuote = { quoteSheet = true },
                    onRemoveQuote = { quotes = quotes - it },
                    attachments = attachments,
                    onAddAttachment = {
                        attachPicker.launch(
                            arrayOf(
                                "text/plain",
                                "text/markdown",
                                "application/json",
                                "application/epub+zip",
                                "application/octet-stream",
                            )
                        )
                    },
                    onRemoveAttachment = { attachments = attachments - it },
                    toolTrace = settings.aiToolTrace,
                    deepThink = settings.aiDeepThink,
                    onToggleDeep = {
                        store.updateSettings { it.copy(aiDeepThink = !it.aiDeepThink) }
                    },
                    webOn = settings.aiWeb,
                    onToggleWeb = {
                        store.updateSettings { it.copy(aiWeb = !it.aiWeb) }
                    },
                    onOpenMemory = { tab = 1 },
                    messages = chat.messages,
                    question = question,
                    onQuestion = { question = it },
                    sending = sending,
                    error = error,
                    errorModel = errorModel,
                    onUseModel = { m ->
                        store.updateSettings { it.copy(aiModel = m) }
                        error = null
                        errorModel = null
                    },
                    onRetry = { error = null; errorModel = null },
                    pending = pending,
                    onApplyPending = { requestApply() },
                    onDropPending = { pending = emptyList() },
                    onUndoDirect = { undoDirect() },
                    undoCount = undoCount,
                    suggestions = suggestions.ifEmpty { FALLBACK_SUGGESTIONS },
                    onSend = { send(question) },
                    onSample = { send(it) },
                    onOpenChapter = onOpenChapter,
                    onLongPressMessage = { id -> messageMenu = id },
                    menuId = messageMenu,
                    onDismissMenu = { messageMenu = null },
                    menuActions = { id, close ->
                        val msg = chat.messages.firstOrNull { it.id == id }
                        if (msg != null) {
                            val clipboard = LocalClipboardManager.current
                            GlassField(
                                Modifier.width(300.dp),
                                shape = SmoothCorner(22.dp),
                                shadowAlpha = .20f,
                                kind = "button",
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MessageChip(Glyph.Share, "复制") {
                                        clipboard.setText(AnnotatedString(msg.text))
                                        close()
                                    }
                                    MessageChip(Glyph.Link, "引用") {
                                        // A transcript message joins the same quote
                                        // system the picker feeds, so it shows up as a
                                        // chip and rides along on the next send.
                                        if (quotes.none { it.id == msg.id }) {
                                            quotes = quotes + QuoteRef(
                                                type = "text",
                                                id = msg.id,
                                                label = "这段对话",
                                                bookId = book.id,
                                                text = msg.text,
                                            )
                                        }
                                        close()
                                    }
                                    MessageChip(Glyph.Note, "笔记") {
                                        close()
                                        val idx = chat.messages.indexOfFirst { it.id == msg.id }
                                        val upto = chat.messages
                                            .take(if (idx >= 0) idx + 1 else chat.messages.size)
                                        val picked = if (settings.noteContextCount <= 0) upto
                                        else upto.takeLast(settings.noteContextCount)
                                        val thread = picked.joinToString("\n") { m ->
                                            (if (m.role == "user") "作者：" else "AI：") + m.text
                                        }
                                        val key = AiKey.load(ctx)
                                        if (key.isNullOrBlank()) {
                                            store.addNote(Note(title = msg.text.trim().take(24), content = msg.text))
                                            ToastBus.show("还没有 Key，先原样存了一份")
                                        } else {
                                            ToastBus.show("正在整理…")
                                            scope.launch {
                                                val r = AiClient.chat(
                                                    settings.aiBaseUrl, settings.aiModel, key,
                                                    settings.aiTemperature,
                                                    "你在帮一位中文小说作者整理笔记。把给出的对话整理成一篇可复用的笔记：" +
                                                        "去掉寒暄、复读和废话，保留结论、设定、待办和问题。" +
                                                        "只输出：第一行「标题：…」，之后是笔记正文；不要解释你在做什么。",
                                                    thread,
                                                    maxTokens = settings.noteMaxTokens,
                                                    // 笔记是机械整理，不需要先想一遍；思考会把
                                                    // 预算吃光，最后 content 为空，反而落到
                                                    // 「整理没成功，先原样存了一份」。
                                                    disableThinking = true,
                                                )
                                                val body = r.text.trim()
                                                if (r.ok && body.isNotBlank()) {
                                                    val first = body.lineSequence().first().trim()
                                                    val title = first.removePrefix("标题：").removePrefix("标题:")
                                                        .trim().ifBlank { msg.text.trim().take(12) }
                                                    val rest = body.lineSequence().drop(1)
                                                        .joinToString("\n").trim().ifBlank { body }
                                                    store.addNote(Note(title = title.take(40), content = rest))
                                                    ToastBus.show("已存成笔记")
                                                } else {
                                                    store.addNote(Note(title = msg.text.trim().take(24), content = msg.text))
                                                    ToastBus.show("整理没成功，先原样存了一份")
                                                }
                                            }
                                        }
                                    }
                                    if (msg.role != "user") {
                                        MessageChip(Glyph.Sparkles, "重答") {
                                            close()
                                            // Replace the answer: drop it, then ask the
                                            // question that produced it again, without
                                            // echoing a second user bubble.
                                            val idx = chat.messages.indexOfFirst { it.id == msg.id }
                                            val prev = chat.messages.take(if (idx >= 0) idx else chat.messages.size)
                                                .lastOrNull { it.role == "user" }
                                            if (prev != null) {
                                                chat.drop(msg.id)
                                                persist(memo.copy(turns = chat.toTurns()))
                                                send(prev.text, echo = false)
                                            }
                                        }
                                    }
                                    MessageChip(Glyph.Trash, "删除", muted = true) {
                                        chat.drop(msg.id)
                                        persist(memo.copy(turns = chat.toTurns()))
                                        close()
                                    }
                                }
                            }
                        }
                    },
                    popId = popId,
                )
                else -> MemoryPane(
                    memo = memo,
                    global = globalFacts,
                    indexSize = index?.size ?: 0,
                    onClearChat = {
                        // Chat only; the memory stays. The old 清空 wiped both,
                        // which is why one button could not serve both jobs.
                        chat.load(emptyList())
                        persist(memo.copy(turns = emptyList()))
                    },
                    onClear = {
                        // Memory only; the transcript stays. Keep the file (it
                        // also holds turns) and just blank the memory fields.
                        persist(memo.copy(facts = emptyList(), chapters = emptyList()))
                    },
                    onEdit = { editing = it },
                    onDelete = { f ->
                        if (MemoryOps.isGlobal(f.kind)) persistGlobal(globalFacts.filterNot { it.id == f.id })
                        else persist(memo.copy(facts = memo.facts.filterNot { it.id == f.id }))
                    },
                )
            }
            }
        }
    }

    // The long-press menu is drawn inside ChatPane, anchored under the pressed
    // bubble; this screen only supplies the actions.

    if (quoteSheet) {
        QuoteSheet(
            book = book,
            books = books,
            notes = store.notes,
            noteGroups = store.data.noteGroups,
            chosen = quotes,
            onDone = { picked ->
                quotes = picked
                quoteSheet = false
            },
            onDismiss = { quoteSheet = false },
        )
    }

    if (bookPicker) {
        BookPickerSheet(
            books = books,
            currentId = book.id,
            onPick = { id ->
                bookId = id
                quotes = emptyList()
                bookPicker = false
            },
            onDismiss = { bookPicker = false },
        )
    }

    if (confirmDanger) {
        val restoring = pending.any { it.kind == AiPendingKind.Restore }
        ConfirmDialog(
            title = if (restoring) "真的要恢复？" else "真的要删？",
            message = pending.filter { it.danger }.joinToString("\n") { "· " + it.label } +
                if (restoring) "\n\n当前全部数据会被这份备份覆盖，无法撤销。" else "\n\n删除后无法恢复。",
            confirmLabel = if (restoring) "恢复" else "删除",
            destructive = true,
            onConfirm = {
                confirmDanger = false
                val danger = pending
                pending = emptyList()
                applyQueue(danger)
            },
            onDismiss = { confirmDanger = false },
        )
    }

    editing?.let { fact ->
        val isGlobal = MemoryOps.isGlobal(fact.kind)
        FactDialog(
            fact = fact,
            onSave = { name, detail ->
                if (isGlobal) {
                    persistGlobal(globalFacts.map { if (it.id == fact.id) it.copy(name = name, detail = detail) else it })
                } else {
                    persist(memo.copy(facts = memo.facts.map { if (it.id == fact.id) it.copy(name = name, detail = detail) else it }))
                }
                editing = null
            },
            onToggle = {
                if (isGlobal) {
                    persistGlobal(globalFacts.map { if (it.id == fact.id) it.copy(enabled = !it.enabled) else it })
                } else {
                    persist(memo.copy(facts = memo.facts.map { if (it.id == fact.id) it.copy(enabled = !it.enabled) else it }))
                }
                editing = null
            },
            onDelete = {
                if (isGlobal) persistGlobal(globalFacts.filterNot { it.id == fact.id })
                else persist(memo.copy(facts = memo.facts.filterNot { it.id == fact.id }))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** 长消息判定：气泡超过列表可视高度的这个比例就不做 1.02 缩放。 */
private const val LongBubbleFraction = .40f

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatPane(
    book: Book,
    listState: LazyListState,
    follow: ScrollFollow,
    /** True when 对话 is being composed again after a visit to 记忆. */
    isReentry: Boolean,
    onShown: () -> Unit,
    notes: List<Note>,
    quotes: List<QuoteRef>,
    onAddQuote: () -> Unit,
    onRemoveQuote: (QuoteRef) -> Unit,
    attachments: List<Attachment>,
    onAddAttachment: () -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    /** Show the per-step tool strip under an answer. */
    toolTrace: Boolean,
    /** 问书 · 深度思考：先拆问题、逐条核对再答，回答更长，思考过程可见。 */
    deepThink: Boolean,
    onToggleDeep: () -> Unit,
    /** 问书 · 联网搜索开关（沿用设置里的 aiWeb）。 */
    webOn: Boolean,
    onToggleWeb: () -> Unit,
    /** 跳到「记忆」页。 */
    onOpenMemory: () -> Unit,
    messages: List<ChatMsg>,
    question: String,
    onQuestion: (String) -> Unit,
    sending: Boolean,
    error: String?,
    errorModel: String?,
    onUseModel: (String) -> Unit,
    onRetry: () -> Unit,
    pending: List<AiPending>,
    onApplyPending: () -> Unit,
    onDropPending: () -> Unit,
    onUndoDirect: () -> Unit,
    undoCount: Int,
    suggestions: List<String>,
    onSend: () -> Unit,
    onSample: (String) -> Unit,
    onOpenChapter: (String, String) -> Unit,
    onLongPressMessage: (String) -> Unit = {},
    /** Row that owns the anchored menu; null when no menu is open. */
    menuId: String? = null,
    onDismissMenu: () -> Unit = {},
    /** The menu body, drawn in a popup anchored under that row. */
    menuActions: @Composable (String, () -> Unit) -> Unit = { _, _ -> },
    /** The answer that just landed and should play the one-shot 同柔 pop. */
    popId: String? = null,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val scope = rememberCoroutineScope()
    val chapterIdByTitle = book.chapters.associate { it.title to it.id }
    // One continuous gap under the floating row: 96 dp above the page bottom
    // when the keyboard is closed, 8 dp above the IME once it is up. The list's
    // bottom padding is derived from it, so the newest message rides up too.
    val density = LocalDensity.current
    val imePx = WindowInsets.ime.getBottom(density)
    // 106 instead of 96: at 96 the floating row sat right on the tab bar and read
    // as one merged block. The list's bottom padding is derived from this, so the
    // newest message rides up with it.
    val bottomGap = maxOf(106.dp, with(LocalDensity.current) { imePx.toDp() } + 8.dp)

    // 输入框左边的工具气泡：开合只存本次页面内存，不写 moyu.json。
    var toolsOpen by remember { mutableStateOf(false) }
    // 输入区（chip 行 + 输入行）的实测高度。工具泡的落点读它，不再写死 58dp：
    // 有引用/附件 chip 时整块变高，泡自动抬到 chip 上方；无 chip 时也只差 6dp。
    // 之前写死 58dp 只够 46dp 输入行，chip 行正好被压在泡底下。
    var inputBlockH by remember { mutableStateOf(0.dp) }
    // 长按强调的唯一时间源：菜单真正出现（arm delay 之后）才从 0 走到 1。
    // 气泡延展、其余消息压暗/后退三者读同一个值，所以开启和取消都同一帧起步。
    var menuOpen by remember { mutableStateOf(false) }
    val menuEmphasis = remember { Animatable(0f) }
    // 气泡/背景的锚点比菜单多活一段：菜单 380ms 就拆掉锚点，但弹簧还在收尾，
    // 若这里跟着清空，气泡会在最后一小段直接跳到 0。等动画自己停再清。
    var emphasisAnchor by remember { mutableStateOf<String?>(null) }
    var anchorId by remember { mutableStateOf<String?>(null) }
    var menuClosing by remember { mutableStateOf(false) }
    var retractJob by remember { mutableStateOf<Job?>(null) }
    var teardownJob by remember { mutableStateOf<Job?>(null) }
    fun closeMenu() {
        if (anchorId == null || menuClosing) return
        menuClosing = true
        menuOpen = false
        val closingId = anchorId
        retractJob?.cancel()
        teardownJob?.cancel()
        retractJob = scope.launch {
            menuEmphasis.animateTo(
                0f,
                spring(dampingRatio = .52f, stiffness = 300f, visibilityThreshold = .0002f),
            )
            menuEmphasis.snapTo(0f)
            if (emphasisAnchor == closingId) emphasisAnchor = null
        }
        teardownJob = scope.launch {
            delay(380)
            if (anchorId == closingId) {
                anchorId = null
                menuClosing = false
                onDismissMenu()
            }
        }
    }
    val toolsT = remember { Animatable(0f) }
    val plusTurn by animateFloatAsState(
        targetValue = if (toolsOpen) 45f else 0f,
        animationSpec = spring(dampingRatio = .62f, stiffness = 520f, visibilityThreshold = .1f),
        label = "plusTurn",
    )
    LaunchedEffect(toolsOpen) {
        if (ui.settings.motion == "none") {
            toolsT.snapTo(if (toolsOpen) 1f else 0f)
        } else {
            // 一次克制的出现：200ms 展开、150ms 收起，缓入缓出、没有过冲。
            // 之前是 spring(1/420) 叠在「宽度拉幕 + 逐图标进位」上，整段
            // 350–400ms 三层同时演，读起来太夸张。
            toolsT.animateTo(
                targetValue = if (toolsOpen) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (toolsOpen) 200 else 150,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    BackHandler(enabled = toolsOpen) { toolsOpen = false }

    // First composition of this pane marks it as seen; a later composition is a
    // return from 记忆, where the list must stay exactly where it was left. The
    // restored position is already right, so no scroll runs on entry: the
    // turn-count effect only fires when the count actually grows.
    LaunchedEffect(Unit) { onShown() }
    // Scroll policy, ported from the DSH chat view's `use-scroll-follow`:
    // intent and motion are separate. Only a reader gesture changes
    // `follow.following`; program scrolls (send, growth, keyboard) leave the
    // intent alone, so they can no longer cancel the follow they serve.
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(listState, dragging) {
        if (!dragging) return@LaunchedEffect
        snapshotFlow { !listState.canScrollForward }.collect { atBottom ->
            follow.readerMovedAtBottom(atBottom)
        }
    }
    // A fling that runs out of content is still "the reader went to the bottom".
    // Only a drag updated the intent, so after a flick the list reported itself
    // as not-following and the next message did not scroll into view.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canForward) ->
                if (!scrolling && !canForward) follow.followNow()
            }
    }
    // Keep the tail exactly at the floor. This used to run on withFrameNanos,
    // i.e. at the START of the next frame, so every typewriter tick showed the
    // new line under the input for one frame before it snapped back up — the
    // "twitch". Reading layoutInfo reacts after layout in the same frame, and
    // dispatching only the exact overflow is one correction, never a chase.
    LaunchedEffect(listState, dragging) {
        snapshotFlow { listState.layoutInfo }
            .collect { li ->
                // Never correct under the reader's finger: a correction while
                // dragging is what made the list feel glued to the bottom.
                if (dragging) return@collect
                if (!follow.following) return@collect
                val last = li.visibleItemsInfo.lastOrNull() ?: return@collect
                val floor = li.viewportEndOffset - li.afterContentPadding
                val overflow = (last.offset + last.size) - floor
                if (overflow > 0) listState.dispatchRawDelta(overflow.toFloat())
            }
    }

    // Ride the keyboard frame by frame, the same trick the editor body uses.
    // `bottomGap` is a plain composition value: a LaunchedEffect keyed only on
    // listState captured the FIRST one (96 dp) and never saw the keyboard rise,
    // so the newest bubble stayed put and the floating row covered it. The
    // updated-state holder is what makes the snapshot flow observe each frame.
    val gapPxState = rememberUpdatedState(with(density) { bottomGap.toPx() })
    var lastGapPx by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(listState) {
        snapshotFlow { gapPxState.value }.collect { px ->
            val prev = lastGapPx
            lastGapPx = px
            if (prev < 0f) return@collect
            val delta = px - prev

            // Rising keyboard: pull the newest bubble up with it. Falling: the
            // row moves down, the list must follow or a hole opens under it.
            // Program motion, so it never changes the follow intent.
            if (!follow.following) return@collect
            runCatching { listState.scrollBy(delta) }

        }
    }

    val listBackdrop = rememberLayerBackdrop()
    // ── send flight ─────────────────────────────────────────────────────────
    // The writer's line leaves the input field and lands where the message will
    // live. SharedTransitionLayout is not usable here: items inside a LazyColumn
    // are recycled, so the shared element would snap to the wrong row. Instead
    // the new row is laid out immediately but drawn invisible while one overlay
    // bubble travels from the measured input rect to the measured row rect.
    var paneOrigin by remember { mutableStateOf(Offset.Zero) }
    var paneSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var fieldRect by remember { mutableStateOf(Rect.Zero) }
    val rowRects = remember { mutableStateMapOf<String, Rect>() }
    // 整条消息（含引用 chip）的布局范围：背景后退时的焦点换算用。
    val itemRects = remember { mutableStateMapOf<String, Rect>() }
    // 长按按下的位置（气泡内 0..1）：气泡放大和背景后退都以它为焦点，
    // 长文本按住的那一行不会跑。
    val pressOrigins = remember { mutableStateMapOf<String, Offset>() }
    var flyId by remember { mutableStateOf<String?>(null) }
    var flyFrom by remember { mutableStateOf(Rect.Zero) }
    var flyTo by remember { mutableStateOf(Rect.Zero) }
    var flyText by remember { mutableStateOf("") }
    val flyT = remember { Animatable(0f) }
    var seenCount by remember { mutableStateOf(messages.size) }
    var lastFlownId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.size) {
        val grew = messages.size - seenCount
        seenCount = messages.size
        if (grew <= 0) return@LaunchedEffect
        // send() appends TWO rows at once (the writer's line + the running
        // answer), so this must not test for a growth of exactly one.
        val lastUser = messages.lastOrNull { it.role == "user" } ?: return@LaunchedEffect
        if (lastUser.id == lastFlownId) return@LaunchedEffect
        if (fieldRect.height <= 0f) return@LaunchedEffect
        lastFlownId = lastUser.id
        flyText = lastUser.text
        flyFrom = fieldRect
        flyId = lastUser.id
    }
    LaunchedEffect(flyId) {
        val id = flyId ?: return@LaunchedEffect
        flyT.snapTo(0f)
        // The row is laid out this frame or the next; anything longer means it is
        // not on screen (the reader scrolled away), so skip the flight entirely.
        val target = withTimeoutOrNull(300) {
            snapshotFlow { rowRects[id] ?: Rect.Zero }.first { it.height > 0f }
        }
        if (target == null) {
            flyId = null
            return@LaunchedEffect
        }
        flyTo = target
        // Low damping on purpose: the bubble overshoots past its slot and settles
        // back, which is the "wobble" that reads as weight. Sharing MOYU_DAMPING
        // (1.0, no overshoot) made the landing feel dead.
        flyT.animateTo(1f, spring(dampingRatio = .52f, stiffness = 300f))
        flyId = null
    }
    // 1型玻璃 needs a layer it does NOT belong to: the list records itself and
    // the three controls below sit outside that layer, projecting it into their
    // own surface. Sampling a layer from inside it is the self-reference that
    // crashed the render thread earlier.
    // One extra line of tail room while an answer is streaming. The typewriter
    // adds a line at a time; without slack the new line lands under the input
    // for a frame before the scroll corrects it, which reads as twitching.
    val tailRoom = if (sending || messages.any { it.status == ChatStatus.Running }) 22.dp else 0.dp
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                paneOrigin = it.boundsInRoot().topLeft
                paneSize = it.size
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(listBackdrop)
                // 景深：整块对话围绕按下那条的中心缩 3%，读作一起后退；
                // 被按下的那一条在自己的 item 层里反向缩回，保持原尺寸。
                // 没有模糊，多长的消息都不会被单条图层尺寸吃掉。
                .graphicsLayer {
                    val pressed = emphasisAnchor?.let { rowRects[it] }
                    val press = emphasisAnchor?.let { pressOrigins[it] }
                    val focus = if (pressed != null) {
                        if (press != null) {
                            Offset(
                                pressed.left + pressed.width * press.x,
                                pressed.top + pressed.height * press.y,
                            )
                        } else {
                            pressed.center
                        }
                    } else null
                    if (focus != null && paneSize.width > 0 && paneSize.height > 0) {
                        transformOrigin = TransformOrigin(
                            (focus.x - paneOrigin.x) / paneSize.width,
                            (focus.y - paneOrigin.y) / paneSize.height,
                        )
                    }
                    val e = if (pressed != null) menuEmphasis.value else 0f
                    val s = 1f - .03f * e
                    scaleX = s
                    scaleY = s
                },
            // Room for the chips + input row, which now float over the list.
            // The list must end above the floating input row, and that row moves
            // with the keyboard: a fixed 176 dp left the newest message hidden
            // behind the field once the IME was up.
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                // bottomGap is where the input row STARTS; the row itself is
                // ~46dp tall, so the list needs bottomGap + rowHeight to clear
                // it. 12dp put the last line under the glass; 62dp left a hole.
                bottom = bottomGap + 54.dp + tailRoom +
                    if (quotes.isEmpty() && attachments.isEmpty()) 0.dp else 38.dp,
            ),
            // A short conversation leaves the rows stuck to the TOP with a hole
            // under them, and then no scroll can fix it: the content is shorter
            // than the viewport, so `scrollBy` has nowhere to go. Aligning the
            // arrangement to the bottom makes a short transcript hug the input
            // row, which is what "new message should be at the bottom" means.
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
        ) {
            if (messages.isEmpty() && !sending) {
                item {
                    Txt(
                        "可以这样问",
                        size = 10.5f,
                        color = p.muted,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                items(suggestions) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(SmoothCorner(14.dp))
                            .bouncy { onSample(s) }
                            .padding(vertical = 11.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Txt("·", size = 13f, color = p.muted, modifier = Modifier.padding(end = 8.dp))
                        Txt(s, size = 13.5f, color = p.ink, lineHeight = 21f)
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                val mine = msg.role == "user"
                val running = msg.status == ChatStatus.Running
                // 长按强调由 ChatPane 的 menuEmphasis 统一驱动：气泡延展、
                // 其余消息压暗、整块列表后退三者读同一个值，所以开启和取消
                // 都在同一帧起步。这里只留气泡自身的落地 pop。
                // 同柔 pop: the whole answer lands in one piece and the bubble
                // springs in with the same low-damping squash the send flight
                // uses. Only the row that just landed plays it.
                val pop = remember { Animatable(1f) }
                // 长按不再走 bouncy：按下时气泡不能先缩一下。只有菜单真的
                // 打开之后，menuEmphasis 才让它放大弹一下。
                val bubbleInteraction = remember { MutableInteractionSource() }
                LaunchedEffect(popId) {
                    if (msg.id == popId) {
                        pop.snapTo(0f)
                        pop.animateTo(1f, spring(dampingRatio = .52f, stiffness = 300f))
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { itemRects[msg.id] = it.boundsInRoot() }
                        .graphicsLayer {
                            val pressedId = emphasisAnchor
                            if (pressedId == msg.id) {
                                // 列表整体已经围绕按下气泡的中心缩了 3%；
                                // 被按下的这一条按同一屏幕焦点反向缩回去，
                                // 所以它保持原尺寸，只由气泡自己延展 8dp。
                                val pressed = rowRects[msg.id]
                                val item = itemRects[msg.id]
                                val press = pressOrigins[msg.id]
                                if (pressed != null && item != null &&
                                    item.width > 0f && item.height > 0f
                                ) {
                                    val focus = if (press != null) {
                                        Offset(
                                            pressed.left + pressed.width * press.x,
                                            pressed.top + pressed.height * press.y,
                                        )
                                    } else {
                                        pressed.center
                                    }
                                    transformOrigin = TransformOrigin(
                                        (focus.x - item.left) / item.width,
                                        (focus.y - item.top) / item.height,
                                    )
                                }
                                val s = 1f - .03f * menuEmphasis.value
                                if (s > 0f) {
                                    scaleX = 1f / s
                                    scaleY = 1f / s
                                }
                            } else {
                                // 其余消息只压暗，位置由列表父层统一后退。
                                alpha = 1f - .55f * menuEmphasis.value
                            }
                        },
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                ) {
                    // References sit ABOVE the bubble: a long answer pushed the
                    // chips to the very bottom of the viewport and the list
                    // clipped them in half.
                    if (!mine && msg.refs.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            msg.refs.forEach { title ->
                                val id = chapterIdByTitle[title]
                                if (id != null) {
                                    GlassChip(label = title, selected = false, onClick = { onOpenChapter(book.id, id) })
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    ) {
                        val pt = pop.value
                        val psy = 1f - .10f * (1f - pt) * kotlin.math.sin(pt * Math.PI).toFloat()
                        val pgrow = .92f + .08f * pt
                        Box(
                            Modifier
                                .fillMaxWidth(.88f)
                                // No height animation at all. A spring here lags
                                // the text, clips the newest line and reads as the
                                // box twitching; the height now tracks the text
                                // exactly, frame by frame.
                                // Measured on the BUBBLE, not on the row: the row is
                                // full width, so its rect would end the flight at the
                                // wrong place. Hidden while its own copy is airborne.
                                // 量在气泡自己的 zoom 之前：rowRects 是布局矩形，
                                // 菜单按 2% 的视觉放大算偏移时不能再量一次。
                                .onGloballyPositioned { rowRects[msg.id] = it.boundsInRoot() }
                                .graphicsLayer {
                                    // 被按下的那条：短消息固定 1.02，原点在手指
                                    // 按住的位置；长消息不缩放——长气泡的顶边
                                    // 离手指太远，2% 会把第一行推出可视区，
                                    // 取消时还要再弹一次。长文本只靠背景后退
                                    // 和压暗表达长按。
                                    val pressedId = emphasisAnchor
                                    val e = if (msg.id == pressedId) menuEmphasis.value else 0f
                                    val long = (rowRects[msg.id]?.height ?: 0f) >
                                        paneSize.height * LongBubbleFraction
                                    val zoom = if (e > 0f && !long) 1f + .02f * e else 1f
                                    val press = pressOrigins[msg.id]
                                    transformOrigin =
                                        if (e > 0f && !long && press != null) {
                                            TransformOrigin(press.x, press.y)
                                        } else {
                                            TransformOrigin(.5f, .5f)
                                        }
                                    scaleY = psy * pgrow * zoom
                                    scaleX = (2f - psy) * pgrow * zoom
                                    alpha = pt.coerceIn(0f, 1f)
                                }
                                .alpha(if (msg.id == flyId) 0f else 1f)
                                // 只记录按下点，不消费事件；长按仍由下面的
                                // combinedClickable 负责。比例坐标给 1.02 放大
                                // 和背景后退当同一个焦点。
                                .pointerInput(msg.id) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        if (size.width > 0 && size.height > 0) {
                                            pressOrigins[msg.id] = Offset(
                                                (down.position.x / size.width.toFloat())
                                                    .coerceIn(0f, 1f),
                                                (down.position.y / size.height.toFloat())
                                                    .coerceIn(0f, 1f),
                                            )
                                        }
                                        waitForUpOrCancellation()
                                    }
                                }
                                // Deleting is offered per row; a running row is
                                // exempt because its text is still arriving.
                                .then(
                                    if (!running) {
                                        Modifier.combinedClickable(
                                            interactionSource = bubbleInteraction,
                                            indication = null,
                                            onClick = {},
                                            onLongClick = { onLongPressMessage(msg.id) },
                                        )
                                    } else Modifier
                                ),
                        ) {
                            // The pop lives on this outer Box, not on MoyuSurface:
                            // the paper fill, its rim and the text must share one
                            // layer, otherwise the border samples a different
                            // frame than the text and reads as "only the text
                            // bounces".
                            MoyuSurface(
                                Modifier.fillMaxWidth(),
                                radius = 20f,
                                flat = true,
                                tone = if (mine) dockTone(ui) else null,
                            ) {
                            if (running && msg.text.isEmpty()) {
                                Column {
                                    Row(
                                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TypingDots()
                                        Spacer(Modifier.width(9.dp))
                                        Txt("正在读", size = 13f, color = p.muted)
                                    }
                                    if (toolTrace && msg.tools.isNotEmpty()) {
                                        ToolTraceStrip(msg.tools, ui, p, msg.id)
                                    }
                                }
                            } else {
                                Column {
                                    if (!mine && msg.reasoning.isNotBlank()) {
                                        ThoughtBlock(msg.id, msg.reasoning)
                                    }
                                    Txt(
                                        msg.text,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                        size = 13.5f,
                                        lineHeight = 21f,
                                    )
                                    if (toolTrace && msg.tools.isNotEmpty()) {
                                        ToolTraceStrip(msg.tools, ui, p, msg.id)
                                    }
                                    // The confirm strip lives INSIDE the bubble
                                    // that asked for the change: it belongs to
                                    // that answer, and a row floating above the
                                    // input read as a second input field.
                                    if (!running && pending.isNotEmpty() &&
                                        msg.id == messages.lastOrNull { it.role != "user" }?.id
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 14.dp, end = 10.dp, bottom = 10.dp)
                                                .height(1.dp)
                                                .background(p.line.copy(alpha = .5f))
                                        )
                                        // Rewrites get a before/after block: the
                                        // writer is approving a change to prose he
                                        // already wrote, not just "a note".
                                        val edits = pending.filter { it.kind == AiPendingKind.Replace }
                                        if (edits.isNotEmpty()) {
                                            Column(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 14.dp, end = 10.dp, top = 10.dp),
                                            ) {
                                                edits.forEachIndexed { i, e ->
                                                    if (i > 0) Spacer(Modifier.height(8.dp))
                                                    Txt(
                                                        "原文：" + e.findText,
                                                        size = 11f,
                                                        color = p.muted,
                                                        lineHeight = 15f,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Spacer(Modifier.height(3.dp))
                                                    Txt(
                                                        "改后：" + e.text,
                                                        size = 11.5f,
                                                        color = p.ink,
                                                        lineHeight = 16f,
                                                        maxLines = 3,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Txt(
                                                pending.joinToString("；") { it.label },
                                                size = 11f,
                                                color = p.muted,
                                                lineHeight = 15f,
                                                maxLines = 3,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Box(
                                                Modifier
                                                    .bouncy { onApplyPending() }
                                                    .background(p.ink, SmoothCorner(999.dp))
                                                    .padding(horizontal = 13.dp, vertical = 6.dp),
                                            ) {
                                                Txt("确认", size = 11.5f, color = p.paper, weight = FontWeight.SemiBold)
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            // No outline: a 1 dp border on a 6 dp
                                            // radius pill reads as a stray ring.
                                            // The tint alone carries the shape.
                                            Box(
                                                Modifier
                                                    .bouncy { onDropPending() }
                                                    .background(
                                                        lerp(p.paper, ui.accent, .20f),
                                                        SmoothCorner(999.dp),
                                                    )
                                                    .padding(horizontal = 13.dp, vertical = 6.dp),
                                            ) {
                                                Txt("算了", size = 11.5f, color = p.ink)
                                            }
                                        }
                                    }
                                    // 直接写入的那一条：改完就在这条回答里留一个撤销口子。
                                    if (!running && pending.isEmpty() && undoCount > 0 &&
                                        msg.id == messages.lastOrNull { it.role != "user" }?.id
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 14.dp, end = 10.dp, bottom = 10.dp)
                                                .height(1.dp)
                                                .background(p.line.copy(alpha = .5f))
                                        )
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Txt(
                                                "已写入 " + undoCount + " 处",
                                                size = 11f,
                                                color = p.muted,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Box(
                                                Modifier
                                                    .bouncy { onUndoDirect() }
                                                    .background(p.ink, SmoothCorner(999.dp))
                                                    .padding(horizontal = 13.dp, vertical = 6.dp),
                                            ) {
                                                Txt("撤销", size = 11.5f, color = p.paper, weight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                    if (msg.status == ChatStatus.Interrupted && msg.error != null) {
                        Txt(
                            msg.error,
                            size = 11.5f,
                            color = p.muted,
                            lineHeight = 17f,
                            modifier = Modifier.padding(start = 4.dp, top = 5.dp),
                        )
                    }
                }
            }
            if (sending && messages.none { it.status == ChatStatus.Running }) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        MoyuSurface(Modifier.fillMaxWidth(.6f), radius = 20f, flat = true) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TypingDots()
                                Spacer(Modifier.width(9.dp))
                                Txt("正在读", size = 13f, color = p.muted)
                            }
                        }
                    }
                }
            }
            if (error != null) {
                item {
                    MoyuSurface(Modifier.fillMaxWidth(), radius = 18f, flat = true, tone = dockTone(ui)) {
                        Column(Modifier.padding(14.dp)) {
                            Txt(error, size = 12.5f, color = p.ink, lineHeight = 19f)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // The provider usually names the models it does
                                // accept; hand that back as one tap.
                                if (errorModel != null) {
                                    GlassChip(
                                        label = "改用 $errorModel",
                                        selected = true,
                                        onClick = { onUseModel(errorModel) },
                                    )
                                }
                                GlassChip(label = "知道了", selected = false, onClick = onRetry)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(6.dp)) }
        }

        // 工具气泡：点空白处收起；气泡本身在列表之上，玻璃采样同一份列表。
        if (toolsOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { toolsOpen = false } }
            )
        }
        CompositionLocalProvider(LocalGlassBackdrop provides listBackdrop) {
            ToolsBubble(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // 落点 = 输入区实测高度 + 12dp，不再写死 58dp：chip 行出现的
                    // 那一帧起，泡就跟着抬到它上方，不再压住 chip。
                    // 52dp 是没有 chip 时的最小高度（6dp 上间距 + 46dp 输入行），
                    // 首帧还没量到时用它兜底。
                    .padding(
                        start = 20.dp,
                        bottom = bottomGap + maxOf(inputBlockH, 52.dp) + 12.dp,
                    ),
                open = toolsT,
                deepThink = deepThink,
                webOn = webOn,
                onFile = { toolsOpen = false; onAddAttachment() },
                onQuote = { toolsOpen = false; onAddQuote() },
                onDeep = {
                    onToggleDeep()
                    ToastBus.show(
                        if (deepThink) "深度思考已关闭" else "深度思考已开启",
                        glyph = Glyph.Sparkles,
                    )
                },
                onWeb = {
                    onToggleWeb()
                    ToastBus.show(
                        if (webOn) "联网搜索已关闭" else "联网搜索已开启",
                        glyph = Glyph.Search,
                    )
                },
                onMemory = { toolsOpen = false; onOpenMemory() },
            )
        }

        // The list scrolls UNDER this block, so the three controls below can
        // project it through real 1型玻璃.
        //
        // The gap under the row is ONE continuous value, not a boolean switch:
        // 96 dp above the page bottom while the keyboard is closed (clear of the
        // tab bar), 8 dp above the IME once it is up. The old code flipped the
        // row's own padding from 96 dp to 8 dp the instant `isImeVisible`
        // turned true — before the keyboard had moved a pixel — so the bar
        // jumped ~250 px down and then rode the keyboard back up. Reported as
        // "returning to 问书 makes the input bar sink once". max(closed,
        // ime + open) is continuous: the row holds still while the keyboard
        // rises to meet it, then travels with it.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = bottomGap)
                // 量的是 padding 内侧的内容高度（chip 行 + 输入行），
                // bottomGap 之外的落点由它换算，工具泡因此永远贴在整块输入区上方。
                .onGloballyPositioned { inputBlockH = with(density) { it.size.height.toDp() } }
        ) {
            if (pending.isNotEmpty()) {

            }
        CompositionLocalProvider(LocalGlassBackdrop provides listBackdrop) {
        // The reader scrolled away from the tail: offer the way back instead of
        // yanking the list. This row only exists while follow is off.
        if (!follow.following) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                GlassChip(
                    label = "回到最新",
                    selected = false,
                    onClick = {
                        follow.followNow()
                        scope.launch { runCatching { listState.scrollBy(1_000_000f) } }
                    },
                )
            }
        }
        // One row: what is pinned (if anything) and the way to change it. No
        // status line, no character-count label — that lived in two more chips
        // and read as clutter.
        if (quotes.isNotEmpty() || attachments.isNotEmpty()) {
            // LazyRow + animateItem: a removed chip fades out while the others
            // slide closed. The old enter-only AppearingChip had no exit at all;
            // fading it in place would have let the row snap shut first.
            val quoteRow = rememberLazyListState()
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                state = quoteRow,
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(quotes, key = { it.type + it.id }) { q ->
                    Box(
                        Modifier.animateItem(
                            fadeInSpec = tween(160),
                            placementSpec = spring(stiffness = 620f, dampingRatio = MOYU_DAMPING),
                            fadeOutSpec = tween(120),
                        )
                    ) {
                        GlassField(shape = CircleShape, shadowAlpha = .10f, kind = "button") {
                            Row(
                                Modifier.bouncy { onRemoveQuote(q) }.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Txt(q.label, size = 11.5f, color = p.ink)
                                Spacer(Modifier.width(6.dp))
                                LineIcon(Glyph.Close, p.muted, 10.dp, 1.9f)
                            }
                        }
                    }
                }
                items(attachments, key = { "att-" + it.name }) { a ->
                    Box(
                        Modifier.animateItem(
                            fadeInSpec = tween(160),
                            placementSpec = spring(stiffness = 620f, dampingRatio = MOYU_DAMPING),
                            fadeOutSpec = tween(120),
                        )
                    ) {
                        GlassField(shape = CircleShape, shadowAlpha = .10f, kind = "button") {
                            Row(
                                Modifier.bouncy { onRemoveAttachment(a) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LineIcon(Glyph.File, p.muted, 11.dp, 1.7f)
                                Spacer(Modifier.width(6.dp))
                                Txt(a.name.take(18), size = 11.5f, color = p.ink)
                                Spacer(Modifier.width(6.dp))
                                LineIcon(Glyph.Close, p.muted, 10.dp, 1.9f)
                            }
                        }
                    }
                }
            }
        }

        // 1型玻璃 on the input and the send pill. They sit OUTSIDE the recorded
        // list layer, so the material projects the scrolling page behind them.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            GlassField(
                Modifier.size(46.dp).bouncy { toolsOpen = !toolsOpen },
                shape = CircleShape,
                shadowAlpha = .14f,
                kind = "button",
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LineIcon(
                        Glyph.Plus,
                        p.ink,
                        19.dp,
                        1.7f,
                        modifier = Modifier.graphicsLayer { rotationZ = plusTurn },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            GlassField(
                Modifier
                    .weight(1f)
                    .onGloballyPositioned { fieldRect = it.boundsInRoot() }
                    // 点输入框也算「点气泡外」：顺手收起工具气泡。
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            toolsOpen = false
                            waitForUpOrCancellation()
                        }
                    },
                shape = SmoothCorner(22.dp),
                shadowAlpha = .14f,
                veil = .44f,
            ) {
                InkField(
                    value = question,
                    onValueChange = onQuestion,
                    placeholder = "问这本书的任何事",
                    singleLine = false,
                    maxLines = 4,
                    minHeight = 48.dp,
                    textSize = 14f,
                    surface = false,
                )
            }
            Spacer(Modifier.width(10.dp))
            val canSend = (question.isNotBlank() || quotes.isNotEmpty() || attachments.isNotEmpty()) && !sending
            // A ready arrow sits a touch larger than an idle one; the spring
            // carries the change instead of a colour flip alone.
            val sendScale by animateFloatAsState(
                targetValue = if (canSend) 1f else .9f,
                animationSpec = spring(stiffness = 640f, dampingRatio = MOYU_DAMPING, visibilityThreshold = .001f),
                visibilityThreshold = .001f,
                label = "sendScale",
            )
            GlassField(
                Modifier.bouncy(canSend) { if (canSend) onSend() },
                shape = CircleShape,
                shadowAlpha = if (canSend) .18f else .10f,
                kind = "button",
            ) {
                Box(
                    Modifier
                        .padding(13.dp)
                        .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                ) {
                    LineIcon(
                        Glyph.ArrowUp,
                        if (canSend) p.ink else p.muted.copy(alpha = .55f),
                        19.dp,
                    )
                }
            }
        }
        }
        }

        // The travelling bubble. It is drawn with the SAME composables as the real
        // row — same MoyuSurface, same padding, same text metrics — and starts life
        // scaled down onto the input rect. At t = 1 it sits exactly on top of the
        // real bubble, so handing over the frame is seamless: no cross-fade, no
        // jump. The squash is a function of t rather than its own spring, which
        // guarantees it is back to 1.0 on the landing frame.
        val flying = flyId
        if (flying != null && flyFrom.height > 0f) {
            val t = flyT.value
            // Live geometry, not the snapshot taken at send: hiding the keyboard
            // slides the input row and the whole list, and the old code kept
            // flying to the old coordinates, so the bubble hung over the answer
            // ("脱离位置"). Reading the field and the target row every frame
            // keeps it glued to the real thing for the whole flight.
            val from = if (fieldRect.height > 0f) fieldRect else flyFrom
            val to = rowRects[flying]?.takeIf { it.height > 0f } ?: flyTo
            val fx = lerp(from.left, to.left, t) - paneOrigin.x
            val fy = lerp(from.top, to.top, t) - paneOrigin.y
            val fw = lerp(from.width, to.width, t)
            val fh = lerp(from.height, to.height, t)
            // 0 at both ends, -10% in the middle: a squeeze while it is in the air.
            val sy = 1f - .10f * (1f - t) * kotlin.math.sin(t * Math.PI).toFloat()
            Box(
                Modifier
                    .offset { IntOffset(fx.roundToInt(), fy.roundToInt()) }
                    .size(with(density) { fw.toDp() }, with(density) { fh.toDp() })
                    .graphicsLayer {
                        scaleY = sy
                        scaleX = 2f - sy
                    },
            ) {
                MoyuSurface(Modifier.fillMaxSize(), radius = 20f, flat = true, tone = dockTone(ui)) {
                    Txt(
                        flyText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        size = 13.5f,
                        lineHeight = 21f,
                    )
                }
            }
        }
        // The action list follows the pressed bubble instead of rising from the
        // screen edge. It flips above the bubble when there is no room below.
        //
        // It used to be torn out on the same frame it was dismissed, so only the
        // entrance was animated. Two states now: [anchorId] keeps the Popup
        // alive, [menuOpen] runs the spring both ways, and the anchor is cleared
        // only after the exit has finished.
        //
        // 新手势在旧收尾还没走完时到来：先取消旧的 380ms 拆锚点任务，
        // 把旧强调用 140ms 收完，再按新消息走 arm delay；这样不会出现
        // 旧 job 把新菜单拆掉，也不会让新气泡没有锚点。
        LaunchedEffect(menuId) {
            val id = menuId
            if (id == null) {
                if (anchorId != null) {
                    closeMenu()
                } else if (retractJob?.isActive != true &&
                    (emphasisAnchor != null || menuEmphasis.value > 0f)
                ) {
                    // 新按下的会话在加速收尾途中被取消（页面切走/发送）：
                    // 没有菜单锚点也要把强调收干净，否则背景会停在半路。
                    // 正常关闭的 retractJob 还在跑时不要插手，否则会在
                    // 380ms 处把剩下的弹簧再加速一次。
                    menuEmphasis.animateTo(0f, tween(140))
                    menuEmphasis.snapTo(0f)
                    emphasisAnchor = null
                }
                return@LaunchedEffect
            }
            retractJob?.cancel()
            teardownJob?.cancel()
            retractJob = null
            teardownJob = null
            menuClosing = false
            menuOpen = false
            anchorId = null
            // 旧强调还在收：先快速收完（旧锚点保留，背景不会跳回 1），
            // 再换成新消息。
            if (menuEmphasis.value > .002f || menuEmphasis.isRunning) {
                menuEmphasis.animateTo(0f, tween(140))
            }
            menuEmphasis.snapTo(0f)
            emphasisAnchor = null
            emphasisAnchor = id
            anchorId = id
            delay(160)
            if (menuClosing || menuId != id) return@LaunchedEffect
            menuOpen = true
            menuEmphasis.animateTo(
                1f,
                spring(dampingRatio = .52f, stiffness = 300f, visibilityThreshold = .0002f),
            )
        }
        // Scrolling the transcript drops the menu: the anchor would drift away
        // from the finger's target anyway.
        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress) closeMenu()
        }
        val menuAnchor = anchorId
        val menuRect = menuAnchor?.let { rowRects[it] }
        if (menuAnchor != null && menuRect != null && menuRect.height > 0f) {
            val gap = with(density) { 8.dp.toPx() }
            val menuH = with(density) { 76.dp.toPx() }
            val menuW = with(density) { 300.dp.toPx() }
            // 短气泡以手指点为原点放大 1.02：菜单要贴着放大后的气泡边缘。
            // 长气泡不缩放，用原布局矩形；否则菜单会跟着一个不存在的
            // 放大再往外让 2%。
            val longMenu = menuRect.height > paneSize.height * LongBubbleFraction
            val grow = if (longMenu) 0f else .02f * menuEmphasis.value
            val press = pressOrigins[menuAnchor]
            val focus = if (press != null) {
                Offset(
                    menuRect.left + menuRect.width * press.x,
                    menuRect.top + menuRect.height * press.y,
                )
            } else {
                menuRect.center
            }
            val visualBottom = focus.y + (menuRect.bottom - focus.y) * (1f + grow)
            val visualTop = focus.y + (menuRect.top - focus.y) * (1f + grow)
            val visualCenterX = focus.x + (menuRect.center.x - focus.x) * (1f + grow)
            val below = visualBottom - paneOrigin.y + gap
            val above = visualTop - paneOrigin.y - gap - menuH
            val y = if (paneSize.height > 0 && below + menuH > paneSize.height) above.coerceAtLeast(0f) else below
            // 横向菜单以气泡中心为准，再夹在面板内。
            val x = (visualCenterX - paneOrigin.x - menuW / 2f)
                .coerceIn(0f, (paneSize.width - menuW).coerceAtLeast(0f))
            // 不用 Popup：Popup 是独立窗口，采不到列表的 layer backdrop，
            // GlassField 会静默退回纸片。放进同一个 Box，1型玻璃才连得上。
            //
            // 遮罩只在菜单真正打开时吃掉外点；一旦开始收回就撤掉，
            // 否则紧接着的长按会被这层遮罩吞掉，看起来像「没动画」。
            if (menuOpen || !menuClosing) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(19f)
                        .pointerInput(Unit) { detectTapGestures { closeMenu() } }
                )
            }
            CompositionLocalProvider(LocalGlassBackdrop provides listBackdrop) {
                // 同柔：菜单从气泡里长出来、收回时缩回去，进出同一条曲线。
                val menuIn = remember { Animatable(0f) }
                LaunchedEffect(menuOpen) {
                    menuIn.animateTo(
                        if (menuOpen) 1f else 0f,
                        spring(dampingRatio = .52f, stiffness = 300f),
                    )
                }
                Box(
                    Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .width(300.dp)
                        .zIndex(20f)
                        .graphicsLayer {
                            alpha = menuIn.value.coerceIn(0f, 1f)
                            val s = .90f + .10f * menuIn.value
                            scaleX = s
                            scaleY = s
                        },
                ) {
                    menuActions(menuAnchor) { closeMenu() }
                    // 收回中的菜单只剩视觉：盖一层透明拦截，别让
                    // 300×76 的旧按钮在淡出时还能点。
                    if (!menuOpen) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .pointerInput(Unit) { detectTapGestures { } }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Four levels, the way the writer thinks about it: 书籍 → 哪一本 → 「整本 / 某一章」;
 * 笔记 is its own shelf, grouped the same way the notes page groups it. Every
 * level slides in from the side inside the same sheet, so stepping down and
 * back up never rebuilds the panel.
 */
@Composable
private fun QuoteSheet(
    book: Book,
    books: List<Book>,
    notes: List<Note>,
    noteGroups: List<Group>,
    chosen: List<QuoteRef>,
    onDone: (List<QuoteRef>) -> Unit,
    onDismiss: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    var picked by remember { mutableStateOf(chosen) }
    // 0 根 / 1 选书 / 2 整本·章 / 3 笔记
    var level by remember { mutableIntStateOf(0) }
    var quoteBookId by remember { mutableStateOf(book.id) }
    var preview by remember { mutableStateOf(false) }
    val quoteBook = books.firstOrNull { it.id == quoteBookId } ?: book
    val bookRef = QuoteRef("book", quoteBook.id, "整本《${quoteBook.title}》", quoteBook.id)
    val bookChars = quoteBook.chapters.sumOf { it.content.length }
    val previewText = if (picked.isEmpty()) "" else AiPromptBuilder.quotePreview(book, picked, notes, books)
    // Notes by their own groups, in the order the notes page shows them.
    val noteSections: List<Pair<String, List<Note>>> = run {
        val grouped = notes.groupBy { it.groupId }
        val out = ArrayList<Pair<String, List<Note>>>()
        noteGroups.sortedBy { it.order }.forEach { g ->
            val list = grouped[g.id].orEmpty()
            if (list.isNotEmpty()) out += g.name to list
        }
        val known = noteGroups.map { it.id }.toSet()
        val loose = notes.filter { it.groupId == null || it.groupId !in known }
        if (loose.isNotEmpty()) out += "未分组" to loose
        out
    }

    // 整本已包含全部章节：选整本时把这本书的章节去掉，章节行同时变灰。
    val wholeBookPicked = picked.any { it.type == "book" && it.bookId == quoteBook.id }

    fun toggle(ref: QuoteRef) {
        picked = if (ref.type == "book") {
            if (wholeBookPicked) {
                picked.filterNot {
                    (it.type == "book" || it.type == "chapter") && it.bookId == ref.bookId
                }
            } else {
                picked.filterNot { it.type == "chapter" && it.bookId == ref.bookId } + ref
            }
        } else if (picked.any { it.type == ref.type && it.id == ref.id }) {
            picked.filterNot { it.type == ref.type && it.id == ref.id }
        } else {
            picked + ref
        }
    }

    ModalSheet(onDismiss = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (level != 0) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).bouncy { level = if (level == 2) 1 else 0 },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Glyph.Back, p.ink, 17.dp, 1.9f) }
                Spacer(Modifier.width(2.dp))
            }
            Column(Modifier.weight(1f)) {
                Txt(
                    when (level) {
                        1 -> "引用哪本书"
                        2 -> "《${quoteBook.title}》"
                        3 -> "引用笔记"
                        else -> "引用什么"
                    },
                    size = 18f,
                    weight = FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Txt(
                    when (level) {
                        1 -> "选一本，再挑整本或其中一章"
                        2 -> "整本或单独一章，选中后原样发出"
                        3 -> "按分组列，笔记正文和待办一起带上"
                        else -> "没选引用时，按问题自动检索原文"
                    },
                    size = 10.5f,
                    color = p.muted,
                )
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape).bouncy { onDismiss() },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Glyph.Close, p.muted, 15.dp, 1.8f) }
        }

        AnimatedContent(
            targetState = level,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(180)) { it / 4 * dir } + fadeIn(tween(140)))
                    .togetherWith(slideOutHorizontally(tween(140)) { -it / 4 * dir } + fadeOut(tween(110)))
            },
            label = "quoteLevel",
        ) { lv ->
            Column {
                when (lv) {
                    0 -> {
                        Spacer(Modifier.height(6.dp))
                        MoyuSurface(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                            radius = 18f,
                            flat = true,
                        ) {
                            Column {
                                BigQuoteEntry(
                                    glyph = Glyph.Book,
                                    title = "书籍",
                                    detail = if (books.size == 1) "只有《${book.title}》· ${book.chapters.size} 章"
                                    else "${books.size} 本 · 可只引某一章",
                                    count = picked.count { it.type == "book" || it.type == "chapter" },
                                ) { level = 1 }
                                RowDivider(inset = 64.dp)
                                BigQuoteEntry(
                                    glyph = Glyph.Note,
                                    title = "笔记",
                                    detail = if (notes.isEmpty()) "还没有笔记" else "${notes.size} 条",
                                    count = picked.count { it.type == "note" },
                                ) { if (notes.isNotEmpty()) level = 3 }
                            }
                        }
                    }
                    1 -> {
                        MoyuSurface(Modifier.fillMaxWidth().padding(horizontal = 18.dp), radius = 18f, flat = true) {
                            Column {
                                books.forEachIndexed { i, b ->
                                    if (i > 0) RowDivider(inset = 62.dp)
                                    BookQuoteRow(
                                        book = b,
                                        current = b.id == book.id,
                                        checkedCount = picked.count {
                                            (it.type == "book" || it.type == "chapter") && it.bookId == b.id
                                        },
                                    ) {
                                        quoteBookId = b.id
                                        level = 2
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        MoyuSurface(Modifier.fillMaxWidth().padding(horizontal = 18.dp), radius = 18f, flat = true) {
                            Column {
                                QuoteRow(
                                    label = "整本",
                                    detail = "$bookChars 字 · ${quoteBook.chapters.size} 章",
                                    checked = picked.any { it.type == "book" && it.bookId == quoteBook.id },
                                ) { toggle(bookRef) }
                                quoteBook.chapters.forEach { c ->
                                    RowDivider(inset = 20.dp)
                                    QuoteRow(
                                        label = c.title,
                                        detail = "${c.content.length} 字",
                                        checked = picked.any { it.type == "chapter" && it.id == c.id },
                                        enabled = !wholeBookPicked,
                                    ) {
                                        toggle(QuoteRef("chapter", c.id, c.title, quoteBook.id))
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        MoyuSurface(Modifier.fillMaxWidth().padding(horizontal = 18.dp), radius = 18f, flat = true) {
                            Column {
                                var first = true
                                noteSections.forEach { (title, list) ->
                                    if (first) {
                                        Txt(
                                            title,
                                            size = 9.5f,
                                            color = p.muted,
                                            letterSpacing = .6f,
                                            modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 1.dp),
                                        )
                                    } else {
                                        RowDivider(inset = 18.dp)
                                        Txt(
                                            title,
                                            size = 9.5f,
                                            color = p.muted,
                                            letterSpacing = .6f,
                                            modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 1.dp),
                                        )
                                    }
                                    list.forEach { n ->
                                        if (!first) RowDivider(inset = 18.dp)
                                        first = false
                                        QuoteRow(
                                            label = n.title.ifBlank { "无标题笔记" },
                                            detail = "${n.content.length} 字" +
                                                if (n.todos.isNotEmpty()) " · ${n.todos.size} 条待办" else "",
                                            checked = picked.any { it.type == "note" && it.id == n.id },
                                        ) { toggle(QuoteRef("note", n.id, n.title.ifBlank { "无标题笔记" })) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // One summary row, not three chips.
        if (picked.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(
                    "已选 ${picked.size} 项 · 约 ${previewText.length} 字",
                    size = 10.5f,
                    color = p.muted,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .bouncy { preview = !preview }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Txt(if (preview) "收起原文" else "看原文", size = 11f, color = p.ink)
                }
            }
            if (preview) {
                Spacer(Modifier.height(6.dp))
                MoyuSurface(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(max = 180.dp),
                    radius = 16f,
                    flat = true,
                ) {
                    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                        Txt(previewText.take(4000), size = 10f, color = p.muted, lineHeight = 15f)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // 分裂动效：勾上第一项时，次按钮从主按钮左缘「长」出来（宽 0 → 54dp，
        // 过冲一次再落位），主按钮同步让出宽度。两边共用同一个弹簧进度；
        // 不做 shader 融合，只靠 5dp 重叠让它们读起来是同一个泡撕开。
        val split by animateFloatAsState(
            targetValue = if (picked.isNotEmpty()) 1f else 0f,
            animationSpec = spring(
                dampingRatio = .62f,
                stiffness = 460f,
                visibilityThreshold = PROGRESS_THRESHOLD,
            ),
            visibilityThreshold = PROGRESS_THRESHOLD,
            label = "splitBar",
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width((54 * split).dp)
                    .height(50.dp)
                    .graphicsLayer { translationX = (5.dp * split).toPx() }
                    .clip(CircleShape)
                    .background(dockTone(ui))
                    .bouncy(split > .85f) { picked = emptyList() },
                contentAlignment = Alignment.Center,
            ) {
                Txt(
                    "清空",
                    size = 13f,
                    color = p.muted.copy(alpha = ((split - .45f) / .55f).coerceIn(0f, 1f)),
                )
            }
            Spacer(Modifier.width((10 * split).dp))
            PrimaryButton("确定", { onDone(picked) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** Which book this conversation belongs to: covers, sizes, one tap to switch. */
@Composable
private fun BookPickerSheet(
    books: List<Book>,
    currentId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalInk.current.palette
    ModalSheet(onDismiss = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Txt("问哪本书", size = 18f, weight = FontWeight.Medium)
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape).bouncy { onDismiss() },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Glyph.Close, p.muted, 15.dp, 1.8f) }
        }
        MoyuSurface(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(max = 430.dp),
            radius = 18f,
            flat = true,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                books.forEachIndexed { i, b ->
                    if (i > 0) RowDivider(inset = 62.dp)
                    BookQuoteRow(book = b, current = b.id == currentId, checkedCount = 0) { onPick(b.id) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** One book in the quote sheet: cover, title, size, and how much is pinned. */
@Composable
private fun BookQuoteRow(
    book: Book,
    current: Boolean,
    checkedCount: Int,
    onClick: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    Row(
        Modifier.fillMaxWidth().bouncy { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book, Modifier.width(34.dp).height(46.dp), radius = 6f, showText = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(
                    book.title,
                    size = 13.5f,
                    weight = if (current) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
                if (current) {
                    Spacer(Modifier.width(6.dp))
                    Txt("当前", size = 9f, color = p.muted)
                }
            }
            Spacer(Modifier.height(2.dp))
            Txt(
                "${book.chapters.size} 章 · ${book.chapters.sumOf { it.content.length }} 字",
                size = 10f,
                color = p.muted,
            )
        }
        if (checkedCount > 0) Txt("已选 $checkedCount", size = 9.5f, color = p.muted)
        Spacer(Modifier.width(4.dp))
        LineIcon(Glyph.Chevron, p.muted, 12.dp, 1.8f)
    }
}

@Composable
private fun BigQuoteEntry(
    glyph: Glyph,
    title: String,
    detail: String,
    count: Int,
    onClick: () -> Unit,
) {
    val ui = LocalInk.current
    Row(
        Modifier.fillMaxWidth().bouncy { onClick() }.padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(SmoothCorner(11.dp)).background(ui.palette.faint),
            contentAlignment = Alignment.Center,
        ) { LineIcon(glyph, ui.palette.ink, 18.dp, 1.6f) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, size = 15f, color = ui.palette.ink, weight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Txt(detail, size = 10.5f, color = ui.palette.muted)
        }
        if (count > 0) {
            Txt("已选 $count", size = 10.5f, color = ui.palette.ink, modifier = Modifier.padding(end = 8.dp))
        }
        LineIcon(Glyph.Chevron, ui.palette.muted, 15.dp, 1.7f)
    }
}

@Composable
private fun QuoteRow(
    label: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val ui = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .bouncy(enabled) { onClick() }
            .alpha(if (enabled) 1f else .36f)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(label, size = 13.5f, color = ui.palette.ink, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Txt(detail, size = 10f, color = ui.palette.muted)
        }
        Spacer(Modifier.width(10.dp))
        val tick by animateFloatAsState(
            targetValue = if (checked) 1f else 0f,
            animationSpec = spring(stiffness = 760f, dampingRatio = MOYU_DAMPING),
            label = "quoteTick",
        )
        Box(
            Modifier
                .size(23.dp)
                .clip(CircleShape)
                .background(if (checked) ui.palette.ink else ui.palette.faint)
                .border(.8.dp, ui.palette.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                LineIcon(
                    Glyph.Check, ui.palette.bgTop, 13.dp, 2.2f,
                    Modifier.graphicsLayer { scaleX = tick; scaleY = tick },
                )
            }
        }
    }
}

@Composable
private fun SectionCaption(text: String) {
    val ui = LocalInk.current
    Txt(
        text,
        size = 9.5f,
        color = ui.palette.muted,
        letterSpacing = 1.1f,
        modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 2.dp),
    )
}

/** Three dots breathing in sequence while the model reads. */
@Composable
private fun TypingDots() {
    val ui = LocalInk.current
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = .22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, delayMillis = i * 140),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(ui.palette.muted.copy(alpha = alpha))
            )
            if (i < 2) Spacer(Modifier.width(4.dp))
        }
    }
}

/** A chip that grows out of the quote button's side the first time it is composed.
 *  宽度 0 → 实宽，spring 0.52 会有一次 5% 过冲，读起来是「被挤出来」。 */
@Composable
private fun AppearingChip(id: String, content: @Composable () -> Unit) {
    val state = remember(id) { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = expandHorizontally(
            animationSpec = spring(dampingRatio = .52f, stiffness = 560f),
            expandFrom = Alignment.End,
        ) + fadeIn(tween(120)),
    ) { content() }
}

/**
 * Memory: one line per fact, no kind chips, no "new" badge, no per-row buttons.
 * Tapping a line opens the editor where 停用 / 删掉 live — the list is meant to
 * be read, not operated.
 */
@Composable
private fun MemoryPane(
    memo: BookMemo,
    global: List<MemoFact>,
    indexSize: Int,
    onClearChat: () -> Unit,
    onClear: () -> Unit,
    onEdit: (MemoFact) -> Unit,
    onDelete: (MemoFact) -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearChat by remember { mutableStateOf(false) }
    // Which branches are open. Empty by default: the eight 大标签 must fit on
    // one screen as a tree you can aim at, not eight cards you must scroll.
    var openTops by remember { mutableStateOf(emptySet<String>()) }
    val bookTree = MemoryOps.treeGroups(memo.facts, global = false)
    val globalTree = MemoryOps.treeGroups(global, global = true)
    // 回顾 = 已经写进正文的；规划 = 作者说过的打算。 Chapter summaries are a
    // review of the manuscript, so they sit in 回顾 instead of posing as 分纲.
    val reviewTree = bookTree.filterNot { MemoryOps.isPlanningTop(it.first) }
    val planTree = bookTree.filter { MemoryOps.isPlanningTop(it.first) }
    val chapterFacts = memo.chapters.map { c ->
        MemoFact(
            id = "sum-" + c.chapterId,
            kind = "章节纲要",
            name = c.title,
            detail = c.summary.ifBlank { "（还没有摘要）" },
            source = "chapter",
            enabled = true,
            pinned = false,
            createdAt = 0L,
            lastUsedAt = 0L,
        )
    }
    val emptyAll = bookTree.isEmpty() && globalTree.isEmpty() && memo.chapters.isEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(
                "${memo.facts.size + global.size} 条 · ${memo.chapters.size} 章摘要 · $indexSize 段原文",
                size = 10.5f,
                color = p.muted,
            )
            Spacer(Modifier.weight(1f))
            GlassChip(label = "清除聊天记录", selected = false, onClick = { confirmClearChat = true })
            Spacer(Modifier.width(8.dp))
            GlassChip(label = "清空记忆", selected = false, onClick = { confirmClear = true })
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            fun drawer(top: String, subs: List<Pair<String, List<MemoFact>>>) {
                item(key = "t-" + top) {
                    MemoryTreeDrawer(
                        top = top,
                        subs = subs,
                        open = top in openTops,
                        onToggle = {
                            openTops = if (top in openTops) openTops - top else openTops + top
                        },
                        onEdit = onEdit,
                    )
                }
            }
            if (reviewTree.isNotEmpty() || chapterFacts.isNotEmpty()) {
                item(key = "h-review") {
                    Txt("回顾 · 已经写进正文", size = 11f, color = p.muted, letterSpacing = .6f)
                }
                reviewTree.forEach { (top, subs) -> drawer(top, subs) }
                if (chapterFacts.isNotEmpty()) {
                    drawer("章节摘要", listOf("每章一句" to chapterFacts))
                }
            }
            if (planTree.isNotEmpty()) {
                item(key = "h-plan") {
                    Txt("规划 · 你打算怎么写", size = 11f, color = p.muted, letterSpacing = .6f)
                }
                planTree.forEach { (top, subs) -> drawer(top, subs) }
            }
            if (globalTree.isNotEmpty()) {
                item(key = "h-global") {
                    Txt("跨书偏好", size = 11f, color = p.muted, letterSpacing = .6f)
                }
                globalTree.forEach { (top, subs) -> drawer(top, subs) }
            }
            if (emptyAll) {
                item { MemoryEmpty("还没有记住什么。聊几轮，它会把人物、设定、伏笔和你说的下一章打算分开记。") }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空这本书的记忆？",
            message = "人物、设定、伏笔、时间线会清掉；聊天记录和作者偏好（跨书）保留。正文一个字都不会动。",
            confirmLabel = "清空记忆",
            onConfirm = { confirmClear = false; onClear() },
            onDismiss = { confirmClear = false },
        )
    }

    if (confirmClearChat) {
        ConfirmDialog(
            title = "清除聊天记录？",
            message = "只清对话，记忆（人物、设定、伏笔等）保留。正文不动。",
            confirmLabel = "清除",
            onConfirm = { confirmClearChat = false; onClearChat() },
            onDismiss = { confirmClearChat = false },
        )
    }
}

/** One low-contrast card: a header (title + count) over its content. */
@Composable
private fun MemoryCard(
    index: Int,
    title: String,
    count: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    // Staggered entrance: 40 ms apart, so the three cards settle in order
    // instead of the whole pane popping in at once.
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    val delay = index * 40
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(220, delayMillis = delay)) +
            slideInVertically(tween(220, delayMillis = delay)) { it / 34 },
    ) {
        MoyuSurface(Modifier.fillMaxWidth(), radius = 18f, flat = true) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Txt(title, size = 12.5f, weight = FontWeight.Medium, color = p.ink, letterSpacing = .3f)
                    Spacer(Modifier.weight(1f))
                    if (count > 0) Txt("$count", size = 10f, color = p.muted)
                }
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

/** The category tag inside a card: faint background, muted text. Low contrast
 *  on purpose — it is a label, not a button. */
@Composable
private fun MemoryKindHeader(kind: String, count: Int) {
    val p = LocalInk.current.palette
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(p.faint)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Txt(kind, size = 9.5f, color = p.muted, letterSpacing = .4f)
        }
        Spacer(Modifier.width(6.dp))
        Txt("$count", size = 9f, color = p.muted.copy(alpha = .7f))
    }
}



/**
 * One 大标签 as a drawer, the way a knowledge tree reads: a closed row shows
 * the branch and which leaves hang off it; opening it reveals the leaves and
 * their entries in place. Closed by default, so the eight branches fit one
 * screen and the writer can aim at the one they want.
 */
@Composable
private fun MemoryTreeDrawer(
    top: String,
    subs: List<Pair<String, List<MemoFact>>>,
    open: Boolean,
    onToggle: () -> Unit,
    onEdit: (MemoFact) -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val count = subs.sumOf { it.second.size }
    val rot by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = spring(stiffness = 620f, dampingRatio = MOYU_DAMPING),
        label = "treeRot",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SmoothCorner(18.dp))
            .background(p.faint),
    ) {
        Row(
            Modifier.fillMaxWidth().bouncy { onToggle() }.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(
                Glyph.ChevronDown,
                p.muted,
                13.dp,
                1.8f,
                Modifier.graphicsLayer { rotationZ = rot },
            )
            Spacer(Modifier.width(10.dp))
            Txt(top, size = 13f, weight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Txt("$count", size = 10f, color = p.muted)
            Spacer(Modifier.weight(1f))
            if (!open) {
                Txt(
                    subs.joinToString(" · ") { it.first },
                    size = 9.5f,
                    color = p.muted.copy(alpha = .62f),
                    maxLines = 1,
                )
            }
        }
        // Both directions animate: closing used to drop the children the same
        // frame, so the drawer collapsed without a retract.
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(
                animationSpec = spring(stiffness = 420f, dampingRatio = MOYU_DAMPING),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(160)),
            exit = shrinkVertically(
                animationSpec = spring(stiffness = 420f, dampingRatio = MOYU_DAMPING),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(120)),
        ) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp)) {
                subs.forEach { (leaf, list) ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Txt(leaf, size = 9.5f, color = p.muted.copy(alpha = .7f))
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(p.line.copy(alpha = .5f)))
                    }
                    list.forEach { fact -> MemoryFactLine(fact, onEdit) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * The settings-root pattern: a small grey caption, then a card holding the rows.
 * (A header inside the card read as one item, not as a group.)
 */
@Composable
private fun MemoryKindCard(title: String, count: Int, content: @Composable ColumnScope.() -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(title, size = 10.5f, color = p.muted.copy(alpha = .85f))
            Spacer(Modifier.weight(1f))
            Txt("$count", size = 10f, color = p.muted.copy(alpha = .7f))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SmoothCorner(18.dp))
                .background(p.faint)
                .padding(vertical = 4.dp, horizontal = 14.dp),
        ) { content() }
    }
}

/** One drawer: folder glyph, name, count, and a chevron that turns when open. */
@Composable
private fun FolderRow(label: String, count: Int, open: Boolean, onClick: () -> Unit) {
    val p = LocalInk.current.palette
    val rot by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = spring(stiffness = 620f, dampingRatio = MOYU_DAMPING),
        label = "folderRot",
    )
    Row(
        Modifier.fillMaxWidth().bouncy { onClick() }.padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(SmoothCorner(8.dp))
                .background(p.faint),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(Glyph.Folder, p.muted, 14.dp)
        }
        Spacer(Modifier.width(10.dp))
        Txt(
            label,
            size = 13.5f,
            weight = if (open) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Txt("$count", size = 10f, color = p.muted)
        Spacer(Modifier.width(8.dp))
        LineIcon(
            Glyph.Chevron,
            p.muted,
            12.dp,
            1.8f,
            modifier = Modifier.graphicsLayer { rotationZ = rot },
        )
    }
}

@Composable
private fun MemoryEmpty(text: String) {
    val p = LocalInk.current.palette
    Txt(text, size = 12f, color = p.muted, lineHeight = 19f, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun MemoryFactLine(f: MemoFact, onEdit: (MemoFact) -> Unit) {
    val ui = LocalInk.current
    Column(
        Modifier
            .fillMaxWidth()
            .bouncy { onEdit(f) }
            .padding(vertical = 9.dp),
    ) {
        Txt(
            f.name + "　" + f.detail,
            size = 13f,
            color = if (f.enabled) ui.palette.ink else ui.palette.muted.copy(alpha = .55f),
            lineHeight = 20f,
        )
        if (!f.enabled) {
            Spacer(Modifier.height(2.dp))
            Txt("已停用，不会发给 AI", size = 9.5f, color = ui.palette.muted)
        }
    }
}

@Composable
private fun FactDialog(
    fact: MemoFact,
    onSave: (String, String) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ui = LocalInk.current
    var name by remember { mutableStateOf(fact.name) }
    var detail by remember { mutableStateOf(fact.detail) }
    GlassDialog(onDismiss) {
        DialogTitle("这条记忆")
        Spacer(Modifier.height(10.dp))
        InkField(value = name, onValueChange = { name = it }, placeholder = "名称", minHeight = 44.dp, textSize = 13f)
        Spacer(Modifier.height(8.dp))
        InkField(
            value = detail,
            onValueChange = { detail = it },
            placeholder = "内容",
            singleLine = false,
            maxLines = 4,
            minHeight = 70.dp,
            textSize = 13f,
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            "保存",
            { if (name.isNotBlank() && detail.isNotBlank()) onSave(name.trim(), detail.trim()) },
            Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton(
                if (fact.enabled) "停用" else "启用",
                onToggle,
                Modifier.weight(1f),
            )
            GlassButton("删掉", onDelete, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Txt(
            "停用的条目不会发给 AI，但还留在列表里。",
            size = 10f,
            color = ui.palette.muted,
            lineHeight = 16f,
        )
    }
}

/**
 * One row in the long-press sheet. Plain text, no icons: the sheet lists verbs,
 * and an icon per verb is decoration.
 */
@Composable
private fun MessageChip(
    glyph: Glyph,
    label: String,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    Column(
        Modifier
            .width(56.dp)
            .clip(SmoothCorner(14.dp))
            .bouncy(scale = .92f) { onClick() }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineIcon(glyph, if (muted) p.muted else p.ink, 20.dp)
        Spacer(Modifier.height(4.dp))
        Txt(label, size = 10.5f, color = if (muted) p.muted else p.ink)
    }
}

