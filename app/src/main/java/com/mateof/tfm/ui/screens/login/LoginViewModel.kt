package com.mateof.tfm.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.AuthApi
import com.mateof.tfm.data.model.LoginRequest
import com.mateof.tfm.data.model.QrPasswordRequest
import com.mateof.tfm.data.model.QrSessionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    // phone flow: phone | vc | pass | ok
    val phoneStep: String = "phone",
    // qr flow
    val qr: QrSessionDto? = null,
    val qrNeedsPassword: Boolean = false,
    val authenticated: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApi: AuthApi
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { apiCall { authApi.status() } }.onSuccess { s ->
                _state.value = _state.value.copy(
                    phoneStep = s.step ?: "phone",
                    authenticated = s.step == "ok"
                )
            }
        }
    }

    // ------------------------------------------------------------------ phone

    fun submitPhoneValue(value: String, isPhone: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { apiCall { authApi.login(LoginRequest(value.trim(), isPhone)) } }
                .onSuccess { s ->
                    _state.value = _state.value.copy(
                        loading = false,
                        phoneStep = s.step ?: "phone",
                        authenticated = s.step == "ok"
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.userMessage())
                }
        }
    }

    // --------------------------------------------------------------------- qr

    fun startQr() {
        pollJob?.cancel()
        _state.value = _state.value.copy(qr = null, qrNeedsPassword = false, error = null)
        pollJob = viewModelScope.launch {
            val session = runCatching { apiCall { authApi.qrStart() } }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.userMessage())
                }
                .getOrNull() ?: return@launch
            _state.value = _state.value.copy(qr = session)
            val id = session.sessionId ?: return@launch
            while (isActive) {
                delay(2000)
                val polled = runCatching { apiCall { authApi.qrPoll(id) } }.getOrNull() ?: continue
                when (polled.status) {
                    "authenticated" -> {
                        _state.value = _state.value.copy(qr = polled, authenticated = true)
                        return@launch
                    }
                    "password_required" -> {
                        _state.value = _state.value.copy(qr = polled, qrNeedsPassword = true)
                    }
                    "cancelled", "error" -> {
                        _state.value = _state.value.copy(
                            qr = polled,
                            error = polled.error ?: "Sesión QR cancelada; genera una nueva"
                        )
                        return@launch
                    }
                    else -> _state.value = _state.value.copy(qr = polled)
                }
            }
        }
    }

    fun submitQrPassword(password: String) {
        val id = _state.value.qr?.sessionId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { apiCall { authApi.qrPassword(id, QrPasswordRequest(password)) } }
                .onSuccess {
                    _state.value = _state.value.copy(loading = false, qrNeedsPassword = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.userMessage())
                }
        }
    }

    fun cancelQr() {
        val id = _state.value.qr?.sessionId
        pollJob?.cancel()
        if (id != null) {
            viewModelScope.launch {
                runCatching { apiCall { authApi.qrCancel(id) } }
            }
        }
    }

    override fun onCleared() {
        cancelQr()
        super.onCleared()
    }
}
