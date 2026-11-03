package com.david.nightkitchen.scene

import com.david.nightkitchen.game.GrabSlot
import com.david.nightkitchen.game.KitchenState
import com.david.nightkitchen.game.Phase
import com.david.nightkitchen.game.Station
import kotlin.math.sqrt

data class Vec3(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    fun dist(other: Vec3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/**
 * Meter-scale poses inside the volumetric stall. Dropping near a station snaps.
 * The center station is the cook surface for every dish (wok, grill, pot, griddle).
 *
 * The volume is 1.28 x 0.96 x 0.62 meters. A 2.20 m volume kept the same
 * countertop height but stalled the frame loop (core props ~32 s, ANR).
 * The shell still puts the volume center near eye height. The work surface
 * stays 0.18 m below that center. The cafe-table legs continue past the
 * volume floor and are clipped; the cookware is not.
 * Ingredients are spread in X as well as Z. +Z faces the user.
 */
data class Box(
    val width: Float,
    val height: Float,
    val depth: Float,
    val at: Vec3,
) {
    fun corners(): List<Vec3> {
        val hx = width / 2f
        val hy = height / 2f
        val hz = depth / 2f
        return listOf(-1f, 1f).flatMap { sx ->
            listOf(-1f, 1f).flatMap { sy ->
                listOf(-1f, 1f).map { sz ->
                    Vec3(at.x + sx * hx, at.y + sy * hy, at.z + sz * hz)
                }
            }
        }
    }
}

object StallLayout {
    data class HitTarget(val radius: Float, val lift: Float)
    fun hitTarget(slot: GrabSlot): HitTarget = when(slot) {
        GrabSlot.A -> HitTarget(.075f,.045f)
        GrabSlot.B -> HitTarget(.040f,.035f)
        GrabSlot.C -> HitTarget(.045f,.075f)
        GrabSlot.SPATULA -> HitTarget(.035f,.10f)
        GrabSlot.DISH -> HitTarget(.09f,.04f)
    }
    fun stationHitTarget(station: Station): HitTarget = when (station) {
        // Once plated, the food collider must enclose the plate collider so
        // the visible food remains pickable instead of selecting the plate.
        Station.PLATE -> HitTarget(.055f, .015f)
        Station.SERVE -> HitTarget(.065f, .05f)
        Station.WOK -> HitTarget(.10f, .04f)
    }
    const val VOLUME_WIDTH = 1.28f
    const val VOLUME_HEIGHT = 0.96f
    const val VOLUME_DEPTH = 0.62f
    /**
     * Drop radius. The middle ingredient rests 0.206 m from the wok, so a
     * pinch released on its home spot must stay outside this radius.
     */
    const val SNAP_RADIUS = 0.195f
    const val GRAB_SIZE = 0.12f

    /** Countertop in volume space. Negative Y is below the eye-height center. */
    const val SURFACE_Y = -0.18f
    const val TABLE_MESH_HEIGHT = 0.533f
    const val SURFACE_CLEARANCE = 0.012f
    val tableFootY = SURFACE_Y - SURFACE_CLEARANCE - TABLE_MESH_HEIGHT

    val slotHomes: List<Vec3> =
        listOf(
            Vec3(-0.40f, SURFACE_Y, 0.16f),
            Vec3(-0.18f, SURFACE_Y, 0.05f),
            Vec3(-0.40f, SURFACE_Y, -0.08f),
        )
    val wok = Vec3(0.02f, SURFACE_Y, 0.0f)
    val plate = Vec3(0.38f, SURFACE_Y, 0.10f)
    val serve = Vec3(0.50f, SURFACE_Y + 0.015f, -0.10f)
    val spatulaHome = Vec3(-0.22f, SURFACE_Y, 0.22f)
    val hidden = Vec3(0f, -1.6f, 0f)

    val counter = Box(1.12f, 0.06f, 0.48f, Vec3(0.00f, SURFACE_Y, 0.00f))
    val pedestals: List<Box> =
        slotHomes.map { home -> Box(0.14f, 0.035f, 0.14f, Vec3(home.x, home.y, home.z)) }
    val wokBox = Box(0.28f, 0.08f, 0.24f, wok)
    val plateBox = Box(0.20f, 0.04f, 0.20f, plate)
    val serveBox = Box(0.08f, 0.10f, 0.08f, serve)

    private val stations: List<Pair<Station, Vec3>> =
        listOf(
            Station.WOK to wok,
            Station.PLATE to plate,
            Station.SERVE to serve,
        )

    fun nearest(position: Vec3): Station? {
        val best = stations.minBy { it.second.dist(position) }
        return if (best.second.dist(position) <= SNAP_RADIUS) best.first else null
    }

    /** Keep a dragged prop inside the volume so a fast pinch can still re-grab it. */
    fun withinReach(position: Vec3): Vec3 {
        val pad = 0.02f
        return Vec3(
            position.x.coerceIn(-VOLUME_WIDTH / 2f + pad, VOLUME_WIDTH / 2f - pad),
            position.y.coerceIn(-VOLUME_HEIGHT / 2f + pad, VOLUME_HEIGHT / 2f - pad),
            position.z.coerceIn(-VOLUME_DEPTH / 2f + pad, VOLUME_DEPTH / 2f - pad),
        )
    }

    fun rest(
        state: KitchenState,
        slot: GrabSlot,
    ): Vec3 =
        when (slot) {
            GrabSlot.SPATULA -> spatulaHome
            GrabSlot.DISH ->
                when (state.phase) {
                    Phase.PLATE -> Vec3(wok.x, wok.y + 0.12f, wok.z)
                    Phase.SERVE -> plate
                    Phase.REVIEW -> serve
                    else -> hidden
                }
            else -> {
                val index = slot.prepIndex ?: return hidden
                val stillInWok =
                    state.cooking.contains(index) && !state.cooking.removed(index) && (state.phase == Phase.PREP || state.phase == Phase.COOK)
                when {
                    state.active.id=="beer-plate"&&state.cooking.contains(index) -> if(state.cooking.coldServed)hidden else Vec3(plate.x+(index-1)*.045f,plate.y+.05f,plate.z)
                    stillInWok -> wokSlot(index)
                    // Lifted soup ingredients stay on the plate only while cooking.
                    // Once combined into DISH, duplicate prep meshes can hide the
                    // serving bell and intercept the player's pinch/tap.
                    state.cooking.removed(index) && state.phase in listOf(Phase.PREP, Phase.COOK) ->
                        Vec3(plate.x+(index-1)*.04f,plate.y+.06f,plate.z)
                    state.cooking.contains(index) -> hidden
                    else -> slotHomes[index]
                }
            }
        }

    fun dishVisible(state: KitchenState): Boolean =
        state.phase == Phase.PLATE || state.phase == Phase.SERVE || state.phase == Phase.REVIEW

    private fun wokSlot(index: Int): Vec3 = Vec3(wok.x + (index - 1) * 0.06f, wok.y + 0.07f, wok.z)
}
