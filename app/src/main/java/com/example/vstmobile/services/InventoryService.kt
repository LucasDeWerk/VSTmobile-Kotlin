package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val INV_TAG  = "VST_INVENTORY"
private const val INV_BASE = "https://compras.vstsolution.com"

// ── Modelos de dados ──────────────────────────────────────────────────────────
data class Almoxarifado(val id: Int, val descricao: String)
data class ProdutoInventario(
    val idProduto: Int,
    val descricao: String,
    val estoque: Double,
    val abreviatura: String,
    val custoCompra: Double
)
data class GrupoInventario(val id: Int, val descricao: String)
data class SubgrupoInventario(val id: Int, val descricao: String, val idGrupo: Int)
data class MarcaInventario(val id: Int, val descricao: String)
data class CodigoInventario(val idInventario: Int, val idEmpresa: Int, val idFilial: Int)
data class ProdutoItemInventario(
    val idProduto: Int,
    val descricao: String,
    val qtdContada: String
)

data class InvResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

class InventoryService(private val userToken: String) {

    private fun get(url: String): String? = try {
        Log.i(INV_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(INV_TAG, "➡️  GET $url")
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
            Log.i(INV_TAG, "✅ $status (${dur}ms) | ${body.length} chars")
            Log.d(INV_TAG, "📦 Body: ${body.take(500)}")
            body
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
            Log.e(INV_TAG, "❌ HTTP $status (${dur}ms) | $err"); null
        }
    } catch (e: Exception) { Log.e(INV_TAG, "💥 GET: ${e.message}", e); null }

    private fun post(url: String, body: String): Pair<Int, String?> = try {
        Log.i(INV_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(INV_TAG, "➡️  POST $url")
        Log.d(INV_TAG, "📤 Body: ${body.take(500)}")
        val start = System.currentTimeMillis()
        val conn  = URL(url).openConnection() as HttpsURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $userToken")
        conn.doOutput = true
        conn.connectTimeout = 15000; conn.readTimeout = 15000
        conn.outputStream.use { it.write(body.toByteArray()) }
        val status = conn.responseCode
        val dur    = System.currentTimeMillis() - start
        val resp = try { conn.inputStream.bufferedReader().readText() }
                   catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
        Log.i(INV_TAG, "${if (status in 200..299) "✅" else "❌"} $status (${dur}ms)")
        Log.d(INV_TAG, "📥 Resp: ${resp.take(300)}")
        Pair(status, resp)
    } catch (e: Exception) { Log.e(INV_TAG, "💥 POST: ${e.message}", e); Pair(-1, null) }

    private fun parseArray(body: String): JSONArray =
        try { JSONObject(body).optJSONArray("data") ?: JSONArray() }
        catch (_: Exception) { try { JSONArray(body) } catch (_: Exception) { JSONArray() } }

    // ── 1. Almoxarifados ──────────────────────────────────────────────────────
    // GET /almoxarifado/{empresa}/idfilial/{filial}
    suspend fun fetchAlmoxarifados(idEmpresa: Int, idFilial: Int): InvResult<List<Almoxarifado>> =
        withContext(Dispatchers.IO) {
            val url = "$INV_BASE/almoxarifado/$idEmpresa/idfilial/$idFilial"
            Log.i(INV_TAG, "🎯 fetchAlmoxarifados | empresa=$idEmpresa filial=$idFilial")
            val body = get(url) ?: return@withContext InvResult(false, error = "Erro de rede")
            return@withContext try {
                val arr = parseArray(body)
                val lista = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    Almoxarifado(o.optInt("ID_ALMOXARIFADO"), o.optString("DESCALMOXARIFADO", ""))
                }
                Log.i(INV_TAG, "✅ ${lista.size} almoxarifado(s)")
                InvResult(true, lista)
            } catch (e: Exception) { Log.e(INV_TAG, "Parse: ${e.message}", e); InvResult(false, error = e.message) }
        }

    // ── 2. Códigos de inventário ──────────────────────────────────────────────
    // GET /prodlistainventario/{empresa}/idfilial/{filial}
    suspend fun fetchCodigosInventario(idEmpresa: Int, idFilial: Int): InvResult<List<CodigoInventario>> =
        withContext(Dispatchers.IO) {
            val url = "$INV_BASE/prodlistainventario/$idEmpresa/idfilial/$idFilial"
            Log.i(INV_TAG, "🎯 fetchCodigosInventario | empresa=$idEmpresa filial=$idFilial")
            val body = get(url) ?: return@withContext InvResult(false, error = "Erro de rede")
            return@withContext try {
                val arr = parseArray(body)
                val lista = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    CodigoInventario(
                        idInventario = o.optInt("ID_INVENTARIO"),
                        idEmpresa    = o.optInt("ID_EMPRESA"),
                        idFilial     = o.optInt("ID_FILIAL")
                    )
                }
                Log.i(INV_TAG, "✅ ${lista.size} código(s)")
                InvResult(true, lista)
            } catch (e: Exception) { Log.e(INV_TAG, "Parse: ${e.message}", e); InvResult(false, error = e.message) }
        }

    // ── 3. Itens de um inventário salvo ───────────────────────────────────────
    // GET /prodlistaiteminventario/{empresa}/idfilial/{filial}/idinventario/{idInv}
    suspend fun fetchItensInventario(idEmpresa: Int, idFilial: Int, idInventario: Int): InvResult<List<ProdutoItemInventario>> =
        withContext(Dispatchers.IO) {
            val url = "$INV_BASE/prodlistaiteminventario/$idEmpresa/idfilial/$idFilial/idinventario/$idInventario"
            Log.i(INV_TAG, "🎯 fetchItensInventario | inv=$idInventario")
            val body = get(url) ?: return@withContext InvResult(false, error = "Erro de rede")
            return@withContext try {
                val arr = parseArray(body)
                val lista = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    ProdutoItemInventario(
                        idProduto  = o.optInt("ID_PRODUTO"),
                        descricao  = o.optString("DESCPRODUTO", ""),
                        qtdContada = o.optString("QTD_CONTADA", "Não Contado")
                    )
                }
                Log.i(INV_TAG, "✅ ${lista.size} item(ns)")
                InvResult(true, lista)
            } catch (e: Exception) { Log.e(INV_TAG, "Parse: ${e.message}", e); InvResult(false, error = e.message) }
        }

    // ── 4. Buscar produtos ────────────────────────────────────────────────────
    // GET /produto/{empresa}/idfilial/{filial}[?descproduto=...]
    suspend fun fetchProdutos(idEmpresa: Int, idFilial: Int, descricao: String = ""): InvResult<List<ProdutoInventario>> =
        withContext(Dispatchers.IO) {
            val enc = if (descricao.isNotBlank()) "?descproduto=${java.net.URLEncoder.encode(descricao.trim(), "UTF-8")}" else ""
            val url = "$INV_BASE/produto/$idEmpresa/idfilial/$idFilial$enc"
            Log.i(INV_TAG, "🎯 fetchProdutos | q='$descricao'")
            val body = get(url) ?: return@withContext InvResult(false, error = "Erro de rede")
            return@withContext try {
                val arr = parseArray(body)
                val lista = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    ProdutoInventario(
                        idProduto    = o.optInt("ID_PRODUTO"),
                        descricao    = o.optString("DESCPRODUTO", ""),
                        estoque      = o.optDouble("ESTOQUE", 0.0),
                        abreviatura  = o.optString("ABREVIATURA", ""),
                        custoCompra  = o.optDouble("CUSTO_COMPRA", 0.0)
                    )
                }
                Log.i(INV_TAG, "✅ ${lista.size} produto(s)")
                InvResult(true, lista)
            } catch (e: Exception) { Log.e(INV_TAG, "Parse: ${e.message}", e); InvResult(false, error = e.message) }
        }

    // ── 5. Grupos ─────────────────────────────────────────────────────────────
    // GET /grupoproduto/{empresa}
    suspend fun fetchGrupos(idEmpresa: Int, filtro: String = ""): InvResult<List<GrupoInventario>> =
        withContext(Dispatchers.IO) {
            val url = "$INV_BASE/grupoproduto/$idEmpresa"
            Log.i(INV_TAG, "🎯 fetchGrupos | filtro='$filtro'")
            val body = get(url) ?: return@withContext InvResult(false, error = "Erro de rede")
            return@withContext try {
                val arr = parseArray(body)
                val lista = (0 until arr.length()).mapNotNull {
                    val o = arr.getJSONObject(it)
                    val desc = o.optString("DESCGRUPO", "")
                    if (filtro.isNotBlank() && !desc.contains(filtro, ignoreCase = true)) null
                    else GrupoInventario(o.optInt("ID_GRUPO"), desc)
                }
                Log.i(INV_TAG, "✅ ${lista.size} grupo(s)")
                InvResult(true, lista)
            } catch (e: Exception) { Log.e(INV_TAG, "Parse: ${e.message}", e); InvResult(false, error = e.message) }
        }

    // ── 6. Subgrupos ──────────────────────────────────────────────────────────
    // GET /subgrupoproduto/{empresa}/idsubgrupo/{idGrupo}
    suspend fun fetchSubgrupos(idEmpresa: Int, idGrupo: Int, filtro: String = ""): InvResult<List<SubgrupoInventario>> =
        withContext(Dispatchers.IO) {
            val url = "$INV_BASE/subgrupoproduto/$idEmpresa/idsubgrupo/$idGrupo"
            Log.i(INV_TAG, "🎯 fetchSubgrupos | grupo=$idGrupo filtro='$filtro'")
            val body = get(url) ?: return@withContext InvResult(false, error = "Erro de rede")
            return@withContext try {
                val arr = parseArray(body)
                val lista = (0 until arr.length()).mapNotNull {
                    val o = arr.getJSONObject(it)
                    val desc = o.optString("DESCSUBGRUPO", "")
                    if (filtro.isNotBlank() && !desc.contains(filtro, ignoreCase = true)) null
                    else SubgrupoInventario(o.optInt("ID_SUBGRUPO"), desc, o.optInt("ID_GRUPO"))
                }
                Log.i(INV_TAG, "✅ ${lista.size} subgrupo(s)")
                InvResult(true, lista)
            } catch (e: Exception) { Log.e(INV_TAG, "Parse: ${e.message}", e); InvResult(false, error = e.message) }
        }

    // ── 7. Marcas ─────────────────────────────────────────────────────────────
    // GET /marcaproduto/{empresa}
    suspend fun fetchMarcas(idEmpresa: Int, filtro: String = ""): InvResult<List<MarcaInventario>> =
        withContext(Dispatchers.IO) {
            val url = "$INV_BASE/marcaproduto/$idEmpresa"
            Log.i(INV_TAG, "🎯 fetchMarcas | filtro='$filtro'")
            val body = get(url) ?: return@withContext InvResult(false, error = "Erro de rede")
            return@withContext try {
                val arr = parseArray(body)
                val lista = (0 until arr.length()).mapNotNull {
                    val o = arr.getJSONObject(it)
                    val desc = o.optString("DESCMARCA", "")
                    if (filtro.isNotBlank() && !desc.contains(filtro, ignoreCase = true)) null
                    else MarcaInventario(o.optInt("ID_MARCA"), desc)
                }
                Log.i(INV_TAG, "✅ ${lista.size} marca(s)")
                InvResult(true, lista)
            } catch (e: Exception) { Log.e(INV_TAG, "Parse: ${e.message}", e); InvResult(false, error = e.message) }
        }

    // ── 8. Buscar produtos do inventário (GET) ────────────────────────────────
    // GET /prodinventario/{empresa}/idfilial/{filial}/idalmoxarifado/{alm}[?idproduto=...&IdGrupo=...&IdSubGrupo=...&IdMarca=...]
    suspend fun buscarInventario(
        idEmpresa: Int, idFilial: Int, idAlmoxarifado: Int,
        idProduto: Int? = null, idGrupo: Int? = null,
        idSubgrupo: Int? = null, idMarca: Int? = null
    ): InvResult<List<ProdutoInventario>> = withContext(Dispatchers.IO) {
        val params = mutableListOf<String>()
        idProduto?.let  { params += "idproduto=$it" }
        idGrupo?.let    { params += "IdGrupo=$it" }
        idSubgrupo?.let { params += "IdSubGrupo=$it" }
        idMarca?.let    { params += "IdMarca=$it" }
        val qs  = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        val url = "$INV_BASE/prodinventario/$idEmpresa/idfilial/$idFilial/idalmoxarifado/$idAlmoxarifado$qs"
        Log.i(INV_TAG, "🎯 buscarInventario")
        Log.i(INV_TAG, "URL: $url")
        val body = get(url) ?: return@withContext InvResult(false, error = "Erro de rede")
        return@withContext try {
            val arr = parseArray(body)
            val lista = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ProdutoInventario(
                    idProduto   = o.optInt("ID_PRODUTO"),
                    descricao   = o.optString("DESCPRODUTO", ""),
                    estoque     = o.optDouble("ESTOQUE", 0.0),
                    abreviatura = o.optString("ABREVIATURA", ""),
                    custoCompra = o.optDouble("CUSTO_COMPRA", 0.0)
                )
            }
            Log.i(INV_TAG, "✅ ${lista.size} resultado(s)")
            InvResult(true, lista)
        } catch (e: Exception) { Log.e(INV_TAG, "Parse: ${e.message}", e); InvResult(false, error = e.message) }
    }

    // ── 9. Salvar inventário (POST) ───────────────────────────────────────────
    // POST /prodinventario/{empresa}/idfilial/{filial}/idalmoxarifado/{alm}
    // Body: { inventario: [{ ID_PRODUTO: N }, ...] }
    suspend fun salvarInventario(
        idEmpresa: Int, idFilial: Int, idAlmoxarifado: Int,
        idsProdutos: List<Int>
    ): InvResult<String?> = withContext(Dispatchers.IO) {
        val url = "$INV_BASE/prodinventario/$idEmpresa/idfilial/$idFilial/idalmoxarifado/$idAlmoxarifado"
        val arr = JSONArray().apply { idsProdutos.forEach { id -> put(JSONObject().put("ID_PRODUTO", id)) } }
        val payload = JSONObject().put("inventario", arr).toString()
        Log.i(INV_TAG, "🎯 salvarInventario | empresa=$idEmpresa filial=$idFilial alm=$idAlmoxarifado prods=${idsProdutos.size}")
        Log.d(INV_TAG, "Payload: ${payload.take(300)}")
        val (status, resp) = post(url, payload)
        if (status in 200..299) {
            // Tenta extrair código do inventário
            var codigo: String? = null
            try {
                val json = JSONObject(resp ?: "")
                codigo = json.optString("codigo").ifEmpty { null }
                    ?: json.optString("id").ifEmpty { null }
                    ?: json.optString("ID_INVENTARIO").ifEmpty { null }
            } catch (_: Exception) {
                if (resp?.contains("201 Created") == true) codigo = null
            }
            Log.i(INV_TAG, "✅ Inventário salvo | código=${codigo ?: "N/A"}")
            InvResult(true, codigo)
        } else {
            Log.e(INV_TAG, "❌ Erro ao salvar: status=$status resp=${resp?.take(200)}")
            InvResult(false, error = "Erro $status ao salvar inventário")
        }
    }
}

