package com.david.nightkitchen.ui.market
import com.david.nightkitchen.domain.model.MarketSelection
import com.david.nightkitchen.game.MarketEvent
data class MarketUiState(val selection: MarketSelection)
sealed interface MarketUiEvent {
    data class Command(val action: MarketEvent, val selection: MarketSelection) : MarketUiEvent
}
