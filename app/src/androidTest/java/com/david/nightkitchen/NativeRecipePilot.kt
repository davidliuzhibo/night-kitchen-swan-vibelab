package com.david.nightkitchen
import com.david.nightkitchen.game.*
import org.junit.Assert.*
object NativeRecipePilot {
 fun cook(start:KitchenState,observe:(KitchenEvent,KitchenState)->Unit={plainEvent,plainState->}):KitchenState {
  var s=start
  fun send(e:KitchenEvent){s=reduce(s,e);observe(e,s);check(s.phase!=Phase.FAILED){"${s.active.id}: ${s.cooking}"}}
  fun ticks(n:Int){repeat(n){send(KitchenEvent.Tick)}}
  fun add(i:Int){send(KitchenEvent.Drop(GrabSlot.entries[i],if(s.active.id=="beer-plate")Station.PLATE else Station.WOK))}
  send(KitchenEvent.Taste(CookBook.request(s)))
  when(s.active.id){
   "skewer","beer-plate"->{(0..2).forEach(::add);if(s.active.id=="beer-plate"){send(KitchenEvent.StartHotSide);send(KitchenEvent.ServeCold)};send(KitchenEvent.Action(CookAction.SEASON));ticks(23);send(KitchenEvent.Action(CookAction.FLIP));ticks(23)}
   "oden","instant-noodles"->{val times=CookBook.times(s.active.id);val max=times.max().toInt();for(t in 0..max){for(i in 0..2)if(t==max-times[i].toInt())add(i);if(t==max)break;send(KitchenEvent.Tick)};(0..2).forEach{send(KitchenEvent.Lift(it))};send(KitchenEvent.Action(CookAction.SEASON))}
   "dumplings"->{(0..2).forEach(::add);send(KitchenEvent.Action(CookAction.SEASON));ticks(18);send(KitchenEvent.Action(CookAction.WATER));ticks(2);send(KitchenEvent.Action(CookAction.COVER));ticks(22);send(KitchenEvent.Action(CookAction.UNCOVER));ticks(10)}
   "cold-noodle"->{add(0);send(KitchenEvent.Action(CookAction.SEASON));ticks(8);add(1);ticks(14);add(2);send(KitchenEvent.Action(CookAction.BRUSH));ticks(10);send(KitchenEvent.Action(CookAction.ROLL))}
   else->{add(0);send(KitchenEvent.Action(CookAction.SEASON));ticks(8);add(1);send(KitchenEvent.Action(CookAction.STIR));ticks(8);send(KitchenEvent.Action(CookAction.STIR));ticks(10);if(s.active.id!="egg-fried-rice")add(2);send(KitchenEvent.Action(CookAction.STIR));ticks(12)}
  }
  assertTrue("not cooked ${s.active.id}: ${s.cooking}",CookBook.ready(s));send(KitchenEvent.TakeOffHeat);assertEquals(Phase.PLATE,s.phase);return s
 }
 fun serve(s:KitchenState):KitchenState=reduce(reduce(cook(s),KitchenEvent.Drop(GrabSlot.DISH,Station.PLATE)),KitchenEvent.Drop(GrabSlot.DISH,Station.SERVE))
 fun dish(id:String)=freshShift().copy(active=dishById(id),queue=emptyList())
}
