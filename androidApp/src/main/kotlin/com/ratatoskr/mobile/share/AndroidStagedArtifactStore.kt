package com.ratatoskr.mobile.share

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class AndroidSourceMetadata(
    val displayName: String,
    val sizeBytes: Long?,
    val mediaType: String?,
)

interface AndroidContentSource {
    fun metadata(uri: Uri): AndroidSourceMetadata

    fun open(uri: Uri): InputStream
}

class ContentResolverAndroidContentSource(
    private val resolver: ContentResolver,
) : AndroidContentSource {
    override fun metadata(uri: Uri): AndroidSourceMetadata {
        var displayName = "shared-file"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                displayName = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME) ?: displayName
                size = cursor.longOrNull(OpenableColumns.SIZE)
            }
        }
        return AndroidSourceMetadata(displayName, size, resolver.getType(uri))
    }

    override fun open(uri: Uri): InputStream = requireNotNull(resolver.openInputStream(uri)) { "Shared content is unreadable" }

    private fun Cursor.stringOrNull(column: String): String? = getColumnIndex(column).takeIf { it >= 0 }?.let(::getString)

    private fun Cursor.longOrNull(column: String): Long? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)
}

data class AndroidStagedArtifact(
    val artifactId: String,
    val displayName: String,
    val mediaType: String,
    val sizeBytes: Long,
    val sha256Hex: String,
)

enum class AndroidStagingFailure {
    UnsupportedType,
    TypeMismatch,
    Oversized,
    CapacityExceeded,
    Unreadable,
    Interrupted,
}

sealed interface AndroidStagingResult {
    data class Staged(
        val artifact: AndroidStagedArtifact,
    ) : AndroidStagingResult

    data class Rejected(
        val failure: AndroidStagingFailure,
    ) : AndroidStagingResult
}

class AndroidStagedArtifactStore(
    private val root: File,
    private val source: AndroidContentSource,
    private val idFactory: () -> String,
    private val afterBytesCopied: (Long) -> Unit = {},
) {
    @Synchronized
    fun stage(candidate: AndroidFileCandidate): AndroidStagingResult {
        if (candidate.mediaType !in SUPPORTED_TYPES) {
            return AndroidStagingResult.Rejected(AndroidStagingFailure.UnsupportedType)
        }
        val metadata =
            runCatching { source.metadata(candidate.uri) }.getOrElse {
                return AndroidStagingResult.Rejected(AndroidStagingFailure.Unreadable)
            }
        if (metadata.mediaType?.lowercase() != candidate.mediaType) {
            return AndroidStagingResult.Rejected(AndroidStagingFailure.TypeMismatch)
        }
        if (metadata.sizeBytes?.let { it <= 0 || it > MAX_FILE_BYTES } == true) {
            return AndroidStagingResult.Rejected(AndroidStagingFailure.Oversized)
        }
        val artifactId = idFactory()
        if (!artifactId.matches(OPAQUE_ID)) {
            return AndroidStagingResult.Rejected(AndroidStagingFailure.Unreadable)
        }
        root.mkdirs()
        if (!root.isDirectory ||
            java.nio.file.Files
                .isSymbolicLink(root.toPath())
        ) {
            return AndroidStagingResult.Rejected(AndroidStagingFailure.Unreadable)
        }
        val existing = root.listFiles().orEmpty()
        if (existing.any {
                java.nio.file.Files
                    .isSymbolicLink(it.toPath()) ||
                    !it.isFile
            }
        ) {
            return AndroidStagingResult.Rejected(AndroidStagingFailure.Unreadable)
        }
        val publishedCount = existing.count { !it.name.endsWith(".partial") }
        val existingBytes = existing.sumOf(File::length)
        if (
            publishedCount >= MAX_ARTIFACT_COUNT ||
            metadata.sizeBytes?.let { existingBytes + it > MAX_STAGED_BYTES } == true
        ) {
            return AndroidStagingResult.Rejected(AndroidStagingFailure.CapacityExceeded)
        }
        val partial = File(root, ".$artifactId.partial")
        val published = File(root, artifactId)
        if (published.exists()) return AndroidStagingResult.Rejected(AndroidStagingFailure.Unreadable)
        var copied = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        val evidence = ArrayList<Byte>(16)
        try {
            source.open(candidate.uri).use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        copied += read
                        if (copied > MAX_FILE_BYTES) {
                            return AndroidStagingResult.Rejected(AndroidStagingFailure.Oversized)
                        }
                        if (existingBytes + copied > MAX_STAGED_BYTES) {
                            return AndroidStagingResult.Rejected(AndroidStagingFailure.CapacityExceeded)
                        }
                        repeat(minOf(read, 16 - evidence.size)) { evidence += buffer[it] }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        afterBytesCopied(copied)
                    }
                    output.fd.sync()
                }
            }
            if (copied <= 0 || metadata.sizeBytes?.let { it != copied } == true) {
                return AndroidStagingResult.Rejected(AndroidStagingFailure.Unreadable)
            }
            if (!matchesEvidence(candidate.mediaType, evidence.toByteArray())) {
                return AndroidStagingResult.Rejected(AndroidStagingFailure.TypeMismatch)
            }
            if (!partial.renameTo(published)) {
                return AndroidStagingResult.Rejected(AndroidStagingFailure.Unreadable)
            }
            return AndroidStagingResult.Staged(
                AndroidStagedArtifact(
                    artifactId = artifactId,
                    displayName = sanitizeDisplayName(metadata.displayName),
                    mediaType = candidate.mediaType,
                    sizeBytes = copied,
                    sha256Hex = digest.digest().joinToString("") { "%02x".format(it) },
                ),
            )
        } catch (_: Throwable) {
            return AndroidStagingResult.Rejected(AndroidStagingFailure.Interrupted)
        } finally {
            partial.delete()
        }
    }

    fun publishedFiles(): List<File> = root.listFiles()?.filterNot { it.name.endsWith(".partial") } ?: emptyList()

    fun deleteUnreferenced(artifactId: String): Boolean {
        if (!artifactId.matches(OPAQUE_ID)) return false
        val file = File(root, artifactId)
        if (java.nio.file.Files
                .isSymbolicLink(file.toPath())
        ) {
            return false
        }
        return !file.exists() || file.delete()
    }

    private fun sanitizeDisplayName(value: String): String =
        File(value)
            .name
            .filterNot(Char::isISOControl)
            .trim()
            .ifEmpty { "shared-file" }
            .take(255)

    private fun matchesEvidence(
        mediaType: String,
        bytes: ByteArray,
    ): Boolean =
        when (mediaType) {
            "application/pdf" -> bytes.startsWith("%PDF-".encodeToByteArray())
            "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
            "image/png" -> bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            "text/plain" -> bytes.none { it == 0.toByte() }
            else -> false
        }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        const val MAX_FILE_BYTES = 100L * 1024L * 1024L
        const val MAX_STAGED_BYTES = 512L * 1024L * 1024L
        const val MAX_ARTIFACT_COUNT = 64
        val OPAQUE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        val SUPPORTED_TYPES = setOf("application/pdf", "image/jpeg", "image/png", "text/plain")
    }
}
