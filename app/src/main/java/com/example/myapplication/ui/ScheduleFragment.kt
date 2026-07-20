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

    // Ссылка на выбранный элемент дня
    private var selectedDayView: View? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация компонентов
        setupScheduleList(view)
        setupDaySelector(view)
    }

    private fun setupScheduleList(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_schedule)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Тестовый список занятий
        val sampleLessons = listOf(
            Lesson(
                subject = "Люблю ДИДИЕНКО",
                startTime = "08:00",
                endTime = "09:30",
                type = "Лекция",
                details = "А-201 · Соколов А.В.",
                duration = "24"
            ),
            Lesson(
                subject = "Роман вы ЛЕОНИЛИ ПЕПСИ",
                startTime = "10:00",
                endTime = "11:30",
                type = "Лабораторная",
                details = "В-305 · Иванов К.Е.",
                duration = "24"
            )
        )

        val adapter = LessonAdapter(sampleLessons)
        recyclerView.adapter = adapter
    }

    private fun setupDaySelector(view: View) {
        // Указываем R.id.название для каждого элемента
        val days: List<LinearLayout?> = listOf(
            view.findViewById(R.id.day_mon),
            view.findViewById(R.id.day_tue),
            view.findViewById(R.id.day_wed),
            view.findViewById(R.id.day_thu),
            view.findViewById(R.id.day_fri),
            view.findViewById(R.id.day_sat)
        )

        // По умолчанию выбираем вторник
        val defaultDay = view.findViewById<LinearLayout>(R.id.day_tue)
        selectedDayView = defaultDay

        // Назначаем обработчик нажатий
        days.forEach { dayView ->
            dayView?.setOnClickListener {
                selectDay(it)
            }
        }
    }

    private fun selectDay(view: View) {
        val context = requireContext()

        // Снимаем выделение с предыдущего дня
        selectedDayView?.let { prev ->
            prev.setBackgroundResource(R.color.ui_surface)
            val dayText = (prev as LinearLayout).getChildAt(0) as TextView
            val numText = prev.getChildAt(1) as TextView
            dayText.setTextColor(ContextCompat.getColor(context, R.color.ui_text_sub))
            numText.setTextColor(ContextCompat.getColor(context, R.color.ui_text_main))
        }

        // Выделяем новый день
        view.setBackgroundResource(R.drawable.bg_day_active)
        val dayText = (view as LinearLayout).getChildAt(0) as TextView
        val numText = view.getChildAt(1) as TextView
        dayText.setTextColor(Color.WHITE)
        numText.setTextColor(Color.WHITE)

        selectedDayView = view

        Toast.makeText(context, "Выбран день: ${dayText.text}", Toast.LENGTH_SHORT).show()
    }
}