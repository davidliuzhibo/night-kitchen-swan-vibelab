package com.david.nightkitchen
import org.junit.Test
import org.junit.Assert.*
import com.david.nightkitchen.game.*
import com.david.nightkitchen.ui.market.components.*
class ProductionReplayTest {
 @Test fun actualTwelveOrdersSupplyCausalPreviewTransitions(){
  val log=ProductionReplay();var s=reduce(freshShift(),KitchenEvent.Market(MarketEvent.Open))
  fun frame()=if(s.market.stage in listOf(MarketStage.OFF,MarketStage.COOK))null else MarketPresentation.frame(s)
  fun act(id:String){val before=frame();s=reduce(s,KitchenEvent.Market(MarketPresentation.command(id)));log.record(id,before,frame(),if(frame()==null)"OrderBoard: cooking/lessons"else"")}
  for(n in 0..2){act("night:$n");repeat(4){
   val ticket=NightMarket.ticket(s.market);val i=ticket.dishes.indexOfFirst{NightMarket.method(it)==ticket.method};act("dish:$i");if(it==0)act("gear:"+listOf("STEADY","WARM","FLAVOR")[n]);act("cook")
   val host=frame();s=RecipePilot.serve(s);assertTrue(s.market.passed);log.record("native-progress",host,frame(),"真实料理reducer：操作、离火、装盘、出餐")
   if(it==0){act("retry");s=RecipePilot.serve(s);log.record("native-progress",null,frame(),"重做不重复奖励")}
   act("next")
  };assertEquals(MarketStage.COMPLETE,s.market.stage);act("open")};act("courses");assertEquals(MarketStage.OFF,s.market.stage)
  log.save()
 }
}
