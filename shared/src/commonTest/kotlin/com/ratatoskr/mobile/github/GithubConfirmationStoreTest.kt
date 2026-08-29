package com.ratatoskr.mobile.github

import com.ratatoskr.mobile.identity.GithubServiceCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GithubConfirmationStoreTest {
    @Test
    fun track_selection_only_discloses_ratatoskr_tracking_until_confirmed() =
        runTest {
            val repository = RecordingGithubRepository(actionResults = mutableListOf(success()))
            val store = store(repository, backgroundScope)
            store.load(githubPreview.target.canonicalUrl)
            runCurrent()

            store.select(GithubActionMode.Track)

            val pending = assertNotNull(content(store).pending)
            assertTrue(githubPreview.target.fullName in pending.disclosure)
            assertTrue("Ratatoskr" in pending.disclosure)
            assertTrue("desired backup tracking" in pending.disclosure)
            assertTrue("does not perform a GitHub write" in pending.disclosure)
            assertTrue(repository.actions.isEmpty())

            store.confirm(pending)
            runCurrent()
            assertEquals(listOf(GithubActionMode.Track), repository.actions.map { it.mode })
        }

    @Test
    fun star_selection_names_repository_account_and_external_write_until_confirmed() =
        runTest {
            val repository = RecordingGithubRepository(actionResults = mutableListOf(success()))
            val store = store(repository, backgroundScope)
            store.load(githubPreview.target.canonicalUrl)
            runCurrent()

            store.select(GithubActionMode.Star)

            val pending = assertNotNull(content(store).pending)
            assertTrue(githubPreview.target.fullName in pending.disclosure)
            assertTrue(githubPreview.accountRef!! in pending.disclosure)
            assertTrue("external GitHub star" in pending.disclosure)
            assertTrue("metadata" in pending.disclosure)
            assertTrue("desired backup" in pending.disclosure)
            assertTrue(repository.actions.isEmpty())
        }

    @Test
    fun cancel_replay_and_replaced_confirmation_send_nothing() =
        runTest {
            val repository = RecordingGithubRepository()
            val store = store(repository, backgroundScope)
            store.load(githubPreview.target.canonicalUrl)
            runCurrent()

            store.select(GithubActionMode.Track)
            val cancelled = assertNotNull(content(store).pending)
            store.cancel(cancelled)
            store.confirm(cancelled)
            store.select(GithubActionMode.Track)
            val replaced = assertNotNull(content(store).pending)
            store.select(GithubActionMode.Star)
            store.confirm(replaced)
            runCurrent()

            assertTrue(repository.actions.isEmpty())
            assertEquals(GithubActionMode.Star, assertNotNull(content(store).pending).mode)
        }

    @Test
    fun target_account_or_capability_change_invalidates_pending_confirmation() =
        runTest {
            val access = MutableStateFlow<GithubAccess>(GithubAccess.Available(githubCapability))
            val repository = RecordingGithubRepository()
            val store = store(repository, backgroundScope, access)
            store.load(githubPreview.target.canonicalUrl)
            runCurrent()
            store.select(GithubActionMode.Star)
            val stale = assertNotNull(content(store).pending)

            access.value =
                GithubAccess.Available(
                    GithubServiceCapability(true, setOf(GithubActionMode.Metadata, GithubActionMode.Track)),
                )
            runCurrent()
            store.confirm(stale)
            runCurrent()

            assertTrue(repository.actions.isEmpty())
            assertEquals(
                GithubDetailState.Failed(GithubDetailFailure.InvalidResponse, canRetry = false),
                store.state.value,
            )
        }

    private fun store(
        repository: GithubRepository,
        scope: CoroutineScope,
        access: MutableStateFlow<GithubAccess> = MutableStateFlow(GithubAccess.Available(githubCapability)),
    ) = GithubDetailStore(repository, access, identityFactory, scope)

    private fun content(store: GithubDetailStore): GithubDetailState.Content = assertIs(store.state.value)

    private fun success() =
        GithubRepositoryResult.Success(
            GithubActionResult(
                GithubActionAggregate.Succeeded,
                GithubComponentOutcome(GithubComponentStatus.Succeeded),
                GithubComponentOutcome(GithubComponentStatus.Succeeded),
                GithubComponentOutcome(GithubComponentStatus.Accepted),
            ),
        )
}
