package com.moyu.writer

import kotlin.math.pow

/**
 * 粘滞水滴（2026-11-15 用户命名并定为标准质感）。
 *
 * 规则：**速度 / 位移驱动的非均匀形变 + 运动前端为锚点 + 松手后指数衰减回零**。
 * 软件里所有「会跟着手指或点击移动的水滴 / 镜片」都按这一套参数画，同一套物理读数：
 *
 * - 沿运动方向最多拉长 [PEAK]（后缘被拖着走，读作拖尾）；
 * - 垂直方向按同比例压扁 [SQUASH]（体积守恒，不会像气筒一样只变长）；
 * - 松手（或落定）后形变在 [DECAY_MS] 量级里指数衰减回零——**不能一帧切掉**，
 *   切掉就没有「粘」，只剩「弹」（3.24.3 之前就是硬切，用户报「没有粘滞感」）。
 *
 * 用到的表面：`TabBar` 的选中镜片（点击切页 + 长按拖动）、`Editor` 的框选水滴手柄。
 */
internal object ViscousDrop {
    /** 沿运动方向拉长的上限：1 + PEAK = 最多 1.75 倍。 */
    const val PEAK = .75f

    /** 垂直方向的压扁上限：1 - SQUASH = 最细到 0.72 倍。 */
    const val SQUASH = .28f

    /** 松手后形变归零的目标时长（ms）。 */
    const val DECAY_MS = 230f

    /** 每 16ms 的衰减系数：.86^14 ≈ .12，约 230ms 收到 12%，再一帧归零。 */
    private const val DECAY_PER_FRAME = .86f

    /** 沿运动方向的缩放；[p] 是归一化强度 0..1。 */
    fun along(p: Float): Float = 1f + PEAK * p.coerceIn(0f, 1f)

    /** 垂直方向的缩放。 */
    fun perp(p: Float): Float = 1f - SQUASH * p.coerceIn(0f, 1f)

    /** 一帧的衰减系数；[dtMs] 是这一帧的时长，按 16ms 归一。 */
    fun decayFor(dtMs: Float): Float =
        DECAY_PER_FRAME.toDouble()
            .pow((dtMs / 16f).coerceIn(.25f, 4f).toDouble())
            .toFloat()

    /** 按分量算两只轴：沿 X 的强度 [px] 拉长 X，同时按 [py] 压 Y；反过来同理。 */
    fun scaleX(px: Float, py: Float): Float = along(px) * perp(py)

    fun scaleY(px: Float, py: Float): Float = along(py) * perp(px)

    /**
     * 局部动效（能力栏展开/收起、浮窗打开/关闭）用的「两轴时间差」。
     *
     * 这些控件的内容是图标和文字，直接对内容做非均匀缩放会糊；改成让两个轴
     * 走**同一条过渡的两个时间点**：主轴用 [t]，副轴用本函数给出的滞后值。
     * 几何上等价于「先长/先缩的那一轴被拉着走」，读出拖尾，而内容是整体缩放。
     *
     * 展开（t: 0→1）时副轴落后 → 中间帧偏扁宽；收起（t: 1→0）时副轴落后 →
     * 主轴的尺寸先回来，同样偏扁宽。两个方向一致，不会出现来回摆的形状。
     */
    fun lagged(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return (c - LAG * kotlin.math.sin((Math.PI * c).toFloat())).coerceIn(0f, 1f)
    }

    /** 副轴相对主轴的最大滞后量（占整段过渡的比例）。 */
    const val LAG = .15f
}
