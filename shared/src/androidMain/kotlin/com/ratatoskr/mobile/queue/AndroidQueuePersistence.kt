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
