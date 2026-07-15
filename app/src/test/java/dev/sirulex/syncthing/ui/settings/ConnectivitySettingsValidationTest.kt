package dev.sirulex.syncthing.ui.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConnectivitySettingsValidationTest {

    @Test
    fun `line parser accepts newline and comma separated values`() {
        assertThat(parseConnectivityLines("default\nhttps://discovery.example, default"))
            .containsExactly("default", "https://discovery.example")
            .inOrder()
    }

    @Test
    fun `discovery validation requires HTTPS or default`() {
        assertThat(isValidDiscoveryServer("default")).isTrue()
        assertThat(isValidDiscoveryServer("https://discovery.example/v2/")).isTrue()
        assertThat(isValidDiscoveryServer("http://discovery.example")).isFalse()
    }

    @Test
    fun `listen validation accepts supported Syncthing schemes`() {
        assertThat(isValidListenAddress("dynamic+https://relays.syncthing.net/endpoint")).isTrue()
        assertThat(isValidListenAddress("relay://relay.example:22067/?id=ABC")).isTrue()
        assertThat(isValidListenAddress("tcp://")).isFalse()
        assertThat(isValidListenAddress("ftp://example.com")).isFalse()
    }
}
