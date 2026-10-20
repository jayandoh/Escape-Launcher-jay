package com.geecee.escapelauncher.core.domain.search

import com.geecee.escapelauncher.core.model.InstalledApp

/**
 * The apps matching a search, paired with the exact query they were computed for
 */
data class AppSearchResult(
    val query: String,
    val apps: List<InstalledApp>
)

/**
 * The app "Quick search" should open for this result: the only match for a query of two or more characters
 */
fun AppSearchResult.quickSearchMatch(): InstalledApp? =
    if (query.trim().length >= 2) apps.singleOrNull() else null
