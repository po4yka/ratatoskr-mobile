package com.ratatoskr.mobile.storage

import android.content.Context
import com.ratatoskr.mobile.identity.SecureCredentialStorage
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID

interface AndroidEraseBoundary {
    fun cancelWorkAndNotifications()

    fun residueCount(): Int
}

data class AndroidEraseInventory(
    val credentials: Boolean,
    val databaseFiles: Int,
    val stagedFiles: Int,
    val preferenceEntries: Int,
    val cacheFiles: Int,
    val scheduledOrNotified: Int,
) {
    val empty: Boolean
        get() = !credentials && databaseFiles + stagedFiles + preferenceEntries + cacheFiles + scheduledOrNotified == 0
}

class AndroidLocalDataErasure(
    private val context: Context,
    private val credentials: SecureCredentialStorage,
    private val closeQueue: () -> Unit,
    private val databaseNames: List<String>,
    private val stagedRoots: List<File>,
    private val preferenceNames: List<String>,
    private val cacheRoots: List<File>,
    private val boundary: AndroidEraseBoundary,
    private val generationStore: AndroidEraseGenerationStore = AndroidEraseGenerationStore(context),
) {
    private val marker = File(context.filesDir, "ratatoskr-erasure.marker")

    fun begin(reason: String): Boolean {
        require(reason.matches(Regex("[a-z_]{1,64}")))
        val generation = UUID.randomUUID().toString()
        if (!writeMarker("$generation:$reason")) return false
        if (!generationStore.replace(generation)) return false
        return eraseMarkedData()
    }

    fun resumeIfNeeded(): Boolean {
        if (!marker.exists()) return true
        val generation = runCatching { marker.readText().substringBefore(':') }.getOrNull() ?: return false
        if (!generation.matches(GENERATION)) return false
        if (eraseGeneration() != generation && !generationStore.replace(generation)) return false
        return eraseMarkedData()
    }

    fun inventory(): AndroidEraseInventory =
        AndroidEraseInventory(
            credentials = runCatching { credentials.load() != null }.getOrDefault(true),
            databaseFiles = databaseNames.sumOf(::databaseResidue),
            stagedFiles = stagedRoots.sumOf(::ownedFileCount),
            preferenceEntries = preferenceNames.sumOf { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.size },
            cacheFiles = cacheRoots.sumOf(::ownedFileCount),
            scheduledOrNotified = boundary.residueCount(),
        )

    fun markerExists(): Boolean = marker.exists()

    private fun eraseGeneration(): String = generationStore.current()

    private fun writeMarker(value: String): Boolean {
        val temporary = File(marker.parentFile, "${marker.name}.partial")
        return runCatching {
            marker.parentFile?.mkdirs()
            FileOutputStream(temporary).use { output ->
                output.write(value.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            check(temporary.renameTo(marker))
            true
        }.getOrElse {
            temporary.delete()
            false
        }
    }

    private fun eraseMarkedData(): Boolean {
        if (!marker.exists()) return true
        runCatching { boundary.cancelWorkAndNotifications() }
        runCatching { credentials.clear() }
        runCatching { closeQueue() }
        databaseNames.forEach(::deleteDatabase)
        stagedRoots.forEach(::deleteOwnedTree)
        preferenceNames.forEach { name ->
            runCatching {
                context
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
                context.deleteSharedPreferences(name)
            }
        }
        cacheRoots.forEach(::deleteOwnedTree)
        val empty = inventory().empty
        return empty && marker.delete()
    }

    private fun deleteDatabase(name: String) {
        val database = context.getDatabasePath(name)
        runCatching { context.deleteDatabase(name) }
        listOf(database, File("${database.path}-wal"), File("${database.path}-shm"))
            .forEach { runCatching { it.delete() } }
    }

    private fun deleteOwnedTree(root: File) {
        if (!root.exists()) return
        if (Files.isSymbolicLink(root.toPath())) {
            root.delete()
            return
        }
        root.listFiles()?.forEach { child ->
            if (Files.isSymbolicLink(child.toPath()) || child.isFile) {
                child.delete()
            } else {
                deleteOwnedTree(child)
            }
        }
        root.delete()
    }

    private fun databaseResidue(name: String): Int {
        val database = context.getDatabasePath(name)
        return listOf(database, File("${database.path}-wal"), File("${database.path}-shm")).count(File::exists)
    }

    private fun ownedFileCount(root: File): Int =
        if (!root.exists()) {
            0
        } else if (Files.isSymbolicLink(root.toPath())) {
            1
        } else {
            root.listFiles()?.sumOf { child ->
                if (Files.isSymbolicLink(child.toPath()) || child.isFile) 1 else 1 + ownedFileCount(child)
            } ?: 0
        }
}

class AndroidEraseGenerationStore(
    context: Context,
) {
    private val file = File(context.filesDir, "ratatoskr-erasure.generation")

    fun current(): String =
        runCatching { file.takeIf(File::isFile)?.readText()?.takeIf { it.matches(GENERATION) } }
            .getOrNull()
            ?: INITIAL_GENERATION

    fun replace(value: String): Boolean {
        require(value.matches(GENERATION))
        val temporary = File(file.parentFile, "${file.name}.partial")
        return runCatching {
            FileOutputStream(temporary).use { output ->
                output.write(value.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            check(temporary.renameTo(file))
            true
        }.getOrElse {
            temporary.delete()
            false
        }
    }

    companion object {
        const val INITIAL_GENERATION = "00000000-0000-0000-0000-000000000000"
        val GENERATION =
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

private val GENERATION = AndroidEraseGenerationStore.GENERATION
