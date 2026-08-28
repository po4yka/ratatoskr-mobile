package com.ratatoskr.mobile.queue

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Update
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "capture_queue",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["ownerOrigin", "ownerAccountId", "source", "sourceSequence"], unique = true),
        Index(value = ["ownerOrigin", "ownerAccountId", "state", "nextEligibleEpochMillis"]),
    ],
)
data class QueueEntity(
    @PrimaryKey val localId: String,
    val idempotencyKey: String,
    val ownerOrigin: String,
    val ownerAccountId: String,
    val source: String,
    val sourceSequence: Long,
    val state: String,
    val nextEligibleEpochMillis: Long,
    val recordJson: String,
)

@Dao
interface QueueDao {
    @Query("SELECT * FROM capture_queue")
    suspend fun records(): List<QueueEntity>

    @Insert
    suspend fun insert(entity: QueueEntity)

    @Update
    suspend fun update(entity: QueueEntity)
}

@Database(entities = [QueueEntity::class], version = 1, exportSchema = false)
@ConstructedBy(QueueDatabaseConstructor::class)
abstract class QueueDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao
}

@Suppress("KotlinNoActualForExpect")
expect object QueueDatabaseConstructor : RoomDatabaseConstructor<QueueDatabase> {
    override fun initialize(): QueueDatabase
}

fun buildQueueDatabase(builder: RoomDatabase.Builder<QueueDatabase>): QueueDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()

class RoomQueuePersistence(
    private val database: QueueDatabase,
) : QueuePersistence {
    override suspend fun <T> transaction(block: suspend QueueTransaction.() -> T): T =
        database.withWriteTransaction {
            block(RoomQueueTransaction(database.queueDao()))
        }

    override fun close() = database.close()
}

private class RoomQueueTransaction(
    private val dao: QueueDao,
) : QueueTransaction {
    override suspend fun records(): List<QueueRecord> = dao.records().map { queueJson.decodeFromString(it.recordJson) }

    override suspend fun insert(record: QueueRecord) = dao.insert(record.toEntity())

    override suspend fun update(record: QueueRecord) = dao.update(record.toEntity())
}

private fun QueueRecord.toEntity() =
    QueueEntity(
        localId = localId,
        idempotencyKey = idempotencyKey,
        ownerOrigin = request.owner.origin,
        ownerAccountId = request.owner.accountId,
        source = request.source.name,
        sourceSequence = sourceSequence,
        state = state.name,
        nextEligibleEpochMillis = nextEligibleAt.toEpochMilliseconds(),
        recordJson = queueJson.encodeToString(this),
    )

private val queueJson =
    Json {
        classDiscriminator = "payload_kind"
        encodeDefaults = true
    }
