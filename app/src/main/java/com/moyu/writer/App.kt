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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

// Stable identity of the tab-pager layer at the bottom of the depth stack.
// It outlives every push/pop, otherwise a recreated pager would start one
// screen to the right and slide in over the page it is supposed to reveal.
private const val RootTabsLayer = "root-tabs"

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
    // The page is recorded into a layer the floating tab bar samples. Backdrop
    // owns the layer and the glass effect; the tab bar never samples the window
    // buffer, so its own icons cannot appear inside the capsule as ghosts.
    val tabBarBackdrop = rememberLayerBackdrop()
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Library)) }
    // Rapid back presses used to start a second page transition before the
    // first one had finished, so the same detail page was composed twice and
    // both copies slid off together. One pop per transition window keeps a
    // single outgoing page on stage.
    var lastPopAt by remember { mutableLongStateOf(0L) }
    // The window must be longer than the transition it guards, otherwise the
    // second pop lands while the first page is still leaving and AnimatedContent
    // composes the same page twice. Durations are the ones transitionSpec uses.
    fun popWindowMs(): Long = when (store.data.settings.motion) {
        "none" -> 80L
        "snappy" -> 340L
        "gentle" -> 600L
        else -> 460L
    }
    // Presses inside the window are queued, not dropped: the page still leaves
    // only after the previous transition finished, but the tap is never
    // swallowed, so the button does not feel laggy.
    val navHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    // Immediate, always. Any queueing here is felt as input lag; the duplicate
    // page is prevented in the transition instead (a back transition has no
    // outgoing content, so nothing can trail behind).
    val pop: () -> Unit = {
        if (stack.size > 1) stack = stack.dropLast(1)
    }
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
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastPopAt > popWindowMs()) {
            lastPopAt = now
            if (current != Screen.Library) stack = listOf(Screen.Library) else activity?.finish()
        }
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

    // The Haze source has to cover the TabBar too. It used to sit on the
    // AnimatedContent only, so the TabBar — a sibling of that content — sampled
    // a source it was not inside and its 模糊 slider did nothing at all.
    Box(
        Modifier
            .fillMaxSize()
            .background(p.bgBottom)
            .then(if (hazeSupported) HazeBridge.source(Modifier, haze) else Modifier),
    ) {
        // The page is recorded into its own layer, and that layer carries the
        // effect. Shading a live node sampled the window buffer, which already
        // held the bar, so the bar's icons showed up inside the capsule as
        // ghosts. A recorded layer holds page pixels only.
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(tabBarBackdrop),
        ) {
// The tab pager is the bottom layer of the depth stack and is kept alive
// while a detail page sits on top of it: pushed pages wait one screen to the
// right, the pager waits one screen to the left, and going back slides the
// same pager into view. It used to be replaced by the detail itself on the
// leaving layer (rendering `current`), so the incoming page was composed
// twice and both copies slid over each other.
val rootTabs: Any = RootTabsLayer
val rootTab = stack.firstOrNull { it.depth == 0 }?.tabIndex ?: 0
val depthScreens: List<Any> = listOf(rootTabs) + stack.filter { it.depth > 0 }
DepthStack(
    screens = depthScreens,
    perPageMs = when (store.data.settings.motion) {
        "none" -> 0
        "snappy" -> 210
        "gentle" -> 420
        else -> 300
    },
) { screenAny, _ ->
    if (screenAny == RootTabsLayer) {
        // Root tabs: four pages live side by side, the whole strip
        // slides by |Δtab| screens so 笔记 and 数据 really pass through.
        val perPage = when (store.data.settings.motion) {
            "none" -> 0
            "snappy" -> 180
            "gentle" -> 380
            else -> 280
        }
        TabPager(
            index = rootTab,
            pageCount = 4,
            perPageMs = perPage,
            motion = store.data.settings.pageMotion,
        ) { i ->
            CompositionLocalProvider(LocalHazeHandle provides if (hazeSupported) haze else null) {
                when (i) {
                    0 -> LibraryScreen(
                        store = store,
                        onOpenBook = { stack = stack + Screen.BookDetail(it) },
                        onContinue = { book, chapter ->
                            stack = stack + Screen.ChapterEditor(book.id, chapter.id)
                        },
                        onCreateBook = { book -> stack = stack + Screen.BookDetail(book.id) },
                    )
                    1 -> NotesScreen(
                        store = store,
                        onOpenNote = { stack = stack + Screen.NoteEditor(it) },
                        onCreateNote = { stack = stack + Screen.NoteEditor(it) },
                    )
                    2 -> StatsScreen(store)
                    else -> SettingsScreen(store, settingsListState) { page ->
                        stack = stack + Screen.SettingPage(page)
                    }
                }
            }
        }
        return@DepthStack
    }
    val screen = screenAny as Screen
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
                    onBack = { pop() },
                    onOpenChapter = { bookId, chapterId -> stack = stack + Screen.ChapterEditor(bookId, chapterId) },
                )
                is Screen.ChapterEditor -> BookEditorScreen(
                    store = store,
                    bookId = screen.bookId,
                    chapterId = screen.chapterId,
                    onBack = { pop() },
                    onOpenBook = { pop() },
                    onOpenNote = { noteId ->
                        // Keep the chapter underneath so back returns to writing.
                        stack = stack + Screen.NoteEditor(noteId)
                    },
                )
                is Screen.NoteEditor -> NoteEditorScreen(
                    store = store,
                    noteId = screen.noteId,
                    onBack = { pop() },
                    onOpenChapter = { bookId, chapterId ->
                        stack = stack + Screen.ChapterEditor(bookId, chapterId)
                    },
                )
                is Screen.SettingPage -> SettingsDetailScreen(
                    store = store,
                    page = screen.page,
                    onBack = { pop() },
                )
            }
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
                backdrop = tabBarBackdrop,
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
