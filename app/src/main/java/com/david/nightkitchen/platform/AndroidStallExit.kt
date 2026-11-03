package com.david.nightkitchen.platform

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.util.Log

internal fun closeGameWindows(context: Context, hosts: List<Activity>, closeSpatial: (StallWindow) -> Unit) {
    val tasks = ownedTasks(context, hosts.map { it.taskId }.toSet())
    closeStallWindows(closeSpatial, { window, error -> Log.w("NightKitchen", "Closing ${window.id}", error) }) {
        hosts.sortedBy { it is LaunchActivity }.forEach(::finishHost)
        tasks.forEach { task ->
            try { task.finishAndRemoveTask() } catch (e: RuntimeException) {
                Log.d("NightKitchen", "App task no longer available", e)
            }
        }
    }
}

private fun ownedTasks(context: Context, liveIds: Set<Int>): List<ActivityManager.AppTask> {
    return try {
        context.getSystemService(ActivityManager::class.java)?.appTasks.orEmpty().filter { task ->
            val info = try { task.taskInfo } catch (_: IllegalArgumentException) { null }
            info != null && (info.taskId in liveIds || info.baseActivity?.packageName == context.packageName)
        }
    } catch (e: RuntimeException) {
        Log.w("NightKitchen", "Using live stall hosts to close the windows", e)
        emptyList()
    }
}

private fun finishHost(activity: Activity) {
    if (activity.isFinishing || activity.isDestroyed) return
    try { activity.finishAndRemoveTask() } catch (e: RuntimeException) {
        Log.w("NightKitchen", "Closing remaining window ${activity.localClassName}", e)
        activity.finish()
    }
}
