package com.pursuitiron.app.engine

import android.content.Context
import androidx.concurrent.futures.await
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox

/**
 * The engine runs in WebView's isolated JavaScript sandbox: a separate process with no DOM,
 * network or storage. Requires the "no transaction limit" feature because the engine bundle and
 * the store both exceed the Binder transaction size.
 */
class SandboxEngine private constructor(
    private val sandbox: JavaScriptSandbox,
    private val isolate: JavaScriptIsolate,
) : JsEngine {
    override val name = "sandbox"

    override suspend fun eval(script: String): String = isolate.evaluateJavaScriptAsync(script).await()

    override fun close() {
        runCatching { isolate.close() }
        runCatching { sandbox.close() }
    }

    companion object {
        fun isSupported(): Boolean = runCatching { JavaScriptSandbox.isSupported() }.getOrDefault(false)

        /** Returns null when this device's sandbox lacks a feature the engine needs. */
        suspend fun create(context: Context): SandboxEngine? {
            val sandbox = JavaScriptSandbox.createConnectedInstanceAsync(context.applicationContext).await()
            val needed = listOf(
                JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN,
                JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT,
            )
            if (!needed.all { sandbox.isFeatureSupported(it) }) {
                sandbox.close()
                return null
            }
            val params = IsolateStartupParameters()
            if (sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) {
                params.maxHeapSizeBytes = 384L * 1024 * 1024
            }
            return SandboxEngine(sandbox, sandbox.createIsolate(params))
        }
    }
}
