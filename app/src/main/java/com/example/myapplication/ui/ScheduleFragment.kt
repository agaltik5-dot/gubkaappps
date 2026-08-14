package com.example.myapplication.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication.R
import com.example.myapplication.ScheduleItem
import com.example.myapplication.data.RecentSelection
import com.example.myapplication.data.ScheduleRepository
import com.example.myapplication.data.SelectionType
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private var viewPager: ViewPager2? = null
    private var headerViewPager: ViewPager2? = null
    private var headerAdapter: CalendarWeekAdapter? = null

    private lateinit var cardHeader: View
    private lateinit var layoutRecentHistory: View
    private lateinit var viewOverlayDim: View
    private lateinit var containerRecentItems: LinearLayout
    private var isExpanded = false

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

        setupHeaderCard(view)
        setupMonthNavigation(view)
        setupViewPagers(view)
        
        val boundaryBlur = view.findViewById<View>(R.id.view_boundary_blur)
        applyBlurEffect(boundaryBlur, 100f)
        
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
        
        // При первой загрузке тоже сохраняем в недавние
        saveToRecentFixed(currentGroupId, currentGroupCode, SelectionType.GROUP)
    }

    private fun setupHeaderCard(view: View) {
        cardHeader = view.findViewById(R.id.card_header)
        layoutRecentHistory = view.findViewById(R.id.layout_recent_history)
        viewOverlayDim = view.findViewById(R.id.view_overlay_dim)
        containerRecentItems = view.findViewById(R.id.container_recent_items)

        val layoutHeaderMain = view.findViewById<View>(R.id.layout_header_main)
        layoutHeaderMain.setOnClickListener {
            toggleHistoryExpansion()
        }
        
        viewOverlayDim.setOnClickListener {
            if (isExpanded) toggleHistoryExpansion()
        }

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

    private fun toggleHistoryExpansion() {
        isExpanded = !isExpanded
        
        // Анимируем расширение самой карточки
        TransitionManager.beginDelayedTransition(cardHeader as ViewGroup)
        
        layoutRecentHistory.visibility = if (isExpanded) View.VISIBLE else View.GONE
        viewOverlayDim.visibility = if (isExpanded) View.VISIBLE else View.GONE
        
        if (isExpanded) {
            updateRecentListUI()
        }
    }

    private fun updateRecentListUI() {
        containerRecentItems.removeAllViews()
        val recents = getRecentSelections()
        
        if (recents.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = "История пуста"
                setPadding(48, 16, 16, 16)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.ui_text_sub))
                textSize = 14f
            }
            containerRecentItems.addView(emptyTv)
            return
        }

        recents.forEach { selection ->
            val itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_recent_selection, containerRecentItems, false)
            
            itemView.findViewById<TextView>(R.id.tv_recent_name).text = selection.name
            itemView.findViewById<ImageView>(R.id.iv_recent_type).setImageResource(
                if (selection.type == SelectionType.GROUP) R.drawable.ic_group else R.drawable.ic_lesson_teacher
            )

            // Подсвечиваем текущую группу
            if (selection.id == currentGroupId) {
                val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                val activeColor = (prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7").toColorInt()
                itemView.findViewById<TextView>(R.id.tv_recent_name).setTextColor(activeColor)
            }

            itemView.setOnClickListener {
                if (selection.id != currentGroupId) {
                    switchToGroup(selection.id, selection.name)
                }
                toggleHistoryExpansion()
            }
            
            containerRecentItems.addView(itemView)
        }
    }

    private fun switchToGroup(id: Int, code: String) {
        currentGroupId = id
        currentGroupCode = code
        
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putInt("key_group_id", id)
            putString("key_group_code", code)
        }
        
        view?.findViewById<TextView>(R.id.tv_schedule_title)?.text = "Расписание $code"
        
        // Очищаем кэш и обновляем адаптер
        scheduleCache.clear()
        viewPager?.adapter?.notifyDataSetChanged()
        
        saveToRecentFixed(id, code, SelectionType.GROUP)
    }

    private fun getRecentSelections(): List<RecentSelection> {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("key_recent_list_json", "[]") ?: "[]"
        val array = org.json.JSONArray(json)
        val list = mutableListOf<RecentSelection>()
        for (i in 0 until array.length()) {
            list.add(RecentSelection.fromJson(array.getString(i)))
        }
        return list
    }

    private fun saveToRecentFixed(id: Int, name: String, type: SelectionType) {
        val list = getRecentSelections().toMutableList()
        list.removeAll { it.id == id }
        list.add(0, RecentSelection(id, name, type))
        val limited = list.take(5)
        
        val array = org.json.JSONArray()
        limited.forEach { array.put(it.toJson()) }
        
        requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit {
            putString("key_recent_list_json", array.toString())
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

    private fun getDayIndexForCalendar(cal: Calendar): Int {
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SUNDAY) 6 else day - 2
    }

    private fun applyBlurEffect(view: View, radius: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            view.setRenderEffect(blurEffect)
        }
    }
}
