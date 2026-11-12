package ge.gela.multi

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** ერთი ვებ ექაუნთი — საკუთარი, სრულად იზოლირებული cookie/მეხსიერებით */
class WebAccountActivity : ComponentActivity() {

    companion object {
        fun open(ctx: Context, acc: WebAccount) {
            val i = Intent(ctx, WebAccountActivity::class.java)
                .setData(Uri.parse("multi://account/${acc.id}")) // ყოველ ექაუნთს — ცალკე ფანჯარა recents-ში
                .putExtra("id", acc.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }

        const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
        fileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val acc = intent.getStringExtra("id")?.let { Store.get(this, it) }
        if (acc == null) { finish(); return }

        @Suppress("DEPRECATION")
        setTaskDescription(ActivityManager.TaskDescription(acc.name))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(acc.color.toInt())
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(6), dp(8))
        }
        val title = TextView(this).apply {
            text = acc.name
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun barButton(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(dp(12), dp(2), dp(12), dp(2))
            setOnClickListener { onClick() }
        }
        bar.addView(title)
        bar.addView(barButton("⌂") { web.loadUrl(acc.url) })
        bar.addView(barButton("↻") { web.reload() })
        root.addView(bar)

        web = WebView(this)
        // იზოლაცია: თითო ექაუნთს თავისი WebView პროფილი
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getOrCreateProfile(acc.profileName)
            WebViewCompat.setProfile(web, acc.profileName)
        } else {
            Toast.makeText(
                this,
                "განაახლე „Android System WebView“ Play Store-დან — თორემ ექაუნთები არ გაიყოფა",
                Toast.LENGTH_LONG
            ).show()
        }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        web.setBackgroundColor(Color.WHITE)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = acc.desktop
            displayZoomControls = false
            userAgentString = if (acc.desktop) DESKTOP_UA
            else userAgentString.replace("; wv", "").replace(Regex("Version/\\S+ "), "")
        }
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
            } else {
                ProfileStore.getInstance().getProfile(acc.profileName)
                    ?.cookieManager?.setAcceptThirdPartyCookies(web, true)
            }
        } catch (_: Exception) {}

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val scheme = u.scheme ?: return false
                if (scheme == "http" || scheme == "https") return false
                // intent:, fb:, whatsapp: და ა.შ. — ვცდილობთ გარე აპში გახსნას
                try {
                    val i = if (scheme == "intent") Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                    else Intent(Intent.ACTION_VIEW, u)
                    startActivity(i)
                } catch (_: Exception) {}
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try { filePicker.launch(params.createIntent()); true }
                catch (e: Exception) { fileCallback = null; false }
            }
        }
        web.setDownloadListener { url, _, _, _, _ ->
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(acc.url)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
                ProfileStore.getInstance().getProfile(
                    intent.getStringExtra("id")?.let { "acc_$it" } ?: ""
                )?.cookieManager?.flush()
            else CookieManager.getInstance().flush()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
