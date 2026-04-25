package dev.lostf1sh.syncthing.data

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
        val target = File(folderPath, conflictRelPath)
        if (!target.exists()) return Result.Failure("Conflict file missing: $conflictRelPath")
        return if (target.delete()) Result.Success
        else Result.Failure("Delete failed: $conflictRelPath")
    }

    /** Replace the current file with the conflict copy: delete current,
     *  rename conflict → original path. */
    fun keepConflict(folderPath: String, conflictRelPath: String): Result {
        val conflict = File(folderPath, conflictRelPath)
        if (!conflict.exists()) return Result.Failure("Conflict file missing: $conflictRelPath")
        val originalRel = ConflictDetector.stripConflictSuffix(conflictRelPath)
        val original = File(folderPath, originalRel)
        return try {
            // Prefer atomic move so a crash mid-operation never leaves the
            // destination in a half-written state.  ATOMIC_MOVE throws
            // AtomicMoveNotSupportedException when source and destination are on
            // different filesystems (e.g. internal storage → external SD card),
            // so fall back to a plain move (which the JVM implements as a
            // copy + delete) in that case.  REPLACE_EXISTING handles the common
            // situation where the original file still exists; it replaces it
            // atomically on same-fs moves and via copy-then-delete on cross-fs.
            try {
                Files.move(
                    conflict.toPath(),
                    original.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    conflict.toPath(),
                    original.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            Result.Success
        } catch (e: Exception) {
            Result.Failure("Move failed: $conflictRelPath → $originalRel (${e.message})")
        }
    }

    /** Keep both versions — this is the starting state. No-op. */
    fun keepBoth(): Result = Result.Success
}
