package com.example.myapplication.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Модель для хранения недавно выбранной группы или преподавателя.
 */
data class RecentSelection(
    val id: Int,
    val name: String,
    val type: SelectionType
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("type", type.name)
        return obj.toString()
    }

    companion object {
        fun fromJson(json: String): RecentSelection {
            val obj = JSONObject(json)
            return RecentSelection(
                obj.getInt("id"),
                obj.getString("name"),
                SelectionType.valueOf(obj.getString("type"))
            )
        }
    }
}

enum class SelectionType {
    GROUP, TEACHER
}
