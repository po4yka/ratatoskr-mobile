package com.ratatoskr.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.ratatoskr.mobile.diagnostics.MobileDiagnosticEvent
import com.ratatoskr.mobile.diagnostics.MobileDiagnosticOutcome
import com.ratatoskr.mobile.diagnostics.MobileDiagnostics
import com.ratatoskr.mobile.library.ContentLinkConfiguration
import com.ratatoskr.mobile.library.ContentRouteResult
import com.ratatoskr.mobile.library.ContentRouteTable
import com.ratatoskr.mobile.library.routeIdOrNull
import com.ratatoskr.mobile.operation.OperationListStore
import com.ratatoskr.mobile.presentation.MobileLocale
import com.ratatoskr.mobile.share.AndroidFileIntakeResult
import com.ratatoskr.mobile.share.AndroidShareIntentParser
import com.ratatoskr.mobile.share.AndroidStagingFailure
import com.ratatoskr.mobile.share.AndroidStagingResult
import com.ratatoskr.mobile.share.ShareIntake
import com.ratatoskr.mobile.share.ShareIntakeRejection
import com.ratatoskr.mobile.share.ShareIntakeResult
import com.ratatoskr.mobile.share.ShareIntakeResult.Accepted
import com.ratatoskr.mobile.share.ShareIntakeResult.Rejected
import com.ratatoskr.mobile.share.ShareStagingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val shareParser = AndroidShareIntentParser()
    private val diagnostics = MobileDiagnostics()
    private val container by lazy { (application as RatatoskrApplication).container }
    private val linkConfiguration by lazy {
        ContentLinkConfiguration(setOf(BuildConfig.RATATOSKR_LINK_HOST))
    }
    private var shareStore by mutableStateOf<ShareStagingStore?>(null)
    private lateinit var operationListStore: OperationListStore
    private var activeDetailStore: com.ratatoskr.mobile.operation.OperationDetailStore? = null
    private var isResumed by mutableStateOf(false)
    internal var pendingShareIntake by mutableStateOf<ShareIntakeResult?>(null)
        private set
    internal var pendingOperationId by mutableStateOf<String?>(null)
        private set
    private var pendingContentRoute: ContentRouteResult? by mutableStateOf(null)
    internal val pendingContentRouteId: String?
        get() = pendingContentRoute?.routeIdOrNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        installPendingShare()
        operationListStore = container.createOperationListStore(lifecycleScope)
        setContent {
            RatatoskrApp(
                sessionManager = container.sessions,
                shareStore = shareStore,
                shareRejection = (pendingShareIntake as? Rejected)?.reason,
                onDismissShareRejection = { pendingShareIntake = null },
                operationListStore = operationListStore,
                initialOperationId = pendingOperationId,
                operationDetailStore = { container.createOperationDetailStore(it, lifecycleScope) },
                detailPollingVisible = isResumed,
                onDetailStoreActive = { activeDetailStore = it },
                library = container.library,
                github = container.github,
                initialContentRoute = pendingContentRoute,
                localStorageStore = container.localStorageStore,
                notificationStore = container.notificationStore,
                locale =
                    if (resources.configuration.locales[0].language == "ru") {
                        MobileLocale.Russian
                    } else {
                        MobileLocale.English
                    },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        activeDetailStore?.setVisible(true)
    }

    override fun onPause() {
        isResumed = false
        activeDetailStore?.setVisible(false)
        super.onPause()
    }

    override fun onDestroy() {
        shareStore?.close()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }

    internal fun acceptIntent(intent: Intent) {
        handleIntent(intent)
        installPendingShare()
    }

    private fun installPendingShare() {
        shareStore?.close()
        val accepted = pendingShareIntake as? Accepted
        if (accepted == null) {
            shareStore = null
            return
        }
        shareStore = container.createShareStore(accepted.intake, lifecycleScope)
    }

    internal fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                pendingOperationId = null
                pendingContentRoute = null
                if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                    stageFileShare(intent)
                } else {
                    pendingShareIntake = shareParser.parse(intent)
                }
            }
            ACTION_VIEW_OPERATION -> {
                pendingShareIntake = null
                pendingContentRoute = null
                pendingOperationId = intent.getStringExtra(EXTRA_OPERATION_ID)?.let(::validatedOperationId)
            }
            Intent.ACTION_VIEW -> intent.dataString?.let(::acceptLibraryLink)
        }
    }

    private fun stageFileShare(intent: Intent) {
        when (val parsed = shareParser.parseFile(intent)) {
            is AndroidFileIntakeResult.Rejected -> pendingShareIntake = Rejected(parsed.reason)
            is AndroidFileIntakeResult.Candidate -> {
                pendingShareIntake = null
                lifecycleScope.launch {
                    val staged =
                        try {
                            withContext(Dispatchers.IO) { container.artifactStore.stage(parsed.value) }
                        } finally {
                            runCatching {
                                revokeUriPermission(parsed.value.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                    pendingShareIntake =
                        when (staged) {
                            is AndroidStagingResult.Staged ->
                                Accepted(
                                    ShareIntake.File(
                                        stagedFileId = staged.artifact.artifactId,
                                        displayName = staged.artifact.displayName,
                                        mediaType = staged.artifact.mediaType,
                                        byteSize = staged.artifact.sizeBytes,
                                        sha256Hex = staged.artifact.sha256Hex,
                                    ),
                                )
                            is AndroidStagingResult.Rejected ->
                                Rejected(
                                    when (staged.failure) {
                                        AndroidStagingFailure.Oversized -> ShareIntakeRejection.OversizedFile
                                        AndroidStagingFailure.CapacityExceeded ->
                                            ShareIntakeRejection.StorageCapacityExceeded
                                        AndroidStagingFailure.UnsupportedType,
                                        AndroidStagingFailure.TypeMismatch,
                                        -> ShareIntakeRejection.UnsafeFile
                                        AndroidStagingFailure.Unreadable,
                                        AndroidStagingFailure.Interrupted,
                                        -> ShareIntakeRejection.UnreadableFile
                                    },
                                )
                        }
                    installPendingShare()
                }
            }
        }
    }

    internal fun acceptLibraryLink(value: String): Boolean {
        val parsed = ContentRouteTable.parse(value, linkConfiguration)
        if (parsed !is ContentRouteResult.Accepted) {
            diagnostics.record(MobileDiagnosticEvent.LinkRejected, MobileDiagnosticOutcome.Rejected)
            return false
        }
        diagnostics.record(MobileDiagnosticEvent.LinkAccepted, MobileDiagnosticOutcome.Succeeded)
        pendingShareIntake = null
        pendingOperationId = null
        pendingContentRoute = parsed
        return true
    }

    companion object {
        const val ACTION_VIEW_OPERATION = "com.ratatoskr.mobile.action.VIEW_OPERATION"
        const val EXTRA_OPERATION_ID = "com.ratatoskr.mobile.extra.OPERATION_ID"

        fun validatedOperationId(value: String): String? =
            runCatching { UUID.fromString(value).toString() }
                .getOrNull()
                ?.takeIf { it == value.lowercase() }
    }
}
