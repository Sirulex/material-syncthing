package dev.sirulex.syncthing.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Resolve a Syncthing conflict via direct filesystem ops. Syncthing has no REST
 * endpoint for this — `/rest/db/override` is for send-only folders, not
 * conflicts. After resolving, callers should trigger a folder rescan so
 * Syncthing picks up the change.
 *
 * All paths are relative to the folder root.
 */
object ConflictResolver {

    sealed interface Result {
        data object Success : Result
        data class Failure(val reason: String) : Result
    }

    /** Delete the conflict copy (its suffix version), keep whatever is at the
     *  original path. */
    fun keepCurrent(folderPath: String, conflictRelPath: String): Result {
        val target = resolveConflictFile(folderPath, conflictRelPath)
            ?: return Result.Failure("Invalid conflict path: $conflictRelPath")
        if (!target.exists()) return Result.Failure("Conflict file missing: $conflictRelPath")
        return if (target.delete()) Result.Success
        else Result.Failure("Delete failed: $conflictRelPath")
    }

    /** Replace the current file with the conflict copy: delete current,
     *  rename conflict → original path. */
    fun keepConflict(folderPath: String, conflictRelPath: String): Result {
        val conflict = resolveConflictFile(folderPath, conflictRelPath)
            ?: return Result.Failure("Invalid conflict path: $conflictRelPath")
        if (!conflict.exists()) return Result.Failure("Conflict file missing: $conflictRelPath")
        val originalRel = ConflictDetector.stripConflictSuffix(conflictRelPath)
        if (originalRel == conflictRelPath) {
            return Result.Failure("Not a Syncthing conflict file: $conflictRelPath")
        }
        val original = resolveWithinRoot(folderPath, originalRel)
            ?: return Result.Failure("Invalid original path: $originalRel")
        return try {
            // Prefer atomic move so a crash mid-operation never leaves the
            // destination in a half-written state.  ATOMIC_MOVE throws
            // AtomicMoveNotSupportedException when source and destination are on
            // different filesystems (e.g. internal storage → external SD card),
            // so fall back to a plain move (which the JVM implements as a
            // copy + delete) in that case.  REPLACE_EXISTING handles the common
            // situation where the original file still exists; it replaces it
            // atomically on same-fs moves and via copy-then-delete on cross-fs.
            move(conflict, original, replaceExisting = true)
            Result.Success
        } catch (e: Exception) {
            Result.Failure("Move failed: $conflictRelPath → $originalRel (${e.message})")
        }
    }

    /** Keep both versions by renaming the conflict copy to a regular filename.
     * Leaving the `.sync-conflict-*` marker in place would make the same item
     * reappear forever, so a no-op is not a completed resolution. */
    fun keepBoth(folderPath: String, conflictRelPath: String): Result {
        val conflict = resolveConflictFile(folderPath, conflictRelPath)
            ?: return Result.Failure("Invalid conflict path: $conflictRelPath")
        if (!conflict.exists()) return Result.Failure("Conflict file missing: $conflictRelPath")
        val originalRel = ConflictDetector.stripConflictSuffix(conflictRelPath)
        if (originalRel == conflictRelPath) {
            return Result.Failure("Not a Syncthing conflict file: $conflictRelPath")
        }
        val original = resolveWithinRoot(folderPath, originalRel)
            ?: return Result.Failure("Invalid original path: $originalRel")
        val shortId = ConflictDetector.shortIdOf(conflictRelPath) ?: "remote"
        val extensionIndex = original.name.lastIndexOf('.').takeIf { it > 0 }
        val stem = extensionIndex?.let { original.name.substring(0, it) } ?: original.name
        val extension = extensionIndex?.let { original.name.substring(it) }.orEmpty()
        val parent = original.parentFile
            ?: return Result.Failure("Original file has no parent directory")

        val destination = generateSequence(1) { it + 1 }
            .map { index ->
                val counter = if (index == 1) "" else " $index"
                File(parent, "$stem (conflict $shortId$counter)$extension")
            }
            .first { !it.exists() }
        return try {
            move(conflict, destination, replaceExisting = false)
            Result.Success
        } catch (e: Exception) {
            Result.Failure("Could not keep both versions: ${e.message}")
        }
    }

    private fun resolveConflictFile(folderPath: String, conflictRelPath: String): File? {
        if (ConflictDetector.shortIdOf(conflictRelPath) == null) return null
        return resolveWithinRoot(folderPath, conflictRelPath)
    }

    private fun resolveWithinRoot(folderPath: String, relativePath: String): File? {
        if (relativePath.isBlank() || File(relativePath).isAbsolute) return null
        return runCatching {
            val root = File(folderPath).canonicalFile
            if (!root.isDirectory) return null
            val candidate = File(root, relativePath).canonicalFile
            if (!candidate.toPath().startsWith(root.toPath())) null else candidate
        }.getOrNull()
    }

    private fun move(source: File, destination: File, replaceExisting: Boolean) {
        val baseOptions: Array<java.nio.file.CopyOption> = if (replaceExisting) {
            arrayOf(StandardCopyOption.REPLACE_EXISTING)
        } else {
            emptyArray()
        }
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                *baseOptions,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), *baseOptions)
        } catch (_: UnsupportedOperationException) {
            Files.move(source.toPath(), destination.toPath(), *baseOptions)
        }
    }
}
