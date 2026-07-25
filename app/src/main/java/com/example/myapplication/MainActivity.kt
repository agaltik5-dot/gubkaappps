package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.myapplication.ui.ProfileFragment
import com.example.myapplication.ui.ScheduleFragment
import com.example.myapplication.ui.SearchFragment

class MainActivity : AppCompatActivity() {

    // Переменная для отслеживания текущей выбранной иконки (по умолчанию 0 = Расписание)
    private var selectedTabIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Настройка отступов для системных баров
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Восстанавливаем индекс выбранной вкладки при пересоздании (например, при смене темы)
        if (savedInstanceState != null) {
            selectedTabIndex = savedInstanceState.getInt("KEY_SELECTED_TAB", 0)
        }

        // Инициализируем нижнее меню
        setupBottomNav()

        // Показываем фрагмент и подсвечиваем иконку согласно selectedTabIndex
        if (savedInstanceState == null) {
            openTab(0)
        } else {
            // Если Activity пересоздалась, обновляем подсветку иконки под текущий фрагмент
            updateNavTintByIndex(selectedTabIndex)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Сохраняем номер выбранной вкладки перед пересозданием
        outState.putInt("KEY_SELECTED_TAB", selectedTabIndex)
    }

    private fun setupBottomNav() {
        val navToday = findViewById<ImageView>(R.id.nav_today)
        val navSearch = findViewById<ImageView>(R.id.nav_search)
        val navProfile = findViewById<ImageView>(R.id.nav_profile)

        navToday.setOnClickListener {
            openTab(0)
        }

        navSearch.setOnClickListener {
            openTab(1)
        }

        navProfile.setOnClickListener {
            openTab(2)
        }
    }

    private fun openTab(index: Int) {
        selectedTabIndex = index
        val fragment: Fragment = when (index) {
            0 -> ScheduleFragment()
            1 -> SearchFragment()
            2 -> ProfileFragment()
            else -> ScheduleFragment()
        }
        replaceFragment(fragment)
        updateNavTintByIndex(index)
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun updateNavTintByIndex(index: Int) {
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        // Достаем актуальный акцентный цвет
        val accentColorRes = prefs.getInt("key_accent_color", R.color.accent_blue)

        val navToday = findViewById<ImageView>(R.id.nav_today)
        val navSearch = findViewById<ImageView>(R.id.nav_search)
        val navProfile = findViewById<ImageView>(R.id.nav_profile)

        val navIcons = listOf(navToday, navSearch, navProfile)

        navIcons.forEachIndexed { i, icon ->
            val color = if (i == index) accentColorRes else R.color.ui_text_sub
            icon.setColorFilter(ContextCompat.getColor(this, color))
        }
    }
}