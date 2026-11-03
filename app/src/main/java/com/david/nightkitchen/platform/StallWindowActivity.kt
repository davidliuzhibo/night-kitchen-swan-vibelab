package com.david.nightkitchen.platform

import android.os.Bundle
import android.media.AudioManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.david.nightkitchen.game.KitchenHub
import com.david.nightkitchen.game.StallMenuPage
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity

abstract class StallWindowActivity : SpatialLaunchActivity() {
    private val back = OnBackInvokedCallback {
        if (KitchenHub.menu.page == StallMenuPage.PLAY) KitchenHub.menu.open() else KitchenHub.menu.resume()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, back)
    }
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        (application as NightKitchenApp).windowFocus(this, isTopResumedActivity)
    }
    override fun onDestroy() {
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(back)
        super.onDestroy()
    }
}
