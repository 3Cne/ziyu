package com.moyu.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val groupColors = listOf(
    0xFF8A7A62, // 茶褐
    0xFF6E7F86, // 雾蓝
    0xFF9A6A5C, // 砖红
    0xFF7A7F5A, // 苔绿
    0xFF8A6F86, // 藕紫
    0xFF5F6368, // 墨灰
)

@Composable
fun GroupFilterBar(
    groups: List<Group>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = "全部",
    ungroupedLabel: String = "未分组",
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { GroupChip(allLabel, null, selected == null, null) { onSelect(null) } }
            item { GroupChip(ungroupedLabel, "", selected == "", null) { onSelect("") } }
            items(groups, key = { it.id }) { group ->
                GroupChip(group.name, group.id, selected == group.id, group.color) { onSelect(group.id) }
            }
        }
        Spacer(Modifier.width(7.dp))
        Row(
            Modifier
                .clip(CircleShape)
                .background(LocalInk.current.palette.faint)
                .border(.8.dp, LocalInk.current.palette.line, CircleShape)
                .clickable { onManage() }
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(Glyph.Layers, LocalInk.current.palette.muted, 13.dp, 1.5f)
            Spacer(Modifier.width(4.dp))
            Txt("分组", size = 10.5f, color = LocalInk.current.palette.muted, weight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GroupChip(
    label: String,
    id: String?,
    selected: Boolean,
    color: Long?,
    onClick: () -> Unit,
) {
    val p = LocalInk.current.palette
    Row(
        Modifier
            .bouncy(scale = .94f) { onClick() }
            .clip(CircleShape)
            .background(if (selected) p.ink else p.faint)
            .then(if (selected) Modifier else Modifier.border(.8.dp, p.line, CircleShape))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color != null) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (selected) p.bgTop.copy(alpha = .8f) else Color(color))
            )
            Spacer(Modifier.width(5.dp))
        }
        Txt(
            label,
            size = 10.5f,
            color = if (selected) p.bgTop else p.muted,
            weight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun GroupDot(color: Long, size: Int = 9) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(color))
    )
}

@Composable
fun GroupManagerSheet(
    kind: GroupKind,
    store: AppStore,
    onDismiss: () -> Unit,
) {
    val ui = LocalInk.current
    val groups = if (kind == GroupKind.BOOK) store.bookGroups else store.noteGroups
    val counts = if (kind == GroupKind.BOOK) {
        store.data.books.groupingBy { it.groupId ?: "" }.eachCount()
    } else {
        store.data.notes.groupingBy { it.groupId ?: "" }.eachCount()
    }
    var editing by remember { mutableStateOf<Group?>(null) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(groupColors.first()) }
    var deleteTarget by remember { mutableStateOf<Group?>(null) }
    val label = if (kind == GroupKind.BOOK) "书籍分组" else "笔记分组"

    ModalSheet(onDismiss = onDismiss, imeAware = true) {
        DialogTitle(
            if (creating) "新建分组" else if (editing != null) "编辑分组" else label,
            if (creating || editing != null) "名字和颜色随时可以再改。" else "点分组可改名、换色、排序；删除只移回未分组。",
            onClose = {
                if (creating || editing != null) {
                    creating = false
                    editing = null
                } else onDismiss()
            },
        )
        Spacer(Modifier.height(14.dp))
        if (creating || editing != null) {
            Txt("分组名", size = 10.5f, color = ui.palette.muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            InkField(name, { name = it.take(16) }, "例如：短篇、灵感、资料", onDone = {})
            Spacer(Modifier.height(13.dp))
            Txt("颜色", size = 10.5f, color = ui.palette.muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items(groupColors) { c ->
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
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (editing != null) {
                    GlassButton("删除", { deleteTarget = editing }, Modifier.weight(1f))
                }
                PrimaryButton(if (creating) "创建" else "保存", {
                    val clean = name.trim()
                    if (clean.isBlank()) {
                        ToastBus.show("先给分组建个名字")
                    } else if (creating) {
                        store.addGroup(kind, Group(name = clean, color = color))
                        creating = false
                        name = ""
                        color = groupColors.first()
                    } else {
                        editing?.let { g -> store.updateGroup(kind, g.id) { it.copy(name = clean, color = color) } }
                        editing = null
                    }
                }, Modifier.weight(1f))
            }
        } else {
            if (groups.isEmpty()) {
                Txt("还没有分组。先建一个，把书或笔记放进去。", size = 12f, color = ui.palette.muted, lineHeight = 19f)
                Spacer(Modifier.height(14.dp))
            } else {
                groups.forEach { g ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(SmoothCorner(14.dp))
                            .clickable { editing = g; name = g.name; color = g.color }
                            .padding(vertical = 9.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GroupDot(g.color, 10)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(g.name, size = 14f, weight = FontWeight.Medium)
                            Txt("${counts[g.id] ?: 0} 项", size = 9.5f, color = ui.palette.muted)
                        }
                        Txt("↑", Modifier.clickable { store.moveGroup(kind, g.id, -1) }.padding(8.dp), size = 13f, color = ui.palette.muted)
                        Txt("↓", Modifier.clickable { store.moveGroup(kind, g.id, 1) }.padding(8.dp), size = 13f, color = ui.palette.muted)
                        LineIcon(Glyph.Edit, ui.palette.muted, 15.dp)
                    }
                    RowDivider()
                }
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("新建分组", {
                creating = true
                name = ""
                color = groupColors.first()
            }, Modifier.fillMaxWidth(), glyph = Glyph.Plus)
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            "删除分组「${target.name}」？",
            "分组里的${if (kind == GroupKind.BOOK) "书" else "笔记"}会移回未分组，不会删除内容。",
            "删除分组",
            {
                store.deleteGroup(kind, target.id)
                deleteTarget = null
                editing = null
            },
            { deleteTarget = null },
            destructive = true,
        )
    }
}

@Composable
fun GroupPickerSheet(
    kind: GroupKind,
    current: String?,
    store: AppStore,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    /** true = 作为「菜单 → 选项页」同框层级的第二层画在面板里（带返回箭头），
     *  不再自己开一个 ModalSheet。 */
    embedded: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val groups = if (kind == GroupKind.BOOK) store.bookGroups else store.noteGroups
    val subtitle = if (kind == GroupKind.BOOK) "书可以随时换组。" else "笔记可以随时换组。"
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        GroupPickerRow("未分组", null, current == null, groupColors.first()) {
            onSelect(null); onDismiss()
        }
        groups.forEach { g ->
            RowDivider()
            GroupPickerRow(g.name, g.id, current == g.id, g.color) {
                onSelect(g.id); onDismiss()
            }
        }
    }
    if (embedded) {
        Column(Modifier.fillMaxWidth()) {
            SheetPageHeader("移动到分组", subtitle, onBack ?: onDismiss)
            Spacer(Modifier.height(12.dp))
            body()
        }
    } else {
        ModalSheet(onDismiss = onDismiss) {
            DialogTitle("移动到分组", subtitle, onClose = onDismiss)
            Spacer(Modifier.height(12.dp))
            body()
        }
    }
}

@Composable
private fun GroupPickerRow(
    name: String,
    id: String?,
    selected: Boolean,
    color: Long,
    onClick: () -> Unit,
) {
    val p = LocalInk.current.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SmoothCorner(14.dp))
            .clickable { onClick() }
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupDot(color, 10)
        Spacer(Modifier.width(10.dp))
        Txt(name, size = 14f, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (selected) LineIcon(Glyph.Check, p.ink, 16.dp)
    }
}
