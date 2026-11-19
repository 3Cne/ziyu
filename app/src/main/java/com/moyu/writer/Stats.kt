package com.moyu.writer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.max

@Composable
fun StatsScreen(store: AppStore) {
    val ui = LocalInk.current
    val p = ui.palette
    val today = store.todayWords()
    val goal = store.data.settings.dailyGoal.coerceAtLeast(1)
    val progress by animateFloatAsState(
        (today.toFloat() / goal).coerceIn(0f, 1f),
        spring(MOYU_DAMPING, 300f, PROGRESS_THRESHOLD),
        visibilityThreshold = PROGRESS_THRESHOLD,
        label = "goal",
    )
    val total = store.books.sumOf { it.words }
    val week = (6 downTo 0).map { offset ->
        val date = LocalDate.now().minusDays(offset.toLong())
        date to store.wordsOn(date)
    }
    val weekTotal = week.sumOf { it.second }
    val maxDay = max(1, week.maxOfOrNull { it.second } ?: 1)

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 14.dp)) {
                    Txt("数据", size = 34f, weight = FontWeight.SemiBold, letterSpacing = .4f)
                    Spacer(Modifier.height(20.dp))
                }
            }
            item {
                GoalHero(today, goal, progress, store.streak(), total)
            }
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("这一周", "近 7 天共 ${formatWords(weekTotal)} 字")
            }
            item {
                MoyuSurface(Modifier.fillMaxWidth(), radius = 26f, flat = true) {
                    Column(Modifier.padding(17.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            week.forEach { (date, words) ->
                                val dayProgress by animateFloatAsState(
                                    if (words == 0) .025f else (words.toFloat() / maxDay).coerceIn(.08f, 1f),
                                    spring(MOYU_DAMPING, 260f, PROGRESS_THRESHOLD),
                                    visibilityThreshold = PROGRESS_THRESHOLD,
                                    label = "bar_$date",
                                )
                                val isToday = date == LocalDate.now()
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Txt(
                                        if (words > 0) formatWords(words) else "·",
                                        size = 7.5f,
                                        color = if (isToday) p.ink else p.muted,
                                        weight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Box(
                                        Modifier
                                            .width(15.dp)
                                            .height(86.dp)
                                            .clip(SmoothCorner(7.dp))
                                            .background(p.faint),
                                        contentAlignment = Alignment.BottomCenter,
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(dayProgress)
                                                .clip(SmoothCorner(7.dp))
                                                .background(
                                                    if (isToday) ui.accent
                                                    // 3.26.53：非今日柱色随昼夜连续过渡
                                                    // （墨 18% ↔ 白 28%），中点不再硬翻。
                                                    else lerp(
                                                        p.ink.copy(alpha = .18f),
                                                        Color.White.copy(alpha = .28f),
                                                        p.darkAmount,
                                                    )
                                                )
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Txt(
                                        if (isToday) "今天" else date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.CHINA),
                                        size = 8f,
                                        color = if (isToday) p.ink else p.muted,
                                        weight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("${store.streak()}", "连续写作", "天", Modifier.weight(1f), Glyph.Motion)
                    StatCard(formatWords(total), "作品总字数", "字", Modifier.weight(1f), Glyph.Book)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("${store.books.size}", "书架", "本", Modifier.weight(1f), Glyph.Folder)
                    StatCard("${store.notes.size}", "笔记", "条", Modifier.weight(1f), Glyph.Note)
                }
            }
            item {
                Spacer(Modifier.height(9.dp))
                SectionTitle("正在写", if (store.books.isEmpty()) "还没有作品" else "按最近更新排序")
            }
            if (store.books.isEmpty()) {
                item {
                    EmptyState(Glyph.Chart, "还没有可统计的故事", "写下第一段文字，这里就会长出曲线。")
                }
            } else {
                items(store.books.take(4), key = { it.id }) { book ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(SmoothCorner(18.dp))
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookCover(book, Modifier.width(42.dp).height(62.dp), radius = 7f, showText = false)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(book.title, size = 13.5f, weight = FontWeight.Medium, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Txt("${book.chapters.size} 章 · ${formatWords(book.words)} 字", size = 9f, color = p.muted)
                            Spacer(Modifier.height(7.dp))
                            Box(
                                Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(p.line)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((book.words / 80000f).coerceIn(.04f, 1f))
                                        .height(2.dp)
                                        .clip(CircleShape)
                                        .background(ui.accent)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Txt(relativeTime(book.updatedAt), size = 8f, color = p.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalHero(today: Int, goal: Int, progress: Float, streak: Int, total: Int) {
    val ui = LocalInk.current
    val p = ui.palette
    val remaining = (goal - today).coerceAtLeast(0)
    MoyuSurface(Modifier.fillMaxWidth(), radius = 30f, flat = true) {
        Row(Modifier.padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(p.line, style = Stroke(7.dp.toPx()))
                    drawArc(
                        brush = Brush.sweepGradient(listOf(ui.accent.copy(alpha = .45f), ui.accent, ui.accent.copy(alpha = .45f))),
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawCircle(Color.White.copy(alpha = .22f), 1.6.dp.toPx(), Offset(size.width * .5f, 7.dp.toPx()))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt("${(progress * 100).toInt()}", size = 24f, weight = FontWeight.SemiBold, letterSpacing = .5f)
                    Txt("%", size = 9f, color = p.muted)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Txt("今日写作", size = 9.5f, color = p.muted, letterSpacing = .8f)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Txt(formatWords(today), size = 31f, weight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Txt("字", size = 10f, color = p.muted, modifier = Modifier.padding(bottom = 5.dp))
                }
                // Only the numeric remainder is shown. The old line also had a decorative
// sentence on both branches ("今天的目标已经完成。" / "，慢慢写。") and a
// blank row when the goal was already met.
                if (remaining > 0) {
                    Spacer(Modifier.height(4.dp))
                    Txt("还差 ${formatWords(remaining)} 字", size = 10f, color = p.muted)
                }
                Spacer(Modifier.height(11.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    HeroTag("连续 $streak 天")
                    HeroTag("累计 ${formatWords(total)}")
                }
            }
        }
    }
}

@Composable
private fun HeroTag(text: String) {
    val ui = LocalInk.current
    Txt(
        text,
        size = 8.5f,
        color = ui.palette.muted,
        modifier = Modifier
            .clip(CircleShape)
            .background(ui.palette.faint)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun StatCard(value: String, label: String, unit: String, modifier: Modifier = Modifier, glyph: Glyph) {
    val ui = LocalInk.current
    MoyuSurface(modifier, radius = 22f, flat = true) {
        Column(Modifier.padding(15.dp)) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(SmoothCorner(9.dp))
                    .background(ui.palette.faint),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(glyph, ui.palette.muted, 15.dp)
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(value, size = 24f, weight = FontWeight.SemiBold)
                Spacer(Modifier.width(3.dp))
                Txt(unit, size = 8.5f, color = ui.palette.muted, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(2.dp))
            Txt(label, size = 9f, color = ui.palette.muted)
        }
    }
}

