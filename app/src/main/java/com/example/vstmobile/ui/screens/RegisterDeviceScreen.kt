package com.example.vstmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.vstmobile.R
import com.example.vstmobile.ui.theme.PrimaryBlue
import com.example.vstmobile.ui.theme.LightBlue
import com.example.vstmobile.ui.theme.White
import com.example.vstmobile.ui.theme.DarkGray
import com.example.vstmobile.ui.theme.LightGray
import com.example.vstmobile.ui.theme.BorderGray
import com.example.vstmobile.ui.theme.SuccessGreen
import com.example.vstmobile.ui.theme.ErrorRed
import androidx.compose.foundation.Image
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.vstmobile.navigation.Screen
import com.example.vstmobile.services.DeviceService
import kotlinx.coroutines.launch

data class Country(
    val code: String,
    val flag: String,
    val name: String,
    val dialCode: String,
    val mask: String
)

val COUNTRIES = listOf(
    Country("BR", "🇧🇷", "Brasil", "+55", "(##) #####-####"),
    Country("US", "🇺🇸", "Estados Unidos", "+1", "(###) ###-####"),
    Country("AR", "🇦🇷", "Argentina", "+54", "## ####-####"),
    Country("CL", "🇨🇱", "Chile", "+56", "# ####-####"),
    Country("CO", "🇨🇴", "Colômbia", "+57", "### ###-####"),
    Country("MX", "🇲🇽", "México", "+52", "## ####-####"),
    Country("PE", "🇵🇪", "Peru", "+51", "### ###-###"),
    Country("UY", "🇺🇾", "Uruguai", "+598", "#### ####"),
    Country("PY", "🇵🇾", "Paraguai", "+595", "### ###-###"),
    Country("BO", "🇧🇴", "Bolívia", "+591", "#### ####"),
    Country("EC", "🇪🇨", "Equador", "+593", "## ###-####"),
    Country("VE", "🇻🇪", "Venezuela", "+58", "###-###-####"),
    Country("PT", "🇵🇹", "Portugal", "+351", "### ### ###"),
    Country("ES", "🇪🇸", "Espanha", "+34", "### ### ###"),
    Country("IT", "🇮🇹", "Itália", "+39", "### ### ####"),
    Country("FR", "🇫🇷", "França", "+33", "# ## ## ## ##"),
    Country("DE", "🇩🇪", "Alemanha", "+49", "#### #######"),
    Country("GB", "🇬🇧", "Reino Unido", "+44", "#### ### ####"),
    Country("CA", "🇨🇦", "Canadá", "+1", "(###) ###-####"),
    Country("JP", "🇯🇵", "Japão", "+81", "## ####-####"),
)

@Composable
fun RegisterDeviceScreen(navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val companyCode = remember { mutableStateOf("") }
    val deviceName = remember { mutableStateOf("") }
    val phoneNumber = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf("") }
    val selectedCountry = remember { mutableStateOf(COUNTRIES[0]) }
    val showCountryModal = remember { mutableStateOf(false) }

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
            text = "Cadastro de Aparelho",
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
                    text = "Registrar Novo Aparelho",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkGray
                    )
                )

                // Description
                Text(
                    text = "Cadastre este aparelho para poder acessar o sistema",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = LightGray
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

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
                    value = companyCode.value,
                    onValueChange = { companyCode.value = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.AccountBox,
                            contentDescription = "Empresa",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    placeholder = { Text("Digite o código da empresa", fontSize = 12.sp) },
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

                // Apelido do Aparelho
                Text(
                    text = "Apelido do Aparelho",
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
                    value = deviceName.value,
                    onValueChange = { deviceName.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Aparelho",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    placeholder = { Text("Ex: Celular João, Tablet Vendas, etc.", fontSize = 12.sp) },
                    textStyle = TextStyle(color = DarkGray, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = BorderGray,
                        focusedTextColor = DarkGray,
                        unfocusedTextColor = DarkGray,
                        cursorColor = PrimaryBlue
                    ),
                    singleLine = true,
                    maxLines = 1
                )

                Text(
                    text = "Escolha um nome que identifique este aparelho",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = LightGray,
                        fontStyle = FontStyle.Italic
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )

                // Número de Telefone
                Text(
                    text = "Número de Telefone",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )

                // Country Selector + Phone Number
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Telefone",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )

                    // Country Selector
                    Row(
                        modifier = Modifier
                            .clickable { showCountryModal.value = true }
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedCountry.value.flag,
                            fontSize = 16.sp
                        )
                        Text(
                            text = selectedCountry.value.dialCode,
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = DarkGray,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text("▼", fontSize = 8.sp, color = LightGray)
                    }

                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(BorderGray)
                    )

                    // Phone Number Input
                    TextField(
                        value = phoneNumber.value,
                        onValueChange = { phoneNumber.value = it },
                        modifier = Modifier
                            .weight(1f),
                        placeholder = {
                            Text(
                                selectedCountry.value.mask.replace("#", "9"),
                                fontSize = 11.sp,
                                color = LightGray
                            )
                        },
                        textStyle = TextStyle(color = DarkGray, fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = White,
                            unfocusedContainerColor = White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = PrimaryBlue
                        ),
                        singleLine = true
                    )
                }

                Text(
                    text = "Número para contato e identificação do dispositivo • ${selectedCountry.value.name}",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = LightGray,
                        fontStyle = FontStyle.Italic
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )

                // Register Button
                Button(
                    onClick = {
                        when {
                            companyCode.value.isEmpty() -> {
                                errorMessage.value = "Por favor, digite o código da empresa"
                            }
                            deviceName.value.isEmpty() -> {
                                errorMessage.value = "Por favor, digite o apelido do aparelho"
                            }
                            phoneNumber.value.isEmpty() -> {
                                errorMessage.value = "Por favor, digite o número de telefone"
                            }
                            else -> {
                                isLoading.value = true
                                errorMessage.value = ""
                                coroutineScope.launch {
                                    try {
                                        val deviceService = DeviceService(context)
                                        val fullPhone = "${selectedCountry.value.dialCode} ${phoneNumber.value}"
                                        val result = deviceService.registerDevice(
                                            companyCode = companyCode.value.trim(),
                                            deviceAlias = deviceName.value.trim(),
                                            phoneNumber = fullPhone
                                        )
                                        if (result.success) {
                                            // Volta para o Login após registrar
                                            navController.navigate(Screen.Login.route) {
                                                popUpTo(0)
                                            }
                                        } else {
                                            errorMessage.value = result.error ?: "Erro ao registrar dispositivo"
                                        }
                                    } catch (e: Exception) {
                                        errorMessage.value = "Erro inesperado: ${e.message}"
                                    } finally {
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
                            modifier = Modifier.size(20.dp),
                            color = White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Registrar",
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Verify Button
                OutlinedButton(
                    onClick = {
                        isLoading.value = true
                        errorMessage.value = ""
                        coroutineScope.launch {
                            try {
                                val deviceService = DeviceService(context)
                                val result = deviceService.checkCurrentDevice()
                                if (result.success && result.isRegistered) {
                                    // Dispositivo já registrado, volta para login
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0)
                                    }
                                } else if (result.error?.contains("não autorizado") == true) {
                                    errorMessage.value = "Dispositivo pendente de ativação pelo administrador."
                                } else {
                                    errorMessage.value = "Nenhum dispositivo ativado encontrado neste aparelho."
                                }
                            } catch (e: Exception) {
                                errorMessage.value = "Erro ao verificar: ${e.message}"
                            } finally {
                                isLoading.value = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(2.dp, SuccessGreen, RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SuccessGreen,
                        containerColor = White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Verificar",
                        tint = SuccessGreen,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = "Verificar aparelho já ativado",
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SuccessGreen
                        )
                    )
                }

                // Error Message
                if (errorMessage.value.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFFFFEAEA),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Erro",
                            tint = ErrorRed,
                            modifier = Modifier.size(20.dp)
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

        // Info Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(PrimaryBlue, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "i",
                            style = TextStyle(
                                color = White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Text(
                        text = "Como funciona?",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkGray
                        )
                    )
                }

                Column(
                    modifier = Modifier.padding(start = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "• Cada aparelho precisa ser cadastrado uma única vez",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = DarkGray
                        )
                    )
                    Text(
                        text = "• O sistema gera um identificador único (GUID)",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = DarkGray
                        )
                    )
                    Text(
                        text = "• Este GUID fica salvo no aparelho",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = DarkGray
                        )
                    )
                    Text(
                        text = "• Use o mesmo aparelho para fazer login sempre",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = DarkGray
                        )
                    )
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

    // Country Selection Modal
    if (showCountryModal.value) {
        Dialog(
            onDismissRequest = { showCountryModal.value = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(White, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = White)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selecionar País",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkGray
                            )
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fechar",
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { showCountryModal.value = false },
                            tint = LightGray
                        )
                    }

                    HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = BorderGray)

                    // Country List
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(COUNTRIES) { country ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCountry.value = country
                                        phoneNumber.value = ""
                                        showCountryModal.value = false
                                    }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = country.flag, fontSize = 24.sp)
                                Text(
                                    text = country.name,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        color = DarkGray
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = country.dialCode,
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        color = LightGray
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun RegisterDeviceScreenPreview() {
    RegisterDeviceScreen(rememberNavController())
}





