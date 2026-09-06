package com.example.myapplication.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ScheduleSyncHelper {

    private const val PREFS_NAME = "user_prefs"
    private const val KEY_SYNC_VERSION = "schedule_sync_version"

    fun getWeekKey(date: Calendar): String {
        val cal = date.clone() as Calendar
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        var isoYear = year
        if (week == 1 && cal.get(Calendar.MONTH) == Calendar.DECEMBER) isoYear++
        else if (week >= 52 && cal.get(Calendar.MONTH) == Calendar.JANUARY) isoYear--
        return String.format(Locale.US, "%d-W%02d", isoYear, week)
    }

    fun saveWeekCache(context: Context, groupId: Int, date: Calendar, data: JSONObject): Boolean {
        if (!data.optBoolean("state", false)) return false
        val weekKey = getWeekKey(date)
        val file = File(context.filesDir, "cache/week_${groupId}_$weekKey.json")
        file.parentFile?.mkdirs()
        return try {
            file.writeText(data.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun mergeGroupIntoFacultyCache(context: Context, facultyId: Int, groupCode: String, groupId: Int) {
        val file = File(context.filesDir, "groups_cache/faculty_$facultyId.json")
        file.parentFile?.mkdirs()

        val existing = loadGroupsArray(context, facultyId).toMutableList()
        if (existing.any { it.second == groupId }) return

        existing.add(groupCode to groupId)
        existing.sortBy { it.first }

        val array = JSONArray()
        existing.forEach { (code, id) ->
            array.put(JSONArray().put(code).put(id))
        }
        try {
            file.writeText(array.toString())
        } catch (_: Exception) {}
    }

    private fun loadGroupsArray(context: Context, facultyId: Int): List<Pair<String, Int>> {
        val relativePath = "groups_cache/faculty_$facultyId.json"
        val internalFile = File(context.filesDir, relativePath)
        val jsonString = when {
            internalFile.exists() -> internalFile.readText()
            else -> try {
                context.assets.open(relativePath).bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                return emptyList()
            }
        }
        return parseGroupsJson(jsonString)
    }

    private fun parseGroupsJson(jsonString: String): List<Pair<String, Int>> {
        return try {
            val array = JSONArray(jsonString)
            val groups = mutableListOf<Pair<String, Int>>()
            for (i in 0 until array.length()) {
                val groupArray = array.getJSONArray(i)
                groups.add(groupArray.getString(0) to groupArray.getInt(1))
            }
            groups
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun extractGroupFromSchedule(data: JSONObject): Triple<Int, String, Int>? {
        val orgs = data.optJSONObject("rows")?.optJSONArray("organizations") ?: return null
        for (i in 0 until orgs.length()) {
            val lessons = orgs.getJSONObject(i).optJSONArray("lessons") ?: continue
            if (lessons.length() == 0) continue
            val groups = lessons.getJSONObject(0).optJSONArray("groups") ?: continue
            if (groups.length() == 0) continue
            val group = groups.getJSONObject(0)
            return Triple(
                group.optInt("id"),
                group.optString("code"),
                group.optInt("facultyId")
            )
        }
        return null
    }

    fun fetchOneWeek(
        client: OkHttpClient,
        groupId: Int,
        date: Calendar,
        cookie: String?,
        userAgent: String
    ): JSONObject {
        val dateStr = SimpleDateFormat("d-M-yyyy", Locale.US).format(date.time)
        val url = "https://lk.gubkin.ru/schedule/api/api.php?act=schedule&date=$dateStr&groupId=$groupId"
        val request = Request.Builder()
            .url(url)
            .addHeader("Cookie", cookie ?: "")
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Referer", "https://lk.gubkin.ru/schedule/")
            .addHeader("Origin", "https://lk.gubkin.ru")
            .addHeader("User-Agent", userAgent)
            .addHeader("Cache-Control", "no-cache")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                android.util.Log.d("ScheduleSync", "API Response for group $groupId: code=${response.code}")
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body.isNullOrBlank()) JSONObject() else JSONObject(body)
                }
                else JSONObject()
            }
        } catch (e: Exception) {
            android.util.Log.e("ScheduleSync", "API Error for group $groupId: ${e.message}")
            JSONObject()
        }
    }

    fun syncGroupWeeks(
        context: Context,
        client: OkHttpClient,
        groupId: Int,
        cookie: String?,
        userAgent: String,
        weekCount: Int = 2
    ): Int {
        var savedWeeks = 0
        val cal = Calendar.getInstance()
        // Устанавливаем на понедельник текущей недели для стабильности API
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        
        repeat(weekCount) {
            val data = fetchOneWeek(client, groupId, cal, cookie, userAgent)
            if (saveWeekCache(context, groupId, cal, data)) {
                savedWeeks++
                extractGroupFromSchedule(data)?.let { (_, code, facultyId) ->
                    if (facultyId >= 0 && code.isNotBlank()) {
                        mergeGroupIntoFacultyCache(context, facultyId, code, groupId)
                    }
                }
            }
            cal.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return savedWeeks
    }

    fun bumpSyncVersion(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val version = prefs.getInt(KEY_SYNC_VERSION, 0)
        prefs.edit().putInt(KEY_SYNC_VERSION, version + 1).apply()
    }

    fun parseGroupIdFromUrl(url: String?): Int? {
        if (url.isNullOrBlank()) return null
        val patterns = listOf(
            Regex("groups/(\\d+)", RegexOption.IGNORE_CASE),
            Regex("groupId=(\\d+)", RegexOption.IGNORE_CASE),
            Regex("group/(\\d+)", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    fun isScheduleSessionReady(url: String?, cookie: String? = null): Boolean {
        if (url.isNullOrBlank() || !url.contains("/schedule/")) return false
        
        // Если в URL есть ID группы/преподавателя/аудитории - значит выбор сделан
        val hasSelection = url.contains("groupId=") || url.contains("groups/") || 
                          url.contains("teacherId=") || url.contains("teachers/") ||
                          url.contains("roomId=") || url.contains("rooms/") ||
                          url.contains("/lessons")

        if (hasSelection) return true
        
        // Ручное обновление: достаточно активной сессии после капчи
        return !cookie.isNullOrBlank() && cookie.contains("PHPSESSID=")
    }
}
