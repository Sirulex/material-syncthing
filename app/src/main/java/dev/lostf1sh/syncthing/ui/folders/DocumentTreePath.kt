package dev.lostf1sh.syncthing.ui.folders

/** Converts the storage document IDs returned by Android's folder picker to
 * the real paths required by the embedded Syncthing process. */
internal fun documentTreePath(documentId: String, primaryStorageRoot: String): String? {
    val separator = documentId.indexOf(':')
    if (separator < 0) return null
    val volume = documentId.substring(0, separator)
    val relative = documentId.substring(separator + 1).trimStart('/')
    if (relative.any { it.isISOControl() } || relative.split('/').any { it == ".." }) return null
    val root = when {
        volume == "primary" -> primaryStorageRoot
        volume == "raw" -> return documentId.removePrefix("raw:").takeIf { it.startsWith('/') }
        VOLUME_ID.matches(volume) -> "/storage/$volume"
        else -> return null
    }.trimEnd('/')
    return if (relative.isEmpty()) root else "$root/$relative"
}

private val VOLUME_ID = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")
