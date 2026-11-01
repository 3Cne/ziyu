package com.moyu.writer

import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * API 33+ AGSL lens pass. Haze supplies the real backdrop blur underneath;
 * this pass adds the SDF edge, refraction tint, dispersion and specular rim.
 */
@Composable
fun LiquidLensOverlay(
    modifier: Modifier = Modifier,
    radius: Dp = 30.dp,
    tint: Color = Color.White,
    backdrop: ImageBitmap? = null,
    sourceOffset: Offset = Offset.Zero,
) {
    if (Build.VERSION.SDK_INT < 33) return
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    val hasBackdrop = backdrop != null
    val shader = remember(radiusPx, tint, hasBackdrop) {
        runCatching {
            RuntimeShader(if (hasBackdrop) LENS_BACKDROP_AGSL else LENS_AGSL).apply {
                setFloatUniform("radius", radiusPx)
                setColorUniform("tint", tint.toArgb())
            }
        }.getOrNull()
    } ?: return
    val paint = remember(shader, hasBackdrop) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            isDither = true
        }
    }
    if (hasBackdrop) {
        val bitmapShader = remember(backdrop) {
            BitmapShader(backdrop!!.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        shader.setInputShader("content", bitmapShader)
    }
    Canvas(modifier) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("sourceOffset", sourceOffset.x, sourceOffset.y)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val save = native.save()
            native.clipPath(
                android.graphics.Path().apply {
                    addRoundRect(
                        0f, 0f, size.width, size.height,
                        radiusPx, radiusPx,
                        android.graphics.Path.Direction.CW,
                    )
                }
            )
            native.drawRect(0f, 0f, size.width, size.height, paint)
            native.restoreToCount(save)
        }
    }
}

private const val LENS_BACKDROP_AGSL = """
uniform float2 size;
uniform float2 sourceOffset;
uniform float radius;
uniform float3 tint;
uniform shader content;

float roundedBox(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 sampleBlur(float2 p) {
    half4 c = content.eval(p);
    c += content.eval(p + float2(3.0, 0.0));
    c += content.eval(p - float2(3.0, 0.0));
    c += content.eval(p + float2(0.0, 3.0));
    c += content.eval(p - float2(0.0, 3.0));
    return c / 5.0;
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord - size * 0.5;
    float2 halfSize = size * 0.5;
    float d = roundedBox(p, halfSize, radius);
    float rim = 1.0 - smoothstep(0.0, 12.0, abs(d));
    float inner = smoothstep(-8.0, 2.0, -d);

    float2 n = normalize(float2(
        sign(p.x) * max(abs(p.x) - (halfSize.x - radius), 0.0),
        sign(p.y) * max(abs(p.y) - (halfSize.y - radius), 0.0)
    ) + 0.0001);

    float2 baseCoord = fragCoord + sourceOffset;
    float2 bend = n * (6.0 + 10.0 * rim);
    half4 base = sampleBlur(baseCoord + bend);
    half4 red = sampleBlur(baseCoord + bend + n * 4.0);
    half4 blue = sampleBlur(baseCoord + bend - n * 4.0);

    float3 refracted = float3(red.r, base.g, blue.b);
    float3 color = refracted * (0.90 + 0.10 * inner);
    color += tint * rim * 0.20;
    color += float3(1.0) * rim * 0.18;
    // Keep the bar's value stable when the page behind it changes. The
    // refraction remains visible at the rim; the body stays near paper.
    color = mix(color, tint, 0.62);

    float alpha = max(inner * 0.92, rim * 0.55);
    return half4(color, alpha);
}
"""

private const val LENS_AGSL = """
uniform float2 size;
uniform float radius;
uniform float3 tint;

float roundedBox(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord - size * 0.5;
    float2 halfSize = size * 0.5;
    float d = roundedBox(p, halfSize, radius);

    // Edge band: a thin refracting rim, not a flat stroke.
    float rim = 1.0 - smoothstep(0.0, 10.0, abs(d));
    float inner = smoothstep(-2.0, 10.0, -d);

    // Fake refraction normal from the rounded-box gradient.
    float2 n = normalize(float2(
        sign(p.x) * max(abs(p.x) - (halfSize.x - radius), 0.0),
        sign(p.y) * max(abs(p.y) - (halfSize.y - radius), 0.0)
    ) + 0.0001);

    // Dispersion: opposite color offsets along the rim.
    float3 dispersion = float3(
        rim * (0.5 + 0.5 * n.x),
        rim * 0.35,
        rim * (0.5 + 0.5 * n.y)
    );

    // Top-left specular and bottom-right cool caustic.
    float top = smoothstep(0.0, 1.0, 1.0 - abs(p.y + halfSize.y) / max(size.y, 1.0));
    float bottom = smoothstep(0.0, 1.0, 1.0 - abs(p.y - halfSize.y) / max(size.y, 1.0));
    float3 spec = float3(1.0) * rim * (0.55 + 0.45 * top);
    float3 cool = tint * rim * (0.10 + 0.25 * bottom);

    float3 color = tint * 0.08 + dispersion * 0.50 + spec * 0.72 + cool * 1.6;
    float alpha = rim * 0.82 + inner * 0.035;
    return half4(color, alpha);
}
"""
