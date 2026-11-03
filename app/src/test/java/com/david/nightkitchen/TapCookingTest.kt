package com.david.nightkitchen

import com.david.nightkitchen.game.*
import org.junit.Assert.*
import org.junit.Test

class TapCookingTest {
    @Test fun cookedFoodCanBePickedThroughItsContainerWithoutCompletingTheStep() {
        val start = freshShift()
        assertNull(TapCooking.pickup(null, Station.WOK, start))
        val cooked = start.copy(added=3, phase=Phase.PLATE)
        assertEquals(GrabSlot.DISH, TapCooking.pickup(null,Station.WOK,cooked))
        assertNull(TapCooking.pickup(null,Station.PLATE,cooked))
        val plated = cooked.copy(phase=Phase.SERVE,plated=true)
        assertEquals(GrabSlot.DISH,TapCooking.pickup(null,Station.PLATE,plated))
        assertNull(TapCooking.pickup(null,Station.SERVE,plated))
        assertEquals(0,plated.servedCount)
    }
    @Test fun tapsAndDropsHaveIdenticalPlacementRules() {
      for(d in NIGHT_MENU){var a=RecipePilot.dish(d.id);var b=a;for(slot in listOf(GrabSlot.C,GrabSlot.A,GrabSlot.B)){val station=a.expectedStation!!;a=reduce(a,requireNotNull(TapCooking.drop(TapCooking.select(slot,a),station,a)));b=reduce(b,KitchenEvent.Drop(slot,station));assertEquals(a,b)};assertFalse(CookBook.ready(a))}
    }
    @Test fun targetAloneAndUnavailablePropsNeverCompleteARecipe() {
        val start=freshShift()
        assertNull(TapCooking.drop(null,Station.WOK,start))
        assertNull(TapCooking.select(GrabSlot.DISH,start))
        assertNull(TapCooking.drop(GrabSlot.DISH,Station.SERVE,start))
        val wrong=requireNotNull(TapCooking.drop(GrabSlot.A,Station.SERVE,start))
        val result=reduce(start,wrong)
        assertEquals(0,result.added)
        assertEquals(1,result.mistakes)
    }
    @Test fun completedOrExpiredSelectionCannotBeReused() {
        val start=freshShift()
        val after=reduce(start,KitchenEvent.Drop(GrabSlot.A,Station.WOK))
        assertNull(TapCooking.drop(GrabSlot.A,Station.WOK,after))
        var expired=after.copy(cooking=after.cooking.copy(heat=Heat.HIGH))
        repeat(100) { expired=reduce(expired,KitchenEvent.Tick) }
        assertEquals(Phase.FAILED,expired.phase)
        assertNull(TapCooking.drop(GrabSlot.A,Station.WOK,expired))
    }
}
