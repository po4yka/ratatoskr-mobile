package com.ratatoskr.mobile.capture

import com.ratatoskr.mobile.queue.QueueLimits
import com.ratatoskr.mobile.queue.QueueRejection
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant

@Serializable
data class CaptureOwner(
    val origin: String,
    val accountId: String,
)

@Serializable
enum class CaptureSource {
    @SerialName("main_app")
    MainApp,

    @SerialName("android_share_target")
    AndroidShareTarget,

    @SerialName("ios_share_extension")
    IosShareExtension,
}

@Serializable
sealed interface CapturePayload {
    @Serializable
    @SerialName("url")
    data class Url(
        val value: String,
    ) : CapturePayload

    @Serializable
    @SerialName("text_note")
    data class TextNote(
        val text: String,
    ) : CapturePayload

    @Serializable
    @SerialName("file_reference")
    data class FileReference(
        val stagedFileId: String,
        val displayName: String,
        val mediaType: String,
        val byteSize: Long,
    ) : CapturePayload
}

@Serializable
data class CaptureRequest(
    val owner: CaptureOwner,
    val source: CaptureSource,
    val payload: CapturePayload,
    val createdAt: Instant,
)

object CaptureCodec {
    private val json =
        Json {
            classDiscriminator = "kind"
            encodeDefaults = true
        }

    fun validate(
        request: CaptureRequest,
        limits: QueueLimits = QueueLimits(),
    ): QueueRejection? {
        if (!isCanonicalOrigin(request.owner.origin) || request.owner.accountId.isBlank()) {
            return QueueRejection.InvalidCapture
        }
        val valid =
            when (val payload = request.payload) {
                is CapturePayload.Url -> isValidUrl(payload.value, limits.maxUrlLength)
                is CapturePayload.TextNote ->
                    payload.text.isNotEmpty() && payload.text.encodeToByteArray().size <= limits.maxTextBytes
                is CapturePayload.FileReference ->
                    isOpaqueId(payload.stagedFileId) &&
                        payload.displayName.isNotBlank() &&
                        payload.displayName.length <= MAX_DISPLAY_NAME_LENGTH &&
                        payload.mediaType.count { it == '/' } == 1 &&
                        payload.byteSize in 1..limits.maxStagedFileBytes
            }
        return if (valid) null else QueueRejection.InvalidCapture
    }

    fun encode(request: CaptureRequest): String = json.encodeToString(request)

    fun decode(value: String): CaptureRequest = json.decodeFromString(value)

    private fun isCanonicalOrigin(value: String): Boolean {
        if (!value.startsWith("https://") || value != value.trim() || value.endsWith('/')) return false
        val authority = value.removePrefix("https://")
        return authority.isNotBlank() && authority.none { it == '/' || it == '?' || it == '#' || it == '@' }
    }

    private fun isValidUrl(
        value: String,
        maxLength: Int,
    ): Boolean {
        if (value != value.trim() || value.length > maxLength) return false
        val url = runCatching { Url(value) }.getOrNull() ?: return false
        return url.host.isNotBlank() && (url.protocol == URLProtocol.HTTP || url.protocol == URLProtocol.HTTPS)
    }

    private fun isOpaqueId(value: String): Boolean =
        value.length in 1..MAX_STAGED_ID_LENGTH &&
            value.first().isLetterOrDigit() &&
            value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' } &&
            ".." !in value

    private const val MAX_STAGED_ID_LENGTH = 128
    private const val MAX_DISPLAY_NAME_LENGTH = 255
}
