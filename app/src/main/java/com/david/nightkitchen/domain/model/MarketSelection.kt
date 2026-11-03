package com.david.nightkitchen.domain.model
import com.david.nightkitchen.game.KitchenState
import com.david.nightkitchen.game.MarketSession

/** Immutable menu selection token; old callbacks must not spend a newer order's coins. */
data class MarketSelection(val kitchen: KitchenState) {
    val revision: MarketSession get() = kitchen.market
}
