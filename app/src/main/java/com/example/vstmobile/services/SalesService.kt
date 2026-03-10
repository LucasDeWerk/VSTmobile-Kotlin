package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val SALES_TAG = "VST_SALES"
private val SALES_BASE = ApiConfig.BASE_API

// ── Data classes ──────────────────────────────────────────────────────────────

data class SalesCardData(
    val faturamento: Double = 0.0,
    val lucroBruto: Double = 0.0,
    val devolucoes: Double = 0.0,
    val faturamentoLiquido: Double = 0.0,
    val custoFaturamento: Double = 0.0,
    val impostoTotal: Double = 0.0,
    val quantidadePedidos: Int = 0,
    val ticketMedio: Double = 0.0,
    val margemLucro: Double = 0.0
)

data class FaturamentoMes(
    val mes: Int,
    val faturamento: Double,
    val dtCadastro: String
)


data class FilialRanking(
    val identificacao: String,
    val qtdVendas: Int,
    val faturamento: Double
)

data class RankingDash(
    val descDashboard: String,
    val registros: List<RankingItem>
)

data class RankingItem(
    val nome: String,
    val valor: Double
)

data class SalesResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

// ── Service ───────────────────────────────────────────────────────────────────

class SalesService(private val userToken: String) {

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
            Log.i(SALES_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(SALES_TAG, "➡️  GET $url")
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
                Log.i(SALES_TAG, "✅ ${status} (${duration}ms) | ${body.length} chars")
                Log.d(SALES_TAG, "📦 Body: ${body.take(500)}")
                body
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
                Log.e(SALES_TAG, "❌ HTTP $status (${duration}ms) | $err")
                null
            }
        } catch (e: Exception) {
            Log.e(SALES_TAG, "💥 Exceção: ${e.message}", e)
            null
        }
    }

    // ── Faturamento Card (cards de resumo) ────────────────────────────────────
    suspend fun fetchFaturamentoCard(
        idEmpresa: String,
        idFilial: String,
        tpFat: String,
        custo: String
    ): SalesResult<SalesCardData> = withContext(Dispatchers.IO) {
        val url = "$SALES_BASE/faturamentocard/$idEmpresa/idfilial/$idFilial?tpfat=$tpFat&custo=$custo"
        Log.i(SALES_TAG, "🎯 fetchFaturamentoCard | empresa=$idEmpresa filial=$idFilial período=$tpFat custo=$custo")
        val body = get(url) ?: return@withContext SalesResult(false, error = "Erro de rede")
        return@withContext try {
            val j = JSONObject(body)
            val devTotal = j.optJSONArray("DevFaturamento")
                ?.optJSONObject(0)?.optDouble("TOTAL_DEVOLVIDO", 0.0) ?: 0.0
            val data = SalesCardData(
                faturamento = parseSaldo(j.opt("TotalFaturamento")),
                lucroBruto = parseSaldo(j.opt("LucroBruto")),
                devolucoes = devTotal,
                faturamentoLiquido = parseSaldo(j.opt("FaturamentoLiquido")),
                custoFaturamento = parseSaldo(j.opt("CustoFaturamento")),
                impostoTotal = parseSaldo(j.opt("TotalImposto")),
                quantidadePedidos = j.optInt("QtdPedidos", 0),
                ticketMedio = parseSaldo(j.opt("TicketMedio")),
                margemLucro = parseSaldo(j.opt("MargemLucro"))
            )
            Log.i(SALES_TAG, "✅ Card: fat=${data.faturamento} lucro=${data.lucroBruto} margem=${data.margemLucro}")
            SalesResult(true, data)
        } catch (e: Exception) {
            Log.e(SALES_TAG, "💥 Parse card: ${e.message}", e)
            SalesResult(false, error = e.message)
        }
    }

    // ── Faturamento mensal/anual ───────────────────────────────────────────────
    suspend fun fetchFaturamento(
        idEmpresa: String,
        idFilial: String,
        tpFat: String
    ): SalesResult<List<FaturamentoMes>> = withContext(Dispatchers.IO) {
        val url = "$SALES_BASE/faturamento/$idEmpresa/idfilial/$idFilial?tpfat=$tpFat"
        Log.i(SALES_TAG, "🎯 fetchFaturamento | período=$tpFat")
        val body = get(url) ?: return@withContext SalesResult(false, error = "Erro de rede")
        return@withContext try {
            val arr = JSONArray(body)
            val lista = mutableListOf<FaturamentoMes>()
            // Primeiro item tem RegistrosFat
            if (arr.length() > 0) {
                val registros = arr.getJSONObject(0).optJSONArray("RegistrosFat") ?: JSONArray()
                for (i in 0 until registros.length()) {
                    val r = registros.getJSONObject(i)
                    lista.add(FaturamentoMes(
                        mes = r.optInt("MES", i + 1),
                        faturamento = parseSaldo(r.opt("FATURAMENTO")),
                        dtCadastro = r.optString("DTCADASTRO", "")
                    ))
                }
            }
            Log.i(SALES_TAG, "✅ Faturamento: ${lista.size} registros")
            SalesResult(true, lista)
        } catch (e: Exception) {
            Log.e(SALES_TAG, "💥 Parse faturamento: ${e.message}", e)
            SalesResult(false, error = e.message)
        }
    }

    // ── Imposto mensal/anual ──────────────────────────────────────────────────
    suspend fun fetchImposto(
        idEmpresa: String,
        idFilial: String,
        tpFat: String
    ): SalesResult<List<Double>> = withContext(Dispatchers.IO) {
        val url = "$SALES_BASE/faturamento/$idEmpresa/idfilial/$idFilial?tpfat=$tpFat"
        Log.i(SALES_TAG, "🎯 fetchImposto | período=$tpFat")
        val body = get(url) ?: return@withContext SalesResult(false, error = "Erro de rede")
        return@withContext try {
            val arr = JSONArray(body)
            val lista = mutableListOf<Double>()
            // Segundo item tem RegistrosImp
            if (arr.length() > 1) {
                val registros = arr.getJSONObject(1).optJSONArray("RegistrosImp") ?: JSONArray()
                for (i in 0 until registros.length()) {
                    lista.add(parseSaldo(registros.getJSONObject(i).opt("TOTAL_IMPOSTO")))
                }
            }
            Log.i(SALES_TAG, "✅ Impostos: ${lista.size} registros")
            SalesResult(true, lista)
        } catch (e: Exception) {
            Log.e(SALES_TAG, "💥 Parse imposto: ${e.message}", e)
            SalesResult(false, error = e.message)
        }
    }

    // ── Faturamento por filial ────────────────────────────────────────────────
    suspend fun fetchFaturamentoPorFilial(
        idEmpresa: String,
        idFilial: String,
        tpFat: String
    ): SalesResult<List<FilialRanking>> = withContext(Dispatchers.IO) {
        val url = "$SALES_BASE/faturamentoporfilial/$idEmpresa/idfilial/$idFilial?tpfat=$tpFat"
        Log.i(SALES_TAG, "🎯 fetchFaturamentoPorFilial | período=$tpFat")
        val body = get(url) ?: return@withContext SalesResult(false, error = "Erro de rede")
        return@withContext try {
            val arr = JSONArray(body)
            val lista = mutableListOf<FilialRanking>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val registros = obj.optJSONArray("Registros") ?: continue
                for (j in 0 until registros.length()) {
                    val r = registros.getJSONObject(j)
                    lista.add(FilialRanking(
                        identificacao = r.optString("IDENTIFICACAO_INTERNA", "Sem Nome"),
                        qtdVendas = r.optInt("QTD_VENDAS", 0),
                        faturamento = parseSaldo(r.opt("FATURAMENTO"))
                    ))
                }
            }
            Log.i(SALES_TAG, "✅ Por filial: ${lista.size} filiais")
            SalesResult(true, lista)
        } catch (e: Exception) {
            Log.e(SALES_TAG, "💥 Parse por filial: ${e.message}", e)
            SalesResult(false, error = e.message)
        }
    }

    // ── Rankings de vendas ────────────────────────────────────────────────────
    suspend fun fetchRankingVendas(
        idEmpresa: String,
        idFilial: String,
        tpFat: String
    ): SalesResult<List<RankingDash>> = withContext(Dispatchers.IO) {
        val url = "$SALES_BASE/rankingvendas/$idEmpresa/idfilial/$idFilial?tpfat=$tpFat"
        Log.i(SALES_TAG, "🎯 fetchRankingVendas | período=$tpFat")
        val body = get(url) ?: return@withContext SalesResult(false, error = "Erro de rede")
        return@withContext try {
            val arr = JSONArray(body)
            val lista = mutableListOf<RankingDash>()
            for (i in 0 until arr.length()) {
                val dash = arr.getJSONObject(i)
                val desc = dash.optString("DESCDASHBOARD", "Ranking")
                val registros = dash.optJSONArray("Registros") ?: continue
                val items = mutableListOf<RankingItem>()
                for (j in 0 until registros.length()) {
                    val r = registros.getJSONObject(j)
                    val nome = r.optString("DESCGRUPO", "")
                        .ifEmpty { r.optString("NOMEFUNC", "") }
                        .ifEmpty { r.optString("DESCPRODUTO", "") }
                        .ifEmpty { r.optString("DESCTIPOPAGREC", "") }
                        .ifEmpty { "Sem Nome" }
                    val valor = parseSaldo(r.opt("VLR_VENDIDO"))
                        .takeIf { it > 0 } ?: parseSaldo(r.opt("TOTAL_VENDAS"))
                    items.add(RankingItem(nome = nome, valor = valor))
                }
                if (items.isNotEmpty()) lista.add(RankingDash(desc, items))
            }
            Log.i(SALES_TAG, "✅ Rankings: ${lista.size} dashboards")
            SalesResult(true, lista)
        } catch (e: Exception) {
            Log.e(SALES_TAG, "💥 Parse rankings: ${e.message}", e)
            SalesResult(false, error = e.message)
        }
    }
}

