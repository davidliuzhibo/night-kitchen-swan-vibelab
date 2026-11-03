package com.david.nightkitchen.content
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.david.nightkitchen.game.*
import com.pico.spatial.ui.design.*
@Composable fun ReviewSlip(){val s=KitchenHub.state
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  StallPanelHeading(when(s.phase){Phase.REVIEW->"本单回评";Phase.SHIFT_END->"今晚收摊";else->"今夜的小窍门"}, PicoTheme.typography.titleLarge)
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){
   Text(if(s.market.active)"${NightMarket.names[s.market.night]} · 第${s.market.ticket+1}/4单 · ${s.active.name}"else"第 ${NIGHT_MENU.indexOf(s.active)+1}/8 道 · ${s.active.name}",style=PicoTheme.typography.titleMedium)
   Text(if(s.phase==Phase.REVIEW)s.lastReview.orEmpty() else if(s.phase==Phase.SHIFT_END)"完成 ${s.servedCount} 单，得分 ${s.score}。菜谱 ${s.unlocked.size}/8。" else CookBook.hint(s),style=PicoTheme.typography.bodyLarge)
   if(s.phase !in listOf(Phase.REVIEW,Phase.SHIFT_END))Text(if(s.market.active)"熟度100%后及时离火、装盘、送到出餐铃。夜市委托会考察做法、口味、火候及离火后的保温。\n${s.market.gear.label}：${s.market.gear.help}"else"熟度到100%后主动离火。先熟的分别捞起；暂停时火候也暂停。前六道可以慢慢学，不按出餐快慢扣分。",style=PicoTheme.typography.bodyMedium)
  }
  Button(onClick=KitchenHub::toggleSound,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text(if(KitchenHub.sound)"音乐和音效已开 · 关闭" else "已静音 · 开启音乐和音效")}
 }
}
