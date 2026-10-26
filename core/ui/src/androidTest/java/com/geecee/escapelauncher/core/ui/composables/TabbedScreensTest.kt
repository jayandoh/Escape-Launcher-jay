package com.geecee.escapelauncher.core.ui.composables

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geecee.escapelauncher.core.theme.EscapeThemePreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabbedScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val allApps = TabbedScreen(title = "All Apps", icon = Icons.Rounded.Apps)
    private val private = TabbedScreen(title = "Private", icon = Icons.Default.Lock)

    private fun setTabDisplay(screens: List<TabbedScreen>, showSearch: Boolean) {
        composeTestRule.setContent {
            EscapeThemePreview {
                TabDisplay(
                    screens = screens,
                    selectedTabIndex = remember { mutableIntStateOf(0) },
                    showSearch = showSearch
                )
            }
        }
    }

    @Test
    fun singleTab_hidesTabButShowsSearch() {
        setTabDisplay(listOf(allApps), showSearch = true)

        composeTestRule.onNodeWithContentDescription("All Apps").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Search").assertExists()
    }

    @Test
    fun multipleTabs_showsEveryTab() {
        setTabDisplay(listOf(allApps, private), showSearch = true)

        composeTestRule.onNodeWithContentDescription("All Apps").assertExists()
        composeTestRule.onNodeWithContentDescription("Private").assertExists()
        composeTestRule.onNodeWithContentDescription("Search").assertExists()
    }

    @Test
    fun singleTabWithoutSearch_dropsSpaceReservedForBar() {
        var contentBottomPadding = Dp.Unspecified
        var navigationBarHeight = Dp.Unspecified
        composeTestRule.setContent {
            EscapeThemePreview {
                navigationBarHeight =
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                TabDisplay(
                    screens = listOf(
                        allApps.copy(content = { padding ->
                            contentBottomPadding = padding.calculateBottomPadding()
                        })
                    ),
                    selectedTabIndex = remember { mutableIntStateOf(0) },
                    showSearch = false
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("All Apps").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Search").assertDoesNotExist()
        composeTestRule.runOnIdle {
            assertEquals(navigationBarHeight + 10.dp, contentBottomPadding)
        }
    }
}
