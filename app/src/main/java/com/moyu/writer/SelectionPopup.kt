package com.moyu.writer

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Android PopupWindow anchored to the editor view. Android owns the window
 * placement, so IME resizing does not require screen-coordinate math.
 */
class SelectionPopup(
    private val anchor: View,
    private val settings: AppSettings,
    private val imeOpen: Boolean,
    private val lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val onAction: (Int) -> Unit,
    private val onDismiss: () -> Unit,
) {
    private var popup: PopupWindow? = null

    fun show(bar: SelectionBar) {
        dismiss()
        val density = anchor.resources.displayMetrics.density
        val gap = (10 * density).toInt()
        val panelHeight = (48 * density).toInt()
        val maxWidth = (330 * density).toInt()
        val below = bar.y2 + gap
        // Keep a safety band for the symbol dock/keyboard. If the IME is open,
        // always prefer the upper side so the panel cannot be covered.
        val fitsBelow = !imeOpen &&
            below + panelHeight <= anchor.height - (110 * density).toInt()
        val above = (bar.y1 - panelHeight - gap).coerceAtLeast((8 * density).toInt())
        val x = (bar.x1 + (bar.x2 - bar.x1) / 2 - maxWidth / 2)
            .coerceIn(0, (anchor.width - maxWidth).coerceAtLeast(0))
        val y = if (fitsBelow) below else above

        val content = ComposeView(anchor.context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setContent {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                MoyuTheme(settings) {
                    AnimatedVisibility(
                        visible = shown,
                        enter = fadeIn(tween(140)) + slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = tween(170, easing = FastOutSlowInEasing),
                        ),
                        exit = fadeOut(tween(100)) + slideOutVertically(
                            targetOffsetY = { it / 3 },
                            animationSpec = tween(130, easing = FastOutSlowInEasing),
                        ),
                    ) {
                        MoyuSurface(
                            SurfaceRole.BAR,
                            Modifier.fillMaxWidth().height(48.dp),
                            radius = 24f,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PopupAction("全选") { onAction(android.R.id.selectAll) }
                                PopupAction("复制") { onAction(android.R.id.copy) }
                                PopupAction("剪切") { onAction(android.R.id.cut) }
                                PopupAction("粘贴") { onAction(android.R.id.paste) }
                                PopupAction("完成") { onDismiss() }
                            }
                        }
                    }
                }
            }
        }
        val window = PopupWindow(content, maxWidth, panelHeight, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = false
            elevation = 12f
            setOnDismissListener { onDismiss() }
        }
        popup = window
        // showAsDropDown offsets from the anchor bottom; compensate so the
        // content lands at the local y computed from the selection.
        window.showAsDropDown(anchor, x, y - anchor.height, Gravity.START)
    }

    fun dismiss() {
        popup?.setOnDismissListener(null)
        popup?.dismiss()
        popup = null
    }
}

@Composable
private fun PopupAction(label: String, onClick: () -> Unit) {
    val ui = LocalInk.current
    Box(
        Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, size = 12.5f, color = ui.palette.ink, weight = androidx.compose.ui.text.font.FontWeight.Medium)
    }
}
