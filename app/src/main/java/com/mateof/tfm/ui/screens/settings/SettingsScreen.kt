package com.mateof.tfm.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.AuthApi
import com.mateof.tfm.data.api.ConfigApi
import com.mateof.tfm.data.api.SystemApi
import com.mateof.tfm.data.model.AppConfigDto
import com.mateof.tfm.data.model.SystemInfoDto
import com.mateof.tfm.data.model.TelegramUserDto
import com.mateof.tfm.data.prefs.ServerPreferences
import com.mateof.tfm.ui.components.ConfirmDialog
import com.mateof.tfm.ui.components.ErrorState
import com.mateof.tfm.ui.components.LoadingBox
import com.mateof.tfm.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

data class SettingsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val info: SystemInfoDto? = null,
    val user: TelegramUserDto? = null,
    val config: AppConfigDto? = null,
    val saving: Boolean = false,
    val loggedOut: Boolean = false,
    val snackbar: String? = null,
    val serverUrl: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val systemApi: SystemApi,
    private val authApi: AuthApi,
    private val configApi: ConfigApi,
    prefs: ServerPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(serverUrl = prefs.current.baseUrl))
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            // The three reads are independent — run them in parallel.
            runCatching {
                coroutineScope {
                    val info = async { runCatching { apiCall { systemApi.info() } }.getOrNull() }
                    val user = async { runCatching { apiCall { authApi.me() } }.getOrNull() }
                    val config = async { runCatching { apiCall { configApi.get() } }.getOrNull() }
                    Triple(info.await(), user.await(), config.await())
                }
            }.onSuccess { (info, user, config) ->
                if (info == null && user == null && config == null) {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = "No se pudo contactar con el servidor"
                    )
                } else {
                    _state.value = _state.value.copy(
                        loading = false, info = info, user = user, config = config
                    )
                }
            }
        }
    }

    fun saveConfig(
        maxSimultaneousDownloads: Int?,
        parallelTransfers: Int?,
        downloadConnections: Int?,
        checkHash: Boolean?
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            val body = buildJsonObject {
                maxSimultaneousDownloads?.let { put("maxSimultaneousDownloads", JsonPrimitive(it)) }
                parallelTransfers?.let { put("parallelTransfers", JsonPrimitive(it)) }
                downloadConnections?.let { put("downloadConnections", JsonPrimitive(it)) }
                checkHash?.let { put("checkHash", JsonPrimitive(it)) }
            }
            runCatching { apiCall { configApi.patch(body) } }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        saving = false,
                        config = updated,
                        snackbar = "Configuración guardada"
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(saving = false, snackbar = e.userMessage())
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            runCatching { apiCall { authApi.logout() } }
                .onSuccess { _state.value = _state.value.copy(saving = false, loggedOut = true) }
                .onFailure { e ->
                    _state.value = _state.value.copy(saving = false, snackbar = e.userMessage())
                }
        }
    }

    fun snackbarShown() {
        _state.value = _state.value.copy(snackbar = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            vm.snackbarShown()
        }
    }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Ajustes") }) }
    ) { padding ->
        when {
            state.loading -> LoadingBox(modifier = Modifier.padding(padding), label = "Cargando…")
            state.error != null -> ErrorState(
                state.error!!,
                modifier = Modifier.padding(padding),
                onRetry = vm::load
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // --------------------------------------------------- server
                SectionCard(icon = { Icon(Icons.Outlined.Dns, null) }, title = "Servidor") {
                    InfoLine("URL", state.serverUrl)
                    state.info?.let { info ->
                        InfoLine("Producto", "${info.product ?: "-"} ${info.version ?: ""}")
                        InfoLine("API", info.apiVersion ?: "-")
                        InfoLine("MongoDB", if (info.mongoConnected) "Conectado" else "Desconectado")
                        InfoLine(
                            "Telegram",
                            if (info.telegramAuthenticated) "Autenticado" else "Sin sesión"
                        )
                        InfoLine("API key requerida", if (info.requiresApiKey) "Sí" else "No")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        navController.navigate(Routes.SETUP)
                    }) { Text("Cambiar servidor / API key") }
                }

                Spacer(Modifier.height(16.dp))

                // --------------------------------------------------- account
                SectionCard(
                    icon = { Icon(Icons.Outlined.AccountCircle, null) },
                    title = "Cuenta de Telegram"
                ) {
                    val user = state.user
                    if (user != null) {
                        InfoLine(
                            "Usuario",
                            listOfNotNull(user.firstName, user.lastName).joinToString(" ")
                                .ifBlank { user.username ?: "-" }
                        )
                        user.username?.let { InfoLine("Alias", "@$it") }
                        user.phone?.let { InfoLine("Teléfono", "+$it") }
                        InfoLine("Premium", if (user.isPremium) "Sí (subidas 4 GB)" else "No (subidas 2 GB)")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { confirmLogout = true },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Outlined.Logout, null)
                            Spacer(Modifier.height(0.dp))
                            Text("  Cerrar sesión (afecta a todos los clientes)")
                        }
                    } else {
                        Text(
                            "Sin sesión de Telegram",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { navController.navigate(Routes.LOGIN) }) {
                            Text("Iniciar sesión")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---------------------------------------------------- config
                SectionCard(
                    icon = { Icon(Icons.Outlined.Tune, null) },
                    title = "Configuración del servidor"
                ) {
                    val config = state.config
                    if (config == null) {
                        Text(
                            "No disponible",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        var maxDownloads by rememberSaveable(config.maxSimultaneousDownloads) {
                            mutableStateOf(config.maxSimultaneousDownloads?.toString() ?: "")
                        }
                        var parallel by rememberSaveable(config.parallelTransfers) {
                            mutableStateOf(config.parallelTransfers?.toString() ?: "")
                        }
                        var connections by rememberSaveable(config.downloadConnections) {
                            mutableStateOf(config.downloadConnections?.toString() ?: "")
                        }
                        var checkHash by rememberSaveable(config.checkHash) {
                            mutableStateOf(config.checkHash ?: false)
                        }

                        OutlinedTextField(
                            value = maxDownloads,
                            onValueChange = { maxDownloads = it.filter(Char::isDigit) },
                            label = { Text("Descargas simultáneas") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = parallel,
                            onValueChange = { parallel = it.filter(Char::isDigit) },
                            label = { Text("Chunks paralelos por transferencia (1–16)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = connections,
                            onValueChange = { connections = it.filter(Char::isDigit) },
                            label = { Text("Conexiones por descarga (2–8)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Calcular hash al subir", modifier = Modifier.weight(1f))
                            Switch(checked = checkHash, onCheckedChange = { checkHash = it })
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                vm.saveConfig(
                                    maxDownloads.toIntOrNull(),
                                    parallel.toIntOrNull(),
                                    connections.toIntOrNull(),
                                    checkHash
                                )
                            },
                            enabled = !state.saving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (state.saving) "Guardando…" else "Guardar configuración")
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Cerrar sesión de Telegram",
            text = "La sesión es compartida: la web y el resto de clientes también quedarán desconectados.",
            confirmLabel = "Cerrar sesión",
            destructive = true,
            onConfirm = vm::logout,
            onDismiss = { confirmLogout = false }
        )
    }
}

@Composable
private fun SectionCard(
    icon: @Composable () -> Unit,
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            content()
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.55f)
        )
    }
}
