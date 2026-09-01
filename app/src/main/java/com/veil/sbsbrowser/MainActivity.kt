package com.veil.sbsbrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.veil.sbsbrowser.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var vrMode = false
    private var defaultUrl = "https://www.google.com"

    // Guards against feedback loops when we programmatically scroll the mirror pane
    private var isSyncingScroll = false

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureWebView(binding.webViewLeft, isPrimary = true)
        configureWebView(binding.webViewRight, isPrimary = false)

        // Right pane is a pure mirror: swallow its touch input so all interaction
        // happens on the left pane and the two never drift out of sync.
        binding.webViewRight.setOnTouchListener { _, _ -> true }

        // Mirror scroll position from left -> right on every scroll frame.
        binding.webViewLeft.setOnScrollChangeListener { _, scrollX, scrollY, _, _ ->
            if (isSyncingScroll) return@setOnScrollChangeListener
            isSyncingScroll = true
            binding.webViewRight.scrollTo(scrollX, scrollY)
            isSyncingScroll = false
        }

        binding.webViewLeft.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    binding.urlInput.setText(it)
                    // Keep the mirror pane on the same page.
                    if (binding.webViewRight.url != it) {
                        binding.webViewRight.loadUrl(it)
                    }
                }
            }
        }

        // Right pane never initiates navigation on its own.
        binding.webViewRight.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return true // ignore; left pane drives navigation
            }
        }

        binding.goButton.setOnClickListener { loadFromInput() }
        binding.urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                loadFromInput()
                true
            } else {
                false
            }
        }

        binding.vrToggleButton.setOnClickListener { toggleVrMode() }

        binding.webViewLeft.loadUrl(defaultUrl)
    }

    private fun configureWebView(webView: WebView, isPrimary: Boolean) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        // Disable independent pinch-zoom so both panes always render the
        // same layout width/scale and stay pixel-aligned when mirrored.
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        webView.isVerticalScrollBarEnabled = isPrimary
        webView.isHorizontalScrollBarEnabled = isPrimary
    }

    private fun loadFromInput() {
        var text = binding.urlInput.text.toString().trim()
        if (text.isEmpty()) return
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            text = "https://$text"
        }
        binding.webViewLeft.loadUrl(text)
        hideKeyboard()
    }

    private fun hideKeyboard() {
        currentFocus?.let { view ->
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun toggleVrMode() {
        vrMode = !vrMode
        binding.addressBar.visibility = if (vrMode) View.GONE else View.VISIBLE
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

    override fun onBackPressed() {
        if (vrMode) {
            toggleVrMode()
            return
        }
        if (binding.webViewLeft.canGoBack()) {
            binding.webViewLeft.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
