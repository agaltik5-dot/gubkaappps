package com.example.myapplication.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.google.android.material.switchmaterial.SwitchMaterial

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        // Инициализация элементов
        val btnNotificationsSection = view.findViewById<LinearLayout>(R.id.btn_notifications_section)
        val btnThemeSettings = view.findViewById<LinearLayout>(R.id.btn_theme_settings)
        val btnLanguage = view.findViewById<LinearLayout>(R.id.btn_language)
        val tvCurrentLanguage = view.findViewById<TextView>(R.id.tv_current_language)

        val switchDefaultScale = view.findViewById<SwitchMaterial>(R.id.switch_default_scale)
        val seekBarScale = view.findViewById<SeekBar>(R.id.seekbar_scale)
        val tvScaleValue = view.findViewById<TextView>(R.id.tv_scale_value)

        val btnLinkLms = view.findViewById<LinearLayout>(R.id.btn_link_lms)
        val btnLinkMap = view.findViewById<LinearLayout>(R.id.btn_link_map)
        val btnResetSettings = view.findViewById<TextView>(R.id.btn_reset_settings)
        val btnAbout = view.findViewById<TextView>(R.id.btn_about)

        // 1. Раздел "Уведомления и звуки"
        btnNotificationsSection.setOnClickListener {
            Toast.makeText(requireContext(), "Раздел: Уведомления и звуки", Toast.LENGTH_SHORT).show()
        }

        // 2. Раздел "Настройки темы"
        btnThemeSettings.setOnClickListener {
            Toast.makeText(requireContext(), "Раздел: Настройки темы", Toast.LENGTH_SHORT).show()
        }

        // 3. Выбор языка
        tvCurrentLanguage.text = prefs.getString("app_language", "Русский")
        btnLanguage.setOnClickListener {
            val languages = arrayOf("Русский", "English", "Татарча", "Беларуская")
            AlertDialog.Builder(requireContext())
                .setTitle("Язык / Language")
                .setItems(languages) { _, which ->
                    val selectedLang = languages[which]
                    tvCurrentLanguage.text = selectedLang
                    prefs.edit().putString("app_language", selectedLang).apply()
                }
                .show()
        }

        // 4. Логика тумблера и ползунка масштаба (диапазон 50% - 150%, 100% ровно посередине)
        val isDefaultScale = prefs.getBoolean("is_default_scale", true)
        switchDefaultScale.isChecked = isDefaultScale
        seekBarScale.isEnabled = !isDefaultScale

        val savedScale = prefs.getInt("app_scale", 100)
        tvScaleValue.text = "$savedScale%"
        seekBarScale.progress = savedScale - 50 // При 100% progress будет 50 (ровно центр)

        switchDefaultScale.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_default_scale", isChecked).apply()
            seekBarScale.isEnabled = !isChecked
            if (isChecked) {
                seekBarScale.progress = 50 // Сбрасываем ползунок на 100%
                tvScaleValue.text = "100%"
                prefs.edit().putInt("app_scale", 100).apply()
            }
        }

        seekBarScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val currentScale = 50 + progress
                tvScaleValue.text = "$currentScale%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val finalScale = 50 + (seekBar?.progress ?: 50)
                prefs.edit().putInt("app_scale", finalScale).apply()
            }
        })

        // 5. Внешние ссылки
        btnLinkLms.setOnClickListener { openWebLink("https://edu.gubkin.ru") }
        btnLinkMap.setOnClickListener { openWebLink("https://www.gubkin.ru/about_the_university/campus_map/") }

        // 6. Сброс
        btnResetSettings.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Сброс настроек")
                .setMessage("Вы уверены, что хотите очистить кэш и настройки?")
                .setPositiveButton("Сбросить") { _, _ ->
                    prefs.edit().clear().apply()
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    requireActivity().recreate()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        btnAbout.setOnClickListener {
            Toast.makeText(requireContext(), "Приложение Расписание v1.0", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWebLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }
}