package com.david.nightkitchen.platform

internal enum class StallWindow(val id: String, val tag: String? = null) {
    ORDERS("order-board", "orders"), REVIEWS("review-slip", "reviews"), COUNTER("kitchen-volume")
}

/** Ignore a late panel-open request after quitting. A new launcher visit starts at the counter. */
internal class StallVisits<T : Any>(private val isCounter: (T) -> Boolean) {
    private val members = linkedSetOf<T>()
    val hosts: Set<T> get() = members
    private var awaitingLaunch = false

    fun join(host: T): Boolean {
        if (awaitingLaunch) {
            if (members.isNotEmpty() || !isCounter(host)) return false
            awaitingLaunch = false
        }
        return members.add(host)
    }

    fun quit() { awaitingLaunch = true }
    fun remove(host: T) = members.remove(host)
}

/** Auxiliary hosts close first. A panel already closed by the player cannot strand the others. */
internal fun closeStallWindows(
    close: (StallWindow) -> Unit,
    failed: (StallWindow, RuntimeException) -> Unit,
    finishTasks: () -> Unit
) {
    for (window in StallWindow.entries) {
        try { close(window) } catch (e: RuntimeException) { failed(window, e) }
    }
    finishTasks()
}
