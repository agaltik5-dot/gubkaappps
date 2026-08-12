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
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.example.myapplication.R

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

        if (savedInstanceState == null) {
            selectTab(0)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
        val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val activeColor = (prefs.getString("accent_color", "#4FC3F7") ?: "#4FC3F7").toColorInt()
        swipeRefresh.setColorSchemeColors(activeColor)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
        }

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

    private fun setupBackPress() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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
}
