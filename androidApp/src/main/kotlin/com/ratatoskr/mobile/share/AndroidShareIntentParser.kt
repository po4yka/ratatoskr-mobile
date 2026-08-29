package com.ratatoskr.mobile.share

import android.content.Intent
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

    private companion object {
        const val TEXT_MIME_TYPE = "text/plain"
        const val MAX_SHARED_TEXT_BYTES = 100_000
        val SUPPORTED_SCHEMES = setOf("http", "https")
        val ABSOLUTE_URL = Regex("(?i)\\b[a-z][a-z0-9+.-]*://[^\\s<>]+")
        val TRAILING_DISPLAY_PUNCTUATION = charArrayOf('.', ',', ';', '!', '?', ')', ']', '}')
    }
}
