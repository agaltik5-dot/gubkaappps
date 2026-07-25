package com.example.myapplication.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.Lesson
import com.example.myapplication.LessonAdapter
import com.example.myapplication.R

/**
 * Адаптер для ViewPager2, реализующий "бесконечный" скролл.
 * @param lessonProvider функция, которая возвращает список уроков для указанной позиции
 */
class DailyScheduleAdapter(
    private val itemCount: Int,
    private val lessonProvider: (position: Int) -> List<Lesson>
) : RecyclerView.Adapter<DailyScheduleAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val recyclerView: RecyclerView = view.findViewById(R.id.rv_day_lessons)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_page, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val dayLessons = lessonProvider(position)
        holder.recyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.recyclerView.adapter = LessonAdapter(dayLessons)
    }

    override fun getItemCount(): Int = itemCount
}
