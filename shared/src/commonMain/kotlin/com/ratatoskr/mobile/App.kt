package com.ratatoskr.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.ratatoskr.mobile.github.GithubApplicationGraph
import com.ratatoskr.mobile.github.GithubCatalogRoute
import com.ratatoskr.mobile.github.GithubCatalogRow
import com.ratatoskr.mobile.github.GithubCatalogSurface
import com.ratatoskr.mobile.github.GithubDetailRoute
import com.ratatoskr.mobile.github.GithubDetailSurface
import com.ratatoskr.mobile.identity.DeviceIdentityAction
import com.ratatoskr.mobile.identity.DeviceIdentityUiState
import com.ratatoskr.mobile.identity.DeviceIdentityViewModel
import com.ratatoskr.mobile.identity.DeviceSessionManager
import com.ratatoskr.mobile.identity.IdentityFailure
import com.ratatoskr.mobile.library.AiArchiveReaderRoute
import com.ratatoskr.mobile.library.ArticleReaderRoute
import com.ratatoskr.mobile.library.CollectionDestinationSurface
import com.ratatoskr.mobile.library.CollectionRoute
import com.ratatoskr.mobile.library.ContentRouteResult
import com.ratatoskr.mobile.library.FixtureLibraryRoute
import com.ratatoskr.mobile.library.FixtureLibrarySurface
import com.ratatoskr.mobile.library.LibraryApplicationGraph
import com.ratatoskr.mobile.library.LibraryListRoute
import com.ratatoskr.mobile.library.LibraryListSurface
import com.ratatoskr.mobile.library.LibraryReaderRequest
import com.ratatoskr.mobile.library.LibraryReaderSurface
import com.ratatoskr.mobile.library.LibrarySearchRoute
import com.ratatoskr.mobile.library.LibrarySearchSurface
import com.ratatoskr.mobile.library.RepositoryRoute
import com.ratatoskr.mobile.library.SocialReaderRoute
import com.ratatoskr.mobile.library.readerRoute
import com.ratatoskr.mobile.notification.CompletionNotificationStore
import com.ratatoskr.mobile.notification.NotificationSettingsRoute
import com.ratatoskr.mobile.notification.NotificationSettingsSurface
import com.ratatoskr.mobile.operation.OperationDetailRoute
import com.ratatoskr.mobile.operation.OperationDetailStore
import com.ratatoskr.mobile.operation.OperationDetailSurface
import com.ratatoskr.mobile.operation.OperationListRoute
import com.ratatoskr.mobile.operation.OperationListStore
import com.ratatoskr.mobile.operation.OperationListSurface
import com.ratatoskr.mobile.presentation.AccessibleAction
import com.ratatoskr.mobile.presentation.AccessibleHeading
import com.ratatoskr.mobile.presentation.LocalMobileLocale
import com.ratatoskr.mobile.presentation.MobileLocale
import com.ratatoskr.mobile.presentation.MobileStringKey
import com.ratatoskr.mobile.presentation.MobileStrings
import com.ratatoskr.mobile.share.ShareIntakeRejection
import com.ratatoskr.mobile.share.ShareRejectionSurface
import com.ratatoskr.mobile.share.ShareStagingStore
import com.ratatoskr.mobile.share.ShareStagingSurface
import com.ratatoskr.mobile.storage.LocalStorageRoute
import com.ratatoskr.mobile.storage.LocalStorageStore
import com.ratatoskr.mobile.storage.LocalStorageSurface
import kotlinx.coroutines.launch

@Composable
@Suppress("ktlint:standard:function-naming")
fun RatatoskrApp(
    sessionManager: DeviceSessionManager,
    shareStore: ShareStagingStore? = null,
    shareRejection: ShareIntakeRejection? = null,
    onDismissShareRejection: () -> Unit = {},
    operationListStore: OperationListStore? = null,
    initialOperationId: String? = null,
    operationDetailStore: ((String) -> OperationDetailStore)? = null,
    detailPollingVisible: Boolean = true,
    onDetailStoreActive: (OperationDetailStore?) -> Unit = {},
    library: LibraryApplicationGraph? = null,
    github: GithubApplicationGraph? = null,
    initialContentRoute: ContentRouteResult? = null,
    localStorageStore: LocalStorageStore? = null,
    notificationStore: CompletionNotificationStore? = null,
    locale: MobileLocale = MobileLocale.English,
) {
    val identityViewModel = viewModel { DeviceIdentityViewModel(sessionManager) }
    val state by identityViewModel.uiState.collectAsState()
    var route: NavKey? by remember(initialOperationId, initialContentRoute) {
        mutableStateOf(
            (initialContentRoute as? ContentRouteResult.Accepted)?.route
                ?: initialOperationId?.let(::OperationDetailRoute),
        )
    }

    CompositionLocalProvider(LocalMobileLocale provides locale) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            if (shareStore != null) {
                val stagingState by shareStore.state.collectAsState()
                ShareStagingSurface(stagingState, shareStore::dispatch)
            } else if (shareRejection != null) {
                ShareRejectionSurface(shareRejection, onDismissShareRejection)
            } else {
                when (val currentRoute = route) {
                    GithubCatalogRoute -> {
                        val graph = github
                        if (graph == null) {
                            IdentitySurface(
                                state,
                                identityViewModel::dispatch,
                                onOpenOperations = { route = OperationListRoute },
                                onOpenLibrary = { route = LibraryListRoute },
                            )
                        } else {
                            val catalog by graph.catalogStore.state.collectAsState()
                            GithubCatalogSurface(
                                state = catalog,
                                onSearch = graph.catalogStore::search,
                                onOpen = {
                                    graph.select(it)
                                    route = GithubDetailRoute
                                },
                                onBack = { route = null },
                            )
                        }
                    }
                    GithubDetailRoute -> {
                        val graph = github
                        if (graph == null) {
                            IdentitySurface(
                                state,
                                identityViewModel::dispatch,
                                onOpenOperations = { route = OperationListRoute },
                                onOpenLibrary = { route = LibraryListRoute },
                            )
                        } else {
                            val detail by graph.detailStore.state.collectAsState()
                            GithubDetailSurface(
                                state = detail,
                                onSelect = graph.detailStore::select,
                                onConfirm = graph.detailStore::confirm,
                                onCancel = graph.detailStore::cancel,
                                onRetryUncertain = graph.detailStore::retryUncertain,
                                onBack = { route = GithubCatalogRoute },
                            )
                        }
                    }
                    OperationListRoute -> {
                        val store = operationListStore
                        if (store == null) {
                            IdentitySurface(
                                state,
                                identityViewModel::dispatch,
                                onOpenOperations = { route = OperationListRoute },
                            )
                        } else {
                            val operations by store.state.collectAsState()
                            LaunchedEffect(store) { store.refresh() }
                            OperationListSurface(
                                state = operations,
                                onRefresh = store::refresh,
                                onOpen = { route = OperationDetailRoute(it) },
                            )
                        }
                    }
                    is OperationDetailRoute -> {
                        val store =
                            remember(currentRoute.operationId) {
                                operationDetailStore?.invoke(currentRoute.operationId)
                            }
                        if (store == null) {
                            IdentitySurface(
                                state,
                                identityViewModel::dispatch,
                                onOpenOperations = { route = OperationListRoute },
                            )
                        } else {
                            val detail by store.state.collectAsState()
                            DisposableEffect(store) {
                                onDetailStoreActive(store)
                                onDispose {
                                    store.setVisible(false)
                                    onDetailStoreActive(null)
                                }
                            }
                            DisposableEffect(store, detailPollingVisible) {
                                store.setVisible(detailPollingVisible)
                                onDispose { store.setVisible(false) }
                            }
                            OperationDetailSurface(
                                state = detail,
                                onRetry = store::retry,
                                onPair = { route = null },
                            )
                        }
                    }
                    LibraryListRoute -> {
                        val graph = library
                        if (graph == null) {
                            IdentitySurface(
                                state,
                                identityViewModel::dispatch,
                                onOpenOperations = { route = OperationListRoute },
                                onOpenLibrary = { route = LibraryListRoute },
                            )
                        } else {
                            val libraryState by graph.listStore.state.collectAsState()
                            LaunchedEffect(graph.listStore) { graph.listStore.refresh() }
                            LibraryListSurface(
                                state = libraryState,
                                onRefresh = graph.listStore::refresh,
                                onReplaceReadState = graph.listStore::replaceReadState,
                                onOpen = { request ->
                                    graph.readerStore.load(request)
                                    val live = request as LibraryReaderRequest.LiveSummary
                                    route = ArticleReaderRoute(live.item.analysisId)
                                },
                                onOpenFixtures = { route = FixtureLibraryRoute },
                                onOpenSearch = { route = LibrarySearchRoute },
                            )
                        }
                    }
                    LibrarySearchRoute -> {
                        val graph = library
                        if (graph == null) {
                            route = LibraryListRoute
                        } else {
                            val searchState by graph.searchStore.state.collectAsState()
                            LibrarySearchSurface(
                                state = searchState,
                                onQueryChanged = graph.searchStore::updateQuery,
                                onSubmit = graph.searchStore::submit,
                                onRetry = graph.searchStore::retry,
                                onLoadMore = graph.searchStore::loadMore,
                                onOpen = { item ->
                                    graph.readerStore.load(LibraryReaderRequest.LiveSummary(item))
                                    route = ArticleReaderRoute(item.analysisId)
                                },
                            )
                        }
                    }
                    is CollectionRoute -> {
                        val graph = library
                        if (graph == null) {
                            route = LibraryListRoute
                        } else {
                            val catalog by graph.fixtures.state.collectAsState()
                            CollectionDestinationSurface(
                                collectionId = currentRoute.collectionId,
                                catalog = catalog,
                                onOpen = { id ->
                                    catalog.item(id)?.let { item ->
                                        graph.readerStore.load(LibraryReaderRequest.Fixture(id))
                                        route = item.readerRoute()
                                    }
                                },
                                onBack = { route = LibraryListRoute },
                            )
                        }
                    }
                    is RepositoryRoute -> {
                        val graph = github
                        if (graph == null) {
                            route = null
                        } else {
                            LaunchedEffect(currentRoute) {
                                val fullName = "${currentRoute.owner}/${currentRoute.repository}"
                                graph.select(
                                    GithubCatalogRow(
                                        fullName = fullName,
                                        description = "Opened from an exact Ratatoskr link",
                                        canonicalUrl = "https://github.com/$fullName",
                                    ),
                                )
                                route = GithubDetailRoute
                            }
                            BasicText(MobileStrings.value(MobileStringKey.RepositoryOpening, locale))
                        }
                    }
                    FixtureLibraryRoute -> {
                        val graph = library
                        if (graph == null) {
                            IdentitySurface(
                                state,
                                identityViewModel::dispatch,
                                onOpenOperations = { route = OperationListRoute },
                                onOpenLibrary = { route = LibraryListRoute },
                            )
                        } else {
                            val catalog by graph.fixtures.state.collectAsState()
                            val scope = rememberCoroutineScope()
                            FixtureLibrarySurface(
                                catalog = catalog,
                                onToggleFavorite = { id -> scope.launch { graph.fixtures.toggleFavorite(id) } },
                                onSaveNote = { id, note -> scope.launch { graph.fixtures.saveNote(id, note) } },
                                onCollectionMembership = { itemId, collectionId, included ->
                                    scope.launch {
                                        graph.fixtures.setCollectionMembership(itemId, collectionId, included)
                                    }
                                },
                                onTagMembership = { itemId, tagId, included ->
                                    scope.launch { graph.fixtures.setTagMembership(itemId, tagId, included) }
                                },
                                onOpen = { id ->
                                    catalog.item(id)?.let { item ->
                                        graph.readerStore.load(LibraryReaderRequest.Fixture(id))
                                        route = item.readerRoute()
                                    }
                                },
                            )
                        }
                    }
                    is ArticleReaderRoute,
                    is SocialReaderRoute,
                    is AiArchiveReaderRoute,
                    -> {
                        val graph = library
                        if (graph == null) {
                            IdentitySurface(
                                state,
                                identityViewModel::dispatch,
                                onOpenOperations = { route = OperationListRoute },
                                onOpenLibrary = { route = LibraryListRoute },
                            )
                        } else {
                            val destinationId =
                                when (currentRoute) {
                                    is ArticleReaderRoute -> currentRoute.analysisId
                                    is SocialReaderRoute -> currentRoute.sourceId
                                    is AiArchiveReaderRoute -> currentRoute.itemId
                                    else -> error("unreachable")
                                }
                            LaunchedEffect(currentRoute) {
                                val fixture =
                                    graph.fixtures.state.value
                                        .item(destinationId)
                                if (fixture != null) {
                                    graph.readerStore.load(LibraryReaderRequest.Fixture(destinationId))
                                } else {
                                    val live =
                                        (graph.listStore.state.value as? com.ratatoskr.mobile.library.LibraryListState.Content)
                                            ?.items
                                            ?.firstOrNull { it.analysisId == destinationId }
                                            ?: (graph.searchStore.state.value as? com.ratatoskr.mobile.library.LibrarySearchState.Content)
                                                ?.items
                                                ?.firstOrNull { it.analysisId == destinationId }
                                    if (live == null) {
                                        graph.readerStore.load(LibraryReaderRequest.Fixture(destinationId))
                                    } else {
                                        graph.readerStore.load(LibraryReaderRequest.LiveSummary(live))
                                    }
                                }
                            }
                            val reader by graph.readerStore.state.collectAsState()
                            LibraryReaderSurface(reader) { route = LibraryListRoute }
                        }
                    }
                    LocalStorageRoute -> {
                        val store = localStorageStore
                        if (store == null) {
                            route = null
                        } else {
                            val storageState by store.state.collectAsState()
                            LocalStorageSurface(storageState, store::dispatch) { route = null }
                        }
                    }
                    NotificationSettingsRoute -> {
                        val store = notificationStore
                        if (store == null) {
                            route = null
                        } else {
                            val notificationState by store.state.collectAsState()
                            NotificationSettingsSurface(
                                state = notificationState,
                                onEnable = store::enable,
                                onDisable = store::disable,
                                onBack = { route = null },
                            )
                        }
                    }
                    else ->
                        IdentitySurface(
                            state,
                            identityViewModel::dispatch,
                            onOpenOperations = { route = OperationListRoute },
                            onOpenLibrary = { route = LibraryListRoute },
                            onOpenGithub = github?.let { { route = GithubCatalogRoute } },
                            onOpenStorage = localStorageStore?.let { { route = LocalStorageRoute } },
                            onOpenNotifications = notificationStore?.let { { route = NotificationSettingsRoute } },
                        )
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun IdentitySurface(
    state: DeviceIdentityUiState,
    dispatch: (DeviceIdentityAction) -> Unit,
    onOpenOperations: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenGithub: (() -> Unit)? = null,
    onOpenStorage: (() -> Unit)? = null,
    onOpenNotifications: (() -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AccessibleHeading("Ratatoskr")
        when (state) {
            is DeviceIdentityUiState.PairingForm -> PairingForm(state, dispatch)
            DeviceIdentityUiState.Working -> BasicText("Working…")
            is DeviceIdentityUiState.Paired ->
                PairedSession(
                    state,
                    dispatch,
                    onOpenOperations,
                    onOpenLibrary,
                    onOpenGithub,
                    onOpenStorage,
                    onOpenNotifications,
                )
            DeviceIdentityUiState.RePairingRequired -> {
                BasicText("This device session was revoked and local data was erased. Restart Ratatoskr to pair again.")
            }
            is DeviceIdentityUiState.Failed -> BasicText(state.failure.safeMessage())
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun PairingForm(
    state: DeviceIdentityUiState.PairingForm,
    dispatch: (DeviceIdentityAction) -> Unit,
) {
    BasicText("Pair this device with your Platform identity.")
    PairingTextField(
        value = state.origin,
        onValueChange = { dispatch(DeviceIdentityAction.OriginChanged(it)) },
        label = "Platform HTTPS origin",
    )
    PairingTextField(
        value = state.code,
        onValueChange = { dispatch(DeviceIdentityAction.CodeChanged(it)) },
        label = "Pairing code",
        visualTransformation = PasswordVisualTransformation(),
    )
    PairingTextField(
        value = state.displayName,
        onValueChange = { dispatch(DeviceIdentityAction.DisplayNameChanged(it)) },
        label = "Device name (optional)",
    )
    state.error?.let { BasicText(it.safeMessage(), style = TextStyle(color = Color(0xFFB00020))) }
    ActionButton(
        label = "Pair device",
        enabled = state.origin.isNotBlank() && state.code.isNotBlank(),
        onClick = { dispatch(DeviceIdentityAction.SubmitPairing) },
    )
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun PairedSession(
    state: DeviceIdentityUiState.Paired,
    dispatch: (DeviceIdentityAction) -> Unit,
    onOpenOperations: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenGithub: (() -> Unit)?,
    onOpenStorage: (() -> Unit)?,
    onOpenNotifications: (() -> Unit)?,
) {
    BasicText("Paired with ${state.origin}")
    BasicText(if (state.capabilitiesFresh) "Capabilities are current" else "Capabilities are unavailable or stale")
    if (state.capabilities.isNotEmpty()) {
        BasicText(state.capabilities.sorted().joinToString(separator = "\n"))
    }
    ActionButton("Refresh capabilities") { dispatch(DeviceIdentityAction.RefreshCapabilities) }
    ActionButton("Operations", onClick = onOpenOperations)
    ActionButton("Library", onClick = onOpenLibrary)
    onOpenGithub?.let { ActionButton("GitHub repositories", onClick = it) }
    onOpenStorage?.let { ActionButton("Local storage", onClick = it) }
    onOpenNotifications?.let { ActionButton("Operation notifications", onClick = it) }
    ActionButton("Sign out on this device") { dispatch(DeviceIdentityAction.SignOut) }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun PairingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Gray)
                    .padding(12.dp),
            singleLine = true,
            visualTransformation = visualTransformation,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ActionButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AccessibleAction(label, enabled = enabled, onClick = onClick)
}

private fun IdentityFailure.safeMessage(): String =
    when (this) {
        IdentityFailure.InvalidOrigin -> "Enter a canonical HTTPS Platform origin."
        IdentityFailure.Validation -> "The pairing request is not valid."
        IdentityFailure.PairingRefused -> "The pairing code cannot be accepted."
        IdentityFailure.Unauthorized -> "Authorization is no longer available."
        is IdentityFailure.Unavailable -> "Platform is temporarily unavailable."
        IdentityFailure.Uncertain -> "The pairing outcome is uncertain. Request a new code."
        IdentityFailure.InvalidResponse -> "Platform returned an invalid response."
        IdentityFailure.SecureStorage -> "Secure device storage is unavailable."
    }
