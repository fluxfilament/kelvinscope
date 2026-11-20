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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendBufferTest {

    @Test
    fun keepsTheNewestRowsInOrder() {
        val t = TrendBuffer(capacity = 3)
        for (i in 1..5) t.add(i * 10.0, i.toDouble(), listOf(i * 2.0))
        val s = t.snapshot()
        assertArrayEquals(floatArrayOf(30f, 40f, 50f), s[0], 0f)
        assertArrayEquals(floatArrayOf(3f, 4f, 5f), s[1], 0f)
        assertArrayEquals(floatArrayOf(6f, 8f, 10f), s[2], 0f)
    }

    @Test
    fun unplacedSpotsAreLeftOut() {
        val t = TrendBuffer(capacity = 4)
        t.add(30.0, 20.0, listOf(25.0))
        t.add(31.0, 20.0, emptyList())
        val s = t.snapshot()
        assertEquals(2, s[2]!!.size)
        assertTrue(s[2]!![1].isNaN())
        assertNull(s[3])
    }

    @Test
    fun clearStartsAfresh() {
        val t = TrendBuffer()
        t.add(1.0, 0.0, emptyList())
        t.clear()
        assertNull(t.snapshot()[0])
    }
}
