package com.example.myapplication.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import java.util.Calendar

class CalendarWeekAdapter(
    private val totalWeeks: Int,
    private val startDate: Calendar,
    private val onDayClick: (Calendar) -> Unit
) : RecyclerView.Adapter<CalendarWeekAdapter.WeekViewHolder>() {

    private var selectedDate: Calendar? = null
    private var recyclerView: RecyclerView? = null

    // Акцентный цвет темы
    private var accentColor: Int = Color.BLUE

    fun updateAccentColor(color: Int) {
        if (this.accentColor != color) {
            this.accentColor = color
            recyclerView?.post { notifyDataSetChanged() }
        }
    }

    /**
     * Обновляет выбранную дату.
     */
    fun updateSelectedDate(newDate: Calendar) {
        if (selectedDate == null || !isSameDay(selectedDate!!, newDate)) {
            selectedDate = newDate
            // Вызываем обновление через post для безопасности
            recyclerView?.post { notifyDataSetChanged() }
        }
    }

    private val today = Calendar.getInstance()

    class WeekViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayContainers = listOf(
            view.findViewById<LinearLayout>(R.id.day_0),
            view.findViewById<LinearLayout>(R.id.day_1),
            view.findViewById<LinearLayout>(R.id.day_2),
            view.findViewById<LinearLayout>(R.id.day_3),
            view.findViewById<LinearLayout>(R.id.day_4),
            view.findViewById<LinearLayout>(R.id.day_5),
            view.findViewById<LinearLayout>(R.id.day_6)
        )
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_week_header, parent, false)
        return WeekViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
        val weekStart = startDate.clone() as Calendar
        weekStart.add(Calendar.DAY_OF_YEAR, position * 7)
        
        holder.dayContainers.forEachIndexed { index, container ->
            if (container == null) return@forEachIndexed
            
            val currentDay = weekStart.clone() as Calendar
            currentDay.add(Calendar.DAY_OF_YEAR, index)
            
            val tvNumber = container.getChildAt(1) as TextView
            val tvLabel = container.getChildAt(0) as TextView
            
            tvNumber.text = currentDay.get(Calendar.DAY_OF_MONTH).toString()
            
            val isSelected = selectedDate?.let { isSameDay(currentDay, it) } ?: false
            val isToday = isSameDay(currentDay, today)
            
            updateStyle(container, tvLabel, tvNumber, isSelected, isToday)
            
            container.setOnClickListener {
                if (!isSelected) {
                    selectedDate = currentDay
                    notifyDataSetChanged()
                    onDayClick(currentDay)
                }
            }
        }
    }

    private fun updateStyle(view: View, label: TextView, number: TextView, isSelected: Boolean, isToday: Boolean) {
        val context = view.context
        when {
            isSelected -> {
                view.setBackgroundResource(R.drawable.bg_day_active)
                view.background?.setTint(accentColor)
                label.setTextColor(Color.WHITE)
                number.setTextColor(Color.WHITE)
                view.elevation = 8f
            }
            isToday -> {
                view.setBackgroundResource(R.drawable.bg_day_today)
                label.setTextColor(accentColor)
                number.setTextColor(ContextCompat.getColor(context, R.color.ui_text_main))
                view.elevation = 0f
            }
            else -> {
                view.setBackgroundResource(R.drawable.bg_day_inactive)
                label.setTextColor(ContextCompat.getColor(context, R.color.ui_text_sub))
                number.setTextColor(ContextCompat.getColor(context, R.color.ui_text_main))
                view.elevation = 0f
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun getItemCount(): Int = totalWeeks
}
