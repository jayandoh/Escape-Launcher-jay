package com.geecee.escapelauncher.feature.homescreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ShrinkingMarginsArrangementTest {

    // 1 px per dp, so margins in dp read as px
    private val density = Density(1f)

    private fun positions(arrangement: Arrangement.Vertical, totalSize: Int, vararg sizes: Int): IntArray {
        val out = IntArray(sizes.size)
        with(ShrinkingMarginsArrangement(arrangement, topMargin = 60.dp, bottomMargin = 80.dp)) {
            density.arrange(totalSize, sizes, out)
        }
        return out
    }

    @Test
    fun `with room, centered content is centered between the margins`() {
        // 1000 - 60 - 80 leaves 860 to center 200 in
        assertArrayEquals(intArrayOf(390, 490), positions(Arrangement.Center, 1000, 100, 100))
    }

    @Test
    fun `with room, top content starts below the top margin`() {
        assertArrayEquals(intArrayOf(60, 160), positions(Arrangement.Top, 1000, 100, 100))
    }

    @Test
    fun `with room, bottom content ends above the bottom margin`() {
        assertArrayEquals(intArrayOf(720, 820), positions(Arrangement.Bottom, 1000, 100, 100))
    }

    @Test
    fun `without room for both margins, they shrink in proportion`() {
        // Only 70 of the wanted 140 is free, so each margin gets half
        assertArrayEquals(intArrayOf(30, 495), positions(Arrangement.Top, 1000, 465, 465))
    }

    @Test
    fun `content that exactly fills the space gets no margins`() {
        assertArrayEquals(intArrayOf(0, 500), positions(Arrangement.Center, 1000, 500, 500))
    }

    @Test
    fun `negative margins are treated as none`() {
        val out = IntArray(2)
        with(ShrinkingMarginsArrangement(Arrangement.Top, topMargin = (-10).dp, bottomMargin = 0.dp)) {
            density.arrange(1000, intArrayOf(100, 100), out)
        }
        assertArrayEquals(intArrayOf(0, 100), out)
    }
}
