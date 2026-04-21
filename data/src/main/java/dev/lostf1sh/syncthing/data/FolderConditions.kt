package dev.lostf1sh.syncthing.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class FolderConditionsJson(
    val conditions: Map<String, FolderCondition> = emptyMap(),
)

@Serializable
data class FolderCondition(
    val wifiOnly: Boolean = false,
    val chargingOnly: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true }

fun parseFolderConditions(raw: String): Map<String, FolderCondition> {
    return try {
        json.decodeFromString<FolderConditionsJson>(raw).conditions
    } catch (_: Exception) {
        emptyMap()
    }
}

fun serializeFolderConditions(map: Map<String, FolderCondition>): String {
    return json.encodeToString(FolderConditionsJson(map))
}
