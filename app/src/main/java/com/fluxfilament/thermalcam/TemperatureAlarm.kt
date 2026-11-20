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

/**
 * Beeps when the scene crosses a temperature: the hottest place above a limit, or
 * the coldest below one. For watching something without watching the screen - a
 * battery on charge, a part under load, a soldering tip - alongside the temperature
 * log.
 *
 * Decides only when to sound; the sound itself is the caller's. It goes off on the
 * crossing and then again every [repeatMillis] while the condition holds, and it
 * clears only once the reading is back past the limit by [hysteresisC], so a value
 * sitting on the line does not chatter on and off with sensor noise.
 */
class TemperatureAlarm(
    private val repeatMillis: Long = 3000,
    private val hysteresisC: Double = 1.0,
) {
    enum class Mode { OFF, MAX_ABOVE, MIN_BELOW }

    @Volatile
    var mode: Mode = Mode.OFF
        set(value) {
            field = value
            active = false
        }

    @Volatile
    var limitC: Double = 60.0
        set(value) {
            field = value
            active = false
        }

    /** True while the condition holds; read by the UI to show it. */
    @Volatile
    var active: Boolean = false
        private set

    private var lastSoundAt = Long.MIN_VALUE

    /** Feeds one frame's extremes; true when the caller should sound now. */
    @Synchronized
    fun update(maxC: Double, minC: Double, nowMillis: Long): Boolean {
        val tripped = when (mode) {
            Mode.OFF -> false
            Mode.MAX_ABOVE -> if (active) maxC > limitC - hysteresisC else maxC >= limitC
            Mode.MIN_BELOW -> if (active) minC < limitC + hysteresisC else minC <= limitC
        }
        if (!tripped) {
            active = false
            return false
        }
        val first = !active
        active = true
        if (first || nowMillis - lastSoundAt >= repeatMillis) {
            lastSoundAt = nowMillis
            return true
        }
        return false
    }
}
