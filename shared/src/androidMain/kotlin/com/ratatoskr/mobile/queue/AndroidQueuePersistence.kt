package com.ratatoskr.mobile.queue

import android.content.Context
import androidx.room3.Room

fun createAndroidQueuePersistence(
    context: Context,
    name: String = "ratatoskr-capture-queue.db",
): QueuePersistence {
    val appContext = context.applicationContext
    val path = appContext.getDatabasePath(name).absolutePath
    return RoomQueuePersistence(
        buildQueueDatabase(
            Room.databaseBuilder<QueueDatabase>(
                context = appContext,
                name = path,
            ),
        ),
    )
}

fun deleteAndroidQueueStore(
    context: Context,
    name: String = "ratatoskr-capture-queue.db",
): Boolean {
    val appContext = context.applicationContext
    val database = appContext.getDatabasePath(name)
    val files = listOf(database, java.io.File("${database.path}-wal"), java.io.File("${database.path}-shm"))
    if (files.any { it.exists() }) appContext.deleteDatabase(name)
    return files.none { it.exists() }
}
