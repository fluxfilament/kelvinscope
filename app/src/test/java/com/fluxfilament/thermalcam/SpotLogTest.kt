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

class SpotLogTest {

    @Test
    fun oneRowASecondWithFixedColumns() {
        val log = SpotLog(startedAtMillis = 10_000)
        assertTrue(log.offer(10_000, listOf(21.456, 30.0), 45.0, 18.25, 0.95))
        assertFalse(log.offer(10_400, listOf(22.0), 45.0, 18.0, 0.95))
        assertTrue(log.offer(11_050, listOf(22.0), 46.0, 18.0, 0.95))
        val lines = log.csv().trimEnd().lines()
        assertEquals(SpotLog.HEADER.trimEnd(), lines[0])
        assertEquals("0.0,21.46,30.00,,,,,,,,45.00,18.25,0.95", lines[1])
        assertEquals("1.1,22.00,,,,,,,,,46.00,18.00,0.95", lines[2])
        assertEquals(2, log.rows)
        for (line in lines) assertEquals(13, line.split(",").size)
    }

    @Test
    fun decimalsUseADotWhateverTheLocale() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val log = SpotLog(0)
            log.offer(0, listOf(1.5), 2.5, 0.5, 0.95)
            assertEquals("0.0,1.50,,,,,,,,,2.50,0.50,0.95", log.csv().trimEnd().lines()[1])
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }
}
