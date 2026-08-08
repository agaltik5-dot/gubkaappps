package com.example.myapplication.data

/**
 * Модель данных для одной новости.
 */
data class NewsItem(
    val title: String,      // Заголовок
    val date: String,       // Дата публикации
    val imageUrl: String,   // Ссылка на картинку
    val detailUrl: String   // Ссылка на полную статью
)
