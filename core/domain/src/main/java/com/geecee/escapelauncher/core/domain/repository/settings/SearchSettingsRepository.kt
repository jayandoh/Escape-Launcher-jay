package com.geecee.escapelauncher.core.domain.repository.settings

import kotlinx.coroutines.flow.Flow

interface SearchSettingsRepository {
    val showSearchBox: Flow<Boolean>
    suspend fun setShowSearchBox(enabled: Boolean)
    val searchAutoOpen: Flow<Boolean>
    suspend fun setSearchAutoOpen(enabled: Boolean)
    val automaticallyOpenAppsInSearch: Flow<Boolean>
    suspend fun setAutomaticallyOpenAppsInSearch(enabled: Boolean)
    val showHiddenAppsInSearch: Flow<Boolean>
    suspend fun setShowHiddenAppsInSearch(enabled: Boolean)
    val swipeUpToSearch: Flow<Boolean>
    suspend fun setSwipeUpToSearch(enabled: Boolean)
    /** Height of the swipe up area above the gesture strip in dp, or null for the default */
    val swipeUpAreaHeightDp: Flow<Float?>
    /** Null goes back to the default */
    suspend fun setSwipeUpAreaHeightDp(heightDp: Float?)
}
