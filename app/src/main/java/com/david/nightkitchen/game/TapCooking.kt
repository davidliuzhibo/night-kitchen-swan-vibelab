package com.david.nightkitchen.game

/** Two deliberate spatial taps share exactly the drag/drop recipe rules. */
object TapCooking {
    /** The cooked food and its containing pan/plate form one pick target.
     * No recipe step is awarded until the separate destination is activated. */
    fun pickup(slot: GrabSlot?, station: Station?, state: KitchenState): GrabSlot? = when {
        slot != null && canDrag(slot, state) -> slot
        state.phase == Phase.PLATE && station == Station.WOK -> GrabSlot.DISH
        state.phase == Phase.SERVE && station == Station.PLATE -> GrabSlot.DISH
        else -> null
    }
    fun select(slot: GrabSlot, state: KitchenState): GrabSlot? =
        slot.takeIf { canDrag(it, state) }

    fun drop(selected: GrabSlot?, target: Station, state: KitchenState): KitchenEvent.Drop? =
        selected?.takeIf { canDrag(it, state) }?.let { KitchenEvent.Drop(it, target) }
}
