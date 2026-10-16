package com.pursuitiron.app.ui.common

import org.json.JSONArray
import org.json.JSONObject

/* Read helpers for the core's view models: absent or null always reads as empty, never throws. */
fun JSONObject?.s(key: String, def: String = ""): String = if (this == null || isNull(key)) def else optString(key, def)
fun JSONObject?.i(key: String, def: Int = 0): Int = if (this == null || isNull(key)) def else optInt(key, def)
fun JSONObject?.d(key: String, def: Double = 0.0): Double = if (this == null || isNull(key)) def else optDouble(key, def)
fun JSONObject?.b(key: String, def: Boolean = false): Boolean = if (this == null || isNull(key)) def else optBoolean(key, def)
fun JSONObject?.o(key: String): JSONObject? = if (this == null || isNull(key)) null else optJSONObject(key)
fun JSONObject?.has(key: String): Boolean = this != null && !isNull(key)
fun JSONObject?.list(key: String): List<JSONObject> {
    val a: JSONArray = (if (this == null || isNull(key)) null else optJSONArray(key)) ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}
fun JSONObject?.strings(key: String): List<String> {
    val a: JSONArray = (if (this == null || isNull(key)) null else optJSONArray(key)) ?: return emptyList()
    return (0 until a.length()).map { a.optString(it) }
}
fun jsonOf(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply { pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
fun fmtNum(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else ((Math.round(v * 10) / 10.0)).toString()
fun mmss(sec: Long): String { val s = if (sec < 0) 0 else sec; return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }
