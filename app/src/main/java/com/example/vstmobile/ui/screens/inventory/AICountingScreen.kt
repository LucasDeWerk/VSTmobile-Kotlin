package com.example.vstmobile.ui.screens.inventory

import android.Manifest
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.example.vstmobile.services.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

// ── Paleta ────────────────────────────────────────────────────────────────────
private val AiNavy  = Color(0xFF1E3A8A)
private val AiBlue  = Color(0xFF3B82F6)
private val AiSlate = Color(0xFF64748B)
private val AiLight = Color(0xFFF1F5F9)
private val AiDark  = Color(0xFF1E293B)
private val AiGreen = Color(0xFF059669)
private val AiRed   = Color(0xFFEF4444)
private val AiAmber = Color(0xFFF59E0B)

// ── Modelo de anotação ─────────────────────────────────────────────────────────
data class Annotation(val id: Long, val x: Float, val y: Float, val type: String) // "add" | "remove"

// ── Estados da tela ───────────────────────────────────────────────────────────
enum class AIScreen { INITIAL, CAMERA, PROCESSING, RESULT }

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AICountingScreen(
    navController: NavHostController,
    idEmpresa: Int,
    idFilial: Int,
    idInventario: Int,
    idProduto: Int,
    descProduto: String,
    idAlmoxarifado: Int,
    qtdEstoque: Double,
    format: String          // "redondo" | "quadrado" | "barra"
) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val session       = remember { SessionManager(context).getSession() }
    val scope         = rememberCoroutineScope()
    val svc           = remember { ScanNearService() }
    val cntSvc        = remember(session.userToken) { CountingService(session.userToken) }

    // ── Permissão câmera ────────────────────────────────────���─────────────────
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // ── Estado da tela ────────────────────────────────────────────────────────
    var screen          by remember { mutableStateOf(AIScreen.INITIAL) }
    var capturedFile    by remember { mutableStateOf<File?>(null) }
    var processedBmp    by remember { mutableStateOf<ImageBitmap?>(null) }
    var scanResult      by remember { mutableStateOf<ScanNearResult?>(null) }
    var errorMsg        by remember { mutableStateOf<String?>(null) }
    var qtdFinal        by remember { mutableStateOf("") }
    var annotations     by remember { mutableStateOf<List<Annotation>>(emptyList()) }
    var annotationMode  by remember { mutableStateOf("add") }   // "add" | "remove"
    var showFullImg     by remember { mutableStateOf(false) }
    var salvando        by remember { mutableStateOf(false) }
    var lensFacing      by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

    // ── CameraX ───────────────────────────────────────────────────────────────
    val imageCapture = remember { ImageCapture.Builder().build() }

    fun calculateFinal(): Int {
        val ai      = scanResult?.totalObjects ?: 0
        val adds    = annotations.count { it.type == "add" }
        val removes = annotations.count { it.type == "remove" }
        return maxOf(0, ai + adds - removes)
    }

    fun resetAll() {
        screen       = AIScreen.INITIAL
        capturedFile = null
        processedBmp = null
        scanResult   = null
        errorMsg     = null
        qtdFinal     = ""
        annotations  = emptyList()
        annotationMode = "add"
        showFullImg  = false
    }

    // ── Capturar foto ─────────────────────────────────────────────────────────
    fun takePicture() {
        val outputDir  = context.cacheDir
        val photoFile  = File(outputDir, "ai_inv_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())}.jpg")
        val outputOpts = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOpts, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedFile = photoFile
                    screen = AIScreen.PROCESSING
                    scope.launch {
                        val r = svc.countObjects(photoFile.absolutePath, format)
                        if (r.isSuccess) {
                            val res = r.getOrNull()!!
                            scanResult = res
                            // Decodificar imagem processada se existir
                            processedBmp = res.processedImageBase64?.let { b64 ->
                                try {
                                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                } catch (_: Exception) { null }
                            }
                            qtdFinal = res.totalObjects.toString()
                            screen   = AIScreen.RESULT
                        } else {
                            errorMsg = r.exceptionOrNull()?.message ?: "Erro desconhecido"
                            screen   = AIScreen.INITIAL
                            Toast.makeText(context, "Erro: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    screen = AIScreen.INITIAL
                    Toast.makeText(context, "Erro ao capturar foto: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // ── Salvar resultado ──────────────────────────────────────────────────────
    fun salvar() {
        val qtd = qtdFinal.toDoubleOrNull()
        if (qtd == null || qtd < 0) {
            Toast.makeText(context, "Digite uma quantidade válida", Toast.LENGTH_SHORT).show(); return
        }
        scope.launch {
            salvando = true
            val r = cntSvc.salvarContagem(
                idEmpresa      = idEmpresa,
                idFilial       = idFilial,
                idInventario   = idInventario,
                idProduto      = idProduto,
                idAlmoxarifado = idAlmoxarifado,
                qtdContada     = qtd,
                qtdEstoque     = qtdEstoque
            )
            salvando = false
            if (r.success) {
                Toast.makeText(context, "✅ Contagem salva!\n$descProduto → ${qtd.toInt()} unidade(s)", Toast.LENGTH_LONG).show()
                navController.popBackStack()
            } else {
                Toast.makeText(context, "❌ Erro ao salvar: ${r.error}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDER CÂMERA
    // ══════════════════════════════════════════════════════════════════════════
    if (screen == AIScreen.CAMERA) {
        if (!cameraPermission.status.isGranted) {
            Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", fontSize = 64.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Sem acesso à câmera", style = TextStyle(fontSize = 16.sp, color = Color.White))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { cameraPermission.launchPermissionRequest() }) { Text("Conceder permissão") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { screen = AIScreen.INITIAL },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Text("Voltar")
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                // Preview câmera
                val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
                AndroidView(
                    factory = { ctx ->
                        val pv = PreviewView(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = CameraPreview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                            } catch (_: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))
                        pv
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Header câmera
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp).align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { screen = AIScreen.INITIAL }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                    Text(
                        "Fotografar ${svc.formatDisplayName(format)}",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    IconButton(onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    }) {
                        Text("🔄", fontSize = 22.sp)
                    }
                }

                // Guias visuais (crosshair)
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Box(Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.5f)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)))
                }

                // Controles câmera
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(bottom = 32.dp).align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Posicione os objetos dentro da área",
                        style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)))
                    Spacer(Modifier.height(16.dp))
                    // Botão captura
                    Box(
                        modifier = Modifier.size(72.dp).clip(CircleShape)
                            .background(Color.White).clickable { takePicture() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(AiNavy))
                    }
                }
            }
        }
        return
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDER PROCESSANDO
    // ════════════════════════════════════════════���═════════════════════════════
    if (screen == AIScreen.PROCESSING) {
        Box(Modifier.fillMaxSize().background(Color(0xEE000000)), Alignment.Center) {
            Card(Modifier.padding(32.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = AiBlue, strokeWidth = 3.dp, modifier = Modifier.size(56.dp))
                    Text("Analisando Imagem",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AiNavy))
                    Text("Nossa IA está contando os objetos na foto...",
                        style = TextStyle(fontSize = 14.sp, color = AiSlate), textAlign = TextAlign.Center)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Processando imagem", "Detectando objetos", "Contando elementos").forEach { step ->
                            Text("• $step", style = TextStyle(fontSize = 13.sp, color = AiSlate))
                        }
                    }
                }
            }
        }
        return
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDER RESULTADO + INICIAL
    // ══════════════════════════════════════════════════════════════════════════
    Box(Modifier.fillMaxSize().background(AiLight)) {
        Column(Modifier.fillMaxSize()) {

            // TopBar
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White).statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (screen == AIScreen.RESULT) resetAll()
                    else navController.popBackStack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AiNavy)
                }
                Spacer(Modifier.width(4.dp))
                Text("Contagem por IA",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AiNavy))
            }
            HorizontalDivider(color = Color(0xFFE5E7EB))

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp).padding(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── Card info produto ─────────────────────────────────────────
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Produto Selecionado",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AiNavy))
                        AiInfoRow("ID",       "#$idProduto")
                        AiInfoRow("Nome",     descProduto.ifEmpty { "Sem descrição" })
                        AiInfoRow("Formato",  svc.formatDisplayName(format))
                    }
                }

                if (screen == AIScreen.INITIAL) {
                    // ── Card instruções ───────────────────────────────────────
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🤖", fontSize = 20.sp)
                                Spacer(Modifier.width(8.dp))
                                Text("Como Funciona a IA",
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AiNavy))
                            }
                            listOf(
                                "1" to "Tire uma foto clara dos objetos que deseja contar",
                                "2" to "Nossa IA irá detectar e contar automaticamente",
                                "3" to "Revise o resultado e ajuste se necessário",
                                "4" to "Salve a contagem no seu inventário"
                            ).forEach { (n, text) ->
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(22.dp).clip(CircleShape).background(AiNavy), Alignment.Center) {
                                        Text(n, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White))
                                    }
                                    Text(text, style = TextStyle(fontSize = 13.sp, color = AiDark))
                                }
                            }
                        }
                    }

                    // ── Card dicas ─────────────────────────────────────────────
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        elevation = CardDefaults.cardElevation(1.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💡", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text("Evite erros, siga as instruções abaixo.",
                                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AiAmber))
                            }
                            listOf(
                                "Certifique-se de que há boa iluminação",
                                "Mantenha a câmera estável",
                                "Objetos devem estar bem visíveis",
                                "Evite sombras e reflexos",
                                "Fotografe os itens de frente"
                            ).forEach { tip ->
                                Text("• $tip", style = TextStyle(fontSize = 13.sp, color = AiDark))
                            }
                        }
                    }
                }

                if (screen == AIScreen.RESULT && scanResult != null) {
                    // ── Card resultado da IA ──────────────────────────────────
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🤖", fontSize = 18.sp)
                                Spacer(Modifier.width(6.dp))
                                Text("Resultado da IA",
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AiNavy))
                            }
                            // Número grande
                            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${scanResult!!.totalObjects}",
                                        style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = AiNavy))
                                    Text("objetos detectados",
                                        style = TextStyle(fontSize = 14.sp, color = AiSlate))
                                }
                            }
                            // Confiança média
                            if (scanResult!!.detectionsDetail.isNotEmpty()) {
                                val avgConf = scanResult!!.detectionsDetail.map { it.confidence }.average()
                                Text("Confiança média: ${"%.0f".format(avgConf * 100)}%",
                                    style = TextStyle(fontSize = 12.sp, color = AiSlate),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    // ── Imagem processada (preview) ───────────────────────────
                    val imgBmp = processedBmp
                    if (imgBmp != null) {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Imagem Analisada",
                                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AiNavy))
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(200.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { showFullImg = true }
                                ) {
                                    androidx.compose.foundation.Image(
                                        bitmap      = imgBmp,
                                        contentDescription = "Imagem processada",
                                        contentScale = ContentScale.Fit,
                                        modifier    = Modifier.fillMaxSize()
                                    )
                                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)), Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(28.dp))
                                            Text("Toque para ampliar",
                                                style = TextStyle(fontSize = 12.sp, color = Color.White))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Anotações ─────────────────────────────────────────────
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Ajustes Manuais",
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AiNavy))

                            // Modo de anotação
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { annotationMode = "add" },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (annotationMode == "add") AiGreen else Color(0xFFE5E7EB),
                                        contentColor   = if (annotationMode == "add") Color.White else AiSlate
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Adicionar (+)", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                                }
                                Button(
                                    onClick = { annotationMode = "remove" },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (annotationMode == "remove") AiRed else Color(0xFFE5E7EB),
                                        contentColor   = if (annotationMode == "remove") Color.White else AiSlate
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("−", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remover (-)", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                                }
                            }

                            // Botões +/- rápidos
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                // Botão remover rápido
                                OutlinedButton(
                                    onClick = {
                                        val a = Annotation(System.currentTimeMillis(), 0f, 0f, "remove")
                                        annotations = annotations + a
                                        qtdFinal = calculateFinal().toString()
                                    },
                                    modifier = Modifier.size(48.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AiRed)
                                ) {
                                    Text("−", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AiRed))
                                }
                                // Campo quantidade final
                                OutlinedTextField(
                                    value         = qtdFinal,
                                    onValueChange = { qtdFinal = it.filter { c -> c.isDigit() } },
                                    modifier      = Modifier.weight(1f),
                                    label         = { Text("Quantidade Final", style = TextStyle(fontSize = 12.sp)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine    = true,
                                    shape         = RoundedCornerShape(8.dp),
                                    colors        = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor   = AiBlue,
                                        unfocusedBorderColor = Color(0xFFD1D5DB)
                                    ),
                                    textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                        color = AiNavy, textAlign = TextAlign.Center)
                                )
                                // Botão adicionar rápido
                                OutlinedButton(
                                    onClick = {
                                        val a = Annotation(System.currentTimeMillis(), 0f, 0f, "add")
                                        annotations = annotations + a
                                        qtdFinal = calculateFinal().toString()
                                    },
                                    modifier = Modifier.size(48.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AiGreen)
                                ) {
                                    Icon(Icons.Default.Add, null, tint = AiGreen, modifier = Modifier.size(20.dp))
                                }
                            }

                            // Resumo das anotações
                            if (annotations.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    val adds = annotations.count { it.type == "add" }
                                    val rems = annotations.count { it.type == "remove" }
                                    Text("+$adds adicionados",
                                        style = TextStyle(fontSize = 12.sp, color = AiGreen, fontWeight = FontWeight.Bold))
                                    Text("-$rems removidos",
                                        style = TextStyle(fontSize = 12.sp, color = AiRed, fontWeight = FontWeight.Bold))
                                    TextButton(onClick = { annotations = emptyList(); qtdFinal = (scanResult?.totalObjects ?: 0).toString() }) {
                                        Text("Limpar ajustes", style = TextStyle(fontSize = 12.sp, color = AiSlate))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Botão fixo na base ────────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(AiLight).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (screen == AIScreen.INITIAL) {
                Button(
                    onClick  = {
                        if (cameraPermission.status.isGranted) {
                            screen = AIScreen.CAMERA
                        } else {
                            cameraPermission.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AiNavy)
                ) {
                    Text("📷", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar Contagem por IA",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White))
                }
            } else if (screen == AIScreen.RESULT) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick  = { resetAll() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = AiRed),
                        border   = androidx.compose.foundation.BorderStroke(1.5.dp, AiRed)
                    ) {
                        Icon(Icons.Default.Close, null, tint = AiRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancelar", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AiRed))
                    }
                    Button(
                        onClick  = { salvar() },
                        enabled  = !salvando,
                        modifier = Modifier.weight(2f).height(52.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = AiNavy)
                    ) {
                        if (salvando) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Salvando...", style = TextStyle(fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold))
                        } else {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Salvar Contagem", style = TextStyle(fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // ── Modal imagem em tela cheia ────────────────────────────────────────
        if (showFullImg && processedBmp != null) {
            Dialog(onDismissRequest = { showFullImg = false }) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
                    androidx.compose.foundation.Image(
                        bitmap           = processedBmp!!,
                        contentDescription = "Imagem processada ampliada",
                        contentScale     = ContentScale.Fit,
                        modifier         = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick  = { showFullImg = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                            Alignment.Center) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────
@Composable
private fun AiInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TextStyle(fontSize = 12.sp, color = AiSlate))
        Text(value, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AiDark),
            modifier = Modifier.weight(1f), textAlign = TextAlign.End,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@ComposePreview(showBackground = true, showSystemUi = true)
@Composable
fun AICountingScreenPreview() {
    AICountingScreen(
        navController  = rememberNavController(),
        idEmpresa      = 1,
        idFilial       = 1,
        idInventario   = 123,
        idProduto      = 456,
        descProduto    = "Tubo Redondo 50mm",
        idAlmoxarifado = 1,
        qtdEstoque     = 100.0,
        format         = "redondo"
    )
}
