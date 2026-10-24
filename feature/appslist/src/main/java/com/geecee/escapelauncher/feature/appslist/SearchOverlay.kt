package com.geecee.escapelauncher.feature.appslist

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.ime
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
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
    showWallpaper: Boolean = false,
    openDrag: SearchOpenDrag? = null
) {
    // However the search closes, take the keyboard down with it at the same moment
    val keyboardController = LocalSoftwareKeyboardController.current
    var wasVisible by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (wasVisible && !visible) {
            openDrag?.cancel()
            keyboardController?.hide()
        }
        wasVisible = visible
    }

    AnimatedVisibility(
        visible = visible,
        // SPIKE: when opened by a swipe, the keyboard and fade follow the finger instead
        enter = if (openDrag?.active == true) EnterTransition.None else fadeIn() + slideInVertically { it / 10 },
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

        // SPIKE: dragging anywhere on the search moves the keyboard with the finger (Android 11+)
        val listState = rememberLazyListState()
        val openingByDrag = remember { openDrag?.active == true }
        val openFailed = openDrag?.failed == true
        val dragging = listState.isScrollInProgress || (openDrag?.active == true && !openFailed)
        val ime = WindowInsets.ime
        val density = LocalDensity.current
        var fullImeHeight by remember { mutableIntStateOf(0) }
        val imeBottom = ime.getBottom(density)
        LaunchedEffect(imeBottom) { if (imeBottom > fullImeHeight) fullImeHeight = imeBottom }
        LaunchedEffect(imeBottom, imeHeadingVisible, dragging) {
            Log.d("SpikeDrag", "ime=$imeBottom/$fullImeHeight headingVisible=$imeHeadingVisible dragging=$dragging")
        }

        // SPIKE: a drag lasts until the keyboard comes to rest after the finger lifts.
        // While it lasts, where the keyboard is heading isn't reliable, so only its resting place counts.
        var dragSession by remember { mutableStateOf(false) }
        val currentDragSession by rememberUpdatedState(dragSession)
        // Once a drag has faded the search out and it's closing, it stays invisible
        var fadedOutByDrag by remember { mutableStateOf(false) }
        LaunchedEffect(visible) { if (visible) fadedOutByDrag = false }
        LaunchedEffect(dragging) { if (dragging) dragSession = true }
        LaunchedEffect(openFailed) {
            // Android wouldn't let the finger move the keyboard, so open it the usual way
            if (openFailed) keyboardController?.show()
        }
        LaunchedEffect(dragSession, dragging, imeBottom, openFailed) {
            if (openFailed) dragSession = false
            if (!dragSession || dragging) return@LaunchedEffect
            if (imeBottom == 0) {
                if (!currentShowingResults) {
                    fadedOutByDrag = true
                    currentOnDismiss()
                }
                dragSession = false
            } else if (imeBottom >= fullImeHeight) {
                dragSession = false
            }
        }

        LaunchedEffect(imeHeadingVisible) {
            if (imeHeadingVisible) {
                imeWasVisible = true
            } else if (imeWasVisible && !currentDragSession && !currentShowingResults) {
                currentOnDismiss()
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                // SPIKE: with nothing listed, the search fades with the keyboard while it's dragged
                .graphicsLayer {
                    val shownHeight = openDrag?.shownHeight ?: 0
                    alpha = when {
                        fadedOutByDrag -> 0f
                        openDrag?.active == true && !openDrag.failed ->
                            if (shownHeight > 0) (ime.getBottom(this).toFloat() / shownHeight).coerceIn(0f, 1f) else 0f
                        dragSession && !showingResults && fullImeHeight > 0 ->
                            (ime.getBottom(this).toFloat() / fullImeHeight).coerceIn(0f, 1f)
                        else -> 1f
                    }
                }
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
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    horizontalAlignment = alignment,
                    contentPadding = PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                        bottom = 10.dp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .imeNestedScroll()
                        .padding(horizontal = 30.dp)
                ) {
                    if (showingResults) {
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
                    showKeyboardOnFocus = !openingByDrag,
                    onFocused = { openDrag?.attach() },
                    onExpandedChange = {},
                    onSearchTextChanged = onSearchTextChanged,
                    onSearchDone = { _, keyboardController -> onSearchDone(keyboardController) }
                )
            }
        }
    }
}
