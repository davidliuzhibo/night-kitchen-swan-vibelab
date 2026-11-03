package com.david.nightkitchen.scene

import com.david.nightkitchen.game.Station

/**
 * Official grab path. A pinch, a poke, or an untagged spatial drag may hold
 * a prop. The same OS spatial-drag route accepts emulator pointers for testing.
 * The debug step buttons live in the debug source set and are not this path.
 */
object GesturePlay {
    const val POINTER_KIND = "Pointer"

    /** Keep the last pose this long after tracking drops away from a station. */
    const val TRACKING_GRACE_MS = 450L

    /** A small linear assist for seated reach. It must remain possible to aim by millimetres. */
    fun travel(metres: Float): Float = 1.6f * metres

    fun allows(kindName: String): Boolean = kindName in setOf("DirectPinch", "GazePinch", "RayBasedPinch", "Poke", "Unknown", POINTER_KIND)

    fun resolve(
        position: Vec3,
        cancelled: Boolean,
    ): Decision {
        // A real release above the lip is valid even when the raised ingredient's origin
        // is a little high. Do not enlarge the horizontal pick area or change other stations.
        val wok = StallLayout.wok
        val lip = wok.copy(y = position.y.coerceIn(wok.y, wok.y + .03f))
        val station = StallLayout.nearest(position) ?: Station.WOK.takeIf {
            position.dist(lip) <= StallLayout.SNAP_RADIUS
        }
        return when {
            cancelled -> Decision.Hold
            station != null -> Decision.Drop(station)
            else -> Decision.Home
        }
    }

    sealed class Decision {
        data class Drop(val station: Station) : Decision()

        data object Hold : Decision()

        data object Home : Decision()
    }
}
