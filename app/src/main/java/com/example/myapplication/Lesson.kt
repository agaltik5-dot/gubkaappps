package com.example.myapplication

/**
 * Общий интерфейс для элементов расписания (пары и перерывы).
 */
sealed interface ScheduleItem

/**
 * Модель данных для одного занятия (пары). 
 * Может содержать одну или две подгруппы (contents).
 */
data class Lesson(
    val startTime: String,    // Время начала
    val endTime: String,      // Время окончания
    val duration: String,     // Длительность (например, "90м")
    val contents: List<LessonContent> // Список данных (обычно 1, для подгрупп 2)
) : ScheduleItem {
    // Вспомогательное свойство: разделена ли пара на подгруппы
    val isSplit: Boolean get() = contents.size > 1
}

/**
 * Конкретные данные занятия (предмет, преподаватель и т.д.)
 */
data class LessonContent(
    val subject: String,      // Название предмета
    val type: String,         // Тип (Лекция, Семинар)
    val room: String,         // Аудитория
    val teacher: String,      // Преподаватель
    val subgroup: Int = 0     // Номер подгруппы (0 - на всех, 1 или 2)
)

/**
 * Модель данных для перерыва между парами.
 */
data class Gap(
    val durationText: String // Текст перерыва (например, "перерыв 15 мин")
) : ScheduleItem
