package com.david.nightkitchen

import android.os.SystemClock
import android.util.Log

/**
 * Cold-start timestamps. Filter logcat on `NightKitchen`.
 * Stages: `t0 process`, `application created`, `gate ready`,
 * `engine init started after user tap`, `mainApp entered`, `panels ready`,
 * `nameplates mounted`.
 * Spans: `spatial class init`, `mainApp registered`, `launch returned`,
 * `engine init done`.
 */
object BootTrace {
    private const val TAG = "NightKitchen"

    @Volatile
    var t0ElapsedMs: Long = SystemClock.elapsedRealtime()
        private set

    fun markProcess() {
        t0ElapsedMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "t0 process")
    }

    fun stage(name: String) {
        val ms = SystemClock.elapsedRealtime() - t0ElapsedMs
        Log.i(TAG, "$name ${ms}ms after process")
    }

    /** Wall time of one main-thread section. Warns when it exceeds 200ms. */
    fun span(name: String, startedElapsedMs: Long) {
        val dt = SystemClock.elapsedRealtime() - startedElapsedMs
        Log.i(TAG, "$name ${dt}ms")
        if (dt > 200L) {
            Log.w(TAG, "$name exceeded 200ms (${dt}ms)")
        }
    }
}
