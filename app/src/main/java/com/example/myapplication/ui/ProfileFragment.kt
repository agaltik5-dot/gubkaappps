package com.example.myapplication.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.google.android.material.switchmaterial.SwitchMaterial

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switchDarkTheme = view.findViewById<SwitchMaterial>(R.id.switch_dark_theme)
        val btnChangeGroup = view.findViewById<TextView>(R.id.btn_change_group)
        val btnAbout = view.findViewById<TextView>(R.id.btn_about)

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

        // 3. Обработка кликов по пунктам меню
        btnChangeGroup.setOnClickListener {
            Toast.makeText(requireContext(), "Выбор основной группы", Toast.LENGTH_SHORT).show()
        }

        btnAbout.setOnClickListener {
            Toast.makeText(requireContext(), "Расписание РГУ нефти и газа v1.0", Toast.LENGTH_SHORT).show()
        }
    }
}