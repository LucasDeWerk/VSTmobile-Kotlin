package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val TRC_TAG         = "VST_TRECEBER_CONF"
private const val TRC_BASE_API    = "https://compras.vstsolution.com"
private const val TRC_BASE_REPORT = "https://report.vstsolution.com"

data class ClienteTRC(
    val idClifor: Int,
    val nomeRazao: String,
    val apelidoFantasia: String,
    val cpfCnpj: String,
    val telefone: String
)

data class TrcResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

class TitulosReceberConferenciaService(private val userToken: String) {

    private fun get(url: String): String? = try {
        Log.i(TRC_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TRC_TAG, "➡️  GET $url")
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
            Log.i(TRC_TAG, "✅ $status (${dur}ms) | ${body.length} chars")
            Log.d(TRC_TAG, "📦 Body: ${body.take(500)}")
            body
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(TRC_TAG, "❌ HTTP $status (${dur}ms) | $err"); null
        }
    } catch (e: Exception) { Log.e(TRC_TAG, "💥 GET: ${e.message}", e); null }

    private fun getBytes(url: String): ByteArray? = try {
        Log.i(TRC_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TRC_TAG, "➡️  GET (bytes) $url")
        val start = System.currentTimeMillis()
        val conn = URL(url).openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $userToken")
        conn.connectTimeout = 30000; conn.readTimeout = 30000
        val status = conn.responseCode
        val dur = System.currentTimeMillis() - start
        if (status == 200) {
            val bytes = conn.inputStream.readBytes()
            Log.i(TRC_TAG, "✅ $status (${dur}ms) | ${bytes.size} bytes"); bytes
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(TRC_TAG, "❌ HTTP $status (${dur}ms) | $err"); null
        }
    } catch (e: Exception) { Log.e(TRC_TAG, "💥 getBytes: ${e.message}", e); null }

    // ── Buscar clientes por nome/CPF/CNPJ ────────────────────────────────────
    suspend fun fetchClientes(
        idEmpresa: String,
        search: String
    ): TrcResult<List<ClienteTRC>> = withContext(Dispatchers.IO) {
        val enc = java.net.URLEncoder.encode(search.trim(), "UTF-8")
        val url = "$TRC_BASE_API/clientes/$idEmpresa?cliente=$enc"
        Log.i(TRC_TAG, "🎯 fetchClientes | empresa=$idEmpresa search='$search'")
        val body = get(url) ?: return@withContext TrcResult(false, error = "Erro de rede")
        return@withContext try {
            val root = JSONObject(body)
            val arr  = root.optJSONArray("data") ?: JSONArray()
            val lista = (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                ClienteTRC(
                    idClifor        = obj.optInt("ID_CLIFOR", 0),
                    nomeRazao       = obj.optString("NOME_RAZAO", ""),
                    apelidoFantasia = obj.optString("APELIDO_FANTASIA", ""),
                    cpfCnpj         = obj.optString("CPF_CNPJ", ""),
                    telefone        = obj.optString("FONERESIDENCIAL", "")
                )
            }
            Log.i(TRC_TAG, "✅ Clientes: ${lista.size}")
            TrcResult(true, lista)
        } catch (e: Exception) {
            Log.e(TRC_TAG, "💥 Parse clientes: ${e.message}", e)
            TrcResult(false, error = e.message)
        }
    }

    // ── Gerar PDF Títulos a Receber Conferência ───────────────────────────────
    suspend fun fetchRelatorioPdf(
        idEmpresa: String,
        idFilial: String,
        dtIni: String,
        dtFim: String,
        periodo: String    = "DTVENCIMENTO",
        impComp: Boolean   = false,
        impAdt: Boolean    = false,
        impCheque: Boolean = false,
        clienteId: Int     = 0,
        valorDe: String    = "",
        valorAte: String   = ""
    ): TrcResult<ByteArray> = withContext(Dispatchers.IO) {
        val sb = StringBuilder(
            "$TRC_BASE_REPORT/receberconferencia/$idEmpresa/idfilial/$idFilial/dtini/$dtIni/dtfim/$dtFim"
        )
        sb.append("?periodo=$periodo")
        sb.append("&impcomp=${if (impComp) "S" else "N"}")
        sb.append("&impadt=${if (impAdt) "S" else "N"}")
        sb.append("&impcheque=${if (impCheque) "S" else "N"}")
        if (clienteId > 0) sb.append("&cliente=$clienteId")
        if (valorDe.isNotEmpty()) sb.append("&valor_de=$valorDe")
        if (valorAte.isNotEmpty()) sb.append("&valor_ate=$valorAte")
        val url = sb.toString()
        Log.i(TRC_TAG, "🎯 fetchRelatorioPdf")
        Log.i(TRC_TAG, "URL: $url")
        Log.i(TRC_TAG, "Params: empresa=$idEmpresa filial=$idFilial dtIni=$dtIni dtFim=$dtFim periodo=$periodo impComp=$impComp impAdt=$impAdt impCheque=$impCheque cliente=$clienteId valorDe=$valorDe valorAte=$valorAte")
        val bytes = getBytes(url) ?: return@withContext TrcResult(false, error = "Erro de rede ao baixar PDF")
        if (bytes.isEmpty()) return@withContext TrcResult(false, error = "PDF vazio")
        Log.i(TRC_TAG, "✅ PDF: ${bytes.size} bytes")
        TrcResult(true, bytes)
    }
}

