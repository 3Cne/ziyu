package com.moyu.writer

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "GlassGalleryDark", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun GlassGalleryDarkPreview() {
    PreviewGallery()
}

@Preview(name = "LiquidBarPaper", widthDp = 412, heightDp = 320, showBackground = true)
@Composable
fun LiquidBarPaperPreview() {
    LiquidBarPreview("paper", 1)
}

@Preview(name = "LiquidBarNight", widthDp = 412, heightDp = 320, showBackground = true)
@Composable
fun LiquidBarNightPreview() {
    LiquidBarPreview("night", 1)
}
