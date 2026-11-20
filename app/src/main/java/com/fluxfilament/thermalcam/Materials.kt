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
 * Typical long-wave (8-14 µm) emissivities of everyday materials, for picking ε by
 * what the thing is made of rather than by how shiny it looks.
 *
 * Rounded values that published tables broadly agree on; any one surface can be off
 * by a few hundredths with its finish, age and dirt, which is why the gloss presets
 * and the tape trick stay the first advice. Metals are listed only oxidised or
 * anodised: bare polished metal is below 0.3, where no ε setting gives a reading
 * worth trusting.
 */
class Material(val nameRes: Int, val emissivity: Double)

object Materials {
    val ALL = listOf(
        Material(R.string.material_skin, 0.98),
        Material(R.string.material_water, 0.96),
        Material(R.string.material_tape, 0.95),
        Material(R.string.material_paint, 0.94),
        Material(R.string.material_brick, 0.93),
        Material(R.string.material_paper, 0.93),
        Material(R.string.material_concrete, 0.92),
        Material(R.string.material_glass, 0.92),
        Material(R.string.material_plastic, 0.92),
        Material(R.string.material_plaster, 0.91),
        Material(R.string.material_pcb, 0.90),
        Material(R.string.material_wood, 0.90),
        Material(R.string.material_steel_oxidised, 0.80),
        Material(R.string.material_copper_oxidised, 0.78),
        Material(R.string.material_aluminium_anodised, 0.77),
    )

    /** What a reflected temperature defaults to: a room at 22 °C, as Planck's own default. */
    const val DEFAULT_REFLECTED_C = 22
}
