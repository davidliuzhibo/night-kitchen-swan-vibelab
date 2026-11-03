package com.david.nightkitchen.ui.market.components
import com.david.nightkitchen.game.*
import com.david.nightkitchen.ui.design.*

object MarketPresentation {
 fun frame(s:KitchenState):PanelFrame {
  val m=s.market
  require(m.stage !in listOf(MarketStage.OFF,MarketStage.COOK))
  val title=when(m.stage){MarketStage.CHOOSE->"今晚的夜市委托";MarketStage.ORDER->"给熟客选一道菜";MarketStage.GEAR->"备好摊位，再开火";MarketStage.REVIEW->"熟客的评价";else->"今晚的灯都亮了"}
  val body=when(m.stage){
   MarketStage.CHOOSE->"三晚各四单，品质要求逐渐提高。八道厨艺课程也会保留。"
   MarketStage.ORDER,MarketStage.GEAR->"${NightMarket.names[m.night]} · 第${m.ticket+1}/4单\n想吃${NightMarket.ticket(m).method} · ${NightMarket.ticket(m).taste}\n金币${m.coins} · 已装${m.gear.label}"
   MarketStage.REVIEW->m.review+"\n本单只奖励一次 · 金币${m.coins}"
   else->"四单品质合计${m.grades.sum()}/400\n这晚最佳${m.bests[m.night]}分 · 菜谱${s.unlocked.size}/8"
  }
  val v=panelValues("title" to title,"body" to body,"passed" to m.passed,"nextLabel" to if(m.ticket==3)"查看今晚成果"else"下一位熟客","gearHelp" to "已选${s.active.name}\n${m.gear.help}\n每次替换花4币，一次只装一种。")
  NightMarket.names.forEachIndexed{i,name->v["night$i"]="$name · 品质${60+10*i} · 最佳${m.bests[i]}"}
  NightMarket.ticket(m).dishes.forEachIndexed{i,id->v["dish$i"]=dishById(id).name+" · "+NightMarket.method(id)}
  StallGear.entries.filter{it!=StallGear.ORIGINAL}.forEach{g->v["gear${g.name}"]="${if(m.gear==g)"已装"else"4币"} · ${g.label}\n${g.help}";v["buy${g.name}"]=(m.gear!=g&&m.coins>=4).toString()}
  return PanelFrame(m.stage.name.lowercase(),v)
 }
 fun command(id:String):MarketEvent=when {
  id.startsWith("night:")->MarketEvent.ChooseNight(id.substringAfter(':').toInt())
  id.startsWith("dish:")->MarketEvent.ChooseDish(id.substringAfter(':').toInt())
  id.startsWith("gear:")->MarketEvent.Equip(StallGear.valueOf(id.substringAfter(':')))
  else->when(id){"cook"->MarketEvent.Cook;"retry"->MarketEvent.Retry;"reselect"->MarketEvent.Reselect;"next"->MarketEvent.Next;"open"->MarketEvent.Open;"courses"->MarketEvent.Courses;else->error("Unknown market action $id")}
 }
}
