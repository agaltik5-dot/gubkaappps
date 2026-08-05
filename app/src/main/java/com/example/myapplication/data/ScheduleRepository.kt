package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.Gap
import com.example.myapplication.Lesson
import com.example.myapplication.LessonContent
import com.example.myapplication.ScheduleItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class ScheduleRepository(private val context: Context) {

    val FACULTIES = mapOf(
        0 to "ФГиГНиГ",
        1 to "ФРНиГМ",
        2 to "ФПСиЭСТТ",
        3 to "ФИМ",
        4 to "ФХТиЭ",
        5 to "АиВТ",
        6 to "ФЭиУ",
        12 to "ФМЭБ",
        21 to "ФКБ ТЭК"
    )

    fun getGroupsForFaculty(facultyId: Int): List<Pair<String, Int>> {
        val fileName = "groups_cache/faculty_$facultyId.json"
        return try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val array = JSONArray(jsonString)
            val groups = mutableListOf<Pair<String, Int>>()
            for (i in 0 until array.length()) {
                val groupArray = array.getJSONArray(i)
                groups.add(groupArray.getString(0) to groupArray.getInt(1))
            }
            groups.sortedBy { it.first }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getScheduleForDate(groupId: Int, date: Calendar): List<ScheduleItem> {
        val weekKey = getWeekKey(date)
        val fileName = "cache/week_${groupId}_$weekKey.json"
        
        return try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            parseScheduleJson(jsonString, date)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getWeekKey(date: Calendar): String {
        val cal = date.clone() as Calendar
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        var isoYear = year
        if (week == 1 && cal.get(Calendar.MONTH) == Calendar.DECEMBER) isoYear++
        else if (week >= 52 && cal.get(Calendar.MONTH) == Calendar.JANUARY) isoYear--
        return String.format("%d-W%02d", isoYear, week)
    }

    private fun parseScheduleJson(jsonString: String, targetDate: Calendar): List<ScheduleItem> {
        val root = JSONObject(jsonString)
        val organizations = root.getJSONObject("rows").getJSONArray("organizations")
        
        var moscowOrg: JSONObject? = null
        for (i in 0 until organizations.length()) {
            if (organizations.getJSONObject(i).getString("name") == "Москва") {
                moscowOrg = organizations.getJSONObject(i)
                break
            }
        }

        if (moscowOrg == null) return emptyList()

        val timeChunks = moscowOrg.getJSONArray("lessonsTimeChunks")
        val rawLessons = moscowOrg.getJSONArray("lessons")
        val targetDayNum = if (targetDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 6
                          else targetDate.get(Calendar.DAY_OF_WEEK) - 2

        // Собираем все занятия за день
        val dayRawLessons = mutableListOf<Triple<String, String, LessonContent>>()
        for (i in 0 until rawLessons.length()) {
            val item = rawLessons.getJSONObject(i)
            if (item.getInt("weekDayNumber") == targetDayNum && !item.optBoolean("isCanceled", false)) {
                val chunks = item.getJSONArray("timeChunks")
                val startTime = timeChunks.getString(chunks.getInt(0)).split("-")[0]
                val endTime = timeChunks.getString(chunks.getInt(chunks.length() - 1)).split("-")[1]
                val content = mapJsonToContent(item)
                dayRawLessons.add(Triple(startTime, endTime, content))
            }
        }

        // Группируем по времени начала
        val groupedLessons = dayRawLessons.groupBy { it.first }
            .map { (start, lessonsAtTime) ->
                Lesson(
                    startTime = start,
                    endTime = lessonsAtTime.first().second,
                    duration = "${(lessonsAtTime.size * 45)}м", // Упрощенно, лучше считать по chunks
                    contents = lessonsAtTime.map { it.third }.sortedBy { it.subgroup }
                )
            }.sortedBy { timeToMinutes(it.startTime) }

        return addGapsBetweenLessons(groupedLessons)
    }

    private fun addGapsBetweenLessons(lessons: List<Lesson>): List<ScheduleItem> {
        if (lessons.isEmpty()) return emptyList()
        val items = mutableListOf<ScheduleItem>()
        for (i in lessons.indices) {
            items.add(lessons[i])
            if (i < lessons.size - 1) {
                val currentEnd = timeToMinutes(lessons[i].endTime)
                val nextStart = timeToMinutes(lessons[i + 1].startTime)
                val gapMinutes = nextStart - currentEnd
                if (gapMinutes > 0) items.add(Gap(formatGapText(gapMinutes)))
            }
        }
        return items
    }

    private fun timeToMinutes(time: String): Int = try {
        val parts = time.split(":")
        parts[0].trim().toInt() * 60 + parts[1].trim().toInt()
    } catch (e: Exception) { 0 }

    private fun formatGapText(minutes: Int): String = when {
        minutes >= 60 -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "перерыв $h ч" else "перерыв $h ч $m мин"
        }
        else -> "перерыв $minutes мин"
    }

    private fun mapJsonToContent(json: JSONObject): LessonContent {
        val subject = json.getJSONObject("course").getString("name")
        val type = json.optString("type", "—")
        val changes = json.optJSONObject("changes")
        
        val teachersArray = changes?.optJSONArray("teachers") ?: json.optJSONArray("teachers")
        var teacherName = ""
        if (teachersArray != null && teachersArray.length() > 0) {
            val t = teachersArray.getJSONObject(0)
            val last = t.optString("lastName", "").trim()
            val first = t.optString("firstName", "").take(1)
            val patron = t.optString("patronymic", "").take(1)
            teacherName = if (last.isNotEmpty()) "$last $first.$patron." else ""
        }
        
        val roomsArray = changes?.optJSONArray("rooms") ?: json.optJSONArray("rooms")
        var roomNumber = ""
        if (roomsArray != null && roomsArray.length() > 0) {
            roomNumber = roomsArray.getJSONObject(0).optString("number", "")
        }

        return LessonContent(
            subject = subject,
            type = type,
            room = roomNumber,
            teacher = teacherName,
            subgroup = json.optInt("subgroup", 0)
        )
    }
}
