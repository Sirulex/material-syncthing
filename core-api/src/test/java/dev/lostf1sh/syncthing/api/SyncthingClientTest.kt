package dev.lostf1sh.syncthing.api

import com.google.common.truth.Truth.assertThat
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.api.dto.DeviceUpdate
import dev.lostf1sh.syncthing.api.dto.ConnectivityOptions
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.api.dto.PingResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Test

class SyncthingClientTest {

    private fun mockClient(handler: MockEngine.() -> Unit = {}): Pair<SyncthingClient, MockEngine> {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/rest/system/ping" -> respond(
                    content = """{"ping":"pong"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/rest/system/version" -> respond(
                    content = """{"arch":"amd64","longVersion":"syncthing v1.29.2","os":"linux","version":"v1.29.2"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/rest/system/status" -> respond(
                    content = """{"alloc":12345,"myID":"DEVICE-ID","goroutines":42,"startTime":"2024-01-01T00:00:00Z","uptime":3600,"tilde":"/storage","pathSeparator":"/"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/rest/config/folders" -> respond(
                    content = """[{"id":"default","label":"Default","path":"/sync","type":"sendreceive","paused":false}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/rest/config/devices" -> respond(
                    content = """[{"deviceID":"ABC-DEF","name":"TestDevice","addresses":["dynamic"],"paused":false}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/rest/system/connections" -> respond(
                    content = """{"connections":{},"total":{"at":"","inBytesTotal":0,"outBytesTotal":0}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(
                    content = "Not found",
                    status = HttpStatusCode.NotFound,
                )
            }
        }
        val httpClient = HttpClient(engine) {
            defaultRequest { header("X-API-Key", "test-key") }
            install(ContentNegotiation) { json(SyncthingClient.defaultJson) }
            install(HttpTimeout) { requestTimeoutMillis = 5_000 }
        }
        val client = SyncthingClient(
            apiKey = "test-key",
            httpClient = httpClient,
        )
        return client to engine
    }

    @Test
    fun `ping returns pong`() = runTest {
        val (client, _) = mockClient()
        val response = client.ping()
        assertThat(response.ping).isEqualTo("pong")
    }

    @Test
    fun `systemVersion parses correctly`() = runTest {
        val (client, _) = mockClient()
        val version = client.systemVersion()
        assertThat(version.version).isEqualTo("v1.29.2")
        assertThat(version.arch).isEqualTo("amd64")
        assertThat(version.os).isEqualTo("linux")
    }

    @Test
    fun `systemStatus parses myID and uptime`() = runTest {
        val (client, _) = mockClient()
        val status = client.systemStatus()
        assertThat(status.myID).isEqualTo("DEVICE-ID")
        assertThat(status.uptime).isEqualTo(3600)
        assertThat(status.goroutines).isEqualTo(42)
    }

    @Test
    fun `folders returns list`() = runTest {
        val (client, _) = mockClient()
        val folders = client.folders()
        assertThat(folders).hasSize(1)
        assertThat(folders[0].id).isEqualTo("default")
        assertThat(folders[0].label).isEqualTo("Default")
        assertThat(folders[0].type).isEqualTo("sendreceive")
    }

    @Test
    fun `devices returns list`() = runTest {
        val (client, _) = mockClient()
        val devices = client.devices()
        assertThat(devices).hasSize(1)
        assertThat(devices[0].deviceID).isEqualTo("ABC-DEF")
        assertThat(devices[0].name).isEqualTo("TestDevice")
    }

    @Test
    fun `connections parses empty connections`() = runTest {
        val (client, _) = mockClient()
        val conn = client.connections()
        assertThat(conn.connections).isEmpty()
    }

    @Test
    fun `unknown keys in JSON are ignored`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"ping":"pong","unknownField":42,"nested":{"a":1}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(SyncthingClient.defaultJson) }
        }
        val client = SyncthingClient(apiKey = "test", httpClient = httpClient)
        val response = client.ping()
        assertThat(response.ping).isEqualTo("pong")
    }

    @Test
    fun `device update patches existing configuration`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            respond(content = "", status = HttpStatusCode.OK)
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(SyncthingClient.defaultJson) }
        }
        val client = SyncthingClient(apiKey = "test", httpClient = httpClient)

        client.updateDevice(Device(deviceID = "ABC-DEF", introducer = true))

        assertThat(method).isEqualTo(HttpMethod.Patch)
        assertThat(path).isEqualTo("/rest/config/devices/ABC-DEF")
    }

    @Test
    fun `device update serializes explicit false values`() {
        val encoded = SyncthingClient.defaultJson.encodeToString(
            DeviceUpdate(
                name = "Peer",
                addresses = listOf("dynamic"),
                compression = "metadata",
                introducer = false,
                autoAcceptFolders = false,
            )
        )

        assertThat(encoded).contains("\"introducer\":false")
        assertThat(encoded).contains("\"autoAcceptFolders\":false")
    }

    @Test
    fun `folder update patches existing configuration`() = runTest {
        var method: HttpMethod? = null
        val engine = MockEngine { request ->
            method = request.method
            respond(content = "", status = HttpStatusCode.OK)
        }
        val client = SyncthingClient(
            apiKey = "test",
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) { json(SyncthingClient.defaultJson) }
            },
        )

        client.updateFolder(Folder(id = "photos", path = "/sync/photos"))

        assertThat(method).isEqualTo(HttpMethod.Patch)
    }

    @Test
    fun `connectivity update patches options endpoint`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            respond(content = "", status = HttpStatusCode.OK)
        }
        val client = SyncthingClient(
            apiKey = "test",
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) { json(SyncthingClient.defaultJson) }
            },
        )

        client.updateConnectivityOptions(ConnectivityOptions())

        assertThat(method).isEqualTo(HttpMethod.Patch)
        assertThat(path).isEqualTo("/rest/config/options")
    }
}
