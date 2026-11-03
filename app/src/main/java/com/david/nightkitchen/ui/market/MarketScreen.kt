package com.david.nightkitchen.ui.market
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.nightkitchen.ui.market.components.MarketPanel
@Composable fun MarketScreen(viewModel: MarketViewModel = viewModel(factory = MarketViewModelFactory())) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val ui = viewModel.present(snapshot)
    MarketPanel(ui.selection.kitchen) { viewModel.onEvent(MarketUiEvent.Command(it, ui.selection)) }
}
