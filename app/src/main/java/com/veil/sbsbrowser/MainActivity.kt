package com.veil.sbsbrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Patterns
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.veil.sbsbrowser.databinding.ActivityMainBinding
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var vrMode = false
    private var defaultUrl = "https://www.google.com"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.webView.mirror = binding.mirrorView
        binding.mirrorView.source = binding.webView

        configureWebView(binding.webView)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { binding.urlInput.setText(it) }
                updateNavButtonStates()
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

        binding.backButton.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
        }
        binding.forwardButton.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }
        binding.refreshButton.setOnClickListener { binding.webView.reload() }
        binding.homeButton.setOnClickListener { binding.webView.loadUrl(defaultUrl) }

        binding.webView.loadUrl(defaultUrl)
    }

    private fun configureWebView(webView: WebView) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
    }

    /**
     * Decides whether the typed text is a real URL (navigate to it) or a
     * plain search term (send it to a Google search) using Android's
     * built-in WEB_URL pattern -- the same kind of heuristic real browser
     * address bars use.
     */
    private fun resolveInputToUrl(raw: String): String {
        val text = raw.trim()

        if (text.startsWith("http://") || text.startsWith("https://")) {
            return text
        }

        // "youtube.com", "www.reddit.com/r/android" etc. -> treat as a URL
        // even without a scheme. Single words with no dot ("youtube") won't
        // match this, and fall through to search.
        if (Patterns.WEB_URL.matcher(text).matches()) {
            return "https://$text"
        }

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
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
