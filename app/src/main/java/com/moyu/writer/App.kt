package com.moyu.writer

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

sealed class Screen(val depth: Int, val tabIndex: Int? = null) {
    data object Library : Screen(0, 0)
    data object Notes : Screen(0, 1)
    data object Stats : Screen(0, 2)
    data object Settings : Screen(0, 3)
    data class BookDetail(val bookId: String) : Screen(1)
    data class ChapterEditor(val bookId: String, val chapterId: String) : Screen(2)
    data class NoteEditor(val noteId: String) : Screen(1)
    data class SettingPage(val page: String) : Screen(1)
}

class MainActivity : ComponentActivity() {
    private var noteFromIntent by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableHighRefresh()
        enableEdgeToEdge()
        noteFromIntent = intent?.getStringExtra("open_note")
        val store = (application as MoyuApplication).store
        val systemNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val night = when (store.settings.theme) {
            "night" -> true
            "system" -> systemNight
            else -> false
        }
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(
                paletteFor(store.settings.theme, night).bgTop.toArgb()
            )
        )
        setContent {
            MoyuTheme(store.data.settings) {
                CompositionLocalProvider(LocalStore provides store) {
                    MoyuRoot(store, noteFromIntent) { noteFromIntent = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        noteFromIntent = intent.getStringExtra("open_note")
    }

    override fun onResume() {
        super.onResume()
        enableHighRefresh()
    }

    override fun onPause() {
        super.onPause()
        (application as MoyuApplication).store.flush()
    }
}

@Composable
private fun MoyuRoot(store: AppStore, initialNote: String?, consumeInitialNote: () -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    val context = LocalContext.current
    val activity = context as? Activity
    val haze = remember { HazeBridge.newHandle() }
    val hazeSupported = HazeBridge.isSupported()
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Library)) }
    // Keep the settings root list state at the navigation host level. It used to
    // live inside SettingsScreen, so pushing a SettingPage destroyed the
    // composable and returning always recreated the LazyColumn at item 0.
    val settingsListState = rememberLazyListState()
    val current = stack.lastOrNull() ?: Screen.Library
    val rootVisible = current.depth == 0

    LaunchedEffect(Unit) {
        if (store.data.settings.autoUpdate && BuildConfig.UPDATE_REPO.isNotBlank()) {
            val info = UpdateChecker.latest(BuildConfig.UPDATE_REPO) ?: return@LaunchedEffect
            if (UpdateChecker.isNewer(info.tag, BuildConfig.VERSION_NAME)) {
                ToastBus.show("发现新版本 ${info.tag}，可在关于页检查更新", long = true)
            }
        }
    }

    LaunchedEffect(initialNote) {
        val noteId = initialNote ?: return@LaunchedEffect
        if (store.data.notes.any { it.id == noteId }) {
            if (current !is Screen.NoteEditor || current.noteId != noteId) {
                stack = stack + Screen.NoteEditor(noteId)
            }
        }
        consumeInitialNote()
    }

    BackHandler(enabled = current.depth == 0) {
        if (current != Screen.Library) stack = listOf(Screen.Library) else activity?.finish()
    }

    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    DisposableEffect(lifecycleOwner, current) {
        val writing = current is Screen.ChapterEditor || current is Screen.NoteEditor
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> activity?.keepAwakeWhileWriting(writing)
                Lifecycle.Event.ON_PAUSE -> activity?.keepAwakeWhileWriting(false)
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        if (lifecycleOwner?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
            activity?.keepAwakeWhileWriting(writing)
        }
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            activity?.keepAwakeWhileWriting(false)
        }
    }

    Box(Modifier.fillMaxSize().background(p.bgBottom)) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val motion = store.data.settings.motion
                val duration = when (motion) {
                    "none" -> 0
                    "snappy" -> 210
                    "gentle" -> 420
                    else -> 300
                }
                val targetTab = targetState.tabIndex
                val initialTab = initialState.tabIndex
                val forward = if (targetTab != null && initialTab != null) {
                    targetTab > initialTab
                } else {
                    targetState.depth > initialState.depth
                }
                when (store.data.settings.pageMotion) {
                    "fade" -> fadeIn(tween(duration, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween((duration * .7f).toInt()))
                    "scale" -> (fadeIn(tween(duration)) + scaleIn(initialScale = .965f, animationSpec = tween(duration, easing = FastOutSlowInEasing))) togetherWith
                        (fadeOut(tween((duration * .7f).toInt())) + scaleOut(targetScale = 1.012f, animationSpec = tween(duration)))
                    else -> if (forward) {
                        (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween((duration * .8f).toInt()))) togetherWith
                            (slideOutHorizontally(tween(duration)) { -it / 10 } + fadeOut(tween((duration * .6f).toInt())))
                    } else {
                        (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it / 6 } + fadeIn(tween((duration * .8f).toInt()))) togetherWith
                            (slideOutHorizontally(tween(duration)) { it / 5 } + fadeOut(tween((duration * .6f).toInt())))
                    }
                }
            },
            label = "screen",
            modifier = if (hazeSupported) HazeBridge.source(Modifier.fillMaxSize(), haze) else Modifier.fillMaxSize(),
        ) { screen ->
            CompositionLocalProvider(LocalHazeHandle provides if (hazeSupported) haze else null) {
            when (screen) {
                Screen.Library -> LibraryScreen(
                    store = store,
                    onOpenBook = { stack = stack + Screen.BookDetail(it) },
                    onContinue = { book, chapter -> stack = stack + Screen.ChapterEditor(book.id, chapter.id) },
                    onCreateBook = { book -> stack = stack + Screen.BookDetail(book.id) },
                )
                Screen.Notes -> NotesScreen(
                    store = store,
                    onOpenNote = { stack = stack + Screen.NoteEditor(it) },
                    onCreateNote = { stack = stack + Screen.NoteEditor(it) },
                )
                Screen.Stats -> StatsScreen(store)
                Screen.Settings -> SettingsScreen(store, settingsListState) { page ->
                    stack = stack + Screen.SettingPage(page)
                }
                is Screen.BookDetail -> BookDetailScreen(
                    store = store,
                    bookId = screen.bookId,
                    onBack = { stack = stack.dropLast(1) },
                    onOpenChapter = { bookId, chapterId -> stack = stack + Screen.ChapterEditor(bookId, chapterId) },
                )
                is Screen.ChapterEditor -> BookEditorScreen(
                    store = store,
                    bookId = screen.bookId,
                    chapterId = screen.chapterId,
                    onBack = { stack = stack.dropLast(1) },
                    onOpenBook = { stack = stack.dropLast(1) },
                )
                is Screen.NoteEditor -> NoteEditorScreen(
                    store = store,
                    noteId = screen.noteId,
                    onBack = { stack = stack.dropLast(1) },
                )
                is Screen.SettingPage -> SettingsDetailScreen(
                    store = store,
                    page = screen.page,
                    onBack = { stack = stack.dropLast(1) },
                )
            }
            }
        }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 5.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            p.bgTop.copy(alpha = .97f),
                            p.bgTop.copy(alpha = .86f),
                            Color.Transparent,
                        )
                    )
                )
        )

        AnimatedVisibility(
            visible = rootVisible,
            enter = slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring(.86f, 340f)) + fadeIn(tween(180)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            MoyuTabBar(
                current = when (current) {
                    Screen.Notes -> 1
                    Screen.Stats -> 2
                    Screen.Settings -> 3
                    else -> 0
                },
                haze = if (hazeSupported) haze else null,
                onSelect = { index ->
                    stack = listOf(
                        when (index) {
                            1 -> Screen.Notes
                            2 -> Screen.Stats
                            3 -> Screen.Settings
                            else -> Screen.Library
                        }
                    )
                },
            )
        }

        ToastLayer(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (rootVisible) 112.dp else 104.dp)
                .navigationBarsPadding()
        )

        if (!ui.settings.onboardingDone) {
            OnboardingOverlay {
                store.updateSettings { it.copy(onboardingDone = true) }
            }
        }

        if (ui.settings.showFps) {
            FpsMeter(
                Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 17.dp, bottom = if (rootVisible) 96.dp else 112.dp)
            )
        }
    }
}

@Composable
internal fun MoyuTabBar(current: Int, haze: HazeBridge.Handle? = null, onSelect: (Int) -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    val s = ui.settings
    val names = (s.tabNames + listOf("书架", "笔记", "数据", "设置")).take(4)
    val icons = listOf(Glyph.Book, Glyph.Note, Glyph.Chart, Glyph.Gear)
    val height = if (s.showTabLabels) 64.dp else 54.dp
    val barCorner = s.materialFor(s.theme, SurfaceRole.BAR).corner
    val pillCorner = (barCorner - 6f).coerceAtLeast(0f).dp
    var tabOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val backdrop by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, haze, current) {

        if (haze == null) return@produceState
        // Freeze a backdrop from Haze's source layer. If the layer is not ready
        // yet, retry after the transition settles instead of showing a flash.
        repeat(12) {
            kotlinx.coroutines.delay(80)
            val layer = HazeBridge.contentLayer(haze) as? androidx.compose.ui.graphics.layer.GraphicsLayer
            if (layer != null) {
                val image = runCatching { layer.toImageBitmap() }.getOrNull()
                if (image != null) {
                    value = image
                    return@produceState
                }
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 13.dp, vertical = 8.dp)
            .onGloballyPositioned { tabOrigin = it.positionInWindow() }
    ) {
        MoyuSurface(
            SurfaceRole.BAR,
            Modifier
                .fillMaxWidth()
                .height(height),
            radius = barCorner,
        ) {
            LiquidLensOverlay(
                Modifier.matchParentSize(),
                radius = barCorner.dp,
                tint = p.paper,
                backdrop = backdrop,
                sourceOffset = tabOrigin,
            )
            BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
                val itemWidth = maxWidth / 4
                val target = itemWidth * current
                val x by animateDpAsState(target, motionSpecDp(s.motion), label = "tabPill")
                Box(
                    Modifier
                        .offset(x = x)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(pillCorner))
                        .background(p.ink)
                )
                Row(Modifier.fillMaxSize()) {
                    names.take(4).forEachIndexed { index, name ->
                        val active = index == current
                        val fg by animateColorAsState(
                            if (active) p.bgTop else p.muted,
                            animationSpec = tween(220),
                            label = "tabFg",
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(pillCorner))
                                .bouncy(scale = .93f, hapticType = 1) { onSelect(index) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            LineIcon(icons[index], fg, if (active) 20.dp else 19.dp)
                            if (s.showTabLabels) {
                                Spacer(Modifier.height(3.dp))
                                Txt(
                                    name.ifBlank { listOf("书架", "笔记", "数据", "设置")[index] }.take(4),
                                    size = 8.5f,
                                    color = fg,
                                    weight = if (active) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
