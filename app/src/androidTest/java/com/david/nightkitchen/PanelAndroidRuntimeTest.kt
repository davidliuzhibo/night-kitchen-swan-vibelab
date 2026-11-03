package com.david.nightkitchen

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.david.nightkitchen.ui.design.PanelContract
import com.david.nightkitchen.ui.design.PanelFrame
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on Android ICU. No synthetic gameplay or Activity launch. */
@RunWith(AndroidJUnit4::class)
class PanelAndroidRuntimeTest {
    private fun contract() = PanelContract.parse(InstrumentationRegistry.getInstrumentation()
        .targetContext.assets.open("design/panel.json").bufferedReader().use { it.readText() })

    @Test fun literalReplacementAndMalformedBracesAreHandledOnAndroid() {
        val c = contract()
        val f = PanelFrame(c.initialState, mapOf("count" to "3", "amount" to "＄4\\金币"))
        assertEquals("3单 · ＄4\\金币 · {not-a-token} · }", c.template("{count}单 · {amount} · {not-a-token} · }", f))
        assertEquals("3 / 3", c.template("{count} / {count}", f))
        assertEquals("未闭合 {count", c.template("未闭合 {count", f))
    }

    @Test fun allPackagedTextNodesCanBeFormattedByAndroidRuntime() {
        val c = contract()
        var checked = 0
        for (node in c.nodes.values) {
            val text = node.get("content")?.asString ?: continue
            val names = Regex("""\{([a-zA-Z0-9]+)\}""").findAll(text).map { it.groupValues[1] }.toSet()
            val values = names.associateWith { "值_" + it }
            val result = c.text(node, PanelFrame(c.initialState, values))
            for (name in names) assertTrue("missing text binding $name", result.contains(values.getValue(name)))
            checked++
        }
        assertTrue("Real packaged node text must be exercised", checked >= 5)
    }

    @Test fun missingBindingStillFailsInsteadOfSilentlyShowingWrongInstructions() {
        val c = contract()
        try {
            c.template("{missingValue}", PanelFrame(c.initialState, emptyMap()))
            fail("Missing binding must remain actionable")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("missingValue"))
        }
    }
}
