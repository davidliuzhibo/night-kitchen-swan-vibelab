package com.david.nightkitchen

import com.david.nightkitchen.game.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Late OS grab/menu callbacks must not alter a background cooking checkpoint. */
class ForegroundInputTest {
    private lateinit var previousState: KitchenState
    private var previousForeground = true
    private var previousPaused = true

    @Before fun setup() {
        previousState = KitchenHub.state
        previousForeground = KitchenHub.foreground
        previousPaused = KitchenHub.playerPaused
        KitchenHub.foreground = true
        KitchenHub.markSceneReady()
        if (KitchenHub.playerPaused) KitchenHub.togglePause()
        KitchenHub.applyForDebug(freshShift())
    }

    @After fun restore() {
        KitchenHub.applyForDebug(previousState)
        KitchenHub.foreground = previousForeground
        if (KitchenHub.playerPaused != previousPaused) KitchenHub.togglePause()
    }

    @Test fun backgroundSceneDoesNotAdvertiseInteraction() {
        assertTrue(KitchenHub.canInteract)
        KitchenHub.foreground = false
        assertFalse(KitchenHub.canInteract)
    }

    @Test fun lateDropAndHeatChangeCannotMutateBackgroundFood() {
        val before = KitchenHub.state
        KitchenHub.foreground = false
        KitchenHub.dispatch(KitchenEvent.Drop(GrabSlot.A, Station.WOK))
        KitchenHub.dispatch(KitchenEvent.SetHeat(Heat.HIGH))
        assertEquals(before, KitchenHub.state)
    }

    @Test fun pendingRestartCannotEraseBackgroundOrder() {
        KitchenHub.dispatch(KitchenEvent.Drop(GrabSlot.A, Station.WOK))
        val before = KitchenHub.state
        KitchenHub.foreground = false
        KitchenHub.dispatch(KitchenEvent.NewShift)
        assertEquals(before, KitchenHub.state)
    }

    @Test fun backgroundTasteCallbackCannotChangeSavedChoice() {
        val before = KitchenHub.state
        KitchenHub.foreground = false
        KitchenHub.setTaste("少盐")
        assertEquals(before, KitchenHub.state)
    }

    @Test fun foregroundReturnAcceptsTheNextDeliberateDrop() {
        KitchenHub.foreground = false
        repeat(20) { KitchenHub.dispatch(KitchenEvent.Tick) }
        assertEquals(freshShift(), KitchenHub.state)
        KitchenHub.foreground = true
        KitchenHub.dispatch(KitchenEvent.Drop(GrabSlot.A, Station.WOK))
        assertTrue(KitchenHub.state.cooking.contains(0))
    }

    @Test fun intentionalRestartStillWorksWhilePlayerPausedInForeground() {
        KitchenHub.dispatch(KitchenEvent.Drop(GrabSlot.A, Station.WOK))
        KitchenHub.togglePause()
        KitchenHub.dispatch(KitchenEvent.NewShift)
        assertEquals(freshShift(), KitchenHub.state)
        assertTrue(KitchenHub.playerPaused)
    }
    @Test fun marketButtonsCannotUseAnOldTicketSnapshot(){
        KitchenHub.dispatch(KitchenEvent.Market(MarketEvent.Open))
        val old=KitchenHub.state.market
        KitchenHub.dispatchMarket(MarketEvent.ChooseNight(0),old)
        val current=KitchenHub.state
        KitchenHub.dispatchMarket(MarketEvent.ChooseDish(1),old)
        assertEquals(current,KitchenHub.state)
        KitchenHub.foreground=false
        KitchenHub.dispatchMarket(MarketEvent.ChooseDish(0),current.market)
        assertEquals(current,KitchenHub.state)
    }
    @Test fun lateLessonButtonsCannotReplaceTheMarketMenu(){
        KitchenHub.dispatch(KitchenEvent.Market(MarketEvent.Open))
        KitchenHub.dispatch(KitchenEvent.Market(MarketEvent.ChooseNight(1)))
        val before=KitchenHub.state
        for(event in listOf(KitchenEvent.NewShift,KitchenEvent.NextOrder,KitchenEvent.RetryOrder)){
            KitchenHub.dispatch(event)
            assertEquals(before,KitchenHub.state)
        }
        KitchenHub.dispatchMarket(MarketEvent.ChooseDish(0),before.market)
        assertEquals(MarketStage.GEAR,KitchenHub.state.market.stage)
    }
}
