package com.ratatoskr.mobile.github

import com.ratatoskr.mobile.identity.GithubServiceCapability
import kotlinx.coroutines.CompletableDeferred
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
class GithubActionOutcomeStoreTest {
    @Test
    fun partial_result_preserves_metadata_star_and_backup_failure() =
        runTest {
            val repository = RecordingGithubRepository(actionResults = mutableListOf(success(PARTIAL)))
            val store = loadedStore(repository)
            submitStar(store)
            runCurrent()

            val presentation = assertNotNull(content(store).result)
            assertEquals("Partial", presentation.aggregateLabel)
            assertTrue("Succeeded" in presentation.metadataLabel)
            assertTrue("Succeeded" in presentation.providerStarLabel)
            assertTrue("DependencyUnavailable" in presentation.desiredBackupLabel)
        }

    @Test
    fun accepted_policy_never_claims_backup_complete() =
        runTest {
            val repository = RecordingGithubRepository(actionResults = mutableListOf(success(ACCEPTED)))
            val store = loadedStore(repository)
            submitStar(store)
            runCurrent()

            val label = assertNotNull(content(store).result).desiredBackupLabel
            assertTrue("accepted for publication" in label)
            assertTrue("complete" !in label.lowercase())
        }

    @Test
    fun inconsistent_aggregate_is_invalid_response() =
        runTest {
            val repository = RecordingGithubRepository(actionResults = mutableListOf(GithubRepositoryResult.InvalidResponse))
            val store = loadedStore(repository)
            submitStar(store)
            runCurrent()

            assertEquals(
                GithubDetailState.Failed(GithubDetailFailure.InvalidResponse, canRetry = false),
                store.state.value,
            )
            assertEquals(null, GithubContractCodec.decodeActionResult(INCONSISTENT_JSON))
        }

    @Test
    fun outcome_unknown_retry_reuses_same_idempotency_key() =
        runTest {
            val repository =
                RecordingGithubRepository(
                    actionResults =
                        mutableListOf(
                            GithubRepositoryResult.Unavailable(retryable = true, outcomeUnknown = true),
                            success(PARTIAL),
                        ),
                )
            val store = loadedStore(repository)
            submitStar(store)
            runCurrent()
            assertTrue(content(store).outcomeUnknown)

            store.retryUncertain()
            runCurrent()

            assertEquals(2, repository.actions.size)
            assertEquals(repository.actions[0].idempotencyKey, repository.actions[1].idempotencyKey)
        }

    @Test
    fun revocation_transitions_to_repairing() =
        runTest {
            val repository = RecordingGithubRepository(actionResults = mutableListOf(GithubRepositoryResult.Unauthorized))
            val store = loadedStore(repository)
            submitStar(store)
            runCurrent()

            assertEquals(GithubDetailState.RePairingRequired, store.state.value)
        }

    @Test
    fun capability_change_prevents_uncertain_retry() =
        runTest {
            val access = MutableStateFlow<GithubAccess>(GithubAccess.Available(githubCapability))
            val repository =
                RecordingGithubRepository(
                    actionResults =
                        mutableListOf(
                            GithubRepositoryResult.Unavailable(retryable = true, outcomeUnknown = true),
                        ),
                )
            val store = GithubDetailStore(repository, access, identityFactory, backgroundScope)
            store.load(githubPreview.target.canonicalUrl)
            runCurrent()
            submitStar(store)
            runCurrent()

            access.value =
                GithubAccess.Available(
                    GithubServiceCapability(
                        true,
                        setOf(GithubActionMode.Metadata, GithubActionMode.Track),
                    ),
                )
            runCurrent()
            store.retryUncertain()
            runCurrent()

            assertEquals(1, repository.actions.size)
            assertEquals(
                GithubDetailState.Failed(GithubDetailFailure.InvalidResponse, canRetry = false),
                store.state.value,
            )
        }

    @Test
    fun revocation_during_inflight_action_does_not_restore_content() =
        runTest {
            val access = MutableStateFlow<GithubAccess>(GithubAccess.Available(githubCapability))
            val response = CompletableDeferred<GithubRepositoryResult<GithubActionResult>>()
            val repository =
                object : GithubRepository {
                    override suspend fun preview(canonicalUrl: String) = GithubRepositoryResult.Success(githubPreview)

                    override suspend fun action(request: GithubActionRequest) = response.await()
                }
            val store = GithubDetailStore(repository, access, identityFactory, backgroundScope)
            store.load(githubPreview.target.canonicalUrl)
            runCurrent()
            submitStar(store)
            runCurrent()

            access.value = GithubAccess.PairingRequired
            runCurrent()
            response.complete(success(PARTIAL))
            runCurrent()

            assertEquals(GithubDetailState.RePairingRequired, store.state.value)
        }

    @Test
    fun capability_expansion_during_inflight_action_does_not_leave_submission_stuck() =
        runTest {
            val originalActions = setOf(GithubActionMode.Metadata, GithubActionMode.Star)
            val access =
                MutableStateFlow<GithubAccess>(
                    GithubAccess.Available(
                        GithubServiceCapability(true, originalActions),
                    ),
                )
            val preview = githubPreview.copy(availableActions = originalActions)
            val response = CompletableDeferred<GithubRepositoryResult<GithubActionResult>>()
            val repository =
                object : GithubRepository {
                    override suspend fun preview(canonicalUrl: String) = GithubRepositoryResult.Success(preview)

                    override suspend fun action(request: GithubActionRequest) = response.await()
                }
            val store = GithubDetailStore(repository, access, identityFactory, backgroundScope)
            store.load(preview.target.canonicalUrl)
            runCurrent()
            submitStar(store)
            runCurrent()

            access.value = GithubAccess.Available(githubCapability)
            runCurrent()
            response.complete(success(PARTIAL))
            runCurrent()

            val final = content(store)
            assertTrue(!final.submitting)
            assertNotNull(final.result)
        }

    private suspend fun kotlinx.coroutines.test.TestScope.loadedStore(repository: RecordingGithubRepository): GithubDetailStore {
        val store =
            GithubDetailStore(
                repository,
                MutableStateFlow(GithubAccess.Available(githubCapability)),
                identityFactory,
                backgroundScope,
            )
        store.load(githubPreview.target.canonicalUrl)
        runCurrent()
        return store
    }

    private fun submitStar(store: GithubDetailStore) {
        store.select(GithubActionMode.Star)
        store.confirm(assertNotNull(content(store).pending))
    }

    private fun content(store: GithubDetailStore): GithubDetailState.Content = assertIs(store.state.value)

    private fun success(result: GithubActionResult) = GithubRepositoryResult.Success(result)

    private companion object {
        val PARTIAL =
            GithubActionResult(
                GithubActionAggregate.Partial,
                GithubComponentOutcome(GithubComponentStatus.Succeeded),
                GithubComponentOutcome(GithubComponentStatus.Succeeded),
                GithubComponentOutcome(GithubComponentStatus.Failed, GithubActionReason.DependencyUnavailable),
            )
        val ACCEPTED =
            GithubActionResult(
                GithubActionAggregate.Succeeded,
                GithubComponentOutcome(GithubComponentStatus.Succeeded),
                GithubComponentOutcome(GithubComponentStatus.Succeeded),
                GithubComponentOutcome(GithubComponentStatus.Accepted),
            )
        const val INCONSISTENT_JSON =
            """{"aggregate":"succeeded","metadata":{"status":"succeeded"},"provider_star":{"status":"succeeded"},"desired_backup":{"status":"failed","reason":"dependency_unavailable"}}"""
    }
}
