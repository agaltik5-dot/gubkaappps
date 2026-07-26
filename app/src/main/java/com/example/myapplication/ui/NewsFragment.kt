package com.example.myapplication.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.myapplication.R

class NewsFragment : Fragment(R.layout.fragment_news) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Здесь будет логика загрузки новостей из JSON
    }
}
