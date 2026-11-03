package com.david.nightkitchen.game

/**
 * Debug-only steps for an emulator with no hand tracking. Not a controller
 * fallback, and not compiled into release. Each play action is the one correct
 * [KitchenEvent.Drop] for the current phase, so a click cannot pick the wrong
 * slot. The label is also the accessibility contentDescription.
 */
object EmulatorPlay {
    const val GRAB_NEXT = "模拟抓取下一步"
    const val PLATE = "模拟盛盘"
    const val SERVE = "模拟出餐"
    const val END_SHIFT = "快进班次到结束"

    fun grabNext(state: KitchenState): KitchenEvent.Drop? {
        if (state.phase != Phase.PREP && state.phase != Phase.COOK) return null
        val grab = state.expectedGrab ?: return null
        val station = state.expectedStation ?: return null
        return KitchenEvent.Drop(grab, station)
    }

    fun plate(state: KitchenState): KitchenEvent.Drop? {
        if (state.phase != Phase.PLATE) return null
        if (state.expectedGrab != GrabSlot.DISH || state.expectedStation != Station.PLATE) return null
        return KitchenEvent.Drop(GrabSlot.DISH, Station.PLATE)
    }

    fun serve(state: KitchenState): KitchenEvent.Drop? {
        if (state.phase != Phase.SERVE) return null
        if (state.expectedGrab != GrabSlot.DISH || state.expectedStation != Station.SERVE) return null
        return KitchenEvent.Drop(GrabSlot.DISH, Station.SERVE)
    }

    /** Explicit debug settlement fixture, never compiled into release. */
    fun fastForwardShift(state: KitchenState): KitchenState = state.copy(phase=Phase.SHIFT_END,feedback=Feedback.SHIFT_END,running=false,shiftSeconds=0)
}
