package com.moyu.writer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val coverPalettes = listOf(
    listOf(Color(0xFF22252C), Color(0xFF0B0D11), Color(0xFFF1EBE1)),
    listOf(Color(0xFFD7BC8D), Color(0xFF7C5E37), Color(0xFF241D12)),
    listOf(Color(0xFF415C4F), Color(0xFF14221C), Color(0xFFE9E4D6)),
    listOf(Color(0xFFA8BDC8), Color(0xFF4A626E), Color(0xFF15232A)),
    listOf(Color(0xFF9A5548), Color(0xFF3D1E19), Color(0xFFF4E5DA)),
    listOf(Color(0xFF7A6A82), Color(0xFF2A2330), Color(0xFFF1E9EE)),
    listOf(Color(0xFFEDE3D0), Color(0xFFC4B296), Color(0xFF241E15)),
    listOf(Color(0xFF4D6B86), Color(0xFF182635), Color(0xFFE9F0F4)),
)

private val coverNames = listOf("夜石", "砂金", "苔庭", "雾蓝", "砖红", "暮紫", "素纸", "靛青")

fun coverColorA(style: Int): Color = coverPalettes[style.mod(coverPalettes.size)][0]
fun coverColorB(style: Int): Color = coverPalettes[style.mod(coverPalettes.size)][1]
fun coverInk(style: Int): Color = coverPalettes[style.mod(coverPalettes.size)][2]

@Composable
fun BookCover(
    book: Book,
    modifier: Modifier = Modifier,
    radius: Float = LocalInk.current.settings.coverRadius,
    showText: Boolean = true,
) {
    val context = LocalContext.current
    val ui = LocalInk.current
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = book.coverUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = book.coverUri ?: return@runCatching null
                val bitmap = if (uri.startsWith("content:") || uri.startsWith("file:")) {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
                } else {
                    BitmapFactory.decodeFile(uri)
                }
                bitmap?.let {
                    if (it.width > 1000) {
                        val ratio = 1000f / it.width
                        Bitmap.createScaledBitmap(it, 1000, (it.height * ratio).toInt().coerceAtLeast(1), true)
                    } else it
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = SmoothCorner(radius.dp)
    // `clip` anti-aliases its edge: the outermost pixels are semi-transparent and
    // the card's paper colour shows through, which on a dark cover reads as a
    // thin bright fringe. Painting the cover's own base colour under the clip
    // makes those edge pixels blend into the cover instead.
    val coverBase = coverColorA(book.coverStyle)
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(coverBase)
    ) {
        val widthDp = maxWidth
        val titleSize = (widthDp.value * .14f).coerceIn(15f, 34f)
        val a = coverBase
        val b = coverColorB(book.coverStyle)
        val ink = coverInk(book.coverStyle)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(a, b)))
            val w = size.width
            val h = size.height
            when (book.coverStyle.mod(8)) {
                0 -> {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = .12f), Color.Transparent),
                            center = Offset(w * .3f, h * .18f),
                            radius = w * .8f,
                        ),
                        w * .8f,
                        Offset(w * .3f, h * .18f),
                    )
                    drawArc(
                        Color.White.copy(alpha = .17f),
                        -36f, 132f, false,
                        topLeft = Offset(w * .22f, h * .18f),
                        size = Size(w * .9f, w * .9f),
                        style = Stroke(w * .005f),
                    )
                    drawLine(Color.White.copy(alpha = .15f), Offset(w * .14f, h * .63f), Offset(w * .86f, h * .63f), w * .004f)
                }
                1 -> {
                    var i = 0
                    while (i < 7) {
                        val y = h * .16f + i * h * .075f
                        drawLine(ink.copy(alpha = .12f), Offset(w * .12f, y), Offset(w * (.55f + i * .045f), y), w * .004f)
                        i++
                    }
                    drawCircle(ink.copy(alpha = .16f), w * .36f, Offset(w * .8f, h * .24f), style = Stroke(w * .006f))
                }
                2 -> {
                    var i = 0
                    while (i < 5) {
                        val p = Path()
                        p.moveTo(w * .1f, h * (.24f + i * .12f))
                        p.cubicTo(w * .35f, h * (.18f + i * .12f), w * .65f, h * (.34f + i * .12f), w * .92f, h * (.24f + i * .12f))
                        drawPath(p, Color.White.copy(alpha = .10f), style = Stroke(w * .006f))
                        i++
                    }
                }
                3 -> {
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = .12f), Color.Transparent),
                            start = Offset(0f, 0f),
                            end = Offset(w, h * .65f),
                        )
                    )
                    val p = Path()
                    p.moveTo(0f, h * .32f); p.lineTo(w, h * .18f); p.lineTo(w, h * .34f); p.lineTo(0f, h * .48f); p.close()
                    drawPath(p, Color.White.copy(alpha = .06f))
                }
                4 -> {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color(0xFFE3B99A).copy(alpha = .24f), Color.Transparent),
                            center = Offset(w * .78f, h * .14f),
                            radius = w,
                        )
                    )
                    rotate(-14f, Offset(w * .5f, h * .5f)) {
                        drawRect(Color.White.copy(alpha = .07f), Offset(-w, h * .58f), Size(w * 3, h * .16f))
                    }
                }
                5 -> {
                    var i = 0
                    while (i < 9) {
                        drawLine(
                            Color.White.copy(alpha = .05f + i * .006f),
                            Offset(w * .1f, h * (.18f + i * .07f)),
                            Offset(w * .9f, h * (.13f + i * .07f)),
                            w * .003f,
                        )
                        i++
                    }
                    drawCircle(Color.White.copy(alpha = .14f), w * .2f, Offset(w * .75f, h * .75f), style = Stroke(w * .005f))
                }
                6 -> {
                    drawCircle(ink.copy(alpha = .07f), w * .55f, Offset(w * .82f, h * .2f))
                    drawLine(ink.copy(alpha = .22f), Offset(w * .12f, h * .13f), Offset(w * .12f, h * .82f), w * .005f)
                    drawLine(ink.copy(alpha = .11f), Offset(w * .17f, h * .13f), Offset(w * .17f, h * .82f), w * .002f)
                }
                else -> {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = .15f), Color.Transparent),
                            center = Offset(w * .24f, h * .22f),
                            radius = w,
                        ),
                        w,
                        Offset(w * .24f, h * .22f),
                    )
                    drawCircularGrid(this, w, h)
                }
            }
            image?.let { bmp ->
                val scale = max(w / bmp.width, h / bmp.height)
                val srcW = (w / scale).toInt().coerceIn(1, bmp.width)
                val srcH = (h / scale).toInt().coerceIn(1, bmp.height)
                drawImage(
                    image = bmp,
                    srcOffset = androidx.compose.ui.unit.IntOffset((bmp.width - srcW) / 2, (bmp.height - srcH) / 2),
                    srcSize = IntSize(srcW, srcH),
                    dstSize = IntSize(w.toInt(), h.toInt()),
                    filterQuality = FilterQuality.Medium,
                )
                drawRect(Color(0x33000000))
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = .38f), Color.Transparent),
                        startY = 0f,
                        endY = h * .26f,
                    )
                )
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = .18f), Color.Black.copy(alpha = .72f)),
                        startY = h * .38f,
                        endY = h,
                    )
                )
            }
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = .10f), Color.Transparent, Color.Black.copy(alpha = .16f)),
                )
            )
            drawLine(Color.White.copy(alpha = .20f), Offset(0f, 1f), Offset(w, 1f), 1f)
            drawRect(Color.Black.copy(alpha = .18f), Offset(0f, 0f), Size(w * .045f, h))
        }
        if (showText) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = (widthDp.value * .11f).coerceAtLeast(11f).dp,
                        end = (widthDp.value * .09f).coerceAtLeast(9f).dp,
                        top = (widthDp.value * .09f).coerceAtLeast(9f).dp,
                        bottom = (widthDp.value * .09f).coerceAtLeast(9f).dp,
                    ),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Txt(
                    book.genre.ifBlank { "小说" },
                    size = (widthDp.value * .046f).coerceIn(7f, 10f),
                    color = (if (image != null) Color.White else ink).copy(alpha = .64f),
                    letterSpacing = 1.4f,
                    maxLines = 1,
                )
                Column {
                    Box(
                        Modifier
                            .width((widthDp.value * .18f).coerceAtLeast(18f).dp)
                            .height(.8.dp)
                            .background((if (image != null) Color.White else ink).copy(alpha = .32f))
                    )
                    Spacer(Modifier.height((widthDp.value * .055f).coerceAtLeast(5f).dp))
                    Txt(
                        book.title,
                        size = titleSize,
                        color = if (image != null) Color.White else ink,
                        weight = FontWeight.SemiBold,
                        lineHeight = titleSize * 1.24f,
                        letterSpacing = .7f,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (book.author.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Txt(
                            book.author,
                            size = (widthDp.value * .047f).coerceIn(7f, 10f),
                            color = (if (image != null) Color.White else ink).copy(alpha = .58f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun drawCircularGrid(scope: androidx.compose.ui.graphics.drawscope.DrawScope, w: Float, h: Float) {
    var i = 0
    while (i < 6) {
        scope.drawCircle(
            Color.White.copy(alpha = .045f),
            w * .12f,
            Offset(w * .84f, h * (.12f + i * .14f)),
            style = Stroke(w * .003f),
        )
        i++
    }
}

@Composable
fun LibraryScreen(
    store: AppStore,
    onOpenBook: (String) -> Unit,
    onContinue: (Book, Chapter) -> Unit,
    onCreateBook: (Book) -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val books = store.books
    val settings = store.data.settings
    var createOpen by remember { mutableStateOf(false) }
    var groupManagerOpen by remember { mutableStateOf(false) }
    var controlsOpen by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    // Where each card sits right now. Click time resolves it through the live
    // coordinates: the tab pager moves pages with a graphics-layer translation,
    // so a rect cached at layout time would be stale.
    val flightCoords = remember { mutableStateMapOf<String, androidx.compose.ui.layout.LayoutCoordinates>() }
    fun openBook(book: Book) {
        onOpenBook(book.id)
    }
    val visibleBooks = books.filter { b ->
        selectedGroup == null || (selectedGroup == "" && b.groupId == null) || (selectedGroup != "" && b.groupId == selectedGroup)
    }
    // "Continue writing" is a global resume shortcut, not part of the filtered
    // shelf. Tying it to visibleBooks made the whole hero card disappear when a
    // custom group had no books, collapsing the top of the library.
    val continueBook = store.book(store.data.lastBookId) ?: books.firstOrNull()
    val continueChapter = continueBook?.chapter(store.data.lastChapterId)
        ?: continueBook?.chapters?.firstOrNull()
    LaunchedEffect(store.bookGroups) {
        if (selectedGroup != null && selectedGroup != "" && store.bookGroups.none { it.id == selectedGroup }) {
            selectedGroup = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        if (settings.libraryMode == "list") {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 130.dp),
                // Match the grid/compact top rhythm exactly: same item gap, no extra
                // list-only spacer after the header. The old 12dp + 6dp combo made
                // the header/continue/shelf offsets jump only in list mode.
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item { LibraryHeader(store) { createOpen = true } }
                if (continueBook != null && continueChapter != null) {
                    item {
                        ContinueCard(continueBook, continueChapter) {
                            onContinue(continueBook, continueChapter)
                        }
                    }
                }
                item {
                    ShelfHeader(
                        summary = librarySummary(store, settings, selectedGroup),
                        onFilters = { controlsOpen = true },
                    )
                }
                if (visibleBooks.isEmpty()) {
                    item {
                        EmptyState(
                            Glyph.Book,
                            if (selectedGroup == null) "书架还空着" else "这个分组还没有书",
                            if (selectedGroup == null) {
                                "点右上角 +，先给故事一个名字。书名、封面、章节都可以随时再改。"
                            } else {
                                "点右上角 + 新建一本，会自动放进当前分组；也可以从书籍信息里换组。"
                            },
                        )
                    }
                } else {
                    items(visibleBooks, key = { it.id }) { book ->
                        Box(Modifier.animateItem()) {
                            BookListRow(
                                book,
                                groupName = store.bookGroups.firstOrNull { it.id == book.groupId }?.name,
                                onClick = { openBook(book) },
                            )
                        }
                    }
                }
            }
        } else {
            // Compact must be visibly denser than the normal grid, not just a
            // smaller caption on the same 3-column layout.
            val columns = if (settings.libraryMode == "compact") 4 else settings.columns.coerceIn(2, 4)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 132.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { LibraryHeader(store) { createOpen = true } }
                if (continueBook != null && continueChapter != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ContinueCard(continueBook, continueChapter) {
                            onContinue(continueBook, continueChapter)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ShelfHeader(
                        summary = librarySummary(store, settings, selectedGroup),
                        onFilters = { controlsOpen = true },
                    )
                }
                if (visibleBooks.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            Glyph.Book,
                            if (selectedGroup == null) "书架还空着" else "这个分组还没有书",
                            if (selectedGroup == null) {
                                "点右上角 +，先给故事一个名字。书名、封面、章节都可以随时再改。"
                            } else {
                                "点右上角 + 新建一本，会自动放进当前分组；也可以从书籍信息里换组。"
                            },
                        )
                    }
                } else {
                    items(visibleBooks, key = { it.id }) { book ->
                        Box(Modifier.animateItem()) {
                            BookGridCard(
                                book,
                                compact = settings.libraryMode == "compact",
                                groupName = store.bookGroups.firstOrNull { it.id == book.groupId }?.name,
                                onClick = { openBook(book) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (createOpen) {
        BookEditorDialog(
            existing = null,
            groups = store.bookGroups,
            initialGroupId = selectedGroup?.takeIf { it.isNotEmpty() },
            onDismiss = { createOpen = false },
            onSubmit = { book ->
                store.addBook(book)
                createOpen = false
                onCreateBook(book)
                ToastBus.show("《${book.title}》已建好")
            },
        )
    }
    if (groupManagerOpen) {
        GroupManagerSheet(GroupKind.BOOK, store) { groupManagerOpen = false }
    }
    if (controlsOpen) {
        InlineModalSheet(onDismiss = { controlsOpen = false }) { _ ->
            DialogTitle("书架筛选与排布", "布局、分栏和分组集中在这里。", onClose = { controlsOpen = false })
            Spacer(Modifier.height(14.dp))
            Txt("排布", size = 10f, color = LocalInk.current.palette.muted)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassChip("网格", settings.libraryMode == "grid") {
                    store.updateSettings { it.copy(libraryMode = "grid") }
                }
                GlassChip("紧凑", settings.libraryMode == "compact") {
                    store.updateSettings { it.copy(libraryMode = "compact") }
                }
                GlassChip("列表", settings.libraryMode == "list") {
                    store.updateSettings { it.copy(libraryMode = "list") }
                }
            }
            if (settings.libraryMode == "grid") {
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("分栏", size = 10f, color = LocalInk.current.palette.muted)
                    Spacer(Modifier.width(9.dp))
                    GlassChip("−", false) {
                        store.updateSettings { it.copy(columns = (it.columns - 1).coerceAtLeast(2)) }
                    }
                    Spacer(Modifier.width(6.dp))
                    Txt("${settings.columns} 栏", size = 11.5f)
                    Spacer(Modifier.width(6.dp))
                    GlassChip("+", false) {
                        store.updateSettings { it.copy(columns = (it.columns + 1).coerceAtMost(4)) }
                    }
                }
            }
            Spacer(Modifier.height(15.dp))
            Txt("分组", size = 10f, color = LocalInk.current.palette.muted)
            Spacer(Modifier.height(7.dp))
            GroupFilterBar(
                groups = store.bookGroups,
                selected = selectedGroup,
                onSelect = { selectedGroup = it },
                onManage = {
                    controlsOpen = false
                    groupManagerOpen = true
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LibraryHeader(store: AppStore, onAdd: () -> Unit) {
    val ui = LocalInk.current
    val settings = ui.settings
    // The shelf used to open with a time-based greeting, a tagline under it, a
    // round monogram, a daily-word badge and a separate 「我的书架」 section
    // title. All of that is gone: one large title on the same 20dp gutter as the
    // other three tabs, the add button moved up to sit beside it, and the space
    // that used to be spent on those lines now belongs to the books.
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 14.dp)) {
        // Same type size, weight and tracking as 笔记 / 数据 / 设置: the shelf
// was the only tab using 29f Medium, which made its title sit 13px lower
// and 21px shorter than the other three.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("书架", size = 34f, weight = FontWeight.SemiBold, letterSpacing = .4f)
            Spacer(Modifier.weight(1f))
            GlassIconButton(Glyph.Plus, onClick = onAdd)
        }
        Spacer(Modifier.height(14.dp))
    }
}


@Composable
private fun ContinueCard(book: Book, chapter: Chapter, onClick: () -> Unit) {
    val ui = LocalInk.current
    MoyuSurface(Modifier.fillMaxWidth(),
        radius = 28f,
        flat = true,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .bouncy(scale = .985f) { onClick() }
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(ui.accent))
                    Spacer(Modifier.width(6.dp))
                    Txt("继续创作", size = 9.5f, color = ui.palette.muted, letterSpacing = .6f)
                }
                Spacer(Modifier.height(7.dp))
                Txt(
                    book.title,
                    size = 23f,
                    weight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = .5f,
                )
                Spacer(Modifier.height(2.dp))
                Txt(
                    "${chapter.title} · ${formatWords(countWords(chapter.content))} 字",
                    size = 10f,
                    color = ui.palette.muted,
                )
                // A blank chapter simply shows no preview line; the old fallback sentence
            // was decorative filler rather than information.
            val chapterPreview = chapter.content.trim().replace(Regex("\\s+"), " ").take(36)
            if (chapterPreview.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Txt(
                    chapterPreview,
                    size = 10f,
                    color = ui.palette.muted.copy(alpha = .88f),
                    lineHeight = 16f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(ui.palette.ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(Glyph.ArrowUp, ui.palette.bgTop, 14.dp, 1.9f)
                    }
                    Spacer(Modifier.width(8.dp))
                    Txt("接着写", size = 10.5f, color = ui.palette.ink, weight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(14.dp))
            BookCover(
                book,
                Modifier.width(88.dp).aspectRatio(.68f),
                radius = 14f,
            )
        }
    }
}

private fun librarySummary(store: AppStore, settings: AppSettings, selectedGroup: String?): String {
    val group = when (selectedGroup) {
        null -> "全部"
        "" -> "未分组"
        else -> store.bookGroups.firstOrNull { it.id == selectedGroup }?.name ?: "全部"
    }
    val layout = when (settings.libraryMode) {
        "compact" -> "紧凑"
        "list" -> "列表"
        else -> "网格 ${settings.columns} 栏"
    }
    return "$layout · $group"
}

@Composable
private fun ShelfHeader(
    summary: String,
    onFilters: () -> Unit,
) {
    // Only the filter row is left. Its old 「我的书架」 title duplicated the tab
    // title directly above it and carried a second book/word count, so it was
    // removed along with that count.
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassChip("筛选", false) { onFilters() }
            Spacer(Modifier.width(9.dp))
            Txt(summary, size = 10.5f, color = LocalInk.current.palette.muted)
        }
        Spacer(Modifier.height(3.dp))
    }
}

@Composable
private fun BookGridCard(book: Book, compact: Boolean, groupName: String?, onClick: () -> Unit) {
    val ui = LocalInk.current
    val settings = ui.settings
    val scale = settings.cardScale.coerceIn(.78f, 1f)
    val pad = ((1f - scale) * 24f).dp
    Column(
        Modifier
            .fillMaxWidth()
            .bouncy(scale = .96f) { onClick() }
            .padding(horizontal = pad)
    ) {
        BookCover(book, Modifier.fillMaxWidth().aspectRatio(.68f))
        Spacer(Modifier.height(if (compact) 7.dp else 10.dp))
        Txt(
            book.title,
            size = if (compact) 12f else 14.5f,
            weight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Txt(
            listOfNotNull(groupName, if (compact) "${book.chapters.size} 章" else "${book.genre.ifBlank { "小说" }} · ${formatWords(book.words)} 字").joinToString(" · "),
            size = if (compact) 8.5f else 9.5f,
            color = ui.palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookListRow(book: Book, groupName: String?, onClick: () -> Unit) {
    val ui = LocalInk.current
    MoyuSurface(Modifier.fillMaxWidth(), radius = 22f, flat = true) {
        Row(
            Modifier
                .fillMaxWidth()
                .bouncy(scale = .985f) { onClick() }
                .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book, Modifier.width(58.dp).aspectRatio(.68f), radius = 10f)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (book.pinned) {
                        LineIcon(Glyph.Pin, ui.palette.muted, 11.dp, 1.4f)
                        Spacer(Modifier.width(4.dp))
                    }
                    Txt(book.title, size = 15f, weight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Txt(
                    listOfNotNull(groupName, "${book.chapters.size} 章 · ${formatWords(book.words)} 字 · ${relativeTime(book.updatedAt)}").joinToString(" · "),
                    size = 9.5f,
                    color = ui.palette.muted,
                    maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(ui.palette.line)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((book.words / 80000f).coerceIn(.06f, 1f))
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(ui.accent)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            LineIcon(Glyph.Chevron, ui.palette.muted.copy(alpha = .6f), 16.dp)
        }
    }
}

@Composable
private fun AddBookTile(onClick: () -> Unit) {
    val ui = LocalInk.current
    Column(Modifier.fillMaxWidth().bouncy(scale = .96f) { onClick() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(.68f)
                .clip(SmoothCorner(ui.settings.coverRadius.dp))
                .background(ui.palette.faint)
                .border(1.dp, ui.palette.line, SmoothCorner(ui.settings.coverRadius.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(43.dp)
                        .clip(CircleShape)
                        .background(ui.palette.ink.copy(alpha = .08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(Glyph.Plus, ui.palette.ink, 20.dp)
                }
                Spacer(Modifier.height(10.dp))
                Txt("新建", size = 12f, color = ui.palette.ink, weight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(10.dp))
        Txt("新的故事", size = 14.5f, color = ui.palette.ink, weight = FontWeight.Medium)
    }
}

@Composable
private fun AddBookRow(onClick: () -> Unit) {
    val ui = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SmoothCorner(22.dp))
            .background(ui.palette.faint)
            .border(.8.dp, ui.palette.line, SmoothCorner(22.dp))
            .bouncy(scale = .985f) { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(Glyph.Plus, ui.palette.ink, 18.dp)
        Spacer(Modifier.width(10.dp))
        Txt("新建一本书", size = 13.5f, weight = FontWeight.Medium)
    }
}

@Composable
fun BookEditorDialog(
    existing: Book?,
    groups: List<Group> = emptyList(),
    initialGroupId: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (Book) -> Unit,
) {
    val ui = LocalInk.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val titleState = remember { mutableStateOf(existing?.title ?: "") }
    var author by remember { mutableStateOf(existing?.author ?: "") }
    var genre by remember { mutableStateOf(existing?.genre ?: "小说") }
    var synopsis by remember { mutableStateOf(existing?.synopsis ?: "") }
    var style by remember { mutableIntStateOf(existing?.coverStyle ?: 0) }
    var coverUri by remember { mutableStateOf(existing?.coverUri) }
    var groupId by remember { mutableStateOf(existing?.groupId ?: initialGroupId) }
    var savingCover by remember { mutableStateOf(false) }
    val genres = listOf("小说", "悬疑", "科幻", "幻想", "言情", "随笔")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            savingCover = true
            scope.launch {
                val path = withContext(Dispatchers.IO) { saveCoverImage(context, uri) }
                coverUri = path
                savingCover = false
                if (path == null) ToastBus.show("封面读取失败，请换一张")
            }
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val dismiss = {
        keyboard?.hide()
        onDismiss()
    }
    var editing by remember { mutableStateOf<String?>(null) }
    val editFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(editing) {
        // Entering a form row must not steal focus or raise the IME. The user
        // explicitly taps the field when they want to type.
        if (editing != null) keyboard?.hide()
    }
    fun finishEdit() {
        keyboard?.hide()
        editing = null
    }
    GlassDialog(
        dismiss,
        dismissOnOutsideClick = false,
    ) {
        AnimatedPane(targetState = editing, label = "bookField") { field ->
            if (field == null) {
            DialogTitle(
                if (existing == null) "新建一本书" else "编辑书籍",
                onClose = dismiss,
            )
            Spacer(Modifier.height(8.dp))
            val groupName = groups.firstOrNull { it.id == groupId }?.name ?: "未分组"
            BookFormRow(
                label = "封面",
                value = if (coverUri != null) "自定义封面" else "色板 ${style + 1}",
                leading = {
                    BookCover(
                        Book(title = titleState.value, author = author, genre = genre, coverStyle = style, coverUri = coverUri),
                        Modifier.width(36.dp).height(52.dp),
                        radius = 6f,
                    )
                },
            ) { editing = "cover" }
            RowDivider()
            BookFormRow("书名", titleState.value.ifBlank { "给故事起一个名字" }) { editing = "title" }
            RowDivider()
            BookFormRow("作者", author.ifBlank { "未填写" }) { editing = "author" }
            RowDivider()
            BookFormRow("题材", genre.ifBlank { "未填写" }) { editing = "genre" }
            RowDivider()
            BookFormRow("分组", groupName) { editing = "group" }
            RowDivider()
            BookFormRow("简介", synopsis.ifBlank { "未填写" }, maxLines = 2) { editing = "synopsis" }
            Spacer(Modifier.height(14.dp))
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Glyph.Back, { finishEdit() })
                Spacer(Modifier.width(8.dp))
                Txt(
                    when (field) {
                        "cover" -> "封面"
                        "title" -> "书名"
                        "author" -> "作者"
                        "genre" -> "题材"
                        "group" -> "分组"
                        else -> "简介"
                    },
                    size = 18f,
                    weight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                GlassIconButton(Glyph.Check, { finishEdit() })
            }
            Spacer(Modifier.height(10.dp))
            when (field) {
                "cover" -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(8) { index ->
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(coverColorA(index), coverColorB(index))))
                                    .then(
                                        if (style == index) Modifier.border(2.dp, ui.palette.ink, CircleShape)
                                        else Modifier.border(.8.dp, Color.White.copy(alpha = .3f), CircleShape)
                                    )
                                    .bouncy {
                                        style = index
                                        coverUri = null
                                        finishEdit()
                                    }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassButton(
                        if (savingCover) "载入中…" else "从相册选择封面",
                        {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        Modifier.fillMaxWidth(),
                        glyph = Glyph.Folder,
                    )
                }
                "title" -> InkField(
                    titleState.value,
                    { titleState.value = it.take(40) },
                    "给故事起一个名字",
                    Modifier.fillMaxWidth().focusRequester(editFocus),
                    textSize = 17f,
                )
                "author" -> InkField(
                    author,
                    { author = it.take(24) },
                    "执笔人",
                    Modifier.fillMaxWidth().focusRequester(editFocus),
                )
                "genre" -> InkField(
                    genre,
                    { genre = it.take(12) },
                    "小说",
                    Modifier.fillMaxWidth().focusRequester(editFocus),
                )
                "group" -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            GlassChip("未分组", groupId == null) {
                                groupId = null
                                finishEdit()
                            }
                        }
                        items(groups, key = { it.id }) { group ->
                            GlassChip(group.name, groupId == group.id) {
                                groupId = group.id
                                finishEdit()
                            }
                        }
                    }
                }
                else -> InkField(
                    synopsis,
                    { synopsis = it.take(300) },
                    "一句话，留给以后翻开这本书的人。",
                    Modifier.fillMaxWidth().focusRequester(editFocus),
                    singleLine = false,
                    minHeight = 90.dp,
                    maxLines = 4,
                )
            }
            Spacer(Modifier.height(16.dp))
            }
        }
        if (editing == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", dismiss, Modifier.weight(1f))
            PrimaryButton(
                if (existing == null) "创建" else "保存",
                {
                    if (titleState.value.isBlank()) {
                        editing = "title"
                        ToastBus.show("先给故事一个名字")
                    } else {
                        keyboard?.hide()
                        if (existing == null) {
                            val first = Chapter(title = chapterFallbackTitle(0))
                            onSubmit(
                                Book(
                                    title = titleState.value.trim(),
                                    author = author.trim(),
                                    genre = genre.trim(),
                                    synopsis = synopsis.trim(),
                                    coverStyle = style,
                                    coverUri = coverUri,
                                    chapters = listOf(first),
                                    groupId = groupId,
                                )
                            )
                        } else {
                            onSubmit(
                                existing.copy(
                                    title = titleState.value.trim(),
                                    author = author.trim(),
                                    genre = genre.trim(),
                                    synopsis = synopsis.trim(),
                                    coverStyle = style,
                                    coverUri = coverUri,
                                    groupId = groupId,
                                )
                            )
                        }
                    }
                },
                Modifier.weight(1f),
                enabled = !savingCover,
            )
        }
        }
    }
}

@Composable
private fun BookFormRow(
    label: String,
    value: String,
    leading: (@Composable () -> Unit)? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) {
    val ui = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SmoothCorner(12.dp))
            .bouncy(scale = .96f, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(label, size = 9f, color = ui.palette.muted, letterSpacing = .8f)
            Spacer(Modifier.height(3.dp))
            Txt(
                value,
                size = 14f,
                color = if (value.startsWith("未填写") || value.startsWith("给故事")) ui.palette.muted else ui.palette.ink,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        LineIcon(Glyph.Chevron, ui.palette.muted.copy(alpha = .6f), 14.dp)
    }
}

private fun saveCoverImage(context: Context, uri: Uri): String? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val raw = BitmapFactory.decodeStream(input)
        input.close()
        if (raw == null) return null
        val max = 1200
        val ratio = min(1f, max.toFloat() / max(raw.width, raw.height))
        val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(raw, (raw.width * ratio).toInt(), (raw.height * ratio).toInt(), true) else raw
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "cover_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        file.absolutePath
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun BookDetailScreen(
    store: AppStore,
    bookId: String,
    onBack: () -> Unit,
    onOpenChapter: (String, String) -> Unit,
) {
    val ui = LocalInk.current
    val book = store.book(bookId)
    if (book == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var editOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // 3.26.9：同框层级。1=菜单，2=移动到分组，3=导出书籍。
    // 编辑书籍与删除是居中 Dialog，仍走 pendingMenuAction：等面板退场后再开。
    var sheetLevel by remember { mutableIntStateOf(1) }
    var pendingSheetAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var groupPickerOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var addChapter by remember { mutableStateOf(false) }
    var storyOpen by remember { mutableStateOf(false) }
    var chapterMenu by remember { mutableStateOf<Chapter?>(null) }
    var renameChapter by remember { mutableStateOf<Chapter?>(null) }
    var deleteChapter by remember { mutableStateOf<Chapter?>(null) }
    BackHandler { onBack() }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DetailTopBar(book.title, onBack, onMore = { menuOpen = true })
            }
            item {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    BookCover(book, Modifier.width(132.dp).aspectRatio(.68f), radius = 18f)
                    Spacer(Modifier.width(17.dp))
                    Column(Modifier.weight(1f).padding(bottom = 3.dp)) {
                        Txt(book.genre.ifBlank { "小说" }, size = 9.5f, color = ui.palette.muted, letterSpacing = 1.2f)
                        Spacer(Modifier.height(5.dp))
                        Txt(book.title, size = 26f, weight = FontWeight.SemiBold, lineHeight = 32f, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Txt(book.author.ifBlank { "未署名" }, size = 10.5f, color = ui.palette.muted)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            MiniStat("${book.chapters.size}", "章")
                            MiniStat(formatWords(book.words), "字")
                            MiniStat(relativeTime(book.updatedAt), "更新")
                        }
                    }
                }
            }
            if (book.synopsis.isNotBlank()) {
                item {
                    MoyuSurface(Modifier.fillMaxWidth(), radius = 22f, flat = true) {
                        Txt(
                            book.synopsis,
                            size = 11.5f,
                            color = ui.palette.muted,
                            lineHeight = 19f,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        "继续写作",
                        {
                            val chapter = book.lastChapter() ?: book.chapters.firstOrNull()
                            if (chapter != null) onOpenChapter(book.id, chapter.id)
                        },
                        Modifier.weight(1f),
                        glyph = Glyph.Edit,
                    )
                    GlassButton("导出", { exportOpen = true }, Modifier.width(104.dp), glyph = Glyph.Export)
                }
            }
            item {
                // 3.26.16：故事圣经入口。角色 / 关系 / 时间线 / 大纲都是「这本书的」，
                // 所以挂在书籍详情而不是新开一个 tab；空着也常驻，点进去是带引导的空态。
                val bible = book.bible
                MoyuSurface(Modifier.fillMaxWidth(), radius = 20f, flat = true) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { storyOpen = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Txt("故事", size = 15f, weight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Txt(
                                "${bible.characters.size} 位角色 · ${bible.relations.size} 条关系 · " +
                                    "${bible.events.size} 个事件 · ${bible.outline.size} 章大纲",
                                size = 10.5f,
                                color = ui.palette.muted,
                            )
                        }
                        LineIcon(Glyph.Chevron, ui.palette.muted, 16.dp, 1.4f)
                    }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                SectionTitle(
                    "目录",
                    "${book.chapters.size} 章 · 每章都会自动成为电子书目录",
                    trailing = {
                        GlassIconButton(Glyph.Plus, { addChapter = true })
                    }
                )
            }
            items(book.chapters, key = { it.id }) { chapter ->
                ChapterRow(
                    index = book.chapters.indexOf(chapter) + 1,
                    chapter = chapter,
                    onClick = { onOpenChapter(book.id, chapter.id) },
                    onMore = { chapterMenu = chapter },
                )
            }
            item {
                AddChapterRow { addChapter = true }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (menuOpen) {
        InlineModalSheet(onDismiss = {
            menuOpen = false
            sheetLevel = 1
            val action = pendingSheetAction
            pendingSheetAction = null
            action?.invoke()
        }) { close ->
            AnimatedContent(
                targetState = sheetLevel,
                transitionSpec = {
                    val forward = targetState > initialState
                    // 3.26.18：整宽位移。w/3 时两层全程重叠，旧层会从新层纸底露出来。
                    (slideInHorizontally(spring(MOYU_DAMPING, 380f)) { w ->
                        if (forward) w else -w
                    }) togetherWith
                        (slideOutHorizontally(spring(MOYU_DAMPING, 380f)) { w ->
                            if (forward) -w else w
                        })
                },
                label = "bookSheet",
            ) { step ->
                when (step) {
                    1 -> SheetPane {
                        DialogTitle("书籍设置", book.title, onClose = { close() })
                        Spacer(Modifier.height(14.dp))
                        SettingRow("编辑书籍", "书名、作者、封面和简介", Glyph.Edit, onClick = {
                            pendingSheetAction = { editOpen = true }
                            close()
                        })
                        RowDivider()
                        SettingRow(
                            "移动到分组",
                            store.bookGroups.firstOrNull { it.id == book.groupId }?.name ?: "未分组",
                            Glyph.Layers,
                            onClick = { sheetLevel = 2 },
                        )
                        RowDivider()
                        SettingRow("导出书籍", "TXT / EPUB，自动生成目录", Glyph.Export, onClick = {
                            sheetLevel = 3
                        })
                        RowDivider()
                        SettingRow("置顶到书架", if (book.pinned) "已置顶" else "按更新时间排序", Glyph.Pin, trailing = {
                            InkToggle(book.pinned) { on ->
                                store.updateBook(book.id) { it.copy(pinned = on) }
                                ToastBus.show(if (on) "已置顶" else "已取消置顶")
                            }
                        })
                        RowDivider()
                        SettingRow("删除书籍", "章节和正文会一并删除", Glyph.Trash, onClick = {
                            pendingSheetAction = { deleteOpen = true }
                            close()
                        })
                    }
                    2 -> SheetPane {
                        GroupPickerSheet(
                            kind = GroupKind.BOOK,
                            current = book.groupId,
                            store = store,
                            onSelect = { group -> store.setBookGroup(book.id, group) },
                            onDismiss = { close() },
                            embedded = true,
                            onBack = { sheetLevel = 1 },
                        )
                    }
                    else -> SheetPane {
                        ExportSheet(
                            book = book,
                            onDismiss = { close() },
                            embedded = true,
                            onBack = { sheetLevel = 1 },
                        )
                    }
                }
            }
        }
    }
    if (editOpen) {
        BookEditorDialog(
            existing = book,
            groups = store.bookGroups,
            initialGroupId = book.groupId,
            onDismiss = { editOpen = false },
            onSubmit = { updated ->
                store.updateBook(book.id) {
                    it.copy(
                        title = updated.title,
                        author = updated.author,
                        genre = updated.genre,
                        synopsis = updated.synopsis,
                        coverStyle = updated.coverStyle,
                        coverUri = updated.coverUri,
                        groupId = updated.groupId,
                    )
                }
                editOpen = false
                ToastBus.show("书籍信息已更新")
            },
        )
    }
    if (storyOpen) {
        StorySheet(
            book = book,
            onBible = { next -> store.updateBook(book.id) { it.copy(bible = next) } },
            onOpenChapter = { chapterId ->
                storyOpen = false
                onOpenChapter(book.id, chapterId)
            },
            onDismiss = { storyOpen = false },
        )
    }
    if (groupPickerOpen) {
        GroupPickerSheet(
            kind = GroupKind.BOOK,
            current = book.groupId,
            store = store,
            onSelect = { group -> store.setBookGroup(book.id, group) },
            onDismiss = { groupPickerOpen = false },
        )
    }
    if (deleteOpen) {
        ConfirmDialog(
            "删除《${book.title}》？",
            "这本书的 ${book.chapters.size} 个章节会从本机移除，且无法恢复。导出的文件不会受影响。",
            "删除",
            {
                store.deleteBook(book.id)
                deleteOpen = false
                onBack()
                ToastBus.show("书籍已删除")
            },
            { deleteOpen = false },
            destructive = true,
        )
    }
    if (exportOpen) {
        ExportSheet(book, onDismiss = { exportOpen = false })
    }
    if (addChapter) {
        ChapterDialog(
            initial = chapterFallbackTitle(book.chapters.size),
            title = "新建章节",
            confirm = "添加",
            onDismiss = { addChapter = false },
            onSubmit = { name ->
                val chapter = Chapter(title = name)
                store.addChapter(book.id, chapter)
                addChapter = false
                onOpenChapter(book.id, chapter.id)
            },
        )
    }
    if (chapterMenu != null) {
        val chapter = chapterMenu!!
        // 3.26.10：章节菜单改用同窗口面板，进出与笔记夹/书籍菜单同一套。
        // 重命名与删除是居中 Dialog，等面板退场后再开（pending）。
        var pending by remember(chapter.id) { mutableStateOf<(() -> Unit)?>(null) }
        InlineModalSheet(onDismiss = {
            chapterMenu = null
            val action = pending
            pending = null
            action?.invoke()
        }) { close ->
            DialogTitle(
                chapter.title,
                "${formatWords(countWords(chapter.content))} 字 · ${relativeTime(chapter.updatedAt)}",
                onClose = { close() },
            )
            Spacer(Modifier.height(12.dp))
            SettingRow("重命名章节", "目录和电子书会同步更新", Glyph.Edit, onClick = {
                pending = { renameChapter = chapter }
                close()
            })
            RowDivider()
            SettingRow("删除章节", "这一章正文会一并删除", Glyph.Trash, onClick = {
                if (book.chapters.size <= 1) {
                    ToastBus.show("至少保留一章，可以先新建一章")
                    close()
                } else {
                    pending = { deleteChapter = chapter }
                    close()
                }
            })
        }
    }
    if (renameChapter != null) {
        val chapter = renameChapter!!
        ChapterDialog(
            initial = chapter.title,
            title = "重命名章节",
            confirm = "保存",
            onDismiss = { renameChapter = null },
            onSubmit = { name ->
                store.updateChapter(book.id, chapter.id, title = name)
                renameChapter = null
                ToastBus.show("章节名已更新")
            },
        )
    }
    if (deleteChapter != null) {
        val chapter = deleteChapter!!
        ConfirmDialog(
            "删除「${chapter.title}」？",
            "这一章的 ${formatWords(countWords(chapter.content))} 字正文会一并删除，无法恢复。",
            "删除章节",
            {
                store.deleteChapter(book.id, chapter.id)
                deleteChapter = null
                ToastBus.show("章节已删除")
            },
            { deleteChapter = null },
            destructive = true,
        )
    }
}

@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit, onMore: () -> Unit) {
    val ui = LocalInk.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Glyph.Back, onBack, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Txt(title, size = 13f, weight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        GlassIconButton(Glyph.More, onMore, size = 40.dp)
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column {
        Txt(value, size = 12f, weight = FontWeight.Medium, maxLines = 1)
        Txt(label, size = 8f, color = LocalInk.current.palette.muted)
    }
}

@Composable
private fun ChapterRow(index: Int, chapter: Chapter, onClick: () -> Unit, onMore: () -> Unit) {
    val ui = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SmoothCorner(18.dp))
            .bouncy(scale = .985f) { onClick() }
            .padding(start = 8.dp, end = 3.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Txt(if (index < 10) "0$index" else "$index", size = 9.5f, color = ui.palette.muted, letterSpacing = .5f)
        }
        Column(Modifier.weight(1f)) {
            Txt(chapter.title, size = 14f, weight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Txt(
                if (chapter.content.isBlank()) "空白章节" else "${formatWords(countWords(chapter.content))} 字 · ${relativeTime(chapter.updatedAt)}",
                size = 9f,
                color = ui.palette.muted,
            )
        }
        Box(Modifier.size(38.dp).bouncy { onMore() }, contentAlignment = Alignment.Center) {
            LineIcon(Glyph.More, ui.palette.muted, 17.dp)
        }
    }
    RowDivider(inset = 40.dp)
}

@Composable
private fun AddChapterRow(onClick: () -> Unit) {
    val ui = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SmoothCorner(18.dp))
            .bouncy(scale = .985f) { onClick() }
            .padding(horizontal = 12.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(ui.palette.faint),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Glyph.Plus, ui.palette.ink, 15.dp) }
        Spacer(Modifier.width(11.dp))
        Txt("添加章节", size = 12.5f, color = ui.palette.muted)
        Spacer(Modifier.weight(1f))
        Txt("自动写入目录", size = 8.5f, color = ui.palette.muted.copy(alpha = .7f))
    }
}

@Composable
fun ChapterDialog(
    initial: String,
    title: String,
    confirm: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val ui = LocalInk.current
    var text by remember { mutableStateOf(initial) }
    GlassDialog(onDismiss) {
        DialogTitle(title, "像「第七章 雨停之后」这样的名字，阅读器会自动识别。", onClose = onDismiss)
        Spacer(Modifier.height(16.dp))
        InkField(text, { text = it.take(40) }, "章节名称", textSize = 16f, onDone = {
            if (text.isNotBlank()) onSubmit(text.trim())
        })
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassButton("取消", onDismiss, Modifier.weight(1f))
            PrimaryButton(confirm, {
                if (text.isBlank()) ToastBus.show("章节名不能为空") else onSubmit(text.trim())
            }, Modifier.weight(1f))
        }
    }
}


