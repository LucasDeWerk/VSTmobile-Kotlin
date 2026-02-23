package com.example.vstmobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.vstmobile.R
import com.example.vstmobile.navigation.Screen
import android.util.Log
import com.example.vstmobile.services.AuthService
import com.example.vstmobile.services.DeviceService
import com.example.vstmobile.services.SessionManager
import com.example.vstmobile.ui.theme.BorderGray
import com.example.vstmobile.ui.theme.DarkGray
import com.example.vstmobile.ui.theme.ErrorRed
import com.example.vstmobile.ui.theme.LightBlue
import com.example.vstmobile.ui.theme.LightGray
import com.example.vstmobile.ui.theme.PrimaryBlue
import com.example.vstmobile.ui.theme.SuccessGreen
import com.example.vstmobile.ui.theme.White
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val companyCodeInput = remember { mutableStateOf("") }
    val userCodeInput = remember { mutableStateOf("") }
    val passwordInput = remember { mutableStateOf("") }
    val showPassword = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }
    val deviceStatus = remember { mutableStateOf<String?>(null) }
    val userData = remember { mutableStateOf<String?>(null) }
    val saveLogin = remember { mutableStateOf(false) }
    val isAutoLogging = remember { mutableStateOf(false) }

    // Verificar sessão ativa, credenciais salvas e status do dispositivo
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val sessionManager = SessionManager(context)

            // Limpar sessões antigas sem saveLogin
            sessionManager.init()

            // 1. Verificar status do dispositivo
            val deviceService = DeviceService(context)
            deviceStatus.value = "checking"
            try {
                val result = deviceService.checkCurrentDevice()
                if (result.success) {
                    deviceStatus.value = if (result.isRegistered) "registered" else "not-registered"
                } else {
                    deviceStatus.value = if (result.error?.contains("não autorizado") == true)
                        "pending-authorization" else "not-registered"
                }
            } catch (_: Exception) {
                deviceStatus.value = "not-registered"
            }

            // 2. Se dispositivo ativo e tem credenciais salvas → auto-login (sempre faz os 3 passos de API)
            if (deviceStatus.value == "registered" && sessionManager.hasSavedCredentials()) {
                val saved = sessionManager.getSavedCredentials()!!
                Log.i("VST_LOGIN", "🔑 Credenciais salvas encontradas, fazendo auto-login com autenticação...")
                companyCodeInput.value = saved.companyCode
                userCodeInput.value = saved.userId
                passwordInput.value = saved.password
                saveLogin.value = true
                isAutoLogging.value = true
                isLoading.value = true

                try {
                    val authService = AuthService()

                    // PASSO 1: Verificar empresa (obtém token atualizado)
                    val companyResult = authService.verifyCompanyCode(saved.companyCode)
                    if (!companyResult.success) {
                        Log.w("VST_LOGIN", "Auto-login falhou no passo 1: ${companyResult.error}")
                        isLoading.value = false
                        isAutoLogging.value = false
                        return@launch
                    }

                    // PASSO 2: Validar usuário (com token atualizado)
                    val userResult = authService.validateUser(
                        userCode = saved.userId,
                        companyCode = saved.companyCode,
                        companyToken = companyResult.token
                    )
                    if (!userResult.success) {
                        Log.w("VST_LOGIN", "Auto-login falhou no passo 2: ${userResult.error}")
                        isLoading.value = false
                        isAutoLogging.value = false
                        return@launch
                    }

                    // PASSO 3: Login final com senha (obtém userToken atualizado)
                    val loginResult = authService.login(
                        companyCode = saved.companyCode,
                        userId = userResult.userId.ifEmpty { saved.userId },
                        password = saved.password,
                        userName = userResult.userName,
                        companyToken = companyResult.token
                    )
                    if (!loginResult.success) {
                        Log.w("VST_LOGIN", "Auto-login falhou no passo 3: ${loginResult.error}")
                        isLoading.value = false
                        isAutoLogging.value = false
                        return@launch
                    }

                    // Salvar sessão com tokens atualizados
                    sessionManager.saveSession(
                        userToken = loginResult.token,
                        companyToken = companyResult.token,
                        companyCode = saved.companyCode,
                        userId = userResult.userId.ifEmpty { saved.userId },
                        userName = userResult.userName
                    )

                    Log.i("VST_LOGIN", "✅ Auto-login bem-sucedido com tokens atualizados!")
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                } catch (e: Exception) {
                    Log.e("VST_LOGIN", "Erro no auto-login: ${e.message}")
                    isLoading.value = false
                    isAutoLogging.value = false
                }
            }
        }
    }

    // Auto-login overlay
    if (isAutoLogging.value) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBlue),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = PrimaryBlue)
                Text(
                    text = "Entrando automaticamente...",
                    style = TextStyle(fontSize = 14.sp, color = DarkGray)
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBlue)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Spacer para cobrir a status bar (aproximadamente 24dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(LightBlue)
        ) {
            // Espaço para status bar
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Logo Card
        Card(
            modifier = Modifier.size(120.dp, 80.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_vst),
                    contentDescription = "Logo VST Solution",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Subtitle
        Text(
            text = "Sistema de Gestão Empresarial",
            style = TextStyle(
                fontSize = 14.sp,
                color = LightGray
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Main Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "Acesso ao Sistema",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkGray
                    )
                )

                // Description
                Text(
                    text = "Digite suas credenciais para acessar o sistema",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = LightGray
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Device Status Messages
                when (deviceStatus.value) {
                    "checking" -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFF7ED), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFFF59E0B),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Verificando dispositivo...",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    color = Color(0xFFF59E0B)
                                )
                            )
                        }
                    }

                    "registered" -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF059669), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Dispositivo ativado",
                                tint = SuccessGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Dispositivo Ativado",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    "not-registered" -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, BorderGray, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Phone,
                                        contentDescription = "Dispositivo",
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Dispositivo não cadastrado",
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryBlue
                                        )
                                    )
                                }
                                Text(
                                    text = "Para acessar o sistema, você precisa cadastrar este aparelho primeiro.",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        color = Color(0xFF64748B)
                                    )
                                )
                                Button(
                                    onClick = { navController.navigate(Screen.RegisterDevice.route) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryBlue
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Email,
                                        contentDescription = "Cadastrar",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(end = 6.dp)
                                    )
                                    Text("Cadastrar")
                                }
                            }
                        }
                    }

                    "pending-authorization" -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, Color(0xFFF59E0B), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "Pendente",
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Aguardando ativação",
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF59E0B)
                                        )
                                    )
                                }
                                Text(
                                    text = "Seu aparelho foi cadastrado e está aguardando ativação pelo administrador.",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        color = Color(0xFF64748B)
                                    )
                                )
                                // Botão para re-verificar se foi ativado
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            deviceStatus.value = "checking"
                                            try {
                                                val deviceService = DeviceService(context)
                                                val result = deviceService.checkCurrentDevice()
                                                if (result.success && result.isRegistered) {
                                                    deviceStatus.value = "registered"
                                                } else if (result.error?.contains("não autorizado") == true) {
                                                    deviceStatus.value = "pending-authorization"
                                                } else {
                                                    deviceStatus.value = "not-registered"
                                                }
                                            } catch (_: Exception) {
                                                deviceStatus.value = "pending-authorization"
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF59E0B),
                                        contentColor = White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Verificar",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(end = 6.dp)
                                    )
                                    Text(
                                        text = "Verificar se foi ativado",
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Código da Empresa
                Text(
                    text = "Código da Empresa",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )

                OutlinedTextField(
                    value = companyCodeInput.value,
                    onValueChange = { companyCodeInput.value = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Email,
                            contentDescription = "Empresa",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    placeholder = { Text("Insira o código da empresa", fontSize = 12.sp) },
                    textStyle = TextStyle(color = DarkGray, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = BorderGray,
                        focusedTextColor = DarkGray,
                        unfocusedTextColor = DarkGray,
                        cursorColor = PrimaryBlue
                    ),
                    singleLine = true
                )

                // ID do Usuário
                Text(
                    text = "ID do Usuário",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )

                OutlinedTextField(
                    value = userCodeInput.value,
                    onValueChange = { userCodeInput.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.AccountBox,
                            contentDescription = "Usuário",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    placeholder = { Text("Insira seu ID de usuário", fontSize = 12.sp) },
                    textStyle = TextStyle(color = DarkGray, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = BorderGray,
                        focusedTextColor = DarkGray,
                        unfocusedTextColor = DarkGray,
                        cursorColor = PrimaryBlue
                    ),
                    singleLine = true
                )

                // Senha
                Text(
                    text = "Senha",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )

                OutlinedTextField(
                    value = passwordInput.value,
                    onValueChange = { passwordInput.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword.value) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Senha",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword.value = !showPassword.value },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (showPassword.value) Icons.Filled.Lock else Icons.Filled.Email,
                                contentDescription = "Toggle senha",
                                tint = LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    placeholder = { Text("Digite sua senha", fontSize = 12.sp) },
                    textStyle = TextStyle(color = DarkGray, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = BorderGray,
                        focusedTextColor = DarkGray,
                        unfocusedTextColor = DarkGray,
                        cursorColor = PrimaryBlue
                    ),
                    singleLine = true
                )

                // User Info (quando validado)
                if (userData.value != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
                            .border(1.dp, SuccessGreen, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Validado",
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Usuário: ${userData.value}",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = SuccessGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Checkbox Salvar Login
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Checkbox(
                        checked = saveLogin.value,
                        onCheckedChange = { saveLogin.value = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = PrimaryBlue,
                            uncheckedColor = LightGray
                        )
                    )
                    Text(
                        text = "Salvar login neste aparelho",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = DarkGray
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Login Button
                Button(
                    onClick = {
                        when {
                            deviceStatus.value != "registered" -> {
                                errorMessage.value = "Dispositivo não está ativado"
                            }
                            companyCodeInput.value.isEmpty() -> {
                                errorMessage.value = "Por favor, digite o código da empresa"
                            }
                            userCodeInput.value.isEmpty() -> {
                                errorMessage.value = "Por favor, digite o ID do usuário"
                            }
                            passwordInput.value.isEmpty() -> {
                                errorMessage.value = "Por favor, digite sua senha"
                            }
                            else -> {
                                isLoading.value = true
                                errorMessage.value = ""
                                coroutineScope.launch {
                                    try {
                                        val authService = AuthService()
                                        val sessionManager = SessionManager(context)

                                        // PASSO 1: Verificar código da empresa
                                        Log.i("VST_LOGIN", "Passo 1: Verificando empresa...")
                                        val companyResult = authService.verifyCompanyCode(
                                            companyCodeInput.value.trim()
                                        )
                                        if (!companyResult.success) {
                                            errorMessage.value = companyResult.error ?: "Código da empresa inválido"
                                            isLoading.value = false
                                            return@launch
                                        }
                                        val companyToken = companyResult.token

                                        // PASSO 2: Validar usuário
                                        Log.i("VST_LOGIN", "Passo 2: Validando usuário...")
                                        val userResult = authService.validateUser(
                                            userCode = userCodeInput.value.trim(),
                                            companyCode = companyCodeInput.value.trim(),
                                            companyToken = companyToken
                                        )
                                        if (!userResult.success) {
                                            errorMessage.value = userResult.error ?: "Usuário não encontrado"
                                            isLoading.value = false
                                            return@launch
                                        }
                                        userData.value = userResult.userName

                                        // PASSO 3: Login final com senha
                                        Log.i("VST_LOGIN", "Passo 3: Realizando login...")
                                        val loginResult = authService.login(
                                            companyCode = companyCodeInput.value.trim(),
                                            userId = userResult.userId.ifEmpty { userCodeInput.value.trim() },
                                            password = passwordInput.value.trim(),
                                            userName = userResult.userName,
                                            companyToken = companyToken
                                        )
                                        if (!loginResult.success) {
                                            errorMessage.value = loginResult.error ?: "Senha inválida"
                                            isLoading.value = false
                                            return@launch
                                        }

                                        // Salvar ou limpar credenciais conforme checkbox
                                        if (saveLogin.value) {
                                            sessionManager.saveCredentials(
                                                companyCode = companyCodeInput.value.trim(),
                                                userId = userCodeInput.value.trim(),
                                                password = passwordInput.value.trim()
                                            )
                                        } else {
                                            sessionManager.clearCredentials()
                                        }

                                        // Salvar sessão e navegar para Home
                                        Log.i("VST_LOGIN", "✅ Login bem-sucedido!")
                                        sessionManager.saveSession(
                                            userToken = loginResult.token,
                                            companyToken = companyToken,
                                            companyCode = companyCodeInput.value.trim(),
                                            userId = userResult.userId.ifEmpty { userCodeInput.value.trim() },
                                            userName = userResult.userName
                                        )
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(Screen.Login.route) { inclusive = true }
                                        }

                                    } catch (e: Exception) {
                                        Log.e("VST_LOGIN", "Erro inesperado: ${e.message}")
                                        errorMessage.value = "Erro inesperado: ${e.message}"
                                        isLoading.value = false
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isLoading.value
                ) {
                    if (isLoading.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Entrar no Sistema",
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Error Message
                if (errorMessage.value.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                            .border(1.dp, ErrorRed, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Erro",
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = errorMessage.value,
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = ErrorRed
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Copyright
        Text(
            text = "© 2026 VST Solution. Todos os direitos reservados.",
            style = TextStyle(
                fontSize = 11.sp,
                color = LightGray
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LoginScreen(rememberNavController())
}