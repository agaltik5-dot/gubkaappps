package com.example.myapplication.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.data.ScheduleRepository
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputLayout

class SearchFragment : Fragment(R.layout.fragment_search) {

    private lateinit var scheduleRepository: ScheduleRepository
    private var currentFacultyId: Int = 0
    private var currentGroups: List<Pair<String, Int>> = emptyList()
    private var selectedGroupId: Int = -1
    private var selectedGroupCode: String = ""
    private var currentTabIndex: Int = 0

    private lateinit var actvSemester: AutoCompleteTextView
    private lateinit var actvFaculty: AutoCompleteTextView
    private lateinit var actvGroup: AutoCompleteTextView
    private lateinit var tilSearchQuery: TextInputLayout
    private lateinit var etSearchQuery: EditText
    private lateinit var tvQueryLabel: TextView
    private lateinit var layoutQuerySearch: LinearLayout
    private lateinit var innerSearchLayout: ViewGroup
    private lateinit var searchContainer: ViewGroup
    private lateinit var tabIndicator: View
    private lateinit var tabFaculties: TextView
    private lateinit var tabTeachers: TextView
    private lateinit var tabRooms: TextView
    private lateinit var containerGroup: View
    private lateinit var btnSelectGroup: Button

    private lateinit var rvSemester: RecyclerView
    private lateinit var rvFaculty: RecyclerView
    private lateinit var rvGroup: RecyclerView
    
    private lateinit var wrapperSemester: View
    private lateinit var wrapperFaculty: View
    private lateinit var wrapperGroup: View
    
    private lateinit var indicatorSemester: View
    private lateinit var indicatorFaculty: View
    private lateinit var indicatorGroup: View

    private lateinit var semesterInlineAdapter: SearchDropdownAdapter
    private lateinit var facultyInlineAdapter: SearchDropdownAdapter
    private lateinit var groupInlineAdapter: SearchDropdownAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleRepository = ScheduleRepository(requireContext())

        actvSemester = view.findViewById(R.id.et_semester)
        actvFaculty = view.findViewById(R.id.et_faculty)
        actvGroup = view.findViewById(R.id.et_group)
        
        tilSearchQuery = view.findViewById(R.id.til_search_query)
        etSearchQuery = view.findViewById(R.id.et_search_query)
        tvQueryLabel = view.findViewById(R.id.tv_query_label)
        layoutQuerySearch = view.findViewById(R.id.layout_query_search)
        innerSearchLayout = view.findViewById(R.id.inner_search_layout)
        searchContainer = view.findViewById(R.id.search_container)
        tabIndicator = view.findViewById(R.id.tab_indicator)
        tabFaculties = view.findViewById(R.id.tab_faculties)
        tabTeachers = view.findViewById(R.id.tab_teachers)
        tabRooms = view.findViewById(R.id.tab_rooms)
        
        containerGroup = view.findViewById(R.id.container_group)
        btnSelectGroup = view.findViewById(R.id.btn_select_group)
        btnSelectGroup.setOnClickListener {
            if (selectedGroupId != -1) {
                saveSelectedGroup(selectedGroupId, selectedGroupCode)
                Toast.makeText(requireContext(), "Группа $selectedGroupCode выбрана!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Пожалуйста, выберите группу", Toast.LENGTH_SHORT).show()
            }
        }

        rvSemester = view.findViewById(R.id.rv_semester_inline)
        rvFaculty = view.findViewById(R.id.rv_faculty_inline)
        rvGroup = view.findViewById(R.id.rv_group_inline)
        
        wrapperSemester = view.findViewById(R.id.wrapper_semester_inline)
        wrapperFaculty = view.findViewById(R.id.wrapper_faculty_inline)
        wrapperGroup = view.findViewById(R.id.wrapper_group_inline)
        
        indicatorSemester = view.findViewById(R.id.indicator_semester)
        indicatorFaculty = view.findViewById(R.id.indicator_faculty)
        indicatorGroup = view.findViewById(R.id.indicator_group)

        // 1. Настройка инлайновых списков
        setupInlineDropdowns(view)
        setupScrollIndicators()
        setupCustomTabs()

        applyThemeColor()
    }

    private fun setupCustomTabs() {
        val tabs = listOf(tabFaculties, tabTeachers, tabRooms)

        // Устанавливаем фиксированную ширину индикатора в зависимости от ширины контейнера
        tabIndicator.post {
            val container = view?.findViewById<View>(R.id.tab_container) ?: return@post
            val tabWidth = container.width / 3
            val indicatorWidth = tabWidth / 2 
            
            val params = tabIndicator.layoutParams
            params.width = indicatorWidth
            tabIndicator.layoutParams = params
            
            // Начальное позиционирование
            tabIndicator.translationX = (currentTabIndex * tabWidth + (tabWidth - indicatorWidth) / 2).toFloat()
        }

        tabs.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                selectTab(index)
            }
        }
    }

    private fun selectTab(index: Int) {
        currentTabIndex = index
        val container = view?.findViewById<View>(R.id.tab_container) ?: return
        val layoutFacultySelectors = view?.findViewById<LinearLayout>(R.id.layout_faculty_selectors) ?: return
        val tabWidth = container.width / 3
        val indicatorWidth = tabWidth / 2 // Используем тот же расчет, что и в post

        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val activeColor = (prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7").toColorInt()

        // Плавная анимация контента
        TransitionManager.beginDelayedTransition(searchContainer, getSmoothTransition())

        // Двигаем ползунок
        tabIndicator.animate()
            .translationX((index * tabWidth + (tabWidth - indicatorWidth) / 2).toFloat())
            .setDuration(300)
            .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f))
            .start()

        // Обновляем текст и видимость
        val tabs = listOf(tabFaculties, tabTeachers, tabRooms)
        tabs.forEachIndexed { i, tv ->
            tv.setTextColor(if (i == index) activeColor else ContextCompat.getColor(requireContext(), R.color.ui_text_sub))
        }

        when (index) {
            0 -> { // Вкладка "Факультеты"
                layoutFacultySelectors.visibility = View.VISIBLE
                layoutQuerySearch.visibility = View.GONE
            }
            1 -> { // Вкладка "Преподаватели"
                layoutFacultySelectors.visibility = View.GONE
                layoutQuerySearch.visibility = View.VISIBLE
                tvQueryLabel.text = "ПРЕПОДАВАТЕЛЬ"
                etSearchQuery.hint = "Введите ФИО..."
            }
            2 -> { // Вкладка "Аудитории"
                layoutFacultySelectors.visibility = View.GONE
                layoutQuerySearch.visibility = View.VISIBLE
                tvQueryLabel.text = "АУДИТОРИЯ"
                etSearchQuery.hint = "Номер аудитории..."
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyThemeColor()
    }

    private fun setupScrollIndicators() {
        listOf(
            rvSemester to indicatorSemester,
            rvFaculty to indicatorFaculty,
            rvGroup to indicatorGroup
        ).forEach { (rv, indicator) ->
            rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    syncScrollIndicator(recyclerView, indicator)
                }
            })
            rv.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    rv.post { syncScrollIndicator(rv, indicator) }
                }
            })
        }
    }

    private fun syncScrollIndicator(rv: RecyclerView, indicator: View) {
        val offset = rv.computeVerticalScrollOffset()
        val extent = rv.computeVerticalScrollExtent()
        val range = rv.computeVerticalScrollRange()

        if (range > extent) {
            indicator.visibility = View.VISIBLE
            val scrollableHeight = range - extent
            val maxIndicatorTravel = (rv.height - indicator.height).toFloat()
            val travelPercent = offset.toFloat() / scrollableHeight.toFloat()
            indicator.translationY = travelPercent * maxIndicatorTravel
        } else {
            indicator.visibility = View.INVISIBLE
        }
    }

    private fun applyThemeColor() {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val accentColorHex = prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7"
        val activeColor = accentColorHex.toColorInt()
        val colorStateList = ColorStateList.valueOf(activeColor)

        val rootView = view ?: return

        // 1. Индикатор вкладок и текст
        val bgIndicator = tabIndicator.background as? android.graphics.drawable.GradientDrawable
        bgIndicator?.setColor(activeColor)
        
        val tabs = listOf(tabFaculties, tabTeachers, tabRooms)
        tabs.forEachIndexed { i, tv ->
            tv.setTextColor(if (i == currentTabIndex) activeColor else ContextCompat.getColor(requireContext(), R.color.ui_text_sub))
        }

        // 2. TextInputLayouts
        val inputLayouts = listOf<TextInputLayout?>(
            rootView.findViewById(R.id.til_semester),
            rootView.findViewById(R.id.til_faculty),
            rootView.findViewById(R.id.til_group),
            rootView.findViewById(R.id.til_search_query)
        )
        inputLayouts.forEach { layout ->
            layout?.setEndIconTintList(colorStateList)
            layout?.setStartIconTintList(colorStateList)
            layout?.setBoxStrokeColorStateList(colorStateList)
        }

        // 3. Обводки контейнеров и индикаторы прокрутки
        updateAllBoxStrokes(activeColor)
        
        listOf(indicatorSemester, indicatorFaculty, indicatorGroup).forEach { indicator ->
            val bg = indicator.background as? android.graphics.drawable.GradientDrawable
            bg?.setColor(activeColor)
        }

        // 4. Кнопка "Выбрать эту группу"
        val btnSelect = rootView.findViewById<Button>(R.id.btn_select_group)
        btnSelect?.backgroundTintList = colorStateList
    }

    private fun updateAllBoxStrokes(color: Int) {
        val rootView = view ?: return
        val boxes = listOf(
            rootView.findViewById<View>(R.id.box_semester) to actvSemester,
            rootView.findViewById<View>(R.id.box_faculty) to actvFaculty,
            rootView.findViewById<View>(R.id.box_group) to actvGroup
        )
        boxes.forEach { (box, actv) ->
            val background = box?.background as? android.graphics.drawable.GradientDrawable
            background?.setStroke(if (actv.isFocused) 5 else 3, color)
        }
    }

    private fun setupInlineDropdowns(rootView: View) {
        // СЕМЕСТР
        val semesters = listOf("2025/2026 осенний", "2025/2026 весенний")
        actvSemester.setText(semesters[0], false)
        semesterInlineAdapter = SearchDropdownAdapter(semesters) { selected ->
            TransitionManager.beginDelayedTransition(searchContainer, getSmoothTransition())
            actvSemester.setText(selected, false)
            toggleInlineList(wrapperSemester, actvSemester)
        }
        rvSemester.adapter = semesterInlineAdapter
        actvSemester.setOnClickListener { toggleInlineList(wrapperSemester, actvSemester) }
        rootView.findViewById<TextInputLayout>(R.id.til_semester)?.setEndIconOnClickListener {
            toggleInlineList(wrapperSemester, actvSemester)
        }

        // ФАКУЛЬТЕТ
        val facultyNames = scheduleRepository.FACULTIES.values.toList()
        val boxFaculty = rootView.findViewById<View>(R.id.box_faculty)
        facultyInlineAdapter = SearchDropdownAdapter(facultyNames) { selected ->
            TransitionManager.beginDelayedTransition(searchContainer, getSmoothTransition())
            actvFaculty.setText(selected, false)
            toggleInlineList(wrapperFaculty, actvFaculty)
            hideKeyboard()
            
            currentFacultyId = scheduleRepository.FACULTIES.filterValues { it == selected }.keys.firstOrNull() ?: 0
            actvGroup.setText("", false)
            
            containerGroup.visibility = View.VISIBLE
            updateGroupsDropdown()
        }
        rvFaculty.adapter = facultyInlineAdapter
        actvFaculty.setOnClickListener { toggleInlineList(wrapperFaculty, actvFaculty) }
        rootView.findViewById<TextInputLayout>(R.id.til_faculty)?.setEndIconOnClickListener {
            toggleInlineList(wrapperFaculty, actvFaculty)
        }
        
        // Эффект фокуса
        actvFaculty.setOnFocusChangeListener { _, hasFocus ->
            val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val color = (prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7").toColorInt()
            (boxFaculty?.background as? android.graphics.drawable.GradientDrawable)?.setStroke(if (hasFocus) 5 else 3, color)
        }

        actvFaculty.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                val filtered = if (query.isEmpty()) facultyNames else facultyNames.filter { 
                    it.contains(query, ignoreCase = true) 
                }
                facultyInlineAdapter.updateItems(filtered)
                if (wrapperFaculty.visibility == View.GONE && query.isNotEmpty()) {
                    toggleInlineList(wrapperFaculty, actvFaculty)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // ГРУППА
        val boxGroup = rootView.findViewById<View>(R.id.box_group)
        groupInlineAdapter = SearchDropdownAdapter(emptyList()) { selected ->
            TransitionManager.beginDelayedTransition(searchContainer, getSmoothTransition())
            actvGroup.setText(selected, false)
            toggleInlineList(wrapperGroup, actvGroup)
            hideKeyboard()
            
            val group = currentGroups.find { it.first == selected }
            if (group != null) {
                selectedGroupCode = group.first
                selectedGroupId = group.second
                btnSelectGroup.visibility = View.VISIBLE
            }
        }
        rvGroup.adapter = groupInlineAdapter
        actvGroup.setOnClickListener { toggleInlineList(wrapperGroup, actvGroup) }
        rootView.findViewById<TextInputLayout>(R.id.til_group)?.setEndIconOnClickListener {
            toggleInlineList(wrapperGroup, actvGroup)
        }

        // Эффект фокуса
        actvGroup.setOnFocusChangeListener { _, hasFocus ->
            val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val color = (prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7").toColorInt()
            (boxGroup?.background as? android.graphics.drawable.GradientDrawable)?.setStroke(if (hasFocus) 5 else 3, color)
        }

        actvGroup.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                val groupNames = currentGroups.map { it.first }
                val filtered = if (query.isEmpty()) groupNames else groupNames.filter { 
                    it.contains(query, ignoreCase = true) 
                }
                groupInlineAdapter.updateItems(filtered)
                if (wrapperGroup.visibility == View.GONE && query.isNotEmpty()) {
                    toggleInlineList(wrapperGroup, actvGroup)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun getSmoothTransition(): TransitionSet {
        return TransitionSet().apply {
            addTransition(Fade().setDuration(200))
            addTransition(ChangeBounds().setDuration(400))
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            ordering = TransitionSet.ORDERING_TOGETHER
        }
    }

    private fun toggleInlineList(wrapper: View, actv: AutoCompleteTextView) {
        val isExpanding = wrapper.visibility == View.GONE
        
        TransitionManager.beginDelayedTransition(searchContainer, getSmoothTransition())

        if (isExpanding) {
            listOf(wrapperSemester, wrapperFaculty, wrapperGroup).forEach { 
                if (it != wrapper && it.visibility == View.VISIBLE) {
                    it.visibility = View.GONE
                    val otherTil = it.parent as? TextInputLayout ?: (it.parent.parent as? TextInputLayout)
                    resetArrow(otherTil)
                }
            }
        }
        
        wrapper.visibility = if (isExpanding) View.VISIBLE else View.GONE
        
        if (isExpanding) {
            when(wrapper.id) {
                R.id.wrapper_semester_inline -> rvSemester.post { syncScrollIndicator(rvSemester, indicatorSemester) }
                R.id.wrapper_faculty_inline -> rvFaculty.post { syncScrollIndicator(rvFaculty, indicatorFaculty) }
                R.id.wrapper_group_inline -> rvGroup.post { syncScrollIndicator(rvGroup, indicatorGroup) }
            }
        }
        
        val til = actv.parent.parent as? TextInputLayout
        val arrow = til?.findViewById<View>(com.google.android.material.R.id.text_input_end_icon)
        arrow?.animate()?.rotation(if (isExpanding) 180f else 0f)?.setDuration(400)?.start()
    }

    private fun resetArrow(til: TextInputLayout?) {
        val arrow = til?.findViewById<View>(com.google.android.material.R.id.text_input_end_icon)
        arrow?.animate()?.rotation(0f)?.setDuration(400)?.start()
    }

    private fun updateGroupsDropdown() {
        currentGroups = scheduleRepository.getGroupsForFaculty(currentFacultyId)
        val groupNames = currentGroups.map { it.first }
        groupInlineAdapter.updateItems(groupNames)
        
        selectedGroupId = -1
        selectedGroupCode = ""
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun saveSelectedGroup(id: Int, code: String) {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("key_group_id", id)
            putString("key_group_code", code)
            apply()
        }
    }
}
