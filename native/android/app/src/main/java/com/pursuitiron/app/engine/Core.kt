package com.pursuitiron.app.engine

import android.content.Context
import com.pursuitiron.app.data.StoreFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The app core (native/core, bundled as assets/pi-core.js) behind one serialized entry point.
 * Every call goes through PICore.call(method, argsJson); after a call that changed the store or
 * the live workout, the new text is written to disk before the call returns.
 */
class Core private constructor(private val engine: JsEngine, private val files: StoreFiles) {
    private val mutex = Mutex()
    val runtime: String get() = engine.name

    /* Non-cancellable once started: a screen that closes mid-call (a sheet's scope, a restarted effect)
       must not cut off the write that follows a change the engine already made in memory. */
    /* Runs on Dispatchers.Default: the engine reply is parsed into JSONObjects here (a history list or a live session
       is tens to hundreds of KB), and that parse used to happen on the main thread because every caller is a
       viewModelScope coroutine. Results come back to the caller's dispatcher, so Compose state is still set on Main. */
    suspend fun call(method: String, args: JSONObject = JSONObject()): Any? = withContext(Dispatchers.Default) { mutex.withLock { withContext(NonCancellable) {
        val res = invoke(method, args.toString())
        val dirty = res.optJSONObject("dirty")
        if (dirty?.optBoolean("store") == true) {
            files.writeStore(invoke("exportStore", "").getString("value"))
        }
        if (dirty?.optBoolean("live") == true) {
            files.writeLive(invoke("exportLive", "").optString("value", ""))
        }
        res.opt("value").takeUnless { it == JSONObject.NULL }
    } } }

    suspend fun obj(method: String, args: JSONObject = JSONObject()): JSONObject? = call(method, args) as? JSONObject

    /** The store as text, for backups and the share sheet. */
    suspend fun exportStoreText(): String = withContext(Dispatchers.Default) { mutex.withLock { invoke("exportStore", "").getString("value") } }

    private suspend fun invoke(method: String, args: String): JSONObject {
        val raw = engine.eval("PICore.call(${JSONObject.quote(method)}, ${JSONObject.quote(args)})")
        val res = JSONObject(raw)
        if (!res.optBoolean("ok")) throw CoreException(res.optString("error", "Engine call failed: $method"))
        return res
    }

    fun close() = engine.close()

    companion object {
        suspend fun start(context: Context): Core {
            val app = context.applicationContext
            val bundle = withContext(Dispatchers.IO) {
                app.assets.open("pi-core.js").use { String(it.readBytes(), Charsets.UTF_8) }
            }
            val engine: JsEngine = (if (SandboxEngine.isSupported()) runCatching { SandboxEngine.create(app) }.getOrNull() else null)
                ?: WebViewEngine.create(app)
            engine.eval(bundle + "\n;'loaded'")
            val files = StoreFiles(app)
            val core = Core(engine, files)
            val init = JSONObject()
                .put("store", files.readStore() ?: JSONObject.NULL)
                .put("live", files.readLive() ?: JSONObject.NULL)
            core.call("init", init)
            return core
        }
    }
}
