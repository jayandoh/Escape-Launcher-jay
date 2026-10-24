package com.geecee.escapelauncher.feature.appslist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geecee.escapelauncher.core.model.InstalledApp
import com.geecee.escapelauncher.core.ui.R
import com.geecee.escapelauncher.core.ui.composables.AnimatedPillSearchBar
import com.geecee.escapelauncher.core.ui.composables.HomeScreenItem

/**
 * Full screen app search opened by swiping up from the bottom of the launcher.
 *
 * The search field sits just above the keyboard and the best match is listed closest to it.
 * While nothing is listed, [onDismiss] is called when the keyboard is dismissed or the empty space is tapped,
 * so a single back press closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(
    visible: Boolean,
    searchText: String,
    results: List<InstalledApp>,
    onSearchTextChanged: (String) -> Unit,
    onSearchDone: (SoftwareKeyboardController?) -> Unit,
    onAppClick: (InstalledApp) -> Unit,
    onAppLongClick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
    showWallpaper: Boolean = false
) {
    // However the search closes, take the keyboard down with it at the same moment
    val keyboardController = LocalSoftwareKeyboardController.current
    var wasVisible by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (wasVisible && !visible) keyboardController?.hide()
        wasVisible = visible
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 10 },
        // Quick, so it finishes together with the keyboard sliding away
        exit = fadeOut(tween(durationMillis = 150)),
        modifier = modifier
    ) {
        val fieldAlignment = when (alignment) {
            Alignment.End -> Alignment.BottomEnd
            Alignment.CenterHorizontally -> Alignment.BottomCenter
            else -> Alignment.BottomStart
        }
        val showingResults = searchText.isNotBlank() && results.isNotEmpty()
        val currentShowingResults by rememberUpdatedState(showingResults)
        val currentOnDismiss by rememberUpdatedState(onDismiss)

        // The keyboard takes the first back press itself. With nothing listed there is nothing
        // to look at without it, so that press closes the search too. This reacts as soon as the
        // keyboard starts to go down, so the search fades out while the keyboard slides away.
        val imeHeadingVisible = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0
        var imeWasVisible by remember { mutableStateOf(false) }
        LaunchedEffect(imeHeadingVisible) {
            if (imeHeadingVisible) {
                imeWasVisible = true
            } else if (imeWasVisible && !currentShowingResults) {
                currentOnDismiss()
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                // Opaque, so the page underneath doesn't show through the results.
                // With the wallpaper shown, let a little of it through like the rest of the launcher.
                .background(MaterialTheme.colorScheme.background.copy(alpha = if (showWallpaper) 0.9f else 1f))
                // Keep touches from reaching the page underneath. With nothing listed, tapping closes the search.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!showingResults) onDismiss() }
                )
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (!showingResults) {
                    Text(
                        text = stringResource(
                            if (searchText.isBlank()) R.string.type_to_search_apps else R.string.no_matching_apps
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(fieldAlignment)
                            .padding(start = 30.dp, end = 30.dp, bottom = 20.dp)
                    )
                } else {
                    LazyColumn(
                        reverseLayout = true,
                        horizontalAlignment = alignment,
                        contentPadding = PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                            bottom = 10.dp
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 30.dp)
                    ) {
                        itemsIndexed(results, key = { _, app -> app.packageName }) { index, app ->
                            val color = if (showWallpaper) MaterialTheme.colorScheme.primaryFixed else MaterialTheme.colorScheme.primary
                            HomeScreenItem(
                                appName = app.displayName,
                                onAppClick = { onAppClick(app) },
                                onAppLongClick = { onAppLongClick(app) },
                                alignment = alignment,
                                shadow = showWallpaper,
                                // The best match is what Enter opens, so it stands out
                                color = if (index == 0) color else color.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp, end = 30.dp, bottom = 30.dp),
                contentAlignment = fieldAlignment
            ) {
                AnimatedPillSearchBar(
                    searchText = searchText,
                    isExpanded = true,
                    autoFocus = true,
                    onExpandedChange = {},
                    onSearchTextChanged = onSearchTextChanged,
                    onSearchDone = { _, keyboardController -> onSearchDone(keyboardController) }
                )
            }
        }
    }
}
