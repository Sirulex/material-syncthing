package dev.sirulex.syncthing.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Event(
    val id: Long = 0,
    val globalID: Long = 0,
    val type: String = "",
    val time: String = "",
    val data: JsonObject? = null,
)
