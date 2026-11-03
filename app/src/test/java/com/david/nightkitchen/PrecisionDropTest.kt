package com.david.nightkitchen

import com.david.nightkitchen.scene.*
import com.david.nightkitchen.game.Station
import org.junit.Assert.*
import org.junit.Test

class PrecisionDropTest {
    private val wok = GesturePlay.Decision.Drop(Station.WOK)
    @Test fun bothMeasuredNaturalReleasesOverTheRamenPotAreRecognised() {
        assertEquals(wok,GesturePlay.resolve(Vec3(-.001727547f,-.060421262f,.16f),false))
        assertEquals(wok,GesturePlay.resolve(Vec3(.013440014f,-.07248003f,.16f),false))
    }
    @Test fun slightMovementWhilePickingUpSausageDoesNotThrowItIntoThePot() {
        val b=StallLayout.slotHomes[1];val end=b.copy(x=b.x+GesturePlay.travel(.005f))
        assertEquals(GesturePlay.Decision.Home,GesturePlay.resolve(end,false))
        assertEquals(-GesturePlay.travel(.005f),GesturePlay.travel(-.005f),.00001f)
    }
    @Test fun allThreeIngredientsReachThePotWithTwentyCentimetresOfLevelSeatedTravel() {
        for (start in StallLayout.slotHomes) {
            val target=StallLayout.wok
            val delta=(target.x-start.x).coerceIn(-GesturePlay.travel(.20f),GesturePlay.travel(.20f))
            assertEquals(wok,GesturePlay.resolve(start.copy(x=start.x+delta),false))
        }
    }
    @Test fun returningToTheWorktopAndLosingTrackingNeverAutoServe() {
        for (start in StallLayout.slotHomes) assertEquals(GesturePlay.Decision.Home,GesturePlay.resolve(start,false))
        val above=StallLayout.wok.copy(y=StallLayout.wok.y+.25f)
        assertEquals(GesturePlay.Decision.Home,GesturePlay.resolve(above,false))
        assertEquals(GesturePlay.Decision.Hold,GesturePlay.resolve(Vec3(-.001727547f,-.060421262f,.16f),true))
    }
}
