package com.david.nightkitchen.scene

import android.os.Handler
import android.os.Looper
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Cold-start order for the stall.
 *
 * `Entity.loadSuspend` always hops to `Dispatchers.Main` and calls
 * `loadFromAssetsAsync` on that thread. On the emulator that call does not
 * return until the file is built: wok 3840 ms, rice bowl 2717 ms, egg 1601 ms,
 * about 9 s back to back. A coroutine `delay` of 160 ms resumes with
 * `Main.immediate`, so the next decode continues in the same main-looper
 * message and the input watchdog sees one multi-second stall.
 *
 * [breatheMain] posts to the real main [Looper]. The decode that follows is
 * its own message, and [AFTER_DECODE_MS] returns to the looper again before
 * the entity is parented. There is no public load API that parses a glTF off
 * the main thread: `Entity.load` from a worker `runBlocking`s back into
 * `loadSuspend`, which still runs the native call on Main.
 */
enum class BootSlot {
    WOK,
    RICE,
    EGG,
    COUNTER,
    LANTERN,
    SCALLION,
    SPATULA,
    PLATE,
    BELL,
    DISH,
    RING,
}

data class BootStep(
    val slot: BootSlot,
    val idleBeforeMs: Long,
)

object StallBoot {
    /** Idle after the order board is touched, before the first glb. */
    const val BEFORE_FIRST_MODEL_MS = 250L

    /** Idle between the end of one model and the decode of the next. */
    const val BETWEEN_MODELS_MS = 250L

    /** Looper turn after decode returns, before the mesh joins the scene. */
    const val AFTER_DECODE_MS = 100L

    /** Looper turn after the mesh is parented, before the grab sphere exists. */
    const val AFTER_ATTACH_MS = 50L

    /**
     * Gap before a dish-swap decode. Shorter than [BETWEEN_MODELS_MS] because
     * a swap is one to five files, each still in its own looper message.
     * A cached mesh skips the wait.
     */
    const val SWAP_GAP_MS = 150L

    /** Cold start loads no glTF. Nameplates and the order-board line only. */
    val coldSteps: List<BootStep> = emptyList()

    /**
     * Essentials for the opening dish (蛋炒饭): station, three grabs, spatula,
     * plate, bell, counter, lantern, and the drop ring. The plated serving
     * ([BootSlot.DISH]) is not in this list. It is decoded when the phase
     * actually shows a finished dish, so the first screen does not pay for a
     * second rice bowl or for any other dish's glb.
     * The wok is first, after [BEFORE_FIRST_MODEL_MS].
     */
    val deferredSteps: List<BootStep> =
        listOf(
            BootStep(BootSlot.WOK, BEFORE_FIRST_MODEL_MS),
            BootStep(BootSlot.RICE, BETWEEN_MODELS_MS),
            BootStep(BootSlot.EGG, BETWEEN_MODELS_MS),
            BootStep(BootSlot.COUNTER, BETWEEN_MODELS_MS),
            BootStep(BootSlot.LANTERN, BETWEEN_MODELS_MS),
            BootStep(BootSlot.SCALLION, BETWEEN_MODELS_MS),
            BootStep(BootSlot.SPATULA, BETWEEN_MODELS_MS),
            BootStep(BootSlot.PLATE, BETWEEN_MODELS_MS),
            BootStep(BootSlot.BELL, BETWEEN_MODELS_MS),
            BootStep(BootSlot.RING, BETWEEN_MODELS_MS),
        )
}

/**
 * One main-looper turn. No timer.
 *
 * Use this to split messages. A multi-second gap belongs in [breatheMain],
 * and only around a glTF decode.
 */
suspend fun yieldMain() {
    val handler = Handler(Looper.getMainLooper())
    suspendCancellableCoroutine { cont ->
        val resume =
            Runnable {
                if (cont.isActive) cont.resume(Unit)
            }
        handler.post(resume)
        cont.invokeOnCancellation { handler.removeCallbacks(resume) }
    }
}

/**
 * Real wait between glTF decodes.
 *
 * [kotlinx.coroutines.delay] resumes on `Dispatchers.Main.immediate`, so the
 * next line keeps running inside the same looper message. `postDelayed` ends
 * that message. The following `post` starts the next native load in a
 * different message from the timer itself.
 */
suspend fun breatheMain(ms: Long) {
    val handler = Handler(Looper.getMainLooper())
    suspendCancellableCoroutine { cont ->
        val resume =
            Runnable {
                if (cont.isActive) cont.resume(Unit)
            }
        handler.postDelayed(resume, ms.coerceAtLeast(0L))
        cont.invokeOnCancellation { handler.removeCallbacks(resume) }
    }
    suspendCancellableCoroutine { cont ->
        val resume =
            Runnable {
                if (cont.isActive) cont.resume(Unit)
            }
        handler.post(resume)
        cont.invokeOnCancellation { handler.removeCallbacks(resume) }
    }
}
