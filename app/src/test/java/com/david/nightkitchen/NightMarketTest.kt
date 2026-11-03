package com.david.nightkitchen
import com.david.nightkitchen.game.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64

class NightMarketTest {
 private fun event(s:KitchenState,e:MarketEvent)=reduce(s,KitchenEvent.Market(e))
 private fun night(i:Int)=event(event(freshShift(),MarketEvent.Open),MarketEvent.ChooseNight(i))
 private fun prepare(s:KitchenState):KitchenState {
  val ticket=NightMarket.ticket(s.market)
  val index=ticket.dishes.indexOfFirst{NightMarket.method(it)==ticket.method}
  return event(event(s,MarketEvent.ChooseDish(index)),MarketEvent.Cook)
 }
 @Test fun twelveOrdersRequireRealCookingAndPersistEachResult(){
  for(n in 0..2){var s=night(n)
   repeat(4){ticket->
    s=prepare(s);assertEquals(ticket,s.market.ticket)
    assertEquals(s,event(s,MarketEvent.Next))
    s=RecipePilot.serve(s)
    assertEquals(s.lastReview,MarketStage.REVIEW,s.market.stage);assertTrue(s.lastReview,s.market.passed)
    assertEquals(s,KitchenCheckpoint.decode(KitchenCheckpoint.encode(s)))
    s=event(s,MarketEvent.Next)
   }
   assertEquals(MarketStage.COMPLETE,s.market.stage);assertTrue(s.market.grades.all{it>=s.market.threshold});assertEquals(14,s.market.coins)
   val best=s.market.bests[n];s=event(s,MarketEvent.Courses);assertEquals(best,s.market.bests[n]);assertFalse(s.market.active)
  }
 }
 @Test fun wrongMethodCannotAdvanceEvenWhenCookedWell(){
  var s=event(event(night(0),MarketEvent.ChooseDish(1)),MarketEvent.Cook)
  s=RecipePilot.serve(s);assertFalse(s.market.passed);assertEquals(6,s.market.coins);assertEquals(s,event(s,MarketEvent.Next))
  assertEquals(MarketStage.ORDER,event(s,MarketEvent.Reselect).market.stage)
 }
 @Test fun wrongFlavorAndColdFoodAreRecoverableWithoutReward(){
  var s=prepare(night(2));s=RecipePilot.cook(s)
  s=s.copy(cooking=s.cooking.copy(seasoned="少盐"),holdSeconds=500)
  s=reduce(reduce(s,KitchenEvent.Drop(GrabSlot.DISH,Station.PLATE)),KitchenEvent.Drop(GrabSlot.DISH,Station.SERVE))
  assertFalse(s.market.passed);assertEquals(6,s.market.coins)
  s=RecipePilot.serve(event(s,MarketEvent.Retry));assertTrue(s.market.passed)
 }
 @Test fun retryAndRepeatedCallbacksCannotFarmCoins(){
  var s=RecipePilot.serve(prepare(night(0)));assertEquals(8,s.market.coins)
  repeat(3){s=reduce(s,KitchenEvent.Drop(GrabSlot.DISH,Station.SERVE));assertEquals(8,s.market.coins)}
  s=RecipePilot.serve(event(s,MarketEvent.Retry));assertEquals(8,s.market.coins)
  s=event(s,MarketEvent.Next);assertEquals(1,s.market.ticket);assertEquals(s,event(s,MarketEvent.Next))
 }
 @Test fun equipmentCostsOnceAndChangesRealHeatCoolingOrQuality(){
  val base=event(night(0),MarketEvent.ChooseDish(0))
  val steady=event(base,MarketEvent.Equip(StallGear.STEADY));assertEquals(2,steady.market.coins);assertEquals(steady,event(steady,MarketEvent.Equip(StallGear.STEADY)))
  assertEquals(steady,event(steady,MarketEvent.Equip(StallGear.WARM)))
  val hot=reduce(event(steady,MarketEvent.Cook),KitchenEvent.SetHeat(Heat.HIGH));assertEquals(Heat.MEDIUM,hot.cooking.heat)
  val warm=event(base,MarketEvent.Equip(StallGear.WARM)).copy(holdSeconds=120)
  assertEquals(3,CookBook.coolingPenalty(warm));assertEquals(12,CookBook.coolingPenalty(warm.copy(market=warm.market.copy(gear=StallGear.ORIGINAL))))
  val flavored=event(base,MarketEvent.Equip(StallGear.FLAVOR)).copy(ticketMistakes=4)
  assertEquals(8,CookBook.quality(flavored)-CookBook.quality(flavored.copy(market=flavored.market.copy(gear=StallGear.ORIGINAL))))
 }
 @Test fun checkpointAndLegacyV5PreserveProgress(){
  val s=RecipePilot.cook(prepare(night(1)))
  assertEquals(s,KitchenCheckpoint.decode(KitchenCheckpoint.encode(s)))
  val bytes=Base64.getDecoder().decode(KitchenCheckpoint.encode(s))
  val tail=ByteArrayOutputStream();DataOutputStream(tail).use{MarketCheckpoint.write(it,s.market)}
  val legacy=bytes.copyOf(bytes.size-tail.size());legacy[3]=5
  val restored=KitchenCheckpoint.decode(Base64.getEncoder().encodeToString(legacy))!!
  assertEquals(s.cooking,restored.cooking);assertEquals(MarketStage.OFF,restored.market.stage)
 }
 @Test fun menusFreezeTimeAndRejectPrematureCommands(){
  var s=night(0);val prior=s
  repeat(100){s=reduce(s,KitchenEvent.Tick)};assertEquals(prior,s)
  for(e in listOf(MarketEvent.Next,MarketEvent.Cook,MarketEvent.Retry,MarketEvent.Equip(StallGear.FLAVOR)))assertEquals(s,event(s,e))
 }
}
