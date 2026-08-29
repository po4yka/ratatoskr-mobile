package com.ratatoskr.mobile.library

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object LibraryListRoute : NavKey

@Serializable
data object FixtureLibraryRoute : NavKey

@Serializable
data class ArticleReaderRoute(
    val analysisId: String,
) : NavKey

@Serializable
enum class SocialReaderProvider {
    X,
    Instagram,
    Threads,
}

@Serializable
data class SocialReaderRoute(
    val provider: SocialReaderProvider,
    val sourceId: String,
) : NavKey

@Serializable
enum class AiArchiveReaderProvider {
    Chatgpt,
    Claude,
}

@Serializable
data class AiArchiveReaderRoute(
    val provider: AiArchiveReaderProvider,
    val itemId: String,
) : NavKey

sealed interface ContentRouteResult {
    data class Accepted(
        val route: NavKey,
    ) : ContentRouteResult

    data object Invalid : ContentRouteResult
}

fun ContentRouteResult.routeIdOrNull(): String? =
    when (this) {
        is ContentRouteResult.Accepted ->
            when (val destination = route) {
                is ArticleReaderRoute -> destination.analysisId
                is SocialReaderRoute -> destination.sourceId
                is AiArchiveReaderRoute -> destination.itemId
                else -> null
            }
        ContentRouteResult.Invalid -> null
    }

fun FixtureLibraryItem.readerRoute(): NavKey =
    when (family) {
        FixtureContentFamily.Article -> ArticleReaderRoute(id)
        FixtureContentFamily.Social ->
            SocialReaderRoute(
                provider =
                    when (provider) {
                        "x" -> SocialReaderProvider.X
                        "instagram" -> SocialReaderProvider.Instagram
                        "threads" -> SocialReaderProvider.Threads
                        else -> error("Unsupported contract fixture provider")
                    },
                sourceId = id,
            )
        FixtureContentFamily.AiArchive ->
            AiArchiveReaderRoute(
                provider =
                    when (provider) {
                        "chatgpt" -> AiArchiveReaderProvider.Chatgpt
                        "claude" -> AiArchiveReaderProvider.Claude
                        else -> error("Unsupported contract fixture provider")
                    },
                itemId = id,
            )
    }

object ContentRouteTable {
    fun parse(value: String): ContentRouteResult {
        ARTICLE.matchEntire(value)?.let { match ->
            return ContentRouteResult.Accepted(ArticleReaderRoute(match.groupValues[1]))
        }
        SOCIAL.matchEntire(value)?.let { match ->
            val provider =
                when (match.groupValues[1]) {
                    "x" -> SocialReaderProvider.X
                    "instagram" -> SocialReaderProvider.Instagram
                    "threads" -> SocialReaderProvider.Threads
                    else -> return ContentRouteResult.Invalid
                }
            return ContentRouteResult.Accepted(SocialReaderRoute(provider, match.groupValues[2]))
        }
        AI_ARCHIVE.matchEntire(value)?.let { match ->
            val provider =
                when (match.groupValues[1]) {
                    "chatgpt" -> AiArchiveReaderProvider.Chatgpt
                    "claude" -> AiArchiveReaderProvider.Claude
                    else -> return ContentRouteResult.Invalid
                }
            return ContentRouteResult.Accepted(AiArchiveReaderRoute(provider, match.groupValues[2]))
        }
        return ContentRouteResult.Invalid
    }

    private const val CANONICAL_UUID =
        "([0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})"
    private val ARTICLE = Regex("^ratatoskr://library/analyses/$CANONICAL_UUID$")
    private val SOCIAL = Regex("^ratatoskr://library/social/(x|instagram|threads)/$CANONICAL_UUID$")
    private val AI_ARCHIVE = Regex("^ratatoskr://library/ai-archives/(chatgpt|claude)/$CANONICAL_UUID$")
}
