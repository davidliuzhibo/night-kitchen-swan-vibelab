package com.david.nightkitchen.game

import kotlin.math.*

enum class Heat(val label:String,val rate:Float) { OFF("关火",0f),LOW("小火",.55f),MEDIUM("中火",1f),HIGH("大火",1.65f) }
enum class CookAction(val label:String) { STIR("翻炒一次"),FLIP("翻到另一面"),WATER("沿锅边加水"),COVER("盖上锅盖"),UNCOVER("揭盖收汁"),BRUSH("均匀刷酱"),ROLL("卷起冷面"),SEASON("加入所选调味") }
data class Cooking(val mask:Int=0,val heat:Heat=Heat.MEDIUM,val a:List<Float> = listOf(0f,0f,0f),val b:List<Float> = listOf(0f,0f,0f),val face:Int=0,val lifted:Int=0,val stirs:Int=0,val lastAction:Int = -100,val time:Int=0,val stage:Int=0,val stageHeat:Float=0f,val watered:Boolean=false,val covered:Boolean=false,val brushed:Boolean=false,val rolled:Boolean=false,val taste:String="",val message:String="",val coldServed:Boolean=false,val hotStarted:Boolean=false,val seasoned:String="",val hotServed:Boolean=false,val bottomHeat:Float=0f,val steamHeat:Float=0f,val messageUntil:Int=0) {
 fun contains(i:Int)=mask and (1 shl i)!=0
 fun removed(i:Int)=lifted and (1 shl i)!=0
}
object CookBook {
 fun times(id:String):List<Float> = when(id){"oden"->listOf(48f,30f,38f);"instant-noodles"->listOf(44f,28f,14f);"skewer","beer-plate"->listOf(23f,23f,23f);"dumplings"->listOf(18f,18f,18f);"cold-noodle"->listOf(32f,24f,10f);else->listOf(38f,30f,12f)}
 fun boiling(id:String)=id in listOf("oden","instant-noodles")
 fun grilling(id:String)=id in listOf("skewer","beer-plate")
 fun request(id:String)=when(id){"egg-fried-rice"->"不加葱";"chow-mein"->"少盐";"skewer"->"少辣";"cold-noodle"->"酸甜";else->"原味"}
 fun request(s:KitchenState)=NightMarket.request(s)
 fun requiredMask(id:String)=when(id){"egg-fried-rice"->3;"skewer"->1;else->7}
 fun loaded(s:KitchenState)=s.cooking.mask and requiredMask(s.active.id)==requiredMask(s.active.id)
 fun actions(s:KitchenState):List<CookAction> = (when(s.active.id){
 "skewer","beer-plate"->if(s.active.id=="skewer"||s.cooking.hotStarted&&!s.cooking.hotServed)listOf(CookAction.FLIP)else emptyList()
 "dumplings"->when(s.cooking.stage){0->listOf(if(s.cooking.watered)CookAction.COVER else CookAction.WATER);1->listOf(CookAction.UNCOVER);else->emptyList()}
 "cold-noodle"->if(s.cooking.rolled)emptyList()else listOf(if(s.cooking.brushed)CookAction.ROLL else CookAction.BRUSH)
 "oden","instant-noodles"->emptyList()
 else->listOf(CookAction.STIR)
 }) + if(s.cooking.taste.isNotBlank()&&s.cooking.seasoned.isBlank())listOf(CookAction.SEASON)else emptyList()
 fun percent(s:KitchenState,i:Int):Int {val c=s.cooking;return ((if(grilling(s.active.id))min(c.a[i],c.b[i])else c.a[i])/times(s.active.id)[i]*100).toInt().coerceIn(0,200)}
 fun ready(s:KitchenState):Boolean {val c=s.cooking;val id=s.active.id
  if(id=="beer-plate")return c.hotStarted&&!c.hotServed&&c.a[0]>=23f&&c.b[0]>=23f
  if(!loaded(s))return false
  if(id=="dumplings")return c.stage==2&&c.stageHeat>=10f
  if(grilling(id))return c.a[0]>=23f&&c.b[0]>=23f
  if(boiling(id))return c.lifted==7
  if(id=="cold-noodle")return c.rolled
  return (0..2).filter{requiredMask(id) and (1 shl it)!=0}.all{c.a[it]>=times(id)[it]}&&c.stirs>=3
 }
 fun hint(s:KitchenState):String {val c=s.cooking;val id=s.active.id
  if(c.message.isNotBlank())return c.message
  if(id=="beer-plate")return when{c.hotServed->"热串已送达。冷拼三样摆齐，再点送出冷拼。";c.coldServed->"冷拼已送达；热串两面都达到100%后离火装盘。";else->"冷拼和热串可以任意先送。冷拼摆到白盘；热串开火后需翻面。"}
  if(c.mask==0)return "客人要${request(s)}。先把食材放入${s.active.stationName}，再根据熟度调整火力。"
  return when(id){
   "oden","instant-noodles"->"食材熟得快慢不同。100%表示刚好，分别捞起；青菜晚放。"
   "skewer"->"正反两面分别受热。第一面达到100%后翻面，另一面也达到100%再离火。"
   "dumplings"->when(c.stage){0->if(c.stageHeat<18f)"先用中火煎出金黄底部。" else "底已金黄：加水，再盖锅盖。";1->if(c.stageHeat<22f)"盖着焖熟，别急着开盖。" else "焖熟了，揭盖转中火收汁。";else->"收汁达到10火候后离火，别把脆底烧糊。"}
   "cold-noodle"->"先让面皮和蛋受热，刷酱后及时卷起；客人要酸甜口。"
   else->"中火受热，间隔翻炒至少3次。熟度达到100%后离火；大火更快，也更容易焦。"
  }
 }
 fun tick(s:KitchenState):KitchenState {if(s.phase !in listOf(Phase.PREP,Phase.COOK))return s
  var c=s.cooking.copy(time=s.cooking.time+1,message=if(s.cooking.time+1>=s.cooking.messageUntil)""else s.cooking.message);val id=s.active.id;val r=if(s.market.active&&s.market.gear==StallGear.STEADY)minOf(c.heat.rate,Heat.MEDIUM.rate)else c.heat.rate
  val heatOn=id!="beer-plate"||c.hotStarted&&!c.hotServed
  if(!heatOn)return s.copy(cooking=c)
  if(id=="dumplings")c=c.copy(stageHeat=c.stageHeat+if(c.mask==7&&(c.stage!=1||c.covered))r else 0f)
  else {val a=c.a.toMutableList();val b=c.b.toMutableList();for(i in 0..2)if((c.contains(i)||id=="beer-plate")&&!c.removed(i)&&(!grilling(id)||i==0)){if(grilling(id)&&c.face==1)b[i]+=r else a[i]+=r};c=c.copy(a=a,b=b)}
  val burnt= if(id=="dumplings")c.stageHeat>when(c.stage){0->42f;1->58f;else->30f} else (0..2).any{i->(!grilling(id)||i==0)&&!c.removed(i)&&(c.a[i]>times(id)[i]*2.1f+12||c.b[i]>times(id)[i]*2.1f+12)}
  return if(burnt)s.copy(cooking=c.copy(heat=Heat.OFF),phase=Phase.FAILED,feedback=Feedback.ORDER_FAILED,lastReview="火太大，食材焦了。重做时用中火，先熟的先捞起。") else s.copy(cooking=c)
 }
 fun action(s:KitchenState,action:CookAction):KitchenState {if(s.phase !in listOf(Phase.PREP,Phase.COOK)||(s.cooking.mask==0&&!(s.active.id=="beer-plate"&&s.cooking.hotStarted&&!s.cooking.hotServed)))return s
  var c=s.cooking;val id=s.active.id
  if(c.time-c.lastAction<2)return s
  if(action !in actions(s))return s
  c=when(action){
   CookAction.SEASON->c.copy(seasoned=c.taste,lastAction=c.time,message=if(c.taste=="不加葱")"已记下不加葱；请保持葱料未下锅。"else "已加入${c.taste}调味。")
   CookAction.STIR->if(c.time-c.lastAction>=5)c.copy(stirs=c.stirs+1,lastAction=c.time,message="翻炒 ${c.stirs+1} 次，继续看火。")else c.copy(message="慢一点，等食材受热后再翻炒。")
   CookAction.FLIP->c.copy(face=1-c.face,lastAction=c.time,message="已翻到${if(c.face==0)"反"else"正"}面，另一面停止受热。")
   CookAction.WATER->if(c.stage==0&&c.stageHeat>=18f)c.copy(watered=true,lastAction=c.time,message="水已加好，现在盖锅盖。")else c.copy(message="锅底还没煎香，先等底部达到18火候。")
   CookAction.COVER->if(c.watered)c.copy(stage=1,bottomHeat=c.stageHeat,stageHeat=0f,covered=true,lastAction=c.time,message="开始焖熟。")else c.copy(message="先沿锅边加水，再盖盖。")
   CookAction.UNCOVER->if(c.stageHeat>=22f)c.copy(stage=2,steamHeat=c.stageHeat,stageHeat=0f,covered=false,lastAction=c.time,message="揭盖了，转中火收汁。")else c.copy(message="饺子还没焖熟，请再等一会。")
   CookAction.BRUSH->if(c.a[0]>=15f&&c.contains(2))c.copy(brushed=true,lastAction=c.time,message="酱已刷匀；等面皮和蛋熟透，再卷起。")else c.copy(message="面皮先受热，并把酱放上铁板。")
   CookAction.ROLL->if(c.brushed&&(0..2).all{c.a[it]>=times(id)[it]})c.copy(rolled=true,heat=Heat.OFF,lastAction=c.time,message="卷好了，可以离火装盘。")else c.copy(message="先熟透、刷酱，才能卷起。")
  };return s.copy(cooking=c)
 }
 fun lift(s:KitchenState,i:Int):KitchenState {if(!boiling(s.active.id)||i !in 0..2||!s.cooking.contains(i)||s.cooking.removed(i))return s
  val c=s.cooking;return if(c.a[i]<times(s.active.id)[i])s.copy(cooking=c.copy(message="${s.active.ingredients[i].name}还没熟，再煮一会。"))else s.copy(cooking=c.copy(lifted=c.lifted or (1 shl i),message="${s.active.ingredients[i].name}已捞起保温。"))
 }
 fun heatExcess(s:KitchenState):Float {val c=s.cooking;val id=s.active.id;return when {grilling(id)->max(0f,c.a[0]/23-1.25f)+max(0f,c.b[0]/23-1.25f);id=="dumplings"->max(0f,c.bottomHeat/18-1.25f)+max(0f,c.steamHeat/22-1.25f)+max(0f,c.stageHeat/10-1.25f);else->(0..2).filter{c.contains(it)}.sumOf{max(0f,c.a[it]/times(id)[it]-1.25f).toDouble()}.toFloat()}}
 fun tasteCorrect(s:KitchenState):Boolean = if(s.active.id=="egg-fried-rice"&&request(s)=="不加葱")!s.cooking.contains(2)&&s.cooking.seasoned in listOf("","原味","不加葱") else s.cooking.seasoned.ifBlank{"原味"}==request(s)
 fun coolingPenalty(s:KitchenState):Int {val base=if(s.market.active||s.active.id in listOf("cold-noodle","beer-plate"))s.holdSeconds/10 else 0;return if(s.market.active&&s.market.gear==StallGear.WARM)base/4 else base}
 fun quality(s:KitchenState):Int = (100-heatExcess(s)*24-s.ticketMistakes*5-(if(tasteCorrect(s))0 else 18)-coolingPenalty(s)+(if(s.market.active&&s.market.gear==StallGear.FLAVOR&&tasteCorrect(s))8 else 0)).toInt().coerceIn(20,100)
 fun reasons(s:KitchenState):String = "火候："+(if(heatExcess(s)<.1f)"受热恰当"else"略过火，下次减火或早点翻面")+"。\n口味："+(if(tasteCorrect(s))"符合客人要求"else"与客人要求不符")+"。\n出餐："+(if(coolingPenalty(s)>0)"热食等待较久，稍凉了"else"保温良好")+"。"
}
