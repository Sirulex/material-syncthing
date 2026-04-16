package dev.lostf1sh.syncthing.api

import dev.lostf1sh.syncthing.api.dto.*
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
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

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
        http.put("/rest/config/folders/${folder.id}") {
            contentType(ContentType.Application.Json)
            setBody(folder)
        }
    }

    suspend fun addDevice(device: Device) {
        http.post("/rest/config/devices") {
            contentType(ContentType.Application.Json)
            setBody(device)
        }
    }

    suspend fun updateDevice(device: Device) {
        http.put("/rest/config/devices/${device.deviceID}") {
            contentType(ContentType.Application.Json)
            setBody(device)
        }
    }

    // --- Database ---

    suspend fun folderStatus(folderId: String): FolderStatus =
        http.get("/rest/db/status") { parameter("folder", folderId) }.body()

    suspend fun rescanFolder(folderId: String) {
        http.post("/rest/db/scan") { parameter("folder", folderId) }
    }

    suspend fun deleteFolder(folderId: String) {
        http.delete("/rest/config/folders/$folderId")
    }

    suspend fun deleteDevice(deviceId: String) {
        http.delete("/rest/config/devices/$deviceId")
    }

    suspend fun pauseFolder(folderId: String) {
        val folder = http.get("/rest/config/folders/$folderId").body<Folder>()
        http.put("/rest/config/folders/$folderId") {
            contentType(ContentType.Application.Json)
            setBody(folder.copy(paused = true))
        }
    }

    suspend fun resumeFolder(folderId: String) {
        val folder = http.get("/rest/config/folders/$folderId").body<Folder>()
        http.put("/rest/config/folders/$folderId") {
            contentType(ContentType.Application.Json)
            setBody(folder.copy(paused = false))
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

    // --- Events ---

    suspend fun events(since: Long, timeout: Int = 60): List<Event> =
        http.get("/rest/events") {
            parameter("since", since)
            parameter("timeout", timeout)
        }.body()

    fun close() {
        http.close()
    }
}
