package com.david.nightkitchen
import com.david.nightkitchen.game.*
import org.junit.Assert.*
import org.junit.Test
class KitchenCheckpointTest {
 @Test fun everyHeatStageAndDishRoundTrips(){for(d in NIGHT_MENU){val s=RecipePilot.dish(d.id);val cooked=RecipePilot.cook(s){assertEquals(it,KitchenCheckpoint.decode(KitchenCheckpoint.encode(it)))};assertEquals(cooked,KitchenCheckpoint.decode(KitchenCheckpoint.encode(cooked)))}}
 @Test fun brokenCheckpointIsRejected(){val d=KitchenCheckpoint.encode(freshShift());for(x in listOf(null,"","invalid",d.take(d.length/2),"A".repeat(50000)))assertNull(KitchenCheckpoint.decode(x))}
 @Test fun invalidHeatRejected(){val s=freshShift().copy(cooking=Cooking(a=listOf(Float.NaN,0f,0f)));assertNull(KitchenCheckpoint.decode(KitchenCheckpoint.encode(s)))}
 @Test fun failureAndSettlementRoundTrip(){for(p in listOf(Phase.FAILED,Phase.SHIFT_END)){val s=freshShift().copy(phase=p);assertEquals(s,KitchenCheckpoint.decode(KitchenCheckpoint.encode(s)))}}
}
