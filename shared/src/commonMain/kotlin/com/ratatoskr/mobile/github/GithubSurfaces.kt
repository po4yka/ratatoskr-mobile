package com.ratatoskr.mobile.github

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey

data object GithubCatalogRoute : NavKey

data object GithubDetailRoute : NavKey

@Composable
@Suppress("ktlint:standard:function-naming")
fun GithubCatalogSurface(
    state: GithubCatalogState,
    onSearch: (String) -> Unit,
    onOpen: (GithubCatalogRow) -> Unit,
    onBack: () -> Unit = {},
) {
    GithubColumn {
        GithubAction("Back", onClick = onBack)
        BasicText("GitHub repositories", style = TextStyle(fontSize = 28.sp))
        when (state) {
            GithubCatalogState.PairingRequired -> BasicText("Pair this device again")
            GithubCatalogState.CapabilityUnavailable ->
                BasicText("GitHub is unavailable on this Ratatoskr instance")
            is GithubCatalogState.Content -> {
                BasicText("Contract fixture browse — not synchronized; resets when Ratatoskr restarts.")
                BasicText("Search contract fixtures")
                BasicTextField(
                    value = state.acceptedQuery,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray).padding(12.dp),
                    singleLine = true,
                )
                if (state.queryRejected) BasicText("Search is limited to 128 characters", style = colorStyle)
                if (state.rows.isEmpty()) BasicText("No fixture repositories match")
                state.rows.forEach { row ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF92700C))
                                .clickable { onOpen(row) }
                                .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BasicText(row.fullName, style = TextStyle(fontSize = 20.sp))
                        BasicText(row.description)
                        BasicText("Open live Platform preview")
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun GithubDetailSurface(
    state: GithubDetailState,
    onSelect: (GithubActionMode) -> Unit,
    onConfirm: (GithubPendingConfirmation) -> Unit,
    onCancel: (GithubPendingConfirmation) -> Unit,
    onRetryUncertain: () -> Unit,
    onBack: () -> Unit = {},
) {
    GithubColumn {
        GithubAction("Back to GitHub fixtures", onClick = onBack)
        when (state) {
            GithubDetailState.Idle -> BasicText("Select a fixture repository")
            GithubDetailState.Loading -> BasicText("Loading live repository preview…")
            GithubDetailState.RePairingRequired -> BasicText("Pair this device again")
            is GithubDetailState.Failed -> {
                BasicText(
                    if (state.failure == GithubDetailFailure.InvalidResponse) {
                        "Platform returned an invalid GitHub response"
                    } else {
                        "GitHub repository preview is unavailable"
                    },
                    style = colorStyle,
                )
            }
            is GithubDetailState.Content -> GithubDetailContent(state, onSelect, onConfirm, onCancel, onRetryUncertain)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun GithubDetailContent(
    state: GithubDetailState.Content,
    onSelect: (GithubActionMode) -> Unit,
    onConfirm: (GithubPendingConfirmation) -> Unit,
    onCancel: (GithubPendingConfirmation) -> Unit,
    onRetryUncertain: () -> Unit,
) {
    BasicText("Live Platform preview")
    BasicText(state.preview.target.fullName, style = TextStyle(fontSize = 26.sp))
    BasicText(state.preview.target.canonicalUrl)
    state.preview.description?.let { BasicText(it) }
    BasicText("Stars: ${state.preview.stargazerCount}")
    state.preview.primaryLanguage?.let { BasicText("Language: $it") }
    state.preview.accountRef?.let { BasicText("Connected account: $it") }
    if (state.submitting) BasicText("Submitting confirmed action…")
    state.actions.sortedBy { it.ordinal }.forEach { mode ->
        GithubAction(
            when (mode) {
                GithubActionMode.Metadata -> "Refresh metadata"
                GithubActionMode.Track -> "Track in Ratatoskr"
                GithubActionMode.Star -> "Star on GitHub"
            },
            enabled = !state.submitting && state.pending == null,
        ) { onSelect(mode) }
    }
    state.pending?.let { pending ->
        BasicText(
            if (pending.mode == GithubActionMode.Track) {
                "Ratatoskr desired backup tracking; no completed backup or GitHub write."
            } else {
                "External GitHub star plus metadata and desired backup request."
            },
        )
        BasicText(pending.disclosure)
        GithubAction(pending.title) { onConfirm(pending) }
        GithubAction("Cancel") { onCancel(pending) }
    }
    if (state.outcomeUnknown) {
        BasicText(
            if (state.uncertainRetryAvailable) {
                "Action outcome is unknown; retry uses the same idempotency key."
            } else {
                "Action outcome is unknown; context changed, so retry is unavailable."
            },
            style = colorStyle,
        )
        if (state.uncertainRetryAvailable) {
            GithubAction("Retry uncertain action", onClick = onRetryUncertain)
        }
    }
    state.result?.let { result ->
        BasicText(result.aggregateLabel, style = TextStyle(fontSize = 22.sp))
        BasicText(result.metadataLabel)
        BasicText(result.providerStarLabel)
        BasicText(result.desiredBackupLabel)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun GithubColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun GithubAction(
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
                .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = if (enabled) Color.White else Color.DarkGray))
    }
}

private val colorStyle = TextStyle(color = Color(0xFFB00020))
