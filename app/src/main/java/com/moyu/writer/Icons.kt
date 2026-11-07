package com.moyu.writer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph {
    Back, Chevron, ChevronDown, Plus, More, Search, Close, Check, Trash, Edit, Export,
    Share, Pin, Clock, Calendar, Bell, List, Layers, Palette, Font, Motion, Download,
    Upload, Info, Undo, Redo, Indent, Quote, Divider, Focus, Keyboard, Sun, Moon,
    Grid, Rows, Sparkles, Book, Note, Chart, Gear, Folder, ArrowUp, Lock, Link,
}

@Composable
fun LineIcon(
    glyph: Glyph,
    color: Color,
    size: Dp = 22.dp,
    strokeWidth: Float = 1.7f,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(
            width = strokeWidth * unit,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = strokeWidth) {
            drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = w * unit, cap = StrokeCap.Round)
        }
        fun path(vararg pts: Pair<Float, Float>, close: Boolean = false, fill: Boolean = false, w: Float = strokeWidth) {
            val pp = Path()
            pts.forEachIndexed { i, (x, y) ->
                if (i == 0) pp.moveTo(x * unit, y * unit) else pp.lineTo(x * unit, y * unit)
            }
            if (close) pp.close()
            if (fill) drawPath(pp, color) else drawPath(pp, color, style = Stroke(w * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        when (glyph) {
            Glyph.Back -> path(15f to 5f, 8f to 12f, 15f to 19f)
            Glyph.Chevron -> path(9f to 5f, 16f to 12f, 9f to 19f)
            Glyph.ChevronDown -> path(5f to 9f, 12f to 16f, 19f to 9f)
            Glyph.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Glyph.More -> {
                drawCircle(color, 1.25f * unit, p(5.5f, 12f))
                drawCircle(color, 1.25f * unit, p(12f, 12f))
                drawCircle(color, 1.25f * unit, p(18.5f, 12f))
            }
            Glyph.Search -> {
                drawCircle(color, 5.4f * unit, p(10.5f, 10.5f), style = stroke)
                line(14.5f, 14.5f, 19.5f, 19.5f)
            }
            Glyph.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Glyph.Check -> path(4.5f to 12.5f, 9.5f to 17f, 19.5f to 7f)
            Glyph.Trash -> {
                line(5f, 7f, 19f, 7f); line(9f, 7f, 9.5f, 4.5f); line(14.5f, 4.5f, 15f, 7f)
                path(7f to 7f, 8f to 20f, 16f to 20f, 17f to 7f)
                line(10.5f, 10.5f, 10.8f, 17f); line(13.5f, 10.5f, 13.2f, 17f)
            }
            Glyph.Edit -> {
                path(4f to 17f, 5f to 20f, 8f to 19f, 19.5f to 7.5f, 16.5f to 4.5f, 5f to 16f, close = true)
                line(14.5f, 6.5f, 17.5f, 9.5f)
            }
            Glyph.Export -> {
                path(12f to 15f, 12f to 3.5f); path(7.5f to 8f, 12f to 3.5f, 16.5f to 8f)
                path(4.5f to 13f, 4.5f to 20f, 19.5f to 20f, 19.5f to 13f)
            }
            Glyph.Share -> {
                drawCircle(color, 2.2f * unit, p(18f, 5.5f), style = stroke)
                drawCircle(color, 2.2f * unit, p(6f, 12f), style = stroke)
                drawCircle(color, 2.2f * unit, p(18f, 18.5f), style = stroke)
                line(8f, 11f, 16f, 6.6f); line(8f, 13f, 16f, 17.2f)
            }
            Glyph.Pin -> {
                path(9f to 3.5f, 15f to 9.5f, 12f to 12.5f, 9f to 9.5f, close = true)
                line(12f, 12.5f, 12f, 20.5f); line(8f, 20.5f, 16f, 20.5f)
            }
            Glyph.Clock -> {
                drawCircle(color, 8.2f * unit, center = p(12f, 12f), style = Stroke(1.7f * unit))
                line(12f, 7f, 12f, 12f); line(12f, 12f, 16f, 14f)
            }
            Glyph.Calendar -> {
                drawRoundRect(color, p(4f, 5.5f), Size(16f * unit, 15f * unit), 2.5f * unit, style = stroke)
                line(4f, 10f, 20f, 10f); line(8f, 3.5f, 8f, 7f); line(16f, 3.5f, 16f, 7f)
            }
            Glyph.Bell -> {
                path(6.5f to 17f, 6.5f to 11f, 8.5f to 6f, 12f to 4.5f, 15.5f to 6f, 17.5f to 11f, 17.5f to 17f)
                line(4.5f, 17f, 19.5f, 17f); path(10f to 20f, 12f to 21f, 14f to 20f)
            }
            Glyph.List -> {
                line(9f, 6f, 20f, 6f); line(9f, 12f, 20f, 12f); line(9f, 18f, 20f, 18f)
                drawCircle(color, 1.05f * unit, p(5f, 6f)); drawCircle(color, 1.05f * unit, p(5f, 12f)); drawCircle(color, 1.05f * unit, p(5f, 18f))
            }
            Glyph.Layers -> {
                path(12f to 3.5f, 21f to 8f, 12f to 12.5f, 3f to 8f, close = true)
                path(3f to 12.5f, 12f to 17f, 21f to 12.5f)
                path(3f to 16.5f, 12f to 21f, 21f to 16.5f)
            }
            Glyph.Palette -> {
                drawCircle(color, 8f * unit, p(12f, 12f), style = stroke)
                drawCircle(color, 1.2f * unit, p(8f, 9f)); drawCircle(color, 1.2f * unit, p(12f, 7.5f))
                drawCircle(color, 1.2f * unit, p(16f, 9.5f)); drawCircle(color, 1.2f * unit, p(11.5f, 13.5f))
            }
            Glyph.Font -> {
                line(5f, 18f, 11f, 5f); line(11f, 5f, 17f, 18f); line(7.5f, 13f, 14.5f, 13f)
            }
            Glyph.Motion -> {
                val pp = Path()
                pp.moveTo(3f * unit, 15.5f * unit)
                pp.cubicTo(7f * unit, 4f * unit, 12f * unit, 21f * unit, 20f * unit, 8f * unit)
                drawPath(pp, color, style = Stroke(1.7f * unit, cap = StrokeCap.Round))
                drawCircle(color, 1.5f * unit, p(20f, 8f))
            }
            Glyph.Download -> {
                path(12f to 4f, 12f to 15f); path(7.5f to 10.5f, 12f to 15f, 16.5f to 10.5f)
                line(4.5f, 20f, 19.5f, 20f)
            }
            Glyph.Upload -> {
                path(12f to 15f, 12f to 4f); path(7.5f to 8.5f, 12f to 4f, 16.5f to 8.5f)
                line(4.5f, 20f, 19.5f, 20f)
            }
            Glyph.Info -> {
                drawCircle(color, 8.4f * unit, p(12f, 12f), style = stroke)
                line(12f, 10.5f, 12f, 16.5f); drawCircle(color, 1f * unit, p(12f, 7.5f))
            }
            Glyph.Undo -> {
                val pp = Path(); pp.moveTo(5f * unit, 10f * unit); pp.cubicTo(10f * unit, 5f * unit, 19f * unit, 7f * unit, 19f * unit, 15f * unit)
                pp.cubicTo(19f * unit, 18f * unit, 17f * unit, 20f * unit, 15f * unit, 20.5f * unit)
                drawPath(pp, color, style = Stroke(1.7f * unit, cap = StrokeCap.Round))
                path(9.5f to 5.5f, 4.5f to 10f, 10.5f to 12.5f)
            }
            Glyph.Redo -> {
                val pp = Path(); pp.moveTo(19f * unit, 10f * unit); pp.cubicTo(14f * unit, 5f * unit, 5f * unit, 7f * unit, 5f * unit, 15f * unit)
                pp.cubicTo(5f * unit, 18f * unit, 7f * unit, 20f * unit, 9f * unit, 20.5f * unit)
                drawPath(pp, color, style = Stroke(1.7f * unit, cap = StrokeCap.Round))
                path(14.5f to 5.5f, 19.5f to 10f, 13.5f to 12.5f)
            }
            Glyph.Indent -> {
                line(10f, 6f, 20f, 6f); line(10f, 12f, 20f, 12f); line(10f, 18f, 20f, 18f)
                path(4f to 8.5f, 7.5f to 12f, 4f to 15.5f)
            }
            Glyph.Quote -> {
                path(5f to 8f, 9f to 8f, 9f to 12f, 5.5f to 12f, 5f to 16f)
                path(13f to 8f, 17f to 8f, 17f to 12f, 13.5f to 12f, 13f to 16f)
            }
            Glyph.Divider -> {
                line(3f, 12f, 21f, 12f)
                drawCircle(color, .95f * unit, p(7f, 8f)); drawCircle(color, .95f * unit, p(12f, 8f)); drawCircle(color, .95f * unit, p(17f, 8f))
            }
            Glyph.Focus -> {
                path(4f to 9f, 4f to 4f, 9f to 4f); path(15f to 4f, 20f to 4f, 20f to 9f)
                path(20f to 15f, 20f to 20f, 15f to 20f); path(9f to 20f, 4f to 20f, 4f to 15f)
            }
            Glyph.Keyboard -> {
                drawRoundRect(color, p(3f, 6f), Size(18f * unit, 12f * unit), 2.5f * unit, style = stroke)
                line(6.5f, 10f, 7.5f, 10f); line(10.5f, 10f, 11.5f, 10f); line(14.5f, 10f, 15.5f, 10f)
                line(6.5f, 13.5f, 7.5f, 13.5f); line(10.5f, 13.5f, 11.5f, 13.5f); line(14.5f, 13.5f, 17.5f, 13.5f)
            }
            Glyph.Sun -> {
                drawCircle(color, 4f * unit, p(12f, 12f), style = stroke)
                listOf(12f to 2.5f, 12f to 5f, 12f to 19f, 12f to 21.5f, 2.5f to 12f, 5f to 12f, 19f to 12f, 21.5f to 12f).forEach { (x, y) -> line(12f, 12f, x, y) }
            }
            Glyph.Moon -> {
                val pp = Path(); pp.moveTo(19f * unit, 15f * unit)
                pp.cubicTo(12f * unit, 18f * unit, 6f * unit, 12f * unit, 9f * unit, 5f * unit)
                pp.cubicTo(4f * unit, 8f * unit, 4.5f * unit, 17f * unit, 12f * unit, 19f * unit)
                pp.cubicTo(15f * unit, 20f * unit, 18f * unit, 18f * unit, 19f * unit, 15f * unit)
                drawPath(pp, color, style = Stroke(1.7f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Glyph.Grid -> {
                drawRoundRect(color, p(4f, 4f), Size(6.5f * unit, 6.5f * unit), 1.5f * unit, style = stroke)
                drawRoundRect(color, p(13.5f, 4f), Size(6.5f * unit, 6.5f * unit), 1.5f * unit, style = stroke)
                drawRoundRect(color, p(4f, 13.5f), Size(6.5f * unit, 6.5f * unit), 1.5f * unit, style = stroke)
                drawRoundRect(color, p(13.5f, 13.5f), Size(6.5f * unit, 6.5f * unit), 1.5f * unit, style = stroke)
            }
            Glyph.Rows -> {
                drawRoundRect(color, p(4f, 5f), Size(16f * unit, 4f * unit), 1.4f * unit, style = stroke)
                drawRoundRect(color, p(4f, 11f), Size(16f * unit, 4f * unit), 1.4f * unit, style = stroke)
                drawRoundRect(color, p(4f, 17f), Size(16f * unit, 3f * unit), 1.4f * unit, style = stroke)
            }
            Glyph.Sparkles -> {
                path(8f to 3f, 9.4f to 7.4f, 13.8f to 9f, 9.4f to 10.6f, 8f to 15f, 6.6f to 10.6f, 2.2f to 9f, 6.6f to 7.4f, close = true, fill = true)
                path(17f to 12f, 18f to 15f, 21f to 16f, 18f to 17f, 17f to 20f, 16f to 17f, 13f to 16f, 16f to 15f, close = true, fill = true)
            }
            Glyph.Book -> {
                drawRoundRect(color, p(5f, 3.5f), Size(14f * unit, 17f * unit), 2f * unit, style = stroke)
                line(8.5f, 3.5f, 8.5f, 20.5f)
            }
            Glyph.Note -> {
                path(5f to 3.5f, 15f to 3.5f, 19f to 7.5f, 19f to 20.5f, 5f to 20.5f, close = true)
                path(14.5f to 3.8f, 14.5f to 8f, 19f to 8f)
                line(8f, 12f, 16f, 12f); line(8f, 15.5f, 14f, 15.5f)
            }
            Glyph.Chart -> {
                line(4f, 20f, 20f, 20f)
                drawRoundRect(color, p(5.5f, 12f), Size(3f * unit, 6f * unit), 1f * unit, style = stroke)
                drawRoundRect(color, p(10.5f, 8f), Size(3f * unit, 10f * unit), 1f * unit, style = stroke)
                drawRoundRect(color, p(15.5f, 4.5f), Size(3f * unit, 13.5f * unit), 1f * unit, style = stroke)
            }
            Glyph.Gear -> {
                drawCircle(color, 3.4f * unit, p(12f, 12f), style = stroke)
                drawCircle(color, 7.4f * unit, p(12f, 12f), style = Stroke(1.7f * unit))
                listOf(12f to 2.5f, 12f to 5.5f, 12f to 18.5f, 12f to 21.5f, 2.5f to 12f, 5.5f to 12f, 18.5f to 12f, 21.5f to 12f).forEach { (x, y) -> line(12f, 12f, x, y) }
            }
            Glyph.Folder -> {
                path(3.5f to 6f, 9f to 6f, 11f to 8.5f, 20.5f to 8.5f, 20.5f to 19f, 3.5f to 19f, close = true)
            }
            Glyph.ArrowUp -> {
                path(12f to 19f, 12f to 5f); path(6f to 11f, 12f to 5f, 18f to 11f)
            }
            Glyph.Lock -> {
                drawRoundRect(color, p(5f, 10f), Size(14f * unit, 10.5f * unit), 2.5f * unit, style = stroke)
                path(8f to 10f, 8f to 7.5f, 9f to 5f, 12f to 4f, 15f to 5f, 16f to 7.5f, 16f to 10f)
                drawCircle(color, 1.2f * unit, p(12f, 15f))
            }
            Glyph.Link -> {
                path(10f to 14f, 14f to 10f)
                path(13f to 6.5f, 14.4f to 5.1f, 16.3f to 4f, 18.3f to 4f, 20f to 5.7f, 20f to 7.7f,
                    18.9f to 9.6f, 17.5f to 11f)
                path(11f to 17.5f, 9.6f to 18.9f, 7.7f to 20f, 5.7f to 20f, 4f to 18.3f, 4f to 16.3f,
                    5.1f to 14.4f, 6.5f to 13f)
            }
        }
    }
}

private fun DrawScope.drawRoundRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    radius: Float,
    style: Stroke,
) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        style = style,
    )
}
