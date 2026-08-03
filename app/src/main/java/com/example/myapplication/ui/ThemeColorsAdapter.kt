package com.example.myapplication.ui

import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView

data class TelegramColor(
    val hexOuter: String,
    val hexInner: String
)

class ThemeColorsAdapter(
    private val colors: List<TelegramColor>,
    private var selectedColorHex: String,
    private val onColorSelected: (TelegramColor) -> Unit
) : RecyclerView.Adapter<ThemeColorsAdapter.ColorViewHolder>() {

    inner class ColorViewHolder(val colorView: ThemeColorCircleView) : RecyclerView.ViewHolder(colorView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val size = (48 * parent.context.resources.displayMetrics.density).toInt()
        val margin = (6 * parent.context.resources.displayMetrics.density).toInt()

        val view = ThemeColorCircleView(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                setMargins(margin, margin, margin, margin)
            }
        }
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        val item = colors[position]
        val isSelected = item.hexInner.equals(selectedColorHex, ignoreCase = true)

        holder.colorView.setColorData(
            outer = item.hexOuter.toColorInt(),
            inner = item.hexInner.toColorInt(),
            isSelected = isSelected
        )

        holder.colorView.setOnClickListener {
            selectedColorHex = item.hexInner
            notifyDataSetChanged()
            onColorSelected(item)
        }
    }

    override fun getItemCount(): Int = colors.size
}