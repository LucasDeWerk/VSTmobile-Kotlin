package com.example.vstmobile.services

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import org.json.JSONArray

private const val TAG = "VST_API"

private fun logRequest(method: String, url: String, headers: Map<String, String> = emptyMap(), body: String = "") {
    Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    Log.i(TAG, "📡 REQUEST")
    Log.i(TAG, "➡️  $method $url")
    if (headers.isNotEmpty()) {
        Log.i(TAG, "Headers:")
        headers.forEach { (k, v) -> Log.i(TAG, "  ├─ $k: $v") }
    }
    if (body.isNotEmpty()) {
        Log.i(TAG, "Body:")
        body.chunked(3000).forEach { Log.i(TAG, it) }
    }
    Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
}

private fun logResponse(method: String, url: String, statusCode: Int, body: String = "", durationMs: Long = 0) {
    val emoji = when { statusCode in 200..299 -> "✅"; statusCode in 400..499 -> "⚠️"; else -> "❌" }
    val level = when { statusCode in 200..299 -> Log.INFO; statusCode in 400..499 -> Log.WARN; else -> Log.ERROR }
    Log.println(level, TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    Log.println(level, TAG, "$emoji RESPONSE ${durationMs}ms | Status: $statusCode")
    Log.println(level, TAG, "⬅️  $method $url")
    if (body.isNotEmpty()) {
        Log.println(level, TAG, "Body:")
        val formatted = try { JSONObject(body).toString(2) } catch (_: Exception) { body }
        formatted.chunked(3000).forEach { Log.println(level, TAG, it) }
    }
    Log.println(level, TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
}

private fun logError(method: String, url: String, errorMessage: String, exception: Exception? = null) {
    Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    Log.e(TAG, "❌ ERROR | $method $url")
    Log.e(TAG, "Error: $errorMessage")
    if (exception != null) Log.e(TAG, Log.getStackTraceString(exception))
    Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
}

/**
 * Service para gerenciar operações de dispositivo
 * Segue a lógica exata do guia: verificação, registro e validação de dispositivos
 */
class DeviceService(private val context: Context) {

    companion object {
        private const val PREFERENCES_NAME = "device_preferences"
    }

    /**
     * Verificar status do dispositivo atual
     * Segue o fluxo exato do guia:
     * 1. Busca GUID no AsyncStorage local
     * 2. Se não existe, retorna isRegistered: false
     * 3. Se existe, verifica na API
     */
    suspend fun checkCurrentDevice(): DeviceCheckResult = withContext(Dispatchers.IO) {
        try {
            // PASSO 1: Tentar recuperar GUID do local storage
            val guidResult = getDeviceGUID()

            if (!guidResult.success || guidResult.guid.isEmpty()) {
                // Não tem GUID salvo = dispositivo novo
                return@withContext DeviceCheckResult(
                    success = true,
                    isRegistered = false,
                    message = "Nenhum aparelho cadastrado"
                )
            }

            // PASSO 2: Verificar se o GUID ainda é válido na API
            val verifyResult = verifyDevice(guidResult.guid)

            if (!verifyResult.success) {
                return@withContext verifyResult
            }

            // PASSO 3: Retornar resultado
            return@withContext DeviceCheckResult(
                success = true,
                isRegistered = verifyResult.isRegistered,
                guid = guidResult.guid,
                message = if (verifyResult.isRegistered) "Dispositivo registrado" else "Dispositivo não registrado"
            )

        } catch (error: Exception) {
            error.printStackTrace()
            return@withContext DeviceCheckResult(
                success = false,
                error = error.message ?: "Erro desconhecido"
            )
        }
    }

    /**
     * Verificar dispositivo na API
     * Chamada: GET /dispositivo/{guid}
     * Respostas possíveis:
     * - 200 OK: Dispositivo existe e está registrado
     * - 404 Not Found: Dispositivo não existe
     * - 401 Unauthorized: Dispositivo não autorizado
     */
    suspend fun verifyDevice(guid: String): DeviceCheckResult = withContext(Dispatchers.IO) {
        try {
            if (guid.isEmpty()) {
                return@withContext DeviceCheckResult(
                    success = false,
                    error = "GUID não fornecido"
                )
            }

            val url = "https://compras.vstsolution.com/dispositivo/$guid"
            val startTime = System.currentTimeMillis()

            // Log da requisição
            logRequest("GET", url, mapOf("Content-Type" to "application/json"))

            val urlConnection = URL(url).openConnection() as HttpsURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("Content-Type", "application/json")
            urlConnection.connectTimeout = 10000
            urlConnection.readTimeout = 10000

            val statusCode = urlConnection.responseCode
            val duration = System.currentTimeMillis() - startTime

            return@withContext when (statusCode) {
                200 -> {
                    val response = urlConnection.inputStream.bufferedReader().use { it.readText() }
                    logResponse("GET", url, statusCode, body = response, durationMs = duration)
                    DeviceCheckResult(success = true, isRegistered = true, data = response)
                }
                404 -> {
                    logResponse("GET", url, statusCode, durationMs = duration)
                    DeviceCheckResult(success = true, isRegistered = false)
                }
                401 -> {
                    logResponse("GET", url, statusCode, durationMs = duration)
                    DeviceCheckResult(success = false, error = "Dispositivo não autorizado. Aguarde a liberação.")
                }
                else -> throw Exception("Erro na verificação: $statusCode")
            }

        } catch (error: Exception) {
            logError("GET", "https://compras.vstsolution.com/dispositivo/$guid", error.message ?: "Erro desconhecido", error)
            return@withContext DeviceCheckResult(success = false, error = error.message ?: "Erro ao verificar dispositivo")
        }
    }

    /**
     * Registrar um novo dispositivo
     * Chamada: POST /dispositivo/{companyCode}
     * Body: { data: [{ APELIDO: string, WHATS: string }] }
     * Resposta: { data: "{GUID}" }
     */
    suspend fun registerDevice(
        companyCode: String,
        deviceAlias: String,
        phoneNumber: String
    ): DeviceRegisterResult = withContext(Dispatchers.IO) {
        try {
            val url = "https://compras.vstsolution.com/dispositivo/$companyCode"
            val startTime = System.currentTimeMillis()

            val bodyJson = JSONObject().apply {
                put("data", JSONArray().apply {
                    put(JSONObject().apply {
                        put("APELIDO", deviceAlias)
                        put("WHATS", phoneNumber)
                    })
                })
            }

            val bodyString = bodyJson.toString()

            // Log da requisição
            logRequest("POST", url, mapOf("Content-Type" to "application/json"), bodyString)

            val urlConnection = URL(url).openConnection() as HttpsURLConnection
            urlConnection.requestMethod = "POST"
            urlConnection.setRequestProperty("Content-Type", "application/json")
            urlConnection.connectTimeout = 10000
            urlConnection.readTimeout = 10000
            urlConnection.doOutput = true

            urlConnection.outputStream.bufferedWriter().use {
                it.write(bodyString)
                it.flush()
            }

            val statusCode = urlConnection.responseCode
            val duration = System.currentTimeMillis() - startTime

            return@withContext if (statusCode == 200) {
                val response = urlConnection.inputStream.bufferedReader().use { it.readText() }
                logResponse("POST", url, statusCode, body = response, durationMs = duration)

                val jsonResponse = JSONObject(response)
                val guidRaw = jsonResponse.getString("data")
                val guid = guidRaw.replace("{", "").replace("}", "")

                saveDeviceGUID(guid)
                saveDeviceAlias(deviceAlias)
                saveDevicePhone(phoneNumber)

                DeviceRegisterResult(success = true, guid = guid, message = "Dispositivo registrado com sucesso")
            } else {
                logResponse("POST", url, statusCode, durationMs = duration)
                throw Exception("Erro ao registrar: $statusCode")
            }

        } catch (error: Exception) {
            logError("POST", "https://compras.vstsolution.com/dispositivo/$companyCode", error.message ?: "Erro desconhecido", error)
            return@withContext DeviceRegisterResult(success = false, error = error.message ?: "Erro ao registrar dispositivo")
        }
    }

    /**
     * Buscar empresas cadastradas para o dispositivo
     * Chamada: GET /dispositivoemp/{guid}
     */
    suspend fun getRegisteredCompanies(guid: String): CompaniesResult = withContext(Dispatchers.IO) {
        try {
            if (guid.isEmpty()) {
                return@withContext CompaniesResult(
                    success = false,
                    error = "GUID não encontrado"
                )
            }

            val url = "https://compras.vstsolution.com/dispositivoemp/$guid"
            val startTime = System.currentTimeMillis()

            // Log da requisição
            logRequest("GET", url, mapOf("Content-Type" to "application/json"))

            val urlConnection = URL(url).openConnection() as HttpsURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("Content-Type", "application/json")
            urlConnection.connectTimeout = 10000
            urlConnection.readTimeout = 10000

            val statusCode = urlConnection.responseCode
            val duration = System.currentTimeMillis() - startTime

            return@withContext if (statusCode == 200) {
                val response = urlConnection.inputStream.bufferedReader().use { it.readText() }
                logResponse("GET", url, statusCode, body = response, durationMs = duration)

                val jsonResponse = JSONObject(response)
                val dataArray = jsonResponse.getJSONArray("data")
                val companies = mutableListOf<CompanyData>()

                for (i in 0 until dataArray.length()) {
                    val obj = dataArray.getJSONObject(i)
                    companies.add(CompanyData(code = obj.getString("CODIGO"), name = obj.getString("NOME")))
                }

                CompaniesResult(success = true, companies = companies)
            } else {
                logResponse("GET", url, statusCode, durationMs = duration)
                throw Exception("Erro ao buscar empresas: $statusCode")
            }

        } catch (error: Exception) {
            logError("GET", "https://compras.vstsolution.com/dispositivoemp/$guid", error.message ?: "Erro desconhecido", error)
            return@withContext CompaniesResult(success = false, error = error.message ?: "Erro ao buscar empresas")
        }
    }

    // ===== Local Storage (SharedPreferences) =====

    suspend fun getDeviceGUID(): GUIDResult = withContext(Dispatchers.IO) {
        try {
            val sharedPref = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val guid = sharedPref.getString("deviceGUID", "") ?: ""

            return@withContext if (guid.isNotEmpty()) {
                GUIDResult(success = true, guid = guid)
            } else {
                GUIDResult(success = true, guid = "")
            }
        } catch (error: Exception) {
            return@withContext GUIDResult(success = false, error = error.message)
        }
    }

    suspend fun saveDeviceGUID(guid: String) = withContext(Dispatchers.IO) {
        val sharedPref = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            putString("deviceGUID", guid)
        }
    }

    suspend fun saveDeviceAlias(alias: String) = withContext(Dispatchers.IO) {
        val sharedPref = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            putString("deviceAlias", alias)
        }
    }

    suspend fun saveDevicePhone(phone: String) = withContext(Dispatchers.IO) {
        val sharedPref = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            putString("devicePhone", phone)
        }
    }
}

// ===== Data Classes =====

data class DeviceCheckResult(
    val success: Boolean,
    val isRegistered: Boolean = false,
    val guid: String = "",
    val data: String = "",
    val message: String = "",
    val error: String? = null
)

data class DeviceRegisterResult(
    val success: Boolean,
    val guid: String = "",
    val message: String = "",
    val error: String? = null
)

data class CompaniesResult(
    val success: Boolean,
    val companies: List<CompanyData> = emptyList(),
    val error: String? = null
)

data class CompanyData(
    val code: String,
    val name: String
)

data class GUIDResult(
    val success: Boolean,
    val guid: String = "",
    val error: String? = null
)

