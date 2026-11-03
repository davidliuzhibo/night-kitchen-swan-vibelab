package com.david.nightkitchen
import com.david.nightkitchen.game.*
import org.junit.Assert.*
import org.junit.Test
class EmulatorPlayTest {
 @Test fun debugDropUsesProductionRuleWithoutInstantCook(){var s=freshShift();repeat(4){val e=EmulatorPlay.grabNext(s)!!;assertEquals(KitchenEvent.Drop(s.expectedGrab!!,s.expectedStation!!),e);s=reduce(s,e)};assertEquals(Phase.COOK,s.phase);assertNull(EmulatorPlay.plate(s));assertNull(EmulatorPlay.serve(s))}
 @Test fun debugServeStillRequiresPlating(){val s=RecipePilot.cook(freshShift());assertNotNull(EmulatorPlay.plate(s));assertNull(EmulatorPlay.serve(s));val p=reduce(s,EmulatorPlay.plate(s)!!);assertEquals(Phase.REVIEW,reduce(p,EmulatorPlay.serve(p)!!).phase)}
 @Test fun debugFastForwardIsExplicitAndKeepsProgress(){val s=RecipePilot.serve(freshShift());val end=EmulatorPlay.fastForwardShift(s);assertEquals(Phase.SHIFT_END,end.phase);assertEquals(s.score,end.score);assertEquals(s.unlocked,end.unlocked)}
}
