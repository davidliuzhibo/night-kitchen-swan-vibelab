package com.david.nightkitchen.content

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.david.nightkitchen.BuildConfig
import com.david.nightkitchen.game.EmulatorPlay
import com.david.nightkitchen.game.KitchenHub
import com.david.nightkitchen.game.Phase
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text

/**
 * Emulator stand-in when pinch tracking is absent. Compiled only into the
 * debug stall. Release uses the empty composable in the release source set.
 * These buttons are not the official clear path.
 */
@Composable
fun DebugStallControls() {
    if (!BuildConfig.DEBUG) return
    val state = KitchenHub.state
    val playing =
        state.phase == Phase.PREP ||
            state.phase == Phase.COOK ||
            state.phase == Phase.PLATE ||
            state.phase == Phase.SERVE
    if (playing) {
        StepButton(EmulatorPlay.GRAB_NEXT, EmulatorPlay.grabNext(state) != null) {
            EmulatorPlay.grabNext(KitchenHub.state)?.let(KitchenHub::dispatch)
        }
        StepButton(EmulatorPlay.PLATE, EmulatorPlay.plate(state) != null) {
            EmulatorPlay.plate(KitchenHub.state)?.let(KitchenHub::dispatch)
        }
        StepButton(EmulatorPlay.SERVE, EmulatorPlay.serve(state) != null) {
            EmulatorPlay.serve(KitchenHub.state)?.let(KitchenHub::dispatch)
        }
    }
    if (state.phase != Phase.SHIFT_END) {
        StepButton(EmulatorPlay.END_SHIFT, enabled = true) {
            KitchenHub.applyForDebug(EmulatorPlay.fastForwardShift(KitchenHub.state))
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = label },
        enabled = enabled,
    ) {
        Text(text = label)
    }
}
