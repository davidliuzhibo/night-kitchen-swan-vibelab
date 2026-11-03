package com.david.nightkitchen.content
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.david.nightkitchen.game.*
import com.david.nightkitchen.ui.market.components.MarketPanel
import com.pico.spatial.ui.design.*

@Composable fun OrderBoard(){
 val s=KitchenHub.state;val c=s.cooking;val active=s.phase in listOf(Phase.PREP,Phase.COOK)
 var tasteOpen by remember{mutableStateOf(false)};var retryConfirm by remember{mutableStateOf(false)}
 var marketConfirm by remember{mutableStateOf(false)}
 if(KitchenHub.assetsReady&&s.market.stage !in listOf(MarketStage.OFF,MarketStage.COOK)){
  com.david.nightkitchen.ui.market.MarketScreen();return
 }
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  StallPanelHeading("深夜食堂 · ${NIGHT_MENU.indexOf(s.active)+1}/8", PicoTheme.typography.titleMedium)
  Text(s.active.name,style=PicoTheme.typography.titleLarge)
  if(!KitchenHub.assetsReady){Text(KitchenHub.sceneError?:"正在准备灶台，火候暂停…");if(KitchenHub.sceneError!=null)Button(onClick=KitchenHub::retryScene){Text("重新载入")};return@Column}
  if(marketConfirm){
   Text("切换夜市委托会放下当前订单，已解锁菜谱和夜市最佳记录保留。",style=PicoTheme.typography.bodyLarge)
   Button(onClick={KitchenHub.dispatch(KitchenEvent.Market(MarketEvent.Open));marketConfirm=false},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("进入夜市委托")}
   Button(onClick={marketConfirm=false},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("返回，保持暂停")};return@Column
  }
  if(s.market.active)Text("${NightMarket.names[s.market.night]} · 第${s.market.ticket+1}/4单 · 品质目标${s.market.threshold}",style=PicoTheme.typography.bodyLarge)
  if(retryConfirm){Text("重做本单会清除这单的操作，之前菜谱保留。",style=PicoTheme.typography.bodyLarge);Button(onClick={KitchenHub.dispatch(KitchenEvent.RetryOrder);retryConfirm=false},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("确认重做")};Button(onClick={retryConfirm=false},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("取消，保持暂停")};return@Column}
  if(tasteOpen){
   Text("客人要：${CookBook.request(s)}。选好后再点“加入调味”；不加葱需要实际留住葱花。看口味时火候暂停。",style=PicoTheme.typography.bodyLarge)
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){for(t in listOf("原味","不加葱","少盐","少辣","酸甜"))Button(onClick={KitchenHub.setTaste(t)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("${if(c.taste==t)"已选 · "else""}$t")}}
   Button(onClick={tasteOpen=false;if(KitchenHub.playerPaused)KitchenHub.togglePause()},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("返回并继续")};return@Column
  }
  Text("客人要：${CookBook.request(s)}${if(c.seasoned.isNotBlank())" · 已调${c.seasoned}"else ""}",style=PicoTheme.typography.bodyLarge)
  // The current reason is always above the scrolling controls, including on small windows.
  Text(feedbackLine(s),style=PicoTheme.typography.bodyMedium)
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
   if(active){
    if(s.active.id=="dumplings")Text("${listOf("煎底","加盖焖熟","开盖收汁")[c.stage]}：${c.stageHeat.toInt()} / ${listOf(18,22,10)[c.stage]}火候",style=PicoTheme.typography.bodyLarge)
    else if(CookBook.grilling(s.active.id))Text("正面 ${(c.a[0]/23*100).toInt()}% · 反面 ${(c.b[0]/23*100).toInt()}%\n${if(c.hotServed)"热串已送达"else "当前${if(c.face==0)"正面"else"反面"}受热；两面都到100%"}",style=PicoTheme.typography.bodyLarge)
    else for(i in 0..2){val pct=CookBook.percent(s,i);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Text("${s.active.ingredients[i].name} · ${when{!c.contains(i)->if(s.active.id=="egg-fried-rice"&&i==2)"客人不加，请留住"else"未入锅";c.removed(i)->"已捞起";pct<100->"受热 $pct%";pct<145->"已熟 $pct%";else->"快离火 $pct%"}}",modifier=Modifier.weight(1f),style=PicoTheme.typography.bodyMedium)
     if(CookBook.boiling(s.active.id))Button(onClick={KitchenHub.dispatch(KitchenEvent.Lift(i))},enabled=c.contains(i)&&!c.removed(i)&&!KitchenHub.playerPaused,modifier=Modifier.heightIn(min=56.dp)){Text("捞起")}
    }}
    if(!(s.active.id=="beer-plate"&&c.hotServed)){
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){for(h in listOf(Heat.LOW,Heat.MEDIUM,Heat.HIGH))Button(onClick={KitchenHub.dispatch(KitchenEvent.SetHeat(h))},enabled=!KitchenHub.playerPaused,modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text("${if(c.heat==h)"● "else""}${h.label}")}}
     val actions=CookBook.actions(s)
     if(actions.isNotEmpty())Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){for(a in actions)Button(onClick={KitchenHub.dispatch(KitchenEvent.Action(a))},enabled=!KitchenHub.playerPaused,modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text(if(a==CookAction.SEASON)"加入调味"else a.label)}}
    }
    if(s.active.id=="beer-plate")Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={KitchenHub.dispatch(KitchenEvent.StartHotSide)},enabled=!c.hotStarted&&!KitchenHub.playerPaused,modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text(if(c.hotServed)"热串已送"else if(c.hotStarted)"热串已开火"else"热串开火")};Button(onClick={KitchenHub.dispatch(KitchenEvent.ServeCold)},enabled=c.mask==7&&!c.coldServed&&!KitchenHub.playerPaused,modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text(if(c.coldServed)"冷拼已送"else"送出冷拼")}}
    if(!(s.active.id=="beer-plate"&&c.hotServed))Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={KitchenHub.dispatch(KitchenEvent.TakeOffHeat)},enabled=!KitchenHub.playerPaused,modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text(if(s.active.id=="beer-plate")"热串离火"else"离火装盘")};Button(onClick={KitchenHub.dispatch(KitchenEvent.SetHeat(Heat.OFF))},enabled=!KitchenHub.playerPaused,modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text("关火")}}
   }
   Text(KitchenHub.selectedLabel?.let{"已选：$it，再点目标位置。"}?:when(s.phase){Phase.PLATE->"轻点成品，再轻点白盘。";Phase.SERVE->"轻点盘中成品，再轻点出餐铃。";else->if(s.active.id=="beer-plate")"冷拼食材点到白盘；热串独立开火。可任意先送。"else"轻点食材，再点锅；也可捏住短距离移动。"},style=PicoTheme.typography.bodyMedium)
  }
  when(s.phase){
   Phase.REVIEW->Button(onClick={KitchenHub.dispatch(KitchenEvent.NextOrder)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text(if(s.queue.isEmpty())"今晚收摊"else"接下一单")}
   Phase.FAILED->Button(onClick={KitchenHub.dispatch(KitchenEvent.RetryOrder)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("重做本单")}
   Phase.SHIFT_END->Button(onClick={KitchenHub.dispatch(KitchenEvent.NewShift)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("新的一晚")}
   else->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=KitchenHub::togglePause,modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text(if(KitchenHub.playerPaused)"开始 / 继续"else"暂停")};Button(onClick={if(!KitchenHub.playerPaused)KitchenHub.togglePause();tasteOpen=true},modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text("口味")}}
  }
  if(KitchenHub.playerPaused||s.phase==Phase.REVIEW)Button(onClick={if(!KitchenHub.playerPaused)KitchenHub.togglePause();retryConfirm=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("重做这一单…")}
  if(KitchenHub.playerPaused||s.phase in listOf(Phase.REVIEW,Phase.FAILED,Phase.SHIFT_END))Button(onClick={if(!KitchenHub.playerPaused)KitchenHub.togglePause();marketConfirm=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("夜市委托 · 选菜与经营…")}
 }
}
