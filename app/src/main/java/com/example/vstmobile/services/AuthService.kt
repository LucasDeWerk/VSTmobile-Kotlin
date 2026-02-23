package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val AUTH_TAG = "VST_AUTH"
private const val BASE_URL = "https://compras.vstsolution.com"

class AuthService {

    /**
     * PASSO 1: Verificar Código da Empresa
     * POST /vstauth
     * Body: { CODIGO_VST: "VST001" }
     * Retorna: companyToken (JWT)
     */
    suspend fun verifyCompanyCode(companyCode: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/vstauth"
            val body = JSONObject().apply {
                put("CODIGO_VST", companyCode)
            }.toString()

            Log.i(AUTH_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(AUTH_TAG, "📡 PASSO 1 - Verificando empresa")
            Log.i(AUTH_TAG, "➡️  POST $url")
            Log.i(AUTH_TAG, "Body: $body")

            val startTime = System.currentTimeMillis()
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.outputStream.bufferedWriter().use { it.write(body) }

            val statusCode = conn.responseCode
            val duration = System.currentTimeMillis() - startTime

            return@withContext if (statusCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                Log.i(AUTH_TAG, "✅ PASSO 1 OK [$statusCode] ${duration}ms")
                Log.i(AUTH_TAG, "Response: $response")
                Log.i(AUTH_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                val json = JSONObject(response)
                val token = json.optString("token", json.optString("TOKEN", ""))
                AuthResult(success = true, token = token, rawResponse = response)
            } else {
                val error = try { conn.errorStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "Erro $statusCode" }
                Log.e(AUTH_TAG, "❌ PASSO 1 FALHOU [$statusCode] - $error")
                Log.e(AUTH_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                val msg = try { JSONObject(error).optString("message", "Código da empresa inválido") } catch (_: Exception) { "Código da empresa inválido" }
                AuthResult(success = false, error = msg)
            }
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "❌ PASSO 1 EXCEPTION: ${e.message}")
            Log.e(AUTH_TAG, Log.getStackTraceString(e))
            AuthResult(success = false, error = e.message ?: "Erro ao verificar empresa")
        }
    }

    /**
     * PASSO 2: Validar Usuário
     * POST /validausuario
     * Header: Authorization: Bearer {companyToken}
     * Body: { ID_USUARIO: 123, CODIGO_VST: "VST001" }
     * Retorna: dados do usuário
     */
    suspend fun validateUser(
        userCode: String,
        companyCode: String,
        companyToken: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/validausuario"
            val body = JSONObject().apply {
                put("ID_USUARIO", userCode.trim().toIntOrNull() ?: userCode.trim())
                put("CODIGO_VST", companyCode)
            }.toString()

            Log.i(AUTH_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(AUTH_TAG, "📡 PASSO 2 - Validando usuário")
            Log.i(AUTH_TAG, "➡️  POST $url")
            Log.i(AUTH_TAG, "Body: $body")

            val startTime = System.currentTimeMillis()
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $companyToken")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.outputStream.bufferedWriter().use { it.write(body) }

            val statusCode = conn.responseCode
            val duration = System.currentTimeMillis() - startTime

            return@withContext if (statusCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                Log.i(AUTH_TAG, "✅ PASSO 2 OK [$statusCode] ${duration}ms")
                Log.i(AUTH_TAG, "Response: $response")
                Log.i(AUTH_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                val json = JSONObject(response)
                val dataArray = json.optJSONArray("data")
                var userName = ""
                var userId = ""
                if (dataArray != null && dataArray.length() > 0) {
                    val user = dataArray.getJSONObject(0)
                    userName = user.optString("USUARIO", "")
                    userId = user.optString("ID_USUARIO", userCode)
                }
                AuthResult(success = true, userName = userName, userId = userId, rawResponse = response)
            } else {
                val error = try { conn.errorStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "Erro $statusCode" }
                Log.e(AUTH_TAG, "❌ PASSO 2 FALHOU [$statusCode] - $error")
                Log.e(AUTH_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                val msg = try { JSONObject(error).optString("message", "Usuário não encontrado") } catch (_: Exception) { "Usuário não encontrado" }
                AuthResult(success = false, error = msg)
            }
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "❌ PASSO 2 EXCEPTION: ${e.message}")
            Log.e(AUTH_TAG, Log.getStackTraceString(e))
            AuthResult(success = false, error = e.message ?: "Erro ao validar usuário")
        }
    }

    /**
     * PASSO 3: Login Final
     * POST /vstlogin
     * Header: Authorization: Bearer {companyToken}
     * Body: { CODIGO_VST, ID_USUARIO, SENHA, USUARIO }
     * Retorna: userToken (JWT permanente)
     */
    suspend fun login(
        companyCode: String,
        userId: String,
        password: String,
        userName: String,
        companyToken: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/vstlogin"
            val body = JSONObject().apply {
                put("CODIGO_VST", companyCode)
                put("ID_USUARIO", userId.trim().toIntOrNull() ?: userId.trim())
                put("SENHA", password)
                put("USUARIO", userName)
            }.toString()

            Log.i(AUTH_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(AUTH_TAG, "📡 PASSO 3 - Login final")
            Log.i(AUTH_TAG, "➡️  POST $url")
            Log.i(AUTH_TAG, "Body: ${body.replace(password, "******")}")

            val startTime = System.currentTimeMillis()
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $companyToken")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.outputStream.bufferedWriter().use { it.write(body) }

            val statusCode = conn.responseCode
            val duration = System.currentTimeMillis() - startTime

            return@withContext if (statusCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                Log.i(AUTH_TAG, "✅ PASSO 3 OK [$statusCode] ${duration}ms")
                Log.i(AUTH_TAG, "Response: $response")
                Log.i(AUTH_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                val json = JSONObject(response)
                val userToken = json.optString("token", json.optString("TOKEN", ""))
                AuthResult(success = true, token = userToken, rawResponse = response)
            } else {
                val error = try { conn.errorStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "Erro $statusCode" }
                Log.e(AUTH_TAG, "❌ PASSO 3 FALHOU [$statusCode] - $error")
                Log.e(AUTH_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                val msg = try { JSONObject(error).optString("message", "Senha inválida") } catch (_: Exception) { "Senha inválida" }
                AuthResult(success = false, error = msg)
            }
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "❌ PASSO 3 EXCEPTION: ${e.message}")
            Log.e(AUTH_TAG, Log.getStackTraceString(e))
            AuthResult(success = false, error = e.message ?: "Erro ao fazer login")
        }
    }
}

data class AuthResult(
    val success: Boolean,
    val token: String = "",
    val userName: String = "",
    val userId: String = "",
    val rawResponse: String = "",
    val error: String? = null
)

