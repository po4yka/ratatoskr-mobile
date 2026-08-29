package com.ratatoskr.mobile.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class LibraryApplicationGraph(
    liveRepository: LibraryRepository,
    access: StateFlow<LibraryAccess>,
    scope: CoroutineScope,
) {
    val fixtures: FixtureUserContentRepository = ContractFixtureUserContentRepository()
    val content = LibraryContentRepository(liveRepository, fixtures)
    val listStore = LibraryListStore(content, access, scope)
    val readerStore = LibraryReaderStore(fixtures, scope)
}
