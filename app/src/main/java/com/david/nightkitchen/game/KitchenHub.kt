package com.david.nightkitchen.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.david.nightkitchen.BuildConfig
import android.content.Context
import android.content.SharedPreferences
import com.david.nightkitchen.audio.StallAudio
import com.david.nightkitchen.audio.StallSound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Process-wide session shared by the volumetric stall and the order window.
 * The two WindowContainers do not share one composition, so a holder both can
 * read is the link between them.
 */
object KitchenHub {
    var sound by mutableStateOf(true)
        private set
    private var audio: StallAudio? = null
    private var hasTopWindow = false
    internal var visitOpen by mutableStateOf(false)
        private set
    internal val menu: StallMenu = StallMenu { page ->
        if (page == StallMenuPage.CHOICE) invalidateInput()
        syncAudio()
    }

    fun toggleSound() {
        if (menu.page != StallMenuPage.PLAY) return
        sound = !sound
        preferences?.edit()?.putBoolean("sound", sound)?.apply()
        syncAudio()
    }

    internal fun startVisit(output: StallAudio) {
        visitOpen = true
        hasTopWindow = false
        audio = output
        menu.reset()
    }

    internal fun topWindowReady() {
        hasTopWindow = true
        syncAudio()
    }

    internal fun awayFromStall() {
        if (visitOpen && sceneReady && hasTopWindow) {
            menu.open()
            save()
        }
    }

    internal fun endVisit() {
        visitOpen = false
        hasTopWindow = false
        audio?.release()
        audio = null
        playerPaused = true
        invalidateInput()
        menu.reset()
        sceneReady = false
        sceneError = null
        loadingModels = 0
        nameplatesMounted = false
        orderBoardTouched = false
        showBadges = false
        ownsInteractionFocus = true
    }

    private fun syncAudio() {
        audio?.setAudible(visitOpen && hasTopWindow && sound && foreground && menu.page == StallMenuPage.PLAY)
    }

    fun setTaste(taste:String){if(!foreground||menu.page!=StallMenuPage.PLAY||state.market.stage !in listOf(MarketStage.OFF,MarketStage.COOK))return;state=reduce(state,KitchenEvent.Taste(taste));save()}
    private var preferences: SharedPreferences? = null
    var sceneReady by mutableStateOf(false)
        private set
    var sceneError by mutableStateOf<String?>(null)
        private set
    var sceneGeneration by mutableStateOf(0)
        private set
    var loadingModels by mutableStateOf(0)
        private set
    private var inForeground by mutableStateOf(true)
    private var ownsInteractionFocus by mutableStateOf(true)
    var foreground: Boolean
        get() = inForeground && ownsInteractionFocus
        set(value) {
            if (foreground && !value) invalidateInput()
            inForeground = value
            syncAudio()
        }
    internal fun setInteractionFocus(owned: Boolean) {
        if (foreground && !owned) invalidateInput()
        ownsInteractionFocus = owned
        syncAudio()
    }
    var inputGeneration by mutableStateOf(0L)
        private set
    var playerPaused by mutableStateOf(true)
        private set
    var selectedLabel by mutableStateOf<String?>(null)
    val assetsReady: Boolean get() = sceneReady && loadingModels == 0 && sceneError == null
    val canInteract: Boolean get() = foreground && menu.page == StallMenuPage.PLAY && assetsReady && !playerPaused && state.market.stage in listOf(MarketStage.OFF,MarketStage.COOK)
    fun pauseForInterruptedGrab(){if(menu.page!=StallMenuPage.PLAY)return;playerPaused=true;selectedLabel=null;state=state.copy(cooking=state.cooking.copy(message="刚才的抓取中断，火候已暂停。整理好姿势后点继续。"));save()}
    private fun invalidateInput() {
        inputGeneration++
        selectedLabel = null
    }

    fun togglePause() {
        if (menu.page != StallMenuPage.PLAY) return
        if (!playerPaused) invalidateInput()
        playerPaused = !playerPaused
        selectedLabel = null
        save()
    }

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences("night-kitchen-progress", Context.MODE_PRIVATE)
        sound=preferences?.getBoolean("sound",true)?:true
        val known = NIGHT_MENU.map { it.id }.toSet()
        val unlocked = preferences?.getStringSet("unlocked", emptySet()).orEmpty().intersect(known)
        state = KitchenCheckpoint.decode(preferences?.getString("checkpoint", null)) ?: freshShift(unlocked)
    }
    fun beginLoading() { loadingModels++ }
    fun endLoading() { loadingModels = (loadingModels - 1).coerceAtLeast(0) }
    fun save() {
        checkpointEditor()?.apply()
    }

    private fun checkpointEditor() = preferences?.edit()
        ?.putStringSet("unlocked", state.unlocked.toSet())
        ?.putString("checkpoint", KitchenCheckpoint.encode(state))
        ?.putBoolean("sound", sound)

    internal suspend fun saveBeforeExit(): Boolean {
        val editor = checkpointEditor() ?: return false
        return withContext(Dispatchers.IO) {
            try { editor.commit() } catch (e: RuntimeException) {
                android.util.Log.e("NightKitchen", "Could not persist the order before exit", e)
                false
            }
        }
    }
    fun markSceneReady() { sceneReady = true; sceneError = null }
    fun failScene() { sceneReady = false; sceneError = "灶台资源未能完整载入，请重试。计时已暂停，已解锁菜谱保留。" }
    fun retryScene() {
        invalidateInput()
        sceneReady = false; sceneError = null; loadingModels = 0
        showBadges = false; nameplatesMounted = false; sceneGeneration++
    }
    private val stateSnapshot = mutableStateOf(freshShift())
    private val stateUpdates = kotlinx.coroutines.flow.MutableStateFlow(stateSnapshot.value)
    val states: kotlinx.coroutines.flow.StateFlow<KitchenState> = stateUpdates
    var state: KitchenState
        get() = stateSnapshot.value
        private set(value) { stateSnapshot.value = value; stateUpdates.value = value }

    /** Compatibility marker; model loading no longer depends on a first pointer. */
    var orderBoardTouched by mutableStateOf(false)
        private set

    /** Set after the badge entities are parented and before any glTF load. */
    var nameplatesMounted by mutableStateOf(false)
        private set

    /** Attachment panels stay out of the first SpatialView composition. */
    var showBadges by mutableStateOf(false)
        private set

    fun markOrderBoardTouched() {
        orderBoardTouched = true
    }

    fun markNameplatesMounted() {
        nameplatesMounted = true
    }

    fun markShowBadges() {
        showBadges = true
    }

    fun dispatch(event: KitchenEvent) {
        // A queued OS drop or menu callback can arrive after the last window stops.
        // Preserve the saved order until an actual foreground session resumes.
        if (!foreground || menu.page != StallMenuPage.PLAY) return
        if(event is KitchenEvent.Market){
            if(!assetsReady)return
            val before=state;state=reduce(state,event)
            if(state!=before){
                if (state.market.stage != before.market.stage) invalidateInput()
                playerPaused=state.market.stage!=MarketStage.COOK;selectedLabel=null;save()
            }
            return
        }
        // A lesson-page callback must not replace a newly opened market order.
        if (state.market.active && (event == KitchenEvent.NewShift || event == KitchenEvent.NextOrder ||
            (event == KitchenEvent.RetryOrder && state.market.stage != MarketStage.COOK))) return
        if (event == KitchenEvent.Tick && (!canInteract || !foreground)) return
        if (event !in listOf(KitchenEvent.NewShift,KitchenEvent.NextOrder,KitchenEvent.RetryOrder) && !canInteract) return
        val before = state
        state = reduce(before, event)
        if (sound && event != KitchenEvent.Tick) {
            StallSound.forAction(before, state, event)?.let { audio?.cue(it) }
        }
        if (event == KitchenEvent.NewShift || event == KitchenEvent.RetryOrder ||
            (event == KitchenEvent.NextOrder && before.phase == Phase.REVIEW) ||
            (before.phase != state.phase && state.phase in listOf(Phase.FAILED, Phase.REVIEW, Phase.SHIFT_END))) {
            invalidateInput()
        }
        if (event != KitchenEvent.Tick) selectedLabel = null
        if (event != KitchenEvent.Tick || state.shiftSeconds % 5 == 0) save()
    }
    fun dispatchMarket(event:MarketEvent,expected:MarketSession){
        if(state.market!=expected)return
        dispatch(KitchenEvent.Market(event))
    }

    /** One-shot state replace for the debug order-board controls. Release ignores it. */
    fun applyForDebug(next: KitchenState) {
        if (!BuildConfig.DEBUG) return
        state = next
    }
}
