package com.example.myapplication.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.ScheduleItem
import com.example.myapplication.LessonAdapter
import com.example.myapplication.R

/**
 * Адаптер для ViewPager2, реализующий "бесконечный" скролл.
 */
class DailyScheduleAdapter(
    private val itemCount: Int,
    private val lessonProvider: (position: Int) -> List<ScheduleItem>
) : RecyclerView.Adapter<DailyScheduleAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val recyclerView: RecyclerView = view.findViewById(R.id.rv_day_lessons)
        val emptyState: View = view.findViewById(R.id.layout_empty_state)
        
        var lessonAdapter: LessonAdapter? = null

        init {
            recyclerView.layoutManager = LinearLayoutManager(view.context)
            recyclerView.setHasFixedSize(true)
            recyclerView.isNestedScrollingEnabled = true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_page, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val dayItems = lessonProvider(position)
        
        if (dayItems.isEmpty()) {
            holder.recyclerView.visibility = View.GONE
            holder.emptyState.visibility = View.VISIBLE
        } else {
            holder.recyclerView.visibility = View.VISIBLE
            holder.emptyState.visibility = View.GONE
            
            // Оптимизация: обновляем адаптер вместо создания нового
            // Оптимизация: обновляем адаптер вместо создания нового
            if (holder.lessonAdapter == null) {
                holder.lessonAdapter = LessonAdapter(dayItems)
                holder.recyclerView.adapter = holder.lessonAdapter
            } else {
                holder.lessonAdapter?.updateItems(dayItems)
            }
        }
    }

    override fun getItemCount(): Int = itemCount
}
