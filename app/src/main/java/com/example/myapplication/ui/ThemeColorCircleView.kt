package com.example.myapplication.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.myapplication.R

class ThemeColorCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var outerColor: Int = Color.BLUE
    private var innerColor: Int = Color.CYAN
    private var isColorSelected: Boolean = false

    private val checkIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_check)

    fun setColorData(outer: Int, inner: Int, isSelected: Boolean) {
        this.outerColor = outer
        this.innerColor = inner
        this.isColorSelected = isSelected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(cx, cy) - 4f

        if (isColorSelected) {
            // КОГДА ВЫБРАН:
            // 1. Закрашиваем весь большой круг главным акцентным цветом
            outerPaint.color = innerColor
            canvas.drawCircle(cx, cy, radius, outerPaint)

            // 2. Рисуем белую галочку по центру
            checkIcon?.let { icon ->
                val iconSize = (radius * 0.9f).toInt()
                val left = (cx - iconSize / 2).toInt()
                val top = (cy - iconSize / 2).toInt()
                icon.setBounds(left, top, left + iconSize, top + iconSize)
                icon.draw(canvas)
            }
        } else {
            // КОГДА НЕ ВЫБРАН (в стиле Telegram):
            // 1. Внешний светлый круг
            outerPaint.color = outerColor
            canvas.drawCircle(cx, cy, radius, outerPaint)

            // 2. Маленький внутренний кружок
            innerPaint.color = innerColor
            canvas.drawCircle(cx, cy, radius * 0.45f, innerPaint)
        }
    }
}