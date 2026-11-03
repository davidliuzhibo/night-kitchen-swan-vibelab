package com.david.nightkitchen

import com.david.nightkitchen.game.NIGHT_MENU
import com.david.nightkitchen.scene.DishKits
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DishKitsTest {
    private val slimmed =
        setOf(
            "plated-chow.glb",
            "noodle-brick.glb",
            "chow-noodles.glb",
            "chow-cabbage.glb",
            "noodle-veg.glb",
        )
    @Test
    fun everyDishHasItsOwnGrabFilesAndTheRiceTrioStaysOnFriedRice() {
        val riceTrio = listOf("rice-bowl.glb", "egg.glb", "scallion.glb")
        val seen = HashSet<String>()
        assertEquals(NIGHT_MENU.map { it.id }.toSet(), DishKits.all().map { it.id }.toSet())
        for (dish in NIGHT_MENU) {
            val kit = DishKits.forId(dish.id)
            val grabs = kit.grabs.map { it.file }
            assertEquals(dish.name, 3, grabs.toSet().size)
            assertEquals(3, kit.grabBadges.toSet().size)
            kit.grabBadges.forEach { badge ->
                assertTrue(badge, badge.length in 1..2)
            }
            assertTrue(kit.stationBadge.isNotEmpty())
            if (dish.id == "egg-fried-rice") {
                assertEquals(riceTrio, grabs)
                assertEquals("wok.glb", kit.station.file)
                assertEquals("rice-bowl.glb", kit.plated.file)
            } else {
                assertFalse(grabs.contains("rice-bowl.glb"))
                assertFalse(grabs.contains("scallion.glb"))
                assertFalse(grabs.toSet() == riceTrio.toSet())
                assertFalse(kit.plated.file == "rice-bowl.glb")
                assertTrue(kit.plated.file.startsWith("plated-"))
            }
            (grabs + kit.station.file + kit.plated.file).forEach { file ->
                val asset = cookware(file)
                assertTrue("${dish.id} missing $file", asset.isFile)
                val bytes = asset.readBytes()
                assertTrue(bytes.size > 8)
                assertEquals(0x67, bytes[0].toInt() and 0xff) // 'g' of glTF
                assertEquals(0x6c, bytes[1].toInt() and 0xff)
                assertFalse(String(bytes, Charsets.ISO_8859_1).contains("KHR_draco"))
                if (file !in riceTrio && file != "wok.glb") {
                    val text = String(bytes, Charsets.ISO_8859_1)
                    assertFalse(text.contains("nightkitchen-stall-meshes"))
                    assertFalse(text.contains("KHR_draco"))
                    assertFalse(text.contains("KHR_mesh_quantization"))
                    assertTrue("$file too small to be a Seed3D mesh", bytes.size > 200_000)
                    if (file in slimmed) {
                        assertTrue("$file still over 2.5MB", bytes.size < 2_500_000)
                        assertTrue(text.contains("image/jpeg"))
                        assertFalse(text.contains("image/png"))
                    }
                    seen.add(file)
                }
            }
        }
        assertTrue(seen.size >= 7 * 3)
        assertTrue(cookware("stall-cloth.glb").isFile)
        val sizzle = asset("sizzle.wav").readBytes()
        assertEquals("RIFF", String(sizzle.copyOfRange(0, 4), Charsets.US_ASCII))
    }

    @Test
    fun stationsFollowTheRecipeAndWholeEggsAreTheOnlySharedGrab() {
        assertEquals("wok.glb", DishKits.forId("chow-mein").station.file)
        assertEquals("grill.glb", DishKits.forId("skewer").station.file)
        assertEquals("broth-pot.glb", DishKits.forId("oden").station.file)
        assertEquals("broth-pot.glb", DishKits.forId("instant-noodles").station.file)
        assertEquals("fry-pan.glb", DishKits.forId("dumplings").station.file)
        assertEquals("griddle.glb", DishKits.forId("cold-noodle").station.file)
        assertEquals("snack-board.glb", DishKits.forId("beer-plate").station.file)
        assertTrue(DishKits.forId("oden").grabs.any { it.file == "egg.glb" })
        assertEquals("noodle-veg.glb", DishKits.forId("instant-noodles").grabs[2].file)
        assertTrue(DishKits.forId("instant-noodles").grabs.none { it.file == "egg.glb" })
        assertTrue(DishKits.forId("cold-noodle").grabs.none { it.file == "egg.glb" })
        assertEquals("cold-eggwash.glb", DishKits.forId("cold-noodle").grabs[1].file)
    }

    private fun cookware(file: String): File = asset("cookware/$file")

    private fun asset(relative: String): File {
        val candidates =
            listOf(
                File("src/main/assets/$relative"),
                File("app/src/main/assets/$relative"),
            )
        return candidates.firstOrNull { it.isFile } ?: candidates.first()
    }
}
