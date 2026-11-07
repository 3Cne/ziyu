package com.moyu.writer

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime

@Composable
fun NotesScreen(
    store: AppStore,
    onOpenNote: (String) -> Unit,
    onCreateNote: (String) -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val context = LocalContext.current
    val notes = store.notes
    val noteMode = ui.settings.noteMode
    var filter by remember { mutableStateOf("全部") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var groupManagerOpen by remember { mutableStateOf(false) }
    var menuNote by remember { mutableStateOf<Note?>(null) }
    var deleteNote by remember { mutableStateOf<Note?>(null) }
    var groupPickerNote by remember { mutableStateOf<Note?>(null) }
    var exportingNote by remember { mutableStateOf<Note?>(null) }
    var controlsOpen by remember { mutableStateOf(false) }
    // Hide a just-created note while the editor push animation is running.
    // Otherwise the new card is visible behind the incoming page.
    var hiddenNewNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(store.noteGroups) {
        if (selectedGroup != null && selectedGroup != "" && store.noteGroups.none { it.id == selectedGroup }) {
            selectedGroup = null
        }
    }
    val groupFiltered = notes.filter { n ->
        selectedGroup == null || (selectedGroup == "" && n.groupId == null) || (selectedGroup != "" && n.groupId == selectedGroup)
    }
    val filtered = when (filter) {
        "待办" -> groupFiltered.filter { n -> n.todos.any { !it.done } }
        "提醒" -> groupFiltered.filter { n -> n.todos.any { !it.done && it.remindAt != null } }
        "置顶" -> groupFiltered.filter { it.pinned }
        else -> groupFiltered
    }.filter { it.id != hiddenNewNote }

    LaunchedEffect(hiddenNewNote) {
        if (hiddenNewNote != null) {
            delay(420)
            hiddenNewNote = null
        }
    }
    val totalTodos = notes.sumOf { n -> n.todos.count { !it.done } }
    val totalReminders = notes.sumOf { n -> n.todos.count { !it.done && it.remindAt != null } }

    fun create() {
        val note = Note(
            // Empty on purpose: the editor draws "无标题笔记" as a muted hint, the
            // same as other placeholder text, instead of real title content.
            title = "",
            groupId = selectedGroup?.takeIf { it.isNotEmpty() },
        )
        hiddenNewNote = note.id
        store.addNote(note)
        onCreateNote(note.id)
        ToastBus.show("新笔记已打开")
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (noteMode == "grid") 2 else 1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 130.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Txt("笔记", size = 34f, weight = FontWeight.SemiBold, letterSpacing = .4f)
                        }
                        GlassIconButton(Glyph.Plus, { create() }, size = 44.dp)
                    }
                    Spacer(Modifier.height(18.dp))
                    val groupName = when (selectedGroup) {
                        null -> "全部分组"
                        "" -> "未分组"
                        else -> store.noteGroups.firstOrNull { it.id == selectedGroup }?.name ?: "全部分组"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassChip("筛选", false) { controlsOpen = true }
                        Spacer(Modifier.width(9.dp))
                        Txt(
                            "$filter · $groupName · ${if (noteMode == "grid") "网格" else "列表"}",
                            size = 10.5f,
                            color = p.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        if (filter == "待办" || filter == "提醒") Glyph.Check else Glyph.Note,
                        when (filter) {
                            "待办" -> "没有未完成的待办"
                            "提醒" -> "还没有设定提醒"
                            "置顶" -> "没有置顶笔记"
                            else -> "还没有笔记"
                        },
                        when (filter) {
                            "待办", "提醒" -> "在笔记里加一条待办，选一个提醒时间。"
                            else -> "点右上角 +，随手记下一句话。正文、符号栏和工具栏和写小说时一样。"
                        },
                    )
                }
            } else {
                gridItems(filtered, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        groupName = store.noteGroups.firstOrNull { it.id == note.groupId }?.name,
                        chapterBadge = noteChapterBadge(store, note),
                        onClick = { onOpenNote(note.id) },
                        onMore = { menuNote = note },
                    )
                }
            }
        }
    }

    if (menuNote != null) {
        val note = notes.firstOrNull { it.id == menuNote!!.id } ?: menuNote!!
        ModalSheet(onDismiss = { menuNote = null }) {
            DialogTitle(note.title, "${formatWords(note.words)} 字 · ${relativeTime(note.updatedAt)}", onClose = { menuNote = null })
            Spacer(Modifier.height(13.dp))
            SettingRow("打开笔记", "继续写正文", Glyph.Edit, onClick = {
                menuNote = null
                onOpenNote(note.id)
            })
            RowDivider()
            SettingRow("固定到顶部", if (note.pinned) "已固定" else "按更新时间排序", Glyph.Pin, trailing = {
                InkToggle(note.pinned) { on -> store.updateNote(note.id) { it.copy(pinned = on) } }
            })
            RowDivider()
            SettingRow(
                "移动到分组",
                store.noteGroups.firstOrNull { it.id == note.groupId }?.name ?: "未分组",
                Glyph.Layers,
                onClick = {
                    menuNote = null
                    groupPickerNote = note
                },
            )
            RowDivider()
            SettingRow("导出笔记", "TXT / Markdown / JSON", Glyph.Export, onClick = {
                menuNote = null
                exportingNote = note
            })
            RowDivider()
            SettingRow("删除笔记", "正文和待办会一并删除", Glyph.Trash, onClick = {
                menuNote = null
                deleteNote = note
            })
        }
    }
    if (groupManagerOpen) {
        GroupManagerSheet(GroupKind.NOTE, store) { groupManagerOpen = false }
    }
    if (controlsOpen) {
        ModalSheet(onDismiss = { controlsOpen = false }) {
            DialogTitle("筛选与排布", "状态、分组和布局集中在这里。", onClose = { controlsOpen = false })
            Spacer(Modifier.height(14.dp))
            Txt("状态", size = 10f, color = p.muted)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("全部", "待办", "提醒", "置顶").forEach { item ->
                    GlassChip(item, filter == item) { filter = item }
                }
            }
            Spacer(Modifier.height(15.dp))
            Txt("分组", size = 10f, color = p.muted)
            Spacer(Modifier.height(7.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { GlassChip("全部分组", selectedGroup == null) { selectedGroup = null } }
                item { GlassChip("未分组", selectedGroup == "") { selectedGroup = "" } }
                lazyItems(store.noteGroups, key = { it.id }) { group ->
                    GlassChip(group.name, selectedGroup == group.id) { selectedGroup = group.id }
                }
            }
            Spacer(Modifier.height(15.dp))
            Txt("排布", size = 10f, color = p.muted)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassChip("列表", noteMode != "grid") { store.updateSettings { it.copy(noteMode = "list") } }
                GlassChip("网格", noteMode == "grid") { store.updateSettings { it.copy(noteMode = "grid") } }
                Spacer(Modifier.weight(1f))
                GlassChip("管理分组", false) { controlsOpen = false; groupManagerOpen = true }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    exportingNote?.let { note ->
        NoteExportSheet(note, onDismiss = { exportingNote = null })
    }
    groupPickerNote?.let { note ->
        GroupPickerSheet(
            kind = GroupKind.NOTE,
            current = note.groupId,
            store = store,
            onSelect = { group -> store.setNoteGroup(note.id, group) },
            onDismiss = { groupPickerNote = null },
        )
    }
    if (deleteNote != null) {
        val note = deleteNote!!
        ConfirmDialog(
            "删除「${note.title}」？",
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

@Composable
private fun NoteCard(
    note: Note,
    groupName: String?,
    chapterBadge: String? = null,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val ui = LocalInk.current
    val p = ui.palette
    val pending = note.todos.count { !it.done }
    val next = note.nextReminder()
    MoyuSurface(Modifier.fillMaxWidth(), radius = 24f, flat = true) {
        Column(
            Modifier
                .fillMaxWidth()
                .bouncy(scale = .985f) { onClick() }
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
            if (note.todos.isNotEmpty() || next != null || groupName != null || chapterBadge != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (chapterBadge != null) {
                        NoteBadge(glyph = Glyph.Link, text = chapterBadge, accent = false)
                    }
                    if (groupName != null) {
                        NoteBadge(glyph = Glyph.Layers, text = groupName, accent = false)
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

