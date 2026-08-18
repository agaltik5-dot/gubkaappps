package com.example.myapplication.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class RecentItem(
    val id: Int,
    val name: String,
    val type: String // "group" or "teacher"
)

class RecentItemsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("recent_items_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun addItem(id: Int, name: String, type: String) {
        val currentItems = getItems().toMutableList()
        // Remove if exists to move to top
        currentItems.removeAll { it.id == id && it.type == type }
        currentItems.add(0, RecentItem(id, name, type))
        
        // Keep only last 5 items
        val listToSave = if (currentItems.size > 5) currentItems.take(5) else currentItems
        
        prefs.edit().putString("key_recent_list", gson.toJson(listToSave)).apply()
    }

    fun getItems(): List<RecentItem> {
        val json = prefs.getString("key_recent_list", null) ?: return emptyList()
        val type = object : TypeToken<List<RecentItem>>() {}.type
        return gson.fromJson(json, type)
    }
}
