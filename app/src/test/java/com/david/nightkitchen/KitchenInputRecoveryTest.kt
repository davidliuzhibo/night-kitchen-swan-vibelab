package com.david.nightkitchen

import com.david.nightkitchen.game.GrabSlot
import com.david.nightkitchen.game.KitchenEvent
import com.david.nightkitchen.game.KitchenHub
import com.david.nightkitchen.game.KitchenState
import com.david.nightkitchen.game.MarketEvent
import com.david.nightkitchen.game.Phase
import com.david.nightkitchen.game.Station
import com.david.nightkitchen.game.TapCooking
import com.david.nightkitchen.scene.KitchenInput
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class KitchenInputRecoveryTest {
    private lateinit var saved: KitchenState
    private var wasInForeground = true
    private var wasPaused = false

    @Before fun enterNoodleOrder() {
        saved = KitchenHub.state
        wasInForeground = KitchenHub.foreground
        wasPaused = KitchenHub.playerPaused
        KitchenHub.foreground = true
        KitchenHub.markSceneReady()
        if (KitchenHub.playerPaused) KitchenHub.togglePause()
        KitchenHub.applyForDebug(RecipePilot.dish("instant-noodles"))
    }

    @After fun restore() {
        KitchenHub.applyForDebug(saved)
        if (KitchenHub.playerPaused != wasPaused) KitchenHub.togglePause()
        KitchenHub.foreground = wasInForeground
    }

    private fun session(): KitchenInput {
        val generation = KitchenHub.inputGeneration
        return KitchenInput { KitchenHub.inputGeneration == generation }
    }

    private fun tapIntoWok(input: KitchenInput, slot: GrabSlot, time: Long) {
        val before = KitchenHub.state
        assertTrue(KitchenHub.canInteract && input.canTap(time))
        val selected = TapCooking.pickup(slot, null, before)
        val drop = requireNotNull(TapCooking.drop(selected, Station.WOK, before))
        assertTrue(input.beginTap(drop.grab, time))
        assertTrue(input.canDeliverTap(drop.grab, before, KitchenHub.state))
        KitchenHub.dispatch(drop)
        input.endTap()
        assertTrue(KitchenHub.state.cooking.contains(requireNotNull(slot.prepIndex)))
    }

    @Test fun rejectedSecondStartCannotStrandTheFirstIngredient() {
        val input = session()
        assertTrue(input.beginDrag(GrabSlot.A, 1000))
        assertFalse(input.beginDrag(null, 1100))
        assertNull(input.endDrag(cancelled = false, 1200))
        assertNull(input.held)
        assertFalse(KitchenHub.state.cooking.contains(0))
        assertFalse(input.canTap(1349))
        tapIntoWok(input, GrabSlot.B, 1350)
    }

    @Test fun overlappingValidGrabsDropNeitherIngredient() {
        val input = session()
        assertTrue(input.beginDrag(GrabSlot.A, 1000))
        assertFalse(input.beginDrag(GrabSlot.B, 1050))
        assertNull(input.endDrag(true, 1100))
        assertNull(input.endDrag(false, 1150))
        assertEquals(0, KitchenHub.state.cooking.mask)
        tapIntoWok(input, GrabSlot.C, 1400)
    }

    @Test fun nextFreshPinchAfterMissingDragEndCanTapWithoutPausing() {
        val input = session()
        input.pointerDown(1)
        assertTrue(input.beginDrag(GrabSlot.A, 100))
        input.pointerUp(1)
        assertEquals(GrabSlot.A, input.dragged)
        // Even when the SDK reuses the same pointer ID, this is a distinct gesture.
        assertTrue(input.pointerDown(1))
        assertNull(input.endDrag(false, 120))
        assertFalse(KitchenHub.playerPaused)
        tapIntoWok(input, GrabSlot.B, 145)
        assertFalse(KitchenHub.state.cooking.contains(0))
    }

    @Test fun aSecondHandAndUnmatchedUpsCannotClearTheCurrentGrab() {
        val input = session()
        input.pointerDown(1)
        input.beginDrag(GrabSlot.A, 100)
        input.pointerUp(99)
        assertFalse(input.pointerDown(2))
        input.pointerUp(2)
        assertFalse(input.pointerDown(3))
        input.pointerUp(1)
        assertFalse(input.pointerDown(4))
        assertEquals(GrabSlot.A, input.dragged)
        assertEquals(GrabSlot.A, input.endDrag(false, 400))
    }

    @Test fun rawUpDoesNotPreemptNormalReleaseOrItsAccidentalTapProtection() {
        val input = session()
        input.pointerDown(1)
        input.beginDrag(GrabSlot.A, 100)
        input.pointerUp(1)
        assertEquals(GrabSlot.A, input.endDrag(false, 200))
        assertFalse(input.pointerDown(2))
        assertFalse(input.canTap(210))
        assertTrue(input.canTap(450))
    }

    @Test fun pauseAndResumeWithoutAnEndRecoversAndRejectsLateEvents() {
        val old = session()
        assertTrue(old.beginDrag(GrabSlot.A, 1000))
        KitchenHub.togglePause()
        KitchenHub.togglePause()
        val next = session()
        assertTrue(next.beginDrag(GrabSlot.B, 1010))
        assertNull(old.endDrag(false, 1020))
        assertNull(old.held)
        assertFalse(old.beginDrag(GrabSlot.C, 1030))
        assertEquals(GrabSlot.B, next.endDrag(false, 1040))
        tapIntoWok(next, GrabSlot.B, 1290)
    }

    @Test fun returningFromBackgroundAllowsAFreshGrabEvenWithoutFocusFrame() {
        val old = session()
        assertTrue(old.beginDrag(GrabSlot.A, 1000))
        KitchenHub.foreground = false
        KitchenHub.foreground = true
        assertNull(old.held)
        assertNull(old.endDrag(true, 1200))
        tapIntoWok(session(), GrabSlot.C, 1210)
    }

    @Test fun retryingTheSameUnchangedDishRejectsThePriorAnimation() {
        val old = session()
        val before = KitchenHub.state
        assertTrue(old.beginTap(GrabSlot.A, 1000))
        KitchenHub.dispatch(KitchenEvent.RetryOrder)
        assertEquals(before.active.id, KitchenHub.state.active.id)
        assertEquals(before.phase, KitchenHub.state.phase)
        assertEquals(before.added, KitchenHub.state.added)
        val next = session()
        assertTrue(next.beginDrag(GrabSlot.B, 1040))
        assertFalse(old.canDeliverTap(GrabSlot.A, before, KitchenHub.state))
        old.endTap()
        assertEquals(GrabSlot.B, next.held)
        assertEquals(0, KitchenHub.state.cooking.mask)
    }

    @Test fun lateAnimationCannotDeliverAfterPauseAndImmediateResume() {
        val old = session()
        val before = KitchenHub.state
        assertTrue(old.beginTap(GrabSlot.A, 1000))
        KitchenHub.togglePause()
        KitchenHub.togglePause()
        assertFalse(old.tapInFlight)
        assertFalse(old.canDeliverTap(GrabSlot.A, before, KitchenHub.state))
        tapIntoWok(session(), GrabSlot.B, 1001)
    }

    @Test fun changingDishAndEnteringMarketDiscardTheOldHold() {
        KitchenHub.applyForDebug(KitchenHub.state.copy(phase = Phase.REVIEW))
        val review = session()
        assertTrue(review.beginTap(GrabSlot.DISH, 1000))
        KitchenHub.dispatch(KitchenEvent.NextOrder)
        assertFalse(review.tapInFlight)
        val lesson = session()
        assertTrue(lesson.beginDrag(GrabSlot.A, 2000))
        KitchenHub.dispatch(KitchenEvent.Market(MarketEvent.Open))
        assertNull(lesson.endDrag(false, 2500))
        assertFalse(KitchenHub.canInteract)
    }

    @Test fun trackingLossStillPausesCookingAndGraceExpiresWithoutDelivery() {
        val input = session()
        assertTrue(input.beginDrag(GrabSlot.A, 1000))
        assertEquals(GrabSlot.A, input.endDrag(true, 1010))
        KitchenHub.pauseForInterruptedGrab()
        assertTrue(KitchenHub.playerPaused)
        assertNull(input.endDrag(false, 1030))
        assertFalse(input.expireGrace(1459))
        assertEquals(GrabSlot.A, input.held)
        assertTrue(input.expireGrace(1460))
        assertTrue(KitchenHub.playerPaused)
        assertEquals(0, KitchenHub.state.cooking.mask)
        KitchenHub.togglePause()
        tapIntoWok(input, GrabSlot.A, 1500)
    }

    @Test fun resumingAndRegrabbingWithinGraceKeepsTheNewHold() {
        val input = session()
        assertTrue(input.beginDrag(GrabSlot.C, 1000))
        assertEquals(GrabSlot.C, input.endDrag(true, 1020))
        KitchenHub.pauseForInterruptedGrab()
        KitchenHub.togglePause()
        assertTrue(input.beginDrag(GrabSlot.C, 1100))
        assertFalse(input.expireGrace(2000))
        assertFalse(input.canTap(7000))
        assertEquals(GrabSlot.C, input.endDrag(false, 7100))
    }

    @Test fun ordinaryCookingTickAndRejectedGrabsDoNotInterruptTapDelivery() {
        val input = session()
        val before = KitchenHub.state
        assertTrue(input.beginTap(GrabSlot.A, 1000))
        KitchenHub.dispatch(KitchenEvent.Tick)
        assertFalse(input.beginDrag(GrabSlot.B, 1100))
        assertNull(input.endDrag(false, 1200))
        assertTrue(input.canDeliverTap(GrabSlot.A, before, KitchenHub.state))
        KitchenHub.dispatch(KitchenEvent.Drop(GrabSlot.A, Station.WOK))
        assertFalse(input.canDeliverTap(GrabSlot.A, before, KitchenHub.state))
        input.endTap()
        assertEquals(1, KitchenHub.state.cooking.mask)
        assertTrue(input.canTap(1400))
    }
}
