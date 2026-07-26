package com.example.myapplication.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.data.ScheduleRepository
import com.google.android.material.tabs.TabLayout

class SearchFragment : Fragment(R.layout.fragment_search) {

    private lateinit var scheduleRepository: ScheduleRepository
    private var currentFacultyId: Int = 0
    private var currentGroups: List<Pair<String, Int>> = emptyList()
    private var selectedGroupId: Int = -1
    private var selectedGroupCode: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleRepository = ScheduleRepository(requireContext())

        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout_search)
        val spinnerSemester = view.findViewById<Spinner>(R.id.spinner_semester)
        val spinnerFaculty = view.findViewById<Spinner>(R.id.spinner_faculty)
        val spinnerGroup = view.findViewById<Spinner>(R.id.spinner_group)
        val layoutFacultySelectors = view.findViewById<LinearLayout>(R.id.layout_faculty_selectors)
        val etSearchQuery = view.findViewById<EditText>(R.id.et_search_query)
        val btnSelectGroup = view.findViewById<Button>(R.id.btn_select_group)

        // 1. Заполняем выпадающий список СЕМЕСТРОВ
        val semesters = listOf("2025/2026 осенний", "2025/2026 весенний")
        val semesterAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, semesters)
        spinnerSemester.adapter = semesterAdapter

        // 2. Заполняем выпадающий список ФАКУЛЬТЕТОВ из репозитория
        val facultyNames = scheduleRepository.FACULTIES.values.toList()
        val facultyIds = scheduleRepository.FACULTIES.keys.toList()
        
        val facultyAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, facultyNames)
        spinnerFaculty.adapter = facultyAdapter

        spinnerFaculty.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFacultyId = facultyIds[position]
                updateGroupsSpinner(spinnerGroup)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 3. Логика выбора ГРУППЫ
        spinnerGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (currentGroups.isNotEmpty()) {
                    val group = currentGroups[position]
                    selectedGroupCode = group.first
                    selectedGroupId = group.second
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 4. Кнопка сохранения выбора
        btnSelectGroup.setOnClickListener {
            if (selectedGroupId != -1) {
                saveSelectedGroup(selectedGroupId, selectedGroupCode)
                Toast.makeText(requireContext(), "Группа $selectedGroupCode выбрана!", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Логика переключения ВКЛАДОК
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // Вкладка "Факультеты"
                        layoutFacultySelectors.visibility = View.VISIBLE
                        btnSelectGroup.visibility = View.VISIBLE
                        etSearchQuery.visibility = View.GONE
                    }
                    1 -> { // Вкладка "Преподаватели"
                        layoutFacultySelectors.visibility = View.GONE
                        btnSelectGroup.visibility = View.GONE
                        etSearchQuery.visibility = View.VISIBLE
                        etSearchQuery.hint = "Введите ФИО преподавателя..."
                    }
                    2 -> { // Вкладка "Аудитории"
                        layoutFacultySelectors.visibility = View.GONE
                        btnSelectGroup.visibility = View.GONE
                        etSearchQuery.visibility = View.VISIBLE
                        etSearchQuery.hint = "Введите номер аудитории..."
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateGroupsSpinner(spinner: Spinner) {
        currentGroups = scheduleRepository.getGroupsForFaculty(currentFacultyId)
        val groupNames = currentGroups.map { it.first }
        
        val groupAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, groupNames)
        spinner.adapter = groupAdapter
        
        if (currentGroups.isNotEmpty()) {
            selectedGroupCode = currentGroups[0].first
            selectedGroupId = currentGroups[0].second
        } else {
            selectedGroupId = -1
            selectedGroupCode = ""
        }
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