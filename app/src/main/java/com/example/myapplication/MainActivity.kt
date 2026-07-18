package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private var selectedDayView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- 1. Настройка списка занятий (RecyclerView) ---
        setupScheduleList()

        // --- 2. Логика кнопок (Селектор дней) ---
        setupDaySelector()

        // --- 3. Логика нижнего меню ---
        setupBottomNav()
    }

    private fun setupScheduleList() {
        val recyclerView = findViewById<RecyclerView>(R.id.rv_schedule)
        
        // LayoutManager отвечает за то, как элементы располагаются (в данном случае - вертикальный список)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Создаем "фейковые" данные для теста
        val sampleLessons = listOf(
            Lesson("Люблю ДИДИЕНКО", "08:00", "09:30", "Лекция", "А-201 · Соколов А.В.", "2ч"),
            Lesson("Роман вы ЛЕОНИЛИ ПЕПСИ", "10:00", "11:30", "Лабораторная", "В-305 · Иванов К.Е.", "2ч"),
            Lesson("ФРАНЦИЯ СОСИКА", "12:00", "13:30", "Семинар", "Б-221 · Смирнова О.А.", "2ч"),
            Lesson("А?", "14:00", "15:30", "Лекция", "А-101 · Громов П.И.", "2ч")
        )

        // Создаем адаптер и привязываем его к RecyclerView
        val adapter = LessonAdapter(sampleLessons)
        recyclerView.adapter = adapter
    }

    private fun setupDaySelector() {
        val days = listOf(
            findViewById<LinearLayout>(R.id.day_mon),
            findViewById<LinearLayout>(R.id.day_tue),
            findViewById<LinearLayout>(R.id.day_wed),
            findViewById<LinearLayout>(R.id.day_thu),
            findViewById<LinearLayout>(R.id.day_fri),
            findViewById<LinearLayout>(R.id.day_sat)
        )

        selectedDayView = findViewById(R.id.day_tue)

        days.forEach { dayView ->
            dayView.setOnClickListener {
                selectDay(it)
            }
        }
    }

    private fun setupBottomNav() {
        findViewById<ImageView>(R.id.nav_today).setOnClickListener {
            Toast.makeText(this, "Сегодня", Toast.LENGTH_SHORT).show()
            updateNavTint(it as ImageView)
        }
        findViewById<ImageView>(R.id.nav_search).setOnClickListener {
            Toast.makeText(this, "Поиск", Toast.LENGTH_SHORT).show()
            updateNavTint(it as ImageView)
        }
        findViewById<ImageView>(R.id.nav_profile).setOnClickListener {
            Toast.makeText(this, "Профиль", Toast.LENGTH_SHORT).show()
            updateNavTint(it as ImageView)
        }
    }

    private fun selectDay(view: View) {
        // Reset previous selection
        selectedDayView?.let { prev ->
            prev.setBackgroundResource(R.color.ui_surface)
            val dayText = (prev as LinearLayout).getChildAt(0) as TextView
            val numText = prev.getChildAt(1) as TextView
            dayText.setTextColor(ContextCompat.getColor(this, R.color.ui_text_sub))
            numText.setTextColor(ContextCompat.getColor(this, R.color.ui_text_main))
        }

        // Apply new selection
        view.setBackgroundResource(R.drawable.bg_day_active)
        val dayText = (view as LinearLayout).getChildAt(0) as TextView
        val numText = view.getChildAt(1) as TextView
        dayText.setTextColor(Color.WHITE)
        numText.setTextColor(Color.WHITE)

        selectedDayView = view
        
        Toast.makeText(this, "Выбран день: ${dayText.text}", Toast.LENGTH_SHORT).show()
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