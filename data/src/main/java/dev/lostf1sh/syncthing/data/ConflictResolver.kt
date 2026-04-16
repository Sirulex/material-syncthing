package dev.lostf1sh.syncthing.data

import java.io.File

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
        if (original.exists() && !original.delete()) {
            return Result.Failure("Could not remove current: $originalRel")
        }
        return if (conflict.renameTo(original)) Result.Success
        else Result.Failure("Rename failed: $conflictRelPath → $originalRel")
    }

    /** Keep both versions — this is the starting state. No-op. */
    fun keepBoth(): Result = Result.Success
}
