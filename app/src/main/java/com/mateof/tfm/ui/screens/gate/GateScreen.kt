package com.mateof.tfm.ui.screens.gate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.AuthApi
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.data.prefs.ServerPreferences
import com.mateof.tfm.ui.components.ErrorState
import com.mateof.tfm.ui.components.LoadingBox
import com.mateof.tfm.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GateState {
    data object Loading : GateState
    data class Go(val route: String) : GateState
    data class Error(val message: String) : GateState
}

@HiltViewModel
class GateViewModel @Inject constructor(
    private val prefs: ServerPreferences,
    private val authApi: AuthApi
) : ViewModel() {

    private val _state = MutableStateFlow<GateState>(GateState.Loading)
    val state = _state.asStateFlow()

    init {
        check()
    }

    fun check() {
        _state.value = GateState.Loading
        viewModelScope.launch {
            val cfg = prefs.awaitLoaded()
            if (!cfg.configured || cfg.baseUrl.isBlank()) {
                _state.value = GateState.Go(Routes.SETUP)
                return@launch
            }
            runCatching { apiCall { authApi.status() } }
                .onSuccess { status ->
                    _state.value = GateState.Go(
                        if (status.step == "ok") Routes.CHANNELS else Routes.LOGIN
                    )
                }
                .onFailure { e ->
                    _state.value = GateState.Error(e.userMessage())
                }
        }
    }
}

@Composable
fun GateScreen(navController: NavHostController, vm: GateViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val go = state as? GateState.Go ?: return@LaunchedEffect
        navController.navigate(go.route) {
            popUpTo(Routes.GATE) { inclusive = true }
        }
    }

    when (val s = state) {
        is GateState.Loading, is GateState.Go -> LoadingBox(label = "Conectando con el servidor…")
        is GateState.Error -> ErrorState(
            message = s.message,
            onRetry = vm::check
        )
    }
}
