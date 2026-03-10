package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val TPC_TAG = "VST_TPAGAR_CONF"
private val TPC_BASE_API = ApiConfig.BASE_API
private val TPC_BASE_REPORT = ApiConfig.BASE_REPORT

data class FornecedorTPC(
    val idClifor: Int,
    val nomeRazao: String,
    val apelidoFantasia: String,
    val cpfCnpj: String,
    val telefone: String
)

data class FornecedorPageResult(
    val fornecedores: List<FornecedorTPC>,
    val totalRecords: Int,
    val totalPages: Int
)

data class TpcResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

class TitulosPagarConferenciaService(private val userToken: String) {

    private fun get(url: String): String? {
        return try {
            Log.i(TPC_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TPC_TAG, "➡️  GET $url")
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
                Log.i(TPC_TAG, "✅ $status (${duration}ms) | ${body.length} chars")
                Log.d(TPC_TAG, "📦 Body: ${body.take(500)}")
                body
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
                Log.e(TPC_TAG, "❌ HTTP $status (${duration}ms) | $err")
                null
            }
        } catch (e: Exception) {
            Log.e(TPC_TAG, "💥 Exceção: ${e.message}", e)
            null
        }
    }

    private fun getBytes(url: String): ByteArray? {
        return try {
            Log.i(TPC_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TPC_TAG, "➡️  GET (bytes) $url")
            val start = System.currentTimeMillis()
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $userToken")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            val status = conn.responseCode
            val duration = System.currentTimeMillis() - start
            if (status == 200) {
                val bytes = conn.inputStream.readBytes()
                Log.i(TPC_TAG, "✅ $status (${duration}ms) | ${bytes.size} bytes")
                bytes
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
                Log.e(TPC_TAG, "❌ HTTP $status (${duration}ms) | $err")
                null
            }
        } catch (e: Exception) {
            Log.e(TPC_TAG, "💥 Exceção bytes: ${e.message}", e)
            null
        }
    }

    // ── Buscar fornecedores paginados ─────────────────────────────────────────
    suspend fun fetchFornecedores(
        idEmpresa: String,
        termo: String,
        page: Int = 0,
        limit: Int = 10
    ): TpcResult<FornecedorPageResult> = withContext(Dispatchers.IO) {
        val offset = page * limit
        val encoded = java.net.URLEncoder.encode(termo.trim(), "UTF-8")
        val url = "$TPC_BASE_API/cprfornecedores/$idEmpresa?razaofor=$encoded&limit=$limit&offset=$offset"
        Log.i(TPC_TAG, "🎯 fetchFornecedores | empresa=$idEmpresa termo='$termo' page=$page offset=$offset")
        val body = get(url) ?: return@withContext TpcResult(false, error = "Erro de rede")
        return@withContext try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val total = root.optInt("records", 0)
            val totalPgs = if (limit > 0) (total + limit - 1) / limit else 1
            val lista = (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                FornecedorTPC(
                    idClifor        = obj.optInt("ID_CLIFOR", 0),
                    nomeRazao       = obj.optString("NOME_RAZAO", ""),
                    apelidoFantasia = obj.optString("APELIDO_FANTASIA", ""),
                    cpfCnpj         = obj.optString("CPF_CNPJ", ""),
                    telefone        = obj.optString("FONERESIDENCIAL", "")
                )
            }
            Log.i(TPC_TAG, "✅ Fornecedores: ${lista.size} / total=$total páginas=$totalPgs")
            TpcResult(true, FornecedorPageResult(lista, total, totalPgs))
        } catch (e: Exception) {
            Log.e(TPC_TAG, "💥 Parse fornecedores: ${e.message}", e)
            TpcResult(false, error = e.message)
        }
    }

    // ── Gerar PDF Títulos a Pagar Conferência ─────────────────────────────────
    suspend fun fetchRelatorioPdf(
        idEmpresa: String,
        idFilial: String,
        dtIni: String,
        dtFim: String,
        impComp: Boolean = false,
        impAdt: Boolean = false,
        impCheque: Boolean = false,
        fornecedorId: Int = 0,
        periodo: String = "DTVENCIMENTO"  // DTVENCIMENTO | DTCADASTRO | DTEMISSAO
    ): TpcResult<ByteArray> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("$TPC_BASE_REPORT/pagarconferencia/$idEmpresa/idfilial/$idFilial/dtini/$dtIni/dtfim/$dtFim")
        sb.append("?impcomp=${if (impComp) "S" else "N"}")
        sb.append("&impadt=${if (impAdt) "S" else "N"}")
        sb.append("&impcheque=${if (impCheque) "S" else "N"}")
        if (fornecedorId > 0) sb.append("&fornecedor=$fornecedorId")
        sb.append("&periodo=$periodo")

        val url = sb.toString()
        Log.i(TPC_TAG, "🎯 fetchRelatorioPdf")
        Log.i(TPC_TAG, "URL: $url")
        Log.i(TPC_TAG, "Params: empresa=$idEmpresa filial=$idFilial dtIni=$dtIni dtFim=$dtFim impComp=$impComp impAdt=$impAdt impCheque=$impCheque fornecedor=$fornecedorId periodo=$periodo")

        val bytes = getBytes(url) ?: return@withContext TpcResult(false, error = "Erro de rede ao baixar PDF")
        if (bytes.isEmpty()) return@withContext TpcResult(false, error = "PDF vazio retornado pela API")

        val header = String(bytes.take(4).toByteArray())
        if (header != "%PDF") {
            Log.w(TPC_TAG, "⚠️ Header inesperado: '$header' (continuando mesmo assim)")
        }
        Log.i(TPC_TAG, "✅ PDF: ${bytes.size} bytes")
        TpcResult(true, bytes)
    }
}

