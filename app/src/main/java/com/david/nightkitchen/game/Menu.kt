package com.david.nightkitchen.game

/**
 * Eight dishes with distinct cooking processes; authored food and cookware are retained.
 * [Rgb] is scene tint data, not a Compose color.
 */
data class Rgb(
    val r: Float,
    val g: Float,
    val b: Float,
)

data class IngredientStep(
    val name: String,
    val action: String,
    val color: Rgb,
)

data class Dish(
    val id: String,
    val name: String,
    val stationName: String,
    val stationColor: Rgb,
    val recipeBody: String,
    val ingredients: List<IngredientStep>,
    val finishedColor: Rgb,
    val reviews: List<String>,
) {
    init {
        require(ingredients.size == 3) { "$name needs exactly 3 grab steps" }
        require(reviews.size == 3) { "$name needs 3 reviews" }
    }
}

val NIGHT_MENU: List<Dish> =
    listOf(
        dish(
            id = "egg-fried-rice",
            name = "蛋炒饭",
            stationName = "铁锅",
            stationColor = Rgb(0.16f, 0.16f, 0.18f),
            recipeBody = "左架按顺序：米饭、鸡蛋、葱花，放进铁锅，再抓锅铲炒匀。成品盛进白盘，放到右侧出餐口。",
            steps =
                listOf(
                    IngredientStep("米饭", "米饭下锅", Rgb(0.96f, 0.94f, 0.86f)),
                    IngredientStep("鸡蛋", "鸡蛋下锅", Rgb(0.98f, 0.78f, 0.22f)),
                    IngredientStep("葱花", "葱花下锅", Rgb(0.32f, 0.70f, 0.30f)),
                ),
            finished = Rgb(0.93f, 0.58f, 0.16f),
            reviews =
                listOf(
                    "蛋炒饭粒粒分开，夜班值了。",
                    "葱花给得刚好，下回还来。",
                    "有点急，但还能吃。",
                ),
        ),
        dish(
            id = "chow-mein",
            name = "炒面",
            stationName = "铁锅",
            stationColor = Rgb(0.18f, 0.16f, 0.14f),
            recipeBody = "左架按顺序：面条、卷心菜、酱油，都下进铁锅。盛盘后放到出餐口。",
            steps =
                listOf(
                    IngredientStep("面条", "面条下锅", Rgb(0.93f, 0.82f, 0.45f)),
                    IngredientStep("卷心菜", "卷心菜下锅", Rgb(0.55f, 0.78f, 0.40f)),
                    IngredientStep("酱油", "酱油下锅", Rgb(0.35f, 0.20f, 0.10f)),
                ),
            finished = Rgb(0.72f, 0.52f, 0.22f),
            reviews =
                listOf(
                    "面炒得干爽，没有糊。",
                    "酱油味刚好，蔬菜还脆。",
                    "有点咸，下次少淋一点。",
                ),
        ),
        dish(
            id = "skewer",
            name = "烤串",
            stationName = "烤架",
            stationColor = Rgb(0.28f, 0.14f, 0.10f),
            recipeBody = "左架按顺序：肉块、孜然、辣椒面，都放上烤架。串盛进盘，再送出餐口。",
            steps =
                listOf(
                    IngredientStep("肉块", "肉块上架", Rgb(0.72f, 0.28f, 0.22f)),
                    IngredientStep("孜然", "撒孜然", Rgb(0.55f, 0.40f, 0.22f)),
                    IngredientStep("辣椒面", "撒辣椒面", Rgb(0.85f, 0.25f, 0.18f)),
                ),
            finished = Rgb(0.62f, 0.32f, 0.18f),
            reviews =
                listOf(
                    "串上有焦香，孜然很香。",
                    "辣度刚好，肉还是热的。",
                    "火候差一点，但能下酒。",
                ),
        ),
        dish(
            id = "oden",
            name = "关东煮",
            stationName = "煮锅",
            stationColor = Rgb(0.20f, 0.16f, 0.12f),
            recipeBody = "左架按顺序：萝卜、鱼饼、鸡蛋，放进煮锅。捞进碗盘，再送到出餐口。",
            steps =
                listOf(
                    IngredientStep("萝卜", "萝卜下锅", Rgb(0.94f, 0.93f, 0.88f)),
                    IngredientStep("鱼饼", "鱼饼下锅", Rgb(0.95f, 0.72f, 0.62f)),
                    IngredientStep("鸡蛋", "鸡蛋下锅", Rgb(0.98f, 0.78f, 0.22f)),
                ),
            finished = Rgb(0.86f, 0.62f, 0.40f),
            reviews =
                listOf(
                    "汤是热的，萝卜很软。",
                    "鱼饼入味了，夜里刚好。",
                    "汤淡了一点，还是暖的。",
                ),
        ),
        dish(
            id = "dumplings",
            name = "煎饺",
            stationName = "平底锅",
            stationColor = Rgb(0.22f, 0.20f, 0.18f),
            recipeBody = "左架按顺序：饺皮、肉馅、底油，放进平底锅。煎好盛盘，送到出餐口。",
            steps =
                listOf(
                    IngredientStep("饺皮", "饺皮入锅", Rgb(0.95f, 0.90f, 0.78f)),
                    IngredientStep("肉馅", "包上肉馅", Rgb(0.78f, 0.42f, 0.38f)),
                    IngredientStep("底油", "淋上底油", Rgb(0.90f, 0.72f, 0.20f)),
                ),
            finished = Rgb(0.86f, 0.68f, 0.36f),
            reviews =
                listOf(
                    "底是脆的，汁还在。",
                    "煎得刚好，没有焦黑。",
                    "有一张破了，味道还行。",
                ),
        ),
        dish(
            id = "instant-noodles",
            name = "泡面加料",
            stationName = "煮锅",
            stationColor = Rgb(0.18f, 0.16f, 0.14f),
            recipeBody = "左架按顺序：面饼、火腿肠、青菜，下进煮锅。盛进碗，再放到出餐口。",
            steps =
                listOf(
                    IngredientStep("面饼", "面饼下锅", Rgb(0.86f, 0.70f, 0.36f)),
                    IngredientStep("火腿肠", "火腿肠下锅", Rgb(0.90f, 0.55f, 0.50f)),
                    IngredientStep("青菜", "青菜下锅", Rgb(0.45f, 0.72f, 0.34f)),
                ),
            finished = Rgb(0.84f, 0.58f, 0.28f),
            reviews =
                listOf(
                    "面是筋的，加料给够了。",
                    "青菜还亮，夜里这一碗对。",
                    "汤少了一点，面还能吃。",
                ),
        ),
        dish(
            id = "cold-noodle",
            name = "烤冷面",
            stationName = "铁板",
            stationColor = Rgb(0.24f, 0.18f, 0.14f),
            recipeBody = "左架按顺序：冷面皮、鸡蛋、酱，都放上铁板。卷好装盘，送到出餐口。",
            steps =
                listOf(
                    IngredientStep("冷面皮", "面皮上板", Rgb(0.82f, 0.62f, 0.38f)),
                    IngredientStep("鸡蛋", "蛋液上板", Rgb(0.98f, 0.78f, 0.22f)),
                    IngredientStep("酱", "刷上酱", Rgb(0.70f, 0.22f, 0.16f)),
                ),
            finished = Rgb(0.74f, 0.40f, 0.18f),
            reviews =
                listOf(
                    "面皮焦边，酱很香。",
                    "蛋铺得匀，卷得整齐。",
                    "酱多了些，还是热的。",
                ),
        ),
        dish(
            id = "beer-plate",
            name = "热串与冷拼双单",
            stationName = "拼盘台",
            stationColor = Rgb(0.20f, 0.18f, 0.16f),
            recipeBody = "左架按顺序：花生、豆干、毛豆，放上拼盘台。装进白盘，再送到出餐口。",
            steps =
                listOf(
                    IngredientStep("花生", "花生上台", Rgb(0.80f, 0.62f, 0.36f)),
                    IngredientStep("豆干", "豆干上台", Rgb(0.48f, 0.30f, 0.16f)),
                    IngredientStep("毛豆", "毛豆上台", Rgb(0.40f, 0.68f, 0.32f)),
                ),
            finished = Rgb(0.70f, 0.50f, 0.28f),
            reviews =
                listOf(
                    "三样都脆，下酒正好。",
                    "豆干入味，花生还热。",
                    "毛豆少了一点，其余够。",
                ),
        ),
    ).sortedBy { listOf("egg-fried-rice","skewer","oden","chow-mein","dumplings","instant-noodles","cold-noodle","beer-plate").indexOf(it.id) }

fun dishById(id: String): Dish = NIGHT_MENU.first { it.id == id }

private fun dish(
    id: String,
    name: String,
    stationName: String,
    stationColor: Rgb,
    recipeBody: String,
    steps: List<IngredientStep>,
    finished: Rgb,
    reviews: List<String>,
): Dish =
    Dish(
        id = id,
        name = name,
        stationName = stationName,
        stationColor = stationColor,
        recipeBody = recipeBody,
        ingredients = steps,
        finishedColor = finished,
        reviews = reviews,
    )
