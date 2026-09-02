package com.veil.sbsbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.util.Patterns
import android.util.TypedValue
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.veil.sbsbrowser.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class Tab(var url: String, var title: String = "New tab")
data class Bookmark(val title: String, val url: String)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var vrMode = false
    private var darkModeOn = false
    private val defaultUrl = "https://www.google.com"

    private val tabs = mutableListOf(Tab(defaultUrl))
    private var currentTabIndex = 0

    private val bookmarks = mutableListOf<Bookmark>()
    private lateinit var prefs: android.content.SharedPreferences

    private var customVideoView: View? = null
    private var customVideoCallback: WebChromeClient.CustomViewCallback? = null
    private var pixelCopyHandler: Handler? = null
    private var pixelCopyRunnable: Runnable? = null
    private var captureBitmap: Bitmap? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("dreamland_prefs", Context.MODE_PRIVATE)
        loadBookmarks()

        binding.webView.mirror = binding.mirrorView
        binding.mirrorView.source = binding.webView

        binding.leftKeyboardHost.mirror = binding.rightKeyboardHost
        binding.rightKeyboardHost.source = binding.leftKeyboardHost

        configureWebView(binding.webView)
        setupFullscreenVideoSupport()

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    binding.urlInput.setText(it)
                    tabs[currentTabIndex].url = it
                    tabs[currentTabIndex].title = view?.title?.takeIf { t -> t.isNotBlank() } ?: it
                    renderTabs()
                    updateBookmarkStar()
                }
                updateNavButtonStates()
            }
        }

        binding.goButton.setOnClickListener { loadFromInput() }
        binding.urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || (event?.keyCode == KeyEvent.KEYCODE_ENTER)) {
                loadFromInput()
                true
            } else {
                false
            }
        }

        binding.backButton.setOnClickListener { if (binding.webView.canGoBack()) binding.webView.goBack() }
        binding.forwardButton.setOnClickListener { if (binding.webView.canGoForward()) binding.webView.goForward() }
        binding.refreshButton.setOnClickListener { binding.webView.reload() }
        binding.homeButton.setOnClickListener { binding.webView.loadUrl(defaultUrl) }

        binding.toolbarToggleButton.setOnClickListener { toggleToolbarPanel() }

        binding.bookmarkStarButton.setOnClickListener { toggleBookmarkForCurrentPage() }
        binding.bookmarksListButton.setOnClickListener { showBookmarksDialog() }
        binding.darkModeButton.setOnClickListener { toggleDarkMode() }

        binding.ipdButton.setOnClickListener {
            binding.scalePanel.visibility = View.GONE
            binding.ipdPanel.visibility = if (binding.ipdPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        binding.ipdCloseButton.setOnClickListener { binding.ipdPanel.visibility = View.GONE }
        binding.ipdSeekBar.progress = 60
        binding.ipdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                applyIpdOffset(progress - 60)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.scaleButton.setOnClickListener {
            binding.ipdPanel.visibility = View.GONE
            binding.scalePanel.visibility = if (binding.scalePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        binding.scaleCloseButton.setOnClickListener { binding.scalePanel.visibility = View.GONE }
        binding.scaleSeekBar.progress = 0
        binding.scaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                applyScaleOffset(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.keyboardButton.setOnClickListener { toggleVirtualKeyboard() }
        binding.vrToggleButton.setOnClickListener { toggleVrMode() }

        buildVirtualKeyboard()
        binding.keyboardOverlay.post {
            binding.keyboardOverlay.layoutParams.height = (resources.displayMetrics.heightPixels * 0.45).toInt()
            binding.keyboardOverlay.requestLayout()
        }

        renderTabs()
        binding.webView.loadUrl(tabs[currentTabIndex].url)
    }

    private fun configureWebView(webView: WebView) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
    }

    private fun resolveInputToUrl(raw: String): String {
        val text = raw.trim()
        if (text.startsWith("http://") || text.startsWith("https://")) return text
        if (Patterns.WEB_URL.matcher(text).matches()) return "https://$text"
        val encoded = URLEncoder.encode(text, "UTF-8")
        return "https://www.google.com/search?q=$encoded"
    }

    private fun loadFromInput() {
        val text = binding.urlInput.text.toString().trim()
        if (text.isEmpty()) return
        binding.webView.loadUrl(resolveInputToUrl(text))
        hideKeyboard()
    }

    private fun updateNavButtonStates() {
        binding.backButton.isEnabled = binding.webView.canGoBack()
        binding.forwardButton.isEnabled = binding.webView.canGoForward()
    }

    private fun hideKeyboard() {
        currentFocus?.let { view ->
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun toggleToolbarPanel() {
        val show = binding.toolbarPanel.visibility != View.VISIBLE
        binding.toolbarPanel.visibility = if (show) View.VISIBLE else View.GONE
        binding.toolbarToggleButton.text = if (show) "✕" else "☰"
    }

    private fun renderTabs() {
        binding.tabsContainer.removeAllViews()
        tabs.forEachIndexed { index, tab ->
            val chip = Button(this).apply {
                text = tab.title.take(14)
                isAllCaps = false
                minWidth = 0
                setPadding(24, 8, 24, 8)
                setBackgroundColor(if (index == currentTabIndex) 0xFF3A3A3A.toInt() else 0xFF1F1F1F.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { switchToTab(index) }
                setOnLongClickListener { confirmCloseTab(index); true }
            }
            binding.tabsContainer.addView(chip)
        }
        val addButton = Button(this).apply {
            text = "+"
            isAllCaps = false
            minWidth = 0
            setPadding(24, 8, 24, 8)
            setBackgroundColor(0xFF1F1F1F.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { addNewTab() }
        }
        binding.tabsContainer.addView(addButton)
    }

    private fun switchToTab(index: Int) {
        if (index == currentTabIndex) return
        tabs[currentTabIndex].url = binding.webView.url ?: tabs[currentTabIndex].url
        currentTabIndex = index
        binding.webView.loadUrl(tabs[currentTabIndex].url)
        renderTabs()
    }

    private fun addNewTab() {
        tabs.add(Tab(defaultUrl))
        currentTabIndex = tabs.size - 1
        binding.webView.loadUrl(defaultUrl)
        renderTabs()
    }

    private fun confirmCloseTab(index: Int) {
        if (tabs.size <= 1) return
        AlertDialog.Builder(this)
            .setTitle("Close tab")
            .setMessage("Close \"${tabs[index].title}\"?")
            .setPositiveButton("Close") { _, _ -> closeTab(index) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun closeTab(index: Int) {
        tabs.removeAt(index)
        if (currentTabIndex >= tabs.size) currentTabIndex = tabs.size - 1
        else if (index < currentTabIndex) currentTabIndex--
        binding.webView.loadUrl(tabs[currentTabIndex].url)
        renderTabs()
    }

    private fun loadBookmarks() {
        bookmarks.clear()
        val raw = prefs.getString("bookmarks", "[]") ?: "[]"
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            bookmarks.add(Bookmark(obj.getString("title"), obj.getString("url")))
        }
    }

    private fun saveBookmarks() {
        val arr = JSONArray()
        bookmarks.forEach {
            val obj = JSONObject()
            obj.put("title", it.title)
            obj.put("url", it.url)
            arr.put(obj)
        }
        prefs.edit().putString("bookmarks", arr.toString()).apply()
    }

    private fun toggleBookmarkForCurrentPage() {
        val url = binding.webView.url ?: return
        val existing = bookmarks.find { it.url == url }
        if (existing != null) {
            bookmarks.remove(existing)
        } else {
            bookmarks.add(Bookmark(binding.webView.title ?: url, url))
        }
        saveBookmarks()
        updateBookmarkStar()
    }

    private fun updateBookmarkStar() {
        val url = binding.webView.url
        val isBookmarked = bookmarks.any { it.url == url }
        binding.bookmarkStarButton.text = if (isBookmarked) "★" else "☆"
    }

    private fun showBookmarksDialog() {
        if (bookmarks.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Bookmarks").setMessage("No bookmarks yet.").setPositiveButton("OK", null).show()
            return
        }
        val titles = bookmarks.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Bookmarks")
            .setItems(titles) { _, which ->
                binding.webView.loadUrl(bookmarks[which].url)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun toggleDarkMode() {
        darkModeOn = !darkModeOn
        applyDarkMode(darkModeOn)
        binding.darkModeButton.text = if (darkModeOn) "Light" else "Dark"
    }

    private fun applyDarkMode(enabled: Boolean) {
        val settings = binding.webView.settings
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, enabled)
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            @Suppress("DEPRECATION")
            WebSettingsCompat.setForceDark(
                settings,
                if (enabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
            )
        }
    }

    private fun applyIpdOffset(offsetDp: Int) {
        val offsetPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, offsetDp.toFloat(), resources.displayMetrics
        )
        binding.leftPaneContainer.translationX = -offsetPx
        binding.rightPaneContainer.translationX = offsetPx
    }

    private fun applyScaleOffset(borderDp: Int) {
        val paddingPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, borderDp.toFloat(), resources.displayMetrics
        ).toInt()
        binding.leftPaneContainer.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        binding.rightPaneContainer.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
    }

    private fun toggleVrMode() {
        vrMode = !vrMode
        if (vrMode) {
            binding.toolbarPanel.visibility = View.GONE
            binding.toolbarToggleButton.visibility = View.GONE
            binding.toolbarToggleButton.text = "☰"
        } else {
            binding.toolbarToggleButton.visibility = View.VISIBLE
        }
        applyImmersiveMode(vrMode)
    }

    private fun applyImmersiveMode(enabled: Boolean) {
        if (enabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
            }
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    private fun setupFullscreenVideoSupport() {
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (customVideoView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customVideoView = view
                customVideoCallback = callback

                binding.leftPaneContainer.removeView(binding.webView)
                binding.leftPaneContainer.addView(
                    view,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                )

                startVideoMirrorCapture(view)
            }

            override fun onHideCustomView() {
                stopVideoMirrorCapture()
                customVideoView?.let { binding.leftPaneContainer.removeView(it) }
                binding.leftPaneContainer.addView(
                    binding.webView,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                )
                binding.mirrorView.useCapturedFrame = false
                customVideoCallback?.onCustomViewHidden()
                customVideoView = null
                customVideoCallback = null
            }
        }
    }

    private fun startVideoMirrorCapture(source: View) {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        binding.mirrorView.useCapturedFrame = true
        val handler = Handler(mainLooper)
        pixelCopyHandler = handler
        val runnable = object : Runnable {
            override fun run() {
                val v = customVideoView ?: return
                if (v.width <= 0 || v.height <= 0) {
                    handler.postDelayed(this, 33)
                    return
                }
                val bmp = captureBitmap?.takeIf { it.width == v.width && it.height == v.height }
                    ?: Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { captureBitmap = it }
                try {
                    PixelCopy.request(v, bmp, { result ->
                        if (result == PixelCopy.SUCCESS) {
                            binding.mirrorView.setCapturedFrame(bmp)
                        }
                    }, handler)
                } catch (e: Exception) {
                    // Skip this frame if the surface isn't copyable right now
                }
                handler.postDelayed(this, 33)
            }
        }
        pixelCopyRunnable = runnable
        handler.post(runnable)
    }

    private fun stopVideoMirrorCapture() {
        pixelCopyRunnable?.let { pixelCopyHandler?.removeCallbacks(it) }
        pixelCopyRunnable = null
        pixelCopyHandler = null
    }

    private fun buildVirtualKeyboard() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        fun keyButton(label: String, weight: Float, onTap: () -> Unit): Button {
            return Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF2A2A2A.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
                    setMargins(2, 2, 2, 2)
                }
                setOnClickListener { onTap() }
            }
        }

        fun newRow(): LinearLayout {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            return row
        }

        val row1 = newRow()
        "1234567890".forEach { c -> row1.addView(keyButton(c.toString(), 1f) { typeChar(c) }) }
        container.addView(row1)

        val row2 = newRow()
        "qwertyuiop".forEach { c -> row2.addView(keyButton(c.toString(), 1f) { typeChar(c) }) }
        container.addView(row2)

        val row3 = newRow()
        "asdfghjkl".forEach { c -> row3.addView(keyButton(c.toString(), 1f) { typeChar(c) }) }
        container.addView(row3)

        val row4 = newRow()
        "zxcvbnm".forEach { c -> row4.addView(keyButton(c.toString(), 1f) { typeChar(c) }) }
        row4.addView(keyButton("⌫", 1.5f) { typeBackspace() })
        container.addView(row4)

        val row5 = newRow()
        row5.addView(keyButton(".", 1f) { typeChar('.') })
        row5.addView(keyButton("/", 1f) { typeChar('/') })
        row5.addView(keyButton("space", 4f) { typeSpace() })
        row5.addView(keyButton(":", 1f) { typeChar(':') })
        row5.addView(keyButton("Go", 1.5f) { typeEnter() })
        row5.addView(keyButton("✕", 1.5f) { hideVirtualKeyboard() })
        container.addView(row5)

        binding.leftKeyboardHost.removeAllViews()
        binding.leftKeyboardHost.addView(container)
    }

    private fun toggleVirtualKeyboard() {
        if (binding.keyboardOverlay.visibility == View.VISIBLE) hideVirtualKeyboard() else showVirtualKeyboard()
    }

    private fun showVirtualKeyboard() {
        binding.keyboardOverlay.visibility = View.VISIBLE
    }

    private fun hideVirtualKeyboard() {
        binding.keyboardOverlay.visibility = View.GONE
    }

    private fun typeChar(c: Char) {
        if (binding.urlInput.hasFocus()) {
            val start = binding.urlInput.selectionStart.coerceAtLeast(0)
            val end = binding.urlInput.selectionEnd.coerceAtLeast(0)
            binding.urlInput.text.replace(minOf(start, end), maxOf(start, end), c.toString())
        } else {
            val keyCode = charToKeyCode(c) ?: return
            dispatchKeyToWebView(keyCode)
        }
    }

    private fun typeSpace() {
        if (binding.urlInput.hasFocus()) {
            val start = binding.urlInput.selectionStart.coerceAtLeast(0)
            val end = binding.urlInput.selectionEnd.coerceAtLeast(0)
            binding.urlInput.text.replace(minOf(start, end), maxOf(start, end), " ")
        } else {
            dispatchKeyToWebView(KeyEvent.KEYCODE_SPACE)
        }
    }

    private fun typeBackspace() {
        if (binding.urlInput.hasFocus()) {
            val start = binding.urlInput.selectionStart
            if (start > 0) binding.urlInput.text.delete(start - 1, start)
        } else {
            dispatchKeyToWebView(KeyEvent.KEYCODE_DEL)
        }
    }

    private fun typeEnter() {
        if (binding.urlInput.hasFocus()) {
            loadFromInput()
        } else {
            dispatchKeyToWebView(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun dispatchKeyToWebView(keyCode: Int) {
        val eventTime = android.os.SystemClock.uptimeMillis()
        binding.webView.dispatchKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        binding.webView.dispatchKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun charToKeyCode(c: Char): Int? {
        return when {
            c in 'a'..'z' -> KeyEvent.KEYCODE_A + (c - 'a')
            c in '0'..'9' -> KeyEvent.KEYCODE_0 + (c - '0')
            c == '.' -> KeyEvent.KEYCODE_PERIOD
            c == '/' -> KeyEvent.KEYCODE_SLASH
            else -> null
        }
    }

    override fun onBackPressed() {
        if (binding.keyboardOverlay.visibility == View.VISIBLE) {
            hideVirtualKeyboard()
            return
        }
        if (vrMode) {
            toggleVrMode()
            return
        }
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
