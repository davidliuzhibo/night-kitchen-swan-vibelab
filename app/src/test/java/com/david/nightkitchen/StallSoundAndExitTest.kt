package com.david.nightkitchen

import com.david.nightkitchen.audio.StallAudio
import com.david.nightkitchen.audio.StallMix
import com.david.nightkitchen.audio.StallSound
import com.david.nightkitchen.game.*
import com.david.nightkitchen.platform.StallFocus
import com.david.nightkitchen.platform.StallWindow
import com.david.nightkitchen.platform.StallVisits
import com.david.nightkitchen.platform.closeStallWindows
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StallSoundAndExitTest {
    @Test fun aLateMusicLoadCannotBreakMuteAndTwoWindowsStartJustOneLoop() {
        val played = mutableListOf<StallSound>()
        val stopped = mutableListOf<Int>()
        val mix = StallMix({ sound -> played.add(sound); played.size }, { stopped.add(it) })
        mix.setAudible(true)
        mix.setAudible(false)
        mix.loaded(StallSound.MUSIC)
        assertTrue(played.isEmpty())
        repeat(2) { mix.setAudible(true) }
        mix.loaded(StallSound.BELL)
        mix.cue(StallSound.BELL)
        assertEquals(listOf(StallSound.MUSIC, StallSound.BELL), played)
        mix.setAudible(false)
        mix.cue(StallSound.BELL)
        assertEquals(setOf(1, 2), stopped.toSet())
        mix.setAudible(true)
        assertEquals(listOf(StallSound.MUSIC, StallSound.BELL, StallSound.MUSIC), played)
    }

    @Test fun leavingDuringLoadAndReopeningNeverReusesReleasedSampleIds() {
        val played = mutableListOf<StallSound>()
        val mix = StallMix({ played.add(it); played.size }, {})
        mix.setAudible(true)
        mix.loaded(StallSound.PAN)
        mix.clear()
        mix.setAudible(true)
        mix.cue(StallSound.PAN)
        assertTrue(played.isEmpty())
        mix.loaded(StallSound.MUSIC)
        assertEquals(listOf(StallSound.MUSIC), played)
    }

    @Test fun soundsDescribeAcceptedKitchenActionsNotLoadedScenesOrRepeatedDrops() {
        val before = RecipePilot.dish("instant-noodles")
        val drop = KitchenEvent.Drop(GrabSlot.A, Station.WOK)
        val after = reduce(before, drop)
        assertEquals(StallSound.PAN, StallSound.forAction(before, after, drop))
        assertNull(StallSound.forAction(after, reduce(after, drop), drop))
        val wrong = KitchenEvent.Drop(GrabSlot.B, Station.SERVE)
        assertNull(StallSound.forAction(after, reduce(after, wrong), wrong))
        val served = KitchenEvent.Drop(GrabSlot.DISH, Station.SERVE)
        val plated = reduce(RecipePilot.cook(before), KitchenEvent.Drop(GrabSlot.DISH, Station.PLATE))
        val review = reduce(plated, served)
        assertEquals(StallSound.BELL, StallSound.forAction(plated, review, served))
        assertNull(StallSound.forAction(review, reduce(review, served), served))
    }

    @Test fun transferBetweenStallAndMenuDoesNotOfferExitButAnActualAbsenceDoes() {
        var now = 0L
        var notices = 0
        val tasks = mutableListOf<() -> Unit>()
        val focus = StallFocus<String>({ now }, { _, task -> tasks.add(task) }, {}, { notices++ })
        focus.update("counter", true)
        focus.update("counter", false)
        now = 80
        focus.update("orders", true)
        tasks.first()()
        assertEquals(0, notices)
        focus.update("orders", false)
        now += 1300
        tasks.last()()
        tasks.last()()
        focus.update("orders", true)
        assertEquals(1, notices)
        focus.update("orders", false)
        now += 1000
        focus.reset()
        tasks.last()()
        assertEquals(1, notices)
    }

    @Test fun switchingToSystemFreezesCookingAndMusicUntilThePlayerChoosesContinue() {
        val hub = KitchenHub
        val oldState = hub.state
        val oldSound = hub.sound
        val oldPaused = hub.playerPaused
        val oldForeground = hub.foreground
        val oldReady = hub.sceneReady
        var audible = false
        val cues = mutableListOf<StallSound>()
        val audio = object : StallAudio {
            override fun setAudible(value: Boolean) { audible = value }
            override fun cue(sound: StallSound) { cues.add(sound) }
            override fun release() { audible = false }
        }
        try {
            hub.startVisit(audio)
            hub.foreground = true
            hub.setInteractionFocus(true)
            hub.markSceneReady()
            if (!hub.sound) hub.toggleSound()
            hub.topWindowReady()
            assertTrue(audible)
            if (hub.playerPaused) hub.togglePause()
            hub.applyForDebug(RecipePilot.dish("instant-noodles"))
            hub.dispatch(KitchenEvent.Drop(GrabSlot.A, Station.WOK))
            assertEquals(listOf(StallSound.PAN), cues)
            val progress = hub.state
            hub.setInteractionFocus(false)
            hub.awayFromStall()
            hub.setInteractionFocus(true)
            hub.topWindowReady()
            assertEquals(StallMenuPage.CHOICE, hub.menu.page)
            assertFalse(audible)
            hub.dispatch(KitchenEvent.Tick)
            hub.dispatch(KitchenEvent.RetryOrder)
            assertEquals(progress, hub.state)
            hub.menu.resume()
            assertTrue(audible)
            assertTrue(hub.canInteract)
            hub.toggleSound()
            assertFalse(audible)
            hub.setInteractionFocus(false)
            hub.setInteractionFocus(true)
            assertFalse(audible)
            hub.endVisit()
            assertFalse(audible)
        } finally {
            hub.endVisit()
            hub.foreground = oldForeground
            hub.applyForDebug(oldState)
            if (oldReady) hub.markSceneReady()
            if (oldPaused != hub.playerPaused) hub.togglePause()
            if (hub.sound != oldSound) hub.toggleSound()
        }
    }

    @Test fun bothPanelsCanConfirmButOnlyOneSaveAndOneCloseRun() = runBlocking {
        val menu = StallMenu {}
        val write = CompletableDeferred<Boolean>()
        var closes = 0
        menu.open()
        val first = launch(start = CoroutineStart.UNDISPATCHED) { menu.saveAndClose({ write.await() }) { closes++ } }
        assertEquals(StallMenuPage.SAVING, menu.page)
        menu.resume()
        menu.saveAndClose({ error("duplicate save") }) { error("duplicate close") }
        assertEquals(0, closes)
        write.complete(true)
        first.join()
        assertEquals(1, closes)
        assertEquals(StallMenuPage.CLOSING, menu.page)
    }

    @Test fun failedDiskWriteKeepsAllWindowsOpenAndCanRetry() = runBlocking {
        val menu = StallMenu {}
        var closed = false
        menu.open()
        menu.saveAndClose({ false }) { closed = true }
        assertFalse(closed)
        assertEquals(StallMenuPage.SAVE_ERROR, menu.page)
        menu.saveAndClose({ true }) { closed = true }
        assertTrue(closed)
    }

    @Test fun aClosedMenuPanelIsRecoveredOnlyWhenTheUserActuallyReturns() = runBlocking {
        val menu = StallMenu {}
        assertFalse(menu.needsPanel(true, false))
        menu.open()
        assertFalse(menu.needsPanel(false, false))
        assertFalse(menu.needsPanel(true, true))
        assertTrue(menu.needsPanel(true, false))
        menu.saveAndClose({ throw java.io.IOException("disk full") }) { error("lost order") }
        assertTrue(menu.needsPanel(true, false))
        menu.saveAndClose({ true }) {}
        assertFalse(menu.needsPanel(true, false))
    }

    @Test fun delayedDiskCompletionFromAnOldVisitCannotCloseANewOne() = runBlocking {
        val menu = StallMenu {}
        val write = CompletableDeferred<Boolean>()
        menu.open()
        val first = launch(start = CoroutineStart.UNDISPATCHED) { menu.saveAndClose({ write.await() }) { error("closed new visit") } }
        menu.reset()
        write.complete(true)
        first.join()
        assertEquals(StallMenuPage.PLAY, menu.page)
    }

    @Test fun anAlreadyClosedPanelStillAllowsTheOtherTwoWindowsAndTasksToClose() {
        for (missing in StallWindow.entries) {
            val calls = mutableListOf<String>()
            closeStallWindows({ window ->
                calls.add(window.id)
                if (window == missing) throw IllegalArgumentException("already closed")
            }, { window, _ -> assertEquals(missing, window) }, { calls.add("tasks") })
            assertEquals(listOf("order-board", "review-slip", "kitchen-volume", "tasks"), calls)
        }
    }

    @Test fun aLatePanelCannotResurrectTheStallAfterQuittingButANewLaunchCan() {
        val owners = StallVisits<String> { it.startsWith("counter") }
        assertTrue(owners.join("counter-1"))
        assertTrue(owners.join("orders-1"))
        owners.quit()
        assertFalse(owners.join("reviews-late"))
        owners.remove("counter-1")
        owners.remove("orders-1")
        assertFalse(owners.join("orders-late"))
        assertTrue(owners.hosts.isEmpty())
        assertTrue(owners.join("counter-2"))
        assertTrue(owners.join("orders-2"))
        assertEquals(setOf("counter-2","orders-2"), owners.hosts)
    }
}
