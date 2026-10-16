package com.pursuitiron.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * Fallback for devices whose WebView has no JavaScript sandbox: a headless WebView on about:blank.
 * It never loads a URL, never gets a JavaScript interface, and never touches the network.
 */
class WebViewEngine private constructor(private val web: WebView) : JsEngine {
    override val name = "webview"

    override suspend fun eval(script: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            web.evaluateJavascript(script) { raw ->
                // evaluateJavascript hands back the result JSON-encoded; a string arrives quoted.
                val value = runCatching { JSONArray("[$raw]").get(0) }.getOrNull()
                cont.resume(if (value is String) value else raw ?: "null")
            }
        }
    }

    override fun close() { web.destroy() }

    class Bridge // reserved; no interface is exposed to page script

    companion object {
        @SuppressLint("SetJavaScriptEnabled")
        suspend fun create(context: Context): WebViewEngine = withContext(Dispatchers.Main) {
            val web = WebView(context.applicationContext)
            web.settings.javaScriptEnabled = true
            web.settings.allowFileAccess = false
            web.settings.allowContentAccess = false
            web.settings.blockNetworkLoads = true
            web.loadUrl("about:blank")
            WebViewEngine(web)
        }
    }
}
