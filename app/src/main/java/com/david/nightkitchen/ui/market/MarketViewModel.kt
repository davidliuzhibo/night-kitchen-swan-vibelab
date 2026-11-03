package com.david.nightkitchen.ui.market
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.david.nightkitchen.data.repository.MarketRepository
import com.david.nightkitchen.data.repository.SharedMarketRepository
import com.david.nightkitchen.domain.model.MarketSelection
import com.david.nightkitchen.game.KitchenState
class MarketViewModel(private val repository: MarketRepository) : ViewModel() {
    val state = repository.states
    fun present(state: KitchenState) = MarketUiState(MarketSelection(state))
    fun onEvent(event: MarketUiEvent) { when(event) {
        is MarketUiEvent.Command -> repository.command(event.action, event.selection.revision)
    } }
}
class MarketViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MarketViewModel::class.java))
        return MarketViewModel(SharedMarketRepository) as T
    }
}
