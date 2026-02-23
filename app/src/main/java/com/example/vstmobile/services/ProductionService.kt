package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val PROD_TAG = "VST_PRODUCTION"
private const val PROD_BASE = "https://compras.vstsolution.com"

data class ProdRankingItem(
    val descProduto: String,
    val totalGrupo: Double
)

data class ProdRankingDash(
    val descDashboard: String,
    val registros: List<ProdRankingItem>
)

data class ProdResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

class ProductionService(private val userToken: String) {

    private fun parseSaldo(value: Any?): Double = when (value) {
        null -> 0.0
        is Double -> value
        is Int -> value.toDouble()
        is Long -> value.toDouble()
        is String -> value.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    private fun get(url: String): String? {
        return try {
            Log.i(PROD_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(PROD_TAG, "➡️  GET $url")
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
                Log.i(PROD_TAG, "✅ $status (${duration}ms) | ${body.length} chars")
                Log.d(PROD_TAG, "📦 Body: ${body.take(500)}")
                body
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
                Log.e(PROD_TAG, "❌ HTTP $status (${duration}ms) | $err")
                null
            }
        } catch (e: Exception) {
            Log.e(PROD_TAG, "💥 Exceção: ${e.message}", e)
            null
        }
    }

    suspend fun fetchRankingEstoque(
        idEmpresa: String,
        idFilial: String
    ): ProdResult<List<ProdRankingDash>> = withContext(Dispatchers.IO) {
        val url = "$PROD_BASE/rankingestoque/$idEmpresa/idfilial/$idFilial"
        Log.i(PROD_TAG, "🎯 fetchRankingEstoque | empresa=$idEmpresa filial=$idFilial")
        val body = get(url) ?: return@withContext ProdResult(false, error = "Erro de rede")
        return@withContext try {
            val arr = JSONArray(body)
            val lista = mutableListOf<ProdRankingDash>()
            for (i in 0 until arr.length()) {
                val dash = arr.getJSONObject(i)
                val desc = dash.optString("DESCDASHBOARD", "Ranking $i")
                val registros = dash.optJSONArray("Registros") ?: continue
                val items = mutableListOf<ProdRankingItem>()
                for (j in 0 until registros.length()) {
                    val r = registros.getJSONObject(j)
                    val nome = r.optString("DESCPRODUTO", "")
                        .ifEmpty { r.optString("GRUPOPRODUTO", "Sem Nome") }
                    val valor = parseSaldo(r.opt("TOTALGRUPO"))
                    items.add(ProdRankingItem(descProduto = nome, totalGrupo = valor))
                }
                if (items.isNotEmpty()) lista.add(ProdRankingDash(desc, items))
            }
            Log.i(PROD_TAG, "✅ Rankings estoque: ${lista.size} dashboards")
            ProdResult(true, lista)
        } catch (e: Exception) {
            Log.e(PROD_TAG, "💥 Parse: ${e.message}", e)
            ProdResult(false, error = e.message)
        }
    }
}

