package dev.sirulex.syncthing.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PingResponse(val ping: String)

@Serializable
data class SystemStatus(
    val alloc: Long = 0,
    val myID: String = "",
    val goroutines: Int = 0,
    val startTime: String = "",
    val uptime: Int = 0,
    val tilde: String = "",
    val pathSeparator: String = "/",
)

@Serializable
data class SystemVersion(
    val arch: String = "",
    val longVersion: String = "",
    val os: String = "",
    val version: String = "",
)

@Serializable
data class Connections(
    val connections: Map<String, ConnectionInfo> = emptyMap(),
    val total: TotalStats = TotalStats(),
)

@Serializable
data class ConnectionInfo(
    val address: String = "",
    val at: String = "",
    val clientVersion: String = "",
    val connected: Boolean = false,
    val inBytesTotal: Long = 0,
    val isLocal: Boolean = false,
    val outBytesTotal: Long = 0,
    val paused: Boolean = false,
    val startedAt: String = "",
    val type: String = "",
    val latencyMs: Double = 0.0,
)

@Serializable
data class TotalStats(
    val at: String = "",
    val inBytesTotal: Long = 0,
    val outBytesTotal: Long = 0,
)
