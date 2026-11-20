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
import org.junit.Test

/** Read back with the JDK's own GIF decoder, so the encoder is checked against a real one. */
class GifEncoderTest {

    private val palette = IntArray(256) { v -> (0xFF shl 24) or (v shl 16) or ((255 - v) shl 8) or (v / 2) }

    private fun frames(bytes: ByteArray): List<java.awt.image.BufferedImage> {
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        return (0 until reader.getNumImages(true)).map { reader.read(it) }
    }

    @Test
    fun everyPixelOfEveryFrameComesBack() {
        val w = 80
        val h = 60
        val enc = GifEncoder(w, h, palette, delayCs = 10)
        // Noise and a gradient: plenty of distinct strings, so the table fills and clears.
        val rnd = java.util.Random(7)
        val a = ByteArray(w * h) { rnd.nextInt(256).toByte() }
        val b = ByteArray(w * h) { ((it % w) * 3).toByte() }
        enc.addFrame(a)
        enc.addFrame(b)
        val decoded = frames(enc.finish())
        assertEquals(2, decoded.size)
        for ((img, src) in decoded.zip(listOf(a, b))) {
            for (i in src.indices) {
                val expected = palette[src[i].toInt() and 0xFF] and 0xFFFFFF
                assertEquals("pixel $i", expected, img.getRGB(i % w, i / w) and 0xFFFFFF)
            }
        }
    }

    @Test
    fun aFlatFrameCompresses() {
        val enc = GifEncoder(320, 240, palette, delayCs = 10)
        enc.addFrame(ByteArray(320 * 240) { 42 })
        val bytes = enc.finish()
        assert(bytes.size < 5000) { "${bytes.size} bytes" }
        assertEquals(palette[42] and 0xFFFFFF, frames(bytes)[0].getRGB(100, 100) and 0xFFFFFF)
    }

    @Test
    fun aLargeNoisyFrameSurvivesTableResets() {
        val w = 320
        val h = 240
        val rnd = java.util.Random(11)
        val src = ByteArray(w * h) { rnd.nextInt(256).toByte() }
        val enc = GifEncoder(w, h, palette, delayCs = 10)
        enc.addFrame(src)
        val img = frames(enc.finish())[0]
        for (i in src.indices step 97) {
            assertEquals("pixel $i", palette[src[i].toInt() and 0xFF] and 0xFFFFFF, img.getRGB(i % w, i / w) and 0xFFFFFF)
        }
        assertEquals(palette[src.last().toInt() and 0xFF] and 0xFFFFFF, img.getRGB(w - 1, h - 1) and 0xFFFFFF)
    }
}
