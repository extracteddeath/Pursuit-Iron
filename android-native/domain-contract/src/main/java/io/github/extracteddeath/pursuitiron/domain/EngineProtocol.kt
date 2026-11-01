package io.github.extracteddeath.pursuitiron.domain

/**
 * Protocol schema only. Android runtime integration remains disabled until
 * canonical JS parity and signed source-closure verification are complete.
 */
object EngineProtocol {
    const val VERSION: Int = 1
    const val METHOD_HANDSHAKE: String = "handshake"
    const val METHOD_CATALOG_SUMMARY: String = "catalogSummary"
}

data class EngineRequest(
    val protocolVersion: Int = EngineProtocol.VERSION,
    val id: String,
    val method: String,
    val params: Map<String, String> = emptyMap()
)

data class EngineError(val code: String, val message: String)

sealed interface EngineResponse<out T> {
    data class Success<T>(val id: String, val result: T) : EngineResponse<T>
    data class Failure(val id: String?, val error: EngineError) : EngineResponse<Nothing>
}

data class EngineHandshake(
    val protocolVersion: Int,
    val engineSource: String,
    val engineCertified: Boolean,
    val capabilities: List<String>
)
