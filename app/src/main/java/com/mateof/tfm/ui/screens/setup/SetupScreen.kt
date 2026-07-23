package com.mateof.tfm.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.SystemApi
import com.mateof.tfm.data.model.SystemInfoDto
import com.mateof.tfm.data.prefs.ServerPreferences
import com.mateof.tfm.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val info: SystemInfoDto? = null,
    val done: Boolean = false,
    val initialUrl: String = "",
    val initialKey: String = ""
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val prefs: ServerPreferences,
    private val systemApi: SystemApi
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = prefs.awaitLoaded()
            _state.value = _state.value.copy(initialUrl = cfg.baseUrl, initialKey = cfg.apiKey)
        }
    }

    fun testAndSave(url: String, apiKey: String) {
        val cleanUrl = url.trim().trimEnd('/')
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            // Save first so interceptors point at the new host, then verify.
            val previous = prefs.current
            prefs.save(cleanUrl, apiKey.trim())
            runCatching { apiCall { systemApi.info() } }
                .onSuccess { info ->
                    _state.value = _state.value.copy(loading = false, info = info, done = true)
                }
                .onFailure { e ->
                    // Roll back so a bad attempt doesn't break an existing setup.
                    if (previous.configured) prefs.save(previous.baseUrl, previous.apiKey)
                    _state.value = _state.value.copy(loading = false, error = e.userMessage())
                }
        }
    }
}

@Composable
fun SetupScreen(navController: NavHostController, vm: SetupViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    var url by rememberSaveable { mutableStateOf("") }
    var key by rememberSaveable { mutableStateOf("") }
    var prefilled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.initialUrl, state.initialKey) {
        if (!prefilled && (state.initialUrl.isNotBlank() || state.initialKey.isNotBlank())) {
            url = state.initialUrl
            key = state.initialKey
            prefilled = true
        }
    }

    LaunchedEffect(state.done) {
        if (state.done) {
            navController.navigate(Routes.GATE) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Send,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text("Telegram File Manager", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Configura la conexión con tu servidor",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL del servidor") },
            placeholder = { Text("http://192.168.1.50:5257") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("API key (vacío si el servidor no la exige)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.testAndSave(url, key) },
            enabled = !state.loading && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Conectar")
            }
        }
    }
}
