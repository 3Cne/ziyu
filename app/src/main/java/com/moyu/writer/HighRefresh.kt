package com.moyu.writer

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Window
import android.view.WindowManager

/**
 * Ask the display for its highest supported mode. Xiaomi/HyperOS exposes 120Hz through
 * preferredDisplayModeId, while some older vendor ROMs only honour preferredRefreshRate.
 */
fun Activity.enableHighRefresh() {
    try {
        if (Build.VERSION.SDK_INT >= 23) {
            val best = display?.supportedModes?.maxByOrNull { it.refreshRate }
            if (best != null) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = best.modeId
                    preferredRefreshRate = best.refreshRate
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.attributes = window.attributes.apply {
                preferredRefreshRate = 120f
            }
        }
    } catch (_: Throwable) {
    }
}

@Suppress("DEPRECATION")
fun Window.enableHighRefresh(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= 23) {
            val display = if (Build.VERSION.SDK_INT >= 30) {
                context.display
            } else {
                windowManager.defaultDisplay
            }
            val best = display?.supportedModes?.maxByOrNull { it.refreshRate } ?: return
            attributes = attributes.apply {
                preferredDisplayModeId = best.modeId
                preferredRefreshRate = best.refreshRate
            }
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    val method = javaClass.methods.firstOrNull {
                        it.name == "setFrameRate" && it.parameterTypes.size == 2
                    }
                    method?.invoke(this, best.refreshRate, 1)
                } catch (_: Throwable) {
                }
            }
        } else {
            attributes = attributes.apply { preferredRefreshRate = 120f }
        }
    } catch (_: Throwable) {
    }
}

fun Activity.keepAwakeWhileWriting(awake: Boolean) {
    if (awake) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
