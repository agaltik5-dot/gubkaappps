package com.example.myapplication.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R

class ThemeSettingsFragment : Fragment(R.layout.fragment_theme_settings) {

    // Палитра без жёлтого кружка (Внешний цвет, Внутренний акцентный цвет)
    private val telegramColors = listOf(
        TelegramColor("#B0D8E8", "#4FC3F7"), // Голубой
        TelegramColor("#F8BBD0", "#F06292"), // Розовый
        TelegramColor("#E1BEE7", "#BA68C8"), // Сиреневый
        TelegramColor("#DCEDC8", "#9CCC65"), // Салатовый
        TelegramColor("#B2EBF2", "#4DD0E1"), // Бирюзовый
        TelegramColor("#FFCCBC", "#FF7043"), // Оранжевый
        TelegramColor("#C5CAE9", "#5C6BC0"), // Индиго / Синий
        TelegramColor("#D1C4E9", "#7E57C2")  // Фиолетовый
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        // Скрытие BottomNav при входе в настройки
        val cardNav = requireActivity().findViewById<View>(R.id.card_nav)
        cardNav?.visibility = View.GONE

        // Элементы переключения режима (Дневная / Тёмная)
        val cardLight = view.findViewById<View>(R.id.card_light_preview)
        val cardDark = view.findViewById<View>(R.id.card_dark_preview)
        val dotLight = view.findViewById<ImageView>(R.id.dot_light)
        val dotDark = view.findViewById<ImageView>(R.id.dot_dark)

        // Текущие сохранённые настройки
        val isDarkMode = prefs.getBoolean("is_dark_mode", false)
        var currentAccent = prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7"

        // Первоначальная установка UI для тем и превью
        setSelectedThemeUI(isDarkMode, currentAccent, cardLight, cardDark, dotLight, dotDark)
        updatePreviewColors(view, currentAccent)

        // -------------------------------------------------------------
        // 1. ВЫБОР ДНЕВНОЙ / ТЁМНОЙ ТЕМЫ
        // -------------------------------------------------------------
        view.findViewById<View>(R.id.btn_mode_light)?.setOnClickListener {
            prefs.edit { putBoolean("is_dark_mode", false) }
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            val accent = prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7"
            setSelectedThemeUI(false, accent, cardLight, cardDark, dotLight, dotDark)
        }

        view.findViewById<View>(R.id.btn_mode_dark)?.setOnClickListener {
            prefs.edit { putBoolean("is_dark_mode", true) }
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            val accent = prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7"
            setSelectedThemeUI(true, accent, cardLight, cardDark, dotLight, dotDark)
        }

        // -------------------------------------------------------------
        // 2. ИНИЦИАЛИЗАЦИЯ ПРЕВЬЮ И СПИСКА ЦВЕТОВ
        // -------------------------------------------------------------
        val rvColors = view.findViewById<RecyclerView>(R.id.rv_theme_colors)

        val colorsAdapter = ThemeColorsAdapter(telegramColors, currentAccent) { selectedColor ->
            currentAccent = selectedColor.hexInner

            // Сохраняем акцентный цвет в SharedPreferences
            prefs.edit { putString("accent_color", currentAccent) }

            // Обновляем карточку превью расписания на лету
            updatePreviewColors(view, currentAccent)

            // Обновляем индикаторы тем с новым акцентным цветом
            val activeDarkState = prefs.getBoolean("is_dark_mode", false)
            setSelectedThemeUI(activeDarkState, currentAccent, cardLight, cardDark, dotLight, dotDark)
        }

        rvColors?.adapter = colorsAdapter

        // Кнопка назад
        view.findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    // Мгновенное обновление цветов элементов в блоке превью расписания
    private fun updatePreviewColors(rootView: View, hexColor: String) {
        val activeColor = hexColor.toColorInt()

        // 1. Полоска-индикатор пары
        val indicator = rootView.findViewById<View>(R.id.view_preview_indicator)
        indicator?.background?.setTint(activeColor)

        // 2. Бейджик "Лекция"
        val badge = rootView.findViewById<TextView>(R.id.tv_preview_badge)
        badge?.setBackgroundResource(R.drawable.bg_day_active)
        badge?.background?.setTint(activeColor)

        // 3. Выбранная плашка дня недели
        val dayLayout = rootView.findViewById<View>(R.id.layout_preview_day)
        dayLayout?.setBackgroundResource(R.drawable.bg_day_active)
        dayLayout?.background?.setTint(activeColor)
    }

    // Подсветка выбранной карточки темы (Светлая / Тёмная) и индикатора-точки
    private fun setSelectedThemeUI(
        isDark: Boolean,
        accentHex: String,
        cardLight: View?,
        cardDark: View?,
        dotLight: ImageView?,
        dotDark: ImageView?
    ) {
        val accentColor = accentHex.toColorInt()

        // Сбрасываем tint, так как цвет задаем напрямую внутри создаваемого Drawable
        dotLight?.imageTintList = null
        dotDark?.imageTintList = null

        if (isDark) {
            // --- ТЁМНАЯ ТЕМА ВЫБРАНА ---
            cardLight?.alpha = 0.6f
            cardDark?.alpha = 1.0f

            // Дневная (невыбранная) — серый ободок
            dotLight?.setImageResource(R.drawable.bg_radio_unchecked)

            // Тёмная (выбранная) — белый ободок с белой точкой внутри
            dotDark?.setImageDrawable(createCheckedRadioDrawable(android.graphics.Color.WHITE))

        } else {
            // --- ДНЕВНАЯ ТЕМА ВЫБРАНА ---
            cardLight?.alpha = 1.0f
            cardDark?.alpha = 0.6f

            // Дневная (выбранная) — акцентный ободок с акцентной точкой внутри!
            dotLight?.setImageDrawable(createCheckedRadioDrawable(accentColor))

            // Тёмная (невыбранная) — серый ободок
            dotDark?.setImageResource(R.drawable.bg_radio_unchecked)
        }
    }

    // Создает радиокнопку (ободок + точка внутри) нужного цвета
    private fun createCheckedRadioDrawable(color: Int): android.graphics.drawable.Drawable {
        val density = resources.displayMetrics.density

        // 1. Внешний ободок (прозрачный внутри, цветная обводка)
        val outerRing = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setStroke((2 * density).toInt(), color)
            setSize((20 * density).toInt(), (20 * density).toInt())
        }

        // 2. Внутренняя точка
        val innerDot = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }

        // 3. Собираем в LayerDrawable с отступами для точки
        val layers = arrayOf(outerRing, innerDot)
        val layerDrawable = android.graphics.drawable.LayerDrawable(layers)

        val inset = (5 * density).toInt()
        layerDrawable.setLayerInset(1, inset, inset, inset, inset)

        return layerDrawable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val cardNav = requireActivity().findViewById<View>(R.id.card_nav)
        cardNav?.visibility = View.VISIBLE
    }
}