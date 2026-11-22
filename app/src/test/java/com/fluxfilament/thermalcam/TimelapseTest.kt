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

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelapseTest {

    private fun frame(level: Int, w: Int = 80, h: Int = 60) =
        ThermalFrame(w, h, IntArray(w * h) { level }, level, level, FrameStatus("{}"), null)

    @Test
    fun takesOneFrameAnInterval() {
        val t = Timelapse(intervalMillis = 5000)
        assertTrue(t.offer(0, frame(7000)))
        assertFalse(t.offer(4000, frame(7000)))
        assertTrue(t.offer(5000, frame(7000)))
        assertEquals(2, t.count)
    }

    @Test
    fun stopsAtTheCapAndIgnoresAnotherSensorSize() {
        val t = Timelapse(intervalMillis = 1, maxFrames = 2)
        assertTrue(t.offer(0, frame(1)))
        assertFalse(t.offer(10, frame(1, 160, 120)))
        assertTrue(t.offer(20, frame(1)))
        assertFalse(t.offer(30, frame(1)))
        assertTrue(t.full)
    }

    @Test
    fun theGifSharesOneScaleAcrossFrames() {
        // A cold frame and a hot one: on one scale they come out at the two ends of
        // the palette, not both at its middle as auto-gain per frame would make them.
        val frames = listOf(
            Timelapse.Frame(0, 80, 60, IntArray(80 * 60) { 7000 }),
            Timelapse.Frame(1, 80, 60, IntArray(80 * 60) { 8000 }),
        )
        val bytes = Timelapse.gif(frames, Palette.GRAYSCALE, rotation = 90, mirrored = false)
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        val a = reader.read(0)
        val b = reader.read(1)
        assertEquals(240, a.width) // turned: 60 wide, blown up 4x
        assertEquals(320, a.height)
        assertEquals(0x000000, a.getRGB(10, 10) and 0xFFFFFF)
        assertEquals(0xFFFFFF, b.getRGB(10, 10) and 0xFFFFFF)
    }

    @Test
    fun aRunOfOneFrameIsNotWorthSaving() {
        val t = Timelapse(intervalMillis = 1000)
        assertFalse(t.worthSaving)
        t.offer(0, frame(1))
        assertFalse(t.worthSaving)
        t.offer(1000, frame(2))
        assertTrue(t.worthSaving)
    }
}
