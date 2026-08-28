package com.ratatoskr.mobile.queue

import androidx.room3.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey

@OptIn(ExperimentalForeignApi::class)
fun createIosQueuePersistence(path: String): QueuePersistence =
    createIosQueuePersistence(path, IosQueueFileProtector(::protectIosQueueFile))

internal fun createIosQueuePersistence(
    path: String,
    fileProtector: IosQueueFileProtector,
): QueuePersistence =
    IosProtectedQueuePersistence(
        delegate =
            RoomQueuePersistence(
                buildQueueDatabase(
                    Room.databaseBuilder<QueueDatabase>(name = path),
                ),
            ),
        path = path,
        fileProtector = fileProtector,
    )

internal fun interface IosQueueFileProtector {
    fun protect(path: String)
}

@OptIn(ExperimentalForeignApi::class)
private class IosProtectedQueuePersistence(
    private val delegate: QueuePersistence,
    private val path: String,
    private val fileProtector: IosQueueFileProtector,
) : QueuePersistence {
    override suspend fun <T> transaction(block: suspend QueueTransaction.() -> T): T =
        try {
            delegate.transaction(block)
        } finally {
            protectDatabaseFiles()
        }

    override fun close() = delegate.close()

    private fun protectDatabaseFiles() {
        listOf(path, "$path-wal", "$path-shm").forEach { file ->
            if (NSFileManager.defaultManager.fileExistsAtPath(file)) {
                fileProtector.protect(file)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun protectIosQueueFile(path: String) {
    val attributes =
        mapOf<Any?, Any?>(
            NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication,
        )
    check(NSFileManager.defaultManager.setAttributes(attributes, path, null)) {
        "Unable to apply iOS data protection to capture queue"
    }
}
