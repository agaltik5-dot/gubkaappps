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
import com.example.myapplication.ui.NewsFragment
import android.content.Context
import androidx.core.graphics.toColorInt
import android.view.HapticFeedbackConstants
import android.view.View
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build

class MainActivity : AppCompatActivity() {

    // Переменная для отслеживания текущей выбранной иконки (по умолчанию 0 = Расписание)
    private var selectedTabIndex = 0
    private lateinit var navSlider: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        navSlider = findViewById(R.id.nav_slider)
        val bottomBlur = findViewById<View>(R.id.view_bottom_blur)

        // Настройка отступов для системных баров
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Убираем нижний паддинг, чтобы контент заходил под навбар
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            
            // Устанавливаем высоту размытия равной высоте навбара
            bottomBlur.layoutParams.height = systemBars.bottom
            bottomBlur.requestLayout()
            
            applyBlurEffect(bottomBlur, 45f)
            
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
        val navNews = findViewById<ImageView>(R.id.nav_news)
        val navProfile = findViewById<ImageView>(R.id.nav_profile)

        navToday.setOnClickListener { openTab(0) }
        navSearch.setOnClickListener { openTab(1) }
        navNews.setOnClickListener { openTab(2) }
        navProfile.setOnClickListener { openTab(3) }
    }

    private fun openTab(index: Int, animate: Boolean = true) {
        if (selectedTabIndex == index && animate) return // Не дергаем, если уже тут

        // Добавляем вибрацию
        val currentIcon = when(index) {
            0 -> findViewById<View>(R.id.nav_today)
            1 -> findViewById<View>(R.id.nav_search)
            2 -> findViewById<View>(R.id.nav_news)
            else -> findViewById<View>(R.id.nav_profile)
        }
        currentIcon?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val oldIndex = selectedTabIndex
        selectedTabIndex = index
        val fragment: Fragment = when (index) {
            0 -> ScheduleFragment()
            1 -> SearchFragment()
            2 -> NewsFragment()
            3 -> ProfileFragment()
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

    /**
     * Public method to allow fragments to request a tab switch with an optional action.
     */
    fun switchToTab(index: Int, action: (() -> Unit)? = null) {
        openTab(index)
        if (action != null) {
            // Give some time for the fragment to be swapped and its view created
            findViewById<View>(R.id.fragment_container).postDelayed({
                action.invoke()
            }, 100)
        }
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
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        // 1. Считываем сохраненный акцентный цвет из SharedPreferences
        val accentColorHex = prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7"
        val activeColor = accentColorHex.toColorInt()

        // Неактивный цвет иконки берем из ресурсов
        val inactiveColor = ContextCompat.getColor(this, R.color.ui_text_main)

        val navToday = findViewById<ImageView>(R.id.nav_today)
        val navSearch = findViewById<ImageView>(R.id.nav_search)
        val navNews = findViewById<ImageView>(R.id.nav_news)
        val navProfile = findViewById<ImageView>(R.id.nav_profile)

        val navIcons = listOf(navToday, navSearch, navNews, navProfile)

        // 2. Перекрашиваем иконки (выбранную в акцентный цвет, остальные — в стандартный)
        navIcons.forEachIndexed { i, icon ->
            if (i == index) {
                icon?.setColorFilter(activeColor)
            } else {
                icon?.setColorFilter(inactiveColor)
            }
        }

        // 3. ПЕРЕКРАШИВАЕМ НИЖНЮЮ ПОЛОСКУ-СЛАЙДЕР В АКТУАЛЬНЫЙ ЦВЕТ
        navSlider.background?.setTint(activeColor)

        // 4. Анимация движения слайдера
        navSlider.post {
            val parentView = navSlider.parent as View
            val totalWidth = parentView.width - parentView.paddingLeft - parentView.paddingRight
            val tabWidth = totalWidth / 4f

            val indicatorWidth = tabWidth * 0.4f

            val params = navSlider.layoutParams
            params.width = indicatorWidth.toInt()
            navSlider.layoutParams = params

            val targetX = (index * tabWidth) + (tabWidth - indicatorWidth) / 2f

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

    private fun applyBlurEffect(view: View, radius: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            view.setRenderEffect(blurEffect)
        }
    }
}
