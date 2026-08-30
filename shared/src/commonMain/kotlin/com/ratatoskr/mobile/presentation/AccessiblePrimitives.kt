package com.ratatoskr.mobile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalMobileLocale = staticCompositionLocalOf { MobileLocale.English }

@Composable
@Suppress("ktlint:standard:function-naming")
fun AccessibleHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text,
        modifier = modifier.semantics { heading() },
        style = TextStyle(color = AccessiblePalette.normalText.color(), fontSize = 28.sp),
    )
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun AccessibleStatus(
    text: String,
    live: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text,
        modifier =
            modifier.semantics {
                stateDescription = text
                if (live) liveRegion = LiveRegionMode.Polite
            },
        style = TextStyle(color = AccessiblePalette.normalText.color()),
    )
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun AccessibleAction(
    label: String,
    accessibleLabel: String = label,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(if (enabled) AccessiblePalette.controlBackground.color() else Color.LightGray)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics {
                    contentDescription = accessibleLabel
                    role = Role.Button
                }.padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(color = if (enabled) AccessiblePalette.controlText.color() else Color.DarkGray),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun AccessibleTextInput(
    value: String,
    label: String,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = label }
                .padding(12.dp),
        singleLine = singleLine,
        textStyle = TextStyle(color = AccessiblePalette.normalText.color()),
    )
}

private fun RgbColor.color(): Color = Color(red, green, blue)
