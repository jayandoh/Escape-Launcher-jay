package com.geecee.escapelauncher.feature.appslist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
 * The swipe that opens it drives the keyboard and the fade through [keyboardDrag], and dragging anywhere on
 * the search moves the keyboard with the finger too.
 * While nothing is listed, [onDismiss] is called when the keyboard goes away or the empty space is tapped,
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
    keyboardDrag: SearchKeyboardDrag,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
    showWallpaper: Boolean = false
) {
    // However the search closes, take the keyboard down with it at the same moment,
    // unless a new swipe has already taken over to reopen it
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var wasVisible by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (wasVisible && !visible && !keyboardDrag.fingerDown) {
            keyboardDrag.close()
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
        wasVisible = visible
    }
    keyboardDrag.overlayState.targetState = visible

    AnimatedVisibility(
        visibleState = keyboardDrag.overlayState,
        // Where the keyboard can follow the finger, the swipe itself fades the search in
        enter = if (SearchKeyboardDrag.isSupported) EnterTransition.None else fadeIn() + slideInVertically { it / 10 },
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

        val openFailed = keyboardDrag.failed
        // A finger, or the keyboard settling after one, is moving the keyboard
        val dragging = keyboardDrag.active && !openFailed
        val currentDragging by rememberUpdatedState(dragging)
        DisposableEffect(Unit) { onDispose { keyboardDrag.onFieldGone() } }

        val ime = WindowInsets.ime
        val imeBottom = ime.getBottom(LocalDensity.current)
        val imeHeadingVisible = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0
        var imeWasVisible by remember { mutableStateOf(false) }
        var fullImeHeight by remember { mutableIntStateOf(0) }
        LaunchedEffect(imeBottom) { if (imeBottom > fullImeHeight) fullImeHeight = imeBottom }

        LaunchedEffect(openFailed) {
            // Android wouldn't let the finger move the keyboard, so show it the usual way
            if (openFailed) keyboardController?.show()
        }

        // A drag lasts until the keyboard comes to rest after the finger lifts. While it lasts, where the
        // keyboard is heading isn't reliable, so only where it comes to rest decides whether the search closes.
        var dragSession by remember { mutableStateOf(false) }
        val currentDragSession by rememberUpdatedState(dragSession)
        // Once a drag has faded the search out and it's closing, it stays invisible
        var fadedOutByDrag by remember { mutableStateOf(false) }
        // Every new swipe starts from a clean slate, even if it caught the search on its way out
        LaunchedEffect(keyboardDrag.generation) { fadedOutByDrag = false }
        LaunchedEffect(dragging) { if (dragging) dragSession = true }
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

        // The keyboard takes the first back press itself. With nothing listed there is nothing to look at
        // without it, so that press closes the search too, as soon as the keyboard starts to go down.
        LaunchedEffect(imeHeadingVisible) {
            if (imeHeadingVisible) {
                imeWasVisible = true
            } else if (imeWasVisible && !currentDragging && !currentDragSession && !currentShowingResults) {
                currentOnDismiss()
            }
        }

        // Safety net: however it got there, an open search with nothing listed and the keyboard down
        // and at rest closes. The checks above close it sooner on the usual paths.
        val atRestWithNothing = imeWasVisible && !showingResults && imeBottom == 0 && !imeHeadingVisible && !dragging
        LaunchedEffect(atRestWithNothing) {
            if (atRestWithNothing) {
                fadedOutByDrag = true
                currentOnDismiss()
            }
        }

        // A tap on the empty space only closes the search if the finger touched down after it finished
        // opening, meaning the keyboard is fully up and still, so a stray touch while the thumb is still
        // opening it does nothing
        val imeAnimationTarget = WindowInsets.imeAnimationTarget
        val imeTarget = imeAnimationTarget.getBottom(LocalDensity.current)
        val finishedOpening = !keyboardDrag.active && keyboardDrag.overlayState.isIdle &&
            imeBottom > 0 && imeBottom == imeTarget
        val currentFinishedOpening by rememberUpdatedState(finishedOpening)
        var tapStartedAfterOpening by remember { mutableStateOf(false) }

        Column(
            Modifier
                .fillMaxSize()
                // With nothing listed, the search fades with the keyboard while it's dragged
                .graphicsLayer {
                    // Results stay fully visible while the keyboard moves under them
                    val shownHeight = keyboardDrag.shownHeight.takeIf { it > 0 } ?: fullImeHeight
                    alpha = when {
                        fadedOutByDrag -> 0f
                        showingResults -> 1f
                        keyboardDrag.active && !keyboardDrag.failed ->
                            if (shownHeight > 0) (ime.getBottom(this).toFloat() / shownHeight).coerceIn(0f, 1f) else 0f
                        dragSession && shownHeight > 0 -> (ime.getBottom(this).toFloat() / shownHeight).coerceIn(0f, 1f)
                        // The keyboard is coming up the usual way: fade in with it, rather than show the search
                        // at the bottom of the screen and then jump up above the keyboard
                        keyboardDrag.failed -> {
                            val target = imeAnimationTarget.getBottom(this)
                            if (target > 0) (ime.getBottom(this).toFloat() / target).coerceIn(0f, 1f) else 0f
                        }
                        else -> 1f
                    }
                }
                // Opaque, so the page underneath doesn't show through the results.
                // With the wallpaper shown, let a little of it through like the rest of the launcher.
                .background(MaterialTheme.colorScheme.background.copy(alpha = if (showWallpaper) 0.9f else 1f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        tapStartedAfterOpening = currentFinishedOpening
                    }
                }
                // Keep touches from reaching the page underneath. With nothing listed, tapping closes the search.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!showingResults && tapStartedAfterOpening) onDismiss() }
                )
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Always there, even when empty, so a drag anywhere can move the keyboard
                LazyColumn(
                    reverseLayout = true,
                    horizontalAlignment = alignment,
                    contentPadding = PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                        bottom = 10.dp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(keyboardDrag.nestedScrollConnection)
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
                    // Where the keyboard can follow the finger, the swipe raises it itself
                    showKeyboardOnFocus = !SearchKeyboardDrag.isSupported,
                    onFocused = { keyboardDrag.onFieldFocused() },
                    onExpandedChange = {},
                    onSearchTextChanged = onSearchTextChanged,
                    onSearchDone = { _, keyboardController -> onSearchDone(keyboardController) }
                )
            }
        }
    }
}
