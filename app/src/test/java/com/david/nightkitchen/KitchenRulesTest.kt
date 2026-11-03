package com.david.nightkitchen
import com.david.nightkitchen.game.*
import com.david.nightkitchen.scene.*
import org.junit.Assert.*
import org.junit.Test
class KitchenRulesTest {
 @Test fun tutorialDoesNotPenalizeReadingTime(){val fresh=RecipePilot.cook(freshShift());var later=fresh;repeat(120){later=reduce(later,KitchenEvent.Tick)};assertEquals(CookBook.quality(fresh),CookBook.quality(later))}
 @Test fun optionalScallionMatchesActualIngredient(){val s=RecipePilot.cook(freshShift());assertFalse(s.cooking.contains(2));assertTrue(CookBook.tasteCorrect(s));assertFalse(CookBook.tasteCorrect(s.copy(cooking=s.cooking.copy(mask=7))))}
 @Test fun finalHotCanBeServedBeforeCold(){var s=reduce(RecipePilot.dish("beer-plate"),KitchenEvent.StartHotSide);repeat(23){s=reduce(s,KitchenEvent.Tick)};s=reduce(s,KitchenEvent.Action(CookAction.FLIP));assertEquals(1,s.cooking.face);repeat(23){s=reduce(s,KitchenEvent.Tick)};assertTrue(CookBook.ready(s));s=reduce(s,KitchenEvent.TakeOffHeat);s=reduce(s,KitchenEvent.Drop(GrabSlot.DISH,Station.PLATE));s=reduce(s,KitchenEvent.Drop(GrabSlot.DISH,Station.SERVE));assertTrue(s.cooking.hotServed);assertEquals(0,s.servedCount);assertEquals(Phase.PREP,s.phase);for(g in listOf(GrabSlot.A,GrabSlot.B,GrabSlot.C))s=reduce(s,KitchenEvent.Drop(g,Station.PLATE));s=reduce(s,KitchenEvent.ServeCold);assertEquals(Phase.REVIEW,s.phase);assertEquals(1,s.servedCount);assertEquals(s,reduce(s,KitchenEvent.ServeCold))}
 @Test fun timerDoesNotSilentlyCutOffEightCourseProgress(){val s=RecipePilot.serve(freshShift()).copy(shiftSeconds=0);assertEquals(Phase.PREP,reduce(s,KitchenEvent.NextOrder).phase)}
 @Test fun reviewingRetryCannotFarmMoney(){val s=RecipePilot.serve(freshShift());val retry=reduce(s,KitchenEvent.RetryOrder);assertEquals(0,retry.servedCount);assertEquals(0,retry.score);assertEquals(0,retry.reviewLog.size);assertTrue(s.active.id in retry.unlocked)}
 @Test fun gradingIncludesGrillBackAndDumplingHistory(){val grill=RecipePilot.cook(RecipePilot.dish("skewer"));assertTrue(CookBook.quality(grill.copy(cooking=grill.cooking.copy(b=listOf(55f,0f,0f))))<CookBook.quality(grill));val dumpling=RecipePilot.cook(RecipePilot.dish("dumplings"));assertTrue(dumpling.cooking.bottomHeat>0);assertTrue(dumpling.cooking.steamHeat>0)}

 @Test fun distinctEightDishCampaign(){assertEquals(8,NIGHT_MENU.size);assertEquals("egg-fried-rice",freshShift().active.id);assertEquals(8,NIGHT_MENU.map{it.id}.distinct().size);assertTrue(NIGHT_MENU.all{it.ingredients.size==3})}
 @Test fun everyDishNeedsRealCookingThenPlatingAndServing(){var s=freshShift();for(d in NIGHT_MENU){assertEquals(d.id,s.active.id);s=RecipePilot.serve(s);assertEquals(Phase.REVIEW,s.phase);assertTrue(s.lastReview!!.contains("品质"));assertTrue(d.id in s.unlocked);s=reduce(s,KitchenEvent.NextOrder)};assertEquals(8,s.servedCount);assertEquals(Phase.SHIFT_END,s.phase);assertFalse(s.running);assertEquals(8,s.reviewLog.size)}
 @Test fun ingredientsCanBeAddedInChosenOrder(){var s=freshShift();for(g in listOf(GrabSlot.B,GrabSlot.A,GrabSlot.C))s=reduce(s,KitchenEvent.Drop(g,Station.WOK));assertEquals(3,s.added);assertEquals(0,s.mistakes);assertEquals(Phase.COOK,s.phase)}
 @Test fun oneSpatulaDropCannotInstantlyCook(){var s=freshShift();for(g in listOf(GrabSlot.A,GrabSlot.B,GrabSlot.C))s=reduce(s,KitchenEvent.Drop(g,Station.WOK));s=reduce(s,KitchenEvent.Drop(GrabSlot.SPATULA,Station.WOK));assertEquals(Phase.COOK,s.phase);assertFalse(CookBook.ready(s));assertEquals(Phase.COOK,reduce(s,KitchenEvent.TakeOffHeat).phase)}
 @Test fun servingBeforePlateIsRejected(){val s=RecipePilot.cook(freshShift());val bad=reduce(s,KitchenEvent.Drop(GrabSlot.DISH,Station.SERVE));assertEquals(Phase.PLATE,bad.phase);assertFalse(bad.plated)}
 @Test fun burnerOffStopsCookingButNotOrderTime(){var s=reduce(freshShift(),KitchenEvent.Drop(GrabSlot.A,Station.WOK));s=reduce(s,KitchenEvent.SetHeat(Heat.OFF));repeat(60){s=reduce(s,KitchenEvent.Tick)};assertEquals(0f,s.cooking.a[0],0f);assertEquals(240,s.orderSeconds)}
 @Test fun highHeatBurnsAndRetryKeepsUnlocks(){var s=reduce(freshShift(setOf("skewer")),KitchenEvent.Drop(GrabSlot.A,Station.WOK));s=reduce(s,KitchenEvent.SetHeat(Heat.HIGH));repeat(100){s=reduce(s,KitchenEvent.Tick)};assertEquals(Phase.FAILED,s.phase);val failed=s;repeat(10){s=reduce(s,KitchenEvent.Tick)};assertEquals(failed,s);s=reduce(s,KitchenEvent.RetryOrder);assertEquals(Phase.PREP,s.phase);assertEquals(0,s.cooking.mask);assertTrue("skewer" in s.unlocked)}
 @Test fun emptyStoveDoesNotFailBecauseTimerExpires(){var s=freshShift();repeat(400){s=reduce(s,KitchenEvent.Tick)};assertEquals(Phase.PREP,s.phase);assertEquals(0,s.orderSeconds)}
 @Test fun grillingRequiresBothFaces(){var s=RecipePilot.dish("skewer");for(g in listOf(GrabSlot.A,GrabSlot.B,GrabSlot.C))s=reduce(s,KitchenEvent.Drop(g,Station.WOK));repeat(23){s=reduce(s,KitchenEvent.Tick)};assertFalse(CookBook.ready(s));assertEquals(0f,s.cooking.b[0],0f);s=reduce(s,KitchenEvent.Action(CookAction.FLIP));repeat(23){s=reduce(s,KitchenEvent.Tick)};assertTrue(CookBook.ready(s))}
 @Test fun boiledIngredientMustBeCookedBeforeLifting(){var s=reduce(RecipePilot.dish("oden"),KitchenEvent.Drop(GrabSlot.B,Station.WOK));s=reduce(s,KitchenEvent.Lift(1));assertEquals(0,s.cooking.lifted);repeat(30){s=reduce(s,KitchenEvent.Tick)};s=reduce(s,KitchenEvent.Lift(1));val amount=s.cooking.a[1];repeat(100){s=reduce(s,KitchenEvent.Tick)};assertEquals(amount,s.cooking.a[1],0f);assertNotEquals(Phase.FAILED,s.phase)}
 @Test fun seasoningSelectionAloneDoesNotApply(){var s=reduce(freshShift(),KitchenEvent.Drop(GrabSlot.A,Station.WOK));s=reduce(s,KitchenEvent.Taste("微辣"));assertEquals("",s.cooking.seasoned);s=reduce(s,KitchenEvent.Action(CookAction.SEASON));assertEquals("微辣",s.cooking.seasoned);assertTrue(CookBook.quality(s)<100)}
 @Test fun dumplingCoverRequiresWaterAndGoldenBottom(){var s=RecipePilot.dish("dumplings");for(g in listOf(GrabSlot.A,GrabSlot.B,GrabSlot.C))s=reduce(s,KitchenEvent.Drop(g,Station.WOK));s=reduce(s,KitchenEvent.Action(CookAction.COVER));assertEquals(0,s.cooking.stage);s=reduce(s,KitchenEvent.Action(CookAction.WATER));assertFalse(s.cooking.watered)}
 @Test fun finalHotSideAndColdPlateBothRequired(){val s=RecipePilot.cook(RecipePilot.dish("beer-plate"));assertTrue(s.cooking.hotStarted);assertTrue(s.cooking.coldServed);assertTrue(s.cooking.a[0]>=23f&&s.cooking.b[0]>=23f)}
 @Test fun lateHotRecipeCoolsAfterLeavingHeat(){val fresh=RecipePilot.cook(RecipePilot.dish("cold-noodle"));var cold=fresh;repeat(60){cold=reduce(cold,KitchenEvent.Tick)};assertTrue(CookBook.quality(cold)<CookBook.quality(fresh));assertEquals(fresh.cooking.a,cold.cooking.a)}
 @Test fun reviewPauseDoesNotSpendTime(){val s=RecipePilot.serve(freshShift());assertEquals(s,reduce(s,KitchenEvent.Tick));val restart=reduce(s,KitchenEvent.NewShift);assertEquals(s.unlocked,restart.unlocked);assertEquals(0,restart.servedCount)}
    @Test
    fun loadingIsStagedWithoutLongFixedDelaysOrFinishedDishDecode() {
        assertEquals(-0.18f, StallLayout.SURFACE_Y, 0.0001f)
        assertEquals(0.96f, StallLayout.VOLUME_HEIGHT, 0.0001f)
        assertTrue(StallBoot.coldSteps.isEmpty())
        assertEquals(BootSlot.WOK, StallBoot.deferredSteps.first().slot)
        val idle=StallBoot.deferredSteps.sumOf { it.idleBeforeMs + StallBoot.AFTER_DECODE_MS + StallBoot.AFTER_ATTACH_MS }
        assertTrue("Fixed waits must not consume an order budget", idle in 1000L..5000L)
        assertTrue(StallBoot.deferredSteps.none { it.slot == BootSlot.DISH })
    }

    @Test
    fun snapOnlyAcceptsANearbyStation() {
        assertEquals(Station.WOK, StallLayout.nearest(StallLayout.wok))
        assertEquals(Station.SERVE, StallLayout.nearest(Vec3(0.54f, StallLayout.serve.y, -0.10f)))
        assertNull(StallLayout.nearest(Vec3(0f, 0.45f, 0.45f)))
    }

    @Test
    fun openingStallFillsTheShallowVolumeWithRiceNearestTheUser() {
        val homes = StallLayout.slotHomes
        assertTrue(homes[0].z > homes[1].z && homes[1].z > homes[2].z)
        assertTrue(homes[0].dist(homes[1]) > 0.16f)
        assertTrue(homes[1].dist(homes[2]) > 0.16f)
        assertNull(StallLayout.nearest(homes[0]))
        assertNull(StallLayout.nearest(homes[1]))
        assertTrue(homes[1].dist(StallLayout.wok) > StallLayout.SNAP_RADIUS)
        assertTrue(StallLayout.wok.dist(StallLayout.plate) > StallLayout.SNAP_RADIUS)
        assertTrue(StallLayout.plate.dist(StallLayout.serve) > StallLayout.SNAP_RADIUS)
        val halfGrab = StallLayout.GRAB_SIZE / 2f
        homes.forEach { inside(it, halfGrab) }
        inside(StallLayout.spatulaHome, halfGrab)
        assertNull(StallLayout.nearest(StallLayout.spatulaHome))
        assertTrue(StallLayout.spatulaHome.dist(StallLayout.wok) > StallLayout.SNAP_RADIUS)
        val scenery =
            listOf(
                StallLayout.counter,
                StallLayout.wokBox,
                StallLayout.plateBox,
                StallLayout.serveBox,
            ) + StallLayout.pedestals
        scenery.forEach { box ->
            box.corners().forEach { inside(it, 0f) }
        }
    }

    private fun inside(
        point: Vec3,
        pad: Float,
    ) {
        assertTrue(kotlin.math.abs(point.x) + pad <= StallLayout.VOLUME_WIDTH / 2f)
        assertTrue(kotlin.math.abs(point.y) + pad <= StallLayout.VOLUME_HEIGHT / 2f)
        assertTrue(kotlin.math.abs(point.z) + pad <= StallLayout.VOLUME_DEPTH / 2f)
    }

}
