package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val TPLC_TAG         = "VST_TPAGAR_LC"
private const val TPLC_BASE_API    = "https://compras.vstsolution.com"
private const val TPLC_BASE_REPORT = "https://report.vstsolution.com"

data class LocalCobranca(
    val idLocalCobranca: Int,
    val descLocalCobranca: String
)

data class TplcResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

class TitulosPagarLocalCobrancaService(private val userToken: String) {

    private fun get(url: String): String? = try {
        Log.i(TPLC_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TPLC_TAG, "➡️  GET $url")
        val start = System.currentTimeMillis()
        val conn = URL(url).openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $userToken")
        conn.connectTimeout = 15000; conn.readTimeout = 15000
        val status = conn.responseCode
        val dur = System.currentTimeMillis() - start
        if (status == 200) {
            val body = conn.inputStream.bufferedReader().readText()
            Log.i(TPLC_TAG, "✅ $status (${dur}ms) | ${body.length} chars")
            Log.d(TPLC_TAG, "📦 Body: ${body.take(400)}")
            body
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(TPLC_TAG, "❌ HTTP $status (${dur}ms) | $err"); null
        }
    } catch (e: Exception) { Log.e(TPLC_TAG, "💥 GET: ${e.message}", e); null }

    private fun getBytes(url: String): ByteArray? = try {
        Log.i(TPLC_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TPLC_TAG, "➡️  GET (bytes) $url")
        val start = System.currentTimeMillis()
        val conn = URL(url).openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $userToken")
        conn.connectTimeout = 30000; conn.readTimeout = 30000
        val status = conn.responseCode
        val dur = System.currentTimeMillis() - start
        if (status == 200) {
            val bytes = conn.inputStream.readBytes()
            Log.i(TPLC_TAG, "✅ $status (${dur}ms) | ${bytes.size} bytes"); bytes
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(TPLC_TAG, "❌ HTTP $status (${dur}ms) | $err"); null
        }
    } catch (e: Exception) { Log.e(TPLC_TAG, "💥 getBytes: ${e.message}", e); null }

    // ── Buscar locais de cobrança ─────────────────────────────────────────────
    suspend fun fetchLocaisCobranca(idEmpresa: String): TplcResult<List<LocalCobranca>> =
        withContext(Dispatchers.IO) {
            val url = "$TPLC_BASE_API/localcobranca/$idEmpresa"
            Log.i(TPLC_TAG, "🎯 fetchLocaisCobranca | empresa=$idEmpresa")
            val body = get(url) ?: return@withContext TplcResult(false, error = "Erro de rede")
            return@withContext try {
                val root = JSONObject(body)
                val arr = root.optJSONArray("data") ?: JSONArray()
                val lista = (0 until arr.length()).map {
                    val obj = arr.getJSONObject(it)
                    LocalCobranca(
                        idLocalCobranca   = obj.optInt("ID_LOCALCOBRANCA", 0),
                        descLocalCobranca = obj.optString("DESCLOCALCOBRANCA", "")
                    )
                }
                Log.i(TPLC_TAG, "✅ ${lista.size} locais carregados")
                TplcResult(true, lista)
            } catch (e: Exception) {
                Log.e(TPLC_TAG, "💥 Parse locais: ${e.message}", e)
                TplcResult(false, error = e.message)
            }
        }

    // ── Buscar fornecedores (busca por texto, paginado) ───────────────────────
    suspend fun fetchFornecedores(
        idEmpresa: String,
        search: String = "",
        page: Int = 0,
        limit: Int = 10
    ): TplcResult<FornecedorPageResult> = withContext(Dispatchers.IO) {
        val offset = page * limit
        val sb = StringBuilder("$TPLC_BASE_API/cprfornecedores/$idEmpresa?limit=$limit&offset=$offset")
        if (search.trim().isNotEmpty()) {
            val enc = java.net.URLEncoder.encode(search.trim(), "UTF-8")
            sb.append("&razaofor=$enc")
        }
        val url = sb.toString()
        Log.i(TPLC_TAG, "🎯 fetchFornecedores | empresa=$idEmpresa search='$search' page=$page")
        val body = get(url) ?: return@withContext TplcResult(false, error = "Erro de rede")
        return@withContext try {
            val root = JSONObject(body)
            val arr  = root.optJSONArray("data") ?: JSONArray()
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
            Log.i(TPLC_TAG, "✅ Fornecedores: ${lista.size} / total=$total")
            TplcResult(true, FornecedorPageResult(lista, total, totalPgs))
        } catch (e: Exception) {
            Log.e(TPLC_TAG, "💥 Parse fornecedores: ${e.message}", e)
            TplcResult(false, error = e.message)
        }
    }

    // ── Gerar PDF ─────────────────────────────────────────────────────────────
    suspend fun fetchRelatorioPdf(
        idEmpresa: String,
        idFilial: String,
        dtIni: String,
        dtFim: String,
        periodo: String = "DTVENCIMENTO",
        idsLocais: List<Int>,
        impComp: Boolean   = false,
        impAdt: Boolean    = false,
        impCheque: Boolean = false,
        fornecedorId: Int  = 0,
        valorDe: String    = "",
        valorAte: String   = ""
    ): TplcResult<ByteArray> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("$TPLC_BASE_REPORT/pagarlocalcobranca/$idEmpresa/idfilial/$idFilial/dtini/$dtIni/dtfim/$dtFim")
        sb.append("?periodo=$periodo")
        sb.append("&idlocalcob=${idsLocais.joinToString(",")}")
        sb.append("&impcomp=${if (impComp) "S" else "N"}")
        sb.append("&impadt=${if (impAdt) "S" else "N"}")
        sb.append("&impcheque=${if (impCheque) "S" else "N"}")
        if (fornecedorId > 0) sb.append("&fornecedor=$fornecedorId")
        if (valorDe.isNotEmpty()) sb.append("&valor_de=$valorDe")
        if (valorAte.isNotEmpty()) sb.append("&valor_ate=$valorAte")
        val url = sb.toString()
        Log.i(TPLC_TAG, "🎯 fetchRelatorioPdf")
        Log.i(TPLC_TAG, "URL: $url")
        val bytes = getBytes(url) ?: return@withContext TplcResult(false, error = "Erro de rede ao baixar PDF")
        if (bytes.isEmpty()) return@withContext TplcResult(false, error = "PDF vazio")
        Log.i(TPLC_TAG, "✅ PDF: ${bytes.size} bytes")
        TplcResult(true, bytes)
    }
}

