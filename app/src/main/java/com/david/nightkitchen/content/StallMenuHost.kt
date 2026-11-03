package com.david.nightkitchen.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.david.nightkitchen.game.KitchenHub
import com.david.nightkitchen.game.StallMenuPage
import com.david.nightkitchen.platform.NightKitchenApp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator

@Composable
internal fun StallPanelHeading(title: String, style: TextStyle) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = style)
        Button(onClick = KitchenHub.menu::open, modifier = Modifier.heightIn(min = 56.dp)) {
            Text("退出游戏")
        }
    }
}

/** A pause page in the existing panel. Either surviving panel can complete the same save. */
@Composable
internal fun StallMenuHost(content: @Composable () -> Unit) {
    val page = KitchenHub.menu.page
    if (page == StallMenuPage.PLAY) {
        content()
        return
    }
    val app = LocalContext.current.applicationContext as NightKitchenApp
    val navigator = LocalSpatialNavigator.current
    val actionable = page == StallMenuPage.CHOICE || page == StallMenuPage.SAVE_ERROR
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("营业菜单", style = PicoTheme.typography.titleLarge)
        val message = when (page) {
            StallMenuPage.SAVING -> "正在保存这单的进度…"
            StallMenuPage.CLOSING -> "进度已保存，正在收起摊位。"
            StallMenuPage.SAVE_ERROR -> "进度保存失败。可以再试一次，或返回游戏。"
            else -> "火候已暂停。退出会收起灶台和两侧面板，下次可以接着做这单。"
        }
        Text(message, style = PicoTheme.typography.bodyLarge)
        if (actionable) {
            Button(onClick = KitchenHub.menu::resume, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (KitchenHub.playerPaused) "返回游戏" else "继续营业")
            }
            Button(onClick = { app.saveAndExit(navigator) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("保存并退出游戏")
            }
        }
    }
}
