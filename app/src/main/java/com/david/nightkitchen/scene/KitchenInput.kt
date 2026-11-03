package com.david.nightkitchen.scene

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.david.nightkitchen.game.GrabSlot
import com.david.nightkitchen.game.KitchenState
import com.david.nightkitchen.game.canDrag

/** One owner for a prop in this scene's current input session. */
internal class KitchenInput(private val isCurrent: () -> Boolean) {
    private sealed class Hold(val slot: GrabSlot) {
        class Drag(slot: GrabSlot) : Hold(slot)
        class Tap(slot: GrabSlot) : Hold(slot)
        class Grace(slot: GrabSlot, val until: Long) : Hold(slot)
    }

    private var hold by mutableStateOf<Hold?>(null)
    private var dragEndedAt: Long? = null
    private val pressedPointers = mutableSetOf<Long>()
    private var releasedDrag: Hold.Drag? = null
    val held: GrabSlot? get() = if (isCurrent()) hold?.slot else null
    val dragged: GrabSlot? get() = if (isCurrent()) (hold as? Hold.Drag)?.slot else null
    val tapInFlight: Boolean get() = isCurrent() && hold is Hold.Tap

    fun pointerUp(id: Long) {
        if (pressedPointers.remove(id) && pressedPointers.isEmpty()) releasedDrag = hold as? Hold.Drag
    }

    /** All observed contacts ended and a distinct press began. Only the old owner can be stale. */
    fun pointerDown(id: Long): Boolean {
        if (!isCurrent() || !pressedPointers.add(id) || pressedPointers.size != 1) return false
        val old = releasedDrag
        releasedDrag = null
        if (old == null || hold !== old) return false
        hold = null
        dragEndedAt = null
        return true
    }

    fun canTap(now: Long): Boolean {
        val end = dragEndedAt
        return isCurrent() && hold == null && (end == null || now - end >= 250L)
    }

    fun beginTap(slot: GrabSlot, now: Long): Boolean {
        if (!canTap(now)) return false
        hold = Hold.Tap(slot)
        return true
    }

    fun canDeliverTap(slot: GrabSlot, before: KitchenState, now: KitchenState): Boolean =
        tapInFlight && held == slot && before.active.id == now.active.id &&
            before.phase == now.phase && before.added == now.added && canDrag(slot, now)

    fun endTap() {
        if (hold is Hold.Tap) hold = null
    }

    fun beginDrag(slot: GrabSlot?, now: Long): Boolean {
        if (!isCurrent() || hold is Hold.Tap) return false
        if (hold is Hold.Drag) {
            // The recognizer started again without ending the old grab. Its terminal
            // callback has no pointer ID. Release both contacts without delivering food.
            abandonDrag(now)
            return false
        }
        if (slot == null) return false
        hold = Hold.Drag(slot)
        return true
    }

    fun endDrag(cancelled: Boolean, now: Long): GrabSlot? {
        val slot = dragged ?: return null
        dragEndedAt = now
        hold = if (cancelled) Hold.Grace(slot, now + GesturePlay.TRACKING_GRACE_MS) else null
        return slot
    }

    fun abandonDrag(now: Long) {
        if (hold is Hold.Drag) {
            hold = null
            dragEndedAt = now
        }
    }

    fun expireGrace(now: Long): Boolean {
        val grace = hold as? Hold.Grace ?: return false
        if (now < grace.until) return false
        hold = null
        return true
    }
}
