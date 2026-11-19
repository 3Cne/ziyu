package com.moyu.writer

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime

/**
 * 笔记 tab 的第一层：笔记夹列表。
 *
 * 2026-11-15 用户定：笔记先按夹分，进夹以后才看得到笔记本身，点笔记才进正文。
 * 「笔记夹」就是原来的笔记分组（`noteGroups`，一条数据都没换），只是从一条筛选
 * 条件升级成进入笔记的必经一层。`folderId == null` 是常驻的「未归类」。
 */
@Composable
fun NotesScreen(
    store: AppStore,
    onOpenFolder: (String?) -> Unit,
    onCreateNote: (String) -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val notes = store.notes
    val folders = store.noteGroups
    var createFolderOpen by remember { mutableStateOf(false) }
    var menuFolder by remember { mutableStateOf<Group?>(null) }
    var deleteFolder by remember { mutableStateOf<Group?>(null) }
    var renameFolder by remember { mutableStateOf<Group?>(null) }

    fun countOf(folderId: String?): Int = notes.count { it.groupId == folderId }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Txt("笔记", size = 34f, weight = FontWeight.SemiBold, letterSpacing = .4f)
                        }
                        GlassIconButton(Glyph.Folder, { createFolderOpen = true })
                        Spacer(Modifier.width(6.dp))
                        GlassIconButton(Glyph.Plus, {
                            val note = Note(title = "")
                            store.addNote(note)
                            onCreateNote(note.id)
                            ToastBus.show("新笔记已打开")
                        })
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
            if (folders.isEmpty() && notes.isEmpty()) {
                item {
                    EmptyState(
                        Glyph.Folder,
                        "还没有笔记夹",
                        "先建一个笔记夹，再往里写。笔记夹就是以前的分组，现在要先点夹才看得到笔记。",
                    )
                }
            }
            item {
                FolderRow(
                    name = "未归类",
                    count = countOf(null),
                    accent = false,
                    onClick = { onOpenFolder(null) },
                )
            }
            // 3.26.48：笔记夹行加 key + animateItem——上移/下移、增删时整行弹簧落到新位置，
            // 不再同帧重排（与夹内笔记卡同一套）。
            lazyItems(folders, key = { it.id }) { folder ->
                Box(Modifier.animateItem()) {
                    FolderRow(
                        name = folder.name,
                        count = countOf(folder.id),
                        colorDot = folder.color,
                        onClick = { onOpenFolder(folder.id) },
                        onMore = { menuFolder = folder },
                    )
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                GlassButton("新建笔记夹", { createFolderOpen = true }, Modifier.fillMaxWidth(), glyph = Glyph.Plus)
            }
        }
    }

    if (createFolderOpen) {
        FolderEditSheet(
            title = "新建笔记夹",
            initialName = "",
            initialColor = groupColors.first(),
            onDismiss = { createFolderOpen = false },
            onSave = { name, color ->
                store.addGroup(GroupKind.NOTE, Group(name = name, color = color))
                createFolderOpen = false
                ToastBus.show("笔记夹已建好")
            },
        )
    }
    renameFolder?.let { folder ->
        FolderEditSheet(
            title = "编辑笔记夹",
            initialName = folder.name,
            initialColor = folder.color,
            onDismiss = { renameFolder = null },
            onSave = { name, color ->
                store.updateGroup(GroupKind.NOTE, folder.id) { it.copy(name = name, color = color) }
                renameFolder = null
            },
        )
    }
    menuFolder?.let { folder ->
        // 3.26.9：菜单 → 编辑笔记夹 收进同一个面板，横向弹簧切换；
        // 不再「关一个 ModalSheet、再开另一个」。
        var level by remember(folder.id) { mutableIntStateOf(0) }
        var pending by remember(folder.id) { mutableStateOf<(() -> Unit)?>(null) }
        // 3.26.48：bleedContent = true —— 内容槽不再加两侧 20dp 的「内窗口」留白，
        // 整幅横移能滑到面板本体边缘（治用户报了两次的「横滑时两道纸色边」）；
        // 每页自己用 SHEET_CONTENT_INSET 补回静止边距，纸底仍铺满整宽。
        InlineModalSheet(onDismiss = {
            menuFolder = null
            val action = pending
            pending = null
            action?.invoke()
        }, bleedContent = true) { close ->
            AnimatedContent(
                targetState = level,
                transitionSpec = {
                    val forward = targetState > initialState
                    // 3.26.18：只滑不淡 + 整宽位移。每层自带纸底，fade 会让纸底半透明；
                    // w/3 位移会让两层全程重叠、旧层从新层纸底下方露出。
                    (slideInHorizontally(spring(MOYU_DAMPING, 380f)) { w ->
                        if (forward) w else -w
                    }) togetherWith
                        (slideOutHorizontally(spring(MOYU_DAMPING, 380f)) { w ->
                            if (forward) -w else w
                        })
                },
                label = "folderSheet",
            ) { step ->
                if (step == 0) {
                    SheetPane {
                        Column(Modifier.fillMaxWidth().padding(horizontal = SHEET_CONTENT_INSET)) {
                            DialogTitle(folder.name, "${countOf(folder.id)} 条笔记", onClose = { close() })
                            Spacer(Modifier.height(12.dp))
                            SettingRow("重命名", "改名和颜色", Glyph.Edit, onClick = { level = 1 })
                            RowDivider()
                            SettingRow("上移", "排到前一个笔记夹前面", Glyph.ArrowUp, onClick = {
                                store.moveGroup(GroupKind.NOTE, folder.id, -1)
                                close()
                            })
                            RowDivider()
                            SettingRow("删除笔记夹", "夹里的笔记回到「未归类」，不会被删", Glyph.Trash, onClick = {
                                pending = { deleteFolder = folder }
                                close()
                            })
                        }
                    }
                } else {
                    SheetPane {
                        Column(Modifier.fillMaxWidth().padding(horizontal = SHEET_CONTENT_INSET)) {
                            FolderEditSheet(
                                title = "编辑笔记夹",
                                initialName = folder.name,
                                initialColor = folder.color,
                                onDismiss = { close() },
                                onSave = { name, color ->
                                    store.updateGroup(GroupKind.NOTE, folder.id) { it.copy(name = name, color = color) }
                                    close()
                                },
                                embedded = true,
                                onBack = { level = 0 },
                            )
                        }
                    }
                }
            }
        }
    }
    deleteFolder?.let { folder ->
        ConfirmDialog(
            "删除笔记夹「${folder.name}」？",
            "夹里 ${countOf(folder.id)} 条笔记会回到「未归类」，章节对这个夹的链接也会失效。笔记本身不会删。",
            "删除笔记夹",
            {
                store.deleteGroup(GroupKind.NOTE, folder.id)
                deleteFolder = null
                ToastBus.show("笔记夹已删除")
            },
            { deleteFolder = null },
            destructive = true,
        )
    }
}

/** 一个笔记夹：名字 + 条数 + 进入；三点只出现在真正的笔记夹上。 */
@Composable
private fun FolderRow(
    name: String,
    count: Int,
    accent: Boolean = true,
    colorDot: Long? = null,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    val ui = LocalInk.current
    val p = ui.palette
    MoyuSurface(Modifier.fillMaxWidth(), radius = 20f, flat = true) {
        Row(
            Modifier
                .fillMaxWidth()
                .bouncy(scale = .985f) { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(SmoothCorner(12.dp))
                    .background(p.faint),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(Glyph.Folder, if (accent) ui.accent else p.muted, 18.dp, 1.5f)
                if (colorDot != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(colorDot))
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(name, size = 15.5f, weight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Txt("$count 条笔记", size = 10f, color = p.muted)
            }
            if (onMore != null) {
                Box(
                    Modifier
                        .size(30.dp)
                        .bouncy { onMore() },
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(Glyph.More, p.muted.copy(alpha = .7f), 17.dp)
                }
                Spacer(Modifier.width(2.dp))
            }
            LineIcon(Glyph.Chevron, p.muted.copy(alpha = .55f), 14.dp)
        }
    }
}

/** 新建 / 改名笔记夹：名字 + 颜色，一次问完。 */
@Composable
private fun FolderEditSheet(
    title: String,
    initialName: String,
    initialColor: Long,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
    /** true = 作为「笔记夹菜单 → 编辑」同框层级的第二层画在面板里。 */
    embedded: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val ui = LocalInk.current
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Spacer(Modifier.height(14.dp))
        Txt("名字", size = 10.5f, color = ui.palette.muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        InkField(name, { name = it.take(16) }, "例如：素材、灵感、大纲", onDone = {})
        Spacer(Modifier.height(13.dp))
        Txt("颜色", size = 10.5f, color = ui.palette.muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            lazyItems(groupColors) { c ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .then(if (color == c) Modifier.border(2.dp, ui.palette.ink, CircleShape) else Modifier)
                        .clickable { color = c }
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("保存", {
            val clean = name.trim()
            if (clean.isBlank()) ToastBus.show("先给笔记夹起个名字") else onSave(clean, color)
        }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
    val subtitle = "名字和颜色随时可以再改。"
    if (embedded) {
        Column(Modifier.fillMaxWidth()) {
            SheetPageHeader(title, subtitle, onBack ?: onDismiss)
            body()
        }
    } else {
        // 3.26.47：面板右上角 X 走 ModalSheet 的 close()（先播退场、空闲后再回调 onDismiss），
        // 不再直接调外层 onDismiss——宿主 if(flag) 会把还没退场的面板当帧卸掉。
        ModalSheet(onDismiss = onDismiss, imeAware = true, closeableContent = { close ->
            DialogTitle(title, subtitle, onClose = close)
            body()
        })
    }
}

/**
 * 夹内：这一夹的笔记。列表/网格、筛选都沿用原来的笔记页，只是不再有「分组」层，
 * 因为已经在夹里了。
 */
@Composable
fun NoteFolderScreen(
    store: AppStore,
    folderId: String?,
    folderName: String,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onCreateNote: (String) -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val context = LocalContext.current
    val notes = store.notes
    val noteMode = ui.settings.noteMode
    var filter by remember { mutableStateOf("全部") }
    var menuNote by remember { mutableStateOf<Note?>(null) }
    var deleteNote by remember { mutableStateOf<Note?>(null) }
    var folderPickerNote by remember { mutableStateOf<Note?>(null) }
    var exportingNote by remember { mutableStateOf<Note?>(null) }
    var controlsOpen by remember { mutableStateOf(false) }
    // Hide a just-created note while the editor push animation is running.
    // Otherwise the new card is visible behind the incoming page.
    var hiddenNewNote by remember { mutableStateOf<String?>(null) }

    // 笔记夹页是 depth 1：根 BackHandler 只接管 depth 0，这里必须自己接，
    // 否则系统返回键会直接退出应用。
    androidx.activity.compose.BackHandler { onBack() }

    val inFolder = notes.filter { it.groupId == folderId }
    val filtered = when (filter) {
        "待办" -> inFolder.filter { n -> n.todos.any { !it.done } }
        "提醒" -> inFolder.filter { n -> n.todos.any { !it.done && it.remindAt != null } }
        "置顶" -> inFolder.filter { it.pinned }
        else -> inFolder
    }.filter { it.id != hiddenNewNote }

    LaunchedEffect(hiddenNewNote) {
        if (hiddenNewNote != null) {
            delay(420)
            hiddenNewNote = null
        }
    }

    fun create() {
        val note = Note(
            // Empty on purpose: the editor draws "无标题笔记" as a muted hint, the
            // same as other placeholder text, instead of real title content.
            title = "",
            groupId = folderId,
        )
        hiddenNewNote = note.id
        store.addNote(note)
        onCreateNote(note.id)
        ToastBus.show("新笔记已打开")
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        // 3.26.48：列表↔两列、筛选空态↔列表都走同一段 AnimatedContent。
        // 目标状态 = (排布, 是否空)：只在这两者变化时重放；两个「非空」筛选之间切换不重放
        // （网格里每张卡的 animateItem 自己负责落位），两套容器共用同一份 filtered。
        // 数值沿用 3.26.47 书架排布切换：新内容淡入 + 从 98% 放大落位，旧内容 140ms 淡出；
        // motion=none 全降成 tween(0) 直接落终态。滚动位置不要求跨排布/空态保留。
        val motion = ui.settings.motion != "none"
        AnimatedContent(
            targetState = noteMode to filtered.isEmpty(),
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (motion) {
                    (fadeIn(tween(200)) + scaleIn(initialScale = .98f)) togetherWith fadeOut(tween(140))
                } else {
                    (fadeIn(tween(0)) + scaleIn(initialScale = .98f, animationSpec = tween(0))) togetherWith
                        fadeOut(tween(0))
                }
            },
            label = "noteLayout",
        ) { (mode, empty) ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (mode == "grid") 2 else 1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 130.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .bouncy { onBack() },
                                contentAlignment = Alignment.Center,
                            ) {
                                LineIcon(Glyph.Back, p.ink, 21.dp)
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(
                                    folderName,
                                    size = 26f,
                                    weight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Txt("${inFolder.size} 条笔记", size = 10f, color = p.muted)
                            }
                            GlassIconButton(Glyph.List, { controlsOpen = true })
                            Spacer(Modifier.width(6.dp))
                            GlassIconButton(Glyph.Plus, { create() })
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
                if (empty) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            if (filter == "待办" || filter == "提醒") Glyph.Check else Glyph.Note,
                            when (filter) {
                                "待办" -> "没有未完成的待办"
                                "提醒" -> "还没有设定提醒"
                                "置顶" -> "没有置顶笔记"
                                else -> "这个笔记夹还是空的"
                            },
                            when (filter) {
                                "待办", "提醒" -> "在笔记里加一条待办，选一个提醒时间。"
                                else -> "点右上角 +，随手记下一句话。正文、符号栏和工具栏和写小说时一样。"
                            },
                        )
                    }
                } else {
                    gridItems(filtered, key = { it.id }) { note ->
                        Box(Modifier.animateItem()) {
                            NoteCard(
                                note = note,
                                chapterBadge = noteChapterBadge(store, note),
                                onClick = { _ -> onOpenNote(note.id) },
                                onMore = { menuNote = note },
                            )
                        }
                    }
                }
            }
        }
    }

    if (menuNote != null) {
        val note = notes.firstOrNull { it.id == menuNote!!.id } ?: menuNote!!
        // 3.26.10：笔记菜单 → 移动到笔记夹 / 导出笔记 收进同一个面板，
        // 横向弹簧切换；关闭时再执行删除（居中 Dialog）。
        var level by remember(note.id) { mutableIntStateOf(0) }
        var pending by remember(note.id) { mutableStateOf<(() -> Unit)?>(null) }
        // 3.26.48：这层同样是整幅横移（3 页），传 bleedContent = true 让页面滑到面板本体边缘；
        // 每页内容自己补 SHEET_CONTENT_INSET（见下面三个 SheetPane 内的 padding）。
        InlineModalSheet(onDismiss = {
            menuNote = null
            val action = pending
            pending = null
            action?.invoke()
        }, bleedContent = true) { close ->
            AnimatedContent(
                targetState = level,
                transitionSpec = {
                    val forward = targetState > initialState
                    // 3.26.18：只滑不淡 + 整宽位移（同上）。
                    (slideInHorizontally(spring(MOYU_DAMPING, 380f)) { w ->
                        if (forward) w else -w
                    }) togetherWith
                        (slideOutHorizontally(spring(MOYU_DAMPING, 380f)) { w ->
                            if (forward) -w else w
                        })
                },
                label = "noteSheet",
            ) { step ->
                when (step) {
                    0 -> SheetPane {
                        Column(Modifier.fillMaxWidth().padding(horizontal = SHEET_CONTENT_INSET)) {
                            DialogTitle(note.title, "${formatWords(note.words)} 字 · ${relativeTime(note.updatedAt)}", onClose = { close() })
                            Spacer(Modifier.height(13.dp))
                            SettingRow("打开笔记", "继续写正文", Glyph.Edit, onClick = {
                                pending = { onOpenNote(note.id) }
                                close()
                            })
                            RowDivider()
                            SettingRow("固定到顶部", if (note.pinned) "已固定" else "按更新时间排序", Glyph.Pin, trailing = {
                                InkToggle(note.pinned) { on -> store.updateNote(note.id) { it.copy(pinned = on) } }
                            })
                            RowDivider()
                            SettingRow(
                                "移动到笔记夹",
                                store.noteGroups.firstOrNull { it.id == note.groupId }?.name ?: "未归类",
                                Glyph.Folder,
                                onClick = { level = 1 },
                            )
                            RowDivider()
                            SettingRow("导出笔记", "TXT / Markdown / JSON", Glyph.Export, onClick = { level = 2 })
                            RowDivider()
                            SettingRow("删除笔记", "正文和待办会一并删除", Glyph.Trash, onClick = {
                                pending = { deleteNote = note }
                                close()
                            })
                        }
                    }
                    1 -> SheetPane {
                        Column(Modifier.fillMaxWidth().padding(horizontal = SHEET_CONTENT_INSET)) {
                            NoteFolderPickerSheet(
                                current = note.groupId,
                                store = store,
                                onSelect = { group -> store.setNoteGroup(note.id, group) },
                                onDismiss = { close() },
                                embedded = true,
                                onBack = { level = 0 },
                            )
                        }
                    }
                    else -> SheetPane {
                        Column(Modifier.fillMaxWidth().padding(horizontal = SHEET_CONTENT_INSET)) {
                            NoteExportSheet(
                                note = note,
                                onDismiss = { close() },
                                embedded = true,
                                onBack = { level = 0 },
                            )
                        }
                    }
                }
            }
        }
    }
    if (controlsOpen) {
        InlineModalSheet(onDismiss = { controlsOpen = false }) { _ ->
            DialogTitle("筛选与排布", "状态和布局集中在这里。", onClose = { controlsOpen = false })
            Spacer(Modifier.height(14.dp))
            Txt("状态", size = 10f, color = p.muted)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("全部", "待办", "提醒", "置顶").forEach { item ->
                    GlassChip(item, filter == item) { filter = item }
                }
            }
            Spacer(Modifier.height(15.dp))
            Txt("排布", size = 10f, color = p.muted)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassChip("列表", noteMode != "grid") { store.updateSettings { it.copy(noteMode = "list") } }
                GlassChip("网格", noteMode == "grid") { store.updateSettings { it.copy(noteMode = "grid") } }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    if (deleteNote != null) {
        val note = deleteNote!!
        ConfirmDialog(
            "删除「${note.title.ifBlank { "无标题笔记" }}」？",
            "正文和 ${note.todos.size} 项待办会被删除，无法恢复。",
            "删除",
            {
                ReminderScheduler.cancelNote(context, note)
                store.deleteNote(note.id)
                deleteNote = null
                ToastBus.show("笔记已删除")
            },
            { deleteNote = null },
            destructive = true,
        )
    }
}

/** 笔记换夹：未归类 + 每个笔记夹。 */
@Composable
internal fun NoteFolderPickerSheet(
    current: String?,
    store: AppStore,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    /** true = 作为「笔记菜单 → 移动」同框层级的第二层画在面板里。 */
    embedded: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(SmoothCorner(14.dp))
                .clickable { onSelect(null); onDismiss() }
                .padding(vertical = 13.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(Glyph.Folder, p.muted, 16.dp)
            Spacer(Modifier.width(10.dp))
            Txt("未归类", size = 14f, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
            // 3.26.22：勾选统一走 SelectMark。
            SelectMark(checked = current == null, onToggle = {}, interactive = false, size = 18.dp, corner = 6.dp)
        }
        store.noteGroups.forEach { g ->
            RowDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(SmoothCorner(14.dp))
                    .clickable { onSelect(g.id); onDismiss() }
                    .padding(vertical = 13.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupDot(g.color, 10)
                Spacer(Modifier.width(10.dp))
                Txt(g.name, size = 14f, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                // 3.26.22：勾选统一走 SelectMark。
                SelectMark(checked = current == g.id, onToggle = {}, interactive = false, size = 18.dp, corner = 6.dp)
            }
        }
    }
    if (embedded) {
        Column(Modifier.fillMaxWidth()) {
            SheetPageHeader("移动到笔记夹", "笔记只能在一个夹里。", onBack ?: onDismiss)
            Spacer(Modifier.height(12.dp))
            body()
        }
    } else {
        ModalSheet(onDismiss = onDismiss) {
            DialogTitle("移动到笔记夹", "笔记只能在一个夹里。", onClose = onDismiss)
            Spacer(Modifier.height(12.dp))
            body()
        }
    }
}

@Composable
private fun NoteCard(
    note: Note,
    chapterBadge: String? = null,
    onClick: (androidx.compose.ui.layout.LayoutCoordinates?) -> Unit,
    onMore: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val pending = note.todos.count { !it.done }
    val next = note.nextReminder()
    // The card keeps its own coordinates, not a rect: the tab pager moves pages
    // with a graphics-layer translation, which does not re-run layout callbacks,
    // so a rect cached at layout time would be stale (and read zero while the
    // page is still off-screen). boundsInRoot() at click time is current.
    var cardCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    MoyuSurface(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { cardCoords = it },
        radius = 24f,
        flat = true,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .bouncy(scale = .985f) { onClick(cardCoords) }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (note.pinned) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(p.faint),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(Glyph.Pin, ui.accent, 12.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Txt(
                        note.title.ifBlank { "无标题笔记" },
                        size = 16.5f,
                        weight = FontWeight.SemiBold,
                        maxLines = 2,
                        lineHeight = 22f,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (note.content.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Txt(
                            note.content.replace("\n", "  ").trim(),
                            size = 10.5f,
                            color = p.muted,
                            lineHeight = 16.5f,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(5.dp))
                Box(Modifier.size(30.dp).bouncy { onMore() }, contentAlignment = Alignment.Center) {
                    LineIcon(Glyph.More, p.muted.copy(alpha = .7f), 17.dp)
                }
            }
            if (note.todos.isNotEmpty() || next != null || chapterBadge != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (chapterBadge != null) {
                        NoteBadge(glyph = Glyph.Link, text = chapterBadge, accent = false)
                    }
                    if (note.todos.isNotEmpty()) {
                        NoteBadge(
                            glyph = Glyph.Check,
                            text = if (pending == 0) "待办已完成" else "$pending 项待办",
                            accent = pending > 0,
                        )
                    }
                    if (next?.remindAt != null) {
                        NoteBadge(glyph = Glyph.Clock, text = formatDateTime(next.remindAt), accent = true)
                    }
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(relativeTime(note.updatedAt), size = 8.5f, color = p.muted.copy(alpha = .75f))
                Spacer(Modifier.weight(1f))
                Txt("${formatWords(note.words)} 字", size = 8.5f, color = p.muted.copy(alpha = .75f))
            }
        }
    }
}

@Composable
private fun NoteBadge(glyph: Glyph, text: String, accent: Boolean) {
    val ui = LocalInk.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (accent) ui.accent.copy(alpha = .10f) else ui.palette.faint)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(glyph, if (accent) ui.accent else ui.palette.muted, 10.dp)
        Spacer(Modifier.width(4.dp))
        Txt(text, size = 8.5f, color = if (accent) ui.palette.ink else ui.palette.muted)
    }
}
