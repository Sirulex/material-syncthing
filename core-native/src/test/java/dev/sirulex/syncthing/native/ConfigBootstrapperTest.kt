package dev.sirulex.syncthing.native

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConfigBootstrapperTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var configDir: File
    private lateinit var bootstrapper: ConfigBootstrapper

    @Before
    fun setup() {
        configDir = tempDir.newFolder("syncthing")
        bootstrapper = ConfigBootstrapper(configDir)
    }

    @Test
    fun `configExists returns false when no config`() {
        assertThat(bootstrapper.configExists).isFalse()
    }

    @Test
    fun `configExists returns true when config exists`() {
        File(configDir, "config.xml").writeText(MINIMAL_CONFIG)
        assertThat(bootstrapper.configExists).isTrue()
    }

    @Test
    fun `readApiKey extracts key from config`() {
        File(configDir, "config.xml").writeText(MINIMAL_CONFIG)
        assertThat(bootstrapper.readApiKey()).isEqualTo("test-api-key-12345678901234567")
    }

    @Test
    fun `readGuiPort extracts port from config`() {
        File(configDir, "config.xml").writeText(MINIMAL_CONFIG)
        assertThat(bootstrapper.readGuiPort()).isEqualTo(8384)
    }

    @Test
    fun `readGuiPort returns default when missing`() {
        val config = MINIMAL_CONFIG.replace(
            "<address>127.0.0.1:8384</address>",
            ""
        )
        File(configDir, "config.xml").writeText(config)
        assertThat(bootstrapper.readGuiPort()).isEqualTo(8384)
    }

    @Test
    fun `patchConfig sets ignorePerms on folders`() {
        val config = MINIMAL_CONFIG.replace(
            "ignorePerms=\"true\"",
            "ignorePerms=\"false\""
        )
        File(configDir, "config.xml").writeText(config)
        bootstrapper.patchConfig(null)

        val patched = File(configDir, "config.xml").readText()
        assertThat(patched).contains("ignorePerms=\"true\"")
    }

    @Test
    fun `patchConfig sets startBrowser false`() {
        val config = MINIMAL_CONFIG.replace(
            "<startBrowser>false</startBrowser>",
            "<startBrowser>true</startBrowser>"
        )
        File(configDir, "config.xml").writeText(config)
        bootstrapper.patchConfig(null)

        val patched = File(configDir, "config.xml").readText()
        assertThat(patched).contains("<startBrowser>false</startBrowser>")
    }

    @Test
    fun `patchConfig enables plaintext localhost GUI`() {
        val config = MINIMAL_CONFIG
            .replace("""<gui enabled="true" tls="false">""", """<gui enabled="false" tls="true">""")
            .replace("<address>127.0.0.1:8384</address>", "<address>0.0.0.0:9999</address>")
        File(configDir, "config.xml").writeText(config)

        bootstrapper.patchConfig(null)

        val patched = File(configDir, "config.xml").readText()
        assertThat(patched).contains("""<gui enabled="true" tls="false">""")
        assertThat(patched).contains("<address>127.0.0.1:8384</address>")
    }

    @Test
    fun `patchConfig is idempotent`() {
        File(configDir, "config.xml").writeText(MINIMAL_CONFIG)
        bootstrapper.patchConfig(null)
        val first = File(configDir, "config.xml").readText()

        bootstrapper.patchConfig(null)
        val second = File(configDir, "config.xml").readText()

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `patchConfig preserves a custom local device name`() {
        File(configDir, "config.xml").writeText(MINIMAL_CONFIG)

        bootstrapper.patchConfig("DEVICE1-ABCDEF")

        val patched = File(configDir, "config.xml").readText()
        assertThat(patched).contains("name=\"TestDevice\"")
    }

    @Test
    fun `patchConfig applies onboarding name to unnamed local device`() {
        File(configDir, "config.xml").writeText(
            MINIMAL_CONFIG.replace("name=\"TestDevice\"", "name=\"\"")
        )

        bootstrapper.patchConfig("DEVICE1-ABCDEF", "My Android")

        val patched = File(configDir, "config.xml").readText()
        assertThat(patched).contains("name=\"My Android\"")
    }

    companion object {
        private val MINIMAL_CONFIG = """
            <?xml version="1.0" encoding="UTF-8"?>
            <configuration version="37">
                <folder id="default" label="Default" path="/storage/emulated/0/Sync" type="sendreceive" ignorePerms="true">
                    <device id="DEVICE1-ABCDEF" introducedBy=""/>
                    <paused>false</paused>
                </folder>
                <device id="DEVICE1-ABCDEF" name="TestDevice">
                    <address>dynamic</address>
                    <paused>false</paused>
                </device>
                <gui enabled="true" tls="false">
                    <address>127.0.0.1:8384</address>
                    <apikey>test-api-key-12345678901234567</apikey>
                    <user>syncthing</user>
                </gui>
                <options>
                    <startBrowser>false</startBrowser>
                    <natEnabled>true</natEnabled>
                </options>
            </configuration>
        """.trimIndent()
    }
}
