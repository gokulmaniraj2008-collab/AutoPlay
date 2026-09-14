package com.gokul.autoplay

import android.content.Context

object TaskHistoryStore {
    private const val PREFS = "autoplay_task_history"
    private const val KEY_LAST = "last_result"

    fun record(context: Context, task: PhoneTask, result: TaskExecutor.Result) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST, "${task.rawCommand}\n${if (result.success) "SUCCESS" else "FAILED"}: ${result.message}")
            .apply()
    }

    fun last(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_LAST, "No task has executed yet.") ?: "No task has executed yet."
}
