package com.david.nightkitchen
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.david.nightkitchen.game.*
import com.david.nightkitchen.scene.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class) class KitchenLiveTest {
 private val inst=InstrumentationRegistry.getInstrumentation()
 private fun main(f:()->Unit)=inst.runOnMainSync(f)
 private fun waitFor(label:String,ms:Long=25000,p:()->Boolean){val end=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<end){if(p())return;SystemClock.sleep(65)};assertTrue(label,p())}
 private fun send(e:KitchenEvent){waitFor("assets before $e"){KitchenHub.assetsReady};main{KitchenHub.dispatch(e)}}
 private fun visual(label:String){if(InstrumentationRegistry.getArguments().getString("visualTour")!="true")return;main{if(!KitchenHub.playerPaused)KitchenHub.togglePause()};SystemClock.sleep(2000);val ack=inst.targetContext.filesDir.resolve("visual-tour-$label.ack");ack.delete();inst.sendStatus(2,android.os.Bundle().apply{putString("stream","VISUAL_TOUR:$label\n")});waitFor("capture $label",35000){ack.exists()};ack.delete();main{if(KitchenHub.playerPaused)KitchenHub.togglePause()}}
 @Test fun threeNightMarketsUseRealCookingAndLiveDishAssets(){
  inst.targetContext.startActivity(Intent(inst.targetContext,com.david.nightkitchen.platform.LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  waitFor("scene loaded",150000){KitchenHub.assetsReady&&NightLiveProbe.latest.ready}
  for(n in 0..2){
   send(KitchenEvent.Market(MarketEvent.Open));send(KitchenEvent.Market(MarketEvent.ChooseNight(n)))
   repeat(4){order->
    val ticket=NightMarket.ticket(KitchenHub.state.market)
    val choice=ticket.dishes.indexOfFirst{NightMarket.method(it)==ticket.method}
    send(KitchenEvent.Market(MarketEvent.ChooseDish(choice)))
    waitFor("order kit loaded"){NightLiveProbe.latest.dish==KitchenHub.state.active.id&&KitchenHub.assetsReady}
    send(KitchenEvent.Market(MarketEvent.Cook))
    NativeRecipePilot.cook(KitchenHub.state){event,expected->send(event);assertEquals(expected.phase,KitchenHub.state.phase)}
    waitFor("actual plated model loaded"){KitchenHub.assetsReady&&NightLiveProbe.latest.platedAssetReady&&"DISH" in NightLiveProbe.latest.visible}
    send(KitchenEvent.Drop(GrabSlot.DISH,Station.PLATE));send(KitchenEvent.Drop(GrabSlot.DISH,Station.SERVE))
    assertTrue("night=$n order=$order ${KitchenHub.state.lastReview}",KitchenHub.state.market.passed)
    assertEquals(MarketStage.REVIEW,KitchenHub.state.market.stage)
    assertEquals(KitchenHub.state,KitchenCheckpoint.decode(KitchenCheckpoint.encode(KitchenHub.state)))
    send(KitchenEvent.Market(MarketEvent.Next))
   }
   assertEquals(MarketStage.COMPLETE,KitchenHub.state.market.stage);assertEquals(14,KitchenHub.state.market.coins)
  }
  inst.targetContext.filesDir.resolve("market-native.txt").writeText("Twelve real cooking reducer orders and live model loading. Programmatic production dispatch and accelerated cooking ticks, NOT natural OS input or optical hands.")
 }
 @Test fun eightRecipesProductionDispatchLiveAssetsPauseAndCheckpoint(){
  inst.targetContext.startActivity(Intent(inst.targetContext,com.david.nightkitchen.platform.LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  waitFor("scene loaded",150000){KitchenHub.assetsReady&&NightLiveProbe.latest.ready}
  // Enter the teaching shift through the same production command as the menu.
  // A restored market session intentionally rejects the legacy NewShift action.
  main{KitchenHub.dispatch(KitchenEvent.Market(MarketEvent.Courses));if(KitchenHub.playerPaused)KitchenHub.togglePause()}
  assertEquals(MarketStage.OFF,KitchenHub.state.market.stage)
  assertEquals(0,KitchenHub.state.cooking.mask)
  for((index,d)in NIGHT_MENU.withIndex()){
   waitFor("dish ${d.id} loaded"){NightLiveProbe.latest.dish==d.id&&KitchenHub.assetsReady}
   visual("recipe-${index+1}-ready")
   val start=KitchenHub.state
   NativeRecipePilot.cook(start){event,expected->send(event);assertEquals("phase $event ${d.id}",expected.phase,KitchenHub.state.phase)}
   waitFor("cooked mesh loaded"){KitchenHub.assetsReady&&NightLiveProbe.latest.platedAssetReady&&"DISH" in NightLiveProbe.latest.visible}
   visual("recipe-${index+1}-cooked")
   main{KitchenHub.togglePause()};val frozen=KitchenHub.state;send(KitchenEvent.Tick);assertEquals(frozen,KitchenHub.state);main{KitchenHub.togglePause()}
   send(KitchenEvent.Drop(GrabSlot.DISH,Station.PLATE));waitFor("plated transform"){NightLiveProbe.latest.positions["DISH"]?.dist(StallLayout.plate)?.let{it<.025f}==true}
   send(KitchenEvent.Drop(GrabSlot.DISH,Station.SERVE));assertEquals(Phase.REVIEW,KitchenHub.state.phase);assertTrue(d.id in KitchenHub.state.unlocked)
   assertEquals(KitchenHub.state,KitchenCheckpoint.decode(KitchenCheckpoint.encode(KitchenHub.state)))
   visual("recipe-${index+1}-served");send(KitchenEvent.NextOrder)
  };assertEquals(Phase.SHIFT_END,KitchenHub.state.phase);assertEquals(8,KitchenHub.state.servedCount);assertEquals(8,KitchenHub.state.unlocked.size)
  main{KitchenHub.applyForDebug(freshShift().copy(active=dishById("beer-plate"),queue=emptyList()))}
  waitFor("hot-first kit loaded"){NightLiveProbe.latest.dish=="beer-plate"&&KitchenHub.assetsReady}
  send(KitchenEvent.StartHotSide);repeat(23){send(KitchenEvent.Tick)};send(KitchenEvent.Action(CookAction.FLIP));repeat(23){send(KitchenEvent.Tick)}
  assertTrue(CookBook.ready(KitchenHub.state));send(KitchenEvent.TakeOffHeat)
  waitFor("hot-first dish shown"){NightLiveProbe.latest.platedAssetReady&&"DISH" in NightLiveProbe.latest.visible&&KitchenHub.assetsReady}
  send(KitchenEvent.Drop(GrabSlot.DISH,Station.PLATE));send(KitchenEvent.Drop(GrabSlot.DISH,Station.SERVE));assertTrue(KitchenHub.state.cooking.hotServed);assertEquals(Phase.PREP,KitchenHub.state.phase)
  visual("dual-hot-served-first");for(g in listOf(GrabSlot.A,GrabSlot.B,GrabSlot.C))send(KitchenEvent.Drop(g,Station.PLATE));send(KitchenEvent.ServeCold);assertEquals(Phase.REVIEW,KitchenHub.state.phase);assertEquals(1,KitchenHub.state.servedCount)
  visual("dual-both-served");assertEquals(KitchenHub.state,KitchenCheckpoint.decode(KitchenCheckpoint.encode(KitchenHub.state)))
  inst.targetContext.filesDir.resolve("overhaul-native.txt").writeText("Eight recipes: actual production dispatch, live model loading/plated transforms, pause/checkpoint. Synthetic events and accelerated cooking seconds; not OS taps or optical-hand acceptance.")
 }
}
