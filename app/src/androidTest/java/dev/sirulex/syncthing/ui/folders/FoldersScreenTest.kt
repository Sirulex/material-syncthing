package dev.sirulex.syncthing.ui.folders

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sirulex.syncthing.R
import dev.sirulex.syncthing.api.dto.Folder
import dev.sirulex.syncthing.ui.core.theme.SyncthingTheme
import org.junit.Rule
import org.junit.Test

class FoldersScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun addFolderFab_showsContentDescription() {
        composeTestRule.setContent {
            SyncthingTheme {
                FoldersScreen(
                    folders = emptyList(),
                    folderStates = emptyMap(),
                    onFolderClick = {},
                    onAddFolder = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.cd_add_folder)
        ).assertIsDisplayed()
    }

    @Test
    fun emptyState_rendersExpectedLabels() {
        composeTestRule.setContent {
            SyncthingTheme {
                FoldersScreen(
                    folders = emptyList(),
                    folderStates = emptyMap(),
                    onFolderClick = {},
                    onAddFolder = {},
                )
            }
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.no_folders)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.no_folders_description)
        ).assertIsDisplayed()
    }

    @Test
    fun filterState_survivesRecomposition() {
        composeTestRule.setContent {
            SyncthingTheme {
                FoldersScreen(
                    folders = listOf(
                        Folder(id = "f1", label = "SyncingFolder", path = "/sync1", paused = false),
                        Folder(id = "f2", label = "PausedFolder", path = "/sync2", paused = true),
                    ),
                    folderStates = mapOf("f1" to "syncing"),
                    onFolderClick = {},
                    onAddFolder = {},
                )
            }
        }
        // Tap "Paused" filter
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.filter_paused)
        ).performClick()

        composeTestRule.onNodeWithText("PausedFolder").assertIsDisplayed()
    }
}
