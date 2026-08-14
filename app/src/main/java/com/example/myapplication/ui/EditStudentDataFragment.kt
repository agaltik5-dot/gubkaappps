package com.example.myapplication.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.example.myapplication.R

class EditStudentDataFragment : Fragment(R.layout.fragment_edit_student_data) {

    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var actvFaculty: AutoCompleteTextView
    private lateinit var actvGroup: AutoCompleteTextView
    private lateinit var etBirthDate: EditText

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Скрываем нижнюю навигацию
        activity?.findViewById<View>(R.id.card_nav)?.visibility = View.GONE

        etFirstName = view.findViewById(R.id.et_first_name)
        etLastName = view.findViewById(R.id.et_last_name)
        actvFaculty = view.findViewById(R.id.actv_faculty)
        actvGroup = view.findViewById(R.id.actv_group)
        etBirthDate = view.findViewById(R.id.et_birth_date)

        // Загрузка сохраненных данных
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        etFirstName.setText(prefs.getString("user_first_name", "Иван"))
        etLastName.setText(prefs.getString("user_last_name", "Иванов"))
        actvFaculty.setText(prefs.getString("user_faculty", "Информационные технологии"))
        actvGroup.setText(prefs.getString("user_group", "ИВТ-221"))
        etBirthDate.setText(prefs.getString("user_birth_date", "27 мар. 2005 (19 лет)"))

        // Кнопка "Назад"
        view.findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Кнопка "Сохранить" (галочка)
        view.findViewById<ImageView>(R.id.btn_save)?.setOnClickListener {
            prefs.edit {
                putString("user_first_name", etFirstName.text.toString().trim())
                putString("user_last_name", etLastName.text.toString().trim())
                putString("user_faculty", actvFaculty.text.toString().trim())
                putString("user_group", actvGroup.text.toString().trim())
                putString("user_birth_date", etBirthDate.text.toString().trim())
            }
            parentFragmentManager.popBackStack()
        }
    }
}
