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
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.ScheduleRepository
import com.google.android.material.textfield.TextInputLayout

class EditStudentDataFragment : Fragment(R.layout.fragment_edit_student_data) {

    private lateinit var scheduleRepository: ScheduleRepository
    private var currentFacultyId: Int = 0
    private var currentGroups: List<Pair<String, Int>> = emptyList()
    private var selectedGroupId: Int = -1
    private var selectedGroupCode: String = ""
    private var selectedFacultyName: String = ""

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

        // Скрываем нижнюю навигацию
        activity?.findViewById<View>(R.id.card_nav)?.visibility = View.GONE

        editContainer = view.findViewById(R.id.edit_container)

        val etFirstName = view.findViewById<EditText>(R.id.et_first_name)
        val etLastName = view.findViewById<EditText>(R.id.et_last_name)
        val etPhone = view.findViewById<EditText>(R.id.et_phone)
        val etEmail = view.findViewById<EditText>(R.id.et_email)
        val etBirthDate = view.findViewById<EditText>(R.id.et_birth_date)

        actvFaculty = view.findViewById(R.id.actv_faculty)
        actvGroup = view.findViewById(R.id.actv_group)

        rvFaculty = view.findViewById(R.id.rv_faculty_inline)
        rvGroup = view.findViewById(R.id.rv_group_inline)

        wrapperFaculty = view.findViewById(R.id.wrapper_faculty_inline)
        wrapperGroup = view.findViewById(R.id.wrapper_group_inline)

        indicatorFaculty = view.findViewById(R.id.indicator_faculty)
        indicatorGroup = view.findViewById(R.id.indicator_group)

        // Подгружаем исходные данные
        etFirstName.setText(prefs.getString("user_first_name", "Иван"))
        etLastName.setText(prefs.getString("user_last_name", "Иванов"))
        etPhone.setText(prefs.getString("user_phone", "+7 999 000 0000"))
        etEmail.setText(prefs.getString("user_email", "ivan@univ.ru"))
        etBirthDate.setText(prefs.getString("user_birth_date", "27.03.2005"))
        
        selectedFacultyName = prefs.getString("user_faculty", "") ?: ""
        selectedGroupCode = prefs.getString("user_group", "") ?: ""
        selectedGroupId = prefs.getInt("key_group_id", -1)
        
        actvFaculty.setText(selectedFacultyName, false)
        actvGroup.setText(selectedGroupCode, false)

        setupInlineDropdowns(view)
        setupScrollIndicators()
        applyThemeColor()

        // Кнопка "Назад"
        view.findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Кнопка "Сохранить" (галочка)
        view.findViewById<ImageView>(R.id.btn_save)?.setOnClickListener {
            prefs.edit {
                putString("user_first_name", etFirstName.text.toString().trim())
                putString("user_last_name", etLastName.text.toString().trim())
                putString("user_phone", etPhone.text.toString().trim())
                putString("user_email", etEmail.text.toString().trim())
                putString("user_faculty", selectedFacultyName.trim())
                putString("user_group", selectedGroupCode.trim())
                putInt("key_group_id", selectedGroupId)
                putString("key_group_code", selectedGroupCode.trim())
                putString("user_birth_date", etBirthDate.text.toString().trim())
                apply()
            }

            // Отправляем сигнал об обновлении в родительский фрагмент
            parentFragmentManager.setFragmentResult("student_data_updated", Bundle())

            Toast.makeText(requireContext(), "Данные сохранены", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupInlineDropdowns(rootView: View) {
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
        
        // Initial group update if faculty is already selected
        if (selectedFacultyName.isNotEmpty()) {
            currentFacultyId = scheduleRepository.FACULTIES.filterValues { it == selectedFacultyName }.keys.firstOrNull() ?: 0
            updateGroupsDropdown()
        }
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