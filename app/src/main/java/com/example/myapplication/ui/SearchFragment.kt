package com.example.myapplication.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.RecentSelection
import com.example.myapplication.data.ScheduleSyncHelper
import com.example.myapplication.data.SelectionType
import com.example.myapplication.data.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SearchFragment : Fragment(R.layout.fragment_search) {

    private var currentTabIndex: Int = 0

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tabIndicator: View
    private lateinit var tabFaculties: TextView
    private lateinit var tabTeachers: TextView
    private lateinit var tabRooms: TextView
    private lateinit var tabContainer: FrameLayout
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private val urls = listOf(
        "https://lk.gubkin.ru/schedule/#/activities/faculties",
        "https://lk.gubkin.ru/schedule/#/activities/teachers",
        "https://lk.gubkin.ru/schedule/#/activities/rooms"
    )

    private var isUnifiedViewActive = false
    private var syncTriggered = false

    inner class ScheduleBridge {
        @android.webkit.JavascriptInterface
        fun onUrlChanged(url: String) {
            requireActivity().runOnUiThread {
                if (url == "exit") {
                    isUnifiedViewActive = false
                    syncTriggered = false
                    webView.loadUrl(urls[currentTabIndex])
                } else {
                    checkIfCaptchaPassed(url)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webView = view.findViewById(R.id.webview_schedule)
        progressBar = view.findViewById(R.id.progress_loader)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        tabIndicator = view.findViewById(R.id.tab_indicator)
        tabFaculties = view.findViewById(R.id.tab_faculties)
        tabTeachers = view.findViewById(R.id.tab_teachers)
        tabRooms = view.findViewById(R.id.tab_rooms)
        tabContainer = view.findViewById(R.id.tab_container)

        setupWebView()
        setupSwipeRefresh()
        setupCustomTabs()
        applyThemeColor()
        setupBackPress()
        setupScrollSync()

        if (savedInstanceState == null) {
            selectTab(0)
        }
    }

    private fun setupScrollSync() {
        // Исправляем баг: SwipeRefresh должен работать только когда WebView в самом верху
        webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            swipeRefresh.isEnabled = scrollY == 0
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            if (isUnifiedViewActive) {
                syncTriggered = false
                fetchUnifiedSchedule()
            } else {
                val cookie = CookieManager.getInstance().getCookie("https://lk.gubkin.ru")
                if (ScheduleSyncHelper.isScheduleSessionReady(webView.url, cookie)) {
                    syncTriggered = false
                    fetchUnifiedSchedule(webView.url)
                } else {
                    webView.reload()
                }
            }
        }
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val activeColor = (prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7").toColorInt()
        swipeRefresh.setColorSchemeColors(activeColor)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
        }

        webView.addJavascriptInterface(ScheduleBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (!swipeRefresh.isRefreshing) {
                    progressBar.visibility = View.VISIBLE
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                injectCustomCss()
                injectNavigationWatcher()
                checkIfCaptchaPassed(url)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun injectCustomCss() {
        val bgColor = try {
            val color = ContextCompat.getColor(requireContext(), R.color.ui_bg)
            String.format("#%06X", (0xFFFFFF and color))
        } catch (e: Exception) {
            "#F5F5F5"
        }

        val js = """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = `
                    /* Hide almost everything that looks like a frame/header/nav */
                    header, footer, nav, 
                    .navbar, .navbar-fixed-top, .navbar-static-top,
                    .site-header, .page-header, .header-wrapper,
                    .university-logo, .university-name, .logo, [class*="logo"], [id*="logo"],
                    .breadcrumb, .nav-tabs, .nav-pills, .nav-sidebar,
                    .schedule-nav, .schedule-header, .btn-group-toggle,
                    .top-menu, .main-menu, #header, #footer {
                        display: none !important;
                    }
                    
                    /* Hide titles */
                    h1, h2, h3, .title, .page-title {
                        display: none !important;
                    }

                    body {
                        background-color: $bgColor !important;
                        padding-top: 0 !important;
                        margin: 0 !important;
                    }
                    
                    /* Expand content to fill space */
                    .content-wrapper, .main-content, main, #content, .container, .container-fluid {
                        margin-top: 0 !important;
                        padding-top: 0 !important;
                        background-color: transparent !important;
                        width: 100% !important;
                        max-width: 100% !important;
                    }
                `;
                document.head.appendChild(style);

                var cleanup = function() {
                    // 1. Hide by typical class names
                    var selectors = [
                        'header', 'footer', 'nav', '.navbar', '.site-header', 
                        '.university-logo', '.university-name', '.logo',
                        '.nav-tabs', '.breadcrumb', '.page-header'
                    ];
                    selectors.forEach(function(s) {
                        document.querySelectorAll(s).forEach(function(el) {
                            el.style.setProperty('display', 'none', 'important');
                        });
                    });

                    // 2. Hide by text content (Tabs and Titles)
                    var keywords = ['расписание', 'факультеты', 'преподаватели', 'аудитории', 'губкин'];
                    document.querySelectorAll('a, button, span, div, h1, h2').forEach(function(el) {
                        if (el.children.length > 2) return; // Don't hide containers
                        
                        var txt = (el.innerText || el.textContent || '').toLowerCase().trim();
                        if (keywords.some(k => txt === k || txt === k + ' занятий')) {
                            el.style.setProperty('display', 'none', 'important');
                            
                            // If it's inside a list item or button group, hide the parent too
                            if (el.parentElement && (el.parentElement.tagName === 'LI' || el.parentElement.classList.contains('btn-group'))) {
                                el.parentElement.style.setProperty('display', 'none', 'important');
                            }
                        }
                    });

                    // 3. Hide any fixed/absolute headers at the top
                    document.querySelectorAll('*').forEach(function(el) {
                        var style = window.getComputedStyle(el);
                        if (style.position === 'fixed' || style.position === 'sticky') {
                            var rect = el.getBoundingClientRect();
                            if (rect.top < 100 && rect.height < 150) {
                                el.style.setProperty('display', 'none', 'important');
                            }
                        }
                    });
                };

                cleanup();
                var observer = new MutationObserver(cleanup);
                observer.observe(document.body, { childList: true, subtree: true });
                
                // Extra run after a short delay for SPA content
                setTimeout(cleanup, 500);
                setTimeout(cleanup, 1500);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectNavigationWatcher() {
        val js = """
            (function() {
                if (window.__gubkinNavHook) return;
                window.__gubkinNavHook = true;
                var notify = function() {
                    if (window.AndroidBridge) {
                        AndroidBridge.onUrlChanged(window.location.href);
                    }
                };
                window.addEventListener('hashchange', notify);
                var pushState = history.pushState;
                history.pushState = function() {
                    pushState.apply(history, arguments);
                    notify();
                };
                var replaceState = history.replaceState;
                history.replaceState = function() {
                    replaceState.apply(history, arguments);
                    notify();
                };
                notify();
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun setupBackPress() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isUnifiedViewActive) {
                    isUnifiedViewActive = false
                    syncTriggered = false
                    webView.loadUrl(urls[currentTabIndex])
                    return
                }
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun isAutoSyncReady(url: String?): Boolean {
        Log.d("ScheduleSync", "Checking URL: $url")
        if (url.isNullOrBlank()) return false
        
        // Расширенное обнаружение: срабатывает при наличии любого ID выбора в URL
        return url.contains("/schedule/") && 
               (url.contains("/lessons") || url.contains("groups/") || url.contains("groupId=") ||
                url.contains("teachers/") || url.contains("teacherId=") ||
                url.contains("rooms/") || url.contains("roomId="))
    }

    private fun checkIfCaptchaPassed(url: String?) {
        if (url == null || isUnifiedViewActive || syncTriggered) {
            Log.d("ScheduleSync", "Skip check: url=$url, active=$isUnifiedViewActive, triggered=$syncTriggered")
            return
        }
        
        if (!isAutoSyncReady(url)) return

        syncTriggered = true
        Log.d("ScheduleSync", "Captcha likely passed, triggering sync...")
        Toast.makeText(requireContext(), "🔄 Начинаю синхронизацию расписания...", Toast.LENGTH_LONG).show()
        fetchUnifiedSchedule(url)
    }

    private fun fetchUnifiedSchedule(pageUrl: String? = null) {
        Log.d("ScheduleSync", "fetchUnifiedSchedule started for URL: $pageUrl")
        showSyncNotification("Синхронизация расписания...", "Загрузка данных для групп...")
        
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        val mainGroupId = prefs.getInt("key_group_id", -1)
        val mainGroupName = prefs.getString("key_group_code", "Основная") ?: "Основная"

        val recentJson = prefs.getString("key_recent_list_json", "[]") ?: "[]"
        val recentList = mutableListOf<RecentSelection>()
        try {
            val arr = JSONArray(recentJson)
            for (i in 0 until arr.length()) {
                recentList.add(RecentSelection.fromJson(arr.getString(i)))
            }
        } catch (_: Exception) {}

        val groupsToFetch = linkedMapOf<Int, String>()
        ScheduleSyncHelper.parseGroupIdFromUrl(pageUrl)?.let { webGroupId ->
            groupsToFetch[webGroupId] = "С сайта"
        }
        if (mainGroupId != -1) groupsToFetch.putIfAbsent(mainGroupId, mainGroupName)
        recentList.filter { it.type == SelectionType.GROUP }
            .take(5)
            .forEach { groupsToFetch.putIfAbsent(it.id, it.name) }

        if (groupsToFetch.isEmpty()) {
            syncTriggered = false
            Toast.makeText(requireContext(), "Сначала выберите группу в профиле", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            swipeRefresh.isRefreshing = false
            return
        }

        isUnifiedViewActive = true
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val client = NetworkUtils.getUnsafeOkHttpClient()
            CookieManager.getInstance().flush()
            val cookie = CookieManager.getInstance().getCookie("https://lk.gubkin.ru")
            
            if (cookie.isNullOrBlank() || !cookie.contains("PHPSESSID")) {
                Toast.makeText(requireContext(), "Сессия не найдена. Попробуйте обновить страницу в браузере", Toast.LENGTH_LONG).show()
                isUnifiedViewActive = false
                syncTriggered = false
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                return@launch
            }

            val userAgent = webView.settings.userAgentString

            val results = withContext(Dispatchers.IO) {
                // Принудительно очищаем кэш перед синхронизацией
                if (syncTriggered) {
                    groupsToFetch.keys.forEach { id ->
                        val cal = Calendar.getInstance()
                        repeat(2) {
                            val weekKey = ScheduleSyncHelper.getWeekKey(cal)
                            val file = File(requireContext().filesDir, "cache/week_${id}_$weekKey.json")
                            if (file.exists()) {
                                Log.d("ScheduleSync", "Deleting old cache: ${file.name}")
                                file.delete()
                            }
                            cal.add(Calendar.WEEK_OF_YEAR, 1)
                        }
                    }
                }

                groupsToFetch.map { (id, name) ->
                    Log.d("ScheduleSync", "Fetching for group: $name ($id)")
                    async {
                        val savedWeeks = ScheduleSyncHelper.syncGroupWeeks(
                            requireContext().applicationContext,
                            client,
                            id,
                            cookie,
                            userAgent
                        )
                        val scheduleData = fetchScheduleForDisplay(client, id, cookie, userAgent)
                        Log.d("ScheduleSync", "Group $name: saved $savedWeeks weeks, display data size: ${scheduleData.size}")
                        SyncResult(name, id, savedWeeks, scheduleData)
                    }
                }.awaitAll()
            }

            val totalSaved = results.sumOf { it.savedWeeks }
            Log.d("ScheduleSync", "Sync finished. Total saved weeks: $totalSaved")
            
            if (totalSaved > 0) {
                ScheduleSyncHelper.bumpSyncVersion(requireContext())
                showSyncNotification("Синхронизация завершена", "Загружено $totalSaved недель")
                Toast.makeText(
                    requireContext(),
                    "✅ Успешно! Загружено $totalSaved недель расписания для ${results.size} групп",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Log.e("ScheduleSync", "Sync failed: no weeks saved")
                showSyncNotification("Ошибка синхронизации", "Не удалось загрузить данные")
                Toast.makeText(
                    requireContext(),
                    "❌ Не удалось загрузить расписание. Проверьте капчу или интернет",
                    Toast.LENGTH_LONG
                ).show()
                syncTriggered = false
                isUnifiedViewActive = false
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                return@launch
            }

            val htmlResults = results.map { it.name to it.weeksForDisplay }
            val html = generateUnifiedHtml(htmlResults)
            webView.loadDataWithBaseURL("https://lk.gubkin.ru", html, "text/html", "UTF-8", null)
            progressBar.visibility = View.GONE
            swipeRefresh.isRefreshing = false
        }
    }

    private data class SyncResult(
        val name: String,
        val groupId: Int,
        val savedWeeks: Int,
        val weeksForDisplay: List<JSONObject>
    )

    private fun fetchScheduleForDisplay(
        client: okhttp3.OkHttpClient,
        groupId: Int,
        cookie: String?,
        userAgent: String
    ): List<JSONObject> {
        val schedules = mutableListOf<JSONObject>()
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        
        repeat(2) {
            val data = ScheduleSyncHelper.fetchOneWeek(client, groupId, cal, cookie, userAgent)
            if (data.optBoolean("state", false)) schedules.add(data)
            cal.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return schedules
    }

    private fun generateUnifiedHtml(results: List<Pair<String, List<JSONObject>>>): String {
        val sb = StringBuilder()
        val accentColor = try {
            val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7"
        } catch (e: Exception) { "#4FC3F7" }

        val bgColor = "#121212"
        val textColor = "#FFFFFF"
        val subTextColor = "#AAAAAA"
        
        val now = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)

        sb.append("""
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { background-color: $bgColor; color: $textColor; font-family: sans-serif; margin: 0; padding: 16px; padding-bottom: 80px; }
                    .header-info { font-size: 12px; color: $subTextColor; margin-bottom: 16px; text-align: right; }
                    .group-section { margin-bottom: 32px; border-left: 4px solid $accentColor; padding-left: 12px; }
                    .group-title { font-size: 22px; font-weight: bold; margin-bottom: 12px; color: $accentColor; }
                    .week-divider { font-size: 14px; font-weight: bold; color: $subTextColor; margin: 20px 0 10px 0; text-transform: uppercase; letter-spacing: 1px; }
                    .day-box { margin-bottom: 16px; background: #1E1E1E; padding: 12px; border-radius: 8px; }
                    .day-title { font-size: 16px; font-weight: bold; margin-bottom: 8px; border-bottom: 1px solid #333; padding-bottom: 4px; }
                    .lesson { margin-bottom: 8px; padding-bottom: 8px; border-bottom: 1px dashed #333; }
                    .lesson:last-child { border-bottom: none; }
                    .time { color: $accentColor; font-weight: bold; font-size: 14px; }
                    .subject { font-size: 15px; margin-top: 2px; }
                    .info { color: $subTextColor; font-size: 13px; margin-top: 2px; }
                    .empty { color: #666; font-style: italic; font-size: 13px; }
                    .btn-row { margin-bottom: 20px; display: flex; gap: 10px; }
                    .btn { display: inline-block; padding: 10px 20px; background: $accentColor; color: #000; border-radius: 8px; text-decoration: none; font-weight: bold; font-size: 14px; flex: 1; text-align: center; }
                    .btn-secondary { background: #333; color: #FFF; }
                </style>
            </head>
            <body>
                <div class="header-info">Обновлено: $now</div>
                <div class="btn-row">
                    <a href="javascript:location.reload()" class="btn">Обновить</a>
                    <a href="javascript:AndroidBridge.onUrlChanged('exit')" class="btn btn-secondary">В браузер</a>
                </div>
        """.trimIndent())

        results.forEach { (groupName, weeks) ->
            sb.append("<div class='group-section'>")
            sb.append("<div class='group-title'>$groupName</div>")
            
            weeks.forEachIndexed { weekIdx, weekJson ->
                sb.append("<div class='week-divider'>Неделя ${weekIdx + 1}</div>")
                val moscow = weekJson.optJSONObject("rows")?.optJSONArray("organizations")?.let { orgs ->
                    for (i in 0 until orgs.length()) {
                        if (orgs.getJSONObject(i).optString("name") == "Москва") return@let orgs.getJSONObject(i)
                    }
                    null
                }

                if (moscow != null) {
                    val lessons = moscow.optJSONArray("lessons")
                    val timeChunks = moscow.optJSONArray("lessonsTimeChunks")
                    
                    if (lessons != null && lessons.length() > 0) {
                        // Группируем по дням
                        val days = mutableMapOf<Int, MutableList<JSONObject>>()
                        for (i in 0 until lessons.length()) {
                            val lesson = lessons.getJSONObject(i)
                            val dayNum = lesson.optInt("weekDayNumber")
                            days.getOrPut(dayNum) { mutableListOf() }.add(lesson)
                        }

                        val dayNames = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
                        for (dayIdx in 0..6) {
                            val dayLessons = days[dayIdx] ?: continue
                            sb.append("<div class='day-box'>")
                            sb.append("<div class='day-title'>${dayNames[dayIdx]}</div>")
                            
                            dayLessons.sortedBy { it.optJSONArray("timeChunks")?.optInt(0) ?: 0 }.forEach { lesson ->
                                val chunks = lesson.optJSONArray("timeChunks")
                                val startTime = timeChunks?.optString(chunks?.optInt(0) ?: -1)?.split("-")?.getOrNull(0) ?: ""
                                val endTime = timeChunks?.optString(chunks?.optInt((chunks?.length() ?: 1) - 1) ?: -1)?.split("-")?.getOrNull(1) ?: ""
                                val subject = lesson.optJSONObject("course")?.optString("name") ?: "—"
                                val type = lesson.optString("type", "")
                                val room = lesson.optJSONArray("rooms")?.optJSONObject(0)?.optString("number") ?: ""
                                val teacher = lesson.optJSONArray("teachers")?.optJSONObject(0)?.let { 
                                    "${it.optString("lastName")} ${it.optString("firstName").take(1)}.${it.optString("patronymic").take(1)}."
                                } ?: ""

                                sb.append("<div class='lesson'>")
                                sb.append("<div class='time'>$startTime - $endTime</div>")
                                sb.append("<div class='subject'>$subject ($type)</div>")
                                sb.append("<div class='info'>Ауд. $room | $teacher</div>")
                                sb.append("</div>")
                            }
                            sb.append("</div>")
                        }
                    } else {
                        sb.append("<div class='empty'>Занятий нет</div>")
                    }
                }
            }
            sb.append("</div>")
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun setupCustomTabs() {
        val tabs = listOf(tabFaculties, tabTeachers, tabRooms)
        tabIndicator.post {
            val containerWidth = tabContainer.width
            if (containerWidth == 0) return@post
            val tabWidth = containerWidth / 3
            val indicatorWidth = tabWidth / 2 
            val params = tabIndicator.layoutParams
            params.width = indicatorWidth
            tabIndicator.layoutParams = params
            tabIndicator.translationX = (currentTabIndex * tabWidth + (tabWidth - indicatorWidth) / 2).toFloat()
        }
        tabs.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                if (currentTabIndex != index) {
                    selectTab(index)
                }
            }
        }
    }

    private fun selectTab(index: Int) {
        currentTabIndex = index
        val containerWidth = tabContainer.width
        if (containerWidth == 0) {
            webView.loadUrl(urls[index])
            return
        }
        val tabWidth = containerWidth / 3
        val indicatorWidth = tabWidth / 2
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val activeColor = (prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7").toColorInt()
        tabIndicator.animate()
            .translationX((index * tabWidth + (tabWidth - indicatorWidth) / 2).toFloat())
            .setDuration(300)
            .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f))
            .start()
        val tabs = listOf(tabFaculties, tabTeachers, tabRooms)
        tabs.forEachIndexed { i, tv ->
            tv.setTextColor(if (i == index) activeColor else ContextCompat.getColor(requireContext(), R.color.ui_text_sub))
        }
        isUnifiedViewActive = false
        syncTriggered = false
        webView.loadUrl(urls[index])
    }

    private fun applyThemeColor() {
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val accentColorHex = prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7"
        val activeColor = accentColorHex.toColorInt()
        val bgIndicator = tabIndicator.background as? android.graphics.drawable.GradientDrawable
        bgIndicator?.setColor(activeColor)
        val tabs = listOf(tabFaculties, tabTeachers, tabRooms)
        tabs.forEachIndexed { i, tv ->
            if (i == currentTabIndex) tv.setTextColor(activeColor)
        }
    }

    override fun onResume() {
        super.onResume()
        applyThemeColor()
    }

    private fun showSyncNotification(title: String, text: String) {
        val context = context ?: return
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = NotificationCompat.Builder(context, "sync_channel")
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            
            notificationManager.notify(1001, builder.build())
        } catch (e: Exception) {
            Log.e("ScheduleSync", "Failed to show notification", e)
        }
    }
}
