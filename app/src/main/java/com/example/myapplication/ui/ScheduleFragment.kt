package com.example.myapplication.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication.R
import com.example.myapplication.ScheduleItem
import com.example.myapplication.data.ScheduleRepository
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private var viewPager: ViewPager2? = null
    private var headerViewPager: ViewPager2? = null
    private var headerAdapter: CalendarWeekAdapter? = null

    private lateinit var scheduleRepository: ScheduleRepository
    private var currentGroupId = -1
    private var currentGroupCode = ""

    private val MAX_DAYS = 2100 
    private val START_INDEX = MAX_DAYS / 2
    private val TOTAL_WEEKS = MAX_DAYS / 7

    private lateinit var firstDayOfCalendar: Calendar
    private lateinit var weekAnchor: Calendar

    // Кэш для данных расписания
    private val scheduleCache = mutableMapOf<Int, List<ScheduleItem>>()

    // Флаги для синхронизации
    private var isSyncingFromBottom = false
    private var isSyncingFromTop = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        scheduleRepository = ScheduleRepository(requireContext())
        loadSavedGroup()

        initCalendarAnchors()

        setupMonthNavigation(view)
        setupHeaderCalendar(view)
        setupViewPagers(view)
        
        applyThemeColor()
    }

    override fun onResume() {
        super.onResume()
        applyThemeColor()
    }

    private fun applyThemeColor() {
        val context = requireContext()
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val accentColorHex = prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7"
        val activeColor = accentColorHex.toColorInt()
        val colorStateList = ColorStateList.valueOf(activeColor)

        // Стрелки перелистывания недель
        view?.findViewById<ImageButton>(R.id.btn_prev_week)?.imageTintList = colorStateList
        view?.findViewById<ImageButton>(R.id.btn_next_week)?.imageTintList = colorStateList

        // Индикатор под названием месяца
        view?.findViewById<View>(R.id.v_indicator_month)?.backgroundTintList = colorStateList

        // Передаем цвет в адаптер
        headerAdapter?.updateAccentColor(activeColor)
    }

    private fun initCalendarAnchors() {
        val today = getTodayNoon()
        firstDayOfCalendar = today.clone() as Calendar
        firstDayOfCalendar.add(Calendar.DAY_OF_YEAR, -START_INDEX)
        
        weekAnchor = firstDayOfCalendar.clone() as Calendar
        val dayShift = getDayIndexForCalendar(weekAnchor)
        weekAnchor.add(Calendar.DAY_OF_YEAR, -dayShift)
    }

    private fun loadSavedGroup() {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        currentGroupId = prefs.getInt("key_group_id", 10045)
        currentGroupCode = prefs.getString("key_group_code", "ХТМ-25-04") ?: "ХТМ-25-04"
    }

    private fun saveSelectedDate(date: Calendar) {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit { 
            putLong("key_selected_date_millis", date.timeInMillis)
        }
    }

    private fun getSavedDate(): Calendar? {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val millis = prefs.getLong("key_selected_date_millis", -1)
        return if (millis != -1L) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = millis
            cal
        } else null
    }

    private fun setupHeaderCalendar(view: View) {
        val btnOpenCalendar = view.findViewById<View>(R.id.btn_open_calendar)
        val tvHeaderTitle = view.findViewById<TextView>(R.id.tv_schedule_title)
        tvHeaderTitle?.text = "Расписание $currentGroupCode"
        
        btnOpenCalendar.setOnClickListener {
            val currentPos = viewPager?.currentItem ?: START_INDEX
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Выберите дату")
                .setSelection(getDateForPosition(currentPos).timeInMillis)
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                val selectedCal = Calendar.getInstance()
                selectedCal.timeInMillis = selection
                jumpToDate(selectedCal, smooth = false)
            }
            datePicker.show(parentFragmentManager, "DATE_PICKER")
        }
    }

    private fun jumpToDate(targetDate: Calendar, smooth: Boolean = true) {
        val diffDays = getDaysBetween(getTodayNoon(), targetDate)
        viewPager?.setCurrentItem(START_INDEX + diffDays, smooth)
    }

    private fun setupMonthNavigation(view: View) {
        view.findViewById<View>(R.id.btn_prev_week).setOnClickListener {
            val currentWeek = headerViewPager?.currentItem ?: 0
            headerViewPager?.setCurrentItem(currentWeek - 1, true)
        }
        view.findViewById<View>(R.id.btn_next_week).setOnClickListener {
            val currentWeek = headerViewPager?.currentItem ?: 0
            headerViewPager?.setCurrentItem(currentWeek + 1, true)
        }
    }

    private fun setupViewPagers(view: View) {
        viewPager = view.findViewById(R.id.vp_schedule)
        headerViewPager = view.findViewById(R.id.vp_calendar_header)

        viewPager?.offscreenPageLimit = 1

        headerAdapter = CalendarWeekAdapter(TOTAL_WEEKS, weekAnchor) { clickedDate ->
            jumpToDate(clickedDate, smooth = true)
        }
        headerViewPager?.adapter = headerAdapter
        headerViewPager?.isUserInputEnabled = false 

        val mainAdapter = DailyScheduleAdapter(MAX_DAYS) { position ->
            scheduleCache.getOrPut(position) {
                val date = getDateForPosition(position)
                scheduleRepository.getScheduleForDate(currentGroupId, date)
            }
        }
        viewPager?.adapter = mainAdapter

        // 1. Слушатель нижнего расписания
        viewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selectedDate = getDateForPosition(position)
                saveSelectedDate(selectedDate)
                
                // Обновляем текст под заголовком (точная дата)
                updateDateInfoText(selectedDate)

                // Подсвечиваем день в календаре
                headerAdapter?.updateSelectedDate(selectedDate)

                // Если это свайп пользователя, двигаем хедер
                if (!isSyncingFromTop) {
                    val diffFromAnchor = getDaysBetween(weekAnchor, selectedDate)
                    val weekIndex = diffFromAnchor / 7
                    if (headerViewPager?.currentItem != weekIndex) {
                        isSyncingFromBottom = true
                        headerViewPager?.setCurrentItem(weekIndex, true)
                        headerViewPager?.post { isSyncingFromBottom = false }
                    }
                }
                
                // Если скролл нижний, он сам знает свой месяц
                if (!isSyncingFromTop) {
                    updateMonthYearText(selectedDate)
                }
            }
        })

        // 2. Слушатель верхнего хедера (для обновления Месяца/Года при кнопках)
        headerViewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (isSyncingFromBottom) return
                
                // Вычисляем примерную дату для отображения месяца в хедере
                val weekDate = (weekAnchor.clone() as Calendar).apply {
                    add(Calendar.WEEK_OF_YEAR, position)
                    add(Calendar.DAY_OF_YEAR, 3) // Берем середину недели (четверг)
                }
                updateMonthYearText(weekDate)
            }
        })

        // Восстановление позиции
        val savedDate = getSavedDate() ?: getTodayNoon()
        val initialDiff = getDaysBetween(getTodayNoon(), savedDate)
        val initialPos = START_INDEX + initialDiff
        
        isSyncingFromTop = true
        viewPager?.setCurrentItem(initialPos, false)
        
        val initialWeek = getDaysBetween(weekAnchor, getDateForPosition(initialPos)) / 7
        headerViewPager?.setCurrentItem(initialWeek, false)
        headerAdapter?.updateSelectedDate(getDateForPosition(initialPos))
        isSyncingFromTop = false
    }

    private fun getDateForPosition(position: Int): Calendar {
        val cal = firstDayOfCalendar.clone() as Calendar
        cal.add(Calendar.DAY_OF_YEAR, position)
        return cal
    }

    private fun getTodayNoon(): Calendar {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun getDaysBetween(start: Calendar, end: Calendar): Int {
        val s = start.clone() as Calendar
        s.set(Calendar.HOUR_OF_DAY, 12)
        s.set(Calendar.MINUTE, 0)
        s.set(Calendar.SECOND, 0)
        s.set(Calendar.MILLISECOND, 0)

        val e = end.clone() as Calendar
        e.set(Calendar.HOUR_OF_DAY, 12)
        e.set(Calendar.MINUTE, 0)
        e.set(Calendar.SECOND, 0)
        e.set(Calendar.MILLISECOND, 0)

        val diffMillis = e.timeInMillis - s.timeInMillis
        return Math.round(diffMillis.toDouble() / (24 * 60 * 60 * 1000)).toInt()
    }

    private fun updateMonthYearText(date: Calendar) {
        val tvMonthYear = view?.findViewById<TextView>(R.id.tv_month_year)
        val monthName = date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.forLanguageTag("ru"))
            ?.replaceFirstChar { it.uppercase() }
        tvMonthYear?.text = "$monthName  •  ${date.get(Calendar.YEAR)}"
    }

    private fun updateDateInfoText(date: Calendar) {
        val tvDateInfo = view?.findViewById<TextView>(R.id.tv_current_date_info)
        val fullDateFormatter = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru"))
        tvDateInfo?.text = fullDateFormatter.format(date.time).replaceFirstChar { it.uppercase() }
    }

    private fun getDayIndexForCalendar(cal: Calendar): Int {
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SUNDAY) 6 else day - 2
    }
}
