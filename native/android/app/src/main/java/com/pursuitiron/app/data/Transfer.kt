package com.pursuitiron.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Data arriving from outside the app: the web app's one-tap handoff link, a shared or opened
 * backup file. Nothing is imported here; the caller asks the user first.
 */
data class ImportOffer(
    val store: String,
    val live: String?,
    val sessions: Int,
    val programs: Int,
    val source: String,
)

object Transfer {
    /** The web app's handoff page, opened in the user's browser where the old app's data lives. */
    const val HANDOFF_URL = "https://extracteddeath.github.io/pi-transfer.html"

    /** The old app ran in Chrome, so its data is in Chrome: prefer Chrome, fall back to any browser. */
    fun openTransferPage(context: Context) {
        val uri = Uri.parse(HANDOFF_URL + "?app=" + context.packageName)
        val chrome = Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.chrome").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chrome) }.onFailure {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    fun fromIntent(context: Context, intent: Intent?): ImportOffer? {
        if (intent == null) return null
        return runCatching {
            when (intent.action) {
                Intent.ACTION_VIEW -> {
                    val data = intent.data ?: return null
                    if (data.scheme == "pursuitiron") fromHandoffUri(data) else readUri(context, data)?.let { parse(it, "file") }
                }
                Intent.ACTION_SEND -> {
                    val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    val text = stream?.let { readUri(context, it) } ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                    text?.let { parse(it, "share") }
                }
                else -> null
            }
        }.getOrNull()
    }

    fun readUri(context: Context, uri: Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }

    /** pursuitiron://handoff?d=<base64url of raw-deflated JSON> */
    private fun fromHandoffUri(uri: Uri): ImportOffer? {
        val d = uri.getQueryParameter("d") ?: return null
        val packed = Base64.decode(d, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return parse(inflate(packed), "web app")
    }

    private fun inflate(bytes: ByteArray): String {
        val inf = Inflater(true)
        inf.setInput(bytes)
        val out = ByteArrayOutputStream(bytes.size * 6)
        val buf = ByteArray(16 * 1024)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inf.end()
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /** Accepts the handoff envelope ({kind:"pi-handoff", store, live}) or a plain backup file. */
    fun parse(text: String, source: String): ImportOffer? {
        val root = JSONObject(text.trim())
        val isEnvelope = root.optString("kind") == "pi-handoff"
        val storeText = if (isEnvelope) root.optString("store", "") else text
        if (storeText.isBlank()) return null
        val store = JSONObject(storeText)
        val sessions = store.optJSONArray("history")?.length() ?: 0
        val programs = store.optJSONArray("saved")?.length() ?: 0
        if (!store.has("history") && !store.has("saved")) return null
        val live = if (isEnvelope) root.optString("live", "").takeIf { it.isNotBlank() } else null
        return ImportOffer(storeText, live, sessions, programs, source)
    }
}
