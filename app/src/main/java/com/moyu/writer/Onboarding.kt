package com.moyu.writer

import androidx.activity.compose.BackHandler
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

/**
 * One onboarding page: a section label ("上手 · 2 / 5"), what this screen is for, and
 * the literal taps that get there. The steps exist because a first-run reader should be
 * able to do the thing, not just read that it exists.
 */
private data class OnboardPage(
    val section: String,
    val title: String,
    val body: String,
    val glyph: Glyph,
    val steps: List<String>,
)

private val ONBOARD_PAGES = listOf(
    OnboardPage(
        "上手 · 1 / 5",
        "第一本书",
        "素纸没有账号，打开就能写。书架上的每一本，都只存在这台手机里。",
        Glyph.Book,
        listOf(
            "底部第 1 个标签是「书架」",
            "点右上「+」，填一个书名就能创建",
            "作者、分组、简介都可以留空",
        ),
    ),
    OnboardPage(
        "上手 · 2 / 5",
        "写下第一章",
        "编辑器和纸一样直接，点进正文就能打字；每一章单独保存。",
        Glyph.Edit,
        listOf(
            "书详情里点「继续写作」，回到上次那一章",
            "点目录右侧的「+」新开一章",
            "点亮正文就能打字，自动保存",
        ),
    ),
    OnboardPage(
        "上手 · 3 / 5",
        "记一条笔记",
        "灵感、待办、片段不用挤进正文，笔记和书是分开的两件事。",
        Glyph.Note,
        listOf(
            "底部第 2 个标签是「笔记」",
            "点右上「+」新建一条笔记",
            "正文里以「☐」开头的那行就是待办",
        ),
    ),
    OnboardPage(
        "上手 · 4 / 5",
        "问这本书",
        "问书读的是你写的正文和笔记；没写到的，它会直接说没写到。",
        Glyph.Sparkles,
        listOf(
            "底部第 4 个标签是「问书」",
            "在「问这本书的任何事」里直接打字",
            "只想问某一段，先点输入框左边的链接按钮「引用」",
        ),
    ),
    OnboardPage(
        "上手 · 5 / 5",
        "文字带得走",
        "写下的东西不会被锁在这个 App 里，随时能导出成文件。",
        Glyph.Export,
        listOf(
            "书详情里点「导出」→ EPUB 或 TXT",
            "笔记里「导出笔记」→ TXT / Markdown / JSON",
            "设置 →「备份与恢复」→「导出备份」带走全部",
        ),
    ),
    OnboardPage(
        "功能 · 1 / 8",
        "书架",
        "一本书一个位置；写得多以后，分组比排序更省事。",
        Glyph.Book,
        listOf(
            "点封面进详情，「继续写作」回到上次那一章",
            "书架点「筛选」，底部有「分组」可新建、改名",
            "书详情里能改书名封面，也能继续加章节",
        ),
    ),
    OnboardPage(
        "功能 · 2 / 8",
        "工具栏",
        "常写的动作都排在键盘上方这一行，左右滑能看到全部。",
        Glyph.Rows,
        listOf(
            "撤销、重做、缩进、引号、分节",
            "「待办」和「提醒」只在笔记里出现",
            "「排版」改字体、字号、行距、页边距",
            "右上三点换纸面；左下 ⌄ 收起整条",
        ),
    ),
    OnboardPage(
        "功能 · 3 / 8",
        "改稿与校核",
        "长按选中一段，就只对这一段动手，不会牵动别处。",
        Glyph.Check,
        listOf(
            "长按选词，拖动两端手柄改范围",
            "工具栏变成 校核 / 复制 / 全选 / 剪切 / 粘贴 / 删除",
            "「校核」给出建议后，逐条点「采纳」或「别改」",
        ),
    ),
    OnboardPage(
        "功能 · 4 / 8",
        "笔记还能做什么",
        "笔记不只是文本：待办能勾、提醒会到点响、还能挂到某一章上。",
        Glyph.List,
        listOf(
            "点方框勾选待办，改完立刻保存",
            "光标停在待办上，工具栏出现「提醒」，可选日期和时间",
            "笔记详情里「关联章节」把它挂到某一章",
            "写作时点「笔记」，把它悬浮在正文右侧",
        ),
    ),
    OnboardPage(
        "功能 · 5 / 8",
        "问书比你想到的多",
        "它记得你写过的设定，也能替你改；但每次改动都要你点头。",
        Glyph.Sparkles,
        listOf(
            "「对话 / 记忆」两个页签；记忆分这本书和跨书偏好",
            "顶部能切书，也能引用别的书来问",
            "让它改书、建章、设提醒，说完就生效；删除前会再问你一次，改错了点「撤销」",
            "回答下方「用了 N 个工具」可展开看每一步",
        ),
    ),
    OnboardPage(
        "功能 · 6 / 8",
        "数据",
        "写了多少、连着写了几天，这里一眼看到。",
        Glyph.Chart,
        listOf(
            "每日目标在设置里改，数据页按目标算进度",
            "柱状图是按天统计的字数",
            "「连续写作」按当天有没有写来算",
        ),
    ),
    OnboardPage(
        "功能 · 7 / 8",
        "外观",
        "主题、字体、动效都能调；标签栏和按钮的玻璃也能单独调。",
        Glyph.Palette,
        listOf(
            "外观里换主题、点缀色、字体、行距、页边距",
            "动效可以调快慢",
            "外观 →「玻璃」调标签栏、输入框、按钮",
        ),
    ),
    OnboardPage(
        "功能 · 8 / 8",
        "只在你的手机里",
        "没有云同步，也不后台上传；换机靠一份备份文件。",
        Glyph.Lock,
        listOf(
            "写作内容存在本机，卸载 App 会一起删掉",
            "设置 →「备份与恢复」→「导出备份」",
            "换机后同一页「恢复备份」，会先确认再覆盖",
            "导出的 EPUB / TXT 在「下载/字隅」里",
        ),
    ),
)

@Composable
fun OnboardingOverlay(onDone: () -> Unit) {
    val ui = LocalInk.current
    val p = ui.palette
    val pages = ONBOARD_PAGES
    var page by remember { mutableIntStateOf(0) }
    val target = if (pages.isEmpty()) 0f else (page + 1f) / pages.size
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(260),
        visibilityThreshold = PROGRESS_THRESHOLD,
        label = "onboardingProgress",
    )

    // 首页按返回等于跳过：刚打开就卡在一页上没有意义。
    BackHandler {
        if (page > 0) page -= 1 else onDone()
    }

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
                Txt("素纸", size = 11f, color = p.ink, weight = FontWeight.Medium, letterSpacing = .8f)
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

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(180))) togetherWith
                                (slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(120)))
                        } else {
                            (slideInHorizontally(tween(260)) { -it / 5 } + fadeIn(tween(180))) togetherWith
                                (slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(120)))
                        }
                    },
                    label = "onboardingPage",
                ) { index ->
                    val item = pages[index]
                    Column(Modifier.fillMaxWidth()) {
                        Txt(
                            item.section,
                            size = 9.5f,
                            color = ui.accent,
                            letterSpacing = 1.6f,
                            weight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(
                            Modifier
                                .size(60.dp)
                                .clip(SmoothCorner(18.dp))
                                .background(ui.accent.copy(alpha = .10f))
                                .border(1.dp, ui.accent.copy(alpha = .14f), SmoothCorner(18.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            LineIcon(item.glyph, ui.accent, 26.dp, 1.7f)
                        }
                        Spacer(Modifier.height(18.dp))
                        Txt(
                            item.title,
                            size = 28f,
                            weight = FontWeight.SemiBold,
                            color = p.ink,
                            lineHeight = 34f,
                        )
                        Spacer(Modifier.height(10.dp))
                        Txt(
                            item.body,
                            size = 13f,
                            color = p.muted,
                            lineHeight = 22f,
                        )
                        Spacer(Modifier.height(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            item.steps.forEachIndexed { i, step ->
                                Row(Modifier.fillMaxWidth()) {
                                    Txt(
                                        "${i + 1}",
                                        size = 9f,
                                        color = ui.accent,
                                        weight = FontWeight.Medium,
                                        modifier = Modifier.width(15.dp),
                                    )
                                    Txt(
                                        step,
                                        size = 12.5f,
                                        color = p.ink.copy(alpha = .88f),
                                        lineHeight = 20f,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

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
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page > 0) {
                    // 不用 GlassButton：它内部的 Row 是 fillMaxSize，在 Row 里会连宽度一起
                    // 吃到满行，把「下一步」挤没。这里按同一个 dockTone 药丸自己画。
                    Row(
                        Modifier
                            .height(50.dp)
                            .clip(CircleShape)
                            .background(dockTone(ui))
                            .bouncy { page -= 1 }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LineIcon(
                            Glyph.Back,
                            p.ink,
                            18.dp,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Txt("上一步", size = 14f, color = p.ink, weight = FontWeight.Medium)
                    }
                }
                PrimaryButton(
                    if (page == pages.lastIndex) "开始写" else "下一步",
                    {
                        if (page == pages.lastIndex) onDone() else page += 1
                    },
                    Modifier.weight(1f),
                    glyph = if (page == pages.lastIndex) Glyph.Check else Glyph.Chevron,
                )
            }
        }
    }
}
