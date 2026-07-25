package com.example.myapplication.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// 1. Правильный импорт R из вашего приложения
import com.example.myapplication.R

// 2. Правильный импорт Lesson и LessonAdapter (они лежат в com.example.myapplication)
import com.example.myapplication.Lesson
import com.example.myapplication.LessonAdapter

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    // Ссылка на ViewPager2
    private var viewPager: androidx.viewpager2.widget.ViewPager2? = null
    
    // Ссылка на выбранный элемент дня
    private var selectedDayView: View? = null
    private var dayViews: List<LinearLayout?> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация компонентов
        setupMonthNavigation(view)
        setupDaySelector(view)
        setupViewPager(view)
    }

    private fun setupMonthNavigation(view: View) {
        val tvMonthYear = view.findViewById<TextView>(R.id.tv_month_year)
        val btnPrev = view.findViewById<View>(R.id.btn_prev_week)
        val btnNext = view.findViewById<View>(R.id.btn_next_week)

        tvMonthYear.text = "Июль 2026"

        btnPrev.setOnClickListener {
            // Логика переключения на предыдущую неделю
        }

        btnNext.setOnClickListener {
            // Логика переключения на следующую неделю
        }
    }

    private fun setupViewPager(view: View) {
        viewPager = view.findViewById(R.id.vp_schedule)
        
        // Подготовка данных для всей недели (7 списков)
        val weeklyData = listOf(
            createSampleLessons("ПН"),
            createSampleLessons("ВТ"),
            createSampleLessons("СР"),
            createSampleLessons("ЧТ"),
            createSampleLessons("ПТ"),
            createSampleLessons("СБ"),
            createSampleLessons("ВС")
        )

        val adapter = DailyScheduleAdapter(weeklyData)
        viewPager?.adapter = adapter

        // Синхронизация: свайп ViewPager -> выбор кнопки наверху
        viewPager?.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                dayViews[position]?.let { selectDayVisuals(it) }
            }
        })

        // По умолчанию переходим на Вторник (индекс 1)
        viewPager?.setCurrentItem(1, false)
    }

    private fun createSampleLessons(dayName: String): List<Lesson> {
        return listOf(
            Lesson(
                subject = "Предмет в $dayName",
                startTime = "08:00",
                endTime = "09:30",
                type = "Лекция",
                details = "А-201 · Преподаватель",
                duration = "1.5ч"
            ),
            Lesson(
                subject = "Еще пара ($dayName)",
                startTime = "10:00",
                endTime = "11:30",
                type = "Практика",
                details = "Б-305 · Соколов А.В.",
                duration = "1.5ч"
            )
        )
    }

    private fun setupDaySelector(view: View) {
        dayViews = listOf(
            view.findViewById(R.id.day_mon),
            view.findViewById(R.id.day_tue),
            view.findViewById(R.id.day_wed),
            view.findViewById(R.id.day_thu),
            view.findViewById(R.id.day_fri),
            view.findViewById(R.id.day_sat),
            view.findViewById(R.id.day_sun)
        )

        // Назначаем обработчик нажатий
        dayViews.forEachIndexed { index, dayView ->
            dayView?.setOnClickListener {
                // При клике на кнопку — листаем ViewPager к нужному дню
                viewPager?.currentItem = index
            }
        }
    }

    /**
     * Только визуальное обновление кнопок дней
     */
    private fun selectDayVisuals(view: View) {
        val context = requireContext()

        // Снимаем выделение с предыдущего дня
        selectedDayView?.let { prev ->
            prev.setBackgroundResource(R.drawable.bg_day_inactive)
            val dayText = (prev as LinearLayout).getChildAt(0) as TextView
            val numText = prev.getChildAt(1) as TextView
            dayText.setTextColor(ContextCompat.getColor(context, R.color.ui_text_sub))
            numText.setTextColor(ContextCompat.getColor(context, R.color.ui_text_main))
            prev.elevation = 0f
        }

        // Выделяем новый день
        view.setBackgroundResource(R.drawable.bg_day_active)
        val dayText = (view as LinearLayout).getChildAt(0) as TextView
        val numText = view.getChildAt(1) as TextView
        dayText.setTextColor(Color.WHITE)
        numText.setTextColor(Color.WHITE)
        view.elevation = 8f

        selectedDayView = view
    }

    private fun selectDay(view: View) {
        // Этот метод теперь вызывается косвенно через ViewPager или клик
        val index = dayViews.indexOf(view as LinearLayout)
        if (index != -1) {
            viewPager?.currentItem = index
        }
    }
}