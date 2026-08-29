package com.ratatoskr.mobile.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class ShareStagingRoute(
    val originalText: String,
    val url: String?,
) : NavKey

@Composable
@Suppress("ktlint:standard:function-naming")
fun ShareStagingSurface(
    state: ShareStagingState,
    dispatch: (ShareStagingAction) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(
            text = "Review capture",
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontSize = 28.sp),
        )
        when (state) {
            is ShareStagingState.Ready -> {
                BasicText(state.originalText)
                state.message?.let {
                    BasicText(it, style = TextStyle(color = Color(0xFF7A2500)))
                }
                StagingButton(
                    label = "Confirm capture",
                    enabled = state.canSubmit,
                    onClick = { dispatch(ShareStagingAction.Confirm) },
                )
                StagingButton(
                    label = "Cancel",
                    onClick = { dispatch(ShareStagingAction.Cancel) },
                )
            }
            ShareStagingState.Saving -> BasicText("Saving capture safely…")
            is ShareStagingState.Queued -> BasicText(state.message)
            ShareStagingState.Cancelled -> BasicText("Capture cancelled.")
            is ShareStagingState.Failed -> BasicText(state.message)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun ShareRejectionSurface(
    reason: ShareIntakeRejection,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(
            text = "Capture unavailable",
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontSize = 28.sp),
        )
        BasicText(reason.safeMessage())
        StagingButton(label = "Back to Ratatoskr", onClick = onDismiss)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun StagingButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(if (enabled) Color(0xFF315C9D) else Color.LightGray)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = if (enabled) Color.White else Color.DarkGray))
    }
}

private fun ShareIntakeRejection.safeMessage(): String =
    when (this) {
        ShareIntakeRejection.UnsupportedAction ->
            "This share action is not supported. Use Share from the source app."
        ShareIntakeRejection.UnsupportedMimeType ->
            "This content type is not supported. Share one URL as text."
        ShareIntakeRejection.MissingText ->
            "Nothing was shared. Share one URL as text."
        ShareIntakeRejection.OversizedText ->
            "This shared text is too large. Share one URL only."
        ShareIntakeRejection.UnsupportedScheme ->
            "Only http and https URLs can be captured."
        ShareIntakeRejection.MultipleUrls ->
            "This share contains more than one URL. Share one URL at a time."
    }
