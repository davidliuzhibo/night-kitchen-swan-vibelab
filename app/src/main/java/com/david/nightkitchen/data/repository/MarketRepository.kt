package com.david.nightkitchen.data.repository
import com.david.nightkitchen.game.*
import kotlinx.coroutines.flow.StateFlow
interface MarketRepository {
    val states: StateFlow<KitchenState>
    fun command(event: MarketEvent, expected: MarketSession)
}
object SharedMarketRepository : MarketRepository {
    override val states get() = KitchenHub.states
    override fun command(event: MarketEvent, expected: MarketSession) = KitchenHub.dispatchMarket(event, expected)
}
