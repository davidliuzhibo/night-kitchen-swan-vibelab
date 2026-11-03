package com.david.nightkitchen
import com.david.nightkitchen.ui.design.*
import org.junit.Test
import org.junit.Assert.*
import com.david.nightkitchen.game.*
import com.david.nightkitchen.ui.market.components.*
import com.david.nightkitchen.ui.market.*
import com.david.nightkitchen.data.repository.MarketRepository
import kotlinx.coroutines.flow.MutableStateFlow

class PresentationContractTest {
 @Test fun everyMenuStateAndDisabledCoinBoundaryRendersFromProduction(){
  val frames=mutableListOf<Pair<String,PanelFrame>>()
  for(stage in listOf(MarketStage.CHOOSE,MarketStage.ORDER,MarketStage.GEAR,MarketStage.REVIEW,MarketStage.COMPLETE))for(night in 0..2)for(last in listOf(false,true)) {
   val s=freshShift().copy(market=MarketSession(stage=stage,night=night,ticket=if(last)3 else 0,coins=if(last)0 else 6,passed=last,review="锅气刚好，调味符合客人要求。"))
   val f=MarketPresentation.frame(s);commands(f).forEach{MarketPresentation.command(it)};frames+=("${stage.name}-$night-$last" to f)
  };saveFrames(frames)
 }
 @Test fun purchaseCannotLeaveGearOrDoubleSpendAndStartHandsOffToCooking(){
  var s=NightMarket.reduce(freshShift(),MarketEvent.Open);s=NightMarket.reduce(s,MarketPresentation.command("night:0"));s=NightMarket.reduce(s,MarketPresentation.command("dish:0"))
  s=NightMarket.reduce(s,MarketPresentation.command("gear:STEADY"));assertEquals(2,s.market.coins)
  val f=MarketPresentation.frame(s);assertFalse(button(f,"gear-steady").get("enabled").asBoolean);assertFalse(button(f,"gear-warm").get("enabled").asBoolean)
  assertEquals(s,NightMarket.reduce(s,MarketPresentation.command("gear:STEADY")))
  s=NightMarket.reduce(s,MarketPresentation.command("cook"));assertEquals(MarketStage.COOK,s.market.stage);assertTrue(s.running)
 }
 @Test fun failedCookingStaysInHostAndRetryKeepsMarketIdentity(){
  val m=MarketSession(stage=MarketStage.COOK,night=2,ticket=3,coins=2,gear=StallGear.WARM,credited=true)
  val burned=freshShift().copy(phase=Phase.FAILED,market=m)
  val retry=reduce(burned,KitchenEvent.RetryOrder);assertEquals(MarketStage.COOK,retry.market.stage);assertEquals(m.night,retry.market.night);assertEquals(m.coins,retry.market.coins);assertNotEquals(Phase.FAILED,retry.phase)
 }
 @Test fun failedReviewDoesNotEnableNextAndCoursesKeepBests(){
  val s=freshShift().copy(market=MarketSession(stage=MarketStage.REVIEW,passed=false,bests=listOf(200,150,0)))
  assertFalse(button(MarketPresentation.frame(s),"next").get("enabled").asBoolean)
  assertEquals(s,NightMarket.reduce(s,MarketPresentation.command("next")))
  val choose=NightMarket.reduce(s,MarketEvent.Open);val host=NightMarket.reduce(choose,MarketPresentation.command("courses"));assertEquals(MarketStage.OFF,host.market.stage);assertEquals(s.market.bests,host.market.bests)
 }
 @Test fun marketViewModelPreservesCapturedOrderToken(){
  val initial=freshShift().copy(market=MarketSession(stage=MarketStage.GEAR))
  val states=MutableStateFlow(initial);var calls=0;var seen:MarketSession?=null
  val vm=MarketViewModel(object:MarketRepository{override val states=states;override fun command(event:MarketEvent,expected:MarketSession){calls++;seen=expected}})
  val old=vm.present(vm.state.value);states.value=initial.copy(market=initial.market.copy(coins=1))
  vm.onEvent(MarketUiEvent.Command(MarketEvent.Cook,old.selection));assertEquals(1,calls);assertEquals(initial.market,seen);assertEquals(1,vm.present(vm.state.value).selection.revision.coins)
 }
}

private val contract get() = PanelContract.parse(java.io.File("src/main/assets/design/panel.json").readText())
private fun flatten(n:com.google.gson.JsonObject):List<com.google.gson.JsonObject> = listOf(n)+n.getAsJsonArray("children").flatMap{flatten(it.asJsonObject)}
private fun button(f:PanelFrame,id:String)=flatten(contract.snapshot(f)).first{it.get("id").asString==id}
private fun commands(f:PanelFrame)=flatten(contract.snapshot(f)).filter{it.has("command")}.map{it.get("command").asString}.toSet()
private fun saveFrames(frames:List<Pair<String,PanelFrame>>) {
 val gson=com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
 val out=com.google.gson.JsonArray()
 frames.forEach{(name,frame)->out.add(com.google.gson.JsonObject().apply{
  addProperty("name",name);addProperty("state",frame.state);add("values",gson.toJsonTree(frame.values));add("tree",contract.snapshot(frame))
 })}
 java.io.File("build/presentation-fixtures.json").apply{parentFile!!.mkdirs();writeText(gson.toJson(out))}
 assertTrue(frames.isNotEmpty())
 frames.forEach{(_,frame)->assertFalse(contract.snapshot(frame).toString().contains("Missing presentation"))}
}
