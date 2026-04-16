package dev.lostf1sh.syncthing.api.dto

import kotlinx.serialization.Serializable

/**
 * Entry returned by /rest/db/browse. Directories may contain nested [children]
 * when `levels` allows recursion. The leaf [name] is relative to the query
 * prefix — not a full folder-relative path.
 */
@Serializable
data class BrowseEntry(
    val name: String = "",
    val type: String = "",
    val size: Long = 0,
    val modTime: String = "",
    val children: List<BrowseEntry> = emptyList(),
) {
    val isDirectory: Boolean
        get() = type == TYPE_DIRECTORY || type == TYPE_SYMLINK_DIR
    val isFile: Boolean
        get() = type == TYPE_FILE || type == TYPE_SYMLINK_FILE

    companion object {
        const val TYPE_FILE = "FILE_INFO_TYPE_FILE"
        const val TYPE_DIRECTORY = "FILE_INFO_TYPE_DIRECTORY"
        const val TYPE_SYMLINK_FILE = "FILE_INFO_TYPE_SYMLINK_FILE"
        const val TYPE_SYMLINK_DIR = "FILE_INFO_TYPE_SYMLINK_DIRECTORY"
    }
}

/**
 * Entry used in /rest/db/need (progress/queued/rest lists). Uses the protobuf
 * integer type encoding; `name` here is the full folder-relative path.
 */
@Serializable
data class NeedFileInfo(
    val name: String = "",
    val type: Int = 0,
    val size: Long = 0,
    val modified: String = "",
    val deleted: Boolean = false,
    val invalid: Boolean = false,
    val sequence: Long = 0,
    val localFlags: Int = 0,
)

@Serializable
data class NeedList(
    val progress: List<NeedFileInfo> = emptyList(),
    val queued: List<NeedFileInfo> = emptyList(),
    val rest: List<NeedFileInfo> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val perpage: Int = 0,
)

@Serializable
data class Ignores(
    val ignore: List<String> = emptyList(),
    val expanded: List<String> = emptyList(),
)

@Serializable
data class SetIgnoresBody(
    val ignore: List<String> = emptyList(),
)
