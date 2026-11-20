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
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    data object Ai : Screen(0, 3)
    data object Settings : Screen(0, 4)
    data class BookDetail(val bookId: String) : Screen(1)
    data class ChapterEditor(val bookId: String, val chapterId: String) : Screen(2)
    /** 笔记夹里的一页；folderId 为 null 是「未归类」。 */
    data class NoteFolder(val folderId: String?) : Screen(1)
    data class NoteEditor(val noteId: String) : Screen(2)
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
        // 教练式引导：锚点表 + 当前组放在 Activity 层，页面与引导层共用同一份。
        // Manifest 里 configChanges 已兜住旋转/昼夜，Activity 不会因此重建；进程重建时
        // 未完成的组会重新弹（tutorialDone 只在走完/退出时才写）。
        val tutorialRegistry = TutorialRegistry()
        val tutorial = TutorialController { groupId ->
            store.updateSettings { s ->
                s.copy(
                    tutorialDone = (s.tutorialDone + groupId).distinct(),
                    // 旧标记保留语义：走完上手 6 步就等于看过首次引导。
                    onboardingDone = if (groupId == TutorialGroups.LANDING) true else s.onboardingDone,
                )
            }
        }
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
                CompositionLocalProvider(
                    LocalStore provides store,
                    LocalTutorialRegistry provides tutorialRegistry,
                    LocalTutorialController provides tutorial,
                ) {
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

@OptIn(ExperimentalLayoutApi::class)
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
    // 长按标签栏拖动镜片时的连续位置（可见页序列），松手回 null。
    // 页面与镜片读同一个值，所以「镜片到哪、页面跟到哪」不会脱位。
    // 长按拖动镜片时的连续位置。传 State 而不是拆包值：TabPager 在绘制阶段
    // 读它，拖动时不再每帧重组整页（3.26.9 修「长按拖动很卡」）。
    val tabDrag = remember { mutableStateOf<Float?>(null) }
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
    // 同窗口面板（InlineModalSheet）存活期间收起 TabBar：面板是页面内的层，
    // TabBar 是 root 的兄弟节点、画在页面之上，不收面板底部会被盖住、点不到。
    var sheetSuppressed by remember { mutableStateOf(false) }
    val suppressSheet: (Boolean) -> Unit = remember { { v -> sheetSuppressed = v } }
    // 兜底：根 tab 变化时强制复位。风险场景 = 面板打开中按系统返回、栈被重置回书架，
    // 而所有根 tab 页常驻组合（TabPager.kt:106），面板所在页不会 dispose → suppress 只置位不复位，
    // TabBar 会永久消失。TabBar 被 suppress 时用户无法切 tab，所以这里只会被「返回重置栈」触发。
    LaunchedEffect(rootTab) { sheetSuppressed = false }

    // 教练式引导：控制器与锚点表由 MainActivity 的 CompositionLocal 提供。
    val tutorial = LocalTutorialController.current
    val tutorialRegistry = LocalTutorialRegistry.current
    LaunchedEffect(tutorial, store) {
        if (tutorial == null) return@LaunchedEffect
        // 等底层页面完成首帧再起：锚点在 onGloballyPositioned 里注册，早一帧会被当成目标不存在。
        withFrameNanos { }
        withFrameNanos { }
        val s = store.data.settings
        // 首次启动（没有 onboardingDone、也没教过 tutorial.landing）才弹上手 6 步。
        // 老用户 onboardingDone=true 但 tutorialDone 缺省为空：不重播上手，
        // 页内首次教学由后续接线按各自的组 id 触发。
        if (!s.onboardingDone && TutorialGroups.LANDING !in s.tutorialDone) {
            tutorial.start(LANDING_TUTORIAL)
        }
    }

    // 3.26.54：设置 → 关于 →「重看功能引导」点了当场播放，不用清后台重进。
    // 那一行只清 tutorialDone / onboardingDone（写设置），而上面那段「要不要起引导」的检查
    // 只在根组合期跑一次，所以这里再挂一个信号：Settings 侧 TutorialSignal.bump()，
    // 版本号一变就立刻起同一组 PPT（结束 / 跳过的写回路径与首启完全一样）。
    LaunchedEffect(tutorial) {
        if (tutorial == null) return@LaunchedEffect
        // 以开始收集时的版本号为基线：Activity 重建（例如改系统字号）后重新组合时，
        // 上次已经处理过的 bump 不会被当成新请求把引导再播一遍。
        var seen = TutorialSignal.version
        snapshotFlow { TutorialSignal.version }.collect { v ->
            if (v != seen) {
                seen = v
                tutorial.start(LANDING_TUTORIAL)
            }
        }
    }

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
            // In-page glass (the AI input row and its chips) samples the same
            // recorded page layer as the tab bar, so both read the same pixels.
            androidx.compose.runtime.CompositionLocalProvider(
                LocalGlassBackdrop provides tabBarBackdrop,
                LocalSheetSuppressed provides suppressSheet,
            ) {
// The tab pager is the bottom layer of the depth stack and is kept alive
// while a detail page sits on top of it: pushed pages wait one screen to the
// right, the pager waits one screen to the left, and going back slides the
// same pager into view. It used to be replaced by the detail itself on the
// leaving layer (rendering `current`), so the incoming page was composed
// twice and both copies slid over each other.
val rootTabs: Any = RootTabsLayer
val rootTab = stack.firstOrNull { it.depth == 0 }?.tabIndex ?: 0
// 底部可以关掉「书架 / 笔记 / 数据 / 问书」中的任意几页；设置永远在。
// key 与 Screen.tabIndex 一一对应，顺序固定，关掉后不重排。
val tabKeys = listOf("library", "notes", "stats", "ai", "settings")
val tabScreens = listOf(Screen.Library, Screen.Notes, Screen.Stats, Screen.Ai, Screen.Settings)
val hiddenTabs = ui.settings.hiddenTabs.toSet()
val visibleTabs = tabKeys.indices.filter { it == 4 || tabKeys[it] !in hiddenTabs }
val visibleScreens = visibleTabs.map { tabScreens[it] }
val rootIndex = visibleScreens.indexOfFirst { it.tabIndex == rootTab }.coerceAtLeast(0)
// 正停在被关掉的那页时自动回退到第一个可见页，否则页面和底栏会对不上。
LaunchedEffect(visibleTabs) {
    if (rootTab !in visibleTabs) {
        stack = listOf(visibleScreens.firstOrNull() ?: Screen.Settings)
    }
}
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
            index = rootIndex,
            pageCount = visibleScreens.size,
            perPageMs = perPage,
            motion = store.data.settings.pageMotion,
            dragOffset = tabDrag,
        ) { i ->
            CompositionLocalProvider(LocalHazeHandle provides if (hazeSupported) haze else null) {
                when (visibleScreens[i]) {
                    Screen.Library -> LibraryScreen(
                        store = store,
                        onOpenBook = { stack = stack + Screen.BookDetail(it) },
                        onContinue = { book, chapter ->
                            stack = stack + Screen.ChapterEditor(book.id, chapter.id)
                        },
                        onCreateBook = { book -> stack = stack + Screen.BookDetail(book.id) },
                    )
                    Screen.Notes -> NotesScreen(
                        store = store,
                        onOpenFolder = { folderId -> stack = stack + Screen.NoteFolder(folderId) },
                        onCreateNote = { noteId -> stack = stack + Screen.NoteEditor(noteId) },
                    )
                    Screen.Stats -> StatsScreen(store)
                    Screen.Ai -> AiScreen(
                        store = store,
                        onOpenChapter = { bookId, chapterId ->
                            stack = stack + Screen.ChapterEditor(bookId, chapterId)
                        },
                        onOpenBook = { bookId -> stack = stack + Screen.BookDetail(bookId) },
                        onOpenNote = { noteId -> stack = stack + Screen.NoteEditor(noteId) },
                    )
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
                    onOpenFolder = { folderId -> stack = stack + Screen.NoteFolder(folderId) },
                    onCreateNote = { noteId -> stack = stack + Screen.NoteEditor(noteId) },
                )
                Screen.Stats -> StatsScreen(store)
                Screen.Ai -> AiScreen(
                    store = store,
                    onOpenChapter = { bookId, chapterId ->
                        stack = stack + Screen.ChapterEditor(bookId, chapterId)
                    },
                    onOpenBook = { bookId -> stack = stack + Screen.BookDetail(bookId) },
                    onOpenNote = { noteId -> stack = stack + Screen.NoteEditor(noteId) },
                )
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
                is Screen.NoteFolder -> NoteFolderScreen(
                    store = store,
                    folderId = screen.folderId,
                    folderName = screen.folderId?.let { id ->
                        store.noteGroups.firstOrNull { it.id == id }?.name
                    } ?: "未归类",
                    onBack = { pop() },
                    onOpenNote = { noteId -> stack = stack + Screen.NoteEditor(noteId) },
                    onCreateNote = { noteId -> stack = stack + Screen.NoteEditor(noteId) },
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
                    onOpen = { p -> stack = stack + Screen.SettingPage(p) },
                )
            }
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
            // 面板打开时一起收起：面板内的底部行必须点得到（3.26.9）。
            visible = rootVisible && !sheetSuppressed,
            enter = slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring(MOYU_DAMPING, 380f)) + fadeIn(tween(180)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 自检示例锚点：页面侧照这个写法，把要指的控件用 Modifier.tutorialAnchor(id)
                // 登记到 TutorialRegistry；Lead 后续把真实锚点逐个加到页面里。
                .tutorialAnchor("app.tabBar"),
        ) {
            // 同一套规则就近再算一次：设置永远在，其余按开关过滤，顺序固定。
            val barKeys = listOf("library", "notes", "stats", "ai", "settings")
            val barHidden = ui.settings.hiddenTabs.toSet()
            val barVisible = barKeys.indices.filter { it == 4 || barKeys[it] !in barHidden }
            val barScreens = barVisible.map {
                listOf(Screen.Library, Screen.Notes, Screen.Stats, Screen.Ai, Screen.Settings)[it]
            }
            val barPos = barScreens.indexOfFirst {
                it.tabIndex == (stack.firstOrNull { s -> s.depth == 0 }?.tabIndex ?: 0)
            }.coerceAtLeast(0)
            MoyuTabBar(
                current = barPos,
                visible = barVisible,
                onSelect = { index ->
                    stack = listOf(barScreens.getOrElse(index) { Screen.Settings })
                },
                onDrag = { tabDrag.value = it },
                backdrop = tabBarBackdrop,
            )
        }

        androidx.compose.runtime.CompositionLocalProvider(
            LocalGlassBackdrop provides tabBarBackdrop
        ) {
            ToastLayer(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 20.dp, end = 20.dp)
            )
        }

        if (ui.settings.showFps) {
            FpsMeter(
                Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 17.dp, bottom = if (rootVisible) 96.dp else 112.dp)
            )
        }

        // 教练式引导画在最上层：盖住 TabBar、Toast 和 FPS，蒙层自己吃触摸。
        if (tutorial != null && tutorialRegistry != null) {
            val activeTutorial = tutorial.current
            if (activeTutorial != null) {
                TutorialOverlay(
                    group = activeTutorial,
                    registry = tutorialRegistry,
                    onFinished = {
                        // 3.26.57：重看入口是在设置页之上起全屏 PPT，结束时栈底仍是设置页，
                        // 引导一散就读成「重看把人留在了设置里」。收尾统一回书架 tab，
                        // 与首启落点一致（和根 tab 返回键同一个写法，走既有 tab 转场规则）。
                        // 首启时栈底本来就是 Library：列表结构相等，不写状态、不会多一次转场。
                        stack = listOf(Screen.Library)
                        tutorial.finish()
                    },
                )
            }
        }
    }
}
