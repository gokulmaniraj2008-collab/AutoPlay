package com.gokul.autoplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_TASK_ID) ?: return
        val task = TaskStore.load(context).firstOrNull { it.id == id && it.enabled } ?: return
        val result = TaskExecutor.execute(context, task)
        TaskHistoryStore.record(context, task, result)
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}
