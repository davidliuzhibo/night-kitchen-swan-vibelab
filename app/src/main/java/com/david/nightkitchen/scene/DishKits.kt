package com.david.nightkitchen.scene

/**
 * What the center station and the three grab slots show for each dish.
 *
 * 蛋炒饭 keeps the earlier Seed3D wok, rice bowl, egg, and scallion.
 * The other seven dishes use the round-2 Seed3D meshes. Draco is removed
 * so SDK 6.1.9 can load them. The five files that were over 5 MB were
 * vertex-clustered and repacked as 512² JPEG; their accessor bounds below
 * are the packed meshes. The other kit files keep the decoded triangles.
 * 关东煮的整颗蛋仍是 `egg.glb`. 泡面第三样是 `noodle-veg.glb`.
 * 烤冷面的蛋液是 `cold-eggwash.glb`.
 */
internal data class DishKit(
    val id: String,
    val grabs: List<BakedModel>,
    val plated: BakedModel,
    val station: BakedModel,
    val grabBadges: List<String>,
    val stationBadge: String,
) {
    init {
        require(grabs.size == 3)
        require(grabBadges.size == 3)
    }
}

internal object DishKits {
    val cloth =
        seated(
            "stall-cloth.glb",
            1.02f,
            -0.5f, 0f, -0.0398f,
            0.5f, 0.62f, 0.0398f,
        )
    val clothAt = Vec3(0f, StallLayout.SURFACE_Y, -0.22f)

    private val rice =
        DishKit(
            id = "egg-fried-rice",
            grabs = listOf(StallAssets.riceBowl, StallAssets.egg, StallAssets.scallion),
            plated = StallAssets.riceBowl,
            station = StallAssets.wok,
            grabBadges = listOf("米", "蛋", "葱"),
            stationBadge = "锅",
        )

    private val kits =
        listOf(
            rice,
            kit(
                "chow-mein",
                grabs("chow-noodles.glb", 0.20f, -0.4964f, -0.2619f, -0.4974f, 0.4972f, 0.2576f, 0.498f),
                grabs("chow-cabbage.glb", 0.16f, -0.4975f, -0.1563f, -0.4952f, 0.4957f, 0.1524f, 0.4984f),
                grabs("chow-soy.glb", 0.18f, -0.1739f, -0.4999f, -0.1741f, 0.1739f, 0.5f, 0.1741f),
                plated("plated-chow.glb", -0.4967f, -0.0975f, -0.4546f, 0.4935f, 0.097f, 0.4946f),
                StallAssets.wok,
                listOf("面", "菜", "酱"),
                "锅",
            ),
            kit(
                "skewer",
                grabs("skewer-meat.glb", 0.24f, -0.5f, -0.1446f, -0.4918f, 0.4993f, 0.1446f, 0.4918f),
                grabs("skewer-cumin.glb", 0.12f, -0.4956f, -0.1434f, -0.4954f, 0.4959f, 0.1435f, 0.4971f),
                grabs("skewer-chili.glb", 0.12f, -0.4861f, -0.1512f, -0.4893f, 0.4723f, 0.1647f, 0.4907f),
                plated("plated-skewer.glb", -0.4995f, -0.0689f, -0.4822f, 0.4998f, 0.0686f, 0.4823f),
                station("grill.glb", 0.42f, -0.2432f, -0.1672f, -0.4999f, 0.243f, 0.1674f, 0.4997f),
                listOf("肉", "孜", "椒"),
                "架",
            ),
            kit(
                "oden",
                grabs("oden-daikon.glb", 0.16f, -0.4994f, -0.2023f, -0.4999f, 0.4995f, 0.2023f, 0.5f),
                grabs("oden-fishcake.glb", 0.14f, -0.1715f, -0.2611f, -0.4999f, 0.1715f, 0.2611f, 0.5f),
                StallAssets.egg,
                plated("plated-oden.glb", -0.4999f, -0.2193f, -0.4999f, 0.5f, 0.2194f, 0.5f),
                station("broth-pot.glb", 0.36f, -0.4196f, -0.1832f, -0.4999f, 0.4197f, 0.1832f, 0.4998f),
                listOf("萝", "鱼", "蛋"),
                "煮",
            ),
            kit(
                "dumplings",
                grabs("dumpling-wrapper.glb", 0.16f, -0.4994f, -0.4995f, -0.1853f, 0.4998f, 0.4996f, 0.1854f),
                grabs("dumpling-filling.glb", 0.14f, -0.4845f, -0.2439f, -0.4991f, 0.4844f, 0.2442f, 0.5f),
                grabs("dumpling-oil.glb", 0.16f, -0.1754f, -0.5f, -0.1776f, 0.1754f, 0.5f, 0.1776f),
                plated("plated-dumpling.glb", -0.499f, -0.0674f, -0.4991f, 0.4987f, 0.0665f, 0.499f),
                station("fry-pan.glb", 0.40f, -0.3894f, -0.1189f, -0.5f, 0.3895f, 0.1188f, 0.5f),
                listOf("皮", "馅", "油"),
                "煎",
            ),
            kit(
                "instant-noodles",
                grabs("noodle-brick.glb", 0.16f, -0.4947f, -0.4946f, -0.2399f, 0.4924f, 0.4971f, 0.2415f),
                grabs("noodle-sausage.glb", 0.16f, -0.1624f, -0.1572f, -0.4999f, 0.1623f, 0.1572f, 0.4999f),
                grabs("noodle-veg.glb", 0.14f, -0.4238f, -0.4851f, -0.4584f, 0.4224f, 0.4962f, 0.4608f),
                plated("plated-noodle.glb", -0.4992f, -0.1538f, -0.4993f, 0.4992f, 0.1538f, 0.4996f),
                station("broth-pot.glb", 0.36f, -0.4196f, -0.1832f, -0.4999f, 0.4197f, 0.1832f, 0.4998f),
                listOf("饼", "肠", "菜"),
                "煮",
            ),
            kit(
                "cold-noodle",
                grabs("cold-sheet.glb", 0.22f, -0.4999f, -0.1049f, -0.4218f, 0.4997f, 0.1048f, 0.4218f),
                grabs("cold-eggwash.glb", 0.14f, -0.5f, -0.2213f, -0.4997f, 0.5f, 0.2213f, 0.4997f),
                grabs("cold-sauce.glb", 0.16f, -0.1596f, -0.5f, -0.1606f, 0.1595f, 0.5f, 0.1607f),
                plated("plated-cold.glb", -0.4999f, -0.106f, -0.4297f, 0.4999f, 0.1058f, 0.4298f),
                station("griddle.glb", 0.44f, -0.2533f, -0.0193f, -0.5f, 0.2533f, 0.0193f, 0.5f),
                listOf("皮", "液", "酱"),
                "板",
            ),
            kit(
                "beer-plate",
                grabs("beer-peanut.glb", 0.14f, -0.4897f, -0.1998f, -0.5f, 0.4899f, 0.1994f, 0.4997f),
                grabs("beer-tofu.glb", 0.14f, -0.4643f, -0.3608f, -0.4992f, 0.4638f, 0.3622f, 0.4993f),
                grabs("beer-edamame.glb", 0.16f, -0.4997f, -0.2067f, -0.4672f, 0.4995f, 0.2064f, 0.4675f),
                plated("plated-beer.glb", -0.4969f, -0.077f, -0.3939f, 0.4976f, 0.0753f, 0.394f),
                station("snack-board.glb", 0.34f, -0.4999f, -0.1228f, -0.3368f, 0.5f, 0.1228f, 0.3368f),
                listOf("花", "干", "荚"),
                "台",
            ),
        )

    private val byId = kits.associateBy { it.id }

    fun forId(id: String): DishKit = byId.getValue(id)

    fun all(): List<DishKit> = kits

    private fun kit(
        id: String,
        a: BakedModel,
        b: BakedModel,
        c: BakedModel,
        plated: BakedModel,
        station: BakedModel,
        badges: List<String>,
        stationBadge: String,
    ) = DishKit(id, listOf(a, b, c), plated, station, badges, stationBadge)

    private fun grabs(
        file: String,
        scale: Float,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
    ) = seated(file, scale, minX, minY, minZ, maxX, maxY, maxZ)

    private fun plated(
        file: String,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
    ) = seated(file, 0.20f, minX, minY, minZ, maxX, maxY, maxZ)

    private fun station(
        file: String,
        scale: Float,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
    ) = seated(file, scale, minX, minY, minZ, maxX, maxY, maxZ)

    private fun seated(
        file: String,
        scale: Float,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
    ) = BakedModel(file, scale, minX, minY, minZ, maxX, maxY, maxZ, bottom = true)
}
