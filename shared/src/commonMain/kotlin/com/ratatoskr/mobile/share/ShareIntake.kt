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

    data class File(
        val stagedFileId: String,
        val displayName: String,
        val mediaType: String,
        val byteSize: Long,
        val sha256Hex: String,
    ) : ShareIntake {
        override val originalText: String = displayName
    }
}

enum class ShareIntakeRejection {
    UnsupportedAction,
    UnsupportedMimeType,
    MissingText,
    OversizedText,
    UnsupportedScheme,
    MultipleUrls,
    MissingFile,
    MissingReadGrant,
    AmbiguousShare,
    OversizedFile,
    StorageCapacityExceeded,
    UnsafeFile,
    UnreadableFile,
}

sealed interface ShareIntakeResult {
    data class Accepted(
        val intake: ShareIntake,
    ) : ShareIntakeResult

    data class Rejected(
        val reason: ShareIntakeRejection,
    ) : ShareIntakeResult
}
