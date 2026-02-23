package com.example.vstmobile.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val AI_TAG  = "VST_SCANNEAR"
private const val AI_BASE = "https://scanear.vstsolution.com"
private const val TIMEOUT_MS = 60_000

data class DetectionDetail(val confidence: Double, val center: List<Int>)

data class ScanNearResult(
    val totalObjects: Int,
    val uniqueObjects: Int,
    val processedImageBase64: String?,    // pode ser null
    val objectType: String,
    val detectionsDetail: List<DetectionDetail>
)

data class ScanNearError(val message: String)

class ScanNearService {

    // ── Health check ──────────────────────────────────────────────────────────
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.i(AI_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(AI_TAG, "🏥 GET $AI_BASE/health")
            val conn = URL("$AI_BASE/health").openConnection() as HttpsURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            val status = conn.responseCode
            if (status == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                Log.i(AI_TAG, "✅ Health OK: $body")
                val json = JSONObject(body)
                json.optString("status") == "ok"
            } else {
                Log.e(AI_TAG, "❌ Health status: $status")
                false
            }
        } catch (e: Exception) {
            Log.e(AI_TAG, "💥 Health check falhou: ${e.message}")
            false
        }
    }

    // ── Mapear formato para tipo de objeto ────────────────────────────────────
    fun formatToObjectType(format: String): String = when (format) {
        "redondo"  -> "tubo"
        "quadrado" -> "tubo_quadrado"
        "barra"    -> "barra"
        else       -> "tubo"
    }

    fun formatDisplayName(format: String): String = when (format) {
        "redondo"  -> "Tubo Redondo"
        "quadrado" -> "Tubo Quadrado"
        "barra"    -> "Barra de Metal"
        else       -> format
    }

    // ── Contar objetos via base64 ─────────────────────────────────────────────
    // POST /count-objects-base64
    // Body: { image: base64, object_type, filename }
    suspend fun countObjects(imageUri: String, format: String): Result<ScanNearResult> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val objectType = formatToObjectType(format)

                Log.i(AI_TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.i(AI_TAG, "🔍 countObjects | format=$format → objectType=$objectType")

                // Verificar health primeiro
                if (!checkHealth()) {
                    return@withContext Result.failure(
                        Exception("Backend ScanNear não está disponível em $AI_BASE")
                    )
                }

                // Ler arquivo e converter para base64
                Log.i(AI_TAG, "📸 Lendo imagem: $imageUri")
                val imageFile   = File(imageUri)
                val imageBytes  = imageFile.readBytes()
                val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                Log.i(AI_TAG, "💾 Imagem convertida | tamanho: ${imageBytes.size / 1024} KB | base64: ${base64Image.length / 1024} KB")

                // Montar payload
                val payload = JSONObject()
                    .put("image", base64Image)
                    .put("object_type", objectType)
                    .put("filename", "analysis_${System.currentTimeMillis()}.jpg")
                    .toString()

                val url = "$AI_BASE/count-objects-base64"
                Log.i(AI_TAG, "🚀 POST $url")
                Log.d(AI_TAG, "📋 object_type=$objectType")

                val conn = URL(url).openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout    = TIMEOUT_MS

                val start = System.currentTimeMillis()
                conn.outputStream.use { it.write(payload.toByteArray()) }

                val status = conn.responseCode
                val dur    = System.currentTimeMillis() - start
                Log.i(AI_TAG, "${if (status in 200..299) "✅" else "❌"} $status (${dur}ms)")

                if (status !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "erro desconhecido"
                    Log.e(AI_TAG, "Erro API: $err")
                    return@withContext Result.failure(mapHttpError(status, err))
                }

                val body = conn.inputStream.bufferedReader().readText()
                Log.d(AI_TAG, "📥 Resposta: ${body.take(300)}")

                val json = JSONObject(body)
                val totalObjects = json.optInt("total_objects", 0)
                val uniqueObjects = json.optInt("unique_objects", 0)
                val processedImg = json.optString("processed_image").ifEmpty { null }

                // Detalhes das detecções
                val detailsArr = json.optJSONArray("detections_detail") ?: JSONArray()
                val details = (0 until detailsArr.length()).map {
                    val d = detailsArr.getJSONObject(it)
                    val centerArr = d.optJSONArray("center")
                    val center = if (centerArr != null)
                        (0 until centerArr.length()).map { i -> centerArr.getInt(i) }
                    else emptyList()
                    DetectionDetail(d.optDouble("confidence", 0.0), center)
                }

                Log.i(AI_TAG, "✅ Análise concluída | total=$totalObjects unique=$uniqueObjects")
                details.forEachIndexed { i, d ->
                    Log.d(AI_TAG, "  ${i+1}. conf=${(d.confidence*100).toInt()}% centro=${d.center}")
                }

                Result.success(ScanNearResult(
                    totalObjects         = totalObjects,
                    uniqueObjects        = uniqueObjects,
                    processedImageBase64 = processedImg,
                    objectType           = objectType,
                    detectionsDetail     = details
                ))
            } catch (e: Exception) {
                Log.e(AI_TAG, "💥 countObjects: ${e.message}", e)
                Result.failure(e)
            }
        }

    private fun mapHttpError(status: Int, body: String): Exception = when (status) {
        400  -> Exception("Imagem inválida: verifique se está no formato correto (JPG/PNG).")
        422  -> Exception("Parâmetros inválidos: verifique o tipo de objeto selecionado.")
        500  -> Exception("Erro interno do servidor: o modelo de IA pode estar indisponível.")
        503  -> Exception("Serviço indisponível: backend sobrecarregado. Tente novamente.")
        else -> Exception("Erro $status: $body")
    }
}

