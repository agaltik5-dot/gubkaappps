package com.example.myapplication.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Загрузка состояний
        switchLessonReminder?.isChecked = prefs.getBoolean("pref_lesson_reminder", true)
        switchScheduleChanges?.isChecked = prefs.getBoolean("pref_schedule_changes", true)
        switchSound?.isChecked = prefs.getBoolean("pref_sound", true)
        switchVibration?.isChecked = prefs.getBoolean("pref_vibration", true)
        switchBackgroundService?.isChecked = prefs.getBoolean("pref_background_service", true)

        // Напоминания о парах (Запрос разрешения + мгновенное уведомление)
        switchLessonReminder?.setOnCheckedChangeListener { _, isChecked ->
            savePreference("pref_lesson_reminder", isChecked)
            if (isChecked) {
                checkPermissionAndNotify()
            }
        }

        // Остальные переключатели
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
    }

    private fun checkPermissionAndNotify() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                sendTestNotification()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            sendTestNotification()
        }
    }

    private fun sendTestNotification() {
        val notificationManager =
            requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "lesson_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Напоминания о парах",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(requireContext(), channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Расписание пар")
            .setContentText("Напоминания о парах успешно включены!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(101, notification)
    }

    private fun savePreference(key: String, value: Boolean) {
        requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            .edit { putBoolean(key, value) }
    }
}