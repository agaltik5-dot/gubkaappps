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

import android.view.HapticFeedbackConstants
import android.view.View

class MainActivity : AppCompatActivity() {

    // Переменная для отслеживания текущей выбранной иконки (по умолчанию 0 = Расписание)
    private var selectedTabIndex = 0
    private lateinit var navSlider: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        navSlider = findViewById(R.id.nav_slider)

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
            openTab(0, animate = false)
        } else {
            // Если Activity пересоздалась, обновляем подсветку иконки под текущий фрагмент
            updateNavTintByIndex(selectedTabIndex, animate = false)
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

    private fun openTab(index: Int, animate: Boolean = true) {
        if (selectedTabIndex == index && animate) return // Не дергаем, если уже тут

        // Добавляем вибрацию
        val currentIcon = when(index) {
            0 -> findViewById<View>(R.id.nav_today)
            1 -> findViewById<View>(R.id.nav_search)
            else -> findViewById<View>(R.id.nav_profile)
        }
        currentIcon.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val oldIndex = selectedTabIndex
        selectedTabIndex = index
        val fragment: Fragment = when (index) {
            0 -> ScheduleFragment()
            1 -> SearchFragment()
            2 -> ProfileFragment()
            else -> ScheduleFragment()
        }

        if (animate) {
            if (index > oldIndex) {
                replaceFragment(fragment, R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                replaceFragment(fragment, R.anim.slide_in_left, R.anim.slide_out_right)
            }
        } else {
            replaceFragment(fragment)
        }
        
        updateNavTintByIndex(index, animate)
    }

    private fun replaceFragment(fragment: Fragment, enterAnim: Int = 0, exitAnim: Int = 0) {
        val transaction = supportFragmentManager.beginTransaction()
        if (enterAnim != 0 && exitAnim != 0) {
            transaction.setCustomAnimations(enterAnim, exitAnim)
        }
        transaction.replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun updateNavTintByIndex(index: Int, animate: Boolean = true) {
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

        // Анимация ползунка
        navSlider.post {
            val totalWidth = (navSlider.parent as View).width - (navSlider.parent as View).paddingLeft - (navSlider.parent as View).paddingRight
            val tabWidth = totalWidth / 3f
            
            // Устанавливаем ширину ползунка (1/3 от меню)
            val params = navSlider.layoutParams
            params.width = tabWidth.toInt()
            navSlider.layoutParams = params

            val targetX = index * tabWidth
            
            if (animate) {
                navSlider.animate()
                    .translationX(targetX)
                    .setDuration(250)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            } else {
                navSlider.translationX = targetX
            }
        }
    }
}