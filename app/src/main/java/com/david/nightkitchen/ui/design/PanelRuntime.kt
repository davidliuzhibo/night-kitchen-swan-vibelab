package com.david.nightkitchen.ui.design

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Presentation only: this model has no score, physics, persistence or reducer. */
data class PanelFrame(val state: String, val values: Map<String,String>) {
    fun value(key: String) = values[key] ?: error("Missing presentation binding: $state / $key")
    fun flag(key: String) = value(key).toBooleanStrict()
}
fun panelValues(vararg pairs: Pair<String,Any?>): MutableMap<String,String> =
    pairs.associate { it.first to it.second.toString() }.toMutableMap()

class PanelContract(val document: JsonObject) {
    val nodes = document.getAsJsonArray("nodes").associate { it.asJsonObject.get("id").asString to it.asJsonObject }
    val actions = document.getAsJsonArray("actions").associate { it.asJsonObject.get("id").asString to it.asJsonObject }
    val states = document.getAsJsonArray("states").associate { it.asJsonObject.get("id").asString to it.asJsonObject }
    val initialState get() = document.get("initialStateId").asString
    fun root(frame: PanelFrame): JsonObject {
        val state = states[frame.state] ?: error("Unknown presentation state ${frame.state}")
        return nodes.getValue(state.getAsJsonObject("surfaceRoots").get("new-controls").asString)
    }
    fun shown(node: JsonObject, frame: PanelFrame): Boolean {
        if (node.has("visibleInStates") && node.getAsJsonArray("visibleInStates").none { it.asString == frame.state }) return false
        return node.obj("bindings")?.str("visible")?.let(frame::flag) ?: true
    }
    fun enabled(node: JsonObject, frame: PanelFrame): Boolean =
        node.obj("bindings")?.str("enabled")?.let(frame::flag) ?: true
    fun text(node: JsonObject, frame: PanelFrame): String = template(node.str("content") ?: "", frame)
    fun template(text: String, frame: PanelFrame): String = PLACEHOLDER.replace(text) { frame.value(it.groupValues[1]) }
    fun command(node: JsonObject): String? = node.obj("events")?.str("click")?.let { actions.getValue(it).getAsJsonObject("payload").get("command").asString }
    fun children(node: JsonObject, frame: PanelFrame): List<JsonObject> = node.getAsJsonArray("children")?.map { nodes.getValue(it.asString) }?.filter { shown(it,frame) && (it.str("kind") != "layout" || children(it,frame).isNotEmpty()) }.orEmpty()
    /** Exports the actual tree consumed by Compose, including disabled controls. */
    fun snapshot(frame: PanelFrame): JsonObject {
        fun walk(node: JsonObject): JsonObject = JsonObject().apply {
            addProperty("id",node.str("id")); addProperty("kind",node.str("kind"))
            addProperty("text",text(node,frame)); addProperty("enabled",enabled(node,frame))
            command(node)?.let { addProperty("command",it) }
            node.obj("layout")?.let { add("layout",it.deepCopy()) }
            node.obj("textStyle")?.let { add("textStyle",it.deepCopy()) }
            node.obj("appearance")?.let { add("appearance",it.deepCopy()) }
            add("children",com.google.gson.JsonArray().apply { children(node,frame).forEach { add(walk(it)) } })
        }
        return walk(root(frame))
    }
    companion object {
        // Android ICU requires both literal braces escaped; JVM tolerated the old closing brace.
        private val PLACEHOLDER = Regex("""\{([a-zA-Z0-9]+)\}""")
        fun parse(text: String) = PanelContract(JsonParser.parseString(text).asJsonObject)
    }
}
internal fun JsonObject.obj(key:String): JsonObject? = get(key)?.takeUnless{it.isJsonNull}?.asJsonObject
internal fun JsonObject.str(key:String): String? = get(key)?.takeUnless{it.isJsonNull}?.asString
internal fun JsonObject.number(key:String,default:Float=0f): Float = get(key)?.asFloat ?: default
