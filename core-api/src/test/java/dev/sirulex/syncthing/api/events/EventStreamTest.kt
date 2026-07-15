package dev.sirulex.syncthing.api.events

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.sirulex.syncthing.api.SyncthingClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class EventStreamTest {

    private fun clientWithEvents(json: String): SyncthingClient {
        var firstCall = true
        val engine = MockEngine {
            if (firstCall) {
                firstCall = false
                respond(
                    content = json,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                // Hang to stop the flow after first batch
                respond(
                    content = "[]",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(SyncthingClient.defaultJson) }
        }
        return SyncthingClient(apiKey = "test", httpClient = httpClient)
    }

    @Test
    fun `parses DeviceConnected event`() = runTest {
        val client = clientWithEvents("""[{
            "id": 1, "globalID": 1, "type": "DeviceConnected",
            "time": "2024-01-01T00:00:00Z",
            "data": {"id": "DEVICE-ABC", "addr": "192.168.1.5:22000"}
        }]""")
        val stream = EventStream(client)

        stream.events().test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(SyncthingEvent.DeviceConnected::class.java)
            val dc = event as SyncthingEvent.DeviceConnected
            assertThat(dc.deviceId).isEqualTo("DEVICE-ABC")
            assertThat(dc.address).isEqualTo("192.168.1.5:22000")
            assertThat(dc.id).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parses StateChanged event`() = runTest {
        val client = clientWithEvents("""[{
            "id": 5, "globalID": 5, "type": "StateChanged",
            "time": "2024-01-01T00:00:00Z",
            "data": {"folder": "default", "from": "idle", "to": "syncing"}
        }]""")
        val stream = EventStream(client)

        stream.events().test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(SyncthingEvent.StateChanged::class.java)
            val sc = event as SyncthingEvent.StateChanged
            assertThat(sc.folderId).isEqualTo("default")
            assertThat(sc.from).isEqualTo("idle")
            assertThat(sc.to).isEqualTo("syncing")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parses ItemFinished event`() = runTest {
        val client = clientWithEvents("""[{
            "id": 10, "globalID": 10, "type": "ItemFinished",
            "time": "2024-01-01T00:00:00Z",
            "data": {"folder": "photos", "item": "img001.jpg", "action": "update", "error": null}
        }]""")
        val stream = EventStream(client)

        stream.events().test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(SyncthingEvent.ItemFinished::class.java)
            val item = event as SyncthingEvent.ItemFinished
            assertThat(item.folderId).isEqualTo("photos")
            assertThat(item.item).isEqualTo("img001.jpg")
            assertThat(item.action).isEqualTo("update")
            assertThat(item.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disk stream requests dedicated endpoint and parses detected changes`() = runTest {
        val requestedPath = AtomicReference<String>()
        var firstCall = true
        val engine = MockEngine { request ->
            requestedPath.compareAndSet(null, request.url.encodedPath)
            respond(
                content = if (firstCall) {
                    firstCall = false
                    """[
                        {
                            "id": 11, "globalID": 20, "type": "LocalChangeDetected",
                            "time": "2024-01-01T00:00:00Z",
                            "data": {"folder": "photos", "path": "new.jpg", "action": "added"}
                        },
                        {
                            "id": 12, "globalID": 21, "type": "RemoteChangeDetected",
                            "time": "2024-01-01T00:00:01Z",
                            "data": {"folderID": "docs", "path": "old.txt", "action": "deleted"}
                        }
                    ]"""
                } else {
                    "[]"
                },
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(SyncthingClient.defaultJson) }
        }
        val stream = EventStream(
            client = SyncthingClient(apiKey = "test", httpClient = httpClient),
            source = EventStream.Source.DISK,
        )

        stream.events().test {
            val local = awaitItem() as SyncthingEvent.LocalChangeDetected
            assertThat(local.folderId).isEqualTo("photos")
            assertThat(local.path).isEqualTo("new.jpg")
            assertThat(local.action).isEqualTo("added")

            val remote = awaitItem() as SyncthingEvent.RemoteChangeDetected
            assertThat(remote.folderId).isEqualTo("docs")
            assertThat(remote.path).isEqualTo("old.txt")
            assertThat(remote.action).isEqualTo("deleted")
            assertThat(requestedPath.get()).isEqualTo("/rest/events/disk")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parses FolderCompletion event`() = runTest {
        val client = clientWithEvents("""[{
            "id": 15, "globalID": 15, "type": "FolderCompletion",
            "time": "2024-01-01T00:00:00Z",
            "data": {"folder": "docs", "device": "DEV-123", "completion": 85.5}
        }]""")
        val stream = EventStream(client)

        stream.events().test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(SyncthingEvent.FolderCompletion::class.java)
            val fc = event as SyncthingEvent.FolderCompletion
            assertThat(fc.folderId).isEqualTo("docs")
            assertThat(fc.deviceId).isEqualTo("DEV-123")
            assertThat(fc.completion).isWithin(0.01).of(85.5)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unknown event type yields Unknown`() = runTest {
        val client = clientWithEvents("""[{
            "id": 99, "globalID": 99, "type": "SomeFutureEvent",
            "time": "2024-01-01T00:00:00Z",
            "data": {"foo": "bar"}
        }]""")
        val stream = EventStream(client)

        stream.events().test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(SyncthingEvent.Unknown::class.java)
            assertThat((event as SyncthingEvent.Unknown).type).isEqualTo("SomeFutureEvent")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parses batch of mixed events`() = runTest {
        val client = clientWithEvents("""[
            {"id": 1, "globalID": 1, "type": "ConfigSaved", "time": "2024-01-01T00:00:00Z", "data": null},
            {"id": 2, "globalID": 2, "type": "DeviceDisconnected", "time": "2024-01-01T00:00:01Z", "data": {"id": "DEV-X"}},
            {"id": 3, "globalID": 3, "type": "FolderPaused", "time": "2024-01-01T00:00:02Z", "data": {"id": "backup"}}
        ]""")
        val stream = EventStream(client)

        stream.events().test {
            assertThat(awaitItem()).isInstanceOf(SyncthingEvent.ConfigSaved::class.java)
            assertThat(awaitItem()).isInstanceOf(SyncthingEvent.DeviceDisconnected::class.java)
            val paused = awaitItem() as SyncthingEvent.FolderPaused
            assertThat(paused.folderId).isEqualTo("backup")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
