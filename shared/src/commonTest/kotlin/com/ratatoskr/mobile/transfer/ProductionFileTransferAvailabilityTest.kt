package com.ratatoskr.mobile.transfer

import com.ratatoskr.mobile.transfer.generated.TransferContentDigest
import com.ratatoskr.mobile.transfer.generated.UploadSessionRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProductionFileTransferAvailabilityTest {
    @Test
    fun missing_public_receipt_binding_is_integration_pending_and_sends_nothing() =
        runTest {
            val availability = ProductionFileTransferAvailability()

            assertEquals(FileTransferAvailability.IntegrationPending, availability.current())
            val result =
                IntegrationPendingBlobReceiptTransport().open(
                    UploadSessionRequest(
                        declaredSizeBytes = 65_536,
                        mediaType = "application/pdf",
                        digest = TransferContentDigest("sha256", "1".repeat(64)),
                        chunkSizeBytes = 65_536,
                    ),
                )
            assertEquals(TransferFailure.Policy, assertIs<TransferResult.Failure>(result).reason)
        }
}
