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
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.ScheduleRepository
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class EditStudentDataFragment : Fragment(R.layout.fragment_edit_student_data) {

    private lateinit var scheduleRepository: ScheduleRepository
    private var currentFacultyId: Int = 0
    private var currentGroups: List<Pair<String, Int>> = emptyList()

    private var selectedFacultyName: String = ""
    private var selectedGroupCode: String = ""
    private var selectedGroupId: Int = -1

    private lateinit var editContainer: ViewGroup

    private lateinit var actvFaculty: AutoCompleteTextView
    private lateinit var actvGroup: AutoCompleteTextView

    private lateinit var rvFaculty: RecyclerView
    private lateinit var rvGroup: RecyclerView

    private lateinit var wrapperFaculty: View
    private lateinit var wrapperGroup: View

    private lateinit var indicatorFaculty: View
    private lateinit var indicatorGroup: View

    private lateinit var facultyInlineAdapter: SearchDropdownAdapter
    private lateinit var groupInlineAdapter: SearchDropdownAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleRepository = ScheduleRepository(requireContext())
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        editContainer = view.findViewById(R.id.edit_container)

        val etFirstName = view.findViewById<EditText>(R.id.et_first_name)
        val etLastName = view.findViewById<EditText>(R.id.et_last_name)
        val etBirthDate = view.findViewById<EditText>(R.id.et_birth_date)

        actvFaculty = view.findViewById(R.id.et_faculty)
        actvGroup = view.findViewById(R.id.et_group)

        rvFaculty = view.findViewById(R.id.rv_faculty_inline)
        rvGroup = view.findViewById(R.id.rv_group_inline)

        wrapperFaculty = view.findViewById(R.id.wrapper_faculty_inline)
        wrapperGroup = view.findViewById(R.id.wrapper_group_inline)

        indicatorFaculty = view.findViewById(R.id.indicator_faculty)
        indicatorGroup = view.findViewById(R.id.indicator_group)

        // Подгружаем исходные данные
        etFirstName.setText(prefs.getString("user_first_name", "Иван"))
        etLastName.setText(prefs.getString("user_last_name", "Иванов"))
        etBirthDate.setText(prefs.getString("user_birth_date", "27.03.2005"))

        // Выбор даты рождения через MaterialDatePicker
        etBirthDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Выберите дату рождения")
                .build()

            datePicker.addOnPositiveButtonClickListener { selectionMillis ->
                val birthCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = selectionMillis
                }

                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                val formattedDate = dateFormat.format(birthCal.time)

                etBirthDate.setText(formattedDate)
            }

            datePicker.show(parentFragmentManager, "BIRTH_DATE_PICKER")
        }

        // Инициализация кастомных выпадающих списков
        setupInlineDropdowns(view)
        setupScrollIndicators()
        applyThemeColor()

        // Загрузка сохраненных значений
        val facultyNames = scheduleRepository.FACULTIES.values.toList()
        val savedFaculty = prefs.getString("user_faculty", facultyNames.firstOrNull()).orEmpty()

        if (savedFaculty.isNotEmpty() && facultyNames.contains(savedFaculty)) {
            actvFaculty.setText(savedFaculty, false)
            selectedFacultyName = savedFaculty
            currentFacultyId = scheduleRepository.FACULTIES.filterValues { it == savedFaculty }.keys.firstOrNull() ?: 0
            updateGroupsDropdown()
        }

        val savedGroup = prefs.getString("user_group", "").orEmpty()
        if (savedGroup.isNotEmpty()) {
            actvGroup.setText(savedGroup, false)
            val group = currentGroups.find { it.first == savedGroup }
            if (group != null) {
                selectedGroupCode = group.first
                selectedGroupId = group.second
            }
        }

        // Гарантируем закрытое состояние списка при открытии экрана
        wrapperFaculty.visibility = View.GONE
        wrapperGroup.visibility = View.GONE
        resetArrow(view.findViewById(R.id.til_faculty))
        resetArrow(view.findViewById(R.id.til_group))

        // Кнопки навигации и сохранения
        view.findViewById<View>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<View>(R.id.btn_save)?.setOnClickListener {
            prefs.edit {
                putString("user_first_name", etFirstName.text.toString())
                putString("user_last_name", etLastName.text.toString())
                putString("user_faculty", selectedFacultyName)
                putString("user_group", selectedGroupCode)
                putInt("key_group_id", selectedGroupId)
                putString("key_group_code", selectedGroupCode)
                putString("user_birth_date", etBirthDate.text.toString())
            }

            Toast.makeText(requireContext(), "Данные сохранены", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupInlineDropdowns(rootView: View) {
        // ФАКУЛЬТЕТ
        val facultyNames = scheduleRepository.FACULTIES.values.toList()
        facultyInlineAdapter = SearchDropdownAdapter(facultyNames) { selected ->
            TransitionManager.beginDelayedTransition(editContainer, getSmoothTransition())
            actvFaculty.setText(selected, false)
            selectedFacultyName = selected
            toggleInlineList(wrapperFaculty, actvFaculty)
            hideKeyboard()

            currentFacultyId = scheduleRepository.FACULTIES.filterValues { it == selected }.keys.firstOrNull() ?: 0
            actvGroup.setText("", false)
            selectedGroupCode = ""
            selectedGroupId = -1

            updateGroupsDropdown()
        }
        rvFaculty.adapter = facultyInlineAdapter

        val openFacultyList = {
            // Показываем полный список при каждом открытии
            facultyInlineAdapter.updateItems(facultyNames)
            toggleInlineList(wrapperFaculty, actvFaculty)
        }

        actvFaculty.setOnClickListener { openFacultyList() }
        rootView.findViewById<TextInputLayout>(R.id.til_faculty)?.setEndIconOnClickListener {
            openFacultyList()
        }

        actvFaculty.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!actvFaculty.hasFocus()) return

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
        groupInlineAdapter = SearchDropdownAdapter(emptyList()) { selected ->
            TransitionManager.beginDelayedTransition(editContainer, getSmoothTransition())
            actvGroup.setText(selected, false)
            toggleInlineList(wrapperGroup, actvGroup)
            hideKeyboard()

            val group = currentGroups.find { it.first == selected }
            if (group != null) {
                selectedGroupCode = group.first
                selectedGroupId = group.second
            }
        }
        rvGroup.adapter = groupInlineAdapter

        val openGroupList = {
            // Показываем полный список групп при открытии
            groupInlineAdapter.updateItems(currentGroups.map { it.first })
            toggleInlineList(wrapperGroup, actvGroup)
        }

        actvGroup.setOnClickListener { openGroupList() }
        rootView.findViewById<TextInputLayout>(R.id.til_group)?.setEndIconOnClickListener {
            openGroupList()
        }

        actvGroup.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!actvGroup.hasFocus()) return

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

    private fun setupScrollIndicators() {
        listOf(
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

    private fun toggleInlineList(wrapper: View, actv: AutoCompleteTextView) {
        val isExpanding = wrapper.visibility == View.GONE

        TransitionManager.beginDelayedTransition(editContainer, getSmoothTransition())

        if (isExpanding) {
            listOf(wrapperFaculty, wrapperGroup).forEach {
                if (it != wrapper && it.visibility == View.VISIBLE) {
                    it.visibility = View.GONE
                    val otherTil = it.parent as? TextInputLayout ?: (it.parent.parent as? TextInputLayout)
                    resetArrow(otherTil)
                }
            }
        }

        wrapper.visibility = if (isExpanding) View.VISIBLE else View.GONE

        if (isExpanding) {
            when (wrapper.id) {
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
    }

    private fun getSmoothTransition(): TransitionSet {
        return TransitionSet().apply {
            addTransition(Fade().setDuration(200))
            addTransition(ChangeBounds().setDuration(400))
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            ordering = TransitionSet.ORDERING_TOGETHER
        }
    }

    private fun applyThemeColor() {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val accentColorHex = prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7"
        val activeColor = accentColorHex.toColorInt()
        val colorStateList = ColorStateList.valueOf(activeColor)

        val rootView = view ?: return

        val inputLayouts = listOf<TextInputLayout?>(
            rootView.findViewById(R.id.til_faculty),
            rootView.findViewById(R.id.til_group)
        )
        inputLayouts.forEach { layout ->
            layout?.setEndIconTintList(colorStateList)
            layout?.setStartIconTintList(colorStateList)
            layout?.setBoxStrokeColorStateList(colorStateList)
        }

        listOf(indicatorFaculty, indicatorGroup).forEach { indicator ->
            val bg = indicator.background as? android.graphics.drawable.GradientDrawable
            bg?.setColor(activeColor)
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }
}