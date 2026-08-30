package com.ratatoskr.mobile.library

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratatoskr.mobile.api.generated.model.ReadState
import com.ratatoskr.mobile.presentation.AccessibleAction
import com.ratatoskr.mobile.presentation.AccessibleHeading
import com.ratatoskr.mobile.presentation.AccessibleStatus
import com.ratatoskr.mobile.presentation.AccessibleTextInput
import com.ratatoskr.mobile.presentation.LocalMobileLocale
import com.ratatoskr.mobile.presentation.MobileStringKey
import com.ratatoskr.mobile.presentation.MobileStrings

@Composable
@Suppress("ktlint:standard:function-naming")
fun LibrarySearchSurface(
    state: LibrarySearchState,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (LibraryItemPresentation) -> Unit,
) {
    val locale = LocalMobileLocale.current

    fun string(key: MobileStringKey) = MobileStrings.value(key, locale)
    LibraryColumn {
        AccessibleHeading(string(MobileStringKey.SearchTitle))
        BasicText("${string(MobileStringKey.SearchQueryLabel)}: ${state.query}")
        AccessibleTextInput(
            value = state.query,
            label = string(MobileStringKey.SearchHint),
            onValueChange = onQueryChanged,
        )
        LibraryAction(string(MobileStringKey.SearchAction), onClick = onSubmit)
        when (state) {
            is LibrarySearchState.Idle -> Unit
            is LibrarySearchState.Invalid -> AccessibleStatus(string(MobileStringKey.SearchInvalid))
            is LibrarySearchState.Loading -> AccessibleStatus(string(MobileStringKey.SearchLoading))
            is LibrarySearchState.Empty -> AccessibleStatus(string(MobileStringKey.SearchEmpty))
            is LibrarySearchState.CapabilityUnavailable -> {
                AccessibleStatus(string(MobileStringKey.SearchUnavailable))
            }
            is LibrarySearchState.Offline -> {
                AccessibleStatus(string(MobileStringKey.SearchOffline))
                LibraryAction(string(MobileStringKey.SearchRetry), onClick = onRetry)
            }
            is LibrarySearchState.Unavailable -> AccessibleStatus(string(MobileStringKey.SearchUnavailable))
            is LibrarySearchState.RePairingRequired -> AccessibleStatus(string(MobileStringKey.SearchRepairPairing))
            is LibrarySearchState.Content -> {
                AccessibleHeading(string(MobileStringKey.SearchResults))
                state.items.forEach { item ->
                    val readState =
                        if (item.readState == ReadState.READ) {
                            string(MobileStringKey.SearchRead)
                        } else {
                            string(MobileStringKey.SearchUnread)
                        }
                    Column(
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LibraryAction(
                            label = item.title,
                            accessibleLabel = "${item.title}, $readState",
                        ) { onOpen(item) }
                        BasicText(readState)
                        item.snippet?.let { BasicText(it) }
                        item.score?.let { BasicText("${string(MobileStringKey.SearchRelevance)}: $it") }
                    }
                }
                when {
                    state.loadingMore -> AccessibleStatus(string(MobileStringKey.SearchLoadingMore))
                    state.nextPageRetryable -> LibraryAction(string(MobileStringKey.SearchRetry), onClick = onRetry)
                    state.hasMore -> LibraryAction(string(MobileStringKey.SearchLoadMore), onClick = onLoadMore)
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun LibraryListSurface(
    state: LibraryListState,
    onRefresh: () -> Unit,
    onReplaceReadState: (String, ReadState) -> Unit,
    onOpen: (LibraryReaderRequest) -> Unit,
    onOpenFixtures: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
) {
    LibraryColumn {
        AccessibleHeading("Library")
        LibraryAction("Search library", onClick = onOpenSearch)
        LibraryAction("Contract fixture preview", onClick = onOpenFixtures)
        when (state) {
            LibraryListState.Idle -> LibraryAction("Load library", onClick = onRefresh)
            LibraryListState.Loading -> BasicText("Loading library…")
            LibraryListState.Empty -> {
                BasicText("No analyses yet")
                LibraryAction("Refresh", onClick = onRefresh)
            }
            LibraryListState.CapabilityUnavailable -> BasicText("Library is unavailable on this Ratatoskr instance")
            LibraryListState.Offline -> {
                BasicText("Library is offline")
                LibraryAction("Retry", onClick = onRefresh)
            }
            LibraryListState.RePairingRequired -> BasicText("Pair this device again")
            is LibraryListState.Failed -> {
                BasicText("Library could not be loaded")
                if (state.canRetry) LibraryAction("Retry", onClick = onRefresh)
            }
            is LibraryListState.Content -> {
                BasicText("Recent analyses")
                state.mutationError?.let { BasicText(it.safeMessage(), style = TextStyle(color = ErrorColor)) }
                state.items.forEach { item ->
                    Column(
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BasicText(item.title, style = TextStyle(fontSize = 20.sp))
                        BasicText(if (item.readState == ReadState.READ) "Read" else "Unread")
                        item.snippet?.let { BasicText(it) }
                        LibraryAction("Open") { onOpen(LibraryReaderRequest.LiveSummary(item)) }
                        if (state.canReplaceReadState) {
                            val replacement = if (item.readState == ReadState.READ) ReadState.UNREAD else ReadState.READ
                            val label = if (replacement == ReadState.READ) "Mark read" else "Mark unread"
                            LibraryAction(label, enabled = state.mutatingAnalysisId == null) {
                                onReplaceReadState(item.analysisId, replacement)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun FixtureLibrarySurface(
    catalog: FixtureCatalog,
    onToggleFavorite: (String) -> Unit,
    onSaveNote: (String, String) -> Unit,
    onCollectionMembership: (String, String, Boolean) -> Unit,
    onTagMembership: (String, String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    LibraryColumn {
        BasicText("Contract fixture preview — not synchronized; resets when Ratatoskr restarts.")
        BasicText("Collections", style = TextStyle(fontSize = 22.sp))
        catalog.collections.forEach { BasicText("${it.name}: ${catalog.collectionCount(it.id)}") }
        BasicText("Tags", style = TextStyle(fontSize = 22.sp))
        catalog.tags.forEach { BasicText("${it.name}: ${catalog.tagCount(it.id)}") }
        catalog.items.forEach { item ->
            var note by remember(item.id, item.note) { mutableStateOf(item.note) }
            Column(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF92700C)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicText(item.title, style = TextStyle(fontSize = 20.sp))
                BasicText("${item.family}${item.provider?.let { provider -> " · $provider" }.orEmpty()}")
                LibraryAction("Open fixture") { onOpen(item.id) }
                LibraryAction(if (item.favorite) "Unfavorite" else "Favorite") { onToggleFavorite(item.id) }
                BasicText("Note (local preview)")
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray).padding(8.dp),
                )
                LibraryAction("Save note") { onSaveNote(item.id, note) }
                catalog.collections.forEach { collection ->
                    val included = collection.id in item.collectionIds
                    LibraryAction(if (included) "Remove from ${collection.name}" else "Add to ${collection.name}") {
                        onCollectionMembership(item.id, collection.id, !included)
                    }
                }
                catalog.tags.forEach { tag ->
                    val included = tag.id in item.tagIds
                    LibraryAction(if (included) "Remove tag: ${tag.name}" else "Add tag: ${tag.name}") {
                        onTagMembership(item.id, tag.id, !included)
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun LibraryReaderSurface(
    state: LibraryReaderState,
    onBack: () -> Unit = {},
) {
    LibraryColumn {
        LibraryAction("Back to library", onClick = onBack)
        when (state) {
            LibraryReaderState.Idle -> BasicText("Reader is idle")
            LibraryReaderState.Loading -> BasicText("Loading reader…")
            LibraryReaderState.Unavailable -> BasicText("This content is unavailable")
            is LibraryReaderState.IntegrationPending -> {
                BasicText(state.item.title, style = TextStyle(fontSize = 26.sp))
                BasicText("Full reader contract is integration pending")
                BasicText("Live Platform summary; no fixture content was substituted.")
            }
            is LibraryReaderState.Content -> {
                val item = state.reader.item
                BasicText(item.title, style = TextStyle(fontSize = 26.sp))
                BasicText("Contract fixture preview — integration pending")
                BasicText("Read state: ${if (item.readState == ReadState.READ) "Read" else "Unread"}")
                BasicText("Favorite: ${if (item.favorite) "Yes" else "No"}")
                BasicText("Tags: ${item.tagIds.sorted().joinToString().ifEmpty { "None" }}")
                BasicText("Source: ${item.provenance.source}")
                BasicText("Acquisition: ${item.provenance.acquisition}")
                item.provenance.provider?.let { BasicText("Provider: $it") }
                item.provenance.completeness?.let { BasicText("Completeness: $it") }
                item.summary?.let {
                    BasicText("Analysis", style = TextStyle(fontSize = 22.sp))
                    BasicText(it)
                }
                item.keyPoints.forEach { BasicText("• $it") }
                item.warnings.forEach { BasicText(it, style = TextStyle(color = ErrorColor)) }
                item.blocks.forEach { block -> BasicText(block.text) }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun CollectionDestinationSurface(
    collectionId: String,
    catalog: FixtureCatalog,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    LibraryColumn {
        LibraryAction("Back to library", onClick = onBack)
        val collection = catalog.collections.firstOrNull { it.id == collectionId }
        if (collection == null) {
            BasicText("Collection is integration pending or unavailable")
        } else {
            BasicText(collection.name, style = TextStyle(fontSize = 28.sp))
            BasicText("Contract fixture collection — not synchronized; resets when Ratatoskr restarts.")
            catalog.items
                .filter { collectionId in it.collectionIds }
                .forEach { item -> LibraryAction(item.title) { onOpen(item.id) } }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun LibraryColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun LibraryAction(
    label: String,
    accessibleLabel: String = label,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AccessibleAction(label, accessibleLabel, enabled, onClick)
}

private fun LibraryMutationError.safeMessage(): String =
    when (this) {
        LibraryMutationError.OutcomeUnknown -> "Read-state outcome is unknown; refresh before retrying."
        LibraryMutationError.ItemUnavailable -> "This analysis is no longer available."
        LibraryMutationError.Unavailable -> "Read state could not be updated."
    }

private val ErrorColor = Color(0xFFB00020)
