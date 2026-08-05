package com.example.myapplication

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
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

    fun updateItems(newItems: List<ScheduleItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class LessonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStartTime: TextView = view.findViewById(R.id.tv_start_time)
        val tvEndTime: TextView = view.findViewById(R.id.tv_end_time)
        val vIndicator: View = view.findViewById(R.id.v_indicator)

        // Подгруппа 1
        val tvSubject1: TextView = view.findViewById(R.id.tv_subject_1)
        val tvType1: TextView = view.findViewById(R.id.tv_type_1)
        val tvRoom1: TextView = view.findViewById(R.id.tv_room_1)
        val tvTeacher1: TextView = view.findViewById(R.id.tv_teacher_1)
        val tvSubLabel1: TextView = view.findViewById(R.id.tv_subgroup_label_1)

        // Подгруппа 2
        val blockSub2: View = view.findViewById(R.id.block_subgroup_2)
        val vDivider: View = view.findViewById(R.id.v_subgroup_divider)
        val tvSubject2: TextView = view.findViewById(R.id.tv_subject_2)
        val tvType2: TextView = view.findViewById(R.id.tv_type_2)
        val tvRoom2: TextView = view.findViewById(R.id.tv_room_2)
        val tvTeacher2: TextView = view.findViewById(R.id.tv_teacher_2)
        val tvSubLabel2: TextView = view.findViewById(R.id.tv_subgroup_label_2)
    }

    class GapViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGap: TextView = view.findViewById(R.id.tv_gap)
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Lesson -> TYPE_LESSON
        else -> TYPE_GAP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_LESSON) {
            LessonViewHolder(inflater.inflate(R.layout.item_lesson, parent, false))
        } else {
            GapViewHolder(inflater.inflate(R.layout.item_gap, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is LessonViewHolder && item is Lesson) {
            val context = holder.itemView.context
            holder.tvStartTime.text = item.startTime
            holder.tvEndTime.text = item.endTime

            // Заполнение первой подгруппы (или единственной пары)
            val c1 = item.contents[0]
            holder.tvSubject1.text = c1.subject
            holder.tvType1.text = c1.type.uppercase()
            holder.tvRoom1.text = if (c1.room.isNotEmpty()) "ауд. ${c1.room}" else "—"
            holder.tvTeacher1.text = c1.teacher
            
            val color1 = getColorForType(context, c1.type)
            holder.vIndicator.backgroundTintList = ColorStateList.valueOf(color1)
            holder.tvType1.setTextColor(color1)
            holder.tvSubLabel1.setTextColor(color1)

            // Если пара разделена
            if (item.isSplit) {
                holder.vDivider.visibility = View.VISIBLE
                holder.blockSub2.visibility = View.VISIBLE
                holder.tvSubLabel1.visibility = View.VISIBLE
                
                val c2 = item.contents[1]
                holder.tvSubject2.text = c2.subject
                holder.tvType2.text = c2.type.uppercase()
                holder.tvRoom2.text = if (c2.room.isNotEmpty()) "ауд. ${c2.room}" else "—"
                holder.tvTeacher2.text = c2.teacher

                val color2 = getColorForType(context, c2.type)
                holder.tvType2.setTextColor(color2)
                holder.tvSubLabel2.setTextColor(color2)
            } else {
                holder.vDivider.visibility = View.GONE
                holder.blockSub2.visibility = View.GONE
                holder.tvSubLabel1.visibility = View.GONE
            }

        } else if (holder is GapViewHolder && item is Gap) {
            holder.tvGap.text = item.durationText
        }
    }

    private fun getColorForType(context: Context, type: String): Int {
        val colorRes = when (type.lowercase()) {
            "лекция" -> R.color.type_lecture
            "семинар", "практика" -> R.color.type_seminar
            "лабораторная работа", "лабораторная", "лаб" -> R.color.type_lab
            else -> R.color.type_other
        }
        return ContextCompat.getColor(context, colorRes)
    }

    override fun getItemCount(): Int = items.size
}
