package com.ratatoskr.mobile.share

sealed interface ShareIntake {
    val originalText: String

    data class Url(
        override val originalText: String,
        val url: String,
    ) : ShareIntake

    data class UnsupportedText(
        override val originalText: String,
    ) : ShareIntake
}

enum class ShareIntakeRejection {
    UnsupportedAction,
    UnsupportedMimeType,
    MissingText,
    OversizedText,
    UnsupportedScheme,
    MultipleUrls,
}

sealed interface ShareIntakeResult {
    data class Accepted(
        val intake: ShareIntake,
    ) : ShareIntakeResult

    data class Rejected(
        val reason: ShareIntakeRejection,
    ) : ShareIntakeResult
}
