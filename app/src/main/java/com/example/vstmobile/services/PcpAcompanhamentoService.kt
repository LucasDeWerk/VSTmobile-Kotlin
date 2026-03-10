package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

private const val PCP_TAG         = "VST_PCP"
private val PCP_BASE_API    = ApiConfig.BASE_API
private val PCP_BASE_REPORT = ApiConfig.BASE_REPORT

// ── Dados estáticos ────────────────────────────────────────────────────────────
val PCP_MODELOS = listOf(
    "0" to "Padrão (Total em Kg)",
    "1" to "Gráfico"
)

// ── Data classes ────────────────────────────────────────────────────────────────
data class PcpResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

data class PCPLinha(
    val idLinhaProducao: Int,
    val descLinhaProducao: String,
    val idMedida: Int = 0,
    val capacidadeProducaoDia: Double = 0.0
)

data class PCPAcompanhamentoItem(
    val idSaida: String,
    val idOp: String,
    val idLinhaProducaoPosse: Int,
    val descLinhaProducao: String,
    val idProdutoComposto: Int,
    val descProduto: String,
    val abreviatura: String,
    val pesoProdutoCad: Double,
    val dtEncerramento: String,
    val qtdProduzir: Double,
    val qtdAutorizada: Double,
    val qtdProduzida: Double,
    val qtdTotalLp: Double
)

class PcpAcompanhamentoService(private val userToken: String) {

    // ── GET texto (JSON) ──────────────────────────────────────────────────────
    private fun get(url: String): String? = try {
        Log.i(PCP_TAG, "➡️  GET $url")
        val start = System.currentTimeMillis()
        val conn  = URL(url).openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $userToken")
        conn.connectTimeout = 30_000
        conn.readTimeout    = 30_000
        val status = conn.responseCode
        val dur    = System.currentTimeMillis() - start
        if (status == 200) {
            val body = conn.inputStream.bufferedReader().readText()
            Log.i(PCP_TAG, "✅ $status (${dur}ms) | ${body.length} chars")
            body
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(PCP_TAG, "❌ HTTP $status (${dur}ms) | $err")
            null
        }
    } catch (e: Exception) {
        Log.e(PCP_TAG, "💥 get: ${e.message}", e)
        null
    }

    // ── GET bytes (PDF) ──────────────────────────────────────────────────────
    private fun getBytes(url: String): ByteArray? = try {
        Log.i(PCP_TAG, "➡️  GET (bytes) $url")
        val start = System.currentTimeMillis()
        val conn  = URL(url).openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $userToken")
        conn.connectTimeout = 30_000
        conn.readTimeout    = 30_000
        val status = conn.responseCode
        val dur    = System.currentTimeMillis() - start
        if (status == 200) {
            val bytes = conn.inputStream.readBytes()
            Log.i(PCP_TAG, "✅ $status (${dur}ms) | ${bytes.size} bytes")
            bytes
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(PCP_TAG, "❌ HTTP $status (${dur}ms) | $err")
            null
        }
    } catch (e: Exception) {
        Log.e(PCP_TAG, "💥 getBytes: ${e.message}", e)
        null
    }

    // ── Buscar linhas de produção ─────────────────────────────────────────────
    // GET /pcplinhaproducao/:idempresa/idfilial/:idfilial
    suspend fun fetchLinhas(
        idEmpresa: Int,
        idFilial: Int
    ): PcpResult<List<PCPLinha>> = withContext(Dispatchers.IO) {
        val url = "$PCP_BASE_API/pcplinhaproducao/$idEmpresa/idfilial/$idFilial"
        Log.i(PCP_TAG, "🎯 fetchLinhas URL: $url")
        val body = get(url) ?: return@withContext PcpResult(false, error = "Erro de rede ao buscar linhas")
        return@withContext try {
            val root = JSONObject(body)
            val arr  = root.optJSONArray("data") ?: JSONArray()
            val lista = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PCPLinha(
                    idLinhaProducao       = obj.optInt("ID_LINHA_PRODUCAO", 0),
                    descLinhaProducao     = obj.optString("DESC_LINHA_PRODUCAO", ""),
                    idMedida              = obj.optInt("ID_MEDIDA", 0),
                    capacidadeProducaoDia = obj.optDouble("CAPACIDADE_PRODUCAO_DIA", 0.0)
                )
            }
            Log.i(PCP_TAG, "✅ Linhas: ${lista.size}")
            PcpResult(true, lista)
        } catch (e: Exception) {
            Log.e(PCP_TAG, "💥 Parse linhas: ${e.message}", e)
            PcpResult(false, error = e.message)
        }
    }

    // ── Buscar dados de acompanhamento ────────────────────────────────────────
    // GET /pcpacompanhamento/:idempresa/idfilial/:idfilial/dtini/:dtini/dtfim/:dtfim?produto=&idlinha=&pedido=
    suspend fun fetchData(
        idEmpresa: Int,
        idFilial: Int,
        dtIni: String,      // yyyy-MM-dd
        dtFim: String,      // yyyy-MM-dd
        produto: String = "",
        idLinha: Int = 0,
        pedido: String = ""
    ): PcpResult<List<PCPAcompanhamentoItem>> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("$PCP_BASE_API/pcpacompanhamento/$idEmpresa/idfilial/$idFilial/dtini/$dtIni/dtfim/$dtFim")
        val params = mutableListOf<String>()
        if (produto.isNotBlank()) params.add("produto=${URLEncoder.encode(produto.trim(), "UTF-8")}")
        if (idLinha > 0)          params.add("idlinha=$idLinha")
        if (pedido.isNotBlank())  params.add("pedido=${URLEncoder.encode(pedido.trim(), "UTF-8")}")
        if (params.isNotEmpty()) sb.append("?${params.joinToString("&")}")
        val url = sb.toString()
        Log.i(PCP_TAG, "🎯 fetchData Acompanhamento URL: $url")
        val body = get(url) ?: return@withContext PcpResult(false, error = "Erro de rede ao buscar dados")
        return@withContext try {
            val root = JSONObject(body)
            val arr  = root.optJSONArray("data") ?: JSONArray()
            val lista = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PCPAcompanhamentoItem(
                    idSaida              = obj.optString("ID_SAIDA", ""),
                    idOp                 = obj.optString("ID_OP", ""),
                    idLinhaProducaoPosse = obj.optInt("ID_LINHA_PRODUCAO_POSSE", 0),
                    descLinhaProducao    = obj.optString("DESC_LINHA_PRODUCAO", ""),
                    idProdutoComposto    = obj.optInt("ID_PRODUTOCOMPOSTO", 0),
                    descProduto          = obj.optString("DESCPRODUTO", ""),
                    abreviatura          = obj.optString("ABREVIATURA", ""),
                    pesoProdutoCad       = obj.optDouble("PESO_PRODUTO_CAD", 0.0),
                    dtEncerramento       = obj.optString("DTENCERRAMENTO", ""),
                    qtdProduzir          = obj.optDouble("QTD_PRODUZIR", 0.0),
                    qtdAutorizada        = obj.optDouble("QTD_AUTORIZADA", 0.0),
                    qtdProduzida         = obj.optDouble("QTD_PRODUZIDA", 0.0),
                    qtdTotalLp           = obj.optDouble("QTD_TOTAL_LP", 0.0)
                )
            }
            Log.i(PCP_TAG, "✅ Dados Acompanhamento: ${lista.size} itens")
            PcpResult(true, lista)
        } catch (e: Exception) {
            Log.e(PCP_TAG, "💥 Parse dados acompanhamento: ${e.message}", e)
            PcpResult(false, error = e.message)
        }
    }

    // ── Gerar PDF (legado – via report server) ────────────────────────────────
    suspend fun fetchRelatorioPdf(
        idFilial: Int,
        idEmpresa: Int,
        tipoProduto: String,
        idProduto: String,
        tipoLinha: String,
        idLinha: String,
        tipoPedido: String,
        idPedido: String,
        modelo: String,
        dataInicio: String,
        dataFim: String
    ): PcpResult<ByteArray> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("$PCP_BASE_REPORT/pcpacompanhamentoproducao")
        sb.append("/$idEmpresa/$idFilial")
        sb.append("?tpProduto=$tipoProduto")
        sb.append("&idProduto=${idProduto.ifBlank { "0" }}")
        sb.append("&tpLinha=$tipoLinha")
        sb.append("&idLinha=${idLinha.ifBlank { "0" }}")
        sb.append("&tpPedido=$tipoPedido")
        sb.append("&idPedido=${idPedido.ifBlank { "0" }}")
        sb.append("&modelo=$modelo")
        sb.append("&dataInicio=${dataInicio.replace("/", "%2F")}")
        sb.append("&dataFim=${dataFim.replace("/", "%2F")}")
        val url = sb.toString()
        Log.i(PCP_TAG, "🎯 fetchRelatorioPdf AcompanhamentoProd URL: $url")
        val bytes = getBytes(url)
            ?: return@withContext PcpResult(false, error = "Erro de rede ao baixar PDF")
        if (bytes.isEmpty())
            return@withContext PcpResult(false, error = "PDF vazio retornado pela API")
        Log.i(PCP_TAG, "✅ PDF PCP Acompanhamento: ${bytes.size} bytes")
        PcpResult(true, bytes)
    }
}
