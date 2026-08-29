package com.ratatoskr.mobile.operation

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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import kotlinx.serialization.Serializable

@Serializable
data object OperationListRoute : NavKey

@Serializable
data class OperationDetailRoute(
    val operationId: String,
) : NavKey

@Composable
@Suppress("ktlint:standard:function-naming")
fun OperationListSurface(
    state: OperationListState,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var staleOperations by remember { mutableStateOf(emptyList<OperationPresentation>()) }
    if (state is OperationListState.Content) {
        SideEffect { staleOperations = state.operations }
    }
    StatusColumn("Operations") {
        when (state) {
            OperationListState.Idle -> BasicText("Open operations to load status.")
            OperationListState.Loading -> BasicText("Loading operations…")
            OperationListState.Empty -> BasicText("No operations yet.")
            is OperationListState.Content -> OperationRows(state.operations, onOpen)
            OperationListState.Offline -> {
                BasicText("Offline")
                if (staleOperations.isNotEmpty()) {
                    BasicText("Last known status")
                    OperationRows(staleOperations, onOpen)
                }
                StatusButton("Retry", onRefresh)
            }
            OperationListState.RePairingRequired -> BasicText("Pair device to view operations.")
            is OperationListState.Failed -> {
                BasicText("Operations are unavailable.")
                if (state.canRetry) StatusButton("Retry", onRefresh)
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun OperationRows(
    operations: List<OperationPresentation>,
    onOpen: (String) -> Unit,
) {
    operations.forEach { operation ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(operation.operationId) }
                    .padding(vertical = 12.dp),
        ) {
            BasicText(operation.kind)
            BasicText(operation.status.label())
            operation.stage?.let { BasicText(it) }
            operation.progressPercent?.let { BasicText("$it%") }
            BasicText(operation.statusChangedAt.toString())
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun OperationDetailSurface(
    state: OperationDetailState,
    onRetry: () -> Unit,
    onPair: () -> Unit,
) {
    StatusColumn("Operation status") {
        when (state) {
            OperationDetailState.Idle -> BasicText("Open an operation to load status.")
            OperationDetailState.Loading -> BasicText("Loading operation…")
            is OperationDetailState.Content -> {
                val operation = state.operation
                BasicText(operation.status.label())
                operation.progressPercent?.let { BasicText("$it%") }
                operation.stage?.let { BasicText(it) }
                BasicText("${operation.warningCount} ${countLabel(operation.warningCount, "warning")}")
                BasicText("${operation.errorCount} ${countLabel(operation.errorCount, "error")}")
                BasicText("${operation.resultCount} ${countLabel(operation.resultCount, "result")}")
            }
            OperationDetailState.Offline -> {
                BasicText("Offline")
                StatusButton("Retry", onRetry)
            }
            OperationDetailState.RePairingRequired -> {
                BasicText("Authorization is no longer available.")
                StatusButton("Pair device", onPair)
            }
            OperationDetailState.NotFoundOrNotOwned -> BasicText("Operation is unavailable.")
            is OperationDetailState.Failed -> {
                BasicText("Operation status could not be refreshed.")
                if (state.requiresManualRetry) StatusButton("Retry", onRetry)
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun StatusColumn(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            title,
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontSize = 28.sp),
        )
        content()
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun StatusButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(Color(0xFF315C9D))
                .clickable(onClick = onClick)
                .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = Color.White))
    }
}

private fun OperationStatus.label(): String =
    when (this) {
        OperationStatus.ACCEPTED -> "Accepted"
        OperationStatus.QUEUED -> "Queued"
        OperationStatus.RUNNING -> "Running"
        OperationStatus.SUCCEEDED -> "Completed"
        OperationStatus.PARTIALLY_SUCCEEDED -> "Partially completed"
        OperationStatus.FAILED -> "Failed"
        OperationStatus.CANCELLED -> "Cancelled"
    }

private fun countLabel(
    count: Int,
    singular: String,
): String = if (count == 1) singular else "${singular}s"
