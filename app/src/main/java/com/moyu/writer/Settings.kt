package com.moyu.writer

import androidx.compose.ui.draw.shadow
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import android.os.Build
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal val accentChoices = listOf(
    "墨黑" to 0xFF22221E,
    "茶褐" to 0xFF7A5B3A,
    "松绿" to 0xFF3F6350,
    "雾蓝" to 0xFF4D7085,
    "朱砂" to 0xFF9A4B43,
    "暮紫" to 0xFF6B5B7A,
    "琥珀" to 0xFFB67B36,
    "青瓷" to 0xFF477D7A,
)

private fun themeLabel(key: String) = when (key) {
    "mist" -> "雾蓝"
    "white" -> "纯白"
    "sepia" -> "暖褐"
    "night" -> "夜间"
    "system" -> "跟随系统"
    else -> "纸白"
}

private fun fontLabel(key: String) = when (key) {
    "sans" -> "无衬线"
    "mono" -> "等宽"
    else -> "衬线"
}

private fun layoutLabel(key: String) = when (key) {
    "compact" -> "紧凑"
    "list" -> "列表"
    else -> "网格"
}

private fun motionLabel(key: String) = when (key) {
    "gentle" -> "柔和"
    "snappy" -> "迅捷"
    "none" -> "关闭"
    else -> "灵动"
}

@Composable
fun SettingsScreen(store: AppStore, listState: LazyListState, onOpenPage: (String) -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    val s = ui.settings
    val books = store.books
    val context = LocalContext.current
    Box(androidx.compose.ui.Modifier.fillMaxSize()) {
        AmbientBackground()
        LazyColumn(
            state = listState,
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
        item {
            Column(
                androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 14.dp)
            ) {
                Txt("设置", size = 34f, weight = FontWeight.SemiBold, letterSpacing = .4f)
                Spacer(androidx.compose.ui.Modifier.height(19.dp))
            }
        }
        item {
            MoyuSurface(androidx.compose.ui.Modifier.fillMaxWidth(),
                radius = 26f,
                flat = true,
            ) {
                Row(
                    androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .bouncy(scale = .988f) { onOpenPage("space") }
                        .padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        androidx.compose.ui.Modifier
                            .size(47.dp)
                            .clip(CircleShape)
                            .background(p.ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Txt(s.appName.take(1).ifBlank { "字" }, size = 21f, color = p.bgTop, weight = FontWeight.Medium)
                    }
                    Spacer(androidx.compose.ui.Modifier.width(13.dp))
                    Column(androidx.compose.ui.Modifier.weight(1f)) {
                        Txt(s.appName, size = 17f, weight = FontWeight.SemiBold, letterSpacing = 1f)
                        Spacer(androidx.compose.ui.Modifier.height(3.dp))
                        Txt("空间名称", size = 9.5f, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    LineIcon(Glyph.Chevron, p.muted, 16.dp)
                }
            }
        }
        item {
            Spacer(androidx.compose.ui.Modifier.height(4.dp))
            SettingGroup("外观") {
                // One door only: theme, accent and type all live on this page.
                SettingRow(
                    "外观",
                    "${themeLabel(s.theme)} · ${accentChoices.firstOrNull { it.second == s.accent }?.first ?: "自定义"} · ${fontLabel(s.font)}",
                    Glyph.Sparkles,
                    onClick = { onOpenPage("appearance") },
                )
            }
        }
        item {
            SettingGroup("书架与写作") {
                SettingRow(
                    "书架陈列",
                    "${layoutLabel(s.libraryMode)} · ${s.columns} 栏 · 卡片 ${(s.cardScale * 100).roundToInt()}%",
                    Glyph.Book,
                    onClick = { onOpenPage("library") },
                )
                RowDivider()
                SettingRow(
                    "写作习惯",
                    "每日 ${s.dailyGoal} 字 · 自动保存 ${s.autoSave} ms · ${if (s.indent) "自动缩进" else "手动缩进"}",
                    Glyph.Edit,
                    onClick = { onOpenPage("writing") },
                )
                RowDivider()
                SettingRow(
                    "标签栏",
                    "${if (s.showTabLabels) "图标与文字" else "仅图标"} · ${s.tabNames.joinToString(" / ")}",
                    Glyph.Layers,
                    onClick = { onOpenPage("navigation") },
                )
            }
        }
        item {
            SettingGroup("问书") {
                SettingRow(
                    "问书 AI",
                    if (s.aiEnabled) "${s.aiModel} · 已启用 · ${if (s.aiBibleSync) "自动整理" else "手动整理"}"
                    else "未启用 · ${s.aiModel}",
                    Glyph.Sparkles,
                    onClick = { onOpenPage("ai") },
                )
            }
        }
        item {
            SettingGroup("体验") {
                SettingRow(
                    "动效",
                    "${motionLabel(s.motion)} · ${if (s.haptics) "有触感" else "无触感"}",
                    Glyph.Motion,
                    onClick = { onOpenPage("motion") },
                )
                RowDivider()
                SettingRow(
                    "提醒与通知",
                    "待办提醒 · 通知权限与系统设置",
                    Glyph.Bell,
                    onClick = { openNotificationSettings(context) },
                )
            }
        }
        item {
            SettingGroup("数据") {
                SettingRow(
                    "备份与恢复",
                    "${books.size} 本书 · ${store.notes.size} 条笔记 · 本地 JSON 备份",
                    Glyph.Download,
                    onClick = { onOpenPage("data") },
                )
                RowDivider()
                SettingRow(
                    "关于素纸",
                    BuildConfig.VERSION_NAME,
                    Glyph.Info,
                    onClick = { onOpenPage("about") },
                )
            }
        }
        item {
            Column(
                androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt("${BuildConfig.VERSION_NAME}", size = 8.5f, color = p.muted.copy(alpha = .65f), letterSpacing = 1f)
            }
        }
        }
    }
}

@Composable
fun SettingsDetailScreen(
    store: AppStore,
    page: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit = {},
) {
    when (page) {
        "appearance" -> AppearancePage(store, onBack, onOpen)
        "glass" -> GlassSettingsPage(store, onBack)
        "library" -> LibrarySettingsPage(store, onBack)
        "writing" -> WritingSettingsPage(store, onBack)
        "motion" -> MotionSettingsPage(store, onBack)
        "navigation" -> NavigationSettingsPage(store, onBack)
        "ai" -> AiSettingsPage(store, onBack, onOpen)
        "data" -> DataSettingsPage(store, onBack)
        "space" -> SpaceSettingsPage(store, onBack)
        else -> AboutPage(store, onBack)
    }
}

@Composable
private fun SettingsPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    listState: LazyListState? = null,
    pinnedBelowTitle: (@Composable () -> Unit)? = null,
    /** Overlay drawn directly under the title row, over the scrolling list.
     *  Unlike a pinned block it takes no layout space, so transient previews
     *  can sit flush against the header without leaving a gap. */
    floatingBelowHeader: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    BackHandler { onBack() }
    Box(androidx.compose.ui.Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(androidx.compose.ui.Modifier.fillMaxSize().imePadding()) {
            Row(
                androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Glyph.Back, onBack, size = 40.dp)
                Spacer(androidx.compose.ui.Modifier.width(11.dp))
                Column {
                    Txt(title, size = 20f, weight = FontWeight.SemiBold)
                    if (subtitle.isNotBlank()) {
                        Spacer(androidx.compose.ui.Modifier.height(2.dp))
                        Txt(subtitle, size = 9.5f, color = LocalInk.current.palette.muted)
                    }
                }
            }
            // A pinned block lives outside the scroller so tuning a lower slider
            // never loses sight of what is being tuned. It used to be the first
            // list item, so scrolling down pushed the preview off screen.
            if (pinnedBelowTitle != null) {
                Box(androidx.compose.ui.Modifier.padding(horizontal = 20.dp)) {
                    pinnedBelowTitle()
                }
            }
            Box(androidx.compose.ui.Modifier.weight(1f)) {
                LazyColumn(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    state = listState ?: rememberLazyListState(),
                    contentPadding = PaddingValues(start = 20.dp, top = 11.dp, end = 20.dp, bottom = 70.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    content()
                }
                floatingBelowHeader?.invoke(this)
            }
        }
    }
}

@Composable
private fun SectionCaption(text: String) {
    Txt(
        text,
        size = 10.5f,
        color = LocalInk.current.palette.muted,
        letterSpacing = 1.1f,
        modifier = androidx.compose.ui.Modifier.padding(start = 5.dp, top = 13.dp, bottom = 1.dp),
    )
}

@Composable
private fun AppearancePage(store: AppStore, onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    val ui = LocalInk.current
    val s = ui.settings
    // Preview cards are transient: they appear only while a related control is
    // being changed. A pinned card stole a fixed slice of the page forever and
    // an inline one was pushed off screen exactly when the low sliders were
    // being tuned.
    var preview by remember { mutableStateOf(0) } // 0 none, 1 text, 2 glass
    LaunchedEffect(preview) {
        if (preview != 0) {
            delay(1700)
            preview = 0
        }
    }
    SettingsPage(
        "外观",
        "主题、点缀色和字体。",
        onBack,
        // One AnimatedVisibility per card, each with fixed content: a single
        // container whose child depended on `preview` swapped to the text card
        // the moment the value reset to 0, so the text card popped in while the
        // glass card was still fading out. It sits inside the page column, right
        // under the header, so it cannot leave a gap above it.
        floatingBelowHeader = {
            val floatModifier = androidx.compose.ui.Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp)
            AnimatedVisibility(
                visible = preview == 2,
                modifier = floatModifier,
                enter = fadeIn(tween(120)) + slideInVertically(tween(160)) { -it / 4 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(160)) { -it / 4 },
            ) {
                GlassPreview(
                    barBlur = s.tabBlur,
                    barRefraction = s.tabRefraction,
                    barDispersion = s.tabDispersion,
                    barShadow = s.tabShadow,
                    barVeil = s.tabBarOpacity,
                    lensBlur = s.lensBlur,
                    lensRefraction = s.lensRefraction,
                    lensDispersion = s.lensDispersion,
                    lensShadow = s.lensShadow,
                    lensVeil = s.tabLensOpacity,
                    barEdge = s.tabEdge,
                    lensEdge = s.lensEdge,
                )
            }
            AnimatedVisibility(
                visible = preview == 1,
                modifier = floatModifier,
                enter = fadeIn(tween(120)) + slideInVertically(tween(160)) { -it / 4 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(160)) { -it / 4 },
            ) {
                AppearancePreview()
            }
        },
    ) {
        item { SectionCaption("主题") }
        item { ThemePicker(store) { preview = 1 } }
        item { SectionCaption("点缀色") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(accentChoices) { (name, value) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ColorDot(Color(value), s.accent == value) {
                            store.updateSettings { it.copy(accent = value) }
                            preview = 1
                        }
                        Spacer(androidx.compose.ui.Modifier.height(5.dp))
                        Txt(name, size = 8f, color = ui.palette.muted)
                    }
                }
            }
        }
        item {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(s.accent.toInt(), hsv)
            Column(androidx.compose.ui.Modifier.padding(top = 10.dp)) {
                Row(
                    androidx.compose.ui.Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt("自定义色相", size = 9.5f, color = ui.palette.muted)
                    Spacer(androidx.compose.ui.Modifier.weight(1f))
                    Txt("左右拖动", size = 8.5f, color = ui.palette.muted.copy(alpha = .7f))
                }
                HueSlider(hsv[0]) { hue ->
                    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, .48f, .52f)).toLong() and 0xFFFFFFFFL
                    store.updateSettings { it.copy(accent = argb) }
                    preview = 1
                }
            }
        }
        item { SectionCaption("玻璃") }
        item {
            SettingGroup {
                SettingRow("玻璃", glyph = Glyph.Layers, onClick = { onOpen("glass") })
            }
        }
        item { SectionCaption("字体") }
        item {
            Segmented(
                listOf("衬线", "无衬线", "等宽"),
                when (s.font) { "sans" -> 1; "mono" -> 2; else -> 0 },
                { index ->
                    store.updateSettings { it.copy(font = when (index) { 1 -> "sans"; 2 -> "mono"; else -> "serif" }) }
                    preview = 1
                },
                androidx.compose.ui.Modifier.fillMaxWidth(),
            )
        }
        item { SectionCaption("排版") }
        item {
            SettingGroup {
                Column {
                    SettingRow("字号", "${(17f * s.fontScale).roundToInt()} sp", Glyph.Font)
                    InkSlider(
                        s.fontScale,
                        { v -> store.updateSettings { it.copy(fontScale = (v * 100).roundToInt() / 100f) }; preview = 1 },
                        .70f, 1.80f,
                        modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp),
                    )
                }
                RowDivider()
                Column {
                    SettingRow("行距", String.format(Locale.US, "%.2f 倍", s.lineHeight), Glyph.Rows)
                    InkSlider(
                        s.lineHeight,
                        { v -> store.updateSettings { it.copy(lineHeight = (v * 100).roundToInt() / 100f) }; preview = 1 },
                        1.20f, 2.90f,
                        modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp),
                    )
                }
                RowDivider()
                Column {
                    SettingRow("页边距", "${s.pageMargin.roundToInt()} dp", Glyph.Indent)
                    InkSlider(
                        s.pageMargin,
                        { v -> store.updateSettings { it.copy(pageMargin = (v * 10).roundToInt() / 10f) }; preview = 1 },
                        8f, 52f,
                        modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
        item { SectionCaption("纸面") }
        item {
            Segmented(
                listOf("空白", "虚线", "实线"),
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
                    preview = 1
                },
                androidx.compose.ui.Modifier.fillMaxWidth(),
            )
        }
        item {
            GlassButton(
                "恢复默认外观",
                {
                    store.updateSettings {
                        it.copy(
                            theme = "paper", accent = 0xFF22221E, font = "serif", fontScale = 1f,
                            lineHeight = 1.82f, pageMargin = 22f,
                            grain = true, glow = true,
                        )
                    }
                    preview = 1
                    ToastBus.show("外观已恢复默认")
                },
                androidx.compose.ui.Modifier.fillMaxWidth(),
                glyph = Glyph.Undo,
            )
        }
    }
}

@Composable
private fun AppearancePreview() {
    val ui = LocalInk.current
    MoyuSurface(androidx.compose.ui.Modifier.fillMaxWidth(),
        radius = GlassStyle.corner,
        flat = true,
    ) {
        Column(androidx.compose.ui.Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    androidx.compose.ui.Modifier.size(5.dp).clip(CircleShape).background(ui.accent)
                )
                Spacer(androidx.compose.ui.Modifier.width(6.dp))
                Txt("实时预览", size = 8.5f, color = ui.palette.muted, letterSpacing = 1f)
                Spacer(androidx.compose.ui.Modifier.weight(1f))
                Txt("${(17f * ui.settings.fontScale).roundToInt()} / ${String.format(Locale.US, "%.2f", ui.settings.lineHeight)}", size = 8.5f, color = ui.palette.muted)
            }
            Spacer(androidx.compose.ui.Modifier.height(12.dp))
            Txt(
                "潮汐退去以后，旧城的石头还留着雨的味道。",
                size = (17f * ui.settings.fontScale).coerceIn(12f, 26f),
                lineHeight = (17f * ui.settings.fontScale * ui.settings.lineHeight).coerceIn(19f, 52f),
                fontFamily = fontFamilyFor(ui.settings.font),
                weight = FontWeight.Medium,
            )
            Spacer(androidx.compose.ui.Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    androidx.compose.ui.Modifier
                        .clip(CircleShape)
                        .background(ui.palette.ink)
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                ) { Txt("继续写", size = 9f, color = ui.palette.bgTop, weight = FontWeight.Medium) }
                Box(
                    androidx.compose.ui.Modifier
                        .clip(CircleShape)
                        .background(ui.accent.copy(alpha = .13f))
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                ) { Txt("换个色调", size = 9f, color = ui.accent, weight = FontWeight.Medium) }
            }
        }
    }
}

/**
 * Live preview for the glass knobs: a sample page slice with the real
 * liquidGlassBar() drawn over it, driven by the same values the TabBar reads.
 */
@Composable
private fun GlassPreview(
    barBlur: Float,
    barRefraction: Float,
    barDispersion: Boolean,
    barShadow: Float,
    barVeil: Float,
    lensBlur: Float,
    lensRefraction: Float,
    lensDispersion: Boolean,
    lensShadow: Float,
    lensVeil: Float,
    barEdge: Float = 1f,
    lensEdge: Float = 1f,
    showBar: Boolean = true,
    showLens: Boolean = true,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val backdrop = rememberLayerBackdrop()
    val shape = SmoothCorner(50)
    val lensTint = if (p.isDark) Color.White.copy(alpha = .08f) else p.ink.copy(alpha = .08f)
    val lensPaper = p.paper.copy(alpha = lensVeil.coerceIn(0f, 1f) * .94f)
    val lensSurface: Modifier = (
        if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    if (lensBlur > 0f) blur(lensBlur.dp.toPx())
                    if (lensRefraction > 0f) {
                        lens(
                            refractionHeight = lensRefraction.dp.toPx(),
                            refractionAmount = (lensRefraction * 1.2f).dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = lensDispersion,
                        )
                    }
                },
                highlight = null,
                shadow = { Shadow(radius = 12.dp, alpha = lensShadow) },
                innerShadow = { InnerShadow(radius = 6.dp, alpha = lensShadow + .07f) },
                onDrawSurface = {
                    drawRect(lensTint)
                    if (lensVeil > 0f) drawRect(lensPaper)
                    glassEdgeBand(shape, p, lensEdge)
                },
            )
        } else {
            Modifier.clip(shape).background(p.paper.copy(alpha = .94f))
        }
        ).border(
        .5.dp,
        if (p.isDark) Color.White.copy(alpha = .06f) else p.ink.copy(alpha = .05f),
        shape,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(SmoothCorner(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(p.paper)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .fillMaxWidth(if (i == 2) .55f else 1f)
                        .height(6.dp)
                        .background(p.ink.copy(alpha = .34f), SmoothCorner(3.dp)),
                )
                Spacer(androidx.compose.ui.Modifier.height(9.dp))
            }
        }
        if (showBar) {
            Box(
                Modifier
                    .width(210.dp)
                    .height(44.dp)
                    .liquidGlassBar(
                        backdrop = backdrop,
                        shape = shape,
                        palette = p,
                        blurDp = barBlur,
                        refractionDp = barRefraction,
                        dispersion = barDispersion,
                        shadowAlpha = barShadow,
                        veil = barVeil,
                        edgeStrength = barEdge,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (showLens) {
                    Box(
                        Modifier
                            .width(58.dp)
                            .height(34.dp)
                            .then(lensSurface),
                    )
                }
            }
        } else if (showLens) {
            Box(
                Modifier
                    .width(124.dp)
                    .height(34.dp)
                    .then(lensSurface),
            )
        }
    }
}

@Composable
private fun ThemePicker(store: AppStore, onChange: () -> Unit = {}) {
    val ui = LocalInk.current
    val s = ui.settings
    val themes = listOf("paper", "mist", "white", "sepia", "night", "system")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(themes) { key ->
            val dark = key == "night"
            val pal = paletteFor(key, dark)
            Column(
                androidx.compose.ui.Modifier
                    .width(104.dp)
                    .clip(SmoothCorner(18.dp))
                    .background(Brush.verticalGradient(listOf(pal.bgTop, pal.bgBottom)))
                    .border(
                        if (s.theme == key) 1.5.dp else .8.dp,
                        if (s.theme == key) accentLine(ui.palette, ui.accent) else ui.palette.line,
                        SmoothCorner(18.dp),
                    )
                    .bouncy(scale = .96f) {
                        store.updateSettings { it.copy(theme = key) }
                        onChange()
                    }
                    .padding(8.dp)
            ) {
                Box(
                    androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(SmoothCorner(11.dp))
                        .background(pal.paper)
                        .border(.6.dp, if (pal.isDark) Color.White.copy(alpha = .08f) else Color.Black.copy(alpha = .06f), SmoothCorner(11.dp))
                ) {
                    Column(androidx.compose.ui.Modifier.padding(8.dp)) {
                        Box(androidx.compose.ui.Modifier.width(28.dp).height(2.dp).background(pal.ink.copy(alpha = .5f)))
                        Spacer(androidx.compose.ui.Modifier.height(4.dp))
                        Box(androidx.compose.ui.Modifier.width(42.dp).height(1.5.dp).background(pal.muted.copy(alpha = .45f)))
                    }
                }
                Spacer(androidx.compose.ui.Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(themeLabel(key), size = 9.5f, color = if (pal.isDark) Color(0xFFF0EDE5) else Color(0xFF191813), weight = FontWeight.Medium)
                    Spacer(androidx.compose.ui.Modifier.weight(1f))
                    // 3.26.22：勾选统一走 SelectMark（尺寸缩到色卡适配）。
                    SelectMark(
                        checked = s.theme == key,
                        onToggle = {},
                        interactive = false,
                        size = 15.dp,
                        corner = 4.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySettingsPage(store: AppStore, onBack: () -> Unit) {
    val ui = LocalInk.current
    val s = ui.settings
    val sampleA = Book(title = "潮汐", genre = "小说", coverStyle = 2)
    val sampleB = Book(title = "旧城", genre = "悬疑", coverStyle = 4)
    SettingsPage("书架陈列", "决定书在页面上的占比，以及你怎么看它们。", onBack) {
        item {
            MoyuSurface(androidx.compose.ui.Modifier.fillMaxWidth(), radius = 24f, flat = true) {
                Row(
                    androidx.compose.ui.Modifier.fillMaxWidth().padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        BookCover(
                            sampleA,
                            androidx.compose.ui.Modifier
                                .width((58 * s.cardScale.coerceIn(.78f, 1f)).dp)
                                .height((85 * s.cardScale.coerceIn(.78f, 1f)).dp),
                            radius = s.coverRadius,
                        )
                        BookCover(
                            sampleB,
                            androidx.compose.ui.Modifier
                                .width((58 * s.cardScale.coerceIn(.78f, 1f)).dp)
                                .height((85 * s.cardScale.coerceIn(.78f, 1f)).dp),
                            radius = s.coverRadius,
                        )
                    }
                    Spacer(androidx.compose.ui.Modifier.width(15.dp))
                    Column(androidx.compose.ui.Modifier.weight(1f)) {
                        Txt("预览", size = 9f, color = ui.palette.muted, letterSpacing = 1f)
                        Spacer(androidx.compose.ui.Modifier.height(4.dp))
                        Txt("${s.columns} 栏 · ${layoutLabel(s.libraryMode)}", size = 14f, weight = FontWeight.Medium)
                        Spacer(androidx.compose.ui.Modifier.height(3.dp))
                        Txt("卡片大小 ${(s.cardScale * 100).roundToInt()}%", size = 9.5f, color = ui.palette.muted)
                    }
                }
            }
        }
        item { SectionCaption("排列") }
        item {
            Segmented(
                listOf("网格", "紧凑", "列表"),
                when (s.libraryMode) { "compact" -> 1; "list" -> 2; else -> 0 },
                { i -> store.updateSettings { it.copy(libraryMode = when (i) { 1 -> "compact"; 2 -> "list"; else -> "grid" }) } },
                androidx.compose.ui.Modifier.fillMaxWidth(),
            )
        }
        item { SectionCaption("排序") }
        item {
            Segmented(
                listOf("最近", "书名", "创建", "字数"),
                when (s.librarySort) { "title" -> 1; "created" -> 2; "words" -> 3; else -> 0 },
                { i ->
                    store.updateSettings {
                        it.copy(librarySort = when (i) { 1 -> "title"; 2 -> "created"; 3 -> "words"; else -> "updated" })
                    }
                },
                androidx.compose.ui.Modifier.fillMaxWidth(),
            )
        }
        item { SectionCaption("尺寸") }
        item {
            SettingGroup {
                Column {
                    SettingRow("分栏", "${s.columns} 栏", Glyph.Grid)
                    Row(
                        androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Stepper(
                            "${s.columns}",
                            { store.updateSettings { it.copy(columns = (it.columns - 1).coerceAtLeast(2)) } },
                            { store.updateSettings { it.copy(columns = (it.columns + 1).coerceAtMost(4)) } },
                            minusEnabled = s.columns > 2,
                            plusEnabled = s.columns < 4,
                        )
                    }
                }
                RowDivider()
                Column {
                    SettingRow("卡片占比", "${(s.cardScale * 100).roundToInt()}%", Glyph.Layers)
                    InkSlider(
                        s.cardScale,
                        { v -> store.updateSettings { it.copy(cardScale = (v * 100).roundToInt() / 100f) } },
                        .78f, 1f,
                        modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp),
                    )
                }
                RowDivider()
                Column {
                    SettingRow("封面圆角", "${s.coverRadius.roundToInt()} dp", Glyph.Book)
                    InkSlider(
                        s.coverRadius,
                        { v -> store.updateSettings { it.copy(coverRadius = v.roundToInt().toFloat()) } },
                        6f, 30f,
                        modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp),
                    )
                }
                RowDivider()
                SettingRow("显示今日字数", "书架顶部显示写作进度", Glyph.Chart) {
                    InkToggle(s.showStats) { on -> store.updateSettings { it.copy(showStats = on) } }
                }
            }
        }
    }
}

@Composable
private fun WritingSettingsPage(store: AppStore, onBack: () -> Unit) {
    val ui = LocalInk.current
    val s = ui.settings
    val context = LocalContext.current
    SettingsPage("写作习惯", "目标、保存和缩进，写的时候会按这里的设置来。", onBack) {
        item {
            MoyuSurface(androidx.compose.ui.Modifier.fillMaxWidth(), radius = 24f, flat = true) {
                Column(androidx.compose.ui.Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(androidx.compose.ui.Modifier.weight(1f)) {
                            Txt("每日目标", size = 9.5f, color = ui.palette.muted)
                            Spacer(androidx.compose.ui.Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Txt("${s.dailyGoal}", size = 27f, weight = FontWeight.SemiBold)
                                Spacer(androidx.compose.ui.Modifier.width(4.dp))
                                Txt("字", size = 9.5f, color = ui.palette.muted, modifier = androidx.compose.ui.Modifier.padding(bottom = 4.dp))
                            }
                        }
                        Stepper(
                            "${s.dailyGoal}",
                            { store.updateSettings { it.copy(dailyGoal = (it.dailyGoal - 500).coerceAtLeast(500)) } },
                            { store.updateSettings { it.copy(dailyGoal = (it.dailyGoal + 500).coerceAtMost(20000)) } },
                        )
                    }
                    Spacer(androidx.compose.ui.Modifier.height(12.dp))
                    Box(
                        androidx.compose.ui.Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                            .background(ui.palette.line)
                    ) {
                        Box(
                            androidx.compose.ui.Modifier
                                .fillMaxWidth((store.todayWords().toFloat() / s.dailyGoal.coerceAtLeast(1)).coerceIn(0f, 1f))
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(ui.accent)
                        )
                    }
                    Spacer(androidx.compose.ui.Modifier.height(6.dp))
                    Txt("今天已写 ${store.todayWords()} 字", size = 8.5f, color = ui.palette.muted)
                }
            }
        }
        item { SectionCaption("保存") }
        item {
            SettingGroup {
                Column {
                    SettingRow("自动保存间隔", "${s.autoSave} ms", Glyph.Clock)
                    InkSlider(
                        s.autoSave.toFloat(),
                        { v -> store.updateSettings { it.copy(autoSave = ((v / 100).roundToInt() * 100).coerceIn(250, 2500)) } },
                        250f, 2500f,
                        modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp),
                    )
                }
                RowDivider()
                SettingRow("显示字数", "编辑器和书籍详情里统计非空白字符", Glyph.Chart) {
                    InkToggle(s.showWordCount) { on -> store.updateSettings { it.copy(showWordCount = on) } }
                }
            }
        }
        item { SectionCaption("分节") }
        item {
            val sectionStyles = listOf(
                "asterisk" to "星号",
                "line" to "短横",
                "dots" to "圆点",
                "blank" to "留白",
                "custom" to "自定义",
            )
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(sectionStyles) { (key, label) ->
                        GlassChip(label, s.sectionStyle == key) {
                            store.updateSettings { it.copy(sectionStyle = key) }
                        }
                    }
                }
                Spacer(androidx.compose.ui.Modifier.height(10.dp))
                Box(
                    androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .clip(SmoothCorner(18.dp))
                        .background(ui.palette.faint)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column {
                        Txt("插入效果", size = 9f, color = ui.palette.muted, letterSpacing = .8f)
                        Spacer(androidx.compose.ui.Modifier.height(8.dp))
                        Txt(
                            sectionBreakText(s).trim().ifBlank { "（留白分节）" },
                            size = 17f,
                            color = ui.palette.ink,
                            fontFamily = fontFamilyFor(ui.settings.font),
                            letterSpacing = 2f,
                        )
                    }
                }
                if (s.sectionStyle == "custom") {
                    Spacer(androidx.compose.ui.Modifier.height(10.dp))
                    InkField(
                        s.sectionSymbol,
                        { value -> store.updateSettings { it.copy(sectionSymbol = value.take(24)) } },
                        "输入分节符号，例如 ❋　❋　❋",
                        minHeight = 48.dp,
                        textSize = 14f,
                    )
                    Spacer(androidx.compose.ui.Modifier.height(5.dp))
                    Txt("分节时会把这段符号单独放在空行里。", size = 8.5f, color = ui.palette.muted)
                }
            }
        }
        item { SectionCaption("提醒") }
        item {
            SettingGroup {
                SettingRow("通知权限", "待办到点时在系统通知里提醒", Glyph.Bell, onClick = {
                    openNotificationSettings(context)
                })
                RowDivider()
                SettingRow("时区", "按本机时间提醒 · ${java.util.TimeZone.getDefault().id}", Glyph.Clock)
            }
        }
    }
}

@Composable
private fun MotionSettingsPage(store: AppStore, onBack: () -> Unit) {
    val ui = LocalInk.current
    val s = ui.settings
    SettingsPage("动效", "按压力度和触感都从这里统一。", onBack) {
        item { MotionPreview() }
        item { SectionCaption("手感") }
        item {
            Segmented(
                listOf("灵动", "柔和", "迅捷", "关闭"),
                when (s.motion) { "gentle" -> 1; "snappy" -> 2; "none" -> 3; else -> 0 },
                { i ->
                    store.updateSettings {
                        it.copy(motion = when (i) { 1 -> "gentle"; 2 -> "snappy"; 3 -> "none"; else -> "spring" })
                    }
                },
                androidx.compose.ui.Modifier.fillMaxWidth(),
            )
        }
        item {
            SettingGroup {
                SettingRow("触感反馈", "按钮和选择时轻触马达", Glyph.Motion) {
                    InkToggle(s.haptics) { on -> store.updateSettings { it.copy(haptics = on) } }
                }
            }
        }
    }
}

@Composable
private fun MotionPreview() {
    val ui = LocalInk.current
    var position by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    MoyuSurface(androidx.compose.ui.Modifier.fillMaxWidth(), radius = 24f, flat = true) {
        Column(androidx.compose.ui.Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("动效预览", size = 9f, color = ui.palette.muted, letterSpacing = 1f)
                Spacer(androidx.compose.ui.Modifier.weight(1f))
                Txt(motionLabel(ui.settings.motion), size = 9f, color = ui.palette.muted)
            }
            Spacer(androidx.compose.ui.Modifier.height(15.dp))
            BoxWithConstraints(
                androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(ui.palette.faint)
                    .bouncy(scale = .98f) { position = (position + 1) % 3 }
            ) {
                val step = (maxWidth - 40.dp) / 2
                val x by animateDpAsState(step * position.toFloat(), motionSpecDp(ui.settings.motion), label = "motionPreview")
                Box(
                    androidx.compose.ui.Modifier
                        .offset(x = x, y = 4.dp)
                        .width(40.dp)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(ui.palette.ink),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(Glyph.ArrowUp, ui.palette.bgTop, 17.dp, 2f)
                }
            }
        }
    }
}

@Composable
private fun NavigationSettingsPage(store: AppStore, onBack: () -> Unit) {
    val ui = LocalInk.current
    val s = ui.settings
    var names by remember { mutableStateOf(s.tabNames) }
    val tabDefaults = listOf("书架", "笔记", "数据", "问书", "设置")
    SettingsPage("标签栏", "五个入口的名字，改完立刻出现在底部。", onBack) {
        item { MiniTabPreview(names, s.showTabLabels) }
        item { SectionCaption("显示") }
        item {
            SettingGroup {
                SettingRow("显示文字", "图标下方显示标签名", Glyph.Layers) {
                    InkToggle(s.showTabLabels) { on -> store.updateSettings { it.copy(showTabLabels = on) } }
                }
            }
        }
        item { SectionCaption("显示哪些页面") }
        item {
            // 设置页永远在；其余四项可单独关掉，至少留一个。
            val pages = listOf(
                Triple("library", "书架", Glyph.Book),
                Triple("notes", "笔记", Glyph.Note),
                Triple("stats", "数据", Glyph.Chart),
                Triple("ai", "问书", Glyph.Sparkles),
            )
            SettingGroup {
                pages.forEachIndexed { i, (key, label, glyph) ->
                    if (i > 0) RowDivider()
                    val on = key !in s.hiddenTabs
                    val wouldBeEmpty = on && s.hiddenTabs.size >= 3
                    SettingRow(
                        label,
                        when {
                            !on -> "已隐藏"
                            wouldBeEmpty -> "至少留一个页面"
                            else -> "显示在底部"
                        },
                        glyph,
                    ) {
                        Box(androidx.compose.ui.Modifier.alpha(if (wouldBeEmpty) .45f else 1f)) {
                            InkToggle(on) { next ->
                                if (!next && s.hiddenTabs.size >= 3) {
                                    ToastBus.show("至少留一个页面")
                                } else {
                                    store.updateSettings {
                                        it.copy(
                                            hiddenTabs = if (next) s.hiddenTabs - key else s.hiddenTabs + key,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                RowDivider()
                SettingRow("设置", "总是显示", Glyph.Gear) {
                    Txt("常开", size = 11f, color = ui.palette.muted)
                }
            }
        }
        item { SectionCaption("名称") }
        item {
            SettingGroup {
                names.forEachIndexed { index, name ->
                    Row(
                        androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            androidx.compose.ui.Modifier
                                .size(26.dp)
                                .clip(SmoothCorner(8.dp))
                                .background(ui.palette.faint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Txt("${index + 1}", size = 9f, color = ui.palette.muted)
                        }
                        Spacer(androidx.compose.ui.Modifier.width(9.dp))
                        InkField(
                            name,
                            { value ->
                                val next = names.toMutableList()
                                next[index] = value.take(5)
                                names = next
                                store.updateSettings {
                                    it.copy(tabNames = next.mapIndexed { i, n -> n.ifBlank { tabDefaults.getOrElse(i) { "标签" } } })
                                }
                            },
                            tabDefaults.getOrElse(index) { "标签" },
                            minHeight = 44.dp,
                            textSize = 13f,
                            modifier = androidx.compose.ui.Modifier.weight(1f),
                        )
                    }
                    if (index < names.lastIndex) RowDivider()
                }
            }
        }
        item {
            GlassButton("恢复默认名称", {
                names = tabDefaults
                store.updateSettings { it.copy(tabNames = tabDefaults) }
                ToastBus.show("标签名称已恢复")
            }, androidx.compose.ui.Modifier.fillMaxWidth(), glyph = Glyph.Undo)
        }
    }
}

@Composable
private fun MiniTabPreview(names: List<String>, showLabels: Boolean) {
    val ui = LocalInk.current
    MoyuSurface(androidx.compose.ui.Modifier.fillMaxWidth(), radius = 30f, flat = true) {
        Row(androidx.compose.ui.Modifier.fillMaxWidth().padding(7.dp)) {
            val icons = listOf(Glyph.Book, Glyph.Note, Glyph.Chart, Glyph.Sparkles, Glyph.Gear)
            names.forEachIndexed { index, name ->
                val active = index == 0
                Column(
                    androidx.compose.ui.Modifier
                        .weight(1f)
                        .clip(SmoothCorner(22.dp))
                        .background(if (active) ui.palette.ink else Color.Transparent)
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LineIcon(icons[index], if (active) ui.palette.bgTop else ui.palette.muted, 19.dp)
                    if (showLabels) {
                        Spacer(androidx.compose.ui.Modifier.height(4.dp))
                        Txt(
                            name.ifBlank { " " },
                            size = 8.5f,
                            color = if (active) ui.palette.bgTop else ui.palette.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataSettingsPage(store: AppStore, onBack: () -> Unit) {
    val ui = LocalInk.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var importCandidate by remember { mutableStateOf<AppData?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    fun importUri(uri: Uri) {
        busy = true
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching { parseBackup(readBackupText(context, uri)) }
            }
            parsed.onSuccess {
                importCandidate = it
                importError = null
                ToastBus.show("已读取备份，请确认恢复")
            }.onFailure {
                importError = it.message ?: "备份文件无法解析"
                ToastBus.show("导入失败：${it.message ?: "格式不正确"}")
            }
            busy = false
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri(uri)
    }
    SettingsPage("备份与恢复", "数据只在本机。导出一份，换机时带得走。", onBack) {
        item {
            MoyuSurface(androidx.compose.ui.Modifier.fillMaxWidth(), radius = 24f, flat = true) {
                Column(androidx.compose.ui.Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            androidx.compose.ui.Modifier
                                .size(42.dp)
                                .clip(SmoothCorner(13.dp))
                                .background(ui.accent.copy(alpha = .12f)),
                            contentAlignment = Alignment.Center,
                        ) { LineIcon(Glyph.Lock, ui.accent, 20.dp) }
                        Spacer(androidx.compose.ui.Modifier.width(12.dp))
                        Column(androidx.compose.ui.Modifier.weight(1f)) {
                            Txt("本地优先", size = 13.5f, weight = FontWeight.Medium)
                        }
                    }
                    Spacer(androidx.compose.ui.Modifier.height(15.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        PrimaryButton(
                            if (busy) "导出中…" else "导出备份",
                            {
                                if (busy) return@PrimaryButton
                                busy = true
                                scope.launch {
                                    val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                                    runCatching {
                                        exportBytes(
                                            context,
                                            "字隅备份-$stamp.json",
                                            "application/json",
                                            backupJson(store.data).toByteArray(Charsets.UTF_8),
                                        )
                                    }.onSuccess { ToastBus.show("备份已导出到 ${it.location}") }
                                        .onFailure { ToastBus.show("导出失败：${it.message ?: "未知错误"}") }
                                    busy = false
                                }
                            },
                            androidx.compose.ui.Modifier.weight(1f),
                            glyph = Glyph.Download,
                            enabled = !busy,
                        )
                        GlassButton(
                            if (busy) "读取中…" else "恢复备份",
                            {
                                if (busy) return@GlassButton
                                busy = true
                                scope.launch {
                                    val latest = withContext(Dispatchers.IO) { findLatestBackup(context) }
                                    busy = false
                                    if (latest != null) importUri(latest)
                                    else openBackup.launch(
                                        arrayOf("application/json", "text/plain", "application/octet-stream")
                                    )
                                }
                            },
                            androidx.compose.ui.Modifier.weight(1f),
                            glyph = Glyph.Upload,
                        )
                    }
                    Spacer(androidx.compose.ui.Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Txt(
                            "恢复时会优先读取「下载/字隅」里的最近一份备份。",
                            size = 8.5f,
                            color = ui.palette.muted,
                            modifier = androidx.compose.ui.Modifier.weight(1f),
                        )
                        Txt(
                            "选择其他文件",
                            size = 8.5f,
                            color = ui.accent,
                            weight = FontWeight.Medium,
                            modifier = androidx.compose.ui.Modifier
                                .clip(CircleShape)
                                .bouncy(scale = .95f) {
                                    openBackup.launch(
                                        arrayOf("application/json", "text/plain", "application/octet-stream")
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
        item { SectionCaption("本机数据") }
        item {
            SettingGroup {
                SettingRow("书籍", "${store.books.size} 本 · ${formatWords(store.books.sumOf { it.words })} 字", Glyph.Book)
                RowDivider()
                SettingRow("笔记", "${store.notes.size} 条 · ${store.notes.sumOf { it.todos.count { t -> !t.done } }} 项待办", Glyph.Note)
                RowDivider()
                SettingRow("占用空间", "${(store.storageBytes() / 1024f).let { String.format(Locale.US, "%.1f", it) }} KB · JSON", Glyph.Folder)
            }
        }
        item { SectionCaption("危险操作") }
        item {
            SettingGroup {
                SettingRow("清空所有内容", "删除全部书籍、章节、笔记和统计", Glyph.Trash, onClick = { confirmClear = true })
            }
        }
        item {
            Txt(
                "导出的 EPUB/TXT 在「下载/字隅」中，不会被清空数据影响。",
                size = 9f,
                color = ui.palette.muted,
                lineHeight = 14f,
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 5.dp),
            )
        }
    }
    if (importCandidate != null) {
        val count = importCandidate!!
        ConfirmDialog(
            "恢复这份备份？",
            "备份里有 ${count.books.size} 本书、${count.notes.size} 条笔记。恢复会覆盖当前全部内容。",
            "恢复",
            {
                ReminderScheduler.cancelAll(context, store.data)
                store.replaceAll(count)
                ReminderScheduler.rescheduleAll(context, store.data)
                importCandidate = null
                ToastBus.show("备份已恢复")
            },
            { importCandidate = null },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            "清空所有内容？",
            "所有书籍、章节、笔记、待办和写作统计都会从本机删除。此操作无法撤销，建议先导出备份。",
            "全部清空",
            {
                ReminderScheduler.cancelAll(context, store.data)
                store.clearAll()
                confirmClear = false
                ToastBus.show("已清空所有内容")
            },
            { confirmClear = false },
            destructive = true,
        )
    }
}

@Composable
private fun SpaceSettingsPage(store: AppStore, onBack: () -> Unit) {
    val ui = LocalInk.current
    val s = ui.settings
    var name by remember { mutableStateOf(s.appName) }
    SettingsPage("空间名称", "显示在书架顶部和设置里的名字。", onBack) {
        item {
            MoyuSurface(androidx.compose.ui.Modifier.fillMaxWidth(), radius = 26f, flat = true) {
                Column(androidx.compose.ui.Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            androidx.compose.ui.Modifier.size(44.dp).clip(CircleShape).background(ui.palette.ink),
                            contentAlignment = Alignment.Center,
                        ) {
                            Txt(name.take(1).ifBlank { "字" }, size = 20f, color = ui.palette.bgTop, weight = FontWeight.Medium)
                        }
                        Spacer(androidx.compose.ui.Modifier.width(12.dp))
                        Column {
                            Txt(name.ifBlank { "未命名" }, size = 19f, weight = FontWeight.SemiBold, letterSpacing = 1f)
                        }
                    }
                }
            }
        }
        item { SectionCaption("名称") }
        item {
            InkField(name, { name = it.take(8) }, "素纸", textSize = 17f)
        }
        item {
            PrimaryButton("保存", {
                store.updateSettings { it.copy(appName = name.trim().ifBlank { "素纸" }) }
                ToastBus.show("空间名称已更新")
                onBack()
            }, androidx.compose.ui.Modifier.fillMaxWidth(), glyph = Glyph.Check)
        }
        item {
            GlassButton("恢复默认", {
                name = "素纸"
                store.updateSettings { it.copy(appName = "素纸") }
            }, androidx.compose.ui.Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AboutPage(store: AppStore, onBack: () -> Unit) {
    val ui = LocalInk.current
    val view = LocalView.current
    val refresh = view.display?.refreshRate ?: 0f
    val maxRefresh = view.display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: refresh
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = ui.settings
    var resetSettings by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    SettingsPage("关于素纸", "", onBack) {
        item {
            Column(
                androidx.compose.ui.Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    androidx.compose.ui.Modifier.size(76.dp).clip(SmoothCorner(23.dp)).background(ui.palette.ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("字", size = 34f, color = ui.palette.bgTop, weight = FontWeight.Medium, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                }
                Spacer(androidx.compose.ui.Modifier.height(12.dp))
                Txt("素纸", size = 22f, weight = FontWeight.SemiBold, letterSpacing = 2f)
                Spacer(androidx.compose.ui.Modifier.height(7.dp))
                Txt(
                    "作者 3Cne · QQ 2503581955",
                    size = 10.5f,
                    color = ui.palette.muted,
                    letterSpacing = .3f,
                )
            }
        }
        item {
            SettingGroup("技术") {
                SettingRow("屏幕刷新率", "本机支持 ${maxRefresh.roundToInt()}Hz · 当前 ${refresh.roundToInt()}Hz", Glyph.Motion)
                RowDivider()
                SettingRow("显示帧率", "在屏幕下方显示实时帧率", Glyph.Chart) {
                    InkToggle(s.showFps) { on -> store.updateSettings { it.copy(showFps = on) } }
                }
                RowDivider()
                SettingRow("导出格式", "EPUB 3 / TXT · 自动目录与封面", Glyph.Export)
            }
        }
        item {
            SettingGroup("关于") {
                SettingRow(
                    "重看功能引导",
                    "重新介绍书架、待办、提醒、纸张和外观",
                    Glyph.Sparkles,
                    onClick = { store.updateSettings { it.copy(onboardingDone = false) } },
                )
                RowDivider()
                SettingRow("重新设置全部偏好", "恢复默认主题、排版、动效和标签", Glyph.Undo, onClick = { resetSettings = true })
                RowDivider()
                SettingRow("自动检测更新", "启动时静默检查 GitHub Releases", Glyph.Download) {
                    InkToggle(s.autoUpdate) { on -> store.updateSettings { it.copy(autoUpdate = on) } }
                }
                RowDivider()
                SettingRow(
                    "检查更新",
                    when {
                        BuildConfig.UPDATE_REPO.isBlank() -> "GitHub 仓库待绑定"
                        checkingUpdate -> "正在检查…"
                        else -> "GitHub Releases"
                    },
                    Glyph.Download,
                    onClick = {
                        if (BuildConfig.UPDATE_REPO.isBlank()) {
                            ToastBus.show("更新仓库还没绑定")
                        } else if (!checkingUpdate) {
                            checkingUpdate = true
                            scope.launch {
                                val info = UpdateChecker.latest(BuildConfig.UPDATE_REPO)
                                checkingUpdate = false
                                if (info == null) {
                                    ToastBus.show("检查失败，请稍后再试")
                                } else if (UpdateChecker.isNewer(info.tag, BuildConfig.VERSION_NAME)) {
                                    availableUpdate = info
                                } else if (UpdateChecker.isNewer(BuildConfig.VERSION_NAME, info.tag)) {
                                    ToastBus.show("线上最新 ${info.tag}；当前 ${BuildConfig.VERSION_NAME} 尚未发布", long = true)
                                } else {
                                    ToastBus.show("已经是最新版本")
                                }
                            }
                        }
                    },
                )
                RowDivider()
                SettingRow("版本", "versionCode ${BuildConfig.VERSION_CODE} · 2026.10", Glyph.Info)
            }
        }
        item {
            Txt(
                "素纸把书、笔记和提醒都留在你的设备上。没有账号，也不会上传正文。",
                size = 10f,
                color = ui.palette.muted,
                lineHeight = 17f,
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
    availableUpdate?.let { info ->
        GlassDialog(onDismiss = { availableUpdate = null }) {
            Txt("发现新版本 ${info.tag}", size = 22f, weight = FontWeight.SemiBold, color = ui.palette.ink)
            Txt(
                "当前 ${BuildConfig.VERSION_NAME} · versionCode ${BuildConfig.VERSION_CODE}",
                size = 10.5f,
                color = ui.palette.muted,
            )
            Spacer(Modifier.height(12.dp))
            Txt(
                info.notes.ifBlank { "这个版本没有填写更新说明。" },
                size = 12f,
                color = ui.palette.ink,
                lineHeight = 20f,
                maxLines = 14,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("稍后", { availableUpdate = null }, Modifier.weight(1f))
                GlassButton(
                    if (info.apkUrl.isBlank()) "打开发布页" else "下载并安装",
                    {
                        AppUpdater.downloadAndInstall(context, info)
                        availableUpdate = null
                    },
                    Modifier.weight(1f),
                    glyph = Glyph.Download,
                )
            }
        }
    }

    if (resetSettings) {
        ConfirmDialog(
            "恢复默认偏好？",
            "主题、字体、书架排列、动效和标签名称会恢复默认。书籍、笔记和正文不会受影响。",
            "恢复",
            {
                store.updateSettings { AppSettings() }
                resetSettings = false
                ToastBus.show("偏好已恢复默认")
            },
            { resetSettings = false },
        )
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallback) }
    }
}




/**
 * 问书 settings live in the app's own settings, not inside the AI tab: the tab
 * is for asking, the settings are for wiring it up. Same controls, one home.
 */
@Composable
private fun AiSettingsPage(store: AppStore, onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    val ui = LocalInk.current
    val context = LocalContext.current
    val settings = store.settings
    val scope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var keyTick by remember { mutableStateOf(0) }
    val hasKey = remember(keyTick) { AiKey.has(context) }
    val keyTail = remember(keyTick) { AiKey.load(context)?.let { AiKey.tail(it) }.orEmpty() }

    SettingsPage("问书", "", onBack) {
        item {
            SettingGroup("开与关") {
                SettingRow(
                    "启用问书",
                    glyph = Glyph.Sparkles,
                ) {
                    InkToggle(settings.aiEnabled) { v -> store.updateSettings { it.copy(aiEnabled = v) } }
                }
                RowDivider()
                SettingRow(
                    "自动整理故事圣经",
                    "章节保存后，把正文里写定的人物、关系、事件、情节点补进故事圣经；3 分钟内最多一次",
                    Glyph.Book,
                ) {
                    InkToggle(settings.aiBibleSync) { v -> store.updateSettings { it.copy(aiBibleSync = v) } }
                }
                RowDivider()
                SettingRow(
                    "改稿方式",
                    when (settings.aiWriteMode) {
                        0 -> "只读：只回答，不改正文也不改圣经"
                        1 -> "询问：改正文前给对比，你点接受才写"
                        else -> "自动：回答结束直接写，可一步撤销"
                    },
                    Glyph.Edit,
                    stacked = true,
                ) {
                    Segmented(
                        listOf("只读", "询问", "自动"),
                        settings.aiWriteMode.coerceIn(0, 2),
                        { i -> store.updateSettings { it.copy(aiWriteMode = i) } },
                    )
                }
            }
        }
        item {
            SettingGroup("联网与文件") {
                SettingRow(
                    "联网搜索",
                    "按次计费 · \$0.75/1000 次",
                    Glyph.Search,
                ) {
                    InkToggle(settings.aiWeb) { v -> store.updateSettings { it.copy(aiWeb = v) } }
                }
                RowDivider()
                SettingRow("搜索上限", "一次最多几次", Glyph.Layers) {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (1..5).forEach { n ->
                            GlassChip(n.toString(), settings.aiWebMax == n) {
                                store.updateSettings { it.copy(aiWebMax = n) }
                            }
                        }
                    }
                }
                RowDivider()
                SettingRow("工具过程", "回答下方显示每一步", Glyph.List) {
                    InkToggle(settings.aiToolTrace) { v -> store.updateSettings { it.copy(aiToolTrace = v) } }
                }
            }
        }
        item {
            SettingGroup("服务") {
                Column(androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Txt("服务地址", size = 12.5f)
                    Spacer(androidx.compose.ui.Modifier.height(6.dp))
                    InkField(
                        value = settings.aiBaseUrl,
                        onValueChange = { v -> store.updateSettings { it.copy(aiBaseUrl = v) } },
                        placeholder = AiDefaults.BASE_URL,
                        textSize = 13f,
                        minHeight = 44.dp,
                    )
                    Spacer(androidx.compose.ui.Modifier.height(12.dp))
                    Txt("模型", size = 12.5f)
                    Spacer(androidx.compose.ui.Modifier.height(6.dp))
                    InkField(
                        value = settings.aiModel,
                        onValueChange = { v -> store.updateSettings { it.copy(aiModel = v) } },
                        placeholder = AiDefaults.MODEL,
                        textSize = 13f,
                        minHeight = 44.dp,
                    )
                    Spacer(androidx.compose.ui.Modifier.height(8.dp))
                    GlassChip("还原默认", false, onClick = {
                        store.updateSettings { it.copy(aiBaseUrl = AiDefaults.BASE_URL, aiModel = AiDefaults.MODEL) }
                    })
                }
            }
        }
        item {
            SettingGroup("Key") {
                Column(androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Txt(if (hasKey) "已保存 $keyTail" else "还没有 Key", size = 12.5f)
                    Spacer(androidx.compose.ui.Modifier.height(6.dp))
                    InkField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = if (hasKey) "输入新 Key 可替换" else "粘贴 Key",
                        textSize = 13f,
                        minHeight = 44.dp,
                    )
                    Spacer(androidx.compose.ui.Modifier.height(10.dp))
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("保存", {
                            if (AiKey.save(context, keyInput)) {
                                keyInput = ""
                                keyTick++
                                testResult = "已保存到本机加密存储"
                            } else {
                                testResult = "保存失败：本机安全存储不可用"
                            }
                        }, androidx.compose.ui.Modifier.weight(1f), glyph = Glyph.Check)
                        GlassButton(if (testing) "测试中…" else "测试连接", {
                            val k = AiKey.load(context) ?: keyInput
                            if (k.isBlank()) {
                                testResult = "先填 Key"
                            } else {
                                testing = true
                                testResult = null
                                scope.launch {
                                    val r = AiClient.chat(
                                        settings.aiBaseUrl, settings.aiModel, k, settings.aiTemperature,
                                        "你是测试助手。", "只回复两个字：可以", maxTokens = 16,
                                    )
                                    testing = false
                                    testResult = if (r.ok) "连接正常：${r.text.take(20)}" else r.error
                                }
                            }
                        }, androidx.compose.ui.Modifier.weight(1f), glyph = Glyph.Link)
                    }
                    if (testResult != null) {
                        Spacer(androidx.compose.ui.Modifier.height(8.dp))
                        Txt(testResult!!, size = 10.5f, color = ui.palette.muted, lineHeight = 16f)
                    }
                    Spacer(androidx.compose.ui.Modifier.height(4.dp))
                }
            }
        }
        item {
            SettingGroup("发送上限") {
                SettingRow("自动检索", "没引用时每次带多少原文", Glyph.Search) {
                    Txt("${settings.aiContextChars} 字 · ${settings.aiTopK} 段", size = 12f, color = ui.palette.muted)
                }
                RowDivider()
                SettingRow("引用上限", "整本 / 章节 / 笔记合计最多发多少字", Glyph.Link) {
                    Txt("${settings.aiQuoteMaxChars} 字", size = 12f, color = ui.palette.muted)
                }
            }
        }
        item {
            SettingGroup("整理笔记") {
                SettingRow("带上几条对话", glyph = Glyph.Note) {
                    val idx = when (settings.noteContextCount) {
                        0 -> 3
                        2 -> 0
                        4 -> 1
                        else -> 2
                    }
                    Segmented(
                        listOf("2", "4", "8", "全部"),
                        idx,
                        { i ->
                            store.updateSettings {
                                it.copy(
                                    noteContextCount = when (i) {
                                        0 -> 2
                                        1 -> 4
                                        2 -> 8
                                        else -> 0
                                    },
                                )
                            }
                        },
                        androidx.compose.ui.Modifier.width(176.dp),
                    )
                }
                RowDivider()
                SettingRow("输出长度", glyph = Glyph.Font) {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GlassChip("标准", settings.noteMaxTokens == 4000) {
                            store.updateSettings { it.copy(noteMaxTokens = 4000) }
                        }
                        GlassChip("长", settings.noteMaxTokens == 8000) {
                            store.updateSettings { it.copy(noteMaxTokens = 8000) }
                        }
                    }
                }
            }
        }
    }
}
/** A named slider with its live value and a one-line explanation. Used by the
 *  问书 glass section so three knobs do not need three bespoke rows. */
@Composable
private fun GlassSliderRow(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    val ui = LocalInk.current
    Column(androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(title, size = 12.5f)
            Spacer(androidx.compose.ui.Modifier.weight(1f))
            Txt(
                if (max <= 2f) "${(value * 100).roundToInt()}%" else "${value.roundToInt()}",
                size = 12f,
                color = ui.palette.muted,
            )
        }
        Spacer(androidx.compose.ui.Modifier.height(6.dp))
        InkSlider(value = value, onChange = onChange, min = min, max = max)
    }
}

/** 开 / 关 row, same shape as the tab bar's 边缘色散 switch. */
@Composable
private fun DispersionRow(on: Boolean, onChange: (Boolean) -> Unit) {
    val ui = LocalInk.current
    androidx.compose.foundation.layout.Row(
        androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(androidx.compose.ui.Modifier.weight(1f)) {
            Txt("边缘色散", size = 12.5f)
        }
        Segmented(
            listOf("开", "关"),
            if (on) 0 else 1,
            { i -> onChange(i == 0) },
            androidx.compose.ui.Modifier.width(110.dp),
        )
    }
}

/**
 * All four glass surfaces on one page: 标签栏 / 镜片 / 输入框 / 按钮.
 * They used to live in two places (外观 and 问书), which made "tune the glass"
 * a hunt; one page, one preview, one set of rows.
 */
@Composable
private fun GlassSettingsPage(store: AppStore, onBack: () -> Unit) {
    val ui = LocalInk.current
    val s = ui.settings
    var group by remember { mutableStateOf(0) }
    // The preview reads the live settings directly. The old 110 ms debounce was
    // the reason "nothing moves while I drag": each new value reset the timer,
    // so a continuous drag never let a single snapshot through.
    SettingsPage("玻璃", "", onBack) {
        item {
            Segmented(
                listOf("标签栏", "镜片", "输入框", "按钮", "通知"),
                group,
                { group = it },
                androidx.compose.ui.Modifier.fillMaxWidth(),
            )
        }
        // The preview has to stay on screen while the knobs are dragged or it
        // reads as "nothing is connected": pin it to the top of the list.
        stickyHeader {
            Box(
                androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .background(ui.palette.bgTop)
                    .padding(top = 6.dp, bottom = 8.dp),
            ) {
                GlassLivePreview(group, s)
            }
        }
        item {
            // The four groups slide by their relative position, like the root
            // tabs and the 对话/记忆 switch; the knobs travel together.
            AnimatedContent(
                targetState = group,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(240)) { it * dir } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(tween(240)) { -it * dir } + fadeOut(tween(140)))
                },
                label = "glassGroup",
            ) { g ->
                Column {
                    Spacer(androidx.compose.ui.Modifier.height(2.dp))
                    when (g) {
                        0 -> GlassKnobs(
                            blur = s.tabBlur, onBlur = { v -> store.updateSettings { it.copy(tabBlur = v) } },
                            refraction = s.tabRefraction, onRefraction = { v -> store.updateSettings { it.copy(tabRefraction = v) } },
                            shadow = s.tabShadow, onShadow = { v -> store.updateSettings { it.copy(tabShadow = v) } },
                            opacity = s.tabBarOpacity, onOpacity = { v -> store.updateSettings { it.copy(tabBarOpacity = v) } },
                            edge = s.tabEdge, onEdge = { v -> store.updateSettings { it.copy(tabEdge = v) } },
                            dispersion = s.tabDispersion, onDispersion = { v -> store.updateSettings { it.copy(tabDispersion = v) } },
                            opacityLabel = "整条不透明度",
                        )
                        1 -> GlassKnobs(
                            blur = s.lensBlur, onBlur = { v -> store.updateSettings { it.copy(lensBlur = v) } },
                            refraction = s.lensRefraction, onRefraction = { v -> store.updateSettings { it.copy(lensRefraction = v) } },
                            shadow = s.lensShadow, onShadow = { v -> store.updateSettings { it.copy(lensShadow = v) } },
                            opacity = s.tabLensOpacity, onOpacity = { v -> store.updateSettings { it.copy(tabLensOpacity = v) } },
                            edge = s.lensEdge, onEdge = { v -> store.updateSettings { it.copy(lensEdge = v) } },
                            dispersion = s.lensDispersion, onDispersion = { v -> store.updateSettings { it.copy(lensDispersion = v) } },
                            opacityLabel = "镜片不透明度",
                        )
                        2 -> GlassKnobs(
                            blur = s.fieldGlassBlur, onBlur = { v -> store.updateSettings { it.copy(fieldGlassBlur = v) } },
                            refraction = s.fieldGlassRefraction, onRefraction = { v -> store.updateSettings { it.copy(fieldGlassRefraction = v) } },
                            shadow = s.fieldGlassShadow, onShadow = { v -> store.updateSettings { it.copy(fieldGlassShadow = v) } },
                            opacity = s.fieldGlassOpacity, onOpacity = { v -> store.updateSettings { it.copy(fieldGlassOpacity = v) } },
                            edge = s.fieldGlassEdge, onEdge = { v -> store.updateSettings { it.copy(fieldGlassEdge = v) } },
                            dispersion = s.fieldGlassDispersion, onDispersion = { v -> store.updateSettings { it.copy(fieldGlassDispersion = v) } },
                            opacityLabel = "输入框不透明度",
                        )
                        3 -> GlassKnobs(
                            blur = s.btnGlassBlur, onBlur = { v -> store.updateSettings { it.copy(btnGlassBlur = v) } },
                            refraction = s.btnGlassRefraction, onRefraction = { v -> store.updateSettings { it.copy(btnGlassRefraction = v) } },
                            shadow = s.btnGlassShadow, onShadow = { v -> store.updateSettings { it.copy(btnGlassShadow = v) } },
                            opacity = s.btnGlassOpacity, onOpacity = { v -> store.updateSettings { it.copy(btnGlassOpacity = v) } },
                            edge = s.btnGlassEdge, onEdge = { v -> store.updateSettings { it.copy(btnGlassEdge = v) } },
                            dispersion = s.btnGlassDispersion, onDispersion = { v -> store.updateSettings { it.copy(btnGlassDispersion = v) } },
                            opacityLabel = "按钮不透明度",
                        )
                        else -> GlassKnobs(
                            blur = s.bubbleGlassBlur, onBlur = { v -> store.updateSettings { it.copy(bubbleGlassBlur = v) } },
                            refraction = s.bubbleGlassRefraction, onRefraction = { v -> store.updateSettings { it.copy(bubbleGlassRefraction = v) } },
                            shadow = s.bubbleGlassShadow, onShadow = { v -> store.updateSettings { it.copy(bubbleGlassShadow = v) } },
                            opacity = s.bubbleGlassOpacity, onOpacity = { v -> store.updateSettings { it.copy(bubbleGlassOpacity = v) } },
                            edge = s.bubbleGlassEdge, onEdge = { v -> store.updateSettings { it.copy(bubbleGlassEdge = v) } },
                            dispersion = s.bubbleGlassDispersion, onDispersion = { v -> store.updateSettings { it.copy(bubbleGlassDispersion = v) } },
                            opacityLabel = "通知不透明度",
                        )
                    }
                }
            }
        }
    }
}

/** The same six rows for every group, so the four tabs cannot drift apart. */
@Composable
private fun GlassKnobs(
    blur: Float, onBlur: (Float) -> Unit,
    refraction: Float, onRefraction: (Float) -> Unit,
    shadow: Float, onShadow: (Float) -> Unit,
    opacity: Float, onOpacity: (Float) -> Unit,
    edge: Float, onEdge: (Float) -> Unit,
    dispersion: Boolean, onDispersion: (Boolean) -> Unit,
    opacityLabel: String,
) {
    SettingGroup {
        GlassSliderRow("模糊", blur, 0f, 30f) { onBlur(it) }
        RowDivider()
        GlassSliderRow("边缘折射", refraction, 0f, 40f) { onRefraction(it) }
        RowDivider()
        GlassSliderRow("内外阴影", shadow, 0f, 1f) { onShadow(it) }
        RowDivider()
        GlassSliderRow(opacityLabel, opacity, 0f, 1f) { onOpacity(it) }
        RowDivider()
        GlassSliderRow("边缘带", edge, 0f, 2f) { onEdge(it) }
        RowDivider()
        DispersionRow(dispersion) { onDispersion(it) }
    }
}

/**
 * Live: the real bar + lens, plus the actual input and button glass below.
 *
 * The field and button previews used to be hand-drawn (paper fill + shadow +
 * border). That is not the material the tab runs: the blur went on the PAGE
 * behind the control, and the control itself had no refraction and no
 * dispersion — so three of the six sliders moved nothing. They now take the
 * same `liquidGlassBar` + recorded-layer path as the real input row, which is
 * the only way "tune it here, see it there" can be true.
 */
@Composable
private fun LegacyGlassLivePreview(group: Int, s: AppSettings) {
    val p = LocalInk.current.palette
    if (group <= 1) {
        GlassPreview(
            barBlur = s.tabBlur,
            barRefraction = s.tabRefraction,
            barDispersion = s.tabDispersion,
            barShadow = s.tabShadow,
            barVeil = s.tabBarOpacity,
            lensBlur = s.lensBlur,
            lensRefraction = s.lensRefraction,
            lensDispersion = s.lensDispersion,
            lensShadow = s.lensShadow,
            lensVeil = s.tabLensOpacity,
            barEdge = s.tabEdge,
            lensEdge = s.lensEdge,
            showBar = group == 0,
            showLens = group == 1,
        )
        return
    }
    val button = group == 3
    val backdrop = rememberLayerBackdrop()
    val blur = if (button) s.btnGlassBlur else s.fieldGlassBlur
    val refraction = if (button) s.btnGlassRefraction else s.fieldGlassRefraction
    val dispersion = if (button) s.btnGlassDispersion else s.fieldGlassDispersion
    val veil = if (button) s.btnGlassOpacity else s.fieldGlassOpacity
    val edge = if (button) s.btnGlassEdge else s.fieldGlassEdge
    val shadow = if (button) s.btnGlassShadow else s.fieldGlassShadow
    // 玻璃现在也走连续曲率：SmoothCornerShape 继承 CornerBasedShape，
    // lens 的类型检查能过；四角按圆弧半径算折射，与裁切路径最多差 1–3dp，
    // 由边缘带盖住。
    val shape = if (button) CircleShape else SmoothCorner(22.dp)
    Box(
        androidx.compose.ui.Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(SmoothCorner(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // The layer the glass samples: a static paper slice. Nothing here
        // changes while a slider moves, so the backdrop is recorded once and
        // only the effects re-run — that is what keeps the drag smooth.
        Column(
            androidx.compose.ui.Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(p.paper)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            repeat(3) { i ->
                Box(
                    androidx.compose.ui.Modifier
                        .fillMaxWidth(if (i == 2) .55f else 1f)
                        .height(6.dp)
                        .background(p.ink.copy(alpha = .34f), SmoothCorner(3.dp)),
                )
                Spacer(androidx.compose.ui.Modifier.height(9.dp))
            }
        }
        if (!button) {
            Box(
                androidx.compose.ui.Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .liquidGlassBar(
                        backdrop = backdrop,
                        shape = shape,
                        palette = p,
                        blurDp = blur,
                        refractionDp = refraction,
                        dispersion = dispersion,
                        shadowAlpha = shadow,
                        veil = veil,
                        edgeStrength = edge,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(androidx.compose.ui.Modifier.padding(horizontal = 14.dp)) {
                    Txt("问这本书的任何事", size = 12.5f, color = p.muted)
                }
            }
        } else {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Glyph.Link, Glyph.ArrowUp).forEach { glyph ->
                    Box(
                        androidx.compose.ui.Modifier
                            .size(46.dp)
                            .liquidGlassBar(
                                backdrop = backdrop,
                                shape = shape,
                                palette = p,
                                blurDp = blur,
                                refractionDp = refraction,
                                dispersion = dispersion,
                                shadowAlpha = shadow,
                                veil = veil,
                                edgeStrength = edge,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(glyph, p.ink, 18.dp)
                    }
                }
            }
        }
    }
}
