package com.geecee.escapelauncher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.geecee.escapelauncher.core.common.DefaultSettings
import com.geecee.escapelauncher.feature.appslist.AppsListViewModel
import com.geecee.escapelauncher.feature.appslist.SwipeUpAreaEditor
import com.geecee.escapelauncher.feature.homescreen.HomeScreen
import kotlinx.coroutines.flow.map

/** The saved swipe up area height, once it has loaded */
private sealed interface SavedHeight {
    data object Loading : SavedHeight
    data class Loaded(val heightDp: Float?) : SavedHeight
}

/**
 * Adjusting the swipe up area, opened from settings: the real home page with the editor over it.
 *
 * It sits on top of settings, so Done and Back ([onClose]) return to settings where it was left.
 */
@Composable
fun SwipeUpAreaEditScreen(
    mainPagerViewModel: MainPagerScreenViewModel,
    onClose: () -> Unit,
    appsListViewModel: AppsListViewModel = hiltViewModel()
) {
    val hapticFeedbackEnabled by mainPagerViewModel.hapticFeedBackEnabled.collectAsState(initial = DefaultSettings.HAPTIC_FEEDBACK)
    // Wait for the real value, so the editor never starts at the default and then jumps
    val saved by remember { appsListViewModel.swipeUpAreaHeightDpUpdates.map { SavedHeight.Loaded(it) } }
        .collectAsState(initial = SavedHeight.Loading)

    Box(Modifier.fillMaxSize()) {
        // The editor covers it and takes every touch, so nothing on it can be opened while editing
        HomeScreen()

        (saved as? SavedHeight.Loaded)?.let { loaded ->
            SwipeUpAreaEditor(
                savedHeightDp = loaded.heightDp,
                hapticFeedbackEnabled = hapticFeedbackEnabled,
                onDone = { heightDp ->
                    appsListViewModel.setSwipeUpAreaHeightDp(heightDp)
                    onClose()
                },
                onCancel = onClose
            )
        }
    }
}
