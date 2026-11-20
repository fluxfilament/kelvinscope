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
 * The sensor's counts of one snapshot, with everything needed to turn them back into
 * degrees, as a 16-bit PGM.
 *
 * The raw PNG beside the card is a coloured picture: palette and contrast window are
 * baked into it, and the temperatures are gone. This file keeps the measurement
 * itself, so a snapshot can be re-read later with other spots, another emissivity or
 * another palette. PGM because it is the simplest format that holds 16 bits per
 * pixel and that ImageMagick, GIMP, numpy and the rest open as is; the conversion
 * parameters ride in its header comments as `key=value`, which every reader skips.
 */
object RawCounts {

    const val EXTENSION = "pgm"
    const val MIME = "image/x-portable-graymap"

    class Decoded(val width: Int, val height: Int, val raw: IntArray, val meta: Map<String, String>)

    fun encode(width: Int, height: Int, raw: IntArray, planck: Planck, extra: Map<String, String> = emptyMap()): ByteArray {
        require(raw.size == width * height) { "${raw.size} counts for $width x $height" }
        val meta = linkedMapOf(
            "kelvinscope_counts" to "1",
            "r1" to planck.r1.toString(),
            "b" to planck.b.toString(),
            "f" to planck.f.toString(),
            "o" to planck.o.toString(),
            "r2" to planck.r2.toString(),
            "emissivity" to planck.emissivity.toString(),
            "reflected_k" to planck.reflectedTemperature.toString(),
            "window_transmission" to planck.irWindowTransmission.toString(),
            "window_k" to planck.irWindowTemperature.toString(),
            "air_k" to planck.atmosphericTemperature.toString(),
            "air_transmission" to planck.atmosphericTransmission.toString(),
            "correction_gain" to planck.correction.gain.toString(),
            "correction_offset" to planck.correction.offset.toString(),
            // The USB stream carries counts divided by four; Planck works on the full scale.
            "usb_scale" to "4",
        )
        meta.putAll(extra)
        val out = ByteArrayOutputStream(64 + raw.size * 2)
        val header = StringBuilder("P5\n")
        for ((k, v) in meta) header.append("# ").append(k).append('=').append(v.replace('\n', ' ')).append('\n')
        header.append(width).append(' ').append(height).append("\n65535\n")
        out.write(header.toString().toByteArray(Charsets.UTF_8))
        for (v in raw) {
            val c = v.coerceIn(0, 65535)
            out.write(c shr 8) // PGM is big-endian
            out.write(c and 0xFF)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Decoded {
        var at = 0
        val meta = linkedMapOf<String, String>()
        val tokens = mutableListOf<String>()
        fun line(): String {
            val start = at
            while (at < bytes.size && bytes[at] != '\n'.code.toByte()) at++
            val s = String(bytes, start, at - start, Charsets.UTF_8)
            at++
            return s
        }
        if (line() != "P5") throw IllegalArgumentException("not a binary PGM")
        while (tokens.size < 3) {
            if (at >= bytes.size) throw IllegalArgumentException("header cut short")
            val l = line()
            if (l.startsWith("#")) {
                val kv = l.removePrefix("#").trim()
                val eq = kv.indexOf('=')
                if (eq > 0) meta[kv.substring(0, eq)] = kv.substring(eq + 1)
            } else {
                tokens += l.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            }
        }
        val width = tokens[0].toInt()
        val height = tokens[1].toInt()
        require(tokens[2] == "65535") { "not a 16-bit PGM" }
        require(bytes.size - at >= width * height * 2) { "pixel data cut short" }
        val raw = IntArray(width * height) {
            ((bytes[at + 2 * it].toInt() and 0xFF) shl 8) or (bytes[at + 2 * it + 1].toInt() and 0xFF)
        }
        return Decoded(width, height, raw, meta)
    }

    /** The conversion the file was saved with, rebuilt from its header. */
    fun planckOf(meta: Map<String, String>): Planck? {
        fun d(k: String) = meta[k]?.toDoubleOrNull()
        return Planck(
            r1 = d("r1") ?: return null,
            b = d("b") ?: return null,
            f = d("f") ?: return null,
            o = d("o") ?: return null,
            r2 = d("r2") ?: return null,
            emissivity = d("emissivity") ?: return null,
            reflectedTemperature = d("reflected_k") ?: return null,
            irWindowTransmission = d("window_transmission") ?: return null,
            irWindowTemperature = d("window_k") ?: return null,
            atmosphericTemperature = d("air_k") ?: return null,
            atmosphericTransmission = d("air_transmission") ?: return null,
            correction = Correction(d("correction_gain") ?: 1.0, d("correction_offset") ?: 0.0),
        )
    }
}
