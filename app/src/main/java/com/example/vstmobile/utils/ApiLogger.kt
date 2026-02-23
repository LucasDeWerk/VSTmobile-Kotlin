package com.example.vstmobile.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Logger centralizado para todas as requisições e respostas de API
 * Logga automaticamente no Logcat do Android Studio
 * Filtrar no Logcat pelo TAG: VST_API
 */
object ApiLogger {

    private const val TAG = "VST_API"

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())

    /**
     * Logar uma requisição HTTP no Logcat
     */
    fun logRequest(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = ""
    ) {
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "📡 REQUEST [${timestamp()}]")
        Log.i(TAG, "➡️  $method $url")
        if (headers.isNotEmpty()) {
            Log.i(TAG, "Headers:")
            headers.forEach { (key, value) -> Log.i(TAG, "  ├─ $key: $value") }
        }
        if (body.isNotEmpty()) {
            Log.i(TAG, "Body:")
            body.chunked(3000).forEach { chunk -> Log.i(TAG, chunk) }
        }
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * Logar uma resposta HTTP no Logcat
     */
    fun logResponse(
        method: String,
        url: String,
        statusCode: Int,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        durationMs: Long = 0
    ) {
        val statusEmoji = when {
            statusCode in 200..299 -> "✅"
            statusCode in 300..399 -> "🔄"
            statusCode in 400..499 -> "⚠️"
            else -> "❌"
        }
        val logLevel = when {
            statusCode in 200..299 -> Log.INFO
            statusCode in 400..499 -> Log.WARN
            else -> Log.ERROR
        }
        Log.println(logLevel, TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.println(logLevel, TAG, "$statusEmoji RESPONSE [${timestamp()}] ${durationMs}ms")
        Log.println(logLevel, TAG, "⬅️  $method $url | Status: $statusCode")
        if (headers.isNotEmpty()) {
            Log.println(logLevel, TAG, "Headers:")
            headers.forEach { (key, value) -> Log.println(logLevel, TAG, "  ├─ $key: $value") }
        }
        if (body.isNotEmpty()) {
            Log.println(logLevel, TAG, "Body:")
            val formattedBody = try {
                org.json.JSONObject(body).toString(2)
            } catch (_: Exception) {
                try { org.json.JSONArray(body).toString(2) } catch (_: Exception) { body }
            }
            formattedBody.chunked(3000).forEach { chunk -> Log.println(logLevel, TAG, chunk) }
        }
        Log.println(logLevel, TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * Logar um erro de API no Logcat
     */
    fun logError(
        method: String,
        url: String,
        errorMessage: String,
        exception: Exception? = null
    ) {
        Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.e(TAG, "❌ ERROR [${timestamp()}]")
        Log.e(TAG, "🔴 $method $url")
        Log.e(TAG, "Error: $errorMessage")
        if (exception != null) {
            Log.e(TAG, "Exception: ${exception.javaClass.simpleName}: ${exception.message}")
            Log.e(TAG, Log.getStackTraceString(exception))
        }
        Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * Logar informações gerais
     */
    fun logInfo(message: String) { Log.i(TAG, "ℹ️  [${timestamp()}] $message") }

    /**
     * Logar debug
     */
    fun logDebug(message: String) { Log.d(TAG, "🔍 $message") }

    /**
     * Logar warning
     */
    fun logWarning(message: String) { Log.w(TAG, "⚠️  $message") }
}
