package com.david.nightkitchen.game

enum class MarketStage { OFF, CHOOSE, ORDER, GEAR, COOK, REVIEW, COMPLETE }
enum class StallGear(val label:String,val help:String) {
 ORIGINAL("原装","没有额外效果"),
 STEADY("稳火灶","大火限制为中火；仍需翻面、搅拌和及时离火。"),
 WARM("保温盘","离火后的冷却扣分降为四分之一，不能补救烧焦。"),
 FLAVOR("调味台","符合客人口味时加8品质；选错口味仍会扣分。")
}
data class MarketSession(
 val stage:MarketStage=MarketStage.OFF,val night:Int=0,val ticket:Int=0,
 val coins:Int=6,val gear:StallGear=StallGear.ORIGINAL,val choice:Int=0,
 val credited:Boolean=false,val passed:Boolean=false,val ticketBest:Int=0,
 val grades:List<Int> = listOf(0,0,0,0),val bests:List<Int> = listOf(0,0,0),val review:String=""
) { val active get()=stage!=MarketStage.OFF;val threshold get()=60+night*10 }
data class MarketTicket(val method:String,val taste:String,val dishes:List<String>)
sealed interface MarketEvent {
 data object Open:MarketEvent
 data object Courses:MarketEvent
 data class ChooseNight(val index:Int):MarketEvent
 data class ChooseDish(val index:Int):MarketEvent
 data class Equip(val gear:StallGear):MarketEvent
 data object Cook:MarketEvent
 data object Next:MarketEvent
 data object Retry:MarketEvent
 data object Reselect:MarketEvent
}

/** A finite four-order service. Outcomes come exclusively from the real cooking reducer. */
object NightMarket {
 val names=listOf("河畔初灯","夜市人声","收摊宴席")
 private fun t(method:String,taste:String,vararg dishes:String)=MarketTicket(method,taste,dishes.toList())
 val nights=listOf(
  listOf(t("炒制","不加葱","egg-fried-rice","skewer","oden"),t("烤制","少辣","oden","skewer","chow-mein"),t("汤煮","原味","oden","dumplings","cold-noodle"),t("卷制","酸甜","chow-mein","cold-noodle","oden")),
  listOf(t("焖蒸","原味","dumplings","chow-mein","skewer"),t("汤煮","原味","cold-noodle","instant-noodles","chow-mein"),t("炒制","少盐","skewer","chow-mein","dumplings"),t("冷热双拼","原味","instant-noodles","cold-noodle","beer-plate")),
  listOf(t("卷制","酸甜","cold-noodle","dumplings","oden"),t("烤制","少辣","instant-noodles","skewer","chow-mein"),t("焖蒸","原味","chow-mein","dumplings","oden"),t("冷热双拼","原味","oden","beer-plate","skewer"))
 )
 fun ticket(m:MarketSession)=nights[m.night][m.ticket]
 fun method(id:String)=when(id){"egg-fried-rice","chow-mein"->"炒制";"skewer"->"烤制";"oden","instant-noodles"->"汤煮";"dumplings"->"焖蒸";"cold-noodle"->"卷制";else->"冷热双拼"}
 fun request(s:KitchenState)=if(s.market.stage in listOf(MarketStage.COOK,MarketStage.REVIEW,MarketStage.GEAR))ticket(s.market).taste else CookBook.request(s.active.id)
 fun reduce(s:KitchenState,e:MarketEvent):KitchenState {
  val m=s.market
  return when(e){
   MarketEvent.Open->s.copy(market=m.copy(stage=MarketStage.CHOOSE),running=false)
   MarketEvent.Courses->freshShift(s.unlocked).copy(market=MarketSession(bests=m.bests))
   is MarketEvent.ChooseNight->if(m.stage!=MarketStage.CHOOSE||e.index !in nights.indices)s else freshShift(s.unlocked).copy(queue=emptyList(),running=false,market=MarketSession(stage=MarketStage.ORDER,night=e.index,bests=m.bests))
   is MarketEvent.ChooseDish->if(m.stage!=MarketStage.ORDER||e.index !in 0..2)s else freshShift(s.unlocked).copy(active=dishById(ticket(m).dishes[e.index]),queue=emptyList(),running=false,score=s.score,servedCount=s.servedCount,reviewLog=s.reviewLog,market=m.copy(stage=MarketStage.GEAR,choice=e.index,passed=false,review=""))
   is MarketEvent.Equip->if(m.stage!=MarketStage.GEAR||e.gear==StallGear.ORIGINAL||e.gear==m.gear||m.coins<4)s else s.copy(market=m.copy(gear=e.gear,coins=m.coins-4))
   MarketEvent.Cook->if(m.stage!=MarketStage.GEAR)s else s.copy(running=true,market=m.copy(stage=MarketStage.COOK))
   MarketEvent.Retry->if(m.stage!=MarketStage.REVIEW)s else com.david.nightkitchen.game.reduce(s,KitchenEvent.RetryOrder).copy(market=m.copy(stage=MarketStage.COOK,passed=false),running=true)
   MarketEvent.Reselect->if(m.stage !in listOf(MarketStage.GEAR,MarketStage.REVIEW))s else s.copy(market=m.copy(stage=MarketStage.ORDER,passed=false),running=false)
   MarketEvent.Next->if(m.stage!=MarketStage.REVIEW||!m.passed)s else {
    val grades=m.grades.mapIndexed{i,v->if(i==m.ticket)maxOf(v,m.ticketBest)else v}
    if(m.ticket==3)s.copy(running=false,market=m.copy(stage=MarketStage.COMPLETE,grades=grades,bests=m.bests.mapIndexed{i,v->if(i==m.night)maxOf(v,grades.sum())else v}))
    else s.copy(running=false,market=m.copy(stage=MarketStage.ORDER,ticket=m.ticket+1,grades=grades,credited=false,passed=false,ticketBest=0,review=""))
   }
  }
 }
 fun settle(before:KitchenState,after:KitchenState):KitchenState {
  val m=after.market
  if(before.phase==Phase.REVIEW||after.phase!=Phase.REVIEW||m.stage!=MarketStage.COOK)return after
  val methodOk=method(after.active.id)==ticket(m).method
  val tasteOk=CookBook.tasteCorrect(after)
  val quality=(CookBook.quality(after)-if(methodOk)0 else 25).coerceAtLeast(0)
  val passed=methodOk&&tasteOk&&quality>=m.threshold
  val reason=when{!methodOk->"做法不符，客人想吃${ticket(m).method}，请重新选菜。";!tasteOk->"口味不符，客人要${ticket(m).taste}，可以重做。";!passed->"品质未达${m.threshold}分，可以减火、及时离火后重做。";else->"熟客满意，这盏灯点亮了！"}
  val review="${after.active.name} · 品质$quality/${m.threshold}分\n$reason"
  return after.copy(running=false,lastReview=review,market=m.copy(stage=MarketStage.REVIEW,passed=passed,credited=m.credited||passed,coins=m.coins+if(passed&&!m.credited)2 else 0,ticketBest=maxOf(m.ticketBest,if(passed)quality else 0),review=review))
 }
}
