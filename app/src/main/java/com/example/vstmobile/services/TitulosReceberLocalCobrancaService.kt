package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val TRLC_TAG         = "VST_TRECEBER_LC"
private const val TRLC_BASE_REPORT = "https://report.vstsolution.com"

// Locais de cobrança fixos (JS usa esses IDs estáticos)
data class LocalCobrancaReceber(
    val id: Int,
    val descricao: String
)

val LOCAIS_COBRANCA_RECEBER = listOf(
    LocalCobrancaReceber(1, "Pagável em qualquer BCO até o vencimento"),
    LocalCobrancaReceber(2, "Carteira"),
    LocalCobrancaReceber(3, "Banco"),
    LocalCobrancaReceber(4, "Título protestado"),
    LocalCobrancaReceber(5, "Baixa filiais"),
    LocalCobrancaReceber(6, "Permuta"),
    LocalCobrancaReceber(7, "Aguardando PCP")
)

val OPCOES_ORDENACAO_RECEBER = listOf(
    "DTVENCIMENTO" to "Data de Vencimento",
    "DTCADASTRO"   to "Data de Cadastro",
    "DTEMISSAO"    to "Data de Emissão",
    "CLIENTE"      to "Cliente",
    "VALOR"        to "Valor"
)

val OPCOES_PERIODO_RECEBER = listOf(
    "DTVENCIMENTO" to "Data de Vencimento",
    "DTCADASTRO"   to "Data de Cadastro",
    "DTEMISSAO"    to "Data de Emissão"
)

data class TrlcResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

class TitulosReceberLocalCobrancaService(private val userToken: String) {

    private fun getBytes(url: String): ByteArray? = try {
        Log.i(TRLC_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TRLC_TAG, "➡️  GET (bytes) $url")
        val start = System.currentTimeMillis()
        val conn  = URL(url).openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $userToken")
        conn.connectTimeout = 30000
        conn.readTimeout    = 30000
        val status = conn.responseCode
        val dur    = System.currentTimeMillis() - start
        if (status == 200) {
            val bytes = conn.inputStream.readBytes()
            Log.i(TRLC_TAG, "✅ $status (${dur}ms) | ${bytes.size} bytes")
            bytes
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(TRLC_TAG, "❌ HTTP $status (${dur}ms) | $err")
            null
        }
    } catch (e: Exception) {
        Log.e(TRLC_TAG, "💥 getBytes: ${e.message}", e)
        null
    }

    // ── Gerar PDF ─────────────────────────────────────────────────────────────
    suspend fun fetchRelatorioPdf(
        idEmpresa: String,
        idFilial: String,
        dtIni: String,
        dtFim: String,
        periodo: String    = "DTVENCIMENTO",
        ordenarPor: String = "DTVENCIMENTO",
        idsLocais: List<Int>,
        impComp: Boolean   = false,
        impAdt: Boolean    = false,
        impCheque: Boolean = false,
        valorDe: String    = "",
        valorAte: String   = ""
    ): TrlcResult<ByteArray> = withContext(Dispatchers.IO) {

        val sb = StringBuilder(
            "$TRLC_BASE_REPORT/receberlocalcobranca/$idEmpresa/idfilial/$idFilial/dtini/$dtIni/dtfim/$dtFim"
        )
        sb.append("?periodo=$periodo")
        sb.append("&ordenar=$ordenarPor")
        sb.append("&idlocalcob=${idsLocais.joinToString(",")}")
        sb.append("&impcomp=${if (impComp) "S" else "N"}")
        sb.append("&impadt=${if (impAdt) "S" else "N"}")
        sb.append("&impcheque=${if (impCheque) "S" else "N"}")
        if (valorDe.isNotEmpty())  sb.append("&valor_de=$valorDe")
        if (valorAte.isNotEmpty()) sb.append("&valor_ate=$valorAte")

        val url = sb.toString()
        Log.i(TRLC_TAG, "🎯 fetchRelatorioPdf")
        Log.i(TRLC_TAG, "URL: $url")
        Log.i(TRLC_TAG, "Params: empresa=$idEmpresa filial=$idFilial dtIni=$dtIni dtFim=$dtFim " +
                "periodo=$periodo ordenar=$ordenarPor locais=${idsLocais.joinToString(",")} " +
                "impComp=$impComp impAdt=$impAdt impCheque=$impCheque " +
                "valorDe=${valorDe.ifEmpty { "N/A" }} valorAte=${valorAte.ifEmpty { "N/A" }}")

        val bytes = getBytes(url)
            ?: return@withContext TrlcResult(false, error = "Erro de rede ao baixar PDF")
        if (bytes.isEmpty())
            return@withContext TrlcResult(false, error = "PDF vazio retornado pela API")

        Log.i(TRLC_TAG, "✅ PDF: ${bytes.size} bytes")
        TrlcResult(true, bytes)
    }
}

