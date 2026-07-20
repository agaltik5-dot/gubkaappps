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

        // При первом запуске показываем расписание
        if (savedInstanceState == null) {
            replaceFragment(ScheduleFragment())
        }

        // ВЫЗЫВАЕМ настройку нижнего меню
        setupBottomNav()
    }

    // Функция должна находиться ВНЕ onCreate!
    private fun setupBottomNav() {
        val navToday = findViewById<ImageView>(R.id.nav_today)
        val navSearch = findViewById<ImageView>(R.id.nav_search)
        val navProfile = findViewById<ImageView>(R.id.nav_profile)

        // 1. Кнопка "Расписание" (Сегодня)
        navToday.setOnClickListener {
            replaceFragment(ScheduleFragment())
            updateNavTint(selectedIcon = it as ImageView)
        }

        // 2. Кнопка "Поиск"
        navSearch.setOnClickListener {
            replaceFragment(SearchFragment())
            updateNavTint(selectedIcon = it as ImageView)
        }

        // 3. Кнопка "Профиль"
        navProfile.setOnClickListener {
            replaceFragment(ProfileFragment())
            updateNavTint(selectedIcon = it as ImageView)
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main, fragment)
            .commit()
    }

    private fun updateNavTint(selectedIcon: ImageView) {
        val navIcons = listOf(
            findViewById<ImageView>(R.id.nav_today),
            findViewById<ImageView>(R.id.nav_search),
            findViewById<ImageView>(R.id.nav_profile)
        )

        navIcons.forEach { icon ->
            val color = if (icon == selectedIcon) R.color.ui_primary else R.color.ui_text_sub
            icon.setColorFilter(ContextCompat.getColor(this, color))
        }
    }
}