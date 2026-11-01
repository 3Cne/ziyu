package com.moyu.writer;

import android.os.Build;

import androidx.compose.ui.Modifier;
import androidx.compose.ui.graphics.ColorKt;

import dev.chrisbanes.haze.HazeChildKt;
import dev.chrisbanes.haze.HazeKt;
import dev.chrisbanes.haze.HazeState;
import dev.chrisbanes.haze.HazeStyle;
import dev.chrisbanes.haze.HazeTint;
import kotlin.Unit;
import kotlin.jvm.internal.DefaultConstructorMarker;

import java.lang.reflect.Constructor;

/**
 * Small Java shim around Haze 1.7.3.
 *
 * The cached Haze artifact was compiled with Kotlin 2.3 metadata while this module uses
 * Kotlin 2.2. Calling it from Java keeps the Kotlin compiler out of that metadata, so we
 * can test real RenderEffect backdrop blur on this device without changing the toolchain.
 */
public final class HazeBridge {
    public static final class Handle {
        final HazeState state = new HazeState();
    }

    private HazeBridge() {
    }

    public static Handle newHandle() {
        return new Handle();
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= 31;
    }

    public static Object contentLayer(Handle handle) {
        if (handle == null) return null;
        return handle.state.getContentLayer();
    }

    public static Modifier source(Modifier modifier, Handle handle) {
        return HazeKt.hazeSource(modifier, handle.state, 1f, null);
    }

    public static Modifier effect(Modifier modifier, Handle handle, float blurRadiusPx) {
        return effect(modifier, handle, blurRadiusPx, 0x40FFFFFFL);
    }

    public static Modifier effect(Modifier modifier, Handle handle, float blurRadiusPx, long tintColor) {
        if (!isSupported()) {
            return modifier;
        }
        try {
            // Haze's public constructors carry Kotlin's synthetic DefaultConstructorMarker,
            // which javac refuses to call directly. Reflection keeps this bridge tiny.
            // 3 == android.graphics.BlendMode.SRC_OVER (also Compose SrcOver).
            Constructor<HazeTint> tintCtor = HazeTint.class.getDeclaredConstructor(
                    long.class, int.class, int.class, DefaultConstructorMarker.class);
            tintCtor.setAccessible(true);
            HazeTint tint = tintCtor.newInstance(ColorKt.Color(tintColor), 3, 0, null);

            Constructor<HazeStyle> styleCtor = HazeStyle.class.getDeclaredConstructor(
                    long.class, HazeTint.class, float.class, float.class, HazeTint.class,
                    DefaultConstructorMarker.class);
            styleCtor.setAccessible(true);
            HazeStyle style = styleCtor.newInstance(
                    ColorKt.Color(0x00000000), tint, blurRadiusPx, 0.02f, tint, null);

            return HazeChildKt.hazeChild(modifier, handle.state, style, scope -> Unit.INSTANCE);
        } catch (Throwable e) {
            // Any vendor/Compose mismatch simply falls back to the static glass fill.
            return modifier;
        }
    }
}
