/*
 * Copyright (C) 2026 Damirusnik
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fluxfilament.thermalcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading blocks of areas and the line, mostly in a 600 x 800 picture. */
class LabelPlacementTest {

    private val picture = Box(0f, 0f, 600f, 800f)

    private fun area(frame: Box, taken: List<Box> = emptyList(), line: FloatArray? = null) =
        LabelPlacement.areaBlock(frame, 120f, 60f, 4f, picture, taken, listOf(frame), line)

    @Test
    fun areaBlockGoesAboveTheFrame() {
        val b = area(Box(100f, 300f, 300f, 400f))
        assertEquals(100f, b.left, 0.01f)
        assertEquals(300f - 4f - 60f, b.top, 0.01f)
    }

    @Test
    fun areaAtTheTopEdgeHasItsBlockBelow() =
        assertEquals(204f, area(Box(100f, 20f, 300f, 200f)).top, 0.01f)

    @Test
    fun areaBlockIsPulledInFromTheRightEdge() =
        assertEquals(600f - 120f, area(Box(550f, 300f, 590f, 400f)).left, 0.01f)

    @Test
    fun areaBlockStepsAsideForASpotLabel() {
        // A spot's reading right where the block would go above the frame.
        val spot = Box(90f, 230f, 230f, 290f)
        val b = area(Box(100f, 300f, 300f, 400f), taken = listOf(spot))
        assertEquals(0f, b.grown(4f).overlap(spot), 0.01f)
    }

    @Test
    fun areaBlockKeepsOffTheLine() {
        val line = floatArrayOf(150f, 0f, 150f, 800f)
        val b = area(Box(100f, 300f, 300f, 400f), line = line)
        assertFalse(b.crossedBy(line[0], line[1], line[2], line[3]))
    }

    @Test
    fun secondAreaBlockDoesNotLieOnTheFirst() {
        // As on the card of 2026-11-22: A1 below A2, A1's block going up into A2's.
        val a2 = Box(360f, 60f, 520f, 240f)
        val a1 = Box(380f, 380f, 540f, 560f)
        val frames = listOf(a2, a1)
        val first = LabelPlacement.areaBlock(a2, 120f, 90f, 4f, picture, emptyList(), frames, null)
        val second = LabelPlacement.areaBlock(a1, 120f, 90f, 4f, picture, listOf(first), frames, null)
        assertEquals(0f, second.overlap(first), 0.01f)
    }

    private fun line(ax: Float, ay: Float, bx: Float, by: Float, w: Float = 130f, h: Float = 70f, bounds: Box = picture) =
        LabelPlacement.lineBlock(ax, ay, bx, by, w, h, 4f, bounds, emptyList())

    @Test
    fun horizontalLineHasItsBlockAbove() {
        val b = line(100f, 400f, 500f, 400f)
        assertTrue(b.bottom < 400f)
        assertFalse(b.crossedBy(100f, 400f, 500f, 400f))
    }

    @Test
    fun uprightLineHasItsBlockBeside() {
        val b = line(300f, 100f, 300f, 700f)
        assertTrue(b.left >= 304f - 0.01f || b.right <= 296f + 0.01f)
    }

    @Test
    fun blockTooWideForEitherSideGoesPastAnEnd() {
        // The card of 2026-11-22: an upright line down the middle of a narrow picture.
        val narrow = Box(0f, 0f, 300f, 800f)
        val b = line(150f, 200f, 150f, 700f, w = 200f, bounds = narrow)
        assertFalse(b.grown(3.9f).crossedBy(150f, 200f, 150f, 700f))
    }

    /** How close the nearest corner of [b] comes to the line through a and b. */
    private fun clearance(b: Box, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val len = kotlin.math.hypot(bx - ax, by - ay)
        return listOf(b.left to b.top, b.right to b.top, b.left to b.bottom, b.right to b.bottom)
            .minOf { (x, y) -> kotlin.math.abs((x - ax) * (by - ay) - (y - ay) * (bx - ax)) / len }
    }

    @Test
    fun slantedLineIsNotStruckThroughItsBlock() {
        // The cards of 2026-11-22: a long line at about 75 degrees.
        val b = line(400f, 235f, 253f, 853f, bounds = Box(0f, 0f, 720f, 960f))
        assertFalse(b.crossedBy(400f, 235f, 253f, 853f))
        assertTrue(clearance(b, 400f, 235f, 253f, 853f) >= 4f - 0.01f)
    }

    @Test
    fun lineAlongTheTopEdgeHasItsBlockBelow() {
        val b = line(100f, 10f, 500f, 10f)
        assertTrue(b.top > 10f)
    }

    @Test
    fun lineBlockStepsAsideForASpotLabel() {
        val spot = Box(300f, 300f, 460f, 360f)
        val b = LabelPlacement.lineBlock(300f, 100f, 300f, 700f, 130f, 70f, 4f, picture, listOf(spot))
        assertEquals(0f, b.overlap(spot), 0.01f)
        assertFalse(b.crossedBy(300f, 100f, 300f, 700f))
    }

    @Test
    fun lineInsideASmallAreaHasItsBlockPastAnEnd() {
        // The second card of 2026-11-22: a short line inside a small area. Beside
        // the line the block covers half the area; past the bottom end, a sliver.
        val frame = Box(250f, 200f, 380f, 340f)
        val b = LabelPlacement.lineBlock(330f, 220f, 330f, 330f, 130f, 70f, 4f, picture, emptyList(), listOf(frame))
        assertTrue(b.top > 330f)
        assertTrue(b.overlap(frame) < 130f * 70f / 4f)
        assertFalse(b.crossedBy(330f, 220f, 330f, 330f))
    }

    @Test
    fun segmentAndBox() {
        val box = Box(10f, 10f, 20f, 20f)
        assertTrue(box.crossedBy(0f, 15f, 30f, 15f))
        assertTrue(box.crossedBy(15f, 15f, 40f, 40f))
        assertFalse(box.crossedBy(0f, 0f, 30f, 5f))
        assertFalse(box.crossedBy(0f, 25f, 25f, 40f))
    }
}
