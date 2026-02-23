package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val FIN_TAG = "VST_FINANCE"
private const val BASE_URL = "https://compras.vstsolution.com"

data class TituloInfo(val saldo: Double = 0.0, val qtdTitulos: Int = 0)
data class DashboardData(
    val pagarvencido: TituloInfo = TituloInfo(),
    val pagardodia: TituloInfo = TituloInfo(),
    val pagarrestantemes: TituloInfo = TituloInfo(),
    val recebervencido: TituloInfo = TituloInfo(),
    val receberdodia: TituloInfo = TituloInfo(),
    val receberrestantemes: TituloInfo = TituloInfo()
)
data class BancoSaldo(val descBanco: String, val saldo: Double)
data class FluxoCaixaMes(val nomeMes: String, val saldoInicial: Double, val receber: Double, val pagar: Double, val saldoFinal: Double)
data class TipoDocItem(val tipo: String, val saldo: Double)
data class LocalCobrancaItem(val local: String, val saldo: Double)
data class GraficosData(
    val receberTipoDocto: List<TipoDocItem> = emptyList(),
    val pagarTipoDocto: List<TipoDocItem> = emptyList(),
    val receberLocalCobranca: List<LocalCobrancaItem> = emptyList(),
    val pagarLocalCobranca: List<LocalCobrancaItem> = emptyList()
)
data class InadimplenciaCliente(val nome: String, val qtd: Int, val saldo: Double)
data class InadimplenciaVendedor(val nome: String, val qtd: Int, val saldo: Double)
data class RankingInadimplencia(val clientes: List<InadimplenciaCliente> = emptyList(), val vendedores: List<InadimplenciaVendedor> = emptyList())
data class FinanceResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

class FinanceService(private val userToken: String) {

    private fun parseSaldo(value: Any?): Double = when (value) {
        null -> 0.0
        is Double -> value
        is Int -> value.toDouble()
        is String -> value.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    private fun get(url: String): String? {
        return try {
            Log.i(FIN_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(FIN_TAG, "➡️  GET $url")
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
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                Log.i(FIN_TAG, "✅ $status ${duration}ms")
                Log.i(FIN_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                body
            } else {
                val err = try { conn.errorStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
                Log.e(FIN_TAG, "❌ $status — $err")
                null
            }
        } catch (e: Exception) {
            Log.e(FIN_TAG, "❌ EXCEPTION: ${e.message}")
            null
        }
    }

    suspend fun fetchSaldosBancarios(idEmpresa: Int): FinanceResult<Pair<Double, List<BancoSaldo>>> =
        withContext(Dispatchers.IO) {
            try {
                val body = get("$BASE_URL/saldosbancario/$idEmpresa") ?: return@withContext FinanceResult(false, error = "Sem resposta")
                val array = JSONArray(body)
                if (array.length() == 0) return@withContext FinanceResult(true, Pair(0.0, emptyList()))
                val first = array.getJSONObject(0)
                val saldoTotal = parseSaldo(first.opt("saldototal"))
                val bancos = mutableListOf<BancoSaldo>()
                val saldosArray = first.optJSONArray("saldosbancario")
                if (saldosArray != null) {
                    for (i in 0 until saldosArray.length()) {
                        val b = saldosArray.getJSONObject(i)
                        bancos.add(BancoSaldo(b.optString("DESCBANCO", ""), parseSaldo(b.opt("SALDO"))))
                    }
                }
                FinanceResult(true, Pair(saldoTotal, bancos))
            } catch (e: Exception) { FinanceResult(false, error = e.message) }
        }

    suspend fun fetchPagarReceber(idEmpresa: Int, idFilial: Int): FinanceResult<DashboardData> =
        withContext(Dispatchers.IO) {
            try {
                val body = get("$BASE_URL/pagarreceber/$idEmpresa/idfilial/$idFilial") ?: return@withContext FinanceResult(false, error = "Sem resposta")
                val array = JSONArray(body)
                var data = DashboardData()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    fun extractTitulo(key: String): TituloInfo {
                        val arr = item.optJSONArray(key) ?: return TituloInfo()
                        if (arr.length() == 0) return TituloInfo()
                        val o = arr.getJSONObject(0)
                        return TituloInfo(parseSaldo(o.opt("SALDO")), o.optInt("QTD_TITULOS", 0))
                    }
                    fun merge(novo: TituloInfo, atual: TituloInfo) = if (novo.saldo > 0 || novo.qtdTitulos > 0) novo else atual
                    data = data.copy(
                        pagarvencido = merge(extractTitulo("pagarvencido"), data.pagarvencido),
                        pagardodia = merge(extractTitulo("pagardodia"), data.pagardodia),
                        pagarrestantemes = merge(extractTitulo("pagarrestantemes"), data.pagarrestantemes),
                        recebervencido = merge(extractTitulo("recebervencido"), data.recebervencido),
                        receberdodia = merge(extractTitulo("receberdodia"), data.receberdodia),
                        receberrestantemes = merge(extractTitulo("receberrestantemes"), data.receberrestantemes)
                    )
                }
                FinanceResult(true, data)
            } catch (e: Exception) { FinanceResult(false, error = e.message) }
        }

    suspend fun fetchFluxoCaixa(idEmpresa: Int, idFilial: Int): FinanceResult<List<FluxoCaixaMes>> =
        withContext(Dispatchers.IO) {
            try {
                val body = get("$BASE_URL/fluxocaixamensal/$idEmpresa/idfilial/$idFilial") ?: return@withContext FinanceResult(false, error = "Sem resposta")
                val array = JSONArray(body)
                val meses = mutableListOf<FluxoCaixaMes>()
                if (array.length() > 0) {
                    val dataArr = array.getJSONObject(0).optJSONArray("data")
                    if (dataArr != null) {
                        for (i in 0 until dataArr.length()) {
                            val m = dataArr.getJSONObject(i)
                            meses.add(FluxoCaixaMes(m.optString("NOMEMES", "Mês ${i+1}"), parseSaldo(m.opt("SALDOINICIAL")), parseSaldo(m.opt("RECEBER")), parseSaldo(m.opt("PAGAR")), parseSaldo(m.opt("SALDOFINAL"))))
                        }
                    }
                }
                FinanceResult(true, meses)
            } catch (e: Exception) { FinanceResult(false, error = e.message) }
        }

    suspend fun fetchGraficos(idEmpresa: Int, idFilial: Int): FinanceResult<GraficosData> =
        withContext(Dispatchers.IO) {
            try {
                val body = get("$BASE_URL/pagrecdocloc/$idEmpresa/idfilial/$idFilial") ?: return@withContext FinanceResult(false, error = "Sem resposta")
                val array = JSONArray(body)
                var graficos = GraficosData()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val desc = item.optString("DESCDASHBOARD", "").lowercase()
                    val regs = item.optJSONArray("Registros") ?: continue
                    fun doctoList() = (0 until regs.length()).map { j -> val o = regs.getJSONObject(j); TipoDocItem(o.optString("TIPODOCUMENTO"), parseSaldo(o.opt("SALDO"))) }
                    fun localList() = (0 until regs.length()).map { j -> val o = regs.getJSONObject(j); LocalCobrancaItem(o.optString("LOCALCOBRANCA"), parseSaldo(o.opt("SALDO"))) }
                    graficos = when {
                        desc.contains("receber") && desc.contains("tipo") -> graficos.copy(receberTipoDocto = doctoList())
                        desc.contains("pagar") && desc.contains("tipo") -> graficos.copy(pagarTipoDocto = doctoList())
                        desc.contains("receber") && desc.contains("local") -> graficos.copy(receberLocalCobranca = localList())
                        desc.contains("pagar") && desc.contains("local") -> graficos.copy(pagarLocalCobranca = localList())
                        else -> graficos
                    }
                }
                FinanceResult(true, graficos)
            } catch (e: Exception) { FinanceResult(false, error = e.message) }
        }

    suspend fun fetchRankingInadimplencia(idEmpresa: Int, idFilial: Int): FinanceResult<RankingInadimplencia> =
        withContext(Dispatchers.IO) {
            try {
                val body = get("$BASE_URL/rankinginadimplencia/$idEmpresa/idfilial/$idFilial") ?: return@withContext FinanceResult(false, error = "Sem resposta")
                val array = JSONArray(body)
                val clientes = mutableListOf<InadimplenciaCliente>()
                val vendedores = mutableListOf<InadimplenciaVendedor>()
                if (array.length() > 0) {
                    val regs = array.getJSONObject(0).optJSONArray("Registros")
                    regs?.let { for (i in 0 until it.length()) { val r = it.getJSONObject(i); clientes.add(InadimplenciaCliente(r.optString("CLIENTE"), r.optInt("QTD"), parseSaldo(r.opt("SALDO")))) } }
                }
                if (array.length() > 1) {
                    val regs = array.getJSONObject(1).optJSONArray("Registros")
                    regs?.let { for (i in 0 until it.length()) { val v = it.getJSONObject(i); vendedores.add(InadimplenciaVendedor(v.optString("VENDEDOR", v.optString("NOME")), v.optInt("QTD"), parseSaldo(v.opt("SALDO")))) } }
                }
                FinanceResult(true, RankingInadimplencia(clientes, vendedores))
            } catch (e: Exception) { FinanceResult(false, error = e.message) }
        }
}

