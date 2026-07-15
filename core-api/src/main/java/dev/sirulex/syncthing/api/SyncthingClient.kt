package dev.sirulex.syncthing.api

import dev.sirulex.syncthing.api.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.plugins.timeout
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class SyncthingClient(
    baseUrl: String = "http://127.0.0.1:8384",
    apiKey: String,
    json: Json = defaultJson,
    httpClient: HttpClient? = null,
) {
    companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    private val http = httpClient ?: HttpClient(OkHttp) {
        // Turn non-2xx responses into ResponseException so mutating calls
        // (addFolder, pauseDevice, …) don't silently swallow 4xx/5xx.
        expectSuccess = true
        defaultRequest {
            url(baseUrl)
            header("X-API-Key", apiKey)
        }
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
    }

    // --- System ---

    suspend fun ping(): PingResponse =
        http.get("/rest/system/ping").body()

    suspend fun systemStatus(): SystemStatus =
        http.get("/rest/system/status").body()

    suspend fun systemVersion(): SystemVersion =
        http.get("/rest/system/version").body()

    suspend fun connections(): Connections =
        http.get("/rest/system/connections").body()

    suspend fun restart() {
        http.post("/rest/system/restart")
    }

    suspend fun resetFolderIndex(folderId: String) {
        http.post("/rest/system/reset") {
            parameter("folder", folderId)
        }
    }

    suspend fun shutdown() {
        http.post("/rest/system/shutdown")
    }

    // --- Config ---

    suspend fun folders(): List<Folder> =
        http.get("/rest/config/folders").body()

    suspend fun devices(): List<Device> =
        http.get("/rest/config/devices").body()

    suspend fun addFolder(folder: Folder) {
        http.post("/rest/config/folders") {
            contentType(ContentType.Application.Json)
            setBody(folder)
        }
    }

    suspend fun updateFolder(folder: Folder) {
        // Folder is intentionally a partial view of Syncthing's configuration.
        // PATCH only the fields exposed by the editor so advanced settings that
        // are unknown to this client are not reset to daemon defaults.
        http.patch("/rest/config/folders/${folder.id}") {
            contentType(ContentType.Application.Json)
            setBody(
                FolderUpdate(
                    label = folder.label,
                    path = folder.path,
                    type = folder.type,
                    devices = folder.devices,
                    versioning = folder.versioning,
                )
            )
        }
    }

    suspend fun connectivityOptions(): ConnectivityOptions =
        http.get("/rest/config/options").body()

    suspend fun updateConnectivityOptions(options: ConnectivityOptions) {
        http.patch("/rest/config/options") {
            contentType(ContentType.Application.Json)
            setBody(
                ConnectivityOptionsUpdate(
                    listenAddresses = options.listenAddresses,
                    globalAnnounceServers = options.globalAnnounceServers,
                    globalAnnounceEnabled = options.globalAnnounceEnabled,
                    localAnnounceEnabled = options.localAnnounceEnabled,
                    relaysEnabled = options.relaysEnabled,
                )
            )
        }
    }

    suspend fun restartRequired(): Boolean =
        http.get("/rest/config/restart-required")
            .body<RestartRequired>()
            .requiresRestart

    suspend fun rawConfig(): JsonObject = http.get("/rest/config").body()

    suspend fun replaceConfig(config: JsonObject) {
        http.put("/rest/config") {
            contentType(ContentType.Application.Json)
            setBody(config)
        }
    }

    suspend fun setFolderDevices(folderId: String, devices: List<FolderDevice>) {
        http.patch("/rest/config/folders/$folderId") {
            contentType(ContentType.Application.Json)
            setBody(FolderDevicesPatch(devices))
        }
    }

    suspend fun addDevice(device: Device) {
        http.post("/rest/config/devices") {
            contentType(ContentType.Application.Json)
            setBody(device)
        }
    }

    suspend fun updateDevice(device: Device) {
        // PATCH is intentional: Device only models the fields the app consumes.
        // PUT would reset Syncthing settings unknown to this DTO (for example an
        // introduced device's provenance and removal policy).
        http.patch("/rest/config/devices/${device.deviceID}") {
            contentType(ContentType.Application.Json)
            setBody(
                DeviceUpdate(
                    name = device.name,
                    addresses = device.addresses,
                    compression = device.compression,
                    introducer = device.introducer,
                    autoAcceptFolders = device.autoAcceptFolders,
                )
            )
        }
    }

    // --- Database ---

    suspend fun folderStatus(folderId: String): FolderStatus =
        http.get("/rest/db/status") { parameter("folder", folderId) }.body()

    suspend fun rescanFolder(folderId: String) {
        http.post("/rest/db/scan") { parameter("folder", folderId) }
    }

    suspend fun rescanSubdir(folderId: String, sub: String) {
        http.post("/rest/db/scan") {
            parameter("folder", folderId)
            parameter("sub", sub)
        }
    }

    suspend fun browseFolder(
        folderId: String,
        prefix: String = "",
        levels: Int = 0,
    ): List<BrowseEntry> =
        http.get("/rest/db/browse") {
            parameter("folder", folderId)
            if (prefix.isNotEmpty()) parameter("prefix", prefix)
            parameter("levels", levels)
        }.body()

    suspend fun folderNeed(folderId: String, page: Int = 0, perpage: Int = 0): NeedList =
        http.get("/rest/db/need") {
            parameter("folder", folderId)
            if (page > 0) parameter("page", page)
            if (perpage > 0) parameter("perpage", perpage)
        }.body()

    suspend fun folderIgnores(folderId: String): Ignores =
        http.get("/rest/db/ignores") { parameter("folder", folderId) }.body()

    suspend fun setFolderIgnores(folderId: String, patterns: List<String>) {
        http.post("/rest/db/ignores") {
            parameter("folder", folderId)
            contentType(ContentType.Application.Json)
            setBody(SetIgnoresBody(ignore = patterns))
        }
    }

    suspend fun revertFolder(folderId: String) {
        http.post("/rest/db/revert") { parameter("folder", folderId) }
    }

    // --- Stats ---

    suspend fun deviceStats(): Map<String, DeviceStats> =
        http.get("/rest/stats/device").body()

    suspend fun folderStats(): Map<String, FolderStats> =
        http.get("/rest/stats/folder").body()

    suspend fun deleteFolder(folderId: String) {
        http.delete("/rest/config/folders/$folderId")
    }

    suspend fun deleteDevice(deviceId: String) {
        http.delete("/rest/config/devices/$deviceId")
    }

    suspend fun pauseFolder(folderId: String) {
        http.patch("/rest/config/folders/$folderId") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("paused" to true))
        }
    }

    suspend fun resumeFolder(folderId: String) {
        http.patch("/rest/config/folders/$folderId") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("paused" to false))
        }
    }

    // --- Pause / Resume ---

    suspend fun pauseDevice(deviceId: String) {
        http.post("/rest/system/pause") { parameter("device", deviceId) }
    }

    suspend fun resumeDevice(deviceId: String) {
        http.post("/rest/system/resume") { parameter("device", deviceId) }
    }

    // --- Pending ---

    suspend fun pendingFolders(): Map<String, PendingFolder> =
        http.get("/rest/cluster/pending/folders").body()

    suspend fun pendingDevices(): Map<String, PendingDevice> =
        http.get("/rest/cluster/pending/devices").body()

    suspend fun dismissPendingFolder(folderId: String, deviceId: String) {
        http.delete("/rest/cluster/pending/folders") {
            parameter("folder", folderId)
            parameter("device", deviceId)
        }
    }

    // --- Completion / Errors ---

    suspend fun folderCompletion(folderId: String, deviceId: String): FolderCompletionInfo =
        http.get("/rest/db/completion") {
            parameter("folder", folderId)
            parameter("device", deviceId)
        }.body()

    suspend fun folderErrors(folderId: String): FolderErrorList =
        http.get("/rest/folder/errors") { parameter("folder", folderId) }.body()

    suspend fun systemLog(): SystemLogResponse =
        http.get("/rest/system/log").body()

    suspend fun systemConfig(): SystemConfig =
        http.get("/rest/config").body()

    // --- Events ---

    suspend fun events(since: Long, timeout: Int = 60): List<Event> =
        http.get("/rest/events") {
            parameter("since", since)
            parameter("timeout", timeout)
            timeout {
                requestTimeoutMillis = (timeout.toLong() + 10) * 1000
                socketTimeoutMillis = (timeout.toLong() + 10) * 1000
            }
        }.body()

    /**
     * File-system changes are deliberately excluded from Syncthing's default
     * event stream. The dedicated disk endpoint includes both locally detected
     * and remotely applied changes.
     */
    suspend fun diskEvents(since: Long, timeout: Int = 60): List<Event> =
        http.get("/rest/events/disk") {
            parameter("since", since)
            parameter("timeout", timeout)
            timeout {
                requestTimeoutMillis = (timeout.toLong() + 10) * 1000
                socketTimeoutMillis = (timeout.toLong() + 10) * 1000
            }
        }.body()

    fun close() {
        http.close()
    }
}

@Serializable
private data class FolderDevicesPatch(
    val devices: List<FolderDevice>,
)
