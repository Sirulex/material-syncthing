package dev.sirulex.syncthing.data

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class SettingsBackupCodecTest {

    @Test
    fun `settings document round trips supported preferences`() {
        val source = JsonObject(
            mapOf(
                "wifi_only" to JsonPrimitive(true),
                "theme" to JsonPrimitive("dark"),
                "gui_port" to JsonPrimitive(8384),
                "folder_conditions" to JsonPrimitive("{}"),
            )
        )

        val decoded = SettingsBackupCodec.decodeSettings(
            SettingsBackupCodec.encodeSettings(source)
        )

        assertThat(decoded).isEqualTo(source)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `settings document rejects invalid port`() {
        SettingsBackupCodec.encodeSettings(
            JsonObject(mapOf("gui_port" to JsonPrimitive(70_000)))
        )
    }

    @Test
    fun `configuration backup retains raw Syncthing fields`() {
        val appSettings = JsonObject(mapOf("theme" to JsonPrimitive("system")))
        val config = kotlinx.serialization.json.buildJsonObject {
            put("folders", kotlinx.serialization.json.JsonArray(emptyList()))
            put("devices", kotlinx.serialization.json.JsonArray(emptyList()))
            put("options", JsonObject(mapOf("customFutureOption" to JsonPrimitive(42))))
            put("gui", JsonObject(mapOf("apiKey" to JsonPrimitive("secret"))))
        }

        val decoded = SettingsBackupCodec.decodeConfigurationBackup(
            SettingsBackupCodec.encodeConfigurationBackup(
                deviceId = "DEVICE-ID",
                createdAtEpochMs = 123L,
                appSettings = appSettings,
                syncthingConfig = config,
            )
        )

        assertThat(decoded.deviceId).isEqualTo("DEVICE-ID")
        assertThat(decoded.syncthingConfig["options"]).isEqualTo(config["options"])
        assertThat(decoded.syncthingConfig).doesNotContainKey("gui")
    }

    @Test
    fun `restore retains current GUI credentials`() {
        val current = kotlinx.serialization.json.buildJsonObject {
            put("folders", kotlinx.serialization.json.JsonArray(emptyList()))
            put("devices", kotlinx.serialization.json.JsonArray(emptyList()))
            put("options", JsonObject(mapOf("relaysEnabled" to JsonPrimitive(true))))
            put("gui", JsonObject(mapOf("apiKey" to JsonPrimitive("current-key"))))
        }
        val backup = kotlinx.serialization.json.buildJsonObject {
            put("folders", kotlinx.serialization.json.JsonArray(emptyList()))
            put("devices", kotlinx.serialization.json.JsonArray(emptyList()))
            put("options", JsonObject(mapOf("relaysEnabled" to JsonPrimitive(false))))
        }

        val merged = SettingsBackupCodec.mergeForRestore(current, backup)

        assertThat(merged["gui"]).isEqualTo(current["gui"])
        assertThat(merged["options"]).isEqualTo(backup["options"])
    }
}
