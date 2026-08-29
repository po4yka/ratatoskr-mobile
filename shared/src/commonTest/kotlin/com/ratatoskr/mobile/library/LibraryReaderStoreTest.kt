package com.ratatoskr.mobile.library

import com.ratatoskr.mobile.api.generated.model.ReadState
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryReaderStoreTest {
    @Test
    fun article_reader_preserves_provenance_warnings_and_ordered_inert_blocks() =
        runTest {
            val store = LibraryReaderStore(ContractFixtureUserContentRepository(), this)

            store.load(LibraryReaderRequest.Fixture(FixtureIds.ARTICLE))
            advanceUntilIdle()

            val reader = content(store)
            assertEquals("https://example.test/articles/evidence", reader.item.provenance.source)
            assertEquals("explicit mobile capture", reader.item.provenance.acquisition)
            assertEquals(
                listOf(FixtureBlockKind.Heading, FixtureBlockKind.Paragraph),
                reader.item.blocks.map { it.kind },
            )
            assertTrue(
                reader.item.blocks
                    .last()
                    .text
                    .contains("<script>"),
            )
            assertEquals(listOf("Synthetic extraction omitted one decorative block."), reader.item.warnings)
        }

    @Test
    fun live_summary_without_detail_is_integration_pending() =
        runTest {
            val summary =
                LibraryItemPresentation(
                    analysisId = LIVE_ID,
                    documentId = DOCUMENT_ID,
                    title = "Live summary",
                    readState = ReadState.UNREAD,
                    snippet = null,
                )
            val store = LibraryReaderStore(ContractFixtureUserContentRepository(), this)

            store.load(LibraryReaderRequest.LiveSummary(summary))
            advanceUntilIdle()

            assertEquals(summary, assertIs<LibraryReaderState.IntegrationPending>(store.state.value).item)
        }

    @Test
    fun social_reader_does_not_infer_saved_authority() =
        runTest {
            val store = LibraryReaderStore(ContractFixtureUserContentRepository(), this)

            store.load(LibraryReaderRequest.Fixture(FixtureIds.INSTAGRAM))
            advanceUntilIdle()

            val item = content(store).item
            assertEquals("instagram", item.provider)
            assertEquals("explicit_user_capture; not native Saved authority", item.provenance.acquisition)
            assertNull(item.provenance.completeness)
        }

    @Test
    fun ai_archive_reader_preserves_import_completeness() =
        runTest {
            val store = LibraryReaderStore(ContractFixtureUserContentRepository(), this)

            store.load(LibraryReaderRequest.Fixture(FixtureIds.CLAUDE))
            advanceUntilIdle()

            val item = content(store).item
            assertEquals(FixtureContentFamily.AiArchive, item.family)
            assertEquals("claude", item.provider)
            assertEquals("partial fixture artifact", item.provenance.completeness)
            assertEquals(listOf("Fixture marks one attachment unavailable."), item.warnings)
        }

    private fun content(store: LibraryReaderStore) = assertIs<LibraryReaderState.Content>(store.state.value).reader

    private companion object {
        const val LIVE_ID = "00000000-0000-4000-8000-000000000901"
        const val DOCUMENT_ID = "00000000-0000-4000-8000-000000000902"
    }
}
