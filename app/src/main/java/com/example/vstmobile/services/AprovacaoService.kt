package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val APO_TAG = "VST_APROVACAO"
private const val APO_BASE = "https://compras.vstsolution.com"

data class PedidoOrcamento(
    val idSaida: Int,
    val idEmpresa: Int,
    val idFilial: Int,
    val numero: String,           // "VEN-123" ou "ORC-123"
    val vendedor: String,
    val cliente: String,
    val valor: Double,
    val valorSubtotal: Double,
    val custoAquisicao: Double,
    val percDesconto: Double,
    val calcPercDesc: Double,
    val vlrParticipaDesc: Double,
    val vlrNaoParticipaDesc: Double,
    val formaDesconto: String,
    val planoPagamento: String,
    val percEntrada: Double,
    val data: String,
    val status: String,           // "Pendente", "Aprovada", "Rejeitada", "Indefinido"
    val resultadoAprovacao: String, // "1"=Pendente "2"=Aprovada "3"=Rejeitada
    val filial: String,
    val tipo: String,
    val observacao: String
)

data class ApoResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

class AprovacaoService(private val userToken: String) {

    private fun get(url: String): String? {
        return try {
            Log.i(APO_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(APO_TAG, "➡️  GET $url")
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
                Log.i(APO_TAG, "✅ $status (${duration}ms) | ${body.length} chars")
                Log.d(APO_TAG, "📦 Body: ${body.take(500)}")
                body
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
                Log.e(APO_TAG, "❌ HTTP $status (${duration}ms) | $err")
                null
            }
        } catch (e: Exception) {
            Log.e(APO_TAG, "💥 Exceção GET: ${e.message}", e)
            null
        }
    }

    private fun put(url: String): String? {
        return try {
            Log.i(APO_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(APO_TAG, "➡️  PUT $url")
            val start = System.currentTimeMillis()
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $userToken")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val status = conn.responseCode
            val duration = System.currentTimeMillis() - start
            if (status in 200..299) {
                val body = runCatching { conn.inputStream.bufferedReader().readText() }.getOrElse { "" }
                Log.i(APO_TAG, "✅ $status (${duration}ms) | body: ${body.take(200)}")
                body
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
                Log.e(APO_TAG, "❌ HTTP $status (${duration}ms) | $err")
                null
            }
        } catch (e: Exception) {
            Log.e(APO_TAG, "💥 Exceção PUT: ${e.message}", e)
            null
        }
    }

    private fun parseSaldo(v: Any?): Double = when (v) {
        null -> 0.0
        is Double -> v
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is String -> v.replace(",", ".").toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    private fun getStatusFromCode(code: String): String = when (code) {
        "1" -> "Pendente"
        "2" -> "Aprovada"
        "3" -> "Rejeitada"
        else -> "Indefinido"
    }

    // ── Buscar lista de pedidos / orçamentos ──────────────────────────────────
    suspend fun fetchPedidos(
        idEmpresa: Int,
        idFilial: Int,
        dtIni: String,
        dtFim: String
    ): ApoResult<List<PedidoOrcamento>> = withContext(Dispatchers.IO) {
        val url = "$APO_BASE/listaaprovpedorcto/$idEmpresa/idfilial/$idFilial/dtini/$dtIni/dtfim/$dtFim"
        Log.i(APO_TAG, "🎯 fetchPedidos | empresa=$idEmpresa filial=$idFilial dtIni=$dtIni dtFim=$dtFim")
        val body = get(url) ?: return@withContext ApoResult(false, error = "Erro de rede")
        return@withContext try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val lista = (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                val tipo = obj.optString("TIPO", "")
                val idSaida = obj.optInt("ID_SAIDA", 0)
                val resultado = obj.optString("RESULTADOAPROVACAO", "0")
                PedidoOrcamento(
                    idSaida          = idSaida,
                    idEmpresa        = obj.optInt("ID_EMPRESA", idEmpresa),
                    idFilial         = obj.optInt("ID_FILIAL", idFilial),
                    numero           = "${if (tipo == "V") "VEN" else "ORC"}-$idSaida",
                    vendedor         = obj.optString("CALCVENDEDOR", "N/A"),
                    cliente          = obj.optString("APELIDO_FANTASIA", "").ifEmpty { obj.optString("NOME_RAZAO", "N/A") },
                    valor            = parseSaldo(obj.opt("VLR_VENDA")),
                    valorSubtotal    = parseSaldo(obj.opt("VLR_SUBTOTAL")),
                    custoAquisicao   = parseSaldo(obj.opt("CUSTO_AQUISICAO")),
                    percDesconto     = parseSaldo(obj.opt("PERC_DESCONTO")),
                    calcPercDesc     = parseSaldo(obj.opt("CALCPERC_DESC")),
                    vlrParticipaDesc = parseSaldo(obj.opt("VLR_PARTICIPA_DESC")),
                    vlrNaoParticipaDesc = parseSaldo(obj.opt("VLR_NAOPARTICIPA_DESC")),
                    formaDesconto    = obj.optString("FORMA_DESCONTO", "N/A"),
                    planoPagamento   = obj.optString("DESCPLANOPAGTO", "N/A"),
                    percEntrada      = parseSaldo(obj.opt("PERC_ENTRADA")),
                    data             = obj.optString("DTEMISSAO", "").take(10),
                    status           = getStatusFromCode(resultado),
                    resultadoAprovacao = resultado,
                    filial           = obj.optString("NOME_FILIAL", ""),
                    tipo             = obj.optString("TIPODESCRICAO", tipo),
                    observacao       = obj.optString("OBSERVACAO", "")
                )
            }
            Log.i(APO_TAG, "✅ Pedidos: ${lista.size}")
            ApoResult(true, lista)
        } catch (e: Exception) {
            Log.e(APO_TAG, "💥 Parse pedidos: ${e.message}", e)
            ApoResult(false, error = e.message)
        }
    }

    // ── Aprovar pedido ────────────────────────────────────────────────────────
    suspend fun aprovarPedido(idEmpresa: Int, idFilial: Int, idSaida: Int): ApoResult<Boolean> =
        withContext(Dispatchers.IO) {
            val url = "$APO_BASE/aprovarpedido/$idEmpresa/idfilial/$idFilial/idsaida/$idSaida"
            Log.i(APO_TAG, "🎯 aprovarPedido | empresa=$idEmpresa filial=$idFilial saida=$idSaida")
            val resp = put(url)
            return@withContext if (resp != null) {
                Log.i(APO_TAG, "✅ Aprovado com sucesso")
                ApoResult(true, true)
            } else {
                ApoResult(false, error = "Erro ao aprovar pedido")
            }
        }

    // ── Rejeitar pedido ───────────────────────────────────────────────────────
    suspend fun rejeitarPedido(idEmpresa: Int, idFilial: Int, idSaida: Int): ApoResult<Boolean> =
        withContext(Dispatchers.IO) {
            val url = "$APO_BASE/rejeitarpedido/$idEmpresa/idfilial/$idFilial/idsaida/$idSaida"
            Log.i(APO_TAG, "🎯 rejeitarPedido | empresa=$idEmpresa filial=$idFilial saida=$idSaida")
            val resp = put(url)
            return@withContext if (resp != null) {
                Log.i(APO_TAG, "✅ Rejeitado com sucesso")
                ApoResult(true, true)
            } else {
                ApoResult(false, error = "Erro ao rejeitar pedido")
            }
        }
}

