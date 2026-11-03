package com.david.nightkitchen.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.PicoTheme

/** Uses the packaged contract directly. It cannot navigate or complete gameplay. */
@Composable fun DesignedPanel(frame: PanelFrame, onCommand:(String)->Unit) {
    val assets=LocalContext.current.assets
    val contract=remember(assets) { assets.open("design/panel.json").bufferedReader().use { PanelContract.parse(it.readText()) } }
    // A new real production state starts at the top; progress never lives in scroll state.
    key(frame.state) { PanelNode(contract,contract.root(frame),frame,onCommand,Modifier.fillMaxSize()) }
}
@Composable private fun PanelNode(c:PanelContract,n:JsonObject,f:PanelFrame,send:(String)->Unit,base:Modifier=Modifier) {
    val l=n.obj("layout"); val appearance=n.obj("appearance"); val props=n.obj("props")
    var mod=base
    l?.let { if(it.has("maxHeightDp")) mod=mod.heightIn(max=it.number("maxHeightDp").dp) }
    when(n.str("kind")) {
        "layout" -> {
            val children=c.children(n,f)
            // Empty state-specific groups take no size and do not create blank footers.
            if(children.isEmpty()) return
            val mode=l?.str("mode");val gap=l?.number("gapDp")?:0f
            if(mode=="scroll"||l?.str("overflow")=="scroll")mod=mod.verticalScroll(rememberScrollState())
            mod=mod.fillMaxWidth().padding((l?.number("paddingDp")?:0f).dp)
            if(mode=="row")Row(mod,horizontalArrangement=Arrangement.spacedBy(gap.dp)) {
                children.forEach { child->PanelNode(c,child,f,send,Modifier.weight(1f)) }
            } else Column(mod,verticalArrangement=Arrangement.spacedBy(gap.dp)) {
                children.forEach { child->
                    val grow=child.obj("layout")?.number("grow")?:0f
                    PanelNode(c,child,f,send,if(grow>0f)Modifier.weight(grow) else Modifier)
                }
            }
        }
        "spatialui" -> {
            require(n.str("component")=="sui-button" && props?.str("size")=="regular" && props.get("fillWidth").asBoolean)
            Button(onClick={c.command(n)?.let(send)},enabled=c.enabled(n,f),modifier=mod.fillMaxWidth().heightIn(min=(l?.number("minHeightDp")?:56f).dp)) {
                val buttonType=c.document.getAsJsonObject("theme").getAsJsonObject("typography").getAsJsonObject(n.obj("textStyle")!!.str("typography"))
                Text(c.text(n,f),fontSize=buttonType.number("sizeDp").sp,lineHeight=buttonType.number("lineHeightDp").sp,fontWeight=FontWeight(buttonType.number("weight").toInt()))
            }
        }
        "text" -> {
            val token=c.document.getAsJsonObject("theme").getAsJsonObject("typography").getAsJsonObject(n.obj("textStyle")!!.str("typography"))
            val size=token.number("sizeDp");val line=token.number("lineHeightDp")
            if(appearance?.str("fill")!=null && n.str("id")=="panel-caption") {
                // design-style: design-surface panel-caption
                Column(mod.fillMaxWidth().clip(RoundedCornerShape(appearance.number("radiusDp").dp)).background(PicoTheme.colorScheme.fillPrimary).verticalScroll(rememberScrollState()).padding((l?.number("paddingDp")?:0f).dp)) {
                    Text(f.value(props!!.str("titleBinding")!!),fontSize=props.number("titleSizeSp").sp,lineHeight=props.number("titleLineHeightSp").sp,fontWeight=FontWeight.SemiBold,color=PicoTheme.colorScheme.labelPrimaryLight)
                    Text(f.value(props.str("bodyBinding")!!),fontSize=size.sp,lineHeight=line.sp,color=PicoTheme.colorScheme.labelPrimaryLight)
                }
            } else if(appearance?.str("fill")!=null) {
                // A body leaf is NOT a nested scroll container: its parent body scrolls.
                Text(c.text(n,f),modifier=ReadabilitySurface(n,mod.fillMaxWidth()),fontSize=size.sp,lineHeight=line.sp,color=PicoTheme.colorScheme.labelPrimaryLight)
            } else Text(c.text(n,f),modifier=mod.fillMaxWidth(),fontSize=size.sp,lineHeight=line.sp,color=PicoTheme.colorScheme.labelPrimary)
        }
        else -> error("Unsupported presentation node ${n.str("id")}")
    }
}

/** Bounded text-only surfaces; structural rows/columns remain transparent. */
@Composable private fun ReadabilitySurface(n:JsonObject,base:Modifier):Modifier {
    val a=n.obj("appearance")!!
    require(a.str("fill")=="fillPrimary" && a.str("foreground")=="labelPrimaryLight")
    val clipped=base.clip(RoundedCornerShape(a.number("radiusDp").dp))
    val filled=when(n.str("id")) {
        "gear-explanation" -> clipped
            // design-style: design-surface gear-explanation
            .background(PicoTheme.colorScheme.fillPrimary)
        else->error("Undeclared text surface ${n.str("id")}")
    }
    return filled.padding(n.obj("layout")!!.number("paddingDp").dp)
}
