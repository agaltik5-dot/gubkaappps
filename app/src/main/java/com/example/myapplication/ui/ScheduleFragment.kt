package com.example.myapplication.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.edit
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

    // Флаг для предотвращения зацикливания при программном скролле
    private var isProgrammaticScroll = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        scheduleRepository = ScheduleRepository(requireContext())
        loadSavedGroup()

        initCalendarAnchors()

        setupMonthNavigation(view)
        setupHeaderCalendar(view)
        setupViewPagers(view)
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
        // Кнопки меняют ТОЛЬКО видимую неделю в шапке, не меняя выбранный день внизу
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

        // Предзагрузка страниц для устранения белых пятен
        viewPager?.offscreenPageLimit = 3

        headerAdapter = CalendarWeekAdapter(TOTAL_WEEKS, weekAnchor) { clickedDate ->
            // При нажатии на конкретное число переходим к нему
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

        viewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selectedDate = getDateForPosition(position)
                updateHeaderTexts(selectedDate)
                saveSelectedDate(selectedDate)
                
                // Всегда обновляем подсветку в адаптере
                headerAdapter?.updateSelectedDate(selectedDate)

                // Если мы свайпаем пальцем нижний список, нужно подтянуть хедер к этой неделе
                if (!isProgrammaticScroll) {
                    val diffFromAnchor = getDaysBetween(weekAnchor, selectedDate)
                    val weekIndex = diffFromAnchor / 7
                    if (headerViewPager?.currentItem != weekIndex) {
                        headerViewPager?.setCurrentItem(weekIndex, true)
                    }
                }
            }
        })

        // Восстановление позиции
        val savedDate = getSavedDate()
        val initialDiff = if (savedDate != null) getDaysBetween(getTodayNoon(), savedDate) else 0
        val initialPos = START_INDEX + initialDiff
        
        isProgrammaticScroll = true
        viewPager?.setCurrentItem(initialPos, false)
        
        val initialWeek = getDaysBetween(weekAnchor, getDateForPosition(initialPos)) / 7
        headerViewPager?.setCurrentItem(initialWeek, false)
        headerAdapter?.updateSelectedDate(getDateForPosition(initialPos))
        isProgrammaticScroll = false
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

    private fun updateHeaderTexts(selectedDate: Calendar) {
        val tvMonthYear = view?.findViewById<TextView>(R.id.tv_month_year)
        val monthName = selectedDate.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.forLanguageTag("ru"))
            ?.replaceFirstChar { it.uppercase() }
        tvMonthYear?.text = "$monthName  •  ${selectedDate.get(Calendar.YEAR)}"

        val tvDateInfo = view?.findViewById<TextView>(R.id.tv_current_date_info)
        val fullDateFormatter = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru"))
        tvDateInfo?.text = fullDateFormatter.format(selectedDate.time).replaceFirstChar { it.uppercase() }
    }

    private fun getDayIndexForCalendar(cal: Calendar): Int {
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SUNDAY) 6 else day - 2
    }
}
