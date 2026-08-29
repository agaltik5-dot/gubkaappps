package com.example.myapplication.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.example.myapplication.R
import com.example.myapplication.data.NetworkUtils
import com.example.myapplication.data.NewsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.io.InputStream

class NewsFragment : Fragment(R.layout.fragment_news) {

    private lateinit var rvNews: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // !!! КРИТИЧЕСКИЙ ФИКС: Ручная регистрация "небезопасного" OkHttp клиента для Glide !!!
        // Это гарантирует, что Glide будет игнорировать ошибки SSL сертификатов для картинок.
        Glide.get(requireContext()).registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(NetworkUtils.getUnsafeOkHttpClient())
        )

        rvNews = view.findViewById(R.id.rv_news)
        progressBar = view.findViewById(R.id.pb_news_loading)
        tvError = view.findViewById(R.id.tv_news_error)

        rvNews.layoutManager = LinearLayoutManager(requireContext())

        loadNews()
    }

    private fun loadNews() {
        progressBar.visibility = View.VISIBLE
        tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val news = fetchNewsFromWebsite()
                if (news.isNotEmpty()) {
                    rvNews.adapter = NewsAdapter(news) { item ->
                        openUrl(item.detailUrl)
                    }
                } else {
                    tvError.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tvError.visibility = View.VISIBLE
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun fetchNewsFromWebsite(): List<NewsItem> = withContext(Dispatchers.IO) {
        val newsItems = mutableListOf<NewsItem>()
        try {
            val url = "https://www.gubkin.ru/news/"

            val client = NetworkUtils.getUnsafeOkHttpClient()
            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            // Парсим полученный HTML через Jsoup
            val doc: Document = Jsoup.parse(html, "https://www.gubkin.ru")

            // Ищем блоки новостей
            val elements: Elements = doc.select(".b-news-item")

            for (element in elements) {
                val title = element.select(".b-news-item__title").text()
                val date = element.select(".a-news-item__date").text()
                
                // Извлекаем ссылку на картинку (более надежный метод)
                val pictureDiv = element.select(".b-news-item__picture")
                val styleAttr = pictureDiv.attr("style")
                val imageUrl = if (styleAttr.contains("url(")) {
                    val path = styleAttr.substringAfter("url(").substringBefore(")")
                        .replace("'", "").replace("\"", "")
                    if (path.startsWith("http")) path else "https://www.gubkin.ru$path"
                } else ""

                // Ссылка на подробности
                val detailLink = element.select("a.b-news-item__picture-container").attr("href")
                val detailUrl = if (detailLink.startsWith("http")) detailLink else "https://www.gubkin.ru$detailLink"

                if (title.isNotEmpty()) {
                    newsItems.add(NewsItem(title, date, imageUrl, detailUrl))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        newsItems
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
