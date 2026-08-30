package com.ratatoskr.mobile.library

import kotlin.test.Test
import kotlin.test.assertEquals

class ContentRouteTableTest {
    @Test
    fun canonical_https_analysis_collection_and_repository_routes_resolve() {
        val configuration = ContentLinkConfiguration(setOf("links.ratatoskr.test"))
        val cases =
            mapOf(
                "https://links.ratatoskr.test/analyses/$ID" to ArticleReaderRoute(ID),
                "https://links.ratatoskr.test/collections/research" to CollectionRoute("research"),
                "https://links.ratatoskr.test/repos/ratatoskr/mobile" to RepositoryRoute("ratatoskr", "mobile"),
            )

        cases.forEach { (input, expected) ->
            assertEquals(ContentRouteResult.Accepted(expected), ContentRouteTable.parse(input, configuration), input)
        }
    }

    @Test
    fun custom_scheme_routes_remain_compatible() {
        assertEquals(
            ContentRouteResult.Accepted(ArticleReaderRoute(ID)),
            ContentRouteTable.parse("ratatoskr://library/analyses/$ID", ContentLinkConfiguration(setOf("links.ratatoskr.test"))),
        )
    }

    @Test
    fun scheme_host_port_credentials_encoding_traversal_query_fragment_identifier_and_extra_segment_variants_are_rejected() {
        val configuration = ContentLinkConfiguration(setOf("links.ratatoskr.test"))
        val invalid =
            listOf(
                "HTTPS://links.ratatoskr.test/analyses/$ID",
                "https://LINKS.ratatoskr.test/analyses/$ID",
                "https://user@links.ratatoskr.test/analyses/$ID",
                "https://links.ratatoskr.test:443/analyses/$ID",
                "https://links.ratatoskr.test/analyses/%61bcdef01-0000-4000-8000-000000000001",
                "https://links.ratatoskr.test/analyses/../$ID",
                "https://links.ratatoskr.test/analyses/$ID?query=private",
                "https://links.ratatoskr.test/analyses/$ID#private",
                "https://links.ratatoskr.test/analyses/${ID.uppercase()}",
                "https://links.ratatoskr.test/collections/Research",
                "https://links.ratatoskr.test/repos/-owner/mobile",
                "https://links.ratatoskr.test/repos/ratatoskr/mobile/extra",
                "https://foreign.example/analyses/$ID",
            )

        invalid.forEach { assertEquals(ContentRouteResult.Invalid, ContentRouteTable.parse(it, configuration), it) }
    }

    @Test
    fun article_social_and_ai_archive_matrix_maps_to_distinct_routes() {
        val cases =
            mapOf(
                "ratatoskr://library/analyses/$ID" to ArticleReaderRoute(ID),
                "ratatoskr://library/social/x/$ID" to SocialReaderRoute(SocialReaderProvider.X, ID),
                "ratatoskr://library/social/instagram/$ID" to SocialReaderRoute(SocialReaderProvider.Instagram, ID),
                "ratatoskr://library/social/threads/$ID" to SocialReaderRoute(SocialReaderProvider.Threads, ID),
                "ratatoskr://library/ai-archives/chatgpt/$ID" to
                    AiArchiveReaderRoute(AiArchiveReaderProvider.Chatgpt, ID),
                "ratatoskr://library/ai-archives/claude/$ID" to
                    AiArchiveReaderRoute(AiArchiveReaderProvider.Claude, ID),
            )

        cases.forEach { (input, expected) ->
            assertEquals(ContentRouteResult.Accepted(expected), ContentRouteTable.parse(input), input)
        }
    }

    @Test
    fun unknown_providers_and_families_are_rejected() {
        val invalid =
            listOf(
                "ratatoskr://library/repositories/$ID",
                "ratatoskr://library/social/facebook/$ID",
                "ratatoskr://library/ai-archives/gemini/$ID",
            )

        invalid.forEach { assertEquals(ContentRouteResult.Invalid, ContentRouteTable.parse(it), it) }
    }

    @Test
    fun noncanonical_ids_and_encoded_ambiguity_are_rejected() {
        val invalid =
            listOf(
                "ratatoskr://library/analyses/${ID.uppercase()}",
                "ratatoskr://library/analyses/not-a-uuid",
                "ratatoskr://library/analyses/00000000-0000-0000-0000-000000000001",
                "ratatoskr://library/analyses/%30%30%30%30%30%30%30%30-0000-4000-8000-000000000001",
                "ratatoskr://library/analyses/..%2f$ID",
            )

        invalid.forEach { assertEquals(ContentRouteResult.Invalid, ContentRouteTable.parse(it), it) }
    }

    @Test
    fun query_fragment_credentials_and_extra_segments_are_rejected() {
        val invalid =
            listOf(
                "https://library/analyses/$ID",
                "ratatoskr://user@library/analyses/$ID",
                "ratatoskr://library/analyses/$ID?token=no",
                "ratatoskr://library/analyses/$ID#fragment",
                "ratatoskr://library/analyses/$ID/extra",
                "ratatoskr://library//analyses/$ID",
            )

        invalid.forEach { assertEquals(ContentRouteResult.Invalid, ContentRouteTable.parse(it), it) }
    }

    private companion object {
        const val ID = "abcdef01-0000-4000-8000-000000000001"
    }
}
