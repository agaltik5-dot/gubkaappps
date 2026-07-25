package com.example.myapplication.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.Lesson
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private var viewPager: androidx.viewpager2.widget.ViewPager2? = null
    private var selectedDayView: View? = null
    private var dayViews: List<LinearLayout?> = emptyList()

    // Константы для бесконечного свайпа
    private val MAX_DAYS = 2000
    private val START_INDEX = MAX_DAYS / 2

    private val today = Calendar.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDaySelector(view)
        setupMonthNavigation(view)
        setupHeaderCalendar(view)
        setupViewPager(view)
    }

    private fun setupHeaderCalendar(view: View) {
        val btnOpenCalendar = view.findViewById<View>(R.id.btn_open_calendar)
        btnOpenCalendar.setOnClickListener {
            val currentPos = viewPager?.currentItem ?: START_INDEX
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Выберите дату")
                .setSelection(getDateForPosition(currentPos).timeInMillis)
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                val selectedCal = Calendar.getInstance()
                selectedCal.timeInMillis = selection
                
                // Вычисляем разницу в днях от начала отсчета (сегодня в START_INDEX)
                val diffDays = ((selectedCal.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
                
                viewPager?.setCurrentItem(START_INDEX + diffDays, true)
            }
            
            datePicker.show(parentFragmentManager, "DATE_PICKER")
        }
    }

    private fun setupMonthNavigation(view: View) {
        val btnPrev = view.findViewById<View>(R.id.btn_prev_week)
        val btnNext = view.findViewById<View>(R.id.btn_next_week)

        btnPrev.setOnClickListener {
            val currentPos = viewPager?.currentItem ?: START_INDEX
            viewPager?.setCurrentItem(currentPos - 7, true)
        }

        btnNext.setOnClickListener {
            val currentPos = viewPager?.currentItem ?: START_INDEX
            viewPager?.setCurrentItem(currentPos + 7, true)
        }
    }

    private fun setupViewPager(view: View) {
        viewPager = view.findViewById(R.id.vp_schedule)

        val adapter = DailyScheduleAdapter(MAX_DAYS) { position ->
            val date = getDateForPosition(position)
            val dayName = date.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.forLanguageTag("ru"))
            createSampleLessons(dayName ?: "")
        }
        
        viewPager?.adapter = adapter

        viewPager?.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUIForPosition(position)
            }
        })

        // Переходим к сегодняшнему дню
        viewPager?.setCurrentItem(START_INDEX, false)
    }

    private fun getDateForPosition(position: Int): Calendar {
        val cal = today.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, position - START_INDEX)
        return cal
    }

    private fun updateUIForPosition(position: Int) {
        val selectedDate = getDateForPosition(position)
        
        // 1. Обновляем заголовок (Месяц . Год)
        val tvMonthYear = view?.findViewById<TextView>(R.id.tv_month_year)
        val monthName = selectedDate.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.forLanguageTag("ru"))
            ?.replaceFirstChar { it.uppercase() }
        tvMonthYear?.text = "$monthName  •  ${selectedDate.get(Calendar.YEAR)}"

        // 2. Обновляем инфо о дате под заголовком
        val tvDateInfo = view?.findViewById<TextView>(R.id.tv_current_date_info)
        val fullDateFormatter = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru"))
        tvDateInfo?.text = fullDateFormatter.format(selectedDate.time).replaceFirstChar { it.uppercase() }

        // 3. Обновляем числа в кнопках (ПН-ВС) для текущей недели
        val startOfWeek = selectedDate.clone() as Calendar
        startOfWeek.firstDayOfWeek = Calendar.MONDAY
        val dayOfWeek = startOfWeek.get(Calendar.DAY_OF_WEEK)
        val diff = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
        startOfWeek.add(Calendar.DAY_OF_YEAR, diff)

        dayViews.forEach { dayView ->
            if (dayView != null) {
                val tvNumber = dayView.getChildAt(1) as? TextView
                tvNumber?.text = startOfWeek.get(Calendar.DAY_OF_MONTH).toString()
                
                val isSelected = isSameDay(startOfWeek, selectedDate)
                val isToday = isSameDay(startOfWeek, today)
                
                updateDayViewStyle(dayView, isSelected, isToday)
                
                startOfWeek.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    private fun updateDayViewStyle(view: View, isSelected: Boolean, isToday: Boolean) {
        val context = requireContext()
        val dayText = (view as LinearLayout).getChildAt(0) as TextView
        val numText = view.getChildAt(1) as TextView

        when {
            isSelected -> {
                view.setBackgroundResource(R.drawable.bg_day_active)
                dayText.setTextColor(Color.WHITE)
                numText.setTextColor(Color.WHITE)
                view.elevation = 8f
                selectedDayView = view
            }
            isToday -> {
                view.setBackgroundResource(R.drawable.bg_day_today)
                dayText.setTextColor(ContextCompat.getColor(context, R.color.ui_primary))
                numText.setTextColor(ContextCompat.getColor(context, R.color.ui_text_main))
                view.elevation = 0f
            }
            else -> {
                view.setBackgroundResource(R.drawable.bg_day_inactive)
                dayText.setTextColor(ContextCompat.getColor(context, R.color.ui_text_sub))
                numText.setTextColor(ContextCompat.getColor(context, R.color.ui_text_main))
                view.elevation = 0f
            }
        }
    }

    private fun setupDaySelector(view: View) {
        dayViews = listOf(
            view.findViewById(R.id.day_mon),
            view.findViewById(R.id.day_tue),
            view.findViewById(R.id.day_wed),
            view.findViewById(R.id.day_thu),
            view.findViewById(R.id.day_fri),
            view.findViewById(R.id.day_sat),
            view.findViewById(R.id.day_sun)
        )

        dayViews.forEachIndexed { index, dayView ->
            dayView?.setOnClickListener {
                val currentPos = viewPager?.currentItem ?: START_INDEX
                val currentDate = getDateForPosition(currentPos)
                val targetDayOfWeek = if (index == 6) Calendar.SUNDAY else index + 2
                
                val currentDayOfWeek = currentDate.get(Calendar.DAY_OF_WEEK)
                val diff = targetDayOfWeek - currentDayOfWeek
                
                viewPager?.setCurrentItem(currentPos + diff, true)
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun createSampleLessons(dayName: String): List<Lesson> {
        return listOf(
            Lesson("Предмет в $dayName", "08:00", "09:30", "Лекция", "А-201", "1.5ч"),
            Lesson("Еще пара ($dayName)", "10:00", "11:30", "Практика", "Б-305", "1.5ч")
        )
    }
}
