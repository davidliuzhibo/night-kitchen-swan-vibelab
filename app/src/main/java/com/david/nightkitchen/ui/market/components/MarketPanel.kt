package com.david.nightkitchen.ui.market.components
import androidx.compose.runtime.Composable
import com.david.nightkitchen.game.*
import com.david.nightkitchen.ui.design.DesignedPanel
/** New menu only. Cooking, burn recovery and resource errors remain in OrderBoard. */
@Composable fun MarketPanel(s:KitchenState,onEvent:(MarketEvent)->Unit) {
 DesignedPanel(MarketPresentation.frame(s)) { onEvent(MarketPresentation.command(it)) }
}
