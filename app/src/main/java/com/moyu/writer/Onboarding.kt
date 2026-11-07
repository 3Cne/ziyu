package com.moyu.writer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class OnboardPage(
    val title: String,
    val body: String,
    val glyph: Glyph,
    val features: List<String>,
)

@Composable
fun OnboardingOverlay(onDone: () -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    val pages = listOf(
        OnboardPage(
            "把故事写下来",
            "书架、章节和笔记都留在本机。没有账号，也不会把正文传到任何地方。",
            Glyph.Book,
            listOf("本地存储", "无账号", "自动保存"),
        ),
        OnboardPage(
            "写到哪儿，都接得上",
            "编辑器会记住光标和草稿。离开再回来，仍然停在刚才那句话。",
            Glyph.Edit,
            listOf("光标记忆", "草稿保护", "120Hz"),
        ),
        OnboardPage(
            "待办就在正文里",
            "光标落在待办文字上，工具栏会出现「提醒」。待办、提醒和正文一起保存。",
            Glyph.List,
            listOf("正文内待办", "一键完成", "不跳页面"),
        ),
        OnboardPage(
            "提醒由你定",
            "日期、小时、分钟和秒都可以自己选。提醒会发到通知栏，点击直接回到对应笔记。",
            Glyph.Bell,
            listOf("自定义时间", "精确闹钟", "通知直达"),
        ),
        OnboardPage(
            "纸张和玻璃都能调",
            "空白、虚线、实线三种纸面；所有卡片、栏、弹窗和控件共用同一份玻璃材质。",
            Glyph.Rows,
            listOf("三种纸面", "线条深浅", "统一玻璃"),
        ),
        OnboardPage(
            "带走你的文字",
            "书籍导出 EPUB/TXT，笔记导出 TXT/Markdown/JSON，也可以做本地 JSON 备份。",
            Glyph.Export,
            listOf("EPUB", "TXT / MD", "JSON 备份"),
        ),
        OnboardPage(
            "给你的界面留白",
            "主题、点缀色、字体、行距、页边距、动效和标签栏都能改；不常用的入口可以隐藏。",
            Glyph.Sparkles,
            listOf("六套主题", "字体排版", "动效可调"),
        ),
        OnboardPage(
            "数据只在本机",
            "写作内容保存在本机 JSON 文件里，可以随时导出备份，也可以从备份恢复。",
            Glyph.Lock,
            listOf("本地 JSON", "可备份", "可恢复"),
        ),
    )
    var page by remember { mutableIntStateOf(0) }
    val target = if (pages.isEmpty()) 0f else (page + 1f) / pages.size
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(260),
        label = "onboardingProgress",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(p.paper)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(ui.accent.copy(alpha = .10f), Color.Transparent),
                    center = Offset(size.width * .82f, size.height * .12f),
                    radius = size.width * .82f,
                ),
                radius = size.width * .82f,
                center = Offset(size.width * .82f, size.height * .12f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .20f), Color.Transparent),
                    center = Offset(size.width * .12f, size.height * .78f),
                    radius = size.width * .72f,
                ),
                radius = size.width * .72f,
                center = Offset(size.width * .12f, size.height * .78f),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(p.ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("字", size = 10f, color = p.paper, weight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(9.dp))
                Txt("字隅", size = 11f, color = p.ink, weight = FontWeight.Medium, letterSpacing = .8f)
                Spacer(Modifier.weight(1f))
                Txt(
                    "${page + 1} / ${pages.size}",
                    size = 9f,
                    color = p.muted,
                )
                Spacer(Modifier.width(12.dp))
                Txt(
                    "跳过",
                    size = 9f,
                    color = p.muted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .bouncy(scale = .95f) { onDone() },
                )
            }

            Spacer(Modifier.weight(.55f))

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(120)))
                },
                label = "onboardingPage",
            ) { index ->
                val item = pages[index]
                Column(Modifier.fillMaxWidth()) {
                    Txt(
                        "0${index + 1}",
                        size = 10f,
                        color = ui.accent,
                        letterSpacing = 2f,
                        weight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .size(66.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(ui.accent.copy(alpha = .10f))
                            .border(1.dp, ui.accent.copy(alpha = .14f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineIcon(item.glyph, ui.accent, 28.dp, 1.7f)
                    }
                    Spacer(Modifier.height(22.dp))
                    Txt(
                        item.title,
                        size = 28f,
                        weight = FontWeight.SemiBold,
                        color = p.ink,
                        lineHeight = 34f,
                    )
                    Spacer(Modifier.height(11.dp))
                    Txt(
                        item.body,
                        size = 13f,
                        color = p.muted,
                        lineHeight = 22f,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        item.features.forEach { feature ->
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(p.faint)
                                    .border(.7.dp, p.line, CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Txt(feature, size = 8.5f, color = p.muted)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(.65f))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(p.line),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(ui.accent),
                )
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                if (page == pages.lastIndex) "开始写" else "下一步",
                {
                    if (page == pages.lastIndex) onDone() else page += 1
                },
                Modifier.fillMaxWidth(),
                glyph = if (page == pages.lastIndex) Glyph.Check else Glyph.Chevron,
            )
        }
    }
}
