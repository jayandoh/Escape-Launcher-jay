package com.geecee.escapelauncher

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.geecee.escapelauncher.core.common.DefaultSettings
import com.geecee.escapelauncher.core.domain.managedprofiles.ManagedProfileType
import com.geecee.escapelauncher.core.model.InstalledApp
import com.geecee.escapelauncher.core.ui.DefaultSettingsUi
import com.geecee.escapelauncher.core.ui.composables.HomeScreenBottomSheet
import com.geecee.escapelauncher.core.ui.composables.OpenChallenge
import com.geecee.escapelauncher.core.ui.composables.TabDisplay
import com.geecee.escapelauncher.core.ui.composables.TabbedScreen
import com.geecee.escapelauncher.core.ui.utils.doHapticFeedBack
import com.geecee.escapelauncher.core.ui.utils.swipeUpFromBottom
import com.geecee.escapelauncher.feature.appslist.AppsList
import com.geecee.escapelauncher.feature.appslist.AppsListViewModel
import com.geecee.escapelauncher.feature.appslist.SearchOpenDrag
import com.geecee.escapelauncher.feature.appslist.SearchOverlay
import com.geecee.escapelauncher.feature.homescreen.HomeScreen
import com.geecee.escapelauncher.feature.screentime.ScreenTimeDashboard
import com.geecee.escapelauncher.feature.screentime.ScreenTimeViewModel
import com.geecee.escapelauncher.feature.securefolder.SecureFolderButton
import com.geecee.escapelauncher.feature.securefolder.canUseSecureFolder
import com.geecee.escapelauncher.feature.workapps.WorkApps
import com.geecee.escapelauncher.privatespace.PrivateSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 *  Main composable for home screen:
 *  contains a pager with all the pages inside of it, contains bottom sheet, contains open challenge UI
 */
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class
)
@Composable
fun MainPagerScreen(
    viewModel: MainPagerScreenViewModel = hiltViewModel(),
    globalViewModel: GlobalViewModel = hiltViewModel(LocalActivity.current as ComponentActivity),
    appsListViewModel: AppsListViewModel = hiltViewModel(),
    screenTimeViewModel: ScreenTimeViewModel = hiltViewModel(LocalActivity.current as ComponentActivity),
    onOpenSettings: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val hideScreenTimePage by viewModel.hideScreenTimePage.collectAsState()
    val doubleTapToLock by viewModel.doubleTapToLock.collectAsState(initial = DefaultSettings.DOUBLE_TAP_TO_LOCK)
    val hapticFeedbackEnabled by viewModel.hapticFeedBackEnabled.collectAsState(initial = DefaultSettings.HAPTIC_FEEDBACK)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Make it that if you go back you go back to main page
    BackHandler(enabled = true) {
        coroutineScope.launch {
            viewModel.animatedGoToMainPage()
        }
    }

    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsState()

    // Search
    val searchExpanded by appsListViewModel.searchExpanded.collectAsState()
    val appsListAlignment by appsListViewModel.appsAlignment.collectAsState(initial = DefaultSettingsUi.APPS_ALIGNMENT)
    val showWallpaper by appsListViewModel.showWallpaper.collectAsState(initial = DefaultSettings.SHOW_WALLPAPER)
    val swipeUpToSearch by appsListViewModel.swipeUpToSearch.collectAsState(initial = DefaultSettings.SWIPE_UP_TO_SEARCH)
    val searchOverlayOpen by appsListViewModel.searchOverlayOpen.collectAsState()
    val searchOverlayText by appsListViewModel.searchOverlayText.collectAsState()
    val searchOverlayResults by appsListViewModel.searchOverlayResults.collectAsState()
    // SPIKE: lets the opening swipe pull the keyboard up with the finger
    val view = LocalView.current
    val searchOpenDrag = remember(view) { SearchOpenDrag(view, coroutineScope) }

    // Opens an app picked from the apps list or either search, then clears both searches
    val openAppAndClearSearch: (InstalledApp) -> Unit = { app ->
        viewModel.openApp(
            app = app, overrideChallenge = false, onAppOpened = {
                screenTimeViewModel.onAppOpened(it)
                appsListViewModel.onSearchExpandedChanged(false)
                appsListViewModel.closeSearchOverlay()
                doHapticFeedBack(haptics, hapticFeedbackEnabled)
            })
    }
    val currentOpenAppAndClearSearch by rememberUpdatedState(openAppAndClearSearch)

    // While the search overlay is open, back closes it and stays on the same page.
    // The keyboard handles the first back press itself.
    BackHandler(enabled = searchOverlayOpen) {
        appsListViewModel.closeSearchOverlay()
    }

    // Quick search: open the app as soon as a search narrows down to it
    LaunchedEffect(Unit) {
        appsListViewModel.quickSearchMatches.collect { app -> currentOpenAppAndClearSearch(app) }
    }

    // Pressing home closes the search overlay
    LaunchedEffect(Unit) {
        globalViewModel.navigateHomeEvent.collect { appsListViewModel.closeSearchOverlay() }
    }

    // Turning the setting off closes the search overlay
    LaunchedEffect(swipeUpToSearch) {
        if (!swipeUpToSearch) appsListViewModel.closeSearchOverlay()
    }

    val appsListTabs = listOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && canUseSecureFolder(context = context)) {
            TabbedScreen(
                title = "Secure Folder", icon = Icons.Default.Lock, content = {
                    SecureFolderButton(
                        modifier = Modifier.fillMaxSize()
                    )
                })
        } else {
            null
        },
        if (isDefaultLauncher && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && viewModel.managedProfileExists(
                ManagedProfileType.PrivateSpace
            )
        ) {
            TabbedScreen(
                title = "Private", icon = Icons.Default.Lock, content = {
                    PrivateSpace(
                        modifier = Modifier
                            .fillMaxSize(),
                        onAppClick = { app ->
                            viewModel.openApp(
                                app = app, overrideChallenge = false, onAppOpened = {
                                    screenTimeViewModel.onAppOpened(it)
                                    appsListViewModel.onSearchExpandedChanged(false)
                                    doHapticFeedBack(haptics, hapticFeedbackEnabled)
                                })
                        },
                        onAppLongClick = { app ->
                            appsListViewModel.setBottomSheetVisible(true)
                            appsListViewModel.setBottomSheetApp(app)
                            doHapticFeedBack(haptics, hapticFeedbackEnabled)
                        })

                })
        } else {
            null
        },
        if (viewModel.isManagedProfileSupported(type = ManagedProfileType.WorkApps) && viewModel.managedProfileExists(
                type = ManagedProfileType.WorkApps
            )
        ) {
            TabbedScreen(
                title = "Work", icon = Icons.Default.Work, content = {
                    WorkApps(modifier =
                        Modifier.fillMaxSize(),
                        onAppClick = { app ->
                        viewModel.openApp(
                            app = app, overrideChallenge = false, onAppOpened = {
                                screenTimeViewModel.onAppOpened(it)
                                appsListViewModel.onSearchExpandedChanged(false)
                                doHapticFeedBack(haptics, hapticFeedbackEnabled)
                            })
                    },
                        onAppLongClick = { app ->
                        appsListViewModel.setBottomSheetVisible(true)
                        appsListViewModel.setBottomSheetApp(app)
                        doHapticFeedBack(haptics, hapticFeedbackEnabled)
                    })
                })
        } else {
            null
        }
    )

    val screenTimePageIndex = if (!hideScreenTimePage) 0 else -1
    val homePageIndex = if (hideScreenTimePage) 0 else 1
    val appsListPageIndex = if (hideScreenTimePage) 1 else 2

    val isAppsListVisible = viewModel.pagerState.currentPage == appsListPageIndex
    val autoOpenSearch by appsListViewModel.searchAutoOpen.collectAsState(initial = DefaultSettings.SEARCH_AUTO_OPEN)

    // Tidy up apps list when it closes or opens
    LaunchedEffect(isAppsListVisible) {
        if (!isAppsListVisible) {
            appsListViewModel.onSearchExpandedChanged(false)
            appsListViewModel.setBottomSheetVisible(visibility = false)
        } else if (autoOpenSearch) {
            appsListViewModel.onSearchExpandedChanged(true)
        }
    }

    // Home Screen Pages, with the swipe up search above them. The swipe is followed from their shared
    // parent so it sees every touch first, including ones on a search that is still moving or fading out.
    Box(
        Modifier
            .fillMaxSize()
            .swipeUpFromBottom(
                enabled = swipeUpToSearch,
                canStartFromZone = { !searchOverlayOpen },
                takesOverAnyDrag = { searchOpenDrag.takesOverAnyDrag },
                onSwipeStart = {
                    val opening = !searchOverlayOpen
                    searchOpenDrag.begin()
                    if (opening) {
                        // The apps list search pill may be open, e.g. with "Automatically Open Search"
                        appsListViewModel.onSearchExpandedChanged(false)
                        appsListViewModel.openSearchOverlay()
                        doHapticFeedBack(haptics, hapticFeedbackEnabled)
                    }
                },
                onSwipe = { distanceUp -> searchOpenDrag.drag(distanceUp) },
                onSwipeEnd = { velocityUp -> searchOpenDrag.release(velocityUp) }
            )
    ) {
        HorizontalPager(
            state = viewModel.pagerState,
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = {},
                    onLongClickLabel = "",
                    onLongClick = {
                        onOpenSettings()
                        viewModel.setFirstTimeHelp(false)
                    },
                    indication = null,
                    interactionSource = viewModel.interactionSource,
                    onDoubleClick = {
                        // Turn screen off
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if (doubleTapToLock) {
                                viewModel.lockScreen()
                            }
                        }
                    })
        ) { page ->
            when (page) {
                screenTimePageIndex -> ScreenTimeDashboard()

                homePageIndex -> HomeScreen(onAppOpened = { app ->
                    viewModel.openApp(
                        app = app, overrideChallenge = false, onAppOpened = {
                            screenTimeViewModel.onAppOpened(it)
                        })
                }, onGoHomeRequest = { globalViewModel.requestToGoHome() })

                appsListPageIndex -> {
                    val searchText by appsListViewModel.searchText.collectAsState()
                    val showSearchBox by appsListViewModel.showSearchBox.collectAsState(initial = DefaultSettings.SHOW_SEARCH_BOX)
                    val selectedTabIndex = remember { mutableIntStateOf(0) }
                    val apps by appsListViewModel.apps.collectAsState()

                    TabDisplay(
                        screens = listOf(
                            TabbedScreen(
                                title = "All Apps",
                                icon = Icons.Rounded.Apps,
                                content = { padding ->
                                    AppsList(
                                        padding = padding,
                                        onAppOpened = { app ->
                                            openAppAndClearSearch(app)
                                        },
                                        onGoHomeRequest = {
                                            globalViewModel.requestToGoHome()
                                        },
                                        appsListViewModel = appsListViewModel,
                                        screenTimeViewModel = screenTimeViewModel
                                    )
                                }
                            )
                        ) + appsListTabs.filterNotNull(),
                        selectedTabIndex = selectedTabIndex,
                        alignment = appsListAlignment,
                        showSearch = showSearchBox,
                        searchText = searchText,
                        searchExpanded = searchExpanded,
                        onSearchExpandedChange = {
                            appsListViewModel.onSearchExpandedChanged(it)
                            doHapticFeedBack(haptics, hapticFeedbackEnabled)
                        },
                        onSearchTextChanged = { query: String ->
                            appsListViewModel.onSearchTextChanged(query)
                        },
                        onSearchDone = { _: String, keyboardController: SoftwareKeyboardController? ->
                            if (apps.isNotEmpty()) {
                                keyboardController?.hide()
                                openAppAndClearSearch(apps.first())
                            } else {
                                doHapticFeedBack(haptics, hapticFeedbackEnabled)
                            }
                        }
                    )
                }
            }
        }

        // Swipe up search
        SearchOverlay(
            visible = searchOverlayOpen,
            searchText = searchOverlayText,
            results = searchOverlayResults.apps,
            alignment = appsListAlignment,
            showWallpaper = showWallpaper,
            openDrag = searchOpenDrag,
            onSearchTextChanged = { appsListViewModel.onSearchOverlayTextChanged(it) },
            onSearchDone = { keyboardController ->
                val bestMatch = searchOverlayResults.apps.firstOrNull()
                if (searchOverlayText.isNotBlank() && bestMatch != null) {
                    keyboardController?.hide()
                    openAppAndClearSearch(bestMatch)
                } else {
                    doHapticFeedBack(haptics, hapticFeedbackEnabled)
                }
            },
            onAppClick = { app -> openAppAndClearSearch(app) },
            onDismiss = { appsListViewModel.closeSearchOverlay() },
            onAppLongClick = { app ->
                appsListViewModel.setBottomSheetVisible(true)
                appsListViewModel.setBottomSheetApp(app)
                doHapticFeedBack(haptics, hapticFeedbackEnabled)
            }
        )
    }

    // Bottom Sheet for apps from the apps list and the search overlay
    val showBottomSheet by appsListViewModel.showBottomSheet.collectAsState()
    val bottomSheetApp by appsListViewModel.bottomSheetApp.collectAsState()
    val bottomSheetActions by appsListViewModel.bottomSheetActions.collectAsState()
    val shortcutActions by appsListViewModel.shortcutActions.collectAsState()
    AnimatedVisibility(showBottomSheet && bottomSheetApp != null) {
        HomeScreenBottomSheet(
            app = bottomSheetApp!!,
            actions = bottomSheetActions,
            onDismissRequest = { appsListViewModel.setBottomSheetVisible(false) },
            shortcutActions = shortcutActions,
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        )
    }

    //Open Challenge
    AnimatedVisibility(
        visible = viewModel.showOpenChallenge.value, enter = fadeIn(), exit = fadeOut()
    ) {
        OpenChallenge(
            haptics = LocalHapticFeedback.current,
            enabled = hapticFeedbackEnabled,
            openApp = {
                viewModel.openApp(
                    app = viewModel.currentSelectedApp.value,
                    overrideChallenge = true,
                    onAppOpened = {
                        screenTimeViewModel.onAppOpened(it)
                        appsListViewModel.closeSearchOverlay()
                    })
                coroutineScope.launch {
                    delay(1000.milliseconds)
                    viewModel.showOpenChallenge.value = false
                }
            },
            goBack = {
                viewModel.showOpenChallenge.value = false
            })
    }
}
