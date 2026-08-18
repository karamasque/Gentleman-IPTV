package com.kaynanamtv.app.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaynanamtv.app.R
import com.kaynanamtv.app.device.rememberIsTelevisionDevice
import com.kaynanamtv.app.ui.components.shell.StatusPill
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.ui.interaction.TvButton
import com.kaynanamtv.app.ui.interaction.TvClickableSurface
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.kaynanamtv.app.ui.screens.provider.ProviderTextField
import com.kaynanamtv.app.ui.screens.welcome.PremiumBackground

@Composable
fun AuthScreen(
    onNavigateToWelcome: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isTelevisionDevice = rememberIsTelevisionDevice()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.sessionFlow.collectAsStateWithLifecycle(initialValue = null)

    var showEmailLoginOnTv by remember { mutableStateOf(false) }

    LaunchedEffect(session) {
        if (session != null) {
            onNavigateToWelcome()
        }
    }

    LaunchedEffect(isTelevisionDevice) {
        if (isTelevisionDevice) {
            viewModel.startTvActivationFlow()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
        }
    }

    PremiumBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isTelevisionDevice && !showEmailLoginOnTv) {
                TvActivationPanel(
                    uiState = uiState,
                    onUseEmail = { showEmailLoginOnTv = true },
                    onRetryCode = { viewModel.startTvActivationFlow() },
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                EmailAuthFormCard(
                    uiState = uiState,
                    isTv = isTelevisionDevice,
                    onEmailChange = viewModel::updateEmail,
                    onTabChange = viewModel::setLoginTab,
                    onSubmit = { pass ->
                        viewModel.authenticate(pass, onNavigateToWelcome)
                    },
                    onForgotPassword = {
                        val email = uiState.email.trim()
                        if (email.isBlank()) {
                            Toast.makeText(context, "Lütfen önce yukarıdaki alana e-posta adresinizi yazın.", Toast.LENGTH_LONG).show()
                        }
                        viewModel.sendPasswordReset()
                    },
                    onBackToTvPairing = { showEmailLoginOnTv = false },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TvActivationPanel(
    uiState: AuthUiState,
    onUseEmail: () -> Unit,
    onRetryCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(AppColors.Brand.copy(alpha = 0.5f), AppColors.NeonCyan.copy(alpha = 0.5f))
    )

    Box(
        modifier = modifier
            .widthIn(max = 780.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE60A0E1A))
            .border(androidx.compose.foundation.BorderStroke(1.5.dp, borderBrush), RoundedCornerShape(24.dp))
            .padding(horizontal = 40.dp, vertical = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Instructions and Code
            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusPill(
                    label = "CİHAZ AKTİVASYONU",
                    containerColor = AppColors.BrandMuted,
                    contentColor = AppColors.BrandStrong
                )
                
                Text(
                    text = "Televizyonunuzu Eşleyin",
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Telefon veya bilgisayarınızdan şu adrese gidin:\nhttps://${uiState.tvActivationUrl ?: "kaynanamtv.app/activate"}\n\nEkranda gördüğünüz kodu girerek anında giriş yapın.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )

                if (uiState.tvActivationStatus == TvActivationStatus.GENERATING_CODE) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.NeonCyan)
                        Text("Kod oluşturuluyor...", color = AppColors.TextTertiary, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (uiState.tvActivationCode != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF132032))
                            .border(1.dp, AppColors.Outline, RoundedCornerShape(12.dp))
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = uiState.tvActivationCode.orEmpty(),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                brush = Brush.linearGradient(
                                    colors = listOf(AppColors.BrandStrong, AppColors.NeonCyan)
                                )
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(onClick = onUseEmail) {
                        Text("E-posta ile Giriş Yap")
                    }
                    if (uiState.tvActivationStatus == TvActivationStatus.ERROR) {
                        TvButton(onClick = onRetryCode) {
                            Text("Tekrar Dene")
                        }
                    }
                }
            }

            // Right Side: QR Code
            Column(
                modifier = Modifier.weight(0.8f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.tvQrBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Eşleşme QR Kodu",
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(2.dp, AppColors.NeonCyan, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    )
                } ?: Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(Color(0xFF0F1F3A), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.Brand)
                }

                Text(
                    text = "QR Kodu Tara",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmailAuthFormCard(
    uiState: AuthUiState,
    isTv: Boolean,
    onEmailChange: (String) -> Unit,
    onTabChange: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onForgotPassword: () -> Unit,
    onBackToTvPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    val borderBrush = Brush.linearGradient(
        colors = listOf(AppColors.Brand.copy(alpha = 0.5f), AppColors.NeonCyan.copy(alpha = 0.5f))
    )

    Box(
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE60A0E1A))
            .border(androidx.compose.foundation.BorderStroke(1.5.dp, borderBrush), RoundedCornerShape(24.dp))
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "KaynanamTV",
                style = MaterialTheme.typography.headlineSmall.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(AppColors.BrandStrong, AppColors.NeonCyan)
                    )
                ),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            // Switch Tab (Remote Focusable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B132B))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(true, false).forEach { isLoginTab ->
                    val selected = uiState.isLoginTab == isLoginTab
                    val tabBg = if (selected) Color(0xFF4F46E5) else Color.Transparent
                    val tabTextColor = if (selected) Color.White else Color(0xFF94A3B8)

                    TvButton(
                        onClick = { onTabChange(isLoginTab) },
                        modifier = Modifier.weight(1f),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = tabBg,
                            focusedContainerColor = if (selected) Color(0xFF6366F1) else Color(0xFF1E293B),
                            contentColor = tabTextColor,
                            focusedContentColor = Color.White
                        ),
                        border = ButtonDefaults.border(
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)),
                                shape = RoundedCornerShape(8.dp)
                            )
                        )
                    ) {
                        Text(
                            text = if (isLoginTab) "Giriş Yap" else "Kayıt Ol",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = tabTextColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Email Input
            ProviderTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                placeholder = "E-posta Adresi",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            // Password Input
            ProviderTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Şifre (Min. 6 karakter)",
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSubmit(password) }
                )
            )

            // Error message
            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = Color(0xFFFF5252),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            // Success message (e.g. password reset sent)
            uiState.successMessage?.let { success ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A2A1A))
                        .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = success,
                        color = Color(0xFF81C784),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color(0xFF4F46E5)
                )
            } else {
                TvButton(
                    onClick = { onSubmit(password) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF4F46E5),
                        focusedContainerColor = Color(0xFF6366F1),
                        contentColor = Color.White,
                        focusedContentColor = Color.White
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(12.dp)
                        )
                    )
                ) {
                    Text(
                        text = if (uiState.isLoginTab) "Giriş Yap" else "Kayıt Ol",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }

                // Forgot password button - only on login tab (TV Focusable)
                if (uiState.isLoginTab) {
                    TvButton(
                        onClick = onForgotPassword,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF0B132B),
                            focusedContainerColor = Color(0xFF1C2541),
                            contentColor = Color(0xFF38BDF8),
                            focusedContentColor = Color.White
                        ),
                        border = ButtonDefaults.border(
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                                shape = RoundedCornerShape(10.dp)
                            )
                        )
                    ) {
                        Text(
                            text = "🔑 Şifremi Unuttum",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (isTv) {
                    TvButton(
                        onClick = onBackToTvPairing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF1E293B),
                            focusedContainerColor = Color(0xFF334155),
                            contentColor = Color.White,
                            focusedContentColor = Color.White
                        ),
                        border = ButtonDefaults.border(
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                                shape = RoundedCornerShape(10.dp)
                            )
                        )
                    ) {
                        Text("QR Eşleşme Koduna Dön", color = Color.White)
                    }
                }
            }

            // Info notice about free trial
            if (!uiState.isLoginTab) {
                Text(
                    text = "Kayıt olan her hesaba 7 gün ücretsiz tam sürüm deneme otomatik tanımlanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
