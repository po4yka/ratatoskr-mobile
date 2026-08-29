package com.ratatoskr.mobile.library

import com.ratatoskr.mobile.api.generated.model.LibraryPage
import com.ratatoskr.mobile.api.generated.model.ReadState
import com.ratatoskr.mobile.api.generated.model.ReadStateResource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FixtureUserContentRepositoryTest {
    @Test
    fun favorite_toggle_preserves_read_and_memberships() =
        runTest {
            val repository = ContractFixtureUserContentRepository()
            val before = repository.state.value.item(ARTICLE_ID)!!

            repository.toggleFavorite(ARTICLE_ID)

            val after = repository.state.value.item(ARTICLE_ID)!!
            assertEquals(!before.favorite, after.favorite)
            assertEquals(before.readState, after.readState)
            assertEquals(before.collectionIds, after.collectionIds)
            assertEquals(before.tagIds, after.tagIds)
            assertTrue(repository.state.value.integrationPending)
        }

    @Test
    fun bounded_note_round_trips_and_oversize_preserves_previous_note() =
        runTest {
            val repository = ContractFixtureUserContentRepository()

            assertIs<FixtureMutationResult.Success>(repository.saveNote(ARTICLE_ID, "Exact synthetic note"))
            assertEquals(
                "Exact synthetic note",
                repository.state.value
                    .item(ARTICLE_ID)
                    ?.note,
            )
            assertIs<FixtureMutationResult.Validation>(repository.saveNote(ARTICLE_ID, "x".repeat(2_001)))
            assertEquals(
                "Exact synthetic note",
                repository.state.value
                    .item(ARTICLE_ID)
                    ?.note,
            )
        }

    @Test
    fun collection_add_is_idempotent() =
        runTest {
            val repository = ContractFixtureUserContentRepository()
            val before = repository.state.value.collectionCount(COLLECTION_ID)

            repository.setCollectionMembership(SOCIAL_ID, COLLECTION_ID, included = true)
            repository.setCollectionMembership(SOCIAL_ID, COLLECTION_ID, included = true)

            assertEquals(before + 1, repository.state.value.collectionCount(COLLECTION_ID))
            assertEquals(
                setOf(COLLECTION_ID),
                repository.state.value
                    .item(SOCIAL_ID)
                    ?.collectionIds,
            )
        }

    @Test
    fun tag_remove_changes_only_named_relation() =
        runTest {
            val repository = ContractFixtureUserContentRepository()
            val before = repository.state.value.item(ARTICLE_ID)!!

            repository.setTagMembership(ARTICLE_ID, TAG_PROVENANCE, included = false)

            val after = repository.state.value.item(ARTICLE_ID)!!
            assertEquals(before.tagIds - TAG_PROVENANCE, after.tagIds)
            assertEquals(before.collectionIds, after.collectionIds)
            assertEquals(before.note, after.note)
            assertEquals(before.favorite, after.favorite)
            assertEquals(before.readState, after.readState)
        }

    @Test
    fun failed_mutation_returns_last_confirmed_snapshot() =
        runTest {
            val repository = ContractFixtureUserContentRepository(FixtureMutationGuard { true })
            val before = repository.state.value

            val result = repository.toggleFavorite(ARTICLE_ID)

            assertEquals(before, assertIs<FixtureMutationResult.Unavailable>(result).snapshot)
            assertEquals(before, repository.state.value)
        }

    @Test
    fun fixture_mutations_make_no_platform_calls() =
        runTest {
            val live = CountingLiveRepository()
            val fixtures = ContractFixtureUserContentRepository()
            val repository = LibraryContentRepository(live, fixtures)

            repository.toggleFixtureFavorite(ARTICLE_ID)
            repository.saveFixtureNote(ARTICLE_ID, "local fixture only")

            assertEquals(0, live.calls)
        }

    private class CountingLiveRepository : LibraryRepository {
        var calls = 0

        override suspend fun recent(): LibraryRepositoryResult<LibraryPage> {
            calls += 1
            error("not expected")
        }

        override suspend fun replaceReadState(
            analysisId: String,
            readState: ReadState,
        ): LibraryRepositoryResult<ReadStateResource> {
            calls += 1
            error("not expected")
        }
    }

    private companion object {
        const val ARTICLE_ID = "00000000-0000-4000-8000-000000000101"
        const val SOCIAL_ID = "00000000-0000-4000-8000-000000000201"
        const val COLLECTION_ID = "reading"
        const val TAG_PROVENANCE = "provenance"
    }
}
