package dev.lostf1sh.syncthing.data

import dev.lostf1sh.syncthing.data.model.ConflictItem
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Detects Syncthing conflict-copy files in a folder's on-disk tree.
 *
 * Filename pattern: `<base>.sync-conflict-YYYYMMDD-HHMMSS-SHORTID.<ext>`
 * where SHORTID is the first 7 chars of the losing device's ID.
 *
 * Walks the filesystem directly (faster + cheaper than `/rest/db/browse` with
 * unlimited levels, per Syncthing docs). Requires read access to the folder
 * path — guaranteed on API 30+ via MANAGE_EXTERNAL_STORAGE; on API 28-29 it
 * works when the folder lives in app-private or legacy-accessible storage.
 */
object ConflictDetector {

    private val CONFLICT_REGEX =
        Regex("""\.sync-conflict-\d{8}-\d{6}-([A-Z0-9]{7})(?:\.|$)""")

    private val ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneOffset.UTC)

    /**
     * Maximum directory depth explored during a conflict scan.
     * Syncthing itself imposes no nesting limit, but an unbounded walkTopDown
     * on a deeply nested tree (e.g. a developer project with node_modules) can
     * block the IO dispatcher for seconds. 50 levels is far beyond any
     * realistic user data layout while still covering all practical cases.
     */
    private const val MAX_SCAN_DEPTH = 50

    /** Scan a single folder. Returns an empty list on any I/O failure. */
    fun scan(folderId: String, folderPath: String): List<ConflictItem> {
        val root = try {
            File(folderPath)
        } catch (_: Exception) {
            return emptyList()
        }
        if (!root.exists() || !root.isDirectory) return emptyList()

        val conflicts = mutableListOf<ConflictItem>()
        try {
            root.walkTopDown()
                .maxDepth(MAX_SCAN_DEPTH)
                .filter { it.isFile && CONFLICT_REGEX.containsMatchIn(it.name) }
                .forEach { file ->
                    val relPath = file.relativeTo(root).path
                        .replace(File.separatorChar, '/')
                    val originalRel = stripConflictSuffix(relPath)
                    val original = File(root, originalRel)
                    conflicts += ConflictItem(
                        folderId = folderId,
                        path = relPath,
                        modifiedLocal = if (original.exists()) formatTime(original.lastModified()) else null,
                        modifiedRemote = formatTime(file.lastModified()),
                        sizeLocal = if (original.exists()) original.length() else 0,
                        sizeRemote = file.length(),
                    )
                }
        } catch (_: Exception) {
        }

        return conflicts.distinctBy { "${it.folderId}\u0000${it.path}" }
    }

    /** Return the base path with the `sync-conflict-*` marker removed. */
    fun stripConflictSuffix(path: String): String {
        val match = CONFLICT_REGEX.find(path) ?: return path
        val start = match.range.first
        val endChar = path.getOrNull(match.range.last)
        val tail = if (endChar == '.') path.substring(match.range.last) else ""
        return path.substring(0, start) + tail
    }

    /** Extract the 7-char SHORTID of the device that introduced the conflict. */
    fun shortIdOf(conflictPath: String): String? =
        CONFLICT_REGEX.find(conflictPath)?.groupValues?.getOrNull(1)

    private fun formatTime(millis: Long): String =
        ISO_FORMAT.format(Instant.ofEpochMilli(millis))
}
