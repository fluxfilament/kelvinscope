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

import java.io.ByteArrayOutputStream

/**
 * A minimal animated GIF89a writer: one global 256-colour table, frames of palette
 * indices, a fixed delay, looping forever.
 *
 * Enough for a time-lapse, because a thermal frame already is a picture in at most
 * 256 colours - the palette's lookup table - so nothing is lost to colour reduction,
 * and no library is needed for it. LZW as the format specifies, variable code width
 * from 9 to 12 bits with a clear code when the table fills.
 */
class GifEncoder(
    private val width: Int,
    private val height: Int,
    /** 256 ARGB colours; alpha is ignored. */
    palette: IntArray,
    /** Per frame, in hundredths of a second. */
    private val delayCs: Int,
) {
    private val out = ByteArrayOutputStream()

    init {
        require(palette.size == 256) { "a 256-colour table" }
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        short(width)
        short(height)
        out.write(0xF7) // global table, 8 bits of colour resolution, 256 entries
        out.write(0) // background colour index
        out.write(0) // pixel aspect ratio
        for (c in palette) {
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }
        // NETSCAPE2.0: loop forever.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
    }

    /** Adds a frame of [width] x [height] palette indices, row by row. */
    fun addFrame(indices: ByteArray) {
        require(indices.size == width * height)
        // Graphic control extension: the delay.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x00))
        short(delayCs)
        out.write(byteArrayOf(0x00, 0x00))
        // Image descriptor: the whole canvas, no local table.
        out.write(0x2C)
        short(0)
        short(0)
        short(width)
        short(height)
        out.write(0)
        out.write(MIN_CODE_SIZE)
        writeSubBlocks(lzw(indices))
    }

    fun finish(): ByteArray {
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun short(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun writeSubBlocks(data: ByteArray) {
        var at = 0
        while (at < data.size) {
            val n = minOf(255, data.size - at)
            out.write(n)
            out.write(data, at, n)
            at += n
        }
        out.write(0)
    }

    private fun lzw(indices: ByteArray): ByteArray {
        val clear = 1 shl MIN_CODE_SIZE
        val end = clear + 1
        val bits = BitWriter()
        val table = HashMap<Int, Int>(8192)
        var codeSize = MIN_CODE_SIZE + 1
        var nextCode = end + 1
        bits.write(clear, codeSize)
        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val k = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = table[key]
            if (found != null) {
                prefix = found
                continue
            }
            bits.write(prefix, codeSize)
            if (nextCode < MAX_CODES) {
                table[key] = nextCode++
                // The decoder widens one code later than the table grows past the width.
                if (nextCode > (1 shl codeSize) && codeSize < MAX_BITS) codeSize++
            } else {
                bits.write(clear, codeSize)
                table.clear()
                codeSize = MIN_CODE_SIZE + 1
                nextCode = end + 1
            }
            prefix = k
        }
        bits.write(prefix, codeSize)
        bits.write(end, codeSize)
        return bits.bytes()
    }

    /** LSB-first bit packing, as GIF wants it. */
    private class BitWriter {
        private val buf = ByteArrayOutputStream()
        private var acc = 0L
        private var n = 0

        fun write(code: Int, size: Int) {
            acc = acc or (code.toLong() shl n)
            n += size
            while (n >= 8) {
                buf.write((acc and 0xFF).toInt())
                acc = acc ushr 8
                n -= 8
            }
        }

        fun bytes(): ByteArray {
            if (n > 0) buf.write((acc and 0xFF).toInt())
            return buf.toByteArray()
        }
    }

    private companion object {
        const val MIN_CODE_SIZE = 8
        const val MAX_BITS = 12
        const val MAX_CODES = 4096
    }
}
