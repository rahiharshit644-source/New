package com.soltini.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.View
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BrowserController
 *
 * Full-Control In-App Autonomous Android WebView Agent.
 * Executes on-device browser actions:
 *   - Navigation: open, back, forward, reload
 *   - Inspection: snapshot (@e1, @e2 refs), readText
 *   - Interaction: click, fill (including passwords/usernames/OTPs), select (dropdowns),
 *                  check/uncheck (checkbox/radio), press (Enter/Tab), clear, scroll
 *   - Form & Account Automation: autofill, save_login, get_login
 *   - Scripting: evaluate (execute custom JavaScript)
 */
class BrowserController private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "BrowserController"
        private const val MAX_SNAPSHOT_CHARS = 6000
        private const val MAX_MAIN_TEXT_CHARS = 1600
        private const val PREFS_VAULT = "myra_browser_vault"

        @Volatile
        private var instance: BrowserController? = null

        fun getInstance(context: Context): BrowserController {
            return instance ?: synchronized(this) {
                instance ?: BrowserController(context.applicationContext).also { instance = it }
            }
        }
    }

    private var webView: WebView? = null
    private val vaultPrefs: SharedPreferences = appContext.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE)

    private val _isPanelVisible = MutableStateFlow(false)
    val isPanelVisible: StateFlow<Boolean> = _isPanelVisible.asStateFlow()

    private val _isPanelExpanded = MutableStateFlow(false)
    val isPanelExpanded: StateFlow<Boolean> = _isPanelExpanded.asStateFlow()

    private val _currentUrl = MutableStateFlow("about:blank")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _pageTitle = MutableStateFlow("")
    val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow("Browser Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _isDesktopMode = MutableStateFlow(false)
    val isDesktopMode: StateFlow<Boolean> = _isDesktopMode.asStateFlow()

    private val isStopped = AtomicBoolean(false)
    private var activePageLoadJob: CompletableDeferred<Boolean>? = null

    private var mobileUserAgent: String = ""
    private val desktopUserAgent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * MUST be called on the main thread. Creates the singleton WebView once.
     * It is wrapped in a MutableContextWrapper so the UI can swap in the Activity context while
     * the WebView is on screen (needed for <select> popups / dialogs) and switch back to the
     * application context when detached (no Activity leak).
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun obtainWebView(): WebView {
        webView?.let { return it }
        val wv = WebView(MutableContextWrapper(appContext)).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            val cookieMgr = CookieManager.getInstance()
            cookieMgr.setAcceptCookie(true)
            cookieMgr.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = false
                allowFileAccess = false
                mediaPlaybackRequiresUserGesture = false
                userAgentString = userAgentString.replace("; wv", "") + " Mobile/MyraAgent"
                mobileUserAgent = userAgentString
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                try {
                    val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                    val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                        setMimeType(mimetype)
                        addRequestHeader("User-Agent", userAgent)
                        CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                        setDescription("Downloading file via Myra Browser")
                        setTitle(fileName)
                        setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                    }
                    val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                    dm.enqueue(request)
                    _statusMessage.value = "Downloading file..."
                } catch (e: Exception) {
                    Log.e(TAG, "Download error: ${e.message}")
                    _statusMessage.value = "Download failed: ${e.message}"
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    Log.i(TAG, "Web JS Alert: $message")
                    _statusMessage.value = "Alert: $message"
                    result?.confirm()
                    return true
                }

                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    Log.i(TAG, "Web JS Confirm: $message -> Auto confirmed")
                    _statusMessage.value = "Confirm: $message"
                    result?.confirm()
                    return true
                }

                override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                    Log.i(TAG, "Web JS Prompt: $message")
                    result?.confirm(defaultValue ?: "")
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase().orEmpty()
                    if (scheme in listOf("http", "https", "about", "blob", "data", "javascript")) return false
                    if (scheme == "file" || scheme == "content") return true // blocked
                    // tel:, mailto:, intent:, market:, whatsapp: ... -> hand over to the system
                    return try {
                        val intent = if (scheme == "intent") {
                            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        } else {
                            Intent(Intent.ACTION_VIEW, uri)
                        }
                        intent.addCategory(Intent.CATEGORY_BROWSABLE)
                        intent.component = null
                        intent.selector = null
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        appContext.startActivity(intent)
                        true
                    } catch (e: Exception) {
                        Log.w(TAG, "External scheme not handled: $uri (${e.message})")
                        true
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    _isLoading.value = true
                    _currentUrl.value = url.orEmpty()
                    _statusMessage.value = "Loading $url..."
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    if (!url.isNullOrBlank()) _currentUrl.value = url
                    _canGoBack.value = view?.canGoBack() ?: false
                    _canGoForward.value = view?.canGoForward() ?: false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    _isLoading.value = false
                    _pageTitle.value = view?.title.orEmpty()
                    _currentUrl.value = url.orEmpty()
                    _canGoBack.value = view?.canGoBack() ?: false
                    _canGoForward.value = view?.canGoForward() ?: false
                    _statusMessage.value = "Page loaded: ${view?.title.orEmpty()}"
                    activePageLoadJob?.let {
                        if (!it.isCompleted) it.complete(true)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        _isLoading.value = false
                        _statusMessage.value = "Load error: ${error?.description}"
                        activePageLoadJob?.let {
                            if (!it.isCompleted) it.complete(false)
                        }
                    }
                }
            }
        }

        // Give the WebView a real size even while it is NOT on screen (e.g. voice agent running in
        // background). Without this getBoundingClientRect() is 0 and snapshot() finds nothing.
        val dm = appContext.resources.displayMetrics
        wv.measure(
            View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY)
        )
        wv.layout(0, 0, dm.widthPixels, dm.heightPixels)
        wv.onResume()
        wv.resumeTimers()

        webView = wv
        return wv
    }

    suspend fun getOrCreateWebView(): WebView = withContext(Dispatchers.Main) { obtainWebView() }

    /** Call when the WebView is put on screen (Activity context needed for popups/dialogs). */
    fun attachContext(activityContext: Context) {
        (webView?.context as? MutableContextWrapper)?.baseContext = activityContext
    }

    /** Call when the on-screen holder is released; only reverts if nobody else has grabbed the view. */
    fun detachContext() {
        val wv = webView ?: return
        if (wv.parent == null) {
            (wv.context as? MutableContextWrapper)?.baseContext = appContext
        }
    }

    fun isStopRequested(): Boolean = isStopped.get()


    /**
     * Executes any requested browser_action with full control.
     */
    suspend fun executeAction(
        action: String,
        ref: String? = null,
        text: String? = null,
        url: String? = null
    ): String {
        return try {
            when (action.lowercase().trim()) {
                "open" -> open(url.orEmpty())
                "snapshot" -> snapshot()
                "click" -> click(ref.orEmpty())
                "fill" -> fill(ref.orEmpty(), text.orEmpty())
                "select", "dropdown" -> selectOption(ref.orEmpty(), text.orEmpty())
                "check" -> setChecked(ref.orEmpty(), true)
                "uncheck" -> setChecked(ref.orEmpty(), false)
                "clear" -> clearInput(ref.orEmpty())
                "press" -> press(text ?: "Enter")
                "scroll" -> scroll(text ?: "down")
                "back" -> back()
                "read" -> readText()
                "evaluate", "eval", "js" -> evaluate(text.orEmpty())
                "save_login" -> saveLoginAction(url.orEmpty(), ref.orEmpty(), text.orEmpty())
                "get_login" -> getLoginAction(url.orEmpty().ifBlank { _currentUrl.value })
                "run_task", "autonomous_task", "agent_loop" -> {
                    val goal = text?.ifBlank { url } ?: url.orEmpty()
                    runAutonomousTask(goal, startUrl = if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) url else null)
                }
                else -> "Unknown action: '$action'. Supported: open, snapshot, click, fill, select, check, uncheck, clear, press, scroll, back, read, evaluate, save_login, get_login, run_task."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in browser action '$action'", e)
            "Error in browser action '$action': ${e.message}"
        }
    }

    /**
     * Autonomous Multi-Step Execution Loop:
     * Executes complex multi-step web tasks up to 12 steps using agent-browser snapshot + refs.
     */
    suspend fun runAutonomousTask(goal: String, startUrl: String? = null): String {
        isStopped.set(false)
        _isPanelVisible.value = true
        _statusMessage.value = "Starting autonomous task for: \"$goal\""
        val settings = com.soltini.app.settings.AppSettings(appContext)
        val agentLoop = BrowserAgentLoop(appContext, this, settings)
        return agentLoop.executeTask(goal, startUrl) { progress ->
            _statusMessage.value = progress
        }
    }

    /**
     * 1. open(url): Prepend https:// if needed, wait for page load.
     */
    suspend fun open(rawUrl: String): String {
        if (rawUrl.isBlank()) return "Error: URL is required for 'open' action."

        val targetUrl = when {
            rawUrl.startsWith("http://", ignoreCase = true) ||
            rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl.trim()
            else -> "https://${rawUrl.trim()}"
        }

        _isPanelVisible.value = true
        _statusMessage.value = "Opening $targetUrl..."

        val wv = getOrCreateWebView()
        val loadJob = CompletableDeferred<Boolean>()
        activePageLoadJob = loadJob

        withContext(Dispatchers.Main) {
            wv.loadUrl(targetUrl)
        }

        withTimeoutOrNull(12000L) {
            loadJob.await()
        }
        delay(1200L)

        val finalTitle = withContext(Dispatchers.Main) { wv.title.orEmpty() }
        val finalUrl = withContext(Dispatchers.Main) { wv.url.orEmpty() }
        _pageTitle.value = finalTitle
        _currentUrl.value = finalUrl

        return "Opened: $finalUrl | Title: \"$finalTitle\". Take 'snapshot' next to see all interactive elements (@e1, @e2)."
    }

    /**
     * 2. snapshot(): Comprehensive DOM element labeling with unique refs (@e1, @e2, ...)
     * Catches buttons, links, inputs, passwords, dropdowns, checkboxes, textareas.
     */
    suspend fun snapshot(): String {
        _statusMessage.value = "Analyzing page DOM..."
        val wv = getOrCreateWebView()

        val jsScript = """
            (function() {
                try {
                    document.querySelectorAll('[data-myra-ref]').forEach(function(n) { n.removeAttribute('data-myra-ref'); });
                    const selectors = 'a, button, input, textarea, select, [role="button"], [role="link"], [role="searchbox"], [role="checkbox"], [onclick], [tabindex="0"]';
                    const allNodes = Array.from(document.querySelectorAll(selectors));
                    let counter = 1;
                    const items = [];

                    for (const el of allNodes) {
                        const rect = el.getBoundingClientRect();
                        const style = window.getComputedStyle(el);
                        if (rect.width <= 0 || rect.height <= 0 || style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
                            continue;
                        }

                        const ref = 'e' + counter++;
                        el.setAttribute('data-myra-ref', ref);

                        const tag = el.tagName.toLowerCase();
                        const role = (el.getAttribute('role') || '').toLowerCase();
                        const type = (el.getAttribute('type') || '').toLowerCase();
                        let kind = 'element';
                        let label = '';

                        if (tag === 'input') {
                            if (type === 'password') {
                                kind = 'password_input';
                                label = el.placeholder || el.name || el.id || 'Password';
                            } else if (type === 'checkbox') {
                                kind = 'checkbox' + (el.checked ? ' [CHECKED]' : ' [UNCHECKED]');
                                label = el.getAttribute('aria-label') || el.name || el.value || '';
                            } else if (type === 'radio') {
                                kind = 'radio' + (el.checked ? ' [SELECTED]' : '');
                                label = el.getAttribute('aria-label') || el.name || el.value || '';
                            } else if (type === 'submit' || type === 'button') {
                                kind = 'button';
                                label = el.value || el.getAttribute('aria-label') || '';
                            } else {
                                kind = 'textbox';
                                label = el.placeholder || el.getAttribute('aria-label') || el.name || el.id || el.value || '';
                            }
                        } else if (tag === 'textarea') {
                            kind = 'textbox_multiline';
                            label = el.placeholder || el.getAttribute('aria-label') || el.name || '';
                        } else if (tag === 'button' || role === 'button') {
                            kind = 'button';
                            label = el.innerText || el.getAttribute('aria-label') || el.value || '';
                        } else if (tag === 'a' || role === 'link') {
                            kind = 'link';
                            label = el.innerText || el.getAttribute('aria-label') || el.title || '';
                        } else if (tag === 'select') {
                            kind = 'dropdown';
                            const selectedOpt = el.options[el.selectedIndex]?.text || '';
                            label = (el.getAttribute('aria-label') || el.name || '') + (selectedOpt ? ' [Current: ' + selectedOpt + ']' : '');
                        } else {
                            kind = role || tag;
                            label = el.innerText || el.getAttribute('aria-label') || '';
                        }

                        label = label.replace(/\s+/g, ' ').trim().substring(0, 60);
                        items.push('@' + ref + ' ' + kind + (label ? ' "' + label + '"' : ''));

                        if (counter > 85) break;
                    }

                    const pageTitle = document.title || '';
                    const pageUrl = window.location.href || '';
                    const bodyText = (document.body ? (document.body.innerText || '') : '').replace(/\s+/g, ' ').trim().substring(0, $MAX_MAIN_TEXT_CHARS);

                    return JSON.stringify({
                        title: pageTitle,
                        url: pageUrl,
                        elements: items,
                        mainText: bodyText
                    });
                } catch(e) {
                    return JSON.stringify({ error: e.message });
                }
            })()
        """.trimIndent()

        val rawResult = evaluateJs(wv, jsScript)
        val json = try {
            JSONObject(rawResult)
        } catch (_: Exception) {
            JSONObject()
        }

        if (json.has("error")) {
            return "Error scanning page: ${json.optString("error")}"
        }

        val title = json.optString("title", _pageTitle.value)
        val url = json.optString("url", _currentUrl.value)
        val mainText = json.optString("mainText", "")
        val elementsArray = json.optJSONArray("elements")

        val sb = StringBuilder()
        sb.append("Page Title: ").append(title).append("\n")
        sb.append("URL: ").append(url).append("\n\n")
        if (mainText.isNotBlank()) {
            sb.append("Main Page Text:\n").append(mainText).append("\n\n")
        }
        sb.append("Interactive Elements:\n")
        if (elementsArray != null && elementsArray.length() > 0) {
            for (i in 0 until elementsArray.length()) {
                sb.append(elementsArray.getString(i)).append("\n")
            }
        } else {
            sb.append("(No visible interactive elements found)\n")
        }

        var result = sb.toString().trim()
        if (result.length > MAX_SNAPSHOT_CHARS) {
            result = result.take(MAX_SNAPSHOT_CHARS) + "\n...[Truncated to 6000 chars]"
        }

        _statusMessage.value = "Snapshot ready (${elementsArray?.length() ?: 0} elements)"
        return result
    }

    /**
     * 3. click(ref): Clicks target element by reference.
     */
    suspend fun click(rawRef: String): String {
        if (rawRef.isBlank()) return "Error: 'ref' (e.g. @e1) is required for click."
        val cleanRef = rawRef.trim().removePrefix("@")
        _statusMessage.value = "Clicking @$cleanRef..."
        val wv = getOrCreateWebView()

        val js = """
            (function() {
                const el = document.querySelector('[data-myra-ref="$cleanRef"]');
                if (!el) return 'NOT_FOUND';
                el.scrollIntoView({behavior: 'smooth', block: 'center'});
                try { el.focus(); } catch(e) {}
                el.click();
                const desc = el.innerText || el.value || el.getAttribute('aria-label') || el.tagName;
                return 'CLICKED: ' + desc.replace(/\s+/g, ' ').trim().substring(0, 40);
            })()
        """.trimIndent()

        val outcome = evaluateJs(wv, js)
        delay(1500L)

        _statusMessage.value = "Clicked @$cleanRef"
        return if (outcome.contains("NOT_FOUND")) {
            "Element @$cleanRef not found. Take a new 'snapshot' to get updated refs."
        } else {
            "Success: Clicked @$cleanRef. Call 'snapshot' next to see the updated page."
        }
    }

    /**
     * 4. fill(ref, text): Full input filling with React/Vue/Angular synthetic setter support.
     * Can fill usernames, emails, addresses, passwords, codes, and search queries.
     */
    suspend fun fill(rawRef: String, textToFill: String): String {
        if (rawRef.isBlank()) return "Error: 'ref' is required for fill."
        val cleanRef = rawRef.trim().removePrefix("@")
        _statusMessage.value = "Filling @$cleanRef..."
        val wv = getOrCreateWebView()

        val escapedText = JSONObject.quote(textToFill)

        val js = """
            (function() {
                const el = document.querySelector('[data-myra-ref="$cleanRef"]');
                if (!el) return 'NOT_FOUND';
                el.scrollIntoView({behavior: 'smooth', block: 'center'});
                try { el.focus(); } catch(e) {}

                // Use native prototype descriptor to trigger React/Angular/Vue internal state updates
                const proto = el instanceof HTMLTextAreaElement ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                const descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
                if (descriptor && descriptor.set) {
                    descriptor.set.call(el, $escapedText);
                } else {
                    el.value = $escapedText;
                }

                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
                return 'FILLED';
            })()
        """.trimIndent()

        val outcome = evaluateJs(wv, js)
        delay(500L)

        return if (outcome.contains("NOT_FOUND")) {
            "Element @$cleanRef not found. Take a new 'snapshot' to check active references."
        } else {
            _statusMessage.value = "Filled @$cleanRef"
            "Success: Filled @$cleanRef. Call 'press(text=\"Enter\")' or click submit button if needed."
        }
    }

    /**
     * 5. selectOption(ref, text): Selects an item from a <select> dropdown by label or value.
     */
    suspend fun selectOption(rawRef: String, optionValue: String): String {
        if (rawRef.isBlank()) return "Error: 'ref' is required for select."
        val cleanRef = rawRef.trim().removePrefix("@")
        val escapedOption = JSONObject.quote(optionValue.lowercase().trim())
        _statusMessage.value = "Selecting '$optionValue' in @$cleanRef..."
        val wv = getOrCreateWebView()

        val js = """
            (function() {
                const el = document.querySelector('[data-myra-ref="$cleanRef"]');
                if (!el || el.tagName.toLowerCase() !== 'select') return 'NOT_SELECT';
                const target = $escapedOption;
                let matched = false;
                for (let i = 0; i < el.options.length; i++) {
                    const opt = el.options[i];
                    if (opt.text.toLowerCase().includes(target) || opt.value.toLowerCase().includes(target)) {
                        el.selectedIndex = i;
                        matched = true;
                        break;
                    }
                }
                if (!matched && el.options.length > 0) {
                    el.selectedIndex = 0;
                }
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return matched ? 'SELECTED' : 'PARTIAL';
            })()
        """.trimIndent()

        val outcome = evaluateJs(wv, js)
        delay(600L)
        return "Selected option in @$cleanRef (outcome: $outcome)."
    }

    /**
     * 6. setChecked(ref, checked): Toggles checkbox or radio button.
     */
    suspend fun setChecked(rawRef: String, checked: Boolean): String {
        val cleanRef = rawRef.trim().removePrefix("@")
        val wv = getOrCreateWebView()

        val js = """
            (function() {
                const el = document.querySelector('[data-myra-ref="$cleanRef"]');
                if (!el) return 'NOT_FOUND';
                if (el.checked !== $checked) { el.click(); }
                return el.checked === $checked ? 'CHECKED' : 'FAILED';
            })()
        """.trimIndent()

        val outcome = evaluateJs(wv, js)
        delay(400L)
        return when {
            outcome.contains("NOT_FOUND") -> "Element @$cleanRef not found. Take a new 'snapshot'."
            outcome.contains("FAILED") -> "Could not set @$cleanRef to checked=$checked (custom widget?). Try 'click' instead."
            else -> "Set @$cleanRef checked=$checked."
        }
    }

    /**
     * 7. clearInput(ref): Clears text inside an input or textarea.
     */
    suspend fun clearInput(rawRef: String): String {
        val cleanRef = rawRef.trim().removePrefix("@")
        val wv = getOrCreateWebView()

        val js = """
            (function() {
                const el = document.querySelector('[data-myra-ref="$cleanRef"]');
                if (!el) return 'NOT_FOUND';
                el.value = '';
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return 'CLEARED';
            })()
        """.trimIndent()

        evaluateJs(wv, js)
        return "Cleared text in @$cleanRef."
    }

    /**
     * 8. press(key): Dispatches key event (e.g. Enter to submit a form).
     */
    suspend fun press(key: String): String {
        _statusMessage.value = "Pressing $key..."
        val wv = getOrCreateWebView()
        val keyJson = JSONObject.quote(key)

        val js = """
            (function() {
                const key = $keyJson;
                const codes = { Enter: 13, Tab: 9, Escape: 27, Backspace: 8, ' ': 32, ArrowDown: 40, ArrowUp: 38, ArrowLeft: 37, ArrowRight: 39 };
                const kc = codes[key] || 0;
                const active = document.activeElement || document.body;
                const init = { key: key, code: key, keyCode: kc, which: kc, bubbles: true, cancelable: true };
                const notPrevented = active.dispatchEvent(new KeyboardEvent('keydown', init));
                if (key === 'Enter') active.dispatchEvent(new KeyboardEvent('keypress', init));
                active.dispatchEvent(new KeyboardEvent('keyup', init));
                if (key === 'Enter' && notPrevented && active.tagName === 'INPUT' && active.form) {
                    try {
                        if (active.form.requestSubmit) { active.form.requestSubmit(); } else { active.form.submit(); }
                    } catch(e) {}
                }
                return 'PRESSED';
            })()
        """.trimIndent()

        evaluateJs(wv, js)
        delay(1500L)

        _statusMessage.value = "Pressed $key"
        return "Pressed '$key'. Call 'snapshot' next to see the updated page."
    }

    /**
     * 9. scroll(direction): Scroll "down" or "up".
     */
    suspend fun scroll(direction: String): String {
        val wv = getOrCreateWebView()
        val deltaY = if (direction.equals("up", ignoreCase = true)) -650 else 650
        _statusMessage.value = "Scrolling $direction..."

        val js = "window.scrollBy({ top: $deltaY, behavior: 'smooth' }); 'SCROLLED';"
        evaluateJs(wv, js)
        delay(600L)

        _statusMessage.value = "Scrolled $direction"
        return "Scrolled $direction. Call 'snapshot' to inspect newly visible elements."
    }

    /**
     * 10. back(): WebView goBack.
     */
    suspend fun back(): String {
        val wv = getOrCreateWebView()
        val canGoBack = withContext(Dispatchers.Main) { wv.canGoBack() }
        if (!canGoBack) return "Cannot navigate back: No browser history."

        _statusMessage.value = "Navigating back..."
        withContext(Dispatchers.Main) {
            wv.goBack()
        }
        delay(1200L)

        val title = withContext(Dispatchers.Main) { wv.title.orEmpty() }
        _statusMessage.value = "Navigated back: $title"
        return "Navigated back to: $title. Call 'snapshot' next."
    }

    /**
     * 11. readText(): Full visible text extraction.
     */
    suspend fun readText(): String {
        _statusMessage.value = "Reading page text..."
        val wv = getOrCreateWebView()

        val js = """
            (function() {
                return (document.body ? (document.body.innerText || '') : '').replace(/\s+/g, ' ').trim().substring(0, 4000);
            })()
        """.trimIndent()

        val raw = evaluateJs(wv, js)

        _statusMessage.value = "Page text read"
        return raw.ifBlank { "(No text content found on this page)" }
    }

    /**
     * 12. evaluate(script): Executes custom JavaScript snippet on the live page.
     */
    suspend fun evaluate(script: String): String {
        if (script.isBlank()) return "Error: JavaScript script required."
        val wv = getOrCreateWebView()
        val scriptJson = JSONObject.quote(script)
        val res = evaluateJs(wv, "(function() { try { return String(eval($scriptJson)); } catch(e) { return 'ERR: ' + e.message; } })()")
        return "JS Evaluation Result: $res"
    }

    /**
     * Credential Vault: Save login info for a domain so Myra can log in automatically.
     */
    fun saveCredential(domain: String, username: String, pass: String) {
        val cleanDomain = extractDomain(domain)
        val data = JSONObject().apply {
            put("username", username)
            put("password", pass)
            put("timestamp", System.currentTimeMillis())
        }
        vaultPrefs.edit().putString("cred_$cleanDomain", data.toString()).apply()
        Log.i(TAG, "Saved credential for domain: $cleanDomain")
    }

    fun getCredential(domain: String): JSONObject? {
        val cleanDomain = extractDomain(domain)
        val raw = vaultPrefs.getString("cred_$cleanDomain", null) ?: return null
        return try { JSONObject(raw) } catch (_: Exception) { null }
    }

    private fun saveLoginAction(url: String, username: String, pass: String): String {
        if (username.isBlank() || pass.isBlank()) {
            return "Error: Both username and password required to save credentials."
        }
        saveCredential(url, username, pass)
        return "Saved login credentials for $url in secure vault."
    }

    private fun getLoginAction(url: String): String {
        val cred = getCredential(url) ?: return "No saved login credentials found for $url."
        return "Found saved login for ${extractDomain(url)}: username='${cred.optString("username")}', password='${cred.optString("password")}'."
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = android.net.Uri.parse(if (url.startsWith("http")) url else "https://$url")
            uri.host ?: url
        } catch (_: Exception) {
            url
        }
    }

    /**
     * Stops ongoing loads and hides the panel.
     */
    fun stop() {
        isStopped.set(true)
        _isLoading.value = false
        _statusMessage.value = "Browser tool stopped by user"
        _isPanelVisible.value = false
        activePageLoadJob?.let {
            if (!it.isCompleted) it.cancel()
        }
        webView?.post {
            try {
                webView?.stopLoading()
            } catch (_: Exception) {}
        }
    }

    fun togglePanelExpanded() {
        _isPanelExpanded.value = !_isPanelExpanded.value
    }

    fun closePanel() {
        _isPanelVisible.value = false
    }

    fun reload() {
        webView?.post { webView?.reload() }
    }

    fun goBackManual() {
        webView?.post {
            if (webView?.canGoBack() == true) {
                webView?.goBack()
            }
        }
    }

    fun goForwardManual() {
        webView?.post {
            if (webView?.canGoForward() == true) {
                webView?.goForward()
            }
        }
    }

    fun toggleDesktopMode() {
        val next = !_isDesktopMode.value
        _isDesktopMode.value = next
        mainHandler.post {
            val wv = webView ?: return@post
            wv.settings.userAgentString = if (next) desktopUserAgent else mobileUserAgent
            wv.settings.useWideViewPort = true
            wv.settings.loadWithOverviewMode = true
            wv.reload()
        }
    }

    fun navigateUserUrl(queryOrUrl: String) {
        val trimmed = queryOrUrl.trim()
        if (trimmed.isBlank()) return
        val target = when {
            trimmed.equals("about:blank", ignoreCase = true) -> "about:blank"
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.contains(":") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
        }
        mainHandler.post { obtainWebView().loadUrl(target) }
    }

    fun getAllCredentials(): Map<String, JSONObject> {
        val result = mutableMapOf<String, JSONObject>()
        for ((k, v) in vaultPrefs.all) {
            if (k.startsWith("cred_") && v is String) {
                try {
                    val domain = k.removePrefix("cred_")
                    result[domain] = JSONObject(v)
                } catch (_: Exception) {}
            }
        }
        return result
    }

    fun deleteCredential(domain: String) {
        val cleanDomain = extractDomain(domain)
        vaultPrefs.edit().remove("cred_$cleanDomain").apply()
    }

    private suspend fun evaluateJs(wv: WebView, script: String, timeoutMs: Long = 8000L): String {
        val res = withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<String>()
            wv.evaluateJavascript(script) { result -> deferred.complete(result ?: "") }
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } ?: return "ERR_TIMEOUT"

        if (res.startsWith("\"") && res.endsWith("\"") && res.length >= 2) {
            return try {
                JSONObject("{ \"v\": $res }").getString("v")
            } catch (_: Exception) {
                res.substring(1, res.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
        }
        return res
    }
}
