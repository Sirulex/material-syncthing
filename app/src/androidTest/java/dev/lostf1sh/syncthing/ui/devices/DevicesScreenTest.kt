package dev.lostf1sh.syncthing.ui.devices

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.lostf1sh.syncthing.R
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.ui.core.theme.SyncthingTheme
import org.junit.Rule
import org.junit.Test

class DevicesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun addDeviceFab_hidden_whenOnAddDeviceIsNull() {
        composeTestRule.setContent {
            SyncthingTheme {
                DevicesScreen(
                    devices = emptyList(),
                    connections = emptyMap(),
                    onDeviceClick = {},
                    onAddDevice = null,
                )
            }
        }
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.cd_add_device)
        ).assertDoesNotExist()
    }

    @Test
    fun addDeviceFab_shown_whenOnAddDeviceIsSet() {
        composeTestRule.setContent {
            SyncthingTheme {
                DevicesScreen(
                    devices = emptyList(),
                    connections = emptyMap(),
                    onDeviceClick = {},
                    onAddDevice = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.cd_add_device)
        ).assertIsDisplayed()
    }

    @Test
    fun emptyState_showsExpectedLabels() {
        composeTestRule.setContent {
            SyncthingTheme {
                DevicesScreen(
                    devices = emptyList(),
                    connections = emptyMap(),
                    onDeviceClick = {},
                    onAddDevice = {},
                )
            }
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.no_devices)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.no_devices_description)
        ).assertIsDisplayed()
    }

    @Test
    fun filterState_survivesRecomposition() {
        composeTestRule.setContent {
            SyncthingTheme {
                DevicesScreen(
                    devices = listOf(
                        Device(deviceID = "d1", name = "OnlineDevice"),
                        Device(deviceID = "d2", name = "OfflineDevice"),
                    ),
                    connections = mapOf("d1" to true, "d2" to false),
                    onDeviceClick = {},
                    onAddDevice = {},
                )
            }
        }
        // Initially "All" shows both devices
        composeTestRule.onNodeWithText("OnlineDevice").assertIsDisplayed()
        composeTestRule.onNodeWithText("OfflineDevice").assertIsDisplayed()

        // Tap "Offline" filter
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.filter_offline)
        ).performClick()

        // Only offline device should be visible
        composeTestRule.onNodeWithText("OnlineDevice").assertIsNotDisplayed()
        composeTestRule.onNodeWithText("OfflineDevice").assertIsDisplayed()
    }
}
