package com.ratatoskr.mobile.github

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GithubContractCodecTest {
    @Test
    fun valid_preview_and_partial_action_fixtures_decode_exactly() {
        val preview = requireNotNull(GithubContractCodec.decodePreview(VALID_PREVIEW))
        val action = requireNotNull(GithubContractCodec.decodeActionResult(PARTIAL_ACTION))

        assertEquals(42, preview.target.numericId)
        assertEquals("owner/repository", preview.target.fullName)
        assertEquals(setOf(GithubActionMode.Metadata, GithubActionMode.Track, GithubActionMode.Star), preview.availableActions)
        assertEquals(GithubActionAggregate.Partial, action.aggregate)
        assertEquals(GithubComponentStatus.Succeeded, action.metadata.status)
        assertEquals(GithubComponentStatus.Succeeded, action.providerStar.status)
        assertEquals(GithubActionReason.DependencyUnavailable, action.desiredBackup.reason)
    }

    @Test
    fun unknown_or_unsafe_preview_members_are_rejected() {
        val unsafe = VALID_PREVIEW.replace("A small repository description.", "unsafe\\ntext")

        assertNull(GithubContractCodec.decodePreview(UNKNOWN_PREVIEW))
        assertNull(GithubContractCodec.decodePreview(unsafe))
        assertNull(GithubContractCodec.decodePreview(ZERO_REPOSITORY_ID))
    }

    @Test
    fun invalid_action_reason_or_aggregate_is_rejected() {
        assertNull(GithubContractCodec.decodeActionResult(INCONSISTENT_ACTION))
        assertNull(GithubContractCodec.decodeActionResult(METADATA_ACCEPTED_ACTION))
        assertNull(
            GithubContractCodec.decodeActionResult(
                PARTIAL_ACTION.replace("dependency_unavailable", "provider_secret_leaked"),
            ),
        )
    }

    @Test
    fun accepted_backup_is_not_projected_as_completed() {
        val result = requireNotNull(GithubContractCodec.decodeActionResult(ACCEPTED_BACKUP_ACTION))
        val presentation = result.present()

        assertEquals(GithubComponentStatus.Accepted, result.desiredBackup.status)
        assertTrue(presentation.desiredBackupLabel.contains("accepted", ignoreCase = true))
        assertTrue(presentation.desiredBackupLabel.contains("publication", ignoreCase = true))
        assertFalse(presentation.desiredBackupLabel.contains("complete", ignoreCase = true))
    }

    private companion object {
        const val VALID_PREVIEW =
            """{
              "target":{"github_repository_numeric_id":42,"repository_full_name":"owner/repository","canonical_url":"https://github.com/owner/repository"},
              "description":"A small repository description.","stargazer_count":123,"primary_language":"Rust",
              "account_ref":"github-account:018f0000-0000-7000-8000-000000000604",
              "available_actions":["metadata","track","star"]
            }"""
        const val ZERO_REPOSITORY_ID =
            """{"target":{"github_repository_numeric_id":0,"repository_full_name":"owner/repository","canonical_url":"https://github.com/owner/repository"},"stargazer_count":0,"available_actions":["metadata"]}"""
        const val UNKNOWN_PREVIEW =
            """{"target":{"github_repository_numeric_id":42,"repository_full_name":"owner/repository","canonical_url":"https://github.com/owner/repository"},"stargazer_count":0,"available_actions":["metadata"],"unknown":true}"""
        const val PARTIAL_ACTION =
            """{"aggregate":"partial","metadata":{"status":"succeeded"},"provider_star":{"status":"succeeded"},"desired_backup":{"status":"failed","reason":"dependency_unavailable"}}"""
        const val INCONSISTENT_ACTION =
            """{"aggregate":"succeeded","metadata":{"status":"succeeded"},"provider_star":{"status":"succeeded"},"desired_backup":{"status":"failed","reason":"dependency_unavailable"}}"""
        const val METADATA_ACCEPTED_ACTION =
            """{"aggregate":"succeeded","metadata":{"status":"accepted"},"provider_star":{"status":"skipped","reason":"not_applicable"},"desired_backup":{"status":"accepted"}}"""
        const val ACCEPTED_BACKUP_ACTION =
            """{"aggregate":"succeeded","metadata":{"status":"succeeded"},"provider_star":{"status":"skipped","reason":"not_applicable"},"desired_backup":{"status":"accepted"}}"""
    }
}
