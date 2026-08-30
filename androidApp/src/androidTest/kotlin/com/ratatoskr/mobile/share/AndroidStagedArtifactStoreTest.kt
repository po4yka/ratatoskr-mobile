package com.ratatoskr.mobile.share

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidStagedArtifactStoreTest {
    private val root = File(ApplicationProvider.getApplicationContext<android.content.Context>().noBackupFilesDir, "staging-test")
    private val uri = Uri.parse("content://ratatoskr.test/synthetic.pdf")

    @After
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun supported_content_uri_is_atomically_staged() {
        val store = store(PDF, "application/pdf", "../synthetic.pdf")

        val result = store.stage(AndroidFileCandidate(uri, "application/pdf"))

        val staged = result as AndroidStagingResult.Staged
        assertEquals("artifact-1", staged.artifact.artifactId)
        assertEquals("synthetic.pdf", staged.artifact.displayName)
        assertEquals(PDF.size.toLong(), staged.artifact.sizeBytes)
        assertEquals(64, staged.artifact.sha256Hex.length)
        assertEquals(listOf("artifact-1"), store.publishedFiles().map { it.name })
    }

    @Test
    fun mismatched_or_oversized_file_is_refused() {
        assertEquals(
            AndroidStagingResult.Rejected(AndroidStagingFailure.TypeMismatch),
            store("plain".encodeToByteArray(), "application/pdf", "x.pdf").stage(AndroidFileCandidate(uri, "application/pdf")),
        )
        val oversizedMetadata = FakeSource(PDF, AndroidSourceMetadata("x.pdf", 100L * 1024L * 1024L + 1, "application/pdf"))
        assertEquals(
            AndroidStagingResult.Rejected(AndroidStagingFailure.Oversized),
            AndroidStagedArtifactStore(root, oversizedMetadata, { "artifact-1" }).stage(AndroidFileCandidate(uri, "application/pdf")),
        )
    }

    @Test
    fun interrupted_copy_publishes_no_artifact() {
        val source = FakeSource(PDF, AndroidSourceMetadata("x.pdf", PDF.size.toLong(), "application/pdf"))
        val store = AndroidStagedArtifactStore(root, source, { "artifact-1" }) { throw IllegalStateException("stop") }

        val result = store.stage(AndroidFileCandidate(uri, "application/pdf"))

        assertEquals(AndroidStagingResult.Rejected(AndroidStagingFailure.Interrupted), result)
        assertTrue(store.publishedFiles().isEmpty())
    }

    @Test
    fun staging_refuses_a_sixty_fifth_published_artifact() {
        root.mkdirs()
        repeat(64) { index -> File(root, "existing-$index").writeBytes(PDF) }

        val result = store(PDF, "application/pdf", "x.pdf").stage(AndroidFileCandidate(uri, "application/pdf"))

        assertEquals(AndroidStagingResult.Rejected(AndroidStagingFailure.CapacityExceeded), result)
        assertEquals(64, root.listFiles()?.size)
    }

    private fun store(
        bytes: ByteArray,
        type: String,
        name: String,
    ) = AndroidStagedArtifactStore(
        root,
        FakeSource(bytes, AndroidSourceMetadata(name, bytes.size.toLong(), type)),
        { "artifact-1" },
    )

    private class FakeSource(
        private val bytes: ByteArray,
        private val metadata: AndroidSourceMetadata,
    ) : AndroidContentSource {
        override fun metadata(uri: Uri) = metadata

        override fun open(uri: Uri) = ByteArrayInputStream(bytes)
    }

    private companion object {
        val PDF = "%PDF-1.7\nsynthetic".encodeToByteArray()
    }
}
