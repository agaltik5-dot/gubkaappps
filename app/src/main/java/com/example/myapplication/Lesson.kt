package com.example.myapplication

/**
 * Общий интерфейс для элементов расписания (пары и перерывы).
 */
sealed interface ScheduleItem

/**
 * Модель данных для одного занятия (пары).
 */
data class Lesson(
    val subject: String,      // Название предмета
    val startTime: String,    // Время начала
    val endTime: String,      // Время окончания
    val type: String,         // Тип (Лекция, Семинар и т.д.)
    val details: String,      // Доп. инфо (кабинет и преподаватель)
    val duration: String      // Длительность (например, "2ч")
) : ScheduleItem

/**
 * Модель данных для перерыва между парами.
 */
data class Gap(
    val durationText: String // Текст перерыва (например, "перерыв 15 мин")
) : ScheduleItem
