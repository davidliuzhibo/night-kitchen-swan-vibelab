package com.david.nightkitchen.platform

/** The cooker, bill and order board belong to one stall, even though Android gives each a window. */
internal class StallFocus<T : Any>(
    private val now: () -> Long,
    private val later: (Long, () -> Unit) -> Unit,
    private val changed: (Boolean) -> Unit,
    private val onAway: () -> Unit = {}
) {
    private val owners = mutableSetOf<T>()
    private var departed: T? = null
    private var leftAt = 0L
    private var epoch = 0L
    private var available = true
    private var announcedAway = false
    private fun confirmAway() {
        mark(false)
        if (!announcedAway) {
            announcedAway = true
            onAway()
        }
    }
    private fun mark(value: Boolean) {
        if (available != value) { available = value; changed(value) }
    }
    fun update(owner: T, top: Boolean) {
        if (top) {
            if (!owners.add(owner)) return
            // A return to the same window without another stall window taking its place
            // is a real interruption, including one shorter than the transfer allowance.
            if (departed != null && now() - leftAt >= HANDOFF_MS) confirmAway()
            else if (departed == owner) mark(false)
            epoch++; departed = null; announcedAway = false; mark(true)
        } else if (owners.remove(owner) && owners.isEmpty()) {
            departed = owner; leftAt = now()
            val token = ++epoch
            later(HANDOFF_MS) { if (epoch == token && owners.isEmpty()) confirmAway() }
        }
    }
    fun reset() {
        epoch++
        owners.clear()
        departed = null
        available = true
        announcedAway = false
    }
    internal companion object { const val HANDOFF_MS = 350L }
}
