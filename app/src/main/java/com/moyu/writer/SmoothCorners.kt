package com.moyu.writer

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Continuous-curvature rounded rectangle (the "squircle" corner).
 *
 * A plain RoundedCornerShape joins a straight edge to a circular arc, so
 * curvature jumps from 0 to 1/R at the tangent point. This uses Figma's
 * corner-smoothing construction: the straight edge ends earlier and three
 * cubic segments ease curvature in around a real arc. Same bounding box, same
 * nominal radius, visibly softer corner.
 *
 * Ported from MartinRGB/Figma_Squircles_Approximation via the figma_squircle
 * Rust crate (docs.rs/figma_squircle/0.1.0); no dependency is added.
 */
private const val SMOOTHING = .6f

private class Corner(
    val radius: Float,
    val p: Float,
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val arc: Float,
    val length: Float,
) {
    val empty: Boolean get() = radius <= .01f
}

private fun cornerFor(radiusPx: Float, budget: Float): Corner {
    val r = radiusPx.coerceIn(0f, budget)
    if (r <= .01f) return Corner(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    var s = SMOOTHING
    val maxS = (budget / r) - 1f
    if (maxS < s) s = maxS.coerceAtLeast(0f)
    val p = (1f + s) * r
    val arc = 90f * (1f - s)
    val length = sin(Math.toRadians((arc / 2f).toDouble())).toFloat() * r * sqrt(2f)
    val alpha = (90f - arc) / 2f
    val p3p4 = r * tan(Math.toRadians((alpha / 2f).toDouble())).toFloat()
    val beta = 45f * s
    val c = p3p4 * cos(Math.toRadians(beta.toDouble())).toFloat()
    val d = c * tan(Math.toRadians(beta.toDouble())).toFloat()
    val b = (p - length - c - d) / 3f
    val a = 2f * b
    return Corner(r, p, a, b, c, d, arc, length)
}

fun SmoothCorner(radius: Dp): Shape =
    SmoothCornerShape(radius, radius, radius, radius)

fun SmoothCorner(percent: Int): Shape = CircleShape

fun SmoothCorner(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): Shape = SmoothCornerShape(topStart, topEnd, bottomEnd, bottomStart)

/** Drop-in [Shape] with continuous-curvature corners. */
class SmoothCornerShape(
    private val topStart: Dp = 0.dp,
    private val topEnd: Dp = 0.dp,
    private val bottomEnd: Dp = 0.dp,
    private val bottomStart: Dp = 0.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Rectangle(Rect(0f, 0f, w, h))
        val budget = min(w, h) / 2f
        fun px(dp: Dp) = with(density) { dp.toPx() }
        val tl = cornerFor(px(topStart), budget)
        val tr = cornerFor(px(topEnd), budget)
        val br = cornerFor(px(bottomEnd), budget)
        val bl = cornerFor(px(bottomStart), budget)
        if (tl.empty && tr.empty && br.empty && bl.empty) {
            return Outline.Rectangle(Rect(0f, 0f, w, h))
        }
        val path = Path()
        path.moveTo(w - tr.p, 0f)
        // Top-right
        if (!tr.empty) {
            path.relativeCubicTo(tr.a, 0f, tr.a + tr.b, 0f, tr.a + tr.b + tr.c, tr.d)
            path.arcSection(w - tr.radius, tr.radius, w - tr.length - tr.d, tr.d, tr)
            path.relativeCubicTo(tr.d, tr.c, tr.d, tr.b + tr.c, tr.d, tr.a + tr.b + tr.c)
        }
        path.lineTo(w, h - br.p)
        // Bottom-right
        if (!br.empty) {
            path.relativeCubicTo(0f, br.a, 0f, br.a + br.b, -br.d, br.a + br.b + br.c)
            path.arcSection(w - br.radius, h - br.radius, w - br.d, h - br.length - br.d, br)
            path.relativeCubicTo(-br.c, br.d, -(br.b + br.c), br.d, -(br.a + br.b + br.c), br.d)
        }
        path.lineTo(bl.p, h)
        // Bottom-left
        if (!bl.empty) {
            path.relativeCubicTo(-bl.a, 0f, -(bl.a + bl.b), 0f, -(bl.a + bl.b + bl.c), -bl.d)
            path.arcSection(bl.radius, h - bl.radius, bl.length + bl.d, h - bl.d, bl)
            path.relativeCubicTo(-bl.d, -bl.c, -bl.d, -(bl.b + bl.c), -bl.d, -(bl.a + bl.b + bl.c))
        }
        path.lineTo(0f, tl.p)
        // Top-left
        if (!tl.empty) {
            path.relativeCubicTo(0f, -tl.a, 0f, -(tl.a + tl.b), tl.d, -(tl.a + tl.b + tl.c))
            path.arcSection(tl.radius, tl.radius, tl.d, tl.length + tl.d, tl)
            path.relativeCubicTo(tl.c, -tl.d, tl.b + tl.c, -tl.d, tl.a + tl.b + tl.c, -tl.d)
        }
        path.close()
        return Outline.Generic(path)
    }
}

private fun Path.arcSection(cx: Float, cy: Float, startX: Float, startY: Float, c: Corner) {
    if (c.arc <= 0f) return
    val startAngle = Math.toDegrees(
        atan2((startY - cy).toDouble(), (startX - cx).toDouble()),
    ).toFloat()
    arcTo(
        rect = Rect(cx - c.radius, cy - c.radius, cx + c.radius, cy + c.radius),
        startAngleDegrees = startAngle,
        sweepAngleDegrees = c.arc,
        forceMoveTo = false,
    )
}
