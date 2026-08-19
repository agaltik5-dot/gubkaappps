package com.example.myapplication.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.google.android.material.switchmaterial.SwitchMaterial

class NotificationsSettingsFragment : Fragment(R.layout.fragment_notifications_settings) {

    private var switchLessonReminder: SwitchMaterial? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            sendTestNotification()
        } else {
            switchLessonReminder?.isChecked = false
            savePreference("pref_lesson_reminder", false)
            Toast.makeText(requireContext(), "Разрешение на уведомления не получено", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        val btnBack = view.findViewById<ImageView>(R.id.btn_back)
        switchLessonReminder = view.findViewById(R.id.switch_lesson_reminder)
        val switchScheduleChanges = view.findViewById<SwitchMaterial>(R.id.switch_schedule_changes)
        val switchSound = view.findViewById<SwitchMaterial>(R.id.switch_sound)
        val switchVibration = view.findViewById<SwitchMaterial>(R.id.switch_vibration)
        val switchBackgroundService = view.findViewById<SwitchMaterial>(R.id.switch_background_service)

        // Кнопка Назад
        btnBack?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Загрузка состояний
        switchLessonReminder?.isChecked = prefs.getBoolean("pref_lesson_reminder", true)
        switchScheduleChanges?.isChecked = prefs.getBoolean("pref_schedule_changes", true)
        switchSound?.isChecked = prefs.getBoolean("pref_sound", true)
        switchVibration?.isChecked = prefs.getBoolean("pref_vibration", true)
        switchBackgroundService?.isChecked = prefs.getBoolean("pref_background_service", true)

        // Сохранение состояний
        switchLessonReminder?.setOnCheckedChangeListener { _, isChecked ->
            savePreference("pref_lesson_reminder", isChecked)
        }
        switchScheduleChanges?.setOnCheckedChangeListener { _, isChecked ->
            savePreference("pref_schedule_changes", isChecked)
        }
        switchSound?.setOnCheckedChangeListener { _, isChecked ->
            savePreference("pref_sound", isChecked)
        }
        switchVibration?.setOnCheckedChangeListener { _, isChecked ->
            savePreference("pref_vibration", isChecked)
        }
        switchBackgroundService?.setOnCheckedChangeListener { _, isChecked ->
            savePreference("pref_background_service", isChecked)
        }

        applyThemeColor()
    }

    override fun onResume() {
        super.onResume()
        applyThemeColor()
    }

    private fun savePreference(key: String, value: Boolean) {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit { putBoolean(key, value) }
    }

    private fun sendTestNotification() {
        // Логика тестового уведомления (если требуется)
    }

    private fun applyThemeColor() {
        val context = requireContext()
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val accentColorHex = prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7"
        val activeColor = accentColorHex.toColorInt()

        val root = view ?: return

        // 1. Покраска заголовков категорий
        root.findViewById<TextView>(R.id.tv_cat_lessons)?.setTextColor(activeColor)
        root.findViewById<TextView>(R.id.tv_cat_in_app)?.setTextColor(activeColor)
        root.findViewById<TextView>(R.id.tv_cat_background)?.setTextColor(activeColor)

        // 2. Настройка цвета переключателей (Thumb и Track)
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )

        val thumbTintList = ColorStateList(
            states,
            intArrayOf(
                activeColor,
                Color.parseColor("#888888")
            )
        )

        val trackTintList = ColorStateList(
            states,
            intArrayOf(
                adjustAlpha(activeColor, 0.4f),
                Color.parseColor("#33888888")
            )
        )

        val switches = listOfNotNull(
            root.findViewById<SwitchMaterial>(R.id.switch_lesson_reminder),
            root.findViewById<SwitchMaterial>(R.id.switch_schedule_changes),
            root.findViewById<SwitchMaterial>(R.id.switch_sound),
            root.findViewById<SwitchMaterial>(R.id.switch_vibration),
            root.findViewById<SwitchMaterial>(R.id.switch_background_service)
        )

        switches.forEach { switch ->
            switch.thumbTintList = thumbTintList
            switch.trackTintList = trackTintList
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
}