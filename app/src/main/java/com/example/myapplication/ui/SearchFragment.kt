package com.example.myapplication.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.google.android.material.tabs.TabLayout

class SearchFragment : Fragment(R.layout.fragment_search) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout_search)
        val spinnerSemester = view.findViewById<Spinner>(R.id.spinner_semester)
        val spinnerFaculty = view.findViewById<Spinner>(R.id.spinner_faculty)
        val spinnerGroup = view.findViewById<Spinner>(R.id.spinner_group)
        val layoutFacultySelectors = view.findViewById<LinearLayout>(R.id.layout_faculty_selectors)
        val etSearchQuery = view.findViewById<EditText>(R.id.et_search_query)

        // 1. Заполняем выпадающий список СЕМЕСТРОВ
        val semesters = listOf("2025/2026 весенний", "2025/2026 осенний")
        val semesterAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, semesters)
        spinnerSemester.adapter = semesterAdapter

        // 2. Заполняем выпадающий список ФАКУЛЬТЕТОВ
        val faculties = listOf("Инженерной механики", "Информационных технологий", "Экономики")
        val facultyAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, faculties)
        spinnerFaculty.adapter = facultyAdapter

        // 3. Заполняем выпадающий список ГРУПП
        val groups = listOf("МР-24-10", "МР-24-11", "ИВТ-22-01")
        val groupAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, groups)
        spinnerGroup.adapter = groupAdapter

        // 4. Логика переключения ВКЛАДОК (Факультеты / Преподаватели / Аудитории)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // Вкладка "Факультеты"
                        layoutFacultySelectors.visibility = View.VISIBLE
                        etSearchQuery.visibility = View.GONE
                    }
                    1 -> { // Вкладка "Преподаватели"
                        layoutFacultySelectors.visibility = View.GONE
                        etSearchQuery.visibility = View.VISIBLE
                        etSearchQuery.hint = "Введите ФИО преподавателя..."
                    }
                    2 -> { // Вкладка "Аудитории"
                        layoutFacultySelectors.visibility = View.GONE
                        etSearchQuery.visibility = View.VISIBLE
                        etSearchQuery.hint = "Введите номер аудитории..."
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
}