package com.rinat.goalnotes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Note(
    val id: Long,
    val title: String,
    val text: String,
    val updated: Long,
)

data class Step(
    val id: Long,
    val text: String,
    val done: Boolean,
)

data class Goal(
    val id: Long,
    val title: String,
    val description: String,
    val steps: List<Step>,
    val remind: Boolean,
    val hour: Int,
    val minute: Int,
    val created: Long,
) {
    val doneCount: Int get() = steps.count { it.done }
    val progress: Float get() = if (steps.isEmpty()) 0f else doneCount.toFloat() / steps.size
    val nextStep: Step? get() = steps.firstOrNull { !it.done }
}

data class AppData(
    val notes: List<Note> = emptyList(),
    val goals: List<Goal> = emptyList(),
)

/** Хранит все данные в одном JSON-файле во внутренней памяти приложения. */
object Store {
    private const val FILE = "data.json"

    @Synchronized
    fun load(ctx: Context): AppData {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return AppData()
        return try {
            fromJson(JSONObject(f.readText()))
        } catch (e: Exception) {
            // Сохраняем повреждённый файл, чтобы данные не потерялись
            f.copyTo(File(ctx.filesDir, "data.broken.json"), overwrite = true)
            AppData()
        }
    }

    @Synchronized
    fun save(ctx: Context, data: AppData) {
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(toJson(data).toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    private fun toJson(d: AppData): JSONObject {
        val notes = JSONArray()
        d.notes.forEach { n ->
            notes.put(
                JSONObject()
                    .put("id", n.id)
                    .put("title", n.title)
                    .put("text", n.text)
                    .put("updated", n.updated)
            )
        }
        val goals = JSONArray()
        d.goals.forEach { g ->
            val steps = JSONArray()
            g.steps.forEach { s ->
                steps.put(JSONObject().put("id", s.id).put("text", s.text).put("done", s.done))
            }
            goals.put(
                JSONObject()
                    .put("id", g.id)
                    .put("title", g.title)
                    .put("description", g.description)
                    .put("steps", steps)
                    .put("remind", g.remind)
                    .put("hour", g.hour)
                    .put("minute", g.minute)
                    .put("created", g.created)
            )
        }
        return JSONObject().put("version", 1).put("notes", notes).put("goals", goals)
    }

    private fun fromJson(o: JSONObject): AppData {
        val notes = mutableListOf<Note>()
        val na = o.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until na.length()) {
            val n = na.getJSONObject(i)
            notes += Note(
                id = n.getLong("id"),
                title = n.optString("title"),
                text = n.optString("text"),
                updated = n.optLong("updated"),
            )
        }
        val goals = mutableListOf<Goal>()
        val ga = o.optJSONArray("goals") ?: JSONArray()
        for (i in 0 until ga.length()) {
            val g = ga.getJSONObject(i)
            val steps = mutableListOf<Step>()
            val sa = g.optJSONArray("steps") ?: JSONArray()
            for (j in 0 until sa.length()) {
                val s = sa.getJSONObject(j)
                steps += Step(s.getLong("id"), s.optString("text"), s.optBoolean("done"))
            }
            goals += Goal(
                id = g.getLong("id"),
                title = g.optString("title"),
                description = g.optString("description"),
                steps = steps,
                remind = g.optBoolean("remind", true),
                hour = g.optInt("hour", 9),
                minute = g.optInt("minute", 0),
                created = g.optLong("created", System.currentTimeMillis()),
            )
        }
        return AppData(notes, goals)
    }
}
