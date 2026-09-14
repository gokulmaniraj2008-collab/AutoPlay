package com.gokul.autoplay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object TaskStore {
    private const val PREFS = "autoplay_tasks"
    private const val KEY_TASKS = "tasks_json"

    fun load(context: Context): List<PhoneTask> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TASKS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        PhoneTask(
                            id = o.getString("id"),
                            rawCommand = o.getString("rawCommand"),
                            action = PhoneTask.Action.valueOf(o.getString("action")),
                            target = o.optString("target"),
                            scheduledAtMillis = if (o.isNull("scheduledAtMillis")) null else o.optLong("scheduledAtMillis"),
                            enabled = o.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, tasks: List<PhoneTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(JSONObject().apply {
                put("id", task.id)
                put("rawCommand", task.rawCommand)
                put("action", task.action.name)
                put("target", task.target)
                if (task.scheduledAtMillis == null) put("scheduledAtMillis", JSONObject.NULL)
                else put("scheduledAtMillis", task.scheduledAtMillis)
                put("enabled", task.enabled)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASKS, array.toString())
            .apply()
    }

    fun upsert(context: Context, task: PhoneTask) {
        save(context, load(context).filterNot { it.id == task.id } + task)
    }

    fun delete(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
    }
}
