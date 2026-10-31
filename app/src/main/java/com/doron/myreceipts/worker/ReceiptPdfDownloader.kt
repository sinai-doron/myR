package com.doron.myreceipts.worker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

class ReceiptPdfDownloader(private val context: Context) {

    suspend fun download(url: String, clientId: String): String? {
        return webViewMutex.withLock {
            val pdfDir = File(context.filesDir, "receipts")
            pdfDir.mkdirs()
            val pdfFile = File(pdfDir, "$clientId.pdf")

            val resolvedUrl = resolveRedirects(url) ?: url
            Log.i(TAG, "Resolved URL: $resolvedUrl (from: $url)")

            val downloadUrl = withTimeoutOrNull(60_000L) {
                findDownloadUrl(resolvedUrl)
            }

            if (downloadUrl != null) {
                Log.d(TAG, "Downloading PDF from: $downloadUrl")
                val downloaded = downloadFromUrl(downloadUrl, pdfFile)
                if (downloaded != null) return@withLock downloaded
            }

            Log.i(TAG, "No download URL, trying print-to-PDF for $clientId")
            val printed = withTimeoutOrNull(45_000L) {
                printToPdf(resolvedUrl, pdfFile)
            }
            if (printed != null) {
                Log.d(TAG, "Print-to-PDF saved: $printed (${pdfFile.length()} bytes)")
                return@withLock printed
            }

            Log.w(TAG, "All download methods failed for $clientId")
            null
        }
    }

    private suspend fun downloadFromUrl(downloadUrl: String, pdfFile: File): String? =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(downloadUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                if (cookies != null) {
                    conn.setRequestProperty("Cookie", cookies)
                }
                conn.inputStream.use { input ->
                    pdfFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (pdfFile.length() > 0) {
                    Log.d(TAG, "PDF saved: ${pdfFile.absolutePath} (${pdfFile.length()} bytes)")
                    pdfFile.absolutePath
                } else {
                    pdfFile.delete()
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download PDF", e)
                pdfFile.delete()
                null
            }
        }

    private suspend fun resolveRedirects(url: String): String? = withContext(Dispatchers.IO) {
        try {
            var currentUrl = url
            repeat(10) {
                val conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                val code = conn.responseCode
                if (code in 300..399) {
                    val location =
                        conn.getHeaderField("Location") ?: return@withContext currentUrl
                    currentUrl = if (location.startsWith("http")) location
                    else URL(URL(currentUrl), location).toString()
                    conn.disconnect()
                } else {
                    conn.disconnect()
                    return@withContext currentUrl
                }
            }
            currentUrl
        } catch (e: Exception) {
            Log.w(TAG, "Redirect resolution failed: ${e.message}")
            null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun findDownloadUrl(receiptUrl: String): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                var resumed = false
                val mainHandler = Handler(Looper.getMainLooper())

                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = USER_AGENT
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }

                fun finish(result: String?) {
                    if (!resumed) {
                        resumed = true
                        mainHandler.post { webView.destroy() }
                        continuation.resume(result)
                    }
                }

                webView.setDownloadListener(DownloadListener { url, _, _, mimeType, _ ->
                    Log.i(TAG, "Download triggered: $mimeType — $url")
                    if (SKIP_URL_PATTERNS.any { url.contains(it) }) {
                        Log.i(TAG, "Skipping non-receipt download: $url")
                        return@DownloadListener
                    }
                    finish(url)
                })

                webView.webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: android.os.Message,
                    ): Boolean {
                        val newWebView = WebView(context)
                        newWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                v: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val url = request.url.toString()
                                Log.i(TAG, "New window URL: $url")
                                finish(url)
                                return true
                            }
                        }
                        (resultMsg.obj as android.webkit.WebView.WebViewTransport).webView =
                            newWebView
                        resultMsg.sendToTarget()
                        return true
                    }
                }
                webView.settings.setSupportMultipleWindows(true)
                webView.settings.javaScriptCanOpenWindowsAutomatically = true

                var pollCount = 0
                var clicked = false
                val maxPolls = 10

                fun pollForButton() {
                    if (resumed || clicked) return
                    pollCount++
                    Log.i(TAG, "Poll #$pollCount (url: ${webView.url})")
                    webView.evaluateJavascript(CLICK_DOWNLOAD_JS) { result ->
                        Log.i(TAG, "Poll #$pollCount result: $result")
                        if (result.contains("clicked")) {
                            clicked = true
                            Log.i(TAG, "Button clicked, waiting for download...")
                            mainHandler.postDelayed({
                                if (!resumed) {
                                    Log.w(TAG, "Download not triggered 15s after click")
                                    finish(null)
                                }
                            }, 15000)
                        } else if (result.contains("not_found")) {
                            if (pollCount >= maxPolls) {
                                Log.w(TAG, "No download button after $pollCount polls")
                                finish(null)
                            } else {
                                mainHandler.postDelayed({ pollForButton() }, 3000)
                            }
                        }
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url.toString()
                        if (clicked && (url.contains(".pdf") || url.startsWith("blob:"))) {
                            Log.i(TAG, "Intercepted navigation after click: $url")
                            if (!url.startsWith("blob:")) finish(url)
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = request.url.toString()
                        if (clicked) {
                            Log.i(TAG, "Request after click: $url")
                            if (url.endsWith(".pdf") || url.contains("/pdf") || url.contains("download")) {
                                if (SKIP_URL_PATTERNS.none { url.contains(it) }) {
                                    Log.i(TAG, "PDF request detected: $url")
                                    finish(url)
                                }
                            }
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        Log.i(TAG, "Page finished: $url")
                        if (pollCount == 0 && !clicked) {
                            mainHandler.postDelayed({ pollForButton() }, 5000)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        if (!resumed) {
                            resumed = true
                            webView.destroy()
                        }
                    }
                }

                webView.loadUrl(receiptUrl)
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun printToPdf(receiptUrl: String, pdfFile: File): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                var resumed = false
                val mainHandler = Handler(Looper.getMainLooper())

                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = USER_AGENT
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }
                webView.layout(0, 0, PAGE_WIDTH_PX, 1920)

                fun finish(result: String?) {
                    if (!resumed) {
                        resumed = true
                        mainHandler.post { webView.destroy() }
                        continuation.resume(result)
                    }
                }

                fun doCapture() {
                    Log.i(TAG, "Print-to-PDF: capturing (url: ${webView.url})")
                    webView.evaluateJavascript(HIDE_CLUTTER_JS) {
                        mainHandler.postDelayed({
                            webView.evaluateJavascript("document.body.scrollHeight") { heightStr ->
                                val contentHeight =
                                    heightStr.trim('"').toIntOrNull() ?: 1920
                                Log.i(TAG, "Print-to-PDF: content height=$contentHeight")
                                if (contentHeight < MIN_RECEIPT_HEIGHT) {
                                    Log.w(TAG, "Print-to-PDF: page too small, likely a loading/error page")
                                    finish(null)
                                    return@evaluateJavascript
                                }
                                webView.layout(0, 0, PAGE_WIDTH_PX, contentHeight)
                                mainHandler.postDelayed({
                                    val path = renderToPdf(webView, pdfFile, contentHeight)
                                    finish(path)
                                }, 500)
                            }
                        }, 1000)
                    }
                }

                var captureRunnable: Runnable? = null

                fun schedulePdfCapture() {
                    captureRunnable?.let { mainHandler.removeCallbacks(it) }
                    val r = Runnable {
                        webView.evaluateJavascript(CLICK_VIEW_RECEIPT_JS) { result ->
                            Log.i(TAG, "Print-to-PDF: view-click result: $result")
                            if (result.contains("clicked")) {
                                mainHandler.postDelayed({ doCapture() }, 8000)
                            } else {
                                doCapture()
                            }
                        }
                    }
                    captureRunnable = r
                    mainHandler.postDelayed(r, 8000)
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        Log.i(TAG, "Print-to-PDF: page loaded: $url")
                        schedulePdfCapture()
                    }
                }

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        captureRunnable?.let { mainHandler.removeCallbacks(it) }
                        if (!resumed) {
                            resumed = true
                            webView.destroy()
                        }
                    }
                }

                webView.loadUrl(receiptUrl)
            }
        }

    private fun renderToPdf(webView: WebView, pdfFile: File, contentHeight: Int): String? {
        return try {
            val document = PdfDocument()
            val scale = PAGE_WIDTH_PT.toFloat() / PAGE_WIDTH_PX
            val totalHeightPt = (contentHeight * scale).toInt()
            var topPt = 0
            var pageNum = 1

            while (topPt < totalHeightPt && pageNum <= 20) {
                val heightPt = minOf(PAGE_HEIGHT_PT, totalHeightPt - topPt)
                val pageInfo =
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, heightPt, pageNum).create()
                val page = document.startPage(pageInfo)
                page.canvas.translate(0f, -topPt.toFloat())
                page.canvas.scale(scale, scale)
                webView.draw(page.canvas)
                document.finishPage(page)
                topPt += PAGE_HEIGHT_PT
                pageNum++
            }

            pdfFile.outputStream().use { document.writeTo(it) }
            document.close()

            if (pdfFile.exists() && pdfFile.length() > 0) {
                Log.i(TAG, "Print-to-PDF success: ${pdfFile.length()} bytes, ${pageNum - 1} pages")
                pdfFile.absolutePath
            } else {
                pdfFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "renderToPdf error", e)
            pdfFile.delete()
            null
        }
    }

    companion object {
        private const val TAG = "ReceiptPdfDownloader"
        private val webViewMutex = Mutex()
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        private const val PAGE_WIDTH_PX = 1080
        private const val PAGE_WIDTH_PT = 595  // A4
        private const val PAGE_HEIGHT_PT = 842  // A4
        private const val MIN_RECEIPT_HEIGHT = 800

        private val SKIP_URL_PATTERNS = listOf("privacy", "policy", "terms-of-use", "cookie")

        private val CLICK_DOWNLOAD_JS = """
            (function() {
                var selectors = [
                    'button.iconDownlond',
                    'button[class*="ownlond"]',
                    'button[class*="ownload"]',
                    'a[class*="download"]',
                    'a[download]',
                    'button.download-btn',
                    'a.download-btn'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el) { el.click(); return 'clicked:' + selectors[i]; }
                }
                var buttons = document.querySelectorAll('button');
                var info = 'buttons=' + buttons.length;
                for (var j = 0; j < Math.min(buttons.length, 5); j++) {
                    info += '|' + buttons[j].className + ':' + (buttons[j].textContent || '').substring(0, 30);
                }
                return 'not_found:' + info;
            })()
        """.trimIndent()

        private val CLICK_VIEW_RECEIPT_JS = """
            (function() {
                var buttons = document.querySelectorAll('button');
                for (var i = 0; i < buttons.length; i++) {
                    var text = (buttons[i].textContent || '').trim();
                    if (text.indexOf('חשבונית') >= 0 || text.indexOf('קבלה') >= 0) {
                        buttons[i].click();
                        return 'clicked:' + text.substring(0, 40);
                    }
                }
                var links = document.querySelectorAll('a');
                for (var i = 0; i < links.length; i++) {
                    var text = (links[i].textContent || '').trim();
                    if (text.indexOf('חשבונית') >= 0 || text.indexOf('קבלה') >= 0) {
                        links[i].click();
                        return 'clicked:' + text.substring(0, 40);
                    }
                }
                return 'not_found';
            })()
        """.trimIndent()

        private val HIDE_CLUTTER_JS = """
            (function() {
                var hide = ['header', 'footer', 'nav', '.cookie-banner', '.consent-banner',
                    '.marketing', '[class*="opt-out"]', '[class*="opt-in"]', '[class*="subscribe"]'];
                hide.forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(el) {
                        el.style.display = 'none';
                    });
                });
                document.body.style.margin = '0';
                document.body.style.padding = '8px';
                return 'cleaned';
            })()
        """.trimIndent()
    }
}
