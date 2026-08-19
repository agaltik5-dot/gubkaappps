package com.example.myapplication.ui

import android.content.Context
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

        val cardLight = view.findViewById<View>(R.id.card_light_preview)
        val cardDark = view.findViewById<View>(R.id.card_dark_preview)
        val dotLight = view.findViewById<ImageView>(R.id.dot_light)
        val dotDark = view.findViewById<ImageView>(R.id.dot_dark)

        val isDarkMode = prefs.getBoolean("is_dark_mode", false)
        var currentAccent = prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7"

        setSelectedThemeUI(isDarkMode, currentAccent, cardLight, cardDark, dotLight, dotDark)
        updatePreviewColors(view, currentAccent)

        // 1. ВЫБОР ДНЕВНОЙ / ТЁМНОЙ ТЕМЫ
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

        // 2. ИНИЦИАЛИЗАЦИЯ ПРЕВЬЮ И СПИСКА ЦВЕТОВ
        val rvColors = view.findViewById<RecyclerView>(R.id.rv_theme_colors)

        val colorsAdapter = ThemeColorsAdapter(telegramColors, currentAccent) { selectedColor ->
            currentAccent = selectedColor.hexInner
            prefs.edit { putString("accent_color", currentAccent) }

            updatePreviewColors(view, currentAccent)

            val activeDarkState = prefs.getBoolean("is_dark_mode", false)
            setSelectedThemeUI(activeDarkState, currentAccent, cardLight, cardDark, dotLight, dotDark)
        }

        rvColors?.adapter = colorsAdapter

        // Кнопка назад с мгновенным обновлением темы приложения
        view.findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
            requireActivity().recreate()
        }
    }

    private fun updatePreviewColors(rootView: View, hexColor: String) {
        val activeColor = hexColor.toColorInt()

        val indicator = rootView.findViewById<View>(R.id.view_preview_indicator)
        indicator?.background?.setTint(activeColor)

        val badge = rootView.findViewById<TextView>(R.id.tv_preview_badge)
        badge?.setBackgroundResource(R.drawable.bg_day_active)
        badge?.background?.setTint(activeColor)

        val dayLayout = rootView.findViewById<View>(R.id.layout_preview_day)
        dayLayout?.setBackgroundResource(R.drawable.bg_day_active)
        dayLayout?.background?.setTint(activeColor)
    }

    private fun setSelectedThemeUI(
        isDark: Boolean,
        accentHex: String,
        cardLight: View?,
        cardDark: View?,
        dotLight: ImageView?,
        dotDark: ImageView?
    ) {
        val accentColor = accentHex.toColorInt()

        dotLight?.imageTintList = null
        dotDark?.imageTintList = null

        if (isDark) {
            cardLight?.alpha = 0.6f
            cardDark?.alpha = 1.0f
            dotLight?.setImageResource(R.drawable.bg_radio_unchecked)
            dotDark?.setImageDrawable(createCheckedRadioDrawable(android.graphics.Color.WHITE))
        } else {
            cardLight?.alpha = 1.0f
            cardDark?.alpha = 0.6f
            dotLight?.setImageDrawable(createCheckedRadioDrawable(accentColor))
            dotDark?.setImageResource(R.drawable.bg_radio_unchecked)
        }
    }

    private fun createCheckedRadioDrawable(color: Int): android.graphics.drawable.Drawable {
        val density = resources.displayMetrics.density

        val outerRing = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setStroke((2 * density).toInt(), color)
            setSize((20 * density).toInt(), (20 * density).toInt())
        }

        val innerDot = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }

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