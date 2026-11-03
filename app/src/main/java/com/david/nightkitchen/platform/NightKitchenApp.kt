package com.david.nightkitchen.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.david.nightkitchen.BootTrace
import com.david.nightkitchen.audio.AndroidStallAudio
import com.david.nightkitchen.game.KitchenHub
import com.david.nightkitchen.mainApp
import com.pico.spatial.ui.foundation.dsl.launch
import com.pico.spatial.ui.platform.containers.SpatialNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch as launchExit

class NightKitchenApp : Application() {
    private val visits = StallVisits<StallWindowActivity> { it is LaunchActivity }
    private val started = mutableSetOf<StallWindowActivity>()
    internal var hasVisibleMenuPanel by mutableStateOf(false)
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val focus = StallFocus<StallWindowActivity>(
        SystemClock::uptimeMillis, { delay, action -> main.postDelayed({ action() }, delay) },
        KitchenHub::setInteractionFocus, KitchenHub::awayFromStall
    )

    override fun onCreate() {
        BootTrace.markProcess()
        super.onCreate()
        KitchenHub.initialize(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (activity !is StallWindowActivity) return
                if (!visits.join(activity)) {
                    activity.finishAndRemoveTask()
                    return
                }
                if (visits.hosts.size == 1) KitchenHub.startVisit(AndroidStallAudio(this@NightKitchenApp))
            }
            override fun onActivityStarted(activity: Activity) {
                if (activity is StallWindowActivity && activity in visits.hosts) {
                    started.add(activity)
                    updateVisiblePanels()
                    KitchenHub.foreground = true
                }
            }
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) { if (activity is StallWindowActivity && activity in visits.hosts) KitchenHub.save() }
            override fun onActivityStopped(activity: Activity) {
                if (activity !is StallWindowActivity || activity !in visits.hosts) return
                started.remove(activity)
                updateVisiblePanels()
                KitchenHub.foreground = started.isNotEmpty()
                KitchenHub.save()
            }
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (activity !is StallWindowActivity || !visits.remove(activity)) return
                focus.update(activity, false)
                started.remove(activity)
                updateVisiblePanels()
                if (visits.hosts.isEmpty()) {
                    focus.reset()
                    KitchenHub.endVisit()
                }
            }
        })
        BootTrace.stage("public spatial launch start")
        launch(::mainApp)
        BootTrace.stage("public spatial launch returned")
    }

    private fun updateVisiblePanels() {
        hasVisibleMenuPanel = started.any {
            (it is OrderBoardActivity || it is ReviewSlipActivity) && !it.isFinishing && !it.isDestroyed
        }
    }

    internal fun windowFocus(window: StallWindowActivity, top: Boolean) {
        if (window !in visits.hosts) return
        focus.update(window, top)
        if (top) KitchenHub.topWindowReady()
    }

    internal fun saveAndExit(navigator: SpatialNavigator) {
        // This job outlives the panel whose button was pressed.
        scope.launchExit {
            KitchenHub.menu.saveAndClose(KitchenHub::saveBeforeExit) {
                visits.quit()
                closeGameWindows(this@NightKitchenApp, visits.hosts.toList()) {
                    navigator.closeWindowContainer(it.id, it.tag)
                }
            }
        }
    }
}
