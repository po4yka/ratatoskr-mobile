package com.ratatoskr.mobile.share

import android.content.Intent
import android.net.Uri
import java.net.URI

class AndroidShareIntentParser {
    fun parse(intent: Intent): ShareIntakeResult {
        if (intent.action != Intent.ACTION_SEND) {
            return ShareIntakeResult.Rejected(ShareIntakeRejection.UnsupportedAction)
        }
        if (intent.type?.lowercase() != TEXT_MIME_TYPE) {
            return ShareIntakeResult.Rejected(ShareIntakeRejection.UnsupportedMimeType)
        }
        val original = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (original.isNullOrBlank()) {
            return ShareIntakeResult.Rejected(ShareIntakeRejection.MissingText)
        }
        if (original.encodeToByteArray().size > MAX_SHARED_TEXT_BYTES) {
            return ShareIntakeResult.Rejected(ShareIntakeRejection.OversizedText)
        }

        val detected =
            ABSOLUTE_URL
                .findAll(original)
                .map { match -> match.value.trimEnd(*TRAILING_DISPLAY_PUNCTUATION) }
                .filter { it.isNotEmpty() }
                .toList()
        if (detected.size > 1) {
            return ShareIntakeResult.Rejected(ShareIntakeRejection.MultipleUrls)
        }
        if (detected.isEmpty()) {
            return ShareIntakeResult.Accepted(ShareIntake.UnsupportedText(original))
        }

        val url = detected.single()
        val parsed = runCatching { URI(url) }.getOrNull()
        if (
            parsed == null ||
            parsed.host.isNullOrBlank() ||
            parsed.scheme?.lowercase() !in SUPPORTED_SCHEMES
        ) {
            return ShareIntakeResult.Rejected(ShareIntakeRejection.UnsupportedScheme)
        }
        return ShareIntakeResult.Accepted(
            ShareIntake.Url(
                originalText = original,
                url = url,
            ),
        )
    }

    @Suppress("DEPRECATION")
    fun parseFile(intent: Intent): AndroidFileIntakeResult {
        if (intent.action != Intent.ACTION_SEND) {
            return AndroidFileIntakeResult.Rejected(ShareIntakeRejection.UnsupportedAction)
        }
        if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            return AndroidFileIntakeResult.Rejected(ShareIntakeRejection.AmbiguousShare)
        }
        val mediaType =
            intent.type?.lowercase()
                ?: return AndroidFileIntakeResult.Rejected(ShareIntakeRejection.UnsupportedMimeType)
        if (mediaType !in FILE_MIME_TYPES) {
            return AndroidFileIntakeResult.Rejected(ShareIntakeRejection.UnsupportedMimeType)
        }
        if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            return AndroidFileIntakeResult.Rejected(ShareIntakeRejection.MissingReadGrant)
        }
        val uri =
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: return AndroidFileIntakeResult.Rejected(ShareIntakeRejection.MissingFile)
        if (uri.scheme != "content") {
            return AndroidFileIntakeResult.Rejected(ShareIntakeRejection.MissingReadGrant)
        }
        return AndroidFileIntakeResult.Candidate(AndroidFileCandidate(uri, mediaType))
    }

    private companion object {
        const val TEXT_MIME_TYPE = "text/plain"
        const val MAX_SHARED_TEXT_BYTES = 100_000
        val SUPPORTED_SCHEMES = setOf("http", "https")
        val ABSOLUTE_URL = Regex("(?i)\\b[a-z][a-z0-9+.-]*://[^\\s<>]+")
        val TRAILING_DISPLAY_PUNCTUATION = charArrayOf('.', ',', ';', '!', '?', ')', ']', '}')
        val FILE_MIME_TYPES = setOf("application/pdf", "image/jpeg", "image/png", "text/plain")
    }
}

data class AndroidFileCandidate(
    val uri: Uri,
    val mediaType: String,
)

sealed interface AndroidFileIntakeResult {
    data class Candidate(
        val value: AndroidFileCandidate,
    ) : AndroidFileIntakeResult

    data class Rejected(
        val reason: ShareIntakeRejection,
    ) : AndroidFileIntakeResult
}
