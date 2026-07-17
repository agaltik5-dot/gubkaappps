package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Адаптер для списка занятий. 
 * Он отвечает за превращение данных из списка Lesson в визуальные карточки.
 */
class LessonAdapter(private val lessons: List<Lesson>) : 
    RecyclerView.Adapter<LessonAdapter.LessonViewHolder>() {

    /**
     * ViewHolder хранит ссылки на все View внутри одной карточки, 
     * чтобы не искать их через findViewById каждый раз (это ускоряет работу).
     */
    class LessonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStartTime: TextView = view.findViewById(R.id.tv_start_time)
        val tvEndTime: TextView = view.findViewById(R.id.tv_end_time)
        val tvDuration: TextView = view.findViewById(R.id.tv_duration)
        val tvSubject: TextView = view.findViewById(R.id.tv_subject)
        val tvType: TextView = view.findViewById(R.id.tv_type)
        val tvDetails: TextView = view.findViewById(R.id.tv_details)
    }

    // Создает новую карточку (вызывается системой, когда нужно отобразить новый элемент)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lesson, parent, false)
        return LessonViewHolder(view)
    }

    // Заполняет карточку данными из конкретного объекта Lesson
    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        val lesson = lessons[position]
        holder.tvStartTime.text = lesson.startTime
        holder.tvEndTime.text = lesson.endTime
        holder.tvDuration.text = lesson.duration
        holder.tvSubject.text = lesson.subject
        holder.tvType.text = lesson.type
        holder.tvDetails.text = lesson.details
    }

    // Возвращает общее количество элементов в списке
    override fun getItemCount(): Int = lessons.size
}
