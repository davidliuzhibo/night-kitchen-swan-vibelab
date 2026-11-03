package com.david.nightkitchen.content

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import com.david.nightkitchen.BootTrace
import com.david.nightkitchen.OrderBoardRegistration
import com.david.nightkitchen.game.KitchenEvent
import com.david.nightkitchen.game.KitchenHub
import com.david.nightkitchen.game.Phase
import com.david.nightkitchen.game.StallMenuPage
import com.david.nightkitchen.platform.StallWindow
import com.david.nightkitchen.platform.NightKitchenApp
import com.david.nightkitchen.scene.KitchenScene
import com.david.nightkitchen.scene.yieldMain
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.SpatialNavigator
import kotlinx.coroutines.delay

@Composable
fun HomeVolume() {
    if (!KitchenHub.visitOpen || KitchenHub.menu.page == StallMenuPage.CLOSING) return
    val navigator = LocalSpatialNavigator.current
    val app = LocalContext.current.applicationContext as NightKitchenApp
    LaunchedEffect(KitchenHub.menu.page, KitchenHub.foreground, app.hasVisibleMenuPanel, KitchenHub.nameplatesMounted) {
        // The player may have closed both flat panels. Reuse one panel when the counter
        // is foreground again, without putting a game window on top of the system Home.
        if (KitchenHub.nameplatesMounted && KitchenHub.menu.needsPanel(KitchenHub.foreground, app.hasVisibleMenuPanel)) {
            OrderBoardRegistration.registerNow()
            openOrderBoard(navigator)
        }
    }
    LaunchedEffect(Unit) {
        while (!KitchenHub.nameplatesMounted) {
            yieldMain()
        }
        yieldMain()
        OrderBoardRegistration.registerNow()
        openOrderBoard(navigator)
        openReviewSlip(navigator)
        while (true) {
            delay(1000)
            val state = KitchenHub.state
            if (state.running && state.phase != Phase.SHIFT_END) {
                KitchenHub.dispatch(KitchenEvent.Tick)
            }
        }
    }
    key(KitchenHub.sceneGeneration) { KitchenScene() }
}

private suspend fun openOrderBoard(navigator: SpatialNavigator) {
    repeat(6) { attempt ->
        if (!KitchenHub.visitOpen || KitchenHub.menu.page == StallMenuPage.CLOSING) return
        val started = SystemClock.elapsedRealtime()
        try {
            navigator.openWindowContainer(StallWindow.ORDERS.id, tag = StallWindow.ORDERS.tag)
            BootTrace.span("order window open", started)
            return
        } catch (error: Throwable) {
            BootTrace.span("order window open failed", started)
            Log.w("NightKitchen", "order window open failed, attempt ${attempt + 1}", error)
            yieldMain()
        }
    }
}

private suspend fun openReviewSlip(navigator: SpatialNavigator) {
    repeat(4) { attempt ->
        if (!KitchenHub.visitOpen || KitchenHub.menu.page == StallMenuPage.CLOSING) return
        try {
            navigator.openWindowContainer(StallWindow.REVIEWS.id, tag = StallWindow.REVIEWS.tag)
            return
        } catch (error: Throwable) {
            Log.w("NightKitchen", "review slip open failed, attempt ${attempt + 1}", error)
            yieldMain()
        }
    }
}
