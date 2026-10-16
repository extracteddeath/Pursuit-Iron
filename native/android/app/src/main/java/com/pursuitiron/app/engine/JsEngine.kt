package com.pursuitiron.app.engine

/** A JavaScript runtime that evaluates a script and returns its string result. */
interface JsEngine {
    val name: String
    suspend fun eval(script: String): String
    fun close()
}

class CoreException(message: String) : Exception(message)
