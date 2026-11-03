package com.david.nightkitchen.game

/**
 * Eight recipes share safe pickup/placement, but use distinct heat, timing and tool rules.
 * Drops snap. There is no rigid-body food, and release builds have no button
 * that completes an order. Debug builds may dispatch this same [KitchenEvent.Drop].
 */
enum class GrabSlot {
    A,
    B,
    C,
    SPATULA,
    DISH,
    ;

    /** Ingredient index for A/B/C. The spatula and the finished dish are not prep steps. */
    val prepIndex: Int?
        get() = if (ordinal < 3) ordinal else null
}

enum class Station {
    WOK,
    PLATE,
    SERVE,
}

enum class Phase {
    PREP,
    COOK,
    PLATE,
    SERVE,
    REVIEW,
    FAILED,
    SHIFT_END,
}

enum class Feedback {
    WELCOME,
    STEP_IN,
    NEED_STIR,
    NEED_PLATE,
    PLATED,
    SERVED,
    WRONG_STEP,
    NOT_READY,
    ORDER_FAILED,
    SHIFT_END,
}

data class KitchenState(
    val shiftSeconds: Int,
    val orderSeconds: Int,
    val active: Dish,
    val queue: List<Dish>,
    val added: Int,
    val plated: Boolean,
    val servedCount: Int,
    val mistakes: Int,
    val ticketMistakes: Int,
    val score: Int,
    val phase: Phase,
    val feedback: Feedback,
    val lastReview: String?,
    val reviewLog: List<String>,
    val unlocked: Set<String>,
    val unlockedThisShift: Set<String>,
    val running: Boolean,
    val cooking: Cooking = Cooking(),
    val holdSeconds: Int = 0,
    val market: MarketSession = MarketSession(),
) {
    val expectedGrab: GrabSlot?
        get() =
            when (phase) {
                Phase.PREP -> (0..2).firstOrNull { !cooking.contains(it) }?.let { GrabSlot.entries[it] }
                Phase.COOK -> GrabSlot.SPATULA
                Phase.PLATE, Phase.SERVE -> GrabSlot.DISH
                else -> null
            }

    val expectedStation: Station?
        get() =
            when (phase) {
                Phase.PREP -> if(active.id=="beer-plate")Station.PLATE else Station.WOK
                Phase.COOK -> Station.WOK
                Phase.PLATE -> Station.PLATE
                Phase.SERVE -> Station.SERVE
                else -> null
            }

    val comingUp: List<Dish>
        get() = queue.take(QUEUE_PREVIEW)
}

sealed interface KitchenEvent {
    data class Market(val event: MarketEvent): KitchenEvent
    data class Drop(
        val grab: GrabSlot,
        val station: Station,
    ) : KitchenEvent

    data class SetHeat(val heat:Heat):KitchenEvent
    data class Action(val action:CookAction):KitchenEvent
    data class Lift(val index:Int):KitchenEvent
    data class Taste(val taste:String):KitchenEvent
    data object TakeOffHeat:KitchenEvent
    data object StartHotSide:KitchenEvent
    data object ServeCold:KitchenEvent
    data object Tick : KitchenEvent

    data object NextOrder : KitchenEvent

    data object RetryOrder : KitchenEvent

    data object NewShift : KitchenEvent
}

const val SHIFT_SECONDS = 30 * 60
const val ORDER_SECONDS = 300
const val PENALTY_SECONDS = 8
const val TIP_PER_ORDER = 6
const val CLEAN_BONUS = 4
const val QUEUE_PREVIEW = 2

fun freshShift(unlocked: Set<String> = emptySet()): KitchenState {
    val menu = NIGHT_MENU
    return KitchenState(
        shiftSeconds = SHIFT_SECONDS,
        orderSeconds = ORDER_SECONDS,
        active = menu.first(),
        queue = menu.drop(1),
        added = 0,
        plated = false,
        servedCount = 0,
        mistakes = 0,
        ticketMistakes = 0,
        score = 0,
        phase = Phase.PREP,
        feedback = Feedback.WELCOME,
        lastReview = null,
        reviewLog = emptyList(),
        unlocked = unlocked,
        unlockedThisShift = emptySet(),
        running = true,
    )
}

fun canDrag(
    slot: GrabSlot,
    state: KitchenState,
): Boolean =
    when (slot) {
        GrabSlot.DISH -> state.phase == Phase.PLATE || state.phase == Phase.SERVE
        GrabSlot.SPATULA -> state.phase in listOf(Phase.PREP,Phase.COOK) && (state.cooking.mask!=0||state.active.id=="beer-plate"&&state.cooking.hotStarted&&!state.cooking.hotServed)
        else -> {
            val index = slot.prepIndex ?: return false
            state.phase in listOf(Phase.PREP,Phase.COOK) && !state.cooking.contains(index) && index < state.active.ingredients.size
        }
    }

fun payout(state: KitchenState): Int {
    return TIP_PER_ORDER + CookBook.quality(state)/10
}

fun reviewFor(
    dish: Dish,
    ticketMistakes: Int,
): String {
    val index =
        when {
            ticketMistakes <= 0 -> 0
            ticketMistakes < 3 -> 1
            else -> 2
        }
    return dish.reviews[index]
}

fun feedbackLine(state: KitchenState): String {
    val dish = state.active
    val nextName = dish.ingredients.getOrNull(state.added)?.name
    val previousName = dish.ingredients.getOrNull(state.added - 1)?.name
    if (state.phase in listOf(Phase.PREP,Phase.COOK)) return CookBook.hint(state)
    return when (state.feedback) {
        Feedback.WELCOME -> "开班了。这一单是${dish.name}。先抓${dish.ingredients.first().name}，放上${dish.stationName}。"
        Feedback.STEP_IN -> "${previousName ?: "食材"}放好了。接着拿${nextName ?: "下一味"}。"
        Feedback.NEED_STIR -> "食材都在锅里了。抓住锅铲，在锅里炒匀。"
        Feedback.NEED_PLATE -> "已经离火。抓住成品，盛进白盘，及时出餐。"
        Feedback.PLATED -> "装盘了。把${dish.name}放到右侧出餐口。"
        Feedback.SERVED -> "出餐成功。听出餐铃，看侧窗短评。"
        Feedback.WRONG_STEP -> "这个位置暂时不需要它。看当前操作提示再试。"
        Feedback.NOT_READY -> "还没做好，先完成火候和工具步骤。"
        Feedback.ORDER_FAILED -> state.lastReview ?: "这一单需要重做，先看一下火候提示。"
        Feedback.SHIFT_END -> "夜班结束。侧窗是本班结算。"
    }
}

fun reduce(
    state: KitchenState,
    event: KitchenEvent,
): KitchenState {
    val next = NightMarket.settle(state, reduceEvent(state, event))
    // Readable feedback has its own lifetime; utensil cooldown must not erase it.
    return if (event != KitchenEvent.Tick && next != state && next.cooking.message.isNotBlank())
        next.copy(cooking = next.cooking.copy(messageUntil = next.cooking.time + 5)) else next
}

private fun reduceEvent(
    state: KitchenState,
    event: KitchenEvent,
): KitchenState =
    when (event) {
        is KitchenEvent.Market -> NightMarket.reduce(state,event.event)
        KitchenEvent.NewShift -> freshShift(state.unlocked).copy(market=MarketSession(bests=state.market.bests))
        KitchenEvent.Tick -> tick(state)
        KitchenEvent.NextOrder ->
            if (state.phase == Phase.REVIEW) advance(state) else state
        KitchenEvent.RetryOrder ->
            resetTicket(if(state.phase==Phase.REVIEW)state.copy(servedCount=(state.servedCount-1).coerceAtLeast(0),score=(state.score-payout(state)).coerceAtLeast(0),reviewLog=state.reviewLog.dropLast(1))else state, state.active, state.queue)
        is KitchenEvent.Drop -> drop(state, event)
        is KitchenEvent.SetHeat -> if(state.phase in listOf(Phase.PREP,Phase.COOK)) state.copy(cooking=state.cooking.copy(heat=if(state.market.active&&state.market.gear==StallGear.STEADY&&event.heat==Heat.HIGH)Heat.MEDIUM else event.heat,message=""))else state
        is KitchenEvent.Action -> CookBook.action(state,event.action)
        is KitchenEvent.Lift -> CookBook.lift(state,event.index)
        is KitchenEvent.Taste -> if(state.phase in listOf(Phase.PREP,Phase.COOK))state.copy(cooking=state.cooking.copy(taste=event.taste,message="已选口味：${event.taste}"))else state
        KitchenEvent.StartHotSide -> if(state.active.id=="beer-plate" && !state.cooking.hotStarted && state.phase in listOf(Phase.PREP,Phase.COOK))state.copy(cooking=state.cooking.copy(hotStarted=true,message="热串开始受热，冷拼可以同时准备。"))else state
        KitchenEvent.ServeCold -> if(state.active.id=="beer-plate"&&state.phase in listOf(Phase.PREP,Phase.COOK)&&state.cooking.mask==7&&!state.cooking.coldServed){val next=state.copy(cooking=state.cooking.copy(coldServed=true,message="冷拼已送达，现在照看热串。"));if(next.cooking.hotServed)serve(next)else next}else state
        KitchenEvent.TakeOffHeat -> if(state.phase in listOf(Phase.PREP,Phase.COOK) && CookBook.ready(state)) state.copy(phase=Phase.PLATE,feedback=Feedback.NEED_PLATE,cooking=state.cooking.copy(heat=Heat.OFF,message=""))else state.copy(cooking=state.cooking.copy(message="还不能离火：${CookBook.hint(state.copy(cooking=state.cooking.copy(message="")))}"))
    }

fun formatClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun advance(state: KitchenState): KitchenState {
    if(state.queue.isEmpty())return state.copy(phase=Phase.SHIFT_END,feedback=Feedback.SHIFT_END,running=false)
    return resetTicket(state, state.queue.first(), state.queue.drop(1))
}

private fun resetTicket(
    state: KitchenState,
    dish: Dish,
    queue: List<Dish>,
): KitchenState =
    state.copy(
        orderSeconds = ORDER_SECONDS,
        cooking = Cooking(), holdSeconds=0,
        active = dish,
        queue = queue,
        added = 0,
        plated = false,
        ticketMistakes = 0,
        phase = Phase.PREP,
        feedback = Feedback.WELCOME,
        lastReview = null,
        running = true,
    )

private fun tick(state: KitchenState): KitchenState {
    if(!state.running || state.phase !in listOf(Phase.PREP,Phase.COOK,Phase.PLATE,Phase.SERVE))return state
    val next=state.copy(shiftSeconds=(state.shiftSeconds-1).coerceAtLeast(0),orderSeconds=(state.orderSeconds-1).coerceAtLeast(0),holdSeconds=state.holdSeconds+if(state.phase in listOf(Phase.PLATE,Phase.SERVE))1 else 0)
    // Time is informative. Food burns or cools through its own rules, not an arbitrary countdown.
    return CookBook.tick(next)
}

private fun drop(
    state: KitchenState,
    event: KitchenEvent.Drop,
): KitchenState {
    if (
        state.phase != Phase.PREP &&
        state.phase != Phase.COOK &&
        state.phase != Phase.PLATE &&
        state.phase != Phase.SERVE
    ) {
        return state
    }
    if(event.grab.prepIndex!=null && state.phase in listOf(Phase.PREP,Phase.COOK)) {
        val i=event.grab.prepIndex!!
        if(state.cooking.contains(i))return state
        if(event.station!=(if(state.active.id=="beer-plate")Station.PLATE else Station.WOK))return penalize(state,Feedback.WRONG_STEP)
        val c=state.cooking.copy(mask=state.cooking.mask or (1 shl i),message="")
        return state.copy(cooking=c,added=Integer.bitCount(c.mask),phase=if(c.mask and CookBook.requiredMask(state.active.id)==CookBook.requiredMask(state.active.id))Phase.COOK else Phase.PREP,feedback=Feedback.STEP_IN)
    }
    if(event.grab==GrabSlot.SPATULA&&event.station==Station.WOK) {
        val action=CookBook.actions(state).firstOrNull()?:return state.copy(cooking=state.cooking.copy(message="这道菜要分别掌握熟度，请看食谱卡。"))
        return CookBook.action(state,action)
    }
    if(event.grab!=state.expectedGrab||event.station!=state.expectedStation)return penalize(state,Feedback.NOT_READY)
    return when(state.phase){
        Phase.PLATE->state.copy(plated=true,phase=Phase.SERVE,feedback=Feedback.PLATED)
        Phase.SERVE->serve(state)
        else->state
    }
}

private fun serve(state: KitchenState): KitchenState {
    if(state.active.id=="beer-plate"&&!state.cooking.hotServed&&!state.cooking.coldServed)return state.copy(phase=if(state.cooking.mask==7)Phase.COOK else Phase.PREP,plated=false,cooking=state.cooking.copy(hotServed=true,heat=Heat.OFF,message="热串已送达，接下来完成冷拼。"))
    val quality = CookBook.quality(state)
    val review = "${state.active.name} · 品质 $quality 分。" + CookBook.reasons(state)
    val firstTime = state.active.id !in state.unlocked
    return state.copy(
        phase = Phase.REVIEW,
        feedback = Feedback.SERVED,
        servedCount = state.servedCount + 1,
        score = state.score + payout(state),
        lastReview = review,
        reviewLog = state.reviewLog + review,
        unlocked = state.unlocked + state.active.id,
        unlockedThisShift = if (firstTime) state.unlockedThisShift + state.active.id else state.unlockedThisShift,
    )
}

private fun penalize(
    state: KitchenState,
    feedback: Feedback,
): KitchenState {
    return state.copy(mistakes=state.mistakes+1,ticketMistakes=state.ticketMistakes+1,feedback=feedback,cooking=state.cooking.copy(message="这一步还不合适，请按当前提示操作。"))
}
