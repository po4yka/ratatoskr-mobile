package com.ratatoskr.mobile.library

import com.ratatoskr.mobile.api.generated.model.ReadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class FixtureContentFamily {
    Article,
    Social,
    AiArchive,
}

enum class FixtureBlockKind {
    Heading,
    Paragraph,
    Quote,
    CodeText,
}

data class FixtureContentBlock(
    val kind: FixtureBlockKind,
    val text: String,
)

data class FixtureProvenance(
    val source: String,
    val acquisition: String,
    val provider: String? = null,
    val completeness: String? = null,
)

data class FixtureLibraryItem(
    val id: String,
    val family: FixtureContentFamily,
    val provider: String?,
    val title: String,
    val summary: String?,
    val keyPoints: List<String>,
    val blocks: List<FixtureContentBlock>,
    val provenance: FixtureProvenance,
    val warnings: List<String>,
    val readState: ReadState,
    val favorite: Boolean,
    val note: String,
    val collectionIds: Set<String>,
    val tagIds: Set<String>,
)

data class FixtureCollection(
    val id: String,
    val name: String,
)

data class FixtureTag(
    val id: String,
    val name: String,
)

data class FixtureCatalog(
    val items: List<FixtureLibraryItem>,
    val collections: List<FixtureCollection>,
    val tags: List<FixtureTag>,
    val integrationPending: Boolean = true,
) {
    fun item(id: String): FixtureLibraryItem? = items.firstOrNull { it.id == id }

    fun collectionCount(id: String): Int = items.count { id in it.collectionIds }

    fun tagCount(id: String): Int = items.count { id in it.tagIds }
}

sealed interface FixtureMutationResult {
    data class Success(
        val snapshot: FixtureCatalog,
    ) : FixtureMutationResult

    data class Validation(
        val snapshot: FixtureCatalog,
    ) : FixtureMutationResult

    data class Unavailable(
        val snapshot: FixtureCatalog,
    ) : FixtureMutationResult
}

fun interface FixtureMutationGuard {
    fun refuse(): Boolean
}

interface FixtureUserContentRepository {
    val state: StateFlow<FixtureCatalog>

    suspend fun toggleFavorite(itemId: String): FixtureMutationResult

    suspend fun saveNote(
        itemId: String,
        note: String,
    ): FixtureMutationResult

    suspend fun setCollectionMembership(
        itemId: String,
        collectionId: String,
        included: Boolean,
    ): FixtureMutationResult

    suspend fun setTagMembership(
        itemId: String,
        tagId: String,
        included: Boolean,
    ): FixtureMutationResult
}

class ContractFixtureUserContentRepository(
    private val mutationGuard: FixtureMutationGuard = FixtureMutationGuard { false },
) : FixtureUserContentRepository {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(fixtureCatalog())
    override val state: StateFlow<FixtureCatalog> = mutableState.asStateFlow()

    override suspend fun toggleFavorite(itemId: String): FixtureMutationResult =
        mutate(itemId) { item -> item.copy(favorite = !item.favorite) }

    override suspend fun saveNote(
        itemId: String,
        note: String,
    ): FixtureMutationResult {
        if (note.scalarCount() > NOTE_LIMIT) return FixtureMutationResult.Validation(state.value)
        return mutate(itemId) { item -> item.copy(note = note) }
    }

    override suspend fun setCollectionMembership(
        itemId: String,
        collectionId: String,
        included: Boolean,
    ): FixtureMutationResult {
        if (state.value.collections.none { it.id == collectionId }) {
            return FixtureMutationResult.Validation(state.value)
        }
        return mutate(itemId) { item ->
            item.copy(
                collectionIds =
                    if (included) item.collectionIds + collectionId else item.collectionIds - collectionId,
            )
        }
    }

    override suspend fun setTagMembership(
        itemId: String,
        tagId: String,
        included: Boolean,
    ): FixtureMutationResult {
        if (state.value.tags.none { it.id == tagId }) return FixtureMutationResult.Validation(state.value)
        return mutate(itemId) { item ->
            item.copy(tagIds = if (included) item.tagIds + tagId else item.tagIds - tagId)
        }
    }

    private suspend fun mutate(
        itemId: String,
        transform: (FixtureLibraryItem) -> FixtureLibraryItem,
    ): FixtureMutationResult =
        mutex.withLock {
            val before = mutableState.value
            if (mutationGuard.refuse()) return@withLock FixtureMutationResult.Unavailable(before)
            var found = false
            val items =
                before.items.map { item ->
                    if (item.id == itemId) {
                        found = true
                        transform(item)
                    } else {
                        item
                    }
                }
            if (!found) return@withLock FixtureMutationResult.Validation(before)
            val after = before.copy(items = items)
            mutableState.value = after
            FixtureMutationResult.Success(after)
        }

    private companion object {
        const val NOTE_LIMIT = 2_000
    }
}

class LibraryContentRepository(
    private val live: LibraryRepository,
    val fixtures: FixtureUserContentRepository,
) : LibraryRepository by live {
    suspend fun toggleFixtureFavorite(itemId: String): FixtureMutationResult = fixtures.toggleFavorite(itemId)

    suspend fun saveFixtureNote(
        itemId: String,
        note: String,
    ): FixtureMutationResult = fixtures.saveNote(itemId, note)
}

object FixtureIds {
    const val ARTICLE = "00000000-0000-4000-8000-000000000101"
    const val X = "00000000-0000-4000-8000-000000000201"
    const val INSTAGRAM = "00000000-0000-4000-8000-000000000202"
    const val THREADS = "00000000-0000-4000-8000-000000000203"
    const val CHATGPT = "00000000-0000-4000-8000-000000000301"
    const val CLAUDE = "00000000-0000-4000-8000-000000000302"
}

private fun fixtureCatalog(): FixtureCatalog =
    FixtureCatalog(
        items =
            listOf(
                fixtureItem(
                    id = FixtureIds.ARTICLE,
                    family = FixtureContentFamily.Article,
                    title = "Evidence-aware article reading",
                    source = "https://example.test/articles/evidence",
                    acquisition = "explicit mobile capture",
                    summary = "Analysis qualifies extracted text with provenance and warnings.",
                    blocks =
                        listOf(
                            FixtureContentBlock(FixtureBlockKind.Heading, "A reader needs evidence"),
                            FixtureContentBlock(
                                FixtureBlockKind.Paragraph,
                                "<script>alert('inert')</script> remains plain text in Ratatoskr.",
                            ),
                        ),
                    warnings = listOf("Synthetic extraction omitted one decorative block."),
                    collections = setOf("reading"),
                    tags = setOf("contracts", "provenance"),
                ),
                fixtureItem(
                    id = FixtureIds.X,
                    family = FixtureContentFamily.Social,
                    provider = "x",
                    title = "Explicit X capture",
                    source = "x source fixture",
                    acquisition = "explicit_user_capture",
                    tags = setOf("social"),
                ),
                fixtureItem(
                    id = FixtureIds.INSTAGRAM,
                    family = FixtureContentFamily.Social,
                    provider = "instagram",
                    title = "Explicit Instagram capture",
                    source = "instagram source fixture",
                    acquisition = "explicit_user_capture; not native Saved authority",
                    tags = setOf("social"),
                ),
                fixtureItem(
                    id = FixtureIds.THREADS,
                    family = FixtureContentFamily.Social,
                    provider = "threads",
                    title = "Explicit Threads capture",
                    source = "threads source fixture",
                    acquisition = "explicit_user_capture; not native Saved authority",
                    tags = setOf("social"),
                ),
                fixtureItem(
                    id = FixtureIds.CHATGPT,
                    family = FixtureContentFamily.AiArchive,
                    provider = "chatgpt",
                    title = "ChatGPT archive conversation",
                    source = "synthetic ChatGPT export",
                    acquisition = "user-approved archive import",
                    completeness = "complete fixture conversation",
                    tags = setOf("ai-archive"),
                ),
                fixtureItem(
                    id = FixtureIds.CLAUDE,
                    family = FixtureContentFamily.AiArchive,
                    provider = "claude",
                    title = "Claude archive artifact",
                    source = "synthetic Claude export",
                    acquisition = "user-approved archive import",
                    completeness = "partial fixture artifact",
                    warnings = listOf("Fixture marks one attachment unavailable."),
                    tags = setOf("ai-archive"),
                ),
            ),
        collections =
            listOf(
                FixtureCollection("reading", "Reading"),
                FixtureCollection("research", "Research"),
            ),
        tags =
            listOf(
                FixtureTag("contracts", "Contracts"),
                FixtureTag("provenance", "Provenance"),
                FixtureTag("social", "Social"),
                FixtureTag("ai-archive", "AI archive"),
            ),
    )

private fun fixtureItem(
    id: String,
    family: FixtureContentFamily,
    title: String,
    source: String,
    acquisition: String,
    provider: String? = null,
    completeness: String? = null,
    summary: String? = "Contract-fixed preview content.",
    blocks: List<FixtureContentBlock> =
        listOf(FixtureContentBlock(FixtureBlockKind.Paragraph, "Synthetic ordered content block.")),
    warnings: List<String> = emptyList(),
    collections: Set<String> = emptySet(),
    tags: Set<String> = emptySet(),
) = FixtureLibraryItem(
    id = id,
    family = family,
    provider = provider,
    title = title,
    summary = summary,
    keyPoints = listOf("Supplied fixture facts remain explicit."),
    blocks = blocks,
    provenance = FixtureProvenance(source, acquisition, provider, completeness),
    warnings = warnings,
    readState = ReadState.UNREAD,
    favorite = false,
    note = "",
    collectionIds = collections,
    tagIds = tags,
)
