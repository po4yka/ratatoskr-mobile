package com.ratatoskr.mobile.transfer

import com.ratatoskr.mobile.transfer.generated.UploadSessionRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

const val MIN_CHUNK_SIZE_BYTES: Int = 65_536
const val MAX_CHUNK_SIZE_BYTES: Int = 16_777_216
const val MAX_CHUNK_COUNT: Int = 10_000

enum class TransferFailure {
    Unsupported,
    InvalidDeclaration,
    InvalidResponse,
    SessionExpired,
    SessionUnknown,
    ChunkConflict,
    Integrity,
    Connectivity,
    LocalPersistence,
    Policy,
}

sealed interface TransferResult<out T> {
    data class Success<T>(
        val value: T,
    ) : TransferResult<T>

    data class Failure(
        val reason: TransferFailure,
    ) : TransferResult<Nothing>
}

data class UploadPlan(
    val declaredSizeBytes: Long,
    val chunkSizeBytes: Int,
    val chunkCount: Int,
) {
    fun chunkLength(index: Int): Int? {
        if (index !in 0 until chunkCount) return null
        if (index < chunkCount - 1) return chunkSizeBytes
        val remainder = (declaredSizeBytes % chunkSizeBytes).toInt()
        return if (remainder == 0) chunkSizeBytes else remainder
    }

    companion object {
        fun create(
            declaredSizeBytes: Long,
            chunkSizeBytes: Int,
        ): TransferResult<UploadPlan> {
            if (declaredSizeBytes <= 0L || chunkSizeBytes !in MIN_CHUNK_SIZE_BYTES..MAX_CHUNK_SIZE_BYTES) {
                return TransferResult.Failure(TransferFailure.InvalidDeclaration)
            }
            val chunkCount = ((declaredSizeBytes - 1L) / chunkSizeBytes + 1L)
            if (chunkCount > MAX_CHUNK_COUNT || chunkCount > Int.MAX_VALUE) {
                return TransferResult.Failure(TransferFailure.InvalidDeclaration)
            }
            return TransferResult.Success(
                UploadPlan(
                    declaredSizeBytes = declaredSizeBytes,
                    chunkSizeBytes = chunkSizeBytes,
                    chunkCount = chunkCount.toInt(),
                ),
            )
        }
    }
}

object BlobTransferContractCodec {
    fun decodeSessionRequest(document: String): TransferResult<UploadSessionRequest> {
        val request =
            runCatching { wireJson.decodeFromString<UploadSessionRequest>(document) }.getOrNull()
                ?: return TransferResult.Failure(TransferFailure.InvalidDeclaration)
        if (
            request.digest.algorithm != "sha256" ||
            !SHA256_HEX.matches(request.digest.hex) ||
            !MEDIA_TYPE.matches(request.mediaType) ||
            UploadPlan.create(request.declaredSizeBytes, request.chunkSizeBytes) is TransferResult.Failure
        ) {
            return TransferResult.Failure(TransferFailure.InvalidDeclaration)
        }
        return TransferResult.Success(request)
    }

    private val wireJson = Json { ignoreUnknownKeys = true }
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    private val MEDIA_TYPE = Regex("^[a-z0-9][a-z0-9!#$&^_.+-]{0,126}/[a-z0-9][a-z0-9!#$&^_.+-]{0,126}$")
}
