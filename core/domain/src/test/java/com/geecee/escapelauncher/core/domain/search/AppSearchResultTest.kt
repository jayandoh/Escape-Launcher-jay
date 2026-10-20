package com.geecee.escapelauncher.core.domain.search

import com.geecee.escapelauncher.core.domain.repository.android.AppsRepository
import com.geecee.escapelauncher.core.domain.repository.db.ModifiedAppsRepository
import com.geecee.escapelauncher.core.model.InstalledApp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSearchResultTest {

    private fun app(name: String): InstalledApp = mockk {
        every { displayName } returns name
        every { packageName } returns "pkg.${name.lowercase()}"
    }

    private val spotify = app("Spotify")
    private val speedtest = app("Speedtest")
    private val maps = app("Maps")

    @Test
    fun `results carry the query they were computed for`() = runTest {
        val appsRepository = mockk<AppsRepository> {
            every { mainUserApps } returns MutableStateFlow(listOf(spotify, speedtest, maps))
        }
        val modifiedAppsRepository = mockk<ModifiedAppsRepository> {
            every { getHiddenPackageIdsFlow() } returns flowOf(emptyList())
        }
        val useCase = SearchAppsUseCase(appsRepository, modifiedAppsRepository)

        val result = useCase.withQuery(flowOf("spot"), flowOf(false)).first()

        assertEquals("spot", result.query)
        assertEquals(listOf(spotify), result.apps)
    }

    @Test
    fun `quick search match is the only app for a query of two or more characters`() {
        assertEquals(spotify, AppSearchResult("sp", listOf(spotify)).quickSearchMatch())
    }

    @Test
    fun `quick search ignores surrounding spaces when counting characters`() {
        assertNull(AppSearchResult(" s ", listOf(spotify)).quickSearchMatch())
    }

    @Test
    fun `quick search does not match a single character query`() {
        assertNull(AppSearchResult("s", listOf(spotify)).quickSearchMatch())
    }

    @Test
    fun `quick search does not match when several apps match`() {
        assertNull(AppSearchResult("sp", listOf(spotify, speedtest)).quickSearchMatch())
    }

    @Test
    fun `quick search does not match when nothing matches`() {
        assertNull(AppSearchResult("spx", emptyList()).quickSearchMatch())
    }
}
