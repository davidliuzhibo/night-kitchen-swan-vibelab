package com.david.nightkitchen
import com.david.nightkitchen.ui.design.PanelFrame
import com.david.nightkitchen.ui.design.PanelContract
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Captured real reducers/API outcomes. Preview never runs a second gameplay engine. */
class ProductionReplay {
 private val records=JsonArray()
 private val contract=PanelContract.parse(java.io.File("src/main/assets/design/panel.json").readText())
 private val gson=GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
 fun record(command:String,before:PanelFrame?,after:PanelFrame?,host:String="",scenario:String="main") {
  records.add(JsonObject().apply{addProperty("command",command);addProperty("scenario",scenario);addProperty("host",host);add("before",gson.toJsonTree(before));add("after",gson.toJsonTree(after));before?.let{add("beforeTree",contract.snapshot(it))};after?.let{add("afterTree",contract.snapshot(it))}})
 }
 fun save(name:String="production-replay.json"){java.io.File("build/"+name).apply{parentFile!!.mkdirs();writeText(gson.toJson(records))}}
}
