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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureAlarmTest {

    private fun alarm(mode: TemperatureAlarm.Mode, limit: Double) =
        TemperatureAlarm(repeatMillis = 3000, hysteresisC = 1.0).apply {
            this.mode = mode
            limitC = limit
        }

    @Test
    fun soundsOnTheCrossingThenEveryRepeat() {
        val a = alarm(TemperatureAlarm.Mode.MAX_ABOVE, 60.0)
        assertFalse(a.update(59.0, 20.0, 0))
        assertTrue(a.update(60.5, 20.0, 100))
        assertFalse(a.update(61.0, 20.0, 1000))
        assertTrue(a.update(61.0, 20.0, 3100))
        assertTrue(a.active)
    }

    @Test
    fun noiseOnTheLineDoesNotChatter() {
        val a = alarm(TemperatureAlarm.Mode.MAX_ABOVE, 60.0)
        assertTrue(a.update(60.0, 20.0, 0))
        // Dips just under the limit keep it active: it has to fall a degree to clear.
        assertFalse(a.update(59.5, 20.0, 200))
        assertTrue(a.active)
        assertFalse(a.update(58.9, 20.0, 400))
        assertFalse(a.active)
        assertTrue(a.update(60.1, 20.0, 600))
    }

    @Test
    fun theColdSideWorksTheSameWay() {
        val a = alarm(TemperatureAlarm.Mode.MIN_BELOW, 5.0)
        assertFalse(a.update(30.0, 6.0, 0))
        assertTrue(a.update(30.0, 4.9, 100))
        assertFalse(a.update(30.0, 5.5, 200))
        assertTrue(a.active)
        assertFalse(a.update(30.0, 6.1, 300))
        assertFalse(a.active)
    }

    @Test
    fun offNeverSounds() {
        val a = alarm(TemperatureAlarm.Mode.OFF, 0.0)
        assertFalse(a.update(500.0, -50.0, 0))
    }
}
