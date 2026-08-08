package com.example.myapplication.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.data.NewsItem

/**
 * Адаптер для отображения списка новостей.
 */
class NewsAdapter(
    private val newsList: List<NewsItem>,
    private val onNewsClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    class NewsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.iv_news_image)
        val tvDate: TextView = view.findViewById(R.id.tv_news_date)
        val tvTitle: TextView = view.findViewById(R.id.tv_news_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_news, parent, false)
        return NewsViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val news = newsList[position]
        
        holder.tvTitle.text = news.title
        holder.tvDate.text = news.date
        
        // Загрузка изображения через Glide
        Glide.with(holder.itemView.context)
            .load(news.imageUrl)
            .placeholder(R.drawable.gubkin_logo)
            .error(R.drawable.gubkin_logo)
            .centerCrop()
            .into(holder.ivImage)
            
        holder.itemView.setOnClickListener { onNewsClick(news) }
    }

    override fun getItemCount(): Int = newsList.size
}
