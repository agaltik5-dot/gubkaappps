package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.Gap
import com.example.myapplication.Lesson
import com.example.myapplication.ScheduleItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
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

    /**
     * Возвращает список групп для конкретного факультета из JSON.
     */
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

    /**
     * Загружает расписание для конкретной даты и ID группы из JSON в assets.
     */
    fun getScheduleForDate(groupId: Int, date: Calendar): List<ScheduleItem> {
        val weekKey = getWeekKey(date)
        val fileName = "cache/week_${groupId}_$weekKey.json"
        
        return try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            parseScheduleJson(jsonString, date)
        } catch (e: Exception) {
            // Если файл не найден или ошибка парсинга — возвращаем пустой список
            emptyList()
        }
    }

    private fun getWeekKey(date: Calendar): String {
        val cal = date.clone() as Calendar
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4
        
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        
        // Коррекция для начала/конца года (ISO 8601)
        var isoYear = year
        if (week == 1 && cal.get(Calendar.MONTH) == Calendar.DECEMBER) {
            isoYear++
        } else if (week >= 52 && cal.get(Calendar.MONTH) == Calendar.JANUARY) {
            isoYear--
        }
        
        return String.format("%d-W%02d", isoYear, week)
    }

    private fun parseScheduleJson(jsonString: String, targetDate: Calendar): List<ScheduleItem> {
        val lessons = mutableListOf<Lesson>()
        val root = JSONObject(jsonString)
        val rows = root.getJSONObject("rows")
        val organizations = rows.getJSONArray("organizations")
        
        var moscowOrg: JSONObject? = null
        for (i in 0 until organizations.length()) {
            val org = organizations.getJSONObject(i)
            if (org.getString("name") == "Москва") {
                moscowOrg = org
                break
            }
        }

        if (moscowOrg != null) {
            val timeChunks = moscowOrg.getJSONArray("lessonsTimeChunks")
            val rawLessons = moscowOrg.getJSONArray("lessons")

            val targetDayNum = if (targetDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 6
                              else targetDate.get(Calendar.DAY_OF_WEEK) - 2

            for (i in 0 until rawLessons.length()) {
                val item = rawLessons.getJSONObject(i)
                if (item.getInt("weekDayNumber") == targetDayNum) {
                    // Учитываем подгруппы и отмены
                    if (!item.optBoolean("isCanceled", false)) {
                        lessons.add(mapJsonToLesson(item, timeChunks))
                    }
                }
            }
        }
        
        val sortedLessons = lessons.sortedBy { timeToMinutes(it.startTime) }
        return addGapsBetweenLessons(sortedLessons)
    }

    private fun addGapsBetweenLessons(lessons: List<Lesson>): List<ScheduleItem> {
        if (lessons.isEmpty()) return emptyList()
        
        val items = mutableListOf<ScheduleItem>()
        for (i in lessons.indices) {
            items.add(lessons[i])
            
            // Если есть следующая пара, считаем перерыв
            if (i < lessons.size - 1) {
                val currentEnd = timeToMinutes(lessons[i].endTime)
                val nextStart = timeToMinutes(lessons[i + 1].startTime)
                
                val gapMinutes = nextStart - currentEnd
                if (gapMinutes > 0) {
                    items.add(Gap(formatGapText(gapMinutes)))
                }
            }
        }
        return items
    }

    private fun timeToMinutes(time: String): Int {
        return try {
            val parts = time.split(":")
            val hours = parts[0].trim().toInt()
            val minutes = parts[1].trim().toInt()
            hours * 60 + minutes
        } catch (e: Exception) {
            0
        }
    }

    private fun formatGapText(minutes: Int): String {
        return when {
            minutes >= 60 -> {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0) "перерыв $h ч" else "перерыв $h ч $m мин"
            }
            else -> "перерыв $minutes мин"
        }
    }

    private fun mapJsonToLesson(json: JSONObject, timeChunks: org.json.JSONArray): Lesson {
        val courseObj = json.getJSONObject("course")
        var subject = courseObj.getString("name")
        val type = json.optString("type", "—")
        
        val chunks = json.getJSONArray("timeChunks")
        val startIdx = chunks.getInt(0)
        val endIdx = chunks.getInt(chunks.length() - 1)

        val startTime = timeChunks.getString(startIdx).split("-")[0]
        val endTime = timeChunks.getString(endIdx).split("-")[1]
        
        // Обработка изменений (замены)
        val changes = json.optJSONObject("changes")

        // Преподаватели (сначала из замен)
        val teachersArray = changes?.optJSONArray("teachers") ?: json.optJSONArray("teachers")
        var teacherName = ""
        if (teachersArray != null && teachersArray.length() > 0) {
            val t = teachersArray.getJSONObject(0)
            val last = t.optString("lastName", "").trim()
            val first = t.optString("firstName", "").take(1)
            val patron = t.optString("patronymic", "").take(1)
            teacherName = if (last.isNotEmpty()) "$last $first.$patron." else ""
        }
        
        // Аудитории (сначала из замен)
        val roomsArray = changes?.optJSONArray("rooms") ?: json.optJSONArray("rooms")
        var roomNumber = ""
        if (roomsArray != null && roomsArray.length() > 0) {
            roomNumber = roomsArray.getJSONObject(0).optString("number", "")
        }

        val subgroup = json.optInt("subgroup", 0)
        if (subgroup > 0) {
            subject += " (п/г $subgroup)"
        }
        
        val details = listOf(roomNumber, teacherName).filter { it.isNotEmpty() }.joinToString(" · ")

        return Lesson(
            subject = subject,
            startTime = startTime,
            endTime = endTime,
            type = type,
            details = details,
            duration = "${chunks.length() * 45}м"
        )
    }
}
