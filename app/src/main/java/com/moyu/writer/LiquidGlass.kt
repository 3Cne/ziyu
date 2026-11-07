package com.moyu.writer

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Provides a lambda that draws the current page content into any DrawScope.
 * The liquid glass bar uses this to sample the page behind it.
 */
val LocalPageDrawer = compositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * Liquid glass effect applied to a composable node.
 *
 * The key insight: instead of trying to sample a separate GraphicsLayer,
 * we draw the page content INSIDE this node (via the pageDrawer lambda),
 * then apply a RenderEffect shader to the whole node. The shader's
 * `content` input receives the page pixels directly.
 *
 * This works because Modifier.graphicsLayer { renderEffect = ... }
 * applies the effect to everything drawn inside that node.
 */
@Composable
fun LiquidGlassOverlay(
    modifier: Modifier = Modifier,
    shape: Shape,
    barOrigin: Offset,
    tint: Color = Color.White,
    blurRadius: Float = 20f,
    refraction: Float = 0.5f,
    rimStrength: Float = 0.6f,
    /** Base opacity under the shader: 0 leaves the glass fully see-through. */
    fill: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val pageDrawer = LocalPageDrawer.current

    if (Build.VERSION.SDK_INT < 33 || pageDrawer == null) {
        // Fallback: just draw content without glass
        Box(modifier = modifier.clip(shape)) {
            content()
        }
        return
    }

    val density = LocalDensity.current
    val blurPx = with(density) { blurRadius.dp.toPx() }

    val shader = remember(tint, blurPx, refraction, rimStrength) {
        runCatching {
            RuntimeShader(GLASS_AGSL).apply {
                setFloatUniform("tintR", tint.red)
                setFloatUniform("tintG", tint.green)
                setFloatUniform("tintB", tint.blue)
                setFloatUniform("blurRadius", blurPx)
                setFloatUniform("refraction", refraction)
                setFloatUniform("rimStrength", rimStrength)
            }
        }.getOrNull()
    }

    val effect = remember(shader) {
        shader?.let {
            runCatching {
                RenderEffect.createRuntimeShaderEffect(it, "content").asComposeRenderEffect()
            }.getOrNull()
        }
    }

    if (effect == null) {
        Box(modifier = modifier.clip(shape)) {
            content()
        }
        return
    }

    Box(
        modifier = modifier
            .clip(shape)
            // The shader always outputs alpha 1, so the material's fill has to
            // be applied here as a base layer underneath it. Without this the
            // bar ignored its own 填充 setting.
            .drawBehind {
                drawRect(
                    color = tint.copy(alpha = fill.coerceIn(0f, 1f)),
                )
            }
            .graphicsLayer { renderEffect = effect }
            .drawWithContent {
                shader?.setFloatUniform("size", size.width, size.height)
                drawContent()
            },
    ) {
        // Draw page content inside this node (translated)
        // The graphicsLayer effect will process everything drawn here
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset {
                    IntOffset(
                        (-barOrigin.x).toInt(),
                        (-barOrigin.y).toInt(),
                    )
                },
        ) {
            pageDrawer()
        }
        // Bar UI on top
        content()
    }
}

private const val GLASS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float tintR;
uniform float tintG;
uniform float tintB;
uniform float blurRadius;
uniform float refraction;
uniform float rimStrength;

half4 main(float2 fragCoord) {
    // Edge distance for rim/refraction
    float edgeDist = min(min(fragCoord.x, size.x - fragCoord.x),
                         min(fragCoord.y, size.y - fragCoord.y));
    float edgeFactor = smoothstep(0.0, 48.0, edgeDist);
    
    // Normal direction from center for refraction
    float2 center = size * 0.5;
    float2 delta = fragCoord - center;
    float len = length(delta) + 0.001;
    float2 norm = delta / len;
    
    // Refraction offset near edges
    float refractAmount = (1.0 - edgeFactor) * refraction * 24.0;
    float2 offset = norm * refractAmount;
    
    // Simple 5x5 box blur for frosted glass
    half4 blurred = half4(0.0);
    float total = 0.0;
    float r = blurRadius * 0.4;
    for (float x = -2.0; x <= 2.0; x += 1.0) {
        for (float y = -2.0; y <= 2.0; y += 1.0) {
            float2 sp = fragCoord + offset + float2(x, y) * r;
            sp = clamp(sp, float2(0.0), size - float2(1.0));
            blurred += content.eval(sp);
            total += 1.0;
        }
    }
    blurred /= total;
    
    // Mix: sharp in center, blurred at edges
    half4 sharp = content.eval(clamp(fragCoord + offset, float2(0.0), size - float2(1.0)));
    half4 color = mix(sharp, blurred, (1.0 - edgeFactor) * 0.85 + 0.15);
    
    // Subtle tint
    color.rgb = mix(color.rgb, half3(tintR, tintG, tintB), 0.06);
    
    // Rim highlight at edges
    float rim = pow(1.0 - edgeFactor, 2.0) * rimStrength;
    color.rgb += half3(rim * 0.25);
    
    // Slight darkening at very edge for depth
    color.rgb *= mix(1.0, 0.92, pow(1.0 - edgeFactor, 3.0));
    
    color.a = 1.0;
    return color;
}
"""
