package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val LC_TAG = "VST_LIMITE_CREDITO"
private const val LC_BASE = "https://compras.vstsolution.com"

data class ClienteLC(
    val idClifor: Int,
    val nomeRazao: String,
    val apelidoFantasia: String,
    val cpfCnpj: String,
    val email: String,
    val telefone: String
)

data class LimiteCreditoAtual(
    val limiteCredito: Double,
    val venctoLimite: String  // formato DD-MM-YYYY
)

data class LcResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

class LimiteCreditoService(private val userToken: String) {

    private fun get(url: String): String? {
        return try {
            Log.i(LC_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(LC_TAG, "➡️  GET $url")
            val start = System.currentTimeMillis()
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $userToken")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val status = conn.responseCode
            val duration = System.currentTimeMillis() - start
            if (status == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                Log.i(LC_TAG, "✅ $status (${duration}ms) | ${body.length} chars")
                Log.d(LC_TAG, "📦 Body: ${body.take(500)}")
                body
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
                Log.e(LC_TAG, "❌ HTTP $status (${duration}ms) | $err")
                null
            }
        } catch (e: Exception) {
            Log.e(LC_TAG, "💥 Exceção GET: ${e.message}", e)
            null
        }
    }

    private fun put(url: String, bodyJson: String): String? {
        return try {
            Log.i(LC_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(LC_TAG, "➡️  PUT $url")
            Log.d(LC_TAG, "📤 Body: $bodyJson")
            val start = System.currentTimeMillis()
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $userToken")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.outputStream.use { it.write(bodyJson.toByteArray()) }
            val status = conn.responseCode
            val duration = System.currentTimeMillis() - start
            if (status in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                Log.i(LC_TAG, "✅ $status (${duration}ms)")
                body
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
                Log.e(LC_TAG, "❌ HTTP $status (${duration}ms) | $err")
                null
            }
        } catch (e: Exception) {
            Log.e(LC_TAG, "💥 Exceção PUT: ${e.message}", e)
            null
        }
    }

    // ── Buscar limite máximo do usuário ───────────────────────────────────────
    suspend fun fetchLimiteMaximo(idEmpresa: String, idFilial: String): LcResult<Double> =
        withContext(Dispatchers.IO) {
            val url = "$LC_BASE/limitecreditousuario/$idEmpresa/idfilial/$idFilial"
            Log.i(LC_TAG, "🎯 fetchLimiteMaximo | empresa=$idEmpresa filial=$idFilial")
            val body = get(url) ?: return@withContext LcResult(false, error = "Erro de rede")
            return@withContext try {
                val j = JSONObject(body)
                val valor = j.optString("data", "0").replace(",", ".").toDoubleOrNull() ?: 0.0
                Log.i(LC_TAG, "✅ Limite máximo: $valor")
                LcResult(true, valor)
            } catch (e: Exception) {
                Log.e(LC_TAG, "💥 Parse limite máximo: ${e.message}", e)
                LcResult(false, error = e.message)
            }
        }

    // ── Buscar clientes ───────────────────────────────────────────────────────
    suspend fun fetchClientes(idEmpresa: String, termo: String): LcResult<List<ClienteLC>> =
        withContext(Dispatchers.IO) {
            val encoded = java.net.URLEncoder.encode(termo.trim(), "UTF-8")
            val url = "$LC_BASE/clientes/$idEmpresa?cliente=$encoded"
            Log.i(LC_TAG, "🎯 fetchClientes | empresa=$idEmpresa termo=$termo")
            val body = get(url) ?: return@withContext LcResult(false, error = "Erro de rede")
            return@withContext try {
                val root = JSONObject(body)
                val arr = root.optJSONArray("data") ?: JSONArray()
                val lista = (0 until arr.length()).map {
                    val obj = arr.getJSONObject(it)
                    ClienteLC(
                        idClifor        = obj.optInt("ID_CLIFOR", 0),
                        nomeRazao       = obj.optString("NOME_RAZAO", ""),
                        apelidoFantasia = obj.optString("APELIDO_FANTASIA", ""),
                        cpfCnpj         = obj.optString("CPF_CNPJ", ""),
                        email           = obj.optString("EMAIL", ""),
                        telefone        = obj.optString("FONERESIDENCIAL", "")
                    )
                }
                Log.i(LC_TAG, "✅ Clientes: ${lista.size}")
                LcResult(true, lista)
            } catch (e: Exception) {
                Log.e(LC_TAG, "💥 Parse clientes: ${e.message}", e)
                LcResult(false, error = e.message)
            }
        }

    // ── Buscar limite atual do cliente ────────────────────────────────────────
    suspend fun fetchLimiteAtual(idEmpresa: String, idClifor: Int): LcResult<LimiteCreditoAtual> =
        withContext(Dispatchers.IO) {
            val url = "$LC_BASE/limitecreditocliente/$idEmpresa/idclifor/$idClifor"
            Log.i(LC_TAG, "🎯 fetchLimiteAtual | empresa=$idEmpresa cliente=$idClifor")
            val body = get(url) ?: return@withContext LcResult(false, error = "Erro de rede")
            return@withContext try {
                val root = JSONObject(body)
                val arr = root.optJSONArray("data") ?: JSONArray()
                if (arr.length() == 0) return@withContext LcResult(true, LimiteCreditoAtual(0.0, ""))
                val obj = arr.getJSONObject(0)
                val limite = obj.optString("LIMITECREDITO", "0").replace(",", ".").toDoubleOrNull() ?: 0.0
                val vencto = obj.optString("VENCTOLIMITE", "")
                Log.i(LC_TAG, "✅ Limite atual: $limite | Vencto: $vencto")
                LcResult(true, LimiteCreditoAtual(limite, vencto))
            } catch (e: Exception) {
                Log.e(LC_TAG, "💥 Parse limite atual: ${e.message}", e)
                LcResult(false, error = e.message)
            }
        }

    // ── Salvar limite de crédito ──────────────────────────────────────────────
    suspend fun salvarLimite(
        idEmpresa: String,
        idFilial: String,
        idClifor: Int,
        limiteCredito: Double,
        venctoLimite: String  // formato YYYY-MM-DD
    ): LcResult<Boolean> = withContext(Dispatchers.IO) {
        val url = "$LC_BASE/limitecreditocliente/$idEmpresa/idfilial/$idFilial/idclifor/$idClifor"
        val bodyJson = JSONObject().apply {
            put("data", JSONArray().apply {
                put(JSONObject().apply {
                    put("LIMITECREDITO", limiteCredito.toString())
                    put("VENCTOLIMITE", venctoLimite)
                })
            })
        }.toString()
        Log.i(LC_TAG, "🎯 salvarLimite | empresa=$idEmpresa filial=$idFilial cliente=$idClifor limite=$limiteCredito vencto=$venctoLimite")
        put(url, bodyJson) ?: return@withContext LcResult(false, error = "Erro de rede ao salvar")
        Log.i(LC_TAG, "✅ Limite salvo com sucesso")
        LcResult(true, true)
    }
}

