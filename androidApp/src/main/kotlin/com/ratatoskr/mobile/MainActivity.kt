package com.ratatoskr.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.ratatoskr.mobile.operation.OperationListStore
import com.ratatoskr.mobile.share.AndroidShareIntentParser
import com.ratatoskr.mobile.share.ShareIntakeResult
import com.ratatoskr.mobile.share.ShareIntakeResult.Accepted
import com.ratatoskr.mobile.share.ShareIntakeResult.Rejected
import com.ratatoskr.mobile.share.ShareStagingStore
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val shareParser = AndroidShareIntentParser()
    private val container by lazy { (application as RatatoskrApplication).container }
    private var shareStore by mutableStateOf<ShareStagingStore?>(null)
    private lateinit var operationListStore: OperationListStore
    private var activeDetailStore: com.ratatoskr.mobile.operation.OperationDetailStore? = null
    private var isResumed by mutableStateOf(false)
    internal var pendingShareIntake by mutableStateOf<ShareIntakeResult?>(null)
        private set
    internal var pendingOperationId by mutableStateOf<String?>(null)
        private set

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
                pendingShareIntake = shareParser.parse(intent)
            }
            ACTION_VIEW_OPERATION -> {
                pendingShareIntake = null
                pendingOperationId = intent.getStringExtra(EXTRA_OPERATION_ID)?.let(::validatedOperationId)
            }
        }
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
