package com.kaynanamtv.app.ui.screens.auth

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.model.UserSession
import com.kaynanamtv.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TvActivationStatus {
    IDLE,
    GENERATING_CODE,
    WAITING_FOR_ACTIVATION,
    ACTIVATED,
    ERROR
}

data class AuthUiState(
    val email: String = "",
    val isLoginTab: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showForgotPassword: Boolean = false,
    val tvActivationStatus: TvActivationStatus = TvActivationStatus.IDLE,
    val tvActivationCode: String? = null,
    val tvActivationUrl: String? = null,
    val tvQrBitmap: Bitmap? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val sessionFlow = authRepository.getSessionFlow()

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun setLoginTab(isLogin: Boolean) {
        _uiState.value = _uiState.value.copy(isLoginTab = isLogin, errorMessage = null)
    }

    fun authenticate(password: String, onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "E-posta ve şifre boş bırakılamaz.")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null, successMessage = null)
        viewModelScope.launch {
            val result = if (state.isLoginTab) {
                authRepository.login(state.email, password)
            } else {
                authRepository.register(state.email, password)
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
            when (result) {
                is Result.Success -> {
                    onSuccess()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
                }
                else -> {}
            }
        }
    }

    fun setShowForgotPassword(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            showForgotPassword = show,
            errorMessage = null,
            successMessage = null
        )
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Lütfen önce yukarıdaki alana e-posta adresinizi girin.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, successMessage = null)
        viewModelScope.launch {
            val result = authRepository.sendPasswordResetEmail(email)
            _uiState.value = _uiState.value.copy(isLoading = false)
            when (result) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "✉️ Şifre sıfırlama bağlantısı $email adresine gönderildi.\nLütfen gelen kutunuzu ve Spam/Gereksiz klasörünü kontrol edin.",
                        showForgotPassword = false
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
                }
                else -> {}
            }
        }
    }

    fun startTvActivationFlow() {
        _uiState.value = _uiState.value.copy(tvActivationStatus = TvActivationStatus.GENERATING_CODE)
        viewModelScope.launch {
            val res = authRepository.getDeviceCode()
            when (res) {
                is Result.Success -> {
                    val (code, url) = res.data
                    val qrBitmap = generateQrCodeBitmap(url, code)
                    _uiState.value = _uiState.value.copy(
                        tvActivationStatus = TvActivationStatus.WAITING_FOR_ACTIVATION,
                        tvActivationCode = code,
                        tvActivationUrl = url,
                        tvQrBitmap = qrBitmap
                    )
                    listenForTvActivation(code)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        tvActivationStatus = TvActivationStatus.ERROR,
                        errorMessage = res.message
                    )
                }
                else -> {}
            }
        }
    }

    private fun listenForTvActivation(code: String) {
        viewModelScope.launch {
            authRepository.watchDeviceActivation(code).collectLatest { res ->
                when (res) {
                    is Result.Success -> {
                        _uiState.value = _uiState.value.copy(tvActivationStatus = TvActivationStatus.ACTIVATED)
                    }
                    is Result.Error -> {
                        _uiState.value = _uiState.value.copy(
                            tvActivationStatus = TvActivationStatus.ERROR,
                            errorMessage = res.message
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    private fun generateQrCodeBitmap(url: String, code: String): Bitmap? {
        return try {
            val activationUrl = "https://$url?code=$code"
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(activationUrl, BarcodeFormat.QR_CODE, 512, 512)
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
            for (x in 0 until 512) {
                for (y in 0 until 512) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
