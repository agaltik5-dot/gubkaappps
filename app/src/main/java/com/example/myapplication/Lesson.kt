package com.example.myapplication

/**
 * Модель данных для одного занятия (пары).
 * Содержит всю информацию, которую мы будем отображать в карточке item_lesson.
 */
data class Lesson(
    val subject: String,      // Название предмета
    val startTime: String,    // Время начала
    val endTime: String,      // Время окончания
    val type: String,         // Тип (Лекция, Семинар и т.д.)
    val details: String,      // Доп. инфо (кабинет и преподаватель)
    val duration: String      // Длительность (например, "2ч")
)
