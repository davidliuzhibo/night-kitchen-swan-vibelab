package com.david.nightkitchen.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import java.io.IOException

internal enum class StallMenuPage { PLAY, CHOICE, SAVING, SAVE_ERROR, CLOSING }

/** A single exit decision for all three windows. Saving may finish after a visit was destroyed. */
internal class StallMenu(private val changed: (StallMenuPage) -> Unit) {
    var page by mutableStateOf(StallMenuPage.PLAY)
        private set
    private var visit = 0L

    fun open() {
        if (page == StallMenuPage.PLAY) show(StallMenuPage.CHOICE)
    }

    fun resume() {
        if (page == StallMenuPage.CHOICE || page == StallMenuPage.SAVE_ERROR) show(StallMenuPage.PLAY)
    }

    fun needsPanel(foreground: Boolean, visiblePanel: Boolean): Boolean =
        foreground && !visiblePanel && (page == StallMenuPage.CHOICE || page == StallMenuPage.SAVE_ERROR)

    suspend fun saveAndClose(save: suspend () -> Boolean, close: () -> Unit) {
        if (page != StallMenuPage.CHOICE && page != StallMenuPage.SAVE_ERROR) return
        val currentVisit = visit
        show(StallMenuPage.SAVING)
        val saved = try {
            save()
        } catch (cancelled: CancellationException) {
            if (visit == currentVisit) show(StallMenuPage.CHOICE)
            throw cancelled
        } catch (e: IOException) {
            false
        }
        if (visit != currentVisit) return
        show(if (saved) StallMenuPage.CLOSING else StallMenuPage.SAVE_ERROR)
        if (saved) close()
    }

    fun reset() {
        visit++
        show(StallMenuPage.PLAY)
    }

    private fun show(next: StallMenuPage) {
        if (page != next) {
            page = next
            changed(next)
        }
    }
}
