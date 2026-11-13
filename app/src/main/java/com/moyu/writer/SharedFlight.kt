package com.moyu.writer

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.LayoutCoordinates

/**
 * One shared element in flight between two pages.
 *
 * Same recipe as the transcript's send flight (`AiTab`): remember the live
 * `LayoutCoordinates` of both ends, keep the real element invisible while the
 * overlay travels, and read both rectangles every frame. Cached rectangles went
 * stale because the page pager moves pages with a graphics-layer translation,
 * which never re-runs layout callbacks.
 */
object Flight {
    /** Parked switches off both the flight and the target hiding. */
    const val ENABLED = true

    /** "book:<id>" / "note:<id>"; null when nothing is flying. */
    var key by mutableStateOf<String?>(null)
        private set

    /** The element in the list the writer tapped. */
    var from by mutableStateOf<LayoutCoordinates?>(null)
        private set

    /** The element it lands on; null until the pushed page lays it out. */
    var to by mutableStateOf<LayoutCoordinates?>(null)
        private set

    /** 0 → 1, driven from the navigation host. */
    val t = Animatable(0f)

    fun start(key: String, from: LayoutCoordinates) {
        android.util.Log.d("MOYU_FLIGHT", "start $key")
        this.key = key
        this.from = from
        this.to = null
    }

    fun land(key: String, to: LayoutCoordinates) {
        if (this.key != key) return
        if (this.to == null) {
            android.util.Log.d("MOYU_FLIGHT", "land $key")
            this.to = to
        }
    }

    /** True while this element is being carried; the real one must stay hidden
     *  or the two copies read as a ghost. */
    fun isCarrying(key: String): Boolean = this.key == key && to != null

    fun finish() {
        android.util.Log.d("MOYU_FLIGHT", "finish key=$key")
        key = null
        from = null
        to = null
    }
}
