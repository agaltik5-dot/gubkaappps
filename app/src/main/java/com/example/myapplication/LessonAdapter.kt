package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Адаптер для списка элементов расписания (пары и перерывы).
 */
class LessonAdapter(private var items: List<ScheduleItem>) : 
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_LESSON = 0
        private const val TYPE_GAP = 1
    }

    /**
     * Позволяет обновить список элементов без пересоздания адаптера.
     */
    fun updateItems(newItems: List<ScheduleItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    /**
     * ViewHolder для карточки занятия.
     */
    class LessonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStartTime: TextView = view.findViewById(R.id.tv_start_time)
        val tvEndTime: TextView = view.findViewById(R.id.tv_end_time)
        val tvSubject: TextView = view.findViewById(R.id.tv_subject)
        val tvType: TextView = view.findViewById(R.id.tv_type)
        val tvDetails: TextView = view.findViewById(R.id.tv_details)
        val vIndicator: View = view.findViewById(R.id.v_indicator)
    }

    /**
     * ViewHolder для разметки перерыва.
     */
    class GapViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGap: TextView = view.findViewById(R.id.tv_gap)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Lesson -> TYPE_LESSON
            is Gap -> TYPE_GAP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_LESSON -> {
                val view = inflater.inflate(R.layout.item_lesson, parent, false)
                LessonViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_gap, parent, false)
                GapViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is LessonViewHolder && item is Lesson) {
            val context = holder.itemView.context
            holder.tvStartTime.text = item.startTime
            holder.tvEndTime.text = item.endTime
            holder.tvSubject.text = item.subject
            holder.tvType.text = item.type
            holder.tvDetails.text = item.details

            // Цветовая кодировка в зависимости от типа
            val typeColor = getColorForType(context, item.type)
            holder.vIndicator.setBackgroundColor(typeColor)
            holder.tvType.setTextColor(typeColor)

        } else if (holder is GapViewHolder && item is Gap) {
            holder.tvGap.text = item.durationText
        }
    }

    private fun getColorForType(context: android.content.Context, type: String): Int {
        val colorRes = when (type.lowercase()) {
            "лекция" -> R.color.type_lecture
            "семинар", "практика" -> R.color.type_seminar
            "лабораторная", "лаб" -> R.color.type_lab
            else -> R.color.type_other
        }
        return androidx.core.content.ContextCompat.getColor(context, colorRes)
    }

    override fun getItemCount(): Int = items.size
}
