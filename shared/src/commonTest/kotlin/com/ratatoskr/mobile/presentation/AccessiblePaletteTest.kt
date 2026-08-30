package com.ratatoskr.mobile.presentation

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class AccessiblePaletteTest {
    @Test
    fun normal_large_and_control_colors_meet_required_ratios() {
        assertTrue(contrast(AccessiblePalette.normalText, AccessiblePalette.background) >= 4.5)
        assertTrue(contrast(AccessiblePalette.largeText, AccessiblePalette.background) >= 3.0)
        assertTrue(contrast(AccessiblePalette.controlText, AccessiblePalette.controlBackground) >= 4.5)
        assertTrue(contrast(AccessiblePalette.errorText, AccessiblePalette.background) >= 4.5)
    }

    private fun contrast(
        first: RgbColor,
        second: RgbColor,
    ): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun luminance(color: RgbColor): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun channel(value: Int): Double {
        val normalized = value / 255.0
        return if (normalized <= 0.03928) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
    }
}
