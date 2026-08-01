package com.example.myapplication.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.example.myapplication.R

class ThemeSettingsFragment : Fragment(R.layout.fragment_theme_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        // Скрытие BottomNav
        val cardNav = requireActivity().findViewById<View>(R.id.card_nav)
        cardNav?.visibility = View.GONE

        // 1. Выбор Дневной / Тёмной темы
        val cardLight = view.findViewById<View>(R.id.card_light_preview)
        val cardDark = view.findViewById<View>(R.id.card_dark_preview)
        val dotLight = view.findViewById<ImageView>(R.id.dot_light)
        val dotDark = view.findViewById<ImageView>(R.id.dot_dark)

        val isDarkMode = prefs.getBoolean("is_dark_mode", false)
        setSelectedThemeUI(isDarkMode, cardLight, cardDark, dotLight, dotDark)

        view.findViewById<View>(R.id.btn_mode_light)?.setOnClickListener {
            prefs.edit { putBoolean("is_dark_mode", false) }
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            setSelectedThemeUI(false, cardLight, cardDark, dotLight, dotDark)
        }

        view.findViewById<View>(R.id.btn_mode_dark)?.setOnClickListener {
            prefs.edit { putBoolean("is_dark_mode", true) }
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            setSelectedThemeUI(true, cardLight, cardDark, dotLight, dotDark)
        }

        // 2. Выбор Акцентного цвета
        val colorsMap = mapOf(
            R.id.color_cyan to "#4FC3F7",
            R.id.color_pink to "#F06292",
            R.id.color_purple to "#BA68C8",
            R.id.color_light_green to "#9CCC65",
            R.id.color_teal to "#4DD0E1",
            R.id.color_yellow to "#FFEE58"
        )

        val currentAccent = prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7"
        highlightSelectedColor(view, colorsMap, currentAccent)

        colorsMap.forEach { (viewId, colorHex) ->
            view.findViewById<View>(viewId)?.setOnClickListener {
                prefs.edit { putString("accent_color", colorHex) }
                highlightSelectedColor(view, colorsMap, colorHex)
                requireActivity().recreate()
            }
        }

        view.findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    // Подсветка выбранной карточки темы (Рамка и прозрачность)
    private fun setSelectedThemeUI(
        isDark: Boolean,
        cardLight: View?,
        cardDark: View?,
        dotLight: ImageView?,
        dotDark: ImageView?
    ) {
        if (isDark) {
            cardLight?.alpha = 0.5f
            cardDark?.alpha = 1.0f
            dotLight?.isSelected = false
            dotDark?.isSelected = true
        } else {
            cardLight?.alpha = 1.0f
            cardDark?.alpha = 0.5f
            dotLight?.isSelected = true
            dotDark?.isSelected = false
        }
    }

    // Подсветка выбранного цветного кружка (увеличение размера и альфа)
    private fun highlightSelectedColor(rootView: View, colorsMap: Map<Int, String>, activeHex: String) {
        colorsMap.forEach { (viewId, hex) ->
            val colorView = rootView.findViewById<View>(viewId)
            if (hex.equals(activeHex, ignoreCase = true)) {
                colorView?.scaleX = 1.2f
                colorView?.scaleY = 1.2f
                colorView?.alpha = 1.0f
            } else {
                colorView?.scaleX = 1.0f
                colorView?.scaleY = 1.0f
                colorView?.alpha = 0.6f
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val cardNav = requireActivity().findViewById<View>(R.id.card_nav)
        cardNav?.visibility = View.VISIBLE
    }
}