package com.example.myapplication.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.example.myapplication.R
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var ivProfileAvatar: ImageView? = null
    private var tvProfileName: TextView? = null
    private var tvProfileSubtitle: TextView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        // Инициализация элементов профиля
        ivProfileAvatar = view.findViewById(R.id.img_profile_avatar)

        // Находим текстовые поля Имени и Подписи (Факультет • Группа)
        tvProfileName = view.findViewById(R.id.tv_user_name)
            ?: view.findViewById(R.id.tv_student_name)
        tvProfileSubtitle = view.findViewById(R.id.tv_user_sub)
            ?: view.findViewById(R.id.tv_user_sub)

        val btnStudentData = view.findViewById<LinearLayout>(R.id.btn_account_details)

        val btnNotificationsSection = view.findViewById<LinearLayout>(R.id.btn_notifications_section)
        val btnNotes = view.findViewById<LinearLayout>(R.id.btn_notes)
        val btnThemeSettings = view.findViewById<LinearLayout>(R.id.btn_theme_settings)
        val btnLanguage = view.findViewById<LinearLayout>(R.id.btn_language)
        val tvCurrentLanguage = view.findViewById<TextView>(R.id.tv_current_language)

        val switchDefaultScale = view.findViewById<SwitchMaterial>(R.id.switch_default_scale)
        val seekBarScale = view.findViewById<SeekBar>(R.id.seekbar_scale)
        val tvScaleValue = view.findViewById<TextView>(R.id.tv_scale_value)

        val btnLinkLms = view.findViewById<LinearLayout>(R.id.btn_link_lms)
        val btnLinkMap = view.findViewById<LinearLayout>(R.id.btn_link_map)

        val btnResetSettings = view.findViewById<LinearLayout>(R.id.btn_reset_settings)
        val btnAbout = view.findViewById<LinearLayout>(R.id.btn_about)

        // Раздел "Данные студента"
        btnStudentData?.setOnClickListener {
            openStudentData()
        }

        // Раздел "Уведомления и звуки"
        btnNotificationsSection?.setOnClickListener {
            Toast.makeText(requireContext(), "Раздел: Уведомления и звуки", Toast.LENGTH_SHORT).show()
        }

        // Раздел "Заметки и Избранное"
        btnNotes?.setOnClickListener {
            Toast.makeText(requireContext(), "Раздел: Заметки и Избранное", Toast.LENGTH_SHORT).show()
        }

        // Раздел "Настройки темы"
        btnThemeSettings?.setOnClickListener {
            openThemeSettings()
        }

        // Выбор языка
        tvCurrentLanguage?.text = prefs.getString("app_language", "Русский")
        btnLanguage?.setOnClickListener {
            val languages = arrayOf("Русский", "English", "Татарча", "Беларуская")
            AlertDialog.Builder(requireContext())
                .setTitle("Язык / Language")
                .setItems(languages) { _, which ->
                    val selectedLang = languages[which]
                    tvCurrentLanguage.text = selectedLang
                    prefs.edit { putString("app_language", selectedLang) }
                }
                .show()
        }

        // Масштаб
        val isDefaultScale = prefs.getBoolean("is_default_scale", true)
        switchDefaultScale?.isChecked = isDefaultScale
        seekBarScale?.isEnabled = isDefaultScale

        val savedScale = prefs.getInt("app_scale", 100)
        tvScaleValue?.text = "$savedScale%"
        seekBarScale?.progress = if (isDefaultScale) savedScale - 50 else 0

        switchDefaultScale?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("is_default_scale", isChecked) }

            seekBarScale?.isEnabled = isChecked

            if (!isChecked) {
                seekBarScale?.progress = 0
                tvScaleValue?.text = "100%"
                prefs.edit { putInt("app_scale", 100) }
            } else {
                seekBarScale?.progress = 50
                tvScaleValue?.text = "100%"
            }
        }

        seekBarScale?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (switchDefaultScale?.isChecked == true) {
                    val currentScale = 50 + progress
                    tvScaleValue?.text = "$currentScale%"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (switchDefaultScale?.isChecked == true) {
                    val finalScale = 50 + (seekBar?.progress ?: 50)
                    prefs.edit { putInt("app_scale", finalScale) }
                }
            }
        })

        // Ссылки
        btnLinkLms?.setOnClickListener { openWebLink("https://edu.gubkin.ru") }
        btnLinkMap?.setOnClickListener { openWebLink("https://www.gubkin.ru/about_the_university/campus_map/") }

        // Сброс
        btnResetSettings?.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Сброс настроек")
                .setMessage("Вы уверены, что хотите очистить кэш и настройки?")
                .setPositiveButton("Сбросить") { _, _ ->
                    prefs.edit { clear() }
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    requireActivity().recreate()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        // О программе
        btnAbout?.setOnClickListener {
            Toast.makeText(requireContext(), "Приложение Расписание v1.0", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadAvatar()
        loadProfileData() // Загружаем свежие имя, фамилию, факультет и группу
    }

    private fun loadProfileData() {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        val firstName = prefs.getString("user_first_name", "Иван")
        val lastName = prefs.getString("user_last_name", "Иванов")
        val faculty = prefs.getString("user_faculty", "Инженерной механики")
        val group = prefs.getString("user_group", "МР-24-10")

        // Обновляем Имя и Фамилию
        tvProfileName?.text = "$firstName $lastName"

        // Обновляем подпись под именем
        tvProfileSubtitle?.text = "$faculty • $group"
    }

    private fun loadAvatar() {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val savedPath = prefs.getString("profile_avatar_path", null)

        ivProfileAvatar?.let { imageView ->
            if (!savedPath.isNullOrEmpty()) {
                val file = File(savedPath)
                if (file.exists()) {
                    // Снимаем синий тинт, чтобы отобразить реальное фото
                    imageView.imageTintList = null

                    Glide.with(this)
                        .load(file)
                        .signature(ObjectKey(file.lastModified().toString()))
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .circleCrop()
                        .into(imageView)
                }
            }
        }
    }

    private fun openStudentData() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, StudentDataFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openThemeSettings() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ThemeSettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openWebLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }
}