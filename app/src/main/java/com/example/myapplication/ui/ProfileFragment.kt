package com.example.myapplication.ui

import android.content.Context
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.google.android.material.switchmaterial.SwitchMaterial
import android.content.res.ColorStateList

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        val tvProfileName = view.findViewById<TextView>(R.id.tv_profile_name)
        val tvProfileDetails = view.findViewById<TextView>(R.id.tv_profile_details)
        val btnEditName = view.findViewById<ImageView>(R.id.btn_edit_name)

        // Загружаем сохраненные значения (или берем стандартные)
        val currentName = prefs.getString("key_user_name", "Иван Иванов") ?: "Иван Иванов"
        var currentGroup = prefs.getString("key_user_group", "МР-24-10") ?: "МР-24-10"
        val currentFaculty = "Инженерной механики"

        tvProfileName.text = currentName
        tvProfileDetails.text = "$currentFaculty • $currentGroup"

        val switchDarkTheme = view.findViewById<SwitchMaterial>(R.id.switch_dark_theme)
        val btnChangeGroup = view.findViewById<TextView>(R.id.btn_change_group)
        val btnAbout = view.findViewById<TextView>(R.id.btn_about)

        val btnResetSettings = view.findViewById<TextView>(R.id.btn_reset_settings)

        // 1. Проверяем текущую тему системы и ставим тумблер в нужное положение
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        switchDarkTheme.isChecked = currentNightMode == Configuration.UI_MODE_NIGHT_YES

        // 2. Обработка переключения темы
        switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        // Реализуем смену основного цвета
        val colorButtons = listOf(
            Pair(view.findViewById<ImageView>(R.id.color_blue), R.color.accent_blue),
            Pair(view.findViewById<ImageView>(R.id.color_green), R.color.accent_green),
            Pair(view.findViewById<ImageView>(R.id.color_purple), R.color.accent_purple),
            Pair(view.findViewById<ImageView>(R.id.color_red), R.color.accent_red),
            Pair(view.findViewById<ImageView>(R.id.color_orange), R.color.accent_orange),
            Pair(view.findViewById<ImageView>(R.id.color_teal), R.color.accent_teal)
        )

        val savedColorRes = prefs.getInt("key_accent_color", R.color.accent_blue)

        // Функция отрисовки стильной белой обводки вокруг выбранного круга
        fun updateColorSelection(selectedRes: Int) {
            colorButtons.forEach { (imageView, colorRes) ->
                if (colorRes == selectedRes) {
                    imageView.setImageResource(R.drawable.bg_color_selected)
                    val padding = (3 * resources.displayMetrics.density).toInt()
                    imageView.setPadding(padding, padding, padding, padding)
                } else {
                    imageView.setImageDrawable(null)
                    imageView.setPadding(0, 0, 0, 0)
                }
            }
        }

        // При старте экрана ставим обводку
        updateColorSelection(savedColorRes)

        // Назначаем клики
        colorButtons.forEach { (imageView, colorRes) ->
            imageView.setOnClickListener {
                val currentSaved = prefs.getInt("key_accent_color", R.color.accent_blue)
                if (currentSaved != colorRes) {
                    prefs.edit().putInt("key_accent_color", colorRes).apply()
                    updateColorSelection(colorRes)

                    // Пересоздаем активность, чтобы применить динамическую раскраску
                    requireActivity().recreate()
                }
            }
        }

        val accentColor = requireContext().getColor(savedColorRes)

        // Красим аватарку, иконку карандаша и кнопку
        view.findViewById<ImageView>(R.id.iv_profile_avatar).backgroundTintList = ColorStateList.valueOf(accentColor)
        view.findViewById<ImageView>(R.id.btn_edit_name).setColorFilter(accentColor)
        view.findViewById<TextView>(R.id.btn_change_group).setTextColor(accentColor)

        // 3. Обработка кликов по пунктам меню
        btnChangeGroup.setOnClickListener {
            val groups = arrayOf("МР-24-10", "МР-24-11", "ИВТ-22-01", "ЭК-23-05")

            AlertDialog.Builder(requireContext())
                .setTitle("Выберите основную группу")
                .setItems(groups) { _, which ->
                    val selectedGroup = groups[which]
                    currentGroup = selectedGroup
                    tvProfileDetails.text = "$currentFaculty • $currentGroup"

                    prefs.edit().putString("key_user_group", selectedGroup).apply()
                    Toast.makeText(
                        requireContext(),
                        "Группа изменена на $selectedGroup",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        btnAbout.setOnClickListener {
            Toast.makeText(requireContext(), "Расписание РГУ нефти и газа v1.0", Toast.LENGTH_SHORT).show()
        }

        // КЛИК ПО КАРАНДАШУ (Смена имени)
        btnEditName.setOnClickListener {
            val editText = EditText(requireContext()).apply {
                setText(tvProfileName.text.toString())
                setSelection(text.length)
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Изменить имя")
                .setView(editText)
                .setPositiveButton("Сохранить") { _, _ ->
                    val newName = editText.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        tvProfileName.text = newName
                        prefs.edit().putString("key_user_name", newName).apply()
                        Toast.makeText(requireContext(), "Имя обновлено", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        // Клик по кнопке Сброс настроек:
        btnAbout.setOnClickListener {
            Toast.makeText(requireContext(), "Расписание РГУ нефти и газа v1.0", Toast.LENGTH_SHORT).show()
        }

        btnResetSettings.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Сброс настроек")
                .setMessage("Вы уверены, что хотите сбросить настройки и очистить кэш приложения?")
                .setPositiveButton("Сбросить") { _, _ ->
                    // 1. Стираем все сохраненные переменные из памяти (SharedPreferences)
                    prefs.edit().clear().apply()

                    // 2. Возвращаем стандартную тему
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

                    Toast.makeText(requireContext(), "Настройки сброшены", Toast.LENGTH_SHORT).show()

                    // 3. Пересоздаем Activity, чтобы интерфейс мгновенно обновился к стартовому виду
                    requireActivity().recreate()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }
}