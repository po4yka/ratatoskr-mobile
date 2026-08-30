package com.ratatoskr.mobile.queue

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Update
import androidx.room3.Upsert
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.transfer.UploadCheckpoint
import com.ratatoskr.mobile.transfer.generated.TransferBlobRef
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

@Entity(
    tableName = "staged_artifacts",
    indices = [Index(value = ["captureLocalId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = QueueEntity::class,
            parentColumns = ["localId"],
            childColumns = ["captureLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StagedArtifactEntity(
    @PrimaryKey val artifactId: String,
    val captureLocalId: String,
    val mediaType: String,
    val sizeBytes: Long,
    val sha256Hex: String?,
)

@Entity(
    tableName = "upload_transfers",
    indices = [Index(value = ["resumptionToken"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = QueueEntity::class,
            parentColumns = ["localId"],
            childColumns = ["captureLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class UploadTransferEntity(
    @PrimaryKey val captureLocalId: String,
    val resumptionToken: String,
    val transferJson: String,
)

@kotlinx.serialization.Serializable
private data class PersistedUploadTransfer(
    val checkpoint: UploadCheckpoint,
    val receipt: TransferBlobRef?,
    val stagedArtifactReclaimable: Boolean,
)

@Dao
interface QueueDao {
    @Query("SELECT * FROM capture_queue")
    suspend fun records(): List<QueueEntity>

    @Insert
    suspend fun insert(entity: QueueEntity)

    @Update
    suspend fun update(entity: QueueEntity)

    @Query("SELECT * FROM upload_transfers")
    suspend fun transfers(): List<UploadTransferEntity>

    @Upsert
    suspend fun upsertArtifact(entity: StagedArtifactEntity)

    @Upsert
    suspend fun upsertTransfer(entity: UploadTransferEntity)
}

@Database(
    entities = [QueueEntity::class, StagedArtifactEntity::class, UploadTransferEntity::class],
    version = 1,
    exportSchema = false,
)
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
    override suspend fun records(): List<QueueRecord> {
        val transfers =
            dao.transfers().associate { entity ->
                entity.captureLocalId to queueJson.decodeFromString<PersistedUploadTransfer>(entity.transferJson)
            }
        return dao.records().map { entity ->
            val record = queueJson.decodeFromString<QueueRecord>(entity.recordJson)
            val transfer = transfers[record.localId]
            if (transfer == null) {
                record
            } else {
                record.copy(
                    uploadCheckpoint = transfer.checkpoint,
                    uploadReceipt = transfer.receipt,
                    stagedArtifactReclaimable = transfer.stagedArtifactReclaimable,
                )
            }
        }
    }

    override suspend fun insert(record: QueueRecord) {
        dao.insert(record.toEntity())
        syncTransfer(record)
    }

    override suspend fun update(record: QueueRecord) {
        dao.update(record.toEntity())
        syncTransfer(record)
    }

    private suspend fun syncTransfer(record: QueueRecord) {
        val file = record.request.payload as? CapturePayload.FileReference ?: return
        dao.upsertArtifact(
            StagedArtifactEntity(
                artifactId = file.stagedFileId,
                captureLocalId = record.localId,
                mediaType = file.mediaType,
                sizeBytes = file.byteSize,
                sha256Hex =
                    record.uploadCheckpoint
                        ?.declaration
                        ?.digest
                        ?.hex,
            ),
        )
        val checkpoint = record.uploadCheckpoint ?: return
        dao.upsertTransfer(
            UploadTransferEntity(
                captureLocalId = record.localId,
                resumptionToken = checkpoint.resumptionToken,
                transferJson =
                    queueJson.encodeToString(
                        PersistedUploadTransfer(
                            checkpoint = checkpoint,
                            receipt = record.uploadReceipt,
                            stagedArtifactReclaimable = record.stagedArtifactReclaimable,
                        ),
                    ),
            ),
        )
    }
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
