package com.example.myapplication.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.google.android.material.textfield.TextInputEditText

class EditStudentDataFragment : Fragment(R.layout.fragment_edit_student_data) {

    private lateinit var etFirstName: TextInputEditText
    private lateinit var etLastName: TextInputEditText
    private lateinit var etFaculty: TextInputEditText
    private lateinit var etGroup: TextInputEditText
    private lateinit var etBirthDate: TextInputEditText

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Скрываем нижнюю навигацию
        activity?.findViewById<View>(R.id.card_nav)?.visibility = View.GONE

        etFirstName = view.findViewById(R.id.et_first_name)
        etLastName = view.findViewById(R.id.et_last_name)
        etFaculty = view.findViewById(R.id.et_faculty)
        etGroup = view.findViewById(R.id.et_group)
        etBirthDate = view.findViewById(R.id.et_birth_date)

        // Загрузка сохраненных данных
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        etFirstName.setText(prefs.getString("user_first_name", "Иван"))
        etLastName.setText(prefs.getString("user_last_name", "Иванов"))
        etFaculty.setText(prefs.getString("user_faculty", "Информационные технологии"))
        etGroup.setText(prefs.getString("user_group", "ИВТ-221"))
        etBirthDate.setText(prefs.getString("user_birth_date", "27 мар. 2005 (19 лет)"))

        // Кнопка "Закрыть" (крестик)
        view.findViewById<ImageView>(R.id.btn_close)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Кнопка "Сохранить" (галочка)
        view.findViewById<ImageView>(R.id.btn_save)?.setOnClickListener {
            prefs.edit {
                putString("user_first_name", etFirstName.text.toString().trim())
                putString("user_last_name", etLastName.text.toString().trim())
                putString("user_faculty", etFaculty.text.toString().trim())
                putString("user_group", etGroup.text.toString().trim())
                putString("user_birth_date", etBirthDate.text.toString().trim())
            }
            parentFragmentManager.popBackStack()
        }
    }
}