# The JS bridge is called by name from the WebView fallback.
-keepclassmembers class com.pursuitiron.app.engine.WebViewEngine$Bridge { @android.webkit.JavascriptInterface <methods>; }
