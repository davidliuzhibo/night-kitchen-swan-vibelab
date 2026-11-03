package com.david.nightkitchen
import android.os.SystemClock
import com.david.nightkitchen.content.HomeVolume
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

/** Entry wiring; delayed side-window ownership lives in OrderBoardRegistration. */
fun mainApp(scope: SpatialAppScope) = with(scope) {
    BootTrace.stage("mainApp entered")
    val started = SystemClock.elapsedRealtime()
    DefaultWindowContainer { PicoTheme { HomeVolume() } }
    BootTrace.span("mainApp registered", started)
    OrderBoardRegistration.hold(scope)
}
