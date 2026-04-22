// Ported from Catfriend1/syncthing-android (MPL-2.0): util/ConfigXml.java
// Only bootstrap/initial-config logic ported. Folder/device CRUD deferred to Phase 5+.
package dev.lostf1sh.syncthing.native

import android.os.Build
import android.util.Log
import java.io.File
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Bootstraps Syncthing config.xml on first run.
 *
 * After `syncthing generate` creates the default config, this class patches it:
 * - GUI binds to 127.0.0.1:8384
 * - startBrowser = false
 * - ignorePerms = true on all folders
 * - Local device named after Build.MODEL
 *
 * @param configDir The STHOMEDIR directory containing config.xml
 */
class ConfigBootstrapper(private val configDir: File) {

    companion object {
        private const val TAG = "ConfigBootstrapper"
        private const val CONFIG_FILE = "config.xml"
        private const val GUI_ADDRESS = "127.0.0.1:8384"
    }

    private val configFile: File
        get() = File(configDir, CONFIG_FILE)

    val configExists: Boolean
        get() = configFile.exists() && configFile.length() > 0

    /**
     * Reads the API key from config.xml.
     * Must be called after config is generated/patched.
     */
    fun readApiKey(): String {
        val doc = parseConfig()
        val gui = doc.documentElement.getElementsByTagName("gui").item(0) ?: error("No <gui> in config")
        val apiKeyNode = (gui as org.w3c.dom.Element).getElementsByTagName("apikey").item(0)
            ?: error("No <apikey> in config")
        return apiKeyNode.textContent
    }

    /**
     * Reads the GUI bind port from config.xml.
     */
    fun readGuiPort(): Int {
        val doc = parseConfig()
        val gui = doc.documentElement.getElementsByTagName("gui").item(0) ?: return 8384
        val addressNode = (gui as org.w3c.dom.Element).getElementsByTagName("address").item(0)
            ?: return 8384
        val address = addressNode.textContent
        return address.substringAfter(":").toIntOrNull() ?: 8384
    }

    /**
     * Patches config.xml after initial generation.
     * Catfriend1 does this in ConfigXml.generateConfig() and updateIfNeeded().
     */
    fun patchConfig(localDeviceId: String?) {
        if (!configFile.exists()) {
            Log.w(TAG, "Config file missing, cannot patch")
            return
        }

        val doc = parseConfig()
        var changed = false

        // --- GUI section ---
        val gui = doc.documentElement.getElementsByTagName("gui").item(0) as? org.w3c.dom.Element
        if (gui != null) {
            if (gui.getAttribute("enabled") != "true") {
                gui.setAttribute("enabled", "true")
                changed = true
            }
            if (gui.getAttribute("tls") != "false") {
                gui.setAttribute("tls", "false")
                changed = true
            }

            // Bind to localhost only
            changed = setElement(doc, gui, "address", GUI_ADDRESS) || changed

            // Disable browser launch (meaningless on Android)
            val options = doc.documentElement.getElementsByTagName("options").item(0) as? org.w3c.dom.Element
            if (options != null) {
                changed = setElement(doc, options, "startBrowser", "false") || changed
            }
        }

        // --- Folders: set ignorePerms ---
        val folders = doc.documentElement.getElementsByTagName("folder")
        for (i in 0 until folders.length) {
            val folder = folders.item(i) as org.w3c.dom.Element
            if (folder.getAttribute("ignorePerms") != "true") {
                folder.setAttribute("ignorePerms", "true")
                changed = true
            }
        }

        // --- Local device name ---
        if (!localDeviceId.isNullOrBlank()) {
            val childNodes = doc.documentElement.childNodes
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                if (node.nodeName == "device") {
                    val element = node as org.w3c.dom.Element
                    if (element.getAttribute("id") == localDeviceId) {
                        val currentName = element.getAttribute("name")
                        if (currentName.isNullOrBlank() || currentName != Build.MODEL) {
                            element.setAttribute("name", Build.MODEL)
                            changed = true
                        }
                    }
                }
            }
        }

        if (changed) {
            saveConfig(doc)
            Log.i(TAG, "Config patched successfully")
        }
    }

    private fun parseConfig(): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // Defense-in-depth against XXE. config.xml is local but still lives
            // in a path that could in theory be tampered with via backup restore.
            // Android's bundled Harmony parser doesn't recognize all of these
            // feature URIs, so swallow ParserConfigurationException per feature
            // — the ones it DOES support still get applied.
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            // Harmony parser throws UnsupportedOperationException for these too.
            try { isXIncludeAware = false } catch (_: UnsupportedOperationException) { }
            try { isExpandEntityReferences = false } catch (_: UnsupportedOperationException) { }
        }
        val builder = factory.newDocumentBuilder()
        return configFile.inputStream().use { builder.parse(it) }
    }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (e: Exception) {
            Log.d(TAG, "XML parser doesn't support $name: ${e.message}")
        }
    }

    private fun setElement(
        doc: org.w3c.dom.Document,
        parent: org.w3c.dom.Element,
        tagName: String,
        value: String,
    ): Boolean {
        var node = parent.getElementsByTagName(tagName).item(0)
        if (node == null) {
            node = doc.createElement(tagName)
            parent.appendChild(node)
        }
        if (node.textContent != value) {
            node.textContent = value
            return true
        }
        return false
    }

    private fun saveConfig(doc: org.w3c.dom.Document) {
        val tempFile = File(configDir, "config.xml.tmp")
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        }
        tempFile.outputStream().use { out ->
            transformer.transform(DOMSource(doc), StreamResult(out))
        }
        // File.renameTo is not guaranteed to overwrite on all Android filesystems;
        // Files.move with REPLACE_EXISTING + ATOMIC_MOVE is (API 26+; minSdk is 28).
        try {
            Files.move(
                tempFile.toPath(),
                configFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move temp config to $configFile", e)
            tempFile.delete()
            error("Config save failed: ${e.message}")
        }
    }
}
