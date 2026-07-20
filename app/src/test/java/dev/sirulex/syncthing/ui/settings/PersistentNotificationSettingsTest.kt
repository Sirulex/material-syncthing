package dev.sirulex.syncthing.ui.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PersistentNotificationSettingsTest {

    @Test
    fun `persistent notification is enabled only when app and channel allow it`() {
        assertThat(
            persistentNotificationIsEnabled(
                appNotificationsEnabled = true,
                channelBlocked = false,
            )
        ).isTrue()
        assertThat(
            persistentNotificationIsEnabled(
                appNotificationsEnabled = false,
                channelBlocked = false,
            )
        ).isFalse()
        assertThat(
            persistentNotificationIsEnabled(
                appNotificationsEnabled = true,
                channelBlocked = true,
            )
        ).isFalse()
    }
}
