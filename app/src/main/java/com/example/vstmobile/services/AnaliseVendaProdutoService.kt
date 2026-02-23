package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val AVP_TAG = "VST_ANALISE_PRODUTO"
private const val AVP_BASE = "https://compras.vstsolution.com"

// ── Data classes ──────────────────────────────────────────────────────────────

data class GrupoProduto(val id: Int, val descGrupo: String)
data class SubGrupoProduto(val id: Int, val descSubGrupo: String, val idGrupo: Int)
data class MarcaProduto(val id: Int, val descMarca: String)
data class ClasseProduto(val id: Int, val descClasse: String)
data class Vendedor(val id: Int, val nomeFuncionario: String)

data class AvpResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)

// ── Service ───────────────────────────────────────────────────────────────────

class AnaliseVendaProdutoService(private val userToken: String) {

    private fun get(url: String): String? {
        return try {
            Log.i(AVP_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(AVP_TAG, "➡️  GET $url")
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
                Log.i(AVP_TAG, "✅ $status (${duration}ms) | ${body.length} chars")
                Log.d(AVP_TAG, "📦 Body: ${body.take(500)}")
                body
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
                Log.e(AVP_TAG, "❌ HTTP $status (${duration}ms) | $err")
                null
            }
        } catch (e: Exception) {
            Log.e(AVP_TAG, "💥 Exceção: ${e.message}", e)
            null
        }
    }

    private fun getBytes(url: String): ByteArray? {
        return try {
            Log.i(AVP_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(AVP_TAG, "➡️  GET (bytes) $url")
            val start = System.currentTimeMillis()
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $userToken")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            val status = conn.responseCode
            val duration = System.currentTimeMillis() - start
            if (status == 200) {
                val bytes = conn.inputStream.readBytes()
                Log.i(AVP_TAG, "✅ $status (${duration}ms) | ${bytes.size} bytes")
                bytes
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "sem body"
                Log.e(AVP_TAG, "❌ HTTP $status (${duration}ms) | $err")
                null
            }
        } catch (e: Exception) {
            Log.e(AVP_TAG, "💥 Exceção bytes: ${e.message}", e)
            null
        }
    }

    suspend fun fetchGruposProduto(idEmpresa: String): AvpResult<List<GrupoProduto>> = withContext(Dispatchers.IO) {
        val url = "$AVP_BASE/grupoproduto/$idEmpresa"
        Log.i(AVP_TAG, "🎯 fetchGruposProduto | empresa=$idEmpresa")
        val body = get(url) ?: return@withContext AvpResult(false, error = "Erro de rede")
        return@withContext try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val lista = (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                GrupoProduto(obj.optInt("ID_GRUPO"), obj.optString("DESCGRUPO", ""))
            }
            Log.i(AVP_TAG, "✅ Grupos: ${lista.size}")
            AvpResult(true, lista)
        } catch (e: Exception) {
            Log.e(AVP_TAG, "💥 Parse grupos: ${e.message}", e)
            AvpResult(false, error = e.message)
        }
    }

    suspend fun fetchSubGruposProduto(idEmpresa: String, idGrupo: Int): AvpResult<List<SubGrupoProduto>> = withContext(Dispatchers.IO) {
        val url = "$AVP_BASE/subgrupoproduto/$idEmpresa/idsubgrupo/$idGrupo"
        Log.i(AVP_TAG, "🎯 fetchSubGrupos | empresa=$idEmpresa grupo=$idGrupo")
        val body = get(url) ?: return@withContext AvpResult(false, error = "Erro de rede")
        return@withContext try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val lista = (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                SubGrupoProduto(obj.optInt("ID_SUBGRUPO"), obj.optString("DESCSUBGRUPO", ""), obj.optInt("ID_GRUPO"))
            }
            Log.i(AVP_TAG, "✅ SubGrupos: ${lista.size}")
            AvpResult(true, lista)
        } catch (e: Exception) {
            Log.e(AVP_TAG, "💥 Parse sub-grupos: ${e.message}", e)
            AvpResult(false, error = e.message)
        }
    }

    suspend fun fetchMarcas(idEmpresa: String): AvpResult<List<MarcaProduto>> = withContext(Dispatchers.IO) {
        val url = "$AVP_BASE/marcaproduto/$idEmpresa"
        Log.i(AVP_TAG, "🎯 fetchMarcas | empresa=$idEmpresa")
        val body = get(url) ?: return@withContext AvpResult(false, error = "Erro de rede")
        return@withContext try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val lista = (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                MarcaProduto(obj.optInt("ID_MARCA"), obj.optString("DESCMARCA", ""))
            }
            Log.i(AVP_TAG, "✅ Marcas: ${lista.size}")
            AvpResult(true, lista)
        } catch (e: Exception) {
            Log.e(AVP_TAG, "💥 Parse marcas: ${e.message}", e)
            AvpResult(false, error = e.message)
        }
    }

    suspend fun fetchClasses(idEmpresa: String): AvpResult<List<ClasseProduto>> = withContext(Dispatchers.IO) {
        val url = "$AVP_BASE/classeproduto/$idEmpresa"
        Log.i(AVP_TAG, "🎯 fetchClasses | empresa=$idEmpresa")
        val body = get(url) ?: return@withContext AvpResult(false, error = "Erro de rede")
        return@withContext try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val lista = (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                ClasseProduto(obj.optInt("ID_CLASSE"), obj.optString("DESCCLASSE", ""))
            }
            Log.i(AVP_TAG, "✅ Classes: ${lista.size}")
            AvpResult(true, lista)
        } catch (e: Exception) {
            Log.e(AVP_TAG, "💥 Parse classes: ${e.message}", e)
            AvpResult(false, error = e.message)
        }
    }

    suspend fun fetchVendedores(idEmpresa: String): AvpResult<List<Vendedor>> = withContext(Dispatchers.IO) {
        val url = "$AVP_BASE/vendedores/$idEmpresa"
        Log.i(AVP_TAG, "🎯 fetchVendedores | empresa=$idEmpresa")
        val body = get(url) ?: return@withContext AvpResult(false, error = "Erro de rede")
        return@withContext try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val lista = (0 until arr.length()).mapNotNull {
                val obj = arr.getJSONObject(it)
                val id = obj.optInt("ID_USUARIO", 0)
                if (id == 0) null
                else Vendedor(id, obj.optString("NOMEFUNC", "Vendedor $id"))
            }
            Log.i(AVP_TAG, "✅ Vendedores: ${lista.size}")
            AvpResult(true, lista)
        } catch (e: Exception) {
            Log.e(AVP_TAG, "💥 Parse vendedores: ${e.message}", e)
            AvpResult(false, error = e.message)
        }
    }

    /**
     * Gera o relatório PDF e retorna os bytes.
     * URL: /analisevendaproduto/{idEmpresa}/idfilial/{idFilial}/dtini/{dtini}/dtfim/{dtfim}?filiais=...&custo=...
     */
    suspend fun fetchRelatorioPdf(
        idEmpresa: String,
        idFilial: String,
        dtIni: String,
        dtFim: String,
        filiaisIds: String,      // ex: "1,2,3"
        grupo: String = "",
        subGrupo: String = "",
        marca: String = "",
        classe: String = "",
        vendedor: String = "",
        custo: String = "",      // "0"=Aquisicao "1"=Medio "2"=Compra
        venTipo: String = "",    // "1"=Produto "2"=Servico "0"=Ambos
        venSituacao: String = "",// "0"=Todas "1"=Abertas "2"=Fechadas
        mostraPeso: String = ""  // "S" ou "N"
    ): AvpResult<ByteArray> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("$AVP_BASE/analisevendaproduto/$idEmpresa/idfilial/$idFilial")
        if (dtIni.isNotEmpty()) sb.append("/dtini/$dtIni")
        if (dtFim.isNotEmpty()) sb.append("/dtfim/$dtFim")
        sb.append("?filiais=$filiaisIds")
        if (grupo.isNotEmpty()) sb.append("&grupo=$grupo")
        if (subGrupo.isNotEmpty()) sb.append("&subgrupo=$subGrupo")
        if (marca.isNotEmpty()) sb.append("&marca=$marca")
        if (classe.isNotEmpty()) sb.append("&classe=$classe")
        if (vendedor.isNotEmpty()) sb.append("&vendedor=$vendedor")
        if (custo.isNotEmpty()) sb.append("&custo=$custo")
        if (venTipo.isNotEmpty()) sb.append("&ventipo=$venTipo")
        if (venSituacao.isNotEmpty()) sb.append("&vensituacao=$venSituacao")
        if (mostraPeso.isNotEmpty()) sb.append("&mostrapeso=$mostraPeso")

        val url = sb.toString()
        Log.i(AVP_TAG, "🎯 fetchRelatorioPdf")
        Log.i(AVP_TAG, "URL: $url")
        Log.i(AVP_TAG, "Params: empresa=$idEmpresa filial=$idFilial dtIni=$dtIni dtFim=$dtFim filiais=$filiaisIds grupo=$grupo subGrupo=$subGrupo marca=$marca classe=$classe vendedor=$vendedor custo=$custo tipo=$venTipo situacao=$venSituacao peso=$mostraPeso")

        val bytes = getBytes(url) ?: return@withContext AvpResult(false, error = "Erro de rede ao baixar PDF")
        if (bytes.isEmpty()) return@withContext AvpResult(false, error = "PDF vazio retornado pela API")

        // Verificar header PDF
        val header = String(bytes.take(4).toByteArray())
        if (header != "%PDF") {
            Log.w(AVP_TAG, "⚠️ Header PDF inesperado: $header (continuando mesmo assim)")
        }

        Log.i(AVP_TAG, "✅ PDF recebido: ${bytes.size} bytes")
        AvpResult(true, bytes)
    }
}

