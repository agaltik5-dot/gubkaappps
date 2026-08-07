package com.example.myapplication.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.data.ScheduleRepository
import com.google.android.material.datepicker.MaterialDatePicker
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleRepository = ScheduleRepository(requireContext())
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        val etFirstName = view.findViewById<EditText>(R.id.et_first_name)
        val etLastName = view.findViewById<EditText>(R.id.et_last_name)
        val etBirthDate = view.findViewById<EditText>(R.id.et_birth_date)

        val actvFaculty = view.findViewById<AutoCompleteTextView>(R.id.actv_faculty)
        val actvGroup = view.findViewById<AutoCompleteTextView>(R.id.actv_group)

        val containerFaculty = view.findViewById<View>(R.id.container_faculty)
        val containerGroup = view.findViewById<View>(R.id.container_group)

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

        val facultyNames = scheduleRepository.FACULTIES.values.toList()
        val facultyIds = scheduleRepository.FACULTIES.keys.toList()

        val facultyAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, facultyNames)
        actvFaculty.setAdapter(facultyAdapter)

        val savedFaculty = prefs.getString("user_faculty", facultyNames.firstOrNull() ?: "")
        actvFaculty.setText(savedFaculty, false)
        val initialFacultyIndex = facultyNames.indexOf(savedFaculty).coerceAtLeast(0)
        currentFacultyId = facultyIds.getOrElse(initialFacultyIndex) { facultyIds.first() }
        selectedFacultyName = facultyNames.getOrElse(initialFacultyIndex) { facultyNames.first() }

        updateGroupsList(actvGroup, savedGroupCode = prefs.getString("user_group", "") ?: "")

        // Вызов выпадающего списка при нажатии на строку или поле
        val showFacultyMenu = { actvFaculty.showDropDown() }
        containerFaculty?.setOnClickListener { showFacultyMenu() }
        actvFaculty.setOnClickListener { showFacultyMenu() }

        val showGroupMenu = { actvGroup.showDropDown() }
        containerGroup?.setOnClickListener { showGroupMenu() }
        actvGroup.setOnClickListener { showGroupMenu() }

        actvFaculty.setOnItemClickListener { _, _, position, _ ->
            currentFacultyId = facultyIds[position]
            selectedFacultyName = facultyNames[position]
            updateGroupsList(actvGroup, "")
        }

        actvGroup.setOnItemClickListener { _, _, position, _ ->
            if (currentGroups.isNotEmpty()) {
                val group = currentGroups[position]
                selectedGroupCode = group.first
                selectedGroupId = group.second
            }
        }

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

    private fun updateGroupsList(actvGroup: AutoCompleteTextView, savedGroupCode: String) {
        currentGroups = scheduleRepository.getGroupsForFaculty(currentFacultyId)
        val groupNames = currentGroups.map { it.first }

        val groupAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, groupNames)
        actvGroup.setAdapter(groupAdapter)

        if (currentGroups.isNotEmpty()) {
            val savedGroupIndex = groupNames.indexOf(savedGroupCode)
            if (savedGroupIndex >= 0) {
                actvGroup.setText(currentGroups[savedGroupIndex].first, false)
                selectedGroupCode = currentGroups[savedGroupIndex].first
                selectedGroupId = currentGroups[savedGroupIndex].second
            } else {
                actvGroup.setText(currentGroups[0].first, false)
                selectedGroupCode = currentGroups[0].first
                selectedGroupId = currentGroups[0].second
            }
        } else {
            actvGroup.setText("", false)
            selectedGroupId = -1
            selectedGroupCode = ""
        }
    }
}