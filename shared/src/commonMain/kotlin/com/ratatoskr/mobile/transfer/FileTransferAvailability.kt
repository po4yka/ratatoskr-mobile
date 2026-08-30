package com.ratatoskr.mobile.transfer

import com.ratatoskr.mobile.transfer.generated.UploadChunkReceipt
import com.ratatoskr.mobile.transfer.generated.UploadCompletionOutcome
import com.ratatoskr.mobile.transfer.generated.UploadSessionOpened
import com.ratatoskr.mobile.transfer.generated.UploadSessionRequest
import com.ratatoskr.mobile.transfer.generated.UploadStatusResponse

enum class FileTransferAvailability {
    Available,
    IntegrationPending,
}

class ProductionFileTransferAvailability {
    fun current(): FileTransferAvailability = FileTransferAvailability.IntegrationPending
}

class IntegrationPendingBlobReceiptTransport : BlobReceiptTransport {
    override suspend fun open(request: UploadSessionRequest): TransferResult<UploadSessionOpened> = pending()

    override suspend fun status(resumptionToken: String): TransferResult<UploadStatusResponse> = pending()

    override suspend fun putChunk(
        resumptionToken: String,
        chunkIndex: Int,
        bytes: ByteArray,
    ): TransferResult<UploadChunkReceipt> = pending()

    override suspend fun finalize(resumptionToken: String): TransferResult<UploadCompletionOutcome> = pending()

    private fun pending(): TransferResult.Failure = TransferResult.Failure(TransferFailure.Policy)
}
