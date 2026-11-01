package com.moyu.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlin.math.min

/**
 * One material contract for every surface that owns a material.
 *
 * These are defaults only. A configured value always wins, and every value is
 * stored per theme so changing one theme never silently changes another.
 */
enum class SurfaceRole(val key: String, val label: String) {
    BAR("bar", "栏"),
    CARD("card", "卡片"),
    POPUP("popup", "弹窗"),
    CONTROL("control", "控件"),
    PAPER("paper", "纸面"),
}

/**
 * Renderer-facing material values. Colors are intentionally absent: the
 * renderer derives all colors from the active palette, so a material can be
 * shared by every theme.
 */
data class MaterialSpec(
    // Every numeric adjustment starts at zero. New themes, missing roles and
    // "restore defaults" all start from a bare material; only saved values
    // keep a non-zero adjustment.
    val fill: Float = 0f,
    val blur: Float = 0f,
    val haze: Boolean = true,
    val edge: Float = 0f,
    val rim: Float = 0f,
    val specular: Float = 0f,
    val refraction: Float = 0f,
    val shadow: Float = 0f,
    val shadowBlur: Float = 0f,
    val shadowY: Float = 0f,
    val corner: Float = 26f,
    val grain: Boolean = true,
) {
    fun sanitized(): MaterialSpec = copy(
        fill = fill.coerceIn(0f, 1f),
        blur = blur.coerceIn(0f, 1f),
        edge = edge.coerceIn(0f, 8f),
        rim = rim.coerceIn(0f, 1f),
        specular = specular.coerceIn(0f, 1f),
        refraction = refraction.coerceIn(0f, 1f),
        shadow = shadow.coerceIn(0f, 1f),
        shadowBlur = shadowBlur.coerceIn(0f, 64f),
        shadowY = shadowY.coerceIn(0f, 32f),
        corner = corner.coerceIn(0f, 48f),
    )
}

fun defaultMaterial(role: SurfaceRole): MaterialSpec = when (role) {
    // All numeric adjustments start at 0 for every role. Only the role's
    // non-numeric switches keep their intended defaults.
    SurfaceRole.BAR -> MaterialSpec()
    SurfaceRole.CARD -> MaterialSpec()
    SurfaceRole.POPUP -> MaterialSpec()
    SurfaceRole.CONTROL -> MaterialSpec()
    SurfaceRole.PAPER -> MaterialSpec(
        haze = false,
        grain = false,
    )
}

val LocalGlassLite = staticCompositionLocalOf { false }

@Composable
fun MoyuSurface(
    role: SurfaceRole,
    modifier: Modifier = Modifier,
    radius: Float? = null,
    shape: Shape? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val ui = LocalInk.current
    val base = ui.settings.materialFor(ui.settings.theme, role)
    val spec = if (LocalGlassLite.current) {
        base.copy(haze = false, refraction = 0f)
    } else {
        base
    }
    val resolvedRadius = radius ?: spec.corner
    val resolvedShape = shape ?: RoundedCornerShape(resolvedRadius.dp)

    if (role == SurfaceRole.PAPER) {
        Box(modifier.background(ui.palette.paper, resolvedShape)) { content() }
        return
    }

    // Shadow belongs outside the glass layer, but it must share the card's
    // measured size. Wrapping GlassSurface in an unsized Box made the glass
    // shrink to its content, which produced a rectangular inner pane on the
    // stats cards. Keep the shadow on the same modifier chain instead.
    GlassSurface(
            modifier = modifier.drawBehind {
                if (spec.shadow > 0f) {
                    val r = resolvedRadius.dp.toPx().coerceAtMost(min(size.width, size.height) / 2f)
                    val elevation = (spec.shadow * 34f).dp.toPx()
                    val shadowColor = ui.palette.ink
                        .copy(alpha = (spec.shadow * .34f).coerceIn(0f, 1f))
                        .toArgb()
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint(
                            android.graphics.Paint.ANTI_ALIAS_FLAG,
                        ).apply {
                            color = android.graphics.Color.TRANSPARENT
                            setShadowLayer(elevation, 0f, 0f, shadowColor)
                        }
                        canvas.nativeCanvas.drawRoundRect(
                            0f,
                            0f,
                            size.width,
                            size.height,
                            r,
                            r,
                            paint,
                        )
                    }
                }
            },
            radius = resolvedRadius,
            strength = 1f,
            frost = 1f,
            tint = ui.palette.paper,
            haze = if (spec.haze) LocalHazeHandle.current else null,
            useHaze = spec.haze,
            specular = spec.specular > 0f,
            shape = shape,
            fillOverride = spec.fill,
            blurOverride = spec.blur,
            grainOverride = spec.grain,
            edgeWidth = spec.edge,
            rimStrength = spec.rim,
            specularStrength = spec.specular,
            refractionStrength = spec.refraction,
            // The solid frame is separate from the rim highlight. liquidGlass
            // now draws this frame and then an inset rim/specular, so Edge
            // contrast and Highlight still work without turning the frame
            // into a multicolour gradient.
            // Edge contrast is the contrast between the solid frame and the
            // card body, not merely the alpha of an inner highlight. At 0 the
            // frame melts into the card; at 100 it resolves to a clear ink
            // edge. The rim gradient remains a separate inner highlight.
            borderColor = if (ui.palette.isDark) {
                lerp(
                    ui.palette.paper,
                    androidx.compose.ui.graphics.Color.White,
                    .04f + spec.rim * .30f,
                )
            } else {
                lerp(ui.palette.paper, ui.palette.ink, .04f + spec.rim * .34f)
            },
            content = content,
    )
}

fun AppSettings.materialFor(theme: String, role: SurfaceRole): MaterialSpec =
    materials[theme]?.get(role.key)?.sanitized() ?: defaultMaterial(role)

fun AppSettings.withMaterial(theme: String, role: SurfaceRole, spec: MaterialSpec): AppSettings {
    val byRole = materials[theme].orEmpty().toMutableMap()
    byRole[role.key] = spec.sanitized()
    return copy(materials = materials + (theme to byRole))
}

fun encodeMaterials(materials: Map<String, Map<String, MaterialSpec>>): JSONObject {
    val root = JSONObject()
    materials.forEach { (theme, roles) ->
        val roleObject = JSONObject()
        roles.forEach { (key, spec) ->
            roleObject.put(key, spec.toJson())
        }
        root.put(theme, roleObject)
    }
    return root
}

fun decodeMaterials(root: JSONObject?): Map<String, Map<String, MaterialSpec>> {
    if (root == null) return emptyMap()
    val out = mutableMapOf<String, MutableMap<String, MaterialSpec>>()
    root.keys().forEach { theme ->
        val roles = root.optJSONObject(theme) ?: return@forEach
        val target = out.getOrPut(theme) { mutableMapOf() }
        roles.keys().forEach { key ->
            roles.optJSONObject(key)?.let { target[key] = materialFromJson(it) }
        }
    }
    return out
}

private fun MaterialSpec.toJson(): JSONObject = JSONObject().apply {
    put("fill", fill.toDouble())
    put("blur", blur.toDouble())
    put("haze", haze)
    put("edge", edge.toDouble())
    put("rim", rim.toDouble())
    put("specular", specular.toDouble())
    put("refraction", refraction.toDouble())
    put("shadow", shadow.toDouble())
    put("shadowBlur", shadowBlur.toDouble())
    put("shadowY", shadowY.toDouble())
    put("corner", corner.toDouble())
    put("grain", grain)
}

private fun materialFromJson(o: JSONObject): MaterialSpec = MaterialSpec(
    fill = o.optDouble("fill", 0.0).toFloat(),
    blur = o.optDouble("blur", 0.0).toFloat(),
    haze = o.optBoolean("haze", true),
    edge = o.optDouble("edge", 0.0).toFloat(),
    rim = o.optDouble("rim", 0.0).toFloat(),
    specular = o.optDouble("specular", 0.0).toFloat(),
    refraction = o.optDouble("refraction", 0.0).toFloat(),
    shadow = o.optDouble("shadow", 0.0).toFloat(),
    shadowBlur = o.optDouble("shadowBlur", 0.0).toFloat(),
    shadowY = o.optDouble("shadowY", 0.0).toFloat(),
    corner = o.optDouble("corner", 26.0).toFloat(),
    grain = o.optBoolean("grain", true),
).sanitized()


