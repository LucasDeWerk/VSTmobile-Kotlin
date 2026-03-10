package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val PCPPROG_TAG         = "VST_PCP_PROG"
private val PCPPROG_BASE_API    = ApiConfig.BASE_API
private val PCPPROG_BASE_REPORT = ApiConfig.BASE_REPORT

// ── Dados estáticos ────────────────────────────────────────────────────────────
val PCPPROG_SITUACOES = listOf(
    "0" to "Todos",
    "1" to "Não produzidos",
    "2" to "Somente produzidos"
)

val PCPPROG_ORDENS = listOf(
    "0" to "por Cliente",
    "1" to "por Produto"
)

// ── Data classes ────────────────────────────────────────────────────────────────
data class PcpProgResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

data class PCPProgramacaoItem(
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
    val qtdTotalLp: Double,
    val cliente: String,
    val vendedor: String,
    val nomeVendedor: String
)

class PcpProgramacaoService(private val userToken: String) {

    // ── GET texto (JSON) ──────────────────────────────────────────────────────
    private fun get(url: String): String? = try {
        Log.i(PCPPROG_TAG, "➡️  GET $url")
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
            Log.i(PCPPROG_TAG, "✅ $status (${dur}ms) | ${body.length} chars")
            body
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(PCPPROG_TAG, "❌ HTTP $status (${dur}ms) | $err")
            null
        }
    } catch (e: Exception) {
        Log.e(PCPPROG_TAG, "💥 get: ${e.message}", e)
        null
    }

    // ── GET bytes (PDF) ──────────────────────────────────────────────────────
    private fun getBytes(url: String): ByteArray? = try {
        Log.i(PCPPROG_TAG, "➡️  GET (bytes) $url")
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
            Log.i(PCPPROG_TAG, "✅ $status (${dur}ms) | ${bytes.size} bytes")
            bytes
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(PCPPROG_TAG, "❌ HTTP $status (${dur}ms) | $err")
            null
        }
    } catch (e: Exception) {
        Log.e(PCPPROG_TAG, "💥 getBytes: ${e.message}", e)
        null
    }

    // ── Buscar linhas de produção ─────────────────────────────────────────────
    // GET /pcplinhaproducao/:idempresa/idfilial/:idfilial
    suspend fun fetchLinhas(
        idEmpresa: Int,
        idFilial: Int
    ): PcpProgResult<List<PCPLinha>> = withContext(Dispatchers.IO) {
        val url = "$PCPPROG_BASE_API/pcplinhaproducao/$idEmpresa/idfilial/$idFilial"
        Log.i(PCPPROG_TAG, "🎯 fetchLinhas URL: $url")
        val body = get(url) ?: return@withContext PcpProgResult(false, error = "Erro de rede ao buscar linhas")
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
            Log.i(PCPPROG_TAG, "✅ Linhas: ${lista.size}")
            PcpProgResult(true, lista)
        } catch (e: Exception) {
            Log.e(PCPPROG_TAG, "💥 Parse linhas: ${e.message}", e)
            PcpProgResult(false, error = e.message)
        }
    }

    // ── Buscar dados de programação ───────────────────────────────────────────
    // GET /pcpprogramacao/:idempresa/idfilial/:idfilial/dtini/:dtini/dtfim/:dtfim?idlinha=
    suspend fun fetchData(
        idEmpresa: Int,
        idFilial: Int,
        dtIni: String,      // yyyy-MM-dd
        dtFim: String,      // yyyy-MM-dd
        idLinha: Int = 0
    ): PcpProgResult<List<PCPProgramacaoItem>> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("$PCPPROG_BASE_API/pcpprogramacao/$idEmpresa/idfilial/$idFilial/dtini/$dtIni/dtfim/$dtFim")
        if (idLinha > 0) sb.append("?idlinha=$idLinha")
        val url = sb.toString()
        Log.i(PCPPROG_TAG, "🎯 fetchData Programação URL: $url")
        val body = get(url) ?: return@withContext PcpProgResult(false, error = "Erro de rede ao buscar dados")
        return@withContext try {
            val root = JSONObject(body)
            val arr  = root.optJSONArray("data") ?: JSONArray()
            val lista = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PCPProgramacaoItem(
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
                    qtdTotalLp           = obj.optDouble("QTD_TOTAL_LP", 0.0),
                    cliente              = obj.optString("CLIENTE", ""),
                    vendedor             = obj.optString("VENDEDOR", ""),
                    nomeVendedor         = obj.optString("NOME_VENDEDOR", "")
                )
            }
            Log.i(PCPPROG_TAG, "✅ Dados Programação: ${lista.size} itens")
            PcpProgResult(true, lista)
        } catch (e: Exception) {
            Log.e(PCPPROG_TAG, "💥 Parse dados programação: ${e.message}", e)
            PcpProgResult(false, error = e.message)
        }
    }

    // ── Gerar PDF (legado – via report server) ────────────────────────────────
    suspend fun fetchRelatorioPdf(
        idFilial: Int,
        idEmpresa: Int,
        tipoLinha: String,
        idLinha: String,
        dataInicio: String,
        dataFim: String,
        situacao: String,
        ordem: String
    ): PcpProgResult<ByteArray> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("$PCPPROG_BASE_REPORT/pcpprogramacaopcp")
        sb.append("/$idEmpresa/$idFilial")
        sb.append("?tpLinha=$tipoLinha")
        sb.append("&idLinha=${idLinha.ifBlank { "0" }}")
        sb.append("&dataInicio=${dataInicio.replace("/", "%2F")}")
        sb.append("&dataFim=${dataFim.replace("/", "%2F")}")
        sb.append("&situacao=$situacao")
        sb.append("&ordem=$ordem")
        val url = sb.toString()
        Log.i(PCPPROG_TAG, "🎯 fetchRelatorioPdf ProgramacaoPCP URL: $url")
        val bytes = getBytes(url)
            ?: return@withContext PcpProgResult(false, error = "Erro de rede ao baixar PDF")
        if (bytes.isEmpty())
            return@withContext PcpProgResult(false, error = "PDF vazio retornado pela API")
        Log.i(PCPPROG_TAG, "✅ PDF PCP Programação: ${bytes.size} bytes")
        PcpProgResult(true, bytes)
    }
}
