package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val DRE_TAG         = "VST_DRE"
private const val DRE_BASE_API    = "https://compras.vstsolution.com"
private const val DRE_BASE_REPORT = "https://report.vstsolution.com"

data class ModeloDre(
    val id: Int,
    val descricao: String
)

data class DreResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

// Dados estáticos — iguais ao JS
val DRE_ANOS = listOf("2025","2024","2023","2022","2021","2020","2019","2018")

val DRE_MESES = listOf(
    "1"  to "Janeiro",
    "2"  to "Fevereiro",
    "3"  to "Março",
    "4"  to "Abril",
    "5"  to "Maio",
    "6"  to "Junho",
    "7"  to "Julho",
    "8"  to "Agosto",
    "9"  to "Setembro",
    "10" to "Outubro",
    "11" to "Novembro",
    "12" to "Dezembro"
)

val DRE_PERIODOS = listOf(
    "0" to "Mensal",
    "1" to "Anual"
)

class DreService(private val userToken: String) {

    private fun get(url: String): String? = try {
        Log.i(DRE_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(DRE_TAG, "➡️  GET $url")
        val start = System.currentTimeMillis()
        val conn  = URL(url).openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $userToken")
        conn.connectTimeout = 15000
        conn.readTimeout    = 15000
        val status = conn.responseCode
        val dur    = System.currentTimeMillis() - start
        if (status == 200) {
            val body = conn.inputStream.bufferedReader().readText()
            Log.i(DRE_TAG, "✅ $status (${dur}ms) | ${body.length} chars")
            Log.d(DRE_TAG, "📦 Body: ${body.take(600)}")
            body
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(DRE_TAG, "❌ HTTP $status (${dur}ms) | $err")
            null
        }
    } catch (e: Exception) {
        Log.e(DRE_TAG, "💥 GET: ${e.message}", e)
        null
    }

    private fun getBytes(url: String): ByteArray? = try {
        Log.i(DRE_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(DRE_TAG, "➡️  GET (bytes) $url")
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
            Log.i(DRE_TAG, "✅ $status (${dur}ms) | ${bytes.size} bytes")
            bytes
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(DRE_TAG, "❌ HTTP $status (${dur}ms) | $err")
            null
        }
    } catch (e: Exception) {
        Log.e(DRE_TAG, "💥 getBytes: ${e.message}", e)
        null
    }

    // ── Buscar modelos DRE ────────────────────────────────────────────────────
    // GET /modelodre/{idEmpresa}
    suspend fun fetchModelosDre(idEmpresa: Int): DreResult<List<ModeloDre>> =
        withContext(Dispatchers.IO) {
            val url = "$DRE_BASE_API/modelodre/$idEmpresa"
            Log.i(DRE_TAG, "🎯 fetchModelosDre | empresa=$idEmpresa")
            val body = get(url) ?: return@withContext DreResult(false, error = "Erro de rede")
            return@withContext try {
                val root = JSONObject(body)
                // API pode retornar { data: [...] } ou diretamente [...]
                val arr: JSONArray = root.optJSONArray("data") ?: run {
                    // Tenta como array direto
                    try { JSONArray(body) } catch (_: Exception) { JSONArray() }
                }
                val lista = (0 until arr.length()).mapNotNull {
                    val obj = arr.getJSONObject(it)
                    val id  = obj.optInt("ID_DRE", -1).takeIf { v -> v >= 0 }
                        ?: obj.optInt("id", -1).takeIf { v -> v >= 0 }
                        ?: obj.optInt("ID", -1).takeIf { v -> v >= 0 }
                        ?: return@mapNotNull null
                    val desc = obj.optString("DESCRICAO", "")
                        .ifEmpty { obj.optString("nome", "") }
                        .ifEmpty { obj.optString("NOME", "") }
                        .ifEmpty { "Modelo $id" }
                    ModeloDre(id, desc)
                }
                Log.i(DRE_TAG, "✅ ${lista.size} modelo(s) DRE carregado(s)")
                DreResult(true, lista)
            } catch (e: Exception) {
                Log.e(DRE_TAG, "💥 Parse modelos: ${e.message}", e)
                DreResult(false, error = e.message)
            }
        }

    // ── Gerar PDF DRE ─────────────────────────────────────────────────────────
    // GET /ctbdre/1/idfilial/{idFilial}?filiais=1,2,3&iddre={idDre}&idano={ano}&tpEmissao={0|1}&tpestrutura={0|1}[&idmes=MM]
    suspend fun fetchRelatorioPdf(
        idFilialPrincipal: Int,
        filiaisIds: List<Int>,
        idDre: Int,
        ano: String,
        periodo: String,           // "0" = Mensal, "1" = Anual
        mes: String = "",          // Somente quando periodo == "0"
        estruturaPronta: Boolean = false
    ): DreResult<ByteArray> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("$DRE_BASE_REPORT/ctbdre/1/idfilial/$idFilialPrincipal")
        sb.append("?filiais=${filiaisIds.joinToString(",")}")
        sb.append("&iddre=$idDre")
        sb.append("&idano=$ano")
        sb.append("&tpEmissao=$periodo")
        sb.append("&tpestrutura=${if (estruturaPronta) "1" else "0"}")
        if (periodo == "0" && mes.isNotEmpty()) {
            val mesFormatado = mes.padStart(2, '0')
            sb.append("&idmes=$mesFormatado")
        }
        val url = sb.toString()
        Log.i(DRE_TAG, "🎯 fetchRelatorioPdf DRE")
        Log.i(DRE_TAG, "URL: $url")
        Log.i(DRE_TAG, "Params: filialPrincipal=$idFilialPrincipal filiais=${filiaisIds.joinToString(",")} " +
                "idDre=$idDre ano=$ano periodo=$periodo mes=${mes.ifEmpty { "N/A" }} estruturaPronta=$estruturaPronta")
        val bytes = getBytes(url)
            ?: return@withContext DreResult(false, error = "Erro de rede ao baixar PDF")
        if (bytes.isEmpty())
            return@withContext DreResult(false, error = "PDF vazio retornado pela API")
        Log.i(DRE_TAG, "✅ PDF DRE: ${bytes.size} bytes")
        DreResult(true, bytes)
    }
}

