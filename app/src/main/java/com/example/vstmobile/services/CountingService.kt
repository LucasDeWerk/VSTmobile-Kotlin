package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val CNT_TAG  = "VST_COUNTING"
private const val CNT_BASE = "https://compras.vstsolution.com"

data class CountingResult(val success: Boolean, val inventarioId: Int? = null, val error: String? = null)

class CountingService(private val userToken: String) {

    private fun get(url: String): String? = try {
        Log.i(CNT_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(CNT_TAG, "➡️  GET $url")
        val start = System.currentTimeMillis()
        val conn  = URL(url).openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $userToken")
        conn.connectTimeout = 15000; conn.readTimeout = 15000
        val status = conn.responseCode
        val dur    = System.currentTimeMillis() - start
        if (status == 200) {
            val body = conn.inputStream.bufferedReader().readText()
            Log.i(CNT_TAG, "✅ $status (${dur}ms) | ${body.length} chars")
            Log.d(CNT_TAG, "📦 Body: ${body.take(500)}")
            body
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(CNT_TAG, "❌ HTTP $status (${dur}ms) | $err"); null
        }
    } catch (e: Exception) { Log.e(CNT_TAG, "💥 GET: ${e.message}", e); null }

    private fun put(url: String, body: String): Pair<Int, String?> = try {
        Log.i(CNT_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(CNT_TAG, "➡️  PUT $url")
        Log.d(CNT_TAG, "📤 Body: ${body.take(300)}")
        val start = System.currentTimeMillis()
        val conn  = URL(url).openConnection() as HttpsURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $userToken")
        conn.doOutput = true
        conn.connectTimeout = 15000; conn.readTimeout = 15000
        conn.outputStream.use { it.write(body.toByteArray()) }
        val status = conn.responseCode
        val dur    = System.currentTimeMillis() - start
        val resp = try { conn.inputStream.bufferedReader().readText() }
                   catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
        Log.i(CNT_TAG, "${if (status in 200..299) "✅" else "❌"} PUT $status (${dur}ms)")
        Log.d(CNT_TAG, "📥 Resp: ${resp.take(300)}")
        Pair(status, resp)
    } catch (e: Exception) { Log.e(CNT_TAG, "💥 PUT: ${e.message}", e); Pair(-1, null) }

    // ── Buscar produtos do inventário ─────────────────────────────────────────
    // GET /prodlistaiteminventario/{empresa}/idfilial/{filial}/idinventario/{inv}
    suspend fun fetchProdutosInventario(
        idEmpresa: Int, idFilial: Int, idInventario: Int
    ): InvResult<List<ProdutoItemInventario>> = withContext(Dispatchers.IO) {
        val url = "$CNT_BASE/prodlistaiteminventario/$idEmpresa/idfilial/$idFilial/idinventario/$idInventario"
        Log.i(CNT_TAG, "🎯 fetchProdutosInventario | inv=$idInventario")
        val body = get(url) ?: return@withContext InvResult(false, error = "Erro de rede")
        return@withContext try {
            val arr = try { JSONObject(body).optJSONArray("data") ?: JSONArray() }
                      catch (_: Exception) { JSONArray() }
            val lista = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ProdutoItemInventario(
                    idProduto  = o.optInt("ID_PRODUTO"),
                    descricao  = o.optString("DESCPRODUTO", ""),
                    qtdContada = if (o.isNull("QTD_CONTADA")) "Não Contado"
                                 else o.optString("QTD_CONTADA", "Não Contado")
                )
            }
            Log.i(CNT_TAG, "✅ ${lista.size} produto(s)")
            InvResult(true, lista)
        } catch (e: Exception) {
            Log.e(CNT_TAG, "Parse: ${e.message}", e)
            InvResult(false, error = e.message)
        }
    }

    // ── Salvar contagem individual (PUT) ──────────────────────────────────────
    // PUT /prodinventario/{empresa}/idfilial/{filial}/idinventario/{inv}/idproduto/{prod}
    // Body: { inventario: [{ ID_ALMOXARIFADO, QTD_CONTADA, QTD_DIFERENCA }] }
    suspend fun salvarContagem(
        idEmpresa: Int, idFilial: Int, idInventario: Int, idProduto: Int,
        idAlmoxarifado: Int, qtdContada: Double, qtdEstoque: Double
    ): CountingResult = withContext(Dispatchers.IO) {
        val url = "$CNT_BASE/prodinventario/$idEmpresa/idfilial/$idFilial/idinventario/$idInventario/idproduto/$idProduto"
        val diferenca = qtdContada - qtdEstoque
        val payload = JSONObject().put(
            "inventario",
            JSONArray().put(
                JSONObject()
                    .put("ID_ALMOXARIFADO", idAlmoxarifado)
                    .put("QTD_CONTADA", qtdContada)
                    .put("QTD_DIFERENCA", diferenca)
            )
        ).toString()

        Log.i(CNT_TAG, "🎯 salvarContagem | emp=$idEmpresa fil=$idFilial inv=$idInventario prod=$idProduto")
        Log.i(CNT_TAG, "Calculando diferença: $qtdContada (contada) - $qtdEstoque (estoque) = $diferenca")
        Log.d(CNT_TAG, "Payload: $payload")

        val (status, resp) = put(url, payload)
        if (status in 200..299) {
            var inventarioId: Int? = null
            try {
                val json = JSONObject(resp ?: "")
                inventarioId = json.optInt("ID_INVENTARIO", -1).takeIf { it > 0 }
                    ?: json.optInt("id_inventario", -1).takeIf { it > 0 }
                    ?: json.optInt("data", -1).takeIf { it > 0 }
            } catch (_: Exception) {}
            Log.i(CNT_TAG, "✅ Contagem salva | inventarioId=${inventarioId ?: "N/A"}")
            CountingResult(true, inventarioId)
        } else {
            Log.e(CNT_TAG, "❌ Erro ao salvar: status=$status")
            CountingResult(false, error = "Erro $status ao salvar contagem")
        }
    }
}

