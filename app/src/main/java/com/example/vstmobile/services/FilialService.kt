package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val FILIAL_TAG = "VST_FILIAL"
private const val BASE_URL = "https://compras.vstsolution.com"

data class Filial(
    val idFilial: Int,
    val idEmpresa: Int,
    val nomeFilial: String,
    val identificacaoInterna: String,
    val cidade: String = "",
    val estado: String = ""
)

class FilialService {

    /**
     * Buscar filiais do usuário
     * POST /vstfilialusuario/{userId}
     * Header: Authorization: Bearer {userToken}
     */
    suspend fun fetchUserFiliais(userId: String, userToken: String): FilialResult =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/vstfilialusuario/$userId"

                Log.i(FILIAL_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.i(FILIAL_TAG, "📡 Buscando filiais do usuário $userId")
                Log.i(FILIAL_TAG, "➡️  POST $url")

                val startTime = System.currentTimeMillis()
                val conn = URL(url).openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $userToken")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val statusCode = conn.responseCode
                val duration = System.currentTimeMillis() - startTime

                return@withContext if (statusCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    Log.i(FILIAL_TAG, "✅ Filiais OK [$statusCode] ${duration}ms")
                    Log.i(FILIAL_TAG, "Response: $response")
                    Log.i(FILIAL_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                    val json = JSONObject(response)
                    val dataArray = json.optJSONArray("data")
                    val filiais = mutableListOf<Filial>()

                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val obj = dataArray.getJSONObject(i)
                            val idFilial = obj.optInt("ID_FILIAL", 0)
                            val nome = obj.optString("FANTASIA", "")
                                .ifEmpty { obj.optString("NOME", "") }
                                .ifEmpty { obj.optString("NOME_FILIAL", "") }
                                .ifEmpty { "Filial $idFilial" }
                            filiais.add(
                                Filial(
                                    idFilial = idFilial,
                                    idEmpresa = obj.optInt("ID_EMPRESA", 0),
                                    nomeFilial = nome,
                                    identificacaoInterna = obj.optString("IDENTIFICACAO_INTERNA", ""),
                                    cidade = obj.optString("CIDADE", ""),
                                    estado = obj.optString("ESTADO", "")
                                )
                            )
                        }
                    }

                    Log.i(FILIAL_TAG, "📋 ${filiais.size} filial(is) carregada(s)")
                    FilialResult(success = true, filiais = filiais)
                } else {
                    val error = try {
                        conn.errorStream.bufferedReader().use { it.readText() }
                    } catch (_: Exception) { "Erro $statusCode" }
                    Log.e(FILIAL_TAG, "❌ Filiais FALHOU [$statusCode] - $error")
                    Log.e(FILIAL_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    FilialResult(success = false, error = "Erro ao buscar filiais: $statusCode")
                }
            } catch (e: Exception) {
                Log.e(FILIAL_TAG, "❌ EXCEPTION ao buscar filiais: ${e.message}")
                Log.e(FILIAL_TAG, Log.getStackTraceString(e))
                FilialResult(success = false, error = e.message ?: "Erro ao buscar filiais")
            }
        }
}

data class FilialResult(
    val success: Boolean,
    val filiais: List<Filial> = emptyList(),
    val error: String? = null
)

