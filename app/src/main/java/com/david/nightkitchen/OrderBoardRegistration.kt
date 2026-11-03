package com.david.nightkitchen

import android.os.SystemClock
import androidx.compose.ui.unit.dp
import com.david.nightkitchen.content.HomeVolume
import com.david.nightkitchen.content.OrderBoard
import com.david.nightkitchen.content.ReviewSlip
import com.david.nightkitchen.platform.OrderBoardActivity
import com.david.nightkitchen.platform.ReviewSlipActivity
import com.david.nightkitchen.platform.StallWindow
import com.david.nightkitchen.content.StallMenuHost
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.Placement
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.pico.spatial.ui.foundation.dsl.WorldScale


object OrderBoardRegistration {
    private var scope: SpatialAppScope? = null
    private var done = false

    fun hold(scope: SpatialAppScope) {
        this.scope = scope
    }

    fun registerNow() {
        val held = scope ?: return
        if (done) return
        done = true
        val started = SystemClock.elapsedRealtime()
        registerOrderBoard(held)
        registerReviewSlip(held)
        BootTrace.span("order board registered", started)
    }
}

private fun registerOrderBoard(scope: SpatialAppScope) =
    with(scope) {
        WindowContainer(
            StallWindow.ORDERS.id,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 440.dp, height = 700.dp),
            enableMaterialBackground = true,
            worldScale = WorldScale.Fixed,
            targetActivity = OrderBoardActivity::class.java,
            placement = {
                val kitchen = containers.firstOrNull { it.id == StallWindow.COUNTER.id }
                if (kitchen != null) {
                    Placement.right(kitchen, 16.dp)
                } else {
                    Placement.none()
                }
            },
        ) {
            PicoTheme {
                StallMenuHost { OrderBoard() }
            }
        }
    }

private fun registerReviewSlip(scope: SpatialAppScope) =
    with(scope) {
        WindowContainer(
            StallWindow.REVIEWS.id,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 340.dp, height = 460.dp),
            enableMaterialBackground = true,
            worldScale = WorldScale.Fixed,
            targetActivity = ReviewSlipActivity::class.java,
            placement = {
                val kitchen = containers.firstOrNull { it.id == StallWindow.COUNTER.id }
                if (kitchen != null) {
                    Placement.left(kitchen, 16.dp)
                } else {
                    Placement.none()
                }
            },
        ) {
            PicoTheme {
                StallMenuHost { ReviewSlip() }
            }
        }
    }
