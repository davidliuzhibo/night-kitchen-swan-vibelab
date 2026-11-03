package com.david.nightkitchen

import com.david.nightkitchen.game.Station
import com.david.nightkitchen.game.GrabSlot
import com.david.nightkitchen.scene.GesturePlay
import com.david.nightkitchen.scene.StallLayout
import com.david.nightkitchen.scene.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GesturePlayTest {
    @Test
    fun liftedSoupIngredientsDoNotDuplicateTheCombinedDishOrBlockItsBell() {
        for (id in listOf("oden", "instant-noodles")) {
            val cooking = RecipePilot.cook(RecipePilot.dish(id))
            assertEquals(7, cooking.cooking.lifted)
            for (slot in listOf(GrabSlot.A, GrabSlot.B, GrabSlot.C)) {
                val simmering = cooking.copy(phase = com.david.nightkitchen.game.Phase.COOK)
                assertTrue(StallLayout.rest(simmering, slot) != StallLayout.hidden)
                for (phase in listOf(com.david.nightkitchen.game.Phase.PLATE,
                    com.david.nightkitchen.game.Phase.SERVE, com.david.nightkitchen.game.Phase.REVIEW)) {
                    val combined = cooking.copy(phase = phase)
                    assertEquals("$id $phase $slot", StallLayout.hidden, StallLayout.rest(combined, slot))
                    assertTrue(StallLayout.dishVisible(combined))
                }
            }
        }
    }
    @Test
    fun platedFoodRemainsPickableInFrontOfPlateTarget() {
        val food = StallLayout.hitTarget(GrabSlot.DISH)
        val plate = StallLayout.stationHitTarget(Station.PLATE)
        assertTrue(abs(food.lift - plate.lift) + plate.radius < food.radius)
        val bell = StallLayout.stationHitTarget(Station.SERVE)
        assertTrue(StallLayout.serve.dist(StallLayout.plate) > food.radius + bell.radius)
    }
    @Test
    fun pointerUsesTheSamePlayInputAsHandGestures() {
        assertTrue(GesturePlay.allows(GesturePlay.POINTER_KIND))
        assertTrue(GesturePlay.allows("Pointer"))
        listOf("DirectPinch", "GazePinch", "RayBasedPinch", "Poke", "Unknown").forEach { kind ->
            assertTrue(kind, GesturePlay.allows(kind))
        }
    }

    @Test
    fun cancelInsideTheSnapDoesNotPlaceAnIngredient() {
        val decision = GesturePlay.resolve(StallLayout.wok, cancelled = true)
        assertEquals(GesturePlay.Decision.Hold, decision)
        assertEquals(Station.WOK, (GesturePlay.resolve(StallLayout.wok, false) as GesturePlay.Decision.Drop).station)
    }

    @Test
    fun cancelInOpenSpaceHoldsInsteadOfHoming() {
        val airborne = Vec3(0f, 0.1f, 0.2f)
        assertEquals(null, StallLayout.nearest(airborne))
        assertEquals(GesturePlay.Decision.Hold, GesturePlay.resolve(airborne, cancelled = true))
        assertEquals(GesturePlay.Decision.Home, GesturePlay.resolve(airborne, cancelled = false))
    }

    @Test
    fun releaseOnTheMiddleHomeDoesNotCountAsAWokDrop() {
        val home = StallLayout.slotHomes[1]
        assertTrue(home.dist(StallLayout.wok) > StallLayout.SNAP_RADIUS)
        assertEquals(GesturePlay.Decision.Home, GesturePlay.resolve(home, cancelled = false))
        assertEquals(GesturePlay.Decision.Hold, GesturePlay.resolve(home, cancelled = true))
    }

    @Test
    fun pinchTargetStaysSeparableAndInsideTheVolume() {
        val homes = listOf(GrabSlot.A to StallLayout.slotHomes[0], GrabSlot.B to StallLayout.slotHomes[1],
            GrabSlot.C to StallLayout.slotHomes[2], GrabSlot.SPATULA to StallLayout.spatulaHome)
        for(i in homes.indices) for(j in i+1 until homes.size) {
            val a=StallLayout.hitTarget(homes[i].first); val b=StallLayout.hitTarget(homes[j].first)
            val pa=homes[i].second.copy(y=homes[i].second.y+a.lift)
            val pb=homes[j].second.copy(y=homes[j].second.y+b.lift)
            assertTrue("${homes[i].first} overlaps ${homes[j].first}",pa.dist(pb)>(a.radius+b.radius)*1.3f)
        }
        val clamped = StallLayout.withinReach(Vec3(4f, -4f, 4f))
        assertTrue(abs(clamped.x) < StallLayout.VOLUME_WIDTH / 2f)
        assertTrue(abs(clamped.y) < StallLayout.VOLUME_HEIGHT / 2f)
        assertTrue(abs(clamped.z) < StallLayout.VOLUME_DEPTH / 2f)
        assertEquals(StallLayout.VOLUME_WIDTH, 1.28f, 0.0001f)
        assertEquals(StallLayout.VOLUME_HEIGHT, 0.96f, 0.0001f)
        assertEquals(StallLayout.VOLUME_DEPTH, 0.62f, 0.0001f)
        assertEquals(StallLayout.SURFACE_Y, -0.18f, 0.0001f)
        assertTrue(GesturePlay.TRACKING_GRACE_MS in 300L..700L)
    }
}
