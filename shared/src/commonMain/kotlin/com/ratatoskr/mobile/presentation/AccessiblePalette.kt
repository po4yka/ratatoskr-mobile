package com.ratatoskr.mobile.presentation

data class RgbColor(
    val red: Int,
    val green: Int,
    val blue: Int,
)

object AccessiblePalette {
    val background = RgbColor(255, 255, 255)
    val normalText = RgbColor(17, 24, 39)
    val largeText = RgbColor(49, 92, 157)
    val controlBackground = RgbColor(49, 92, 157)
    val controlText = RgbColor(255, 255, 255)
    val errorText = RgbColor(155, 28, 28)
}
