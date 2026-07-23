package com.mateof.tfm.ui.screens.login

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.mateof.tfm.ui.nav.Routes

@Composable
fun LoginScreen(navController: NavHostController, vm: LoginViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(state.authenticated) {
        if (state.authenticated) {
            navController.navigate(Routes.CHANNELS) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(tab) {
        if (tab == 0) vm.startQr() else vm.cancelQr()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Inicia sesión en Telegram",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            "La sesión se guarda en el servidor y se comparte con la web",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Código QR") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Teléfono") })
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.error != null) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            if (tab == 0) QrTab(state, vm) else PhoneTab(state, vm)
        }
    }
}

@Composable
private fun QrTab(state: LoginUiState, vm: LoginViewModel) {
    val context = LocalContext.current

    if (state.qrNeedsPassword) {
        var password by rememberSaveable { mutableStateOf("") }
        Text(
            "Tu cuenta tiene verificación en dos pasos. Introduce la contraseña:",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña 2FA") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.submitQrPassword(password) },
            enabled = password.isNotBlank() && !state.loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Enviar") }
        return
    }

    val qrBitmap = remember(state.qr?.qrImageBase64) {
        state.qr?.qrImageBase64?.let { b64 ->
            runCatching {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    if (qrBitmap != null) {
        Image(
            bitmap = qrBitmap,
            contentDescription = "Código QR de acceso",
            modifier = Modifier
                .size(260.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(12.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Escanéalo desde Telegram: Ajustes → Dispositivos → Conectar dispositivo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        val loginUrl = state.qr?.loginUrl
        if (!loginUrl.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(loginUrl))
                    )
                }
            }) { Text("Abrir en Telegram (este dispositivo)") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = vm::startQr) { Text("Regenerar QR") }
    } else {
        Spacer(Modifier.height(48.dp))
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Generando código QR…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhoneTab(state: LoginUiState, vm: LoginViewModel) {
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    when (state.phoneStep) {
        "vc" -> {
            Text("Introduce el código que Telegram te ha enviado")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Código de verificación") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.submitPhoneValue(code, isPhone = false) },
                enabled = code.isNotBlank() && !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else Text("Verificar")
            }
        }

        "pass" -> {
            Text("Tu cuenta tiene verificación en dos pasos")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña 2FA") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.submitPhoneValue(password, isPhone = false) },
                enabled = password.isNotBlank() && !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else Text("Entrar")
            }
        }

        else -> {
            Text("Introduce tu número de teléfono con prefijo internacional")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Teléfono") },
                placeholder = { Text("+34600000000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.submitPhoneValue(phone, isPhone = true) },
                enabled = phone.isNotBlank() && !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else Text("Enviar código")
            }
        }
    }
}
