package dev.sirulex.syncthing.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

object SettingsBackupCodec {
    const val SETTINGS_KIND = "material-syncthing-settings"
    const val BACKUP_KIND = "material-syncthing-configuration-backup"
    const val SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = true
    }

    private val booleanKeys = setOf(
        "run_on_boot",
        "wifi_only",
        "allow_metered",
        "charging_only",
        "respect_battery_saver",
        "onboarding_complete",
        "notify_sync_complete",
        "notify_device_connected",
        "notify_errors",
        "hide_syncing_card",
        "scheduler_enabled",
        "biometric_enabled",
        "start_suppressed_by_user",
    )
    private val stringKeys = setOf(
        "device_name",
        "theme",
        "active_profile",
        "folder_conditions",
    )
    private val intRanges = mapOf(
        "gui_port" to 1..65535,
        "scheduler_start_hour" to 0..23,
        "scheduler_start_minute" to 0..59,
        "scheduler_end_hour" to 0..23,
        "scheduler_end_minute" to 0..59,
    )

    fun encodeSettings(preferences: JsonObject): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("kind", SETTINGS_KIND)
            put("schemaVersion", SCHEMA_VERSION)
            put("preferences", sanitizePreferences(preferences))
        },
    )

    fun decodeSettings(document: String): JsonObject {
        val root = parseObject(document)
        // Accept the old flat document once so backups created by earlier
        // builds remain importable.
        if (root["kind"] == null) return sanitizePreferences(root)
        requireString(root, "kind", SETTINGS_KIND)
        requireVersion(root)
        return sanitizePreferences(root["preferences"] as? JsonObject
            ?: throw IllegalArgumentException("Settings document has no preferences object"))
    }

    fun encodeConfigurationBackup(
        deviceId: String,
        createdAtEpochMs: Long,
        appSettings: JsonObject,
        syncthingConfig: JsonObject,
    ): String {
        require(deviceId.isNotBlank()) { "Local device ID is unavailable" }
        validateSyncthingConfig(syncthingConfig)
        val restorableConfig = restorableSyncthingConfig(syncthingConfig)
        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("kind", BACKUP_KIND)
                put("schemaVersion", SCHEMA_VERSION)
                put("createdAtEpochMs", createdAtEpochMs)
                put("deviceId", deviceId)
                put("appSettings", sanitizePreferences(appSettings))
                put("syncthingConfig", restorableConfig)
            },
        )
    }

    fun decodeConfigurationBackup(document: String): ConfigurationBackup {
        val root = parseObject(document)
        requireString(root, "kind", BACKUP_KIND)
        requireVersion(root)
        val deviceIdPrimitive = root["deviceId"] as? JsonPrimitive
        val deviceId = deviceIdPrimitive?.takeIf { it.isString }?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Backup has no device ID")
        val appSettings = sanitizePreferences(root["appSettings"] as? JsonObject
            ?: throw IllegalArgumentException("Backup has no app settings"))
        val config = root["syncthingConfig"] as? JsonObject
            ?: throw IllegalArgumentException("Backup has no Syncthing configuration")
        validateSyncthingConfig(config)
        return ConfigurationBackup(deviceId, appSettings, restorableSyncthingConfig(config))
    }

    /** Applies only user-managed sync configuration. The current GUI object is
     * deliberately retained so restoring an old API key cannot lock the app
     * out of the local daemon halfway through the operation. */
    fun mergeForRestore(current: JsonObject, backup: JsonObject): JsonObject {
        validateSyncthingConfig(current)
        validateSyncthingConfig(backup)
        val merged = current.toMutableMap()
        RESTORABLE_CONFIG_KEYS.forEach { key -> merged[key] = backup.getValue(key) }
        return JsonObject(merged)
    }

    fun sanitizePreferences(input: JsonObject): JsonObject {
        val sanitized = linkedMapOf<String, JsonElement>()
        booleanKeys.forEach { key ->
            input[key]?.let { value ->
                val primitive = value as? JsonPrimitive
                    ?: throw IllegalArgumentException("$key must be true or false")
                val parsed = primitive.takeUnless { it.isString }?.booleanOrNull
                    ?: throw IllegalArgumentException("$key must be true or false")
                sanitized[key] = JsonPrimitive(parsed)
            }
        }
        stringKeys.forEach { key ->
            input[key]?.let { value ->
                val primitive = value as? JsonPrimitive
                    ?: throw IllegalArgumentException("$key must be text")
                val parsed = primitive.takeIf { it.isString }?.contentOrNull
                    ?: throw IllegalArgumentException("$key must be text")
                if (key == "theme" && parsed !in setOf("system", "light", "dark")) {
                    throw IllegalArgumentException("Unsupported theme: $parsed")
                }
                if (key == "folder_conditions") {
                    val conditions = runCatching { Json.parseToJsonElement(parsed) }.getOrNull()
                    if (conditions !is JsonObject) {
                        throw IllegalArgumentException("folder_conditions must contain a JSON object")
                    }
                }
                sanitized[key] = JsonPrimitive(parsed)
            }
        }
        intRanges.forEach { (key, range) ->
            input[key]?.let { value ->
                val primitive = value as? JsonPrimitive
                    ?: throw IllegalArgumentException("$key must be a number")
                val parsed = primitive.takeUnless { it.isString }?.intOrNull
                    ?: throw IllegalArgumentException("$key must be a number")
                require(parsed in range) { "$key must be in ${range.first}..${range.last}" }
                sanitized[key] = JsonPrimitive(parsed)
            }
        }
        require(sanitized.isNotEmpty()) { "The settings document contains no supported values" }
        return JsonObject(sanitized)
    }

    private fun parseObject(document: String): JsonObject =
        runCatching { json.parseToJsonElement(document).jsonObject }
            .getOrElse { throw IllegalArgumentException("Invalid JSON document", it) }

    private fun requireVersion(root: JsonObject) {
        val primitive = root["schemaVersion"] as? JsonPrimitive
        val version = primitive?.takeUnless { it.isString }?.intOrNull
            ?: throw IllegalArgumentException("Document has no schema version")
        require(version == SCHEMA_VERSION) { "Unsupported schema version: $version" }
    }

    private fun requireString(root: JsonObject, key: String, expected: String) {
        val primitive = root[key] as? JsonPrimitive
        val actual = primitive?.takeIf { it.isString }?.contentOrNull
        require(actual == expected) { "Unexpected document type: ${actual ?: "missing"}" }
    }

    private fun validateSyncthingConfig(config: JsonObject) {
        require(config["folders"] is JsonArray) { "Syncthing configuration has no folders array" }
        require(config["devices"] is JsonArray) { "Syncthing configuration has no devices array" }
        require(config["options"] is JsonObject) { "Syncthing configuration has no options object" }
    }

    private fun restorableSyncthingConfig(config: JsonObject): JsonObject = JsonObject(
        RESTORABLE_CONFIG_KEYS.associateWith(config::getValue)
    )

    private val RESTORABLE_CONFIG_KEYS = listOf("folders", "devices", "options")
}

data class ConfigurationBackup(
    val deviceId: String,
    val appSettings: JsonObject,
    val syncthingConfig: JsonObject,
)
