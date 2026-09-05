package com.kaynanamtv.app.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaynanamtv.app.ui.components.shell.StatusPill
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.ui.interaction.TvButton
import com.kaynanamtv.domain.manager.EntitlementManager
import com.kaynanamtv.domain.model.Feature
import com.kaynanamtv.domain.model.PaymentRequest
import com.kaynanamtv.domain.model.PaymentRequestStatus
import com.kaynanamtv.domain.model.PremiumPlan
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.model.TrialStatus
import com.kaynanamtv.domain.model.UserSession
import com.kaynanamtv.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ── ViewModel ──────────────────────────────────────────────────────────────────

data class MembershipUiState(
    val session: UserSession? = null,
    val trialStatus: TrialStatus = TrialStatus.NO_SESSION,
    val paymentRequests: List<PaymentRequest> = emptyList(),
    val isSubmittingPayment: Boolean = false,
    val paymentMessage: String? = null,
    val isLoading: Boolean = true,
    val loggedOut: Boolean = false
)

@HiltViewModel
class MembershipViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MembershipUiState())
    val uiState: StateFlow<MembershipUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getSessionFlow().collect { session ->
                if (session == null) {
                    _uiState.update {
                        it.copy(
                            session = null,
                            trialStatus = TrialStatus.NO_SESSION,
                            isLoading = false
                        )
                    }
                } else {
                    val status = authRepository.checkTrialStatus()
                    _uiState.update {
                        it.copy(
                            session = session,
                            trialStatus = status,
                            isLoading = false
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            authRepository.getPaymentRequestsFlow().collect { requests ->
                _uiState.update { it.copy(paymentRequests = requests) }
            }
        }
    }

    fun submitPayment(plan: PremiumPlan, expectedPrice: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingPayment = true, paymentMessage = null) }
            when (val result = authRepository.submitPaymentRequest(plan, expectedPrice)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmittingPayment = false,
                            paymentMessage = "Ödeme bildiriminiz alındı! Havale açıklamanıza '${result.data.paymentCode}' yazmayı unutmayın. Admin onayından sonra Premium aktif olacaktır."
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSubmittingPayment = false,
                            paymentMessage = "Hata: ${result.message}"
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isSubmittingPayment = false) }
                }
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}

// ── Section Extension ──────────────────────────────────────────────────────────

internal fun LazyListScope.settingsMembershipSection(
    onLogout: () -> Unit
) {
    item(key = "settings_membership_section_content") {
        MembershipSectionContent(onLogout = onLogout)
    }
}

// ── Composables ────────────────────────────────────────────────────────────────

@Composable
private fun MembershipSectionContent(
    onLogout: () -> Unit,
    viewModel: MembershipViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) onLogout()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MembershipSectionHeader()

        if (uiState.isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = AppColors.Brand
            )
            return@Column
        }

        val session = uiState.session
        if (session == null) {
            MembershipCard(
                icon = "⚠",
                title = "Giriş yapılmamış",
                subtitle = "Bu bölümü görüntülemek için lütfen giriş yapın.",
                accentColor = Color(0xFFFFB74D)
            )
            return@Column
        }

        // ── Account Info Card ────────────────────────────────────────────────
        AccountInfoCard(session = session)

        // ── Trial / Premium Status Card ──────────────────────────────────────
        SubscriptionStatusCard(
            trialStatus = uiState.trialStatus,
            trialExpiresAt = session.trialExpiresAt,
            isPremium = session.isPremium,
            premiumPlan = session.premiumPlan,
            premiumExpiresAt = session.premiumExpiresAt
        )

        // ── Plan Breakdown & Feature Comparison (Free vs Premium) ─────────────
        MembershipPlanComparisonCard(
            isPremium = session.isPremium,
            premiumPlan = session.premiumPlan
        )

        // ── IBAN Payment / Upgrade Section (If not Lifetime) ─────────────────
        if (session.premiumPlan != PremiumPlan.LIFETIME) {
            IbanPaymentUpgradeCard(
                userId = session.userId,
                isSubmitting = uiState.isSubmittingPayment,
                paymentMessage = uiState.paymentMessage,
                onSubmit = { plan, price -> viewModel.submitPayment(plan, price) },
                onCopy = { label, text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
                    Toast.makeText(context, "$label kopyalandı!", Toast.LENGTH_SHORT).show()
                }
            )

            // ── WhatsApp Support Button Card ─────────────────────────────────
            WhatsAppSupportCard(
                userId = session.userId,
                onOpenWhatsApp = { code -> openWhatsAppSupport(context, code) }
            )
        }

        // ── Pending / Past Payment Requests Card ─────────────────────────────
        if (uiState.paymentRequests.isNotEmpty()) {
            PaymentRequestsHistoryCard(requests = uiState.paymentRequests)
        }

        // ── Account Date Card ───────────────────────────────────────────────
        AccountDatesCard(
            createdAt = session.createdAt,
            trialExpiresAt = session.trialExpiresAt,
            premiumExpiresAt = session.premiumExpiresAt,
            premiumPlan = session.premiumPlan
        )

        // ── Logout ──────────────────────────────────────────────────────────
        Spacer(Modifier.height(4.dp))
        TvButton(
            onClick = { viewModel.logout(onDone = onLogout) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.colors(
                containerColor = Color(0xFF3A1A1A),
                focusedContainerColor = Color(0xFFEF5350),
                contentColor = Color(0xFFEF5350),
                focusedContentColor = Color.White
            )
        ) {
            Text(
                text = "🚪  Çıkış Yap",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MembershipSectionHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(AppColors.Brand, AppColors.NeonCyan)
                    )
                )
        ) {
            Text("👤", fontSize = 22.sp)
        }
        Column {
            Text(
                text = "Üyelik & Premium Bilgileri",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = AppColors.TextPrimary
            )
            Text(
                text = "Hesabınızı, üyelik modelinizi ve Premium aboneliklerinizi buradan yönetin.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun AccountInfoCard(session: UserSession) {
    InfoCard(
        title = "Hesap Bilgileri",
        accentColor = AppColors.Brand
    ) {
        InfoRow(label = "E-posta", value = session.email)
        InfoRow(label = "Kullanıcı ID", value = session.userId.take(16) + "…")
        InfoRow(label = "Rol / Yetki", value = if (session.isAdmin || session.role == "ADMIN" || session.email == "kilicemre3437@gmail.com") "👑 Kurucu" else "Kullanıcı")
    }
}

@Composable
private fun SubscriptionStatusCard(
    trialStatus: TrialStatus,
    trialExpiresAt: Long,
    isPremium: Boolean,
    premiumPlan: PremiumPlan,
    premiumExpiresAt: Long
) {
    val now = System.currentTimeMillis()
    val remainingMs = if (premiumPlan == PremiumPlan.YEARLY) (premiumExpiresAt - now) else 0L
    val remainingDays = TimeUnit.MILLISECONDS.toDays(remainingMs).coerceAtLeast(0)
    val remainingHours = (TimeUnit.MILLISECONDS.toHours(remainingMs) % 24).coerceAtLeast(0)

    val (accentColor, statusLabel, statusEmoji, description) = when {
        premiumPlan == PremiumPlan.LIFETIME || (isPremium && premiumPlan == PremiumPlan.LIFETIME) -> Tuple4(
            Color(0xFFFFD700),
            "SINIRSIZ (LIFETIME) PREMİUM",
            "👑",
            "KaynanamTV'ye sınırsız ve süresiz tam erişiminiz bulunmaktadır. Tüm Premium özellikler aktiftir."
        )
        premiumPlan == PremiumPlan.YEARLY && isPremium && remainingMs > 0 -> Tuple4(
            Color(0xFFFFD700),
            "YILLIK PREMİUM AKTİF",
            "⭐",
            "Yıllık Premium aboneliğiniz aktif ($remainingDays gün $remainingHours saat kaldı)."
        )
        premiumPlan == PremiumPlan.YEARLY && (!isPremium || remainingMs <= 0) -> Tuple4(
            Color(0xFFEF5350),
            "YILLIK PREMİUM SÜRESİ BİTTİ",
            "❌",
            "Yıllık abonelik süreniz dolmuştur. Aşağıdaki ödeme bölümünden yenileyebilir veya Ücretsiz sürümü süresiz kullanmaya devam edebilirsiniz."
        )
        else -> Tuple4(
            Color(0xFF10B981),
            "ÜCRETSİZ (FREE) ÜYE",
            "🆓",
            "KaynanamTV'yi süresiz ve kesintisiz olarak Ücretsiz kullanıyorsunuz. Canlı TV, film ve dizi yayınlarını sınırsız izleyebilirsiniz. Gelişmiş Premium özellikler için aşağıdan Premium pakete geçebilirsiniz."
        )
    }

    InfoCard(
        title = "Abonelik Durumu",
        accentColor = accentColor
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(statusEmoji, fontSize = 28.sp)
            Column {
                StatusPill(
                    label = statusLabel,
                    containerColor = accentColor.copy(alpha = 0.2f),
                    contentColor = accentColor
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun IbanPaymentUpgradeCard(
    userId: String,
    isSubmitting: Boolean,
    paymentMessage: String?,
    onSubmit: (PremiumPlan, String) -> Unit,
    onCopy: (String, String) -> Unit
) {
    var selectedPlan by remember { mutableStateOf(PremiumPlan.YEARLY) }

    val accountHolder = com.kaynanamtv.domain.model.PremiumBankConfig.ACCOUNT_HOLDER
    val bankName = com.kaynanamtv.domain.model.PremiumBankConfig.BANK_NAME
    val ibanFormatted = com.kaynanamtv.domain.model.PremiumBankConfig.IBAN_FORMATTED
    val ibanClean = com.kaynanamtv.domain.model.PremiumBankConfig.IBAN_CLEAN
    val paymentCode = "KTV-" + userId.takeLast(6).uppercase()
    val price = if (selectedPlan == PremiumPlan.YEARLY) "${com.kaynanamtv.domain.model.PremiumPricingConfig.PRICE_YEARLY} / Yıl (365 Gün)" else "${com.kaynanamtv.domain.model.PremiumPricingConfig.PRICE_LIFETIME} / Tek Sefer (Süresiz)"

    InfoCard(
        title = "💳 Premium Satın Al (IBAN / FAST / Havale)",
        accentColor = Color(0xFFFFD700)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "KaynanamTV altyapısını ve kesintisiz desteği sürdürmek için Premium paketinizi seçip doğrudan banka hesabımıza güvenle ödeyebilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )

            // ── Plan Selection Toggle ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TvButton(
                    onClick = { selectedPlan = PremiumPlan.YEARLY },
                    modifier = Modifier.weight(1f),
                    shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = if (selectedPlan == PremiumPlan.YEARLY) Color(0xFF4F46E5) else Color(0xFF0F172A),
                        focusedContainerColor = if (selectedPlan == PremiumPlan.YEARLY) Color(0xFF6366F1) else Color(0xFF1E293B),
                        contentColor = if (selectedPlan == PremiumPlan.YEARLY) Color.White else Color(0xFF94A3B8),
                        focusedContentColor = Color.White
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(10.dp)
                        )
                    )
                ) {
                    Text(
                        text = "💎 Yıllık (${com.kaynanamtv.domain.model.PremiumPricingConfig.PRICE_YEARLY})",
                        fontWeight = FontWeight.Bold,
                        color = if (selectedPlan == PremiumPlan.YEARLY) Color.White else Color(0xFF94A3B8)
                    )
                }
                TvButton(
                    onClick = { selectedPlan = PremiumPlan.LIFETIME },
                    modifier = Modifier.weight(1f),
                    shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = if (selectedPlan == PremiumPlan.LIFETIME) Color(0xFFEAB308) else Color(0xFF0F172A),
                        focusedContainerColor = if (selectedPlan == PremiumPlan.LIFETIME) Color(0xFFFACC15) else Color(0xFF1E293B),
                        contentColor = if (selectedPlan == PremiumPlan.LIFETIME) Color.Black else Color(0xFF94A3B8),
                        focusedContentColor = if (selectedPlan == PremiumPlan.LIFETIME) Color.Black else Color.White
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(10.dp)
                        )
                    )
                ) {
                    Text(
                        text = "👑 Sınırsız (${com.kaynanamtv.domain.model.PremiumPricingConfig.PRICE_LIFETIME})",
                        fontWeight = FontWeight.Bold,
                        color = if (selectedPlan == PremiumPlan.LIFETIME) Color.Black else Color(0xFF94A3B8)
                    )
                }
            }

            // ── Bank Information Box ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF161B22))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow(label = "Seçilen Paket", value = "${selectedPlan.name} ($price)")
                    InfoRow(label = "Hesap Sahibi", value = accountHolder)
                    InfoRow(label = "Banka", value = bankName)
                    InfoRow(label = "IBAN", value = ibanFormatted)
                    InfoRow(label = "Ödeme Açıklama Kodu", value = paymentCode)
                }
            }

            // ── Copy Buttons & Submit Action ─────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TvButton(
                    onClick = { onCopy("IBAN", ibanClean) },
                    modifier = Modifier.weight(1f),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF0F172A),
                        focusedContainerColor = Color(0xFF1E293B),
                        contentColor = Color.White,
                        focusedContentColor = Color.White
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(8.dp)
                        )
                    )
                ) {
                    Text("📋 IBAN Kopyala", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                TvButton(
                    onClick = { onCopy("Ödeme Kodu", paymentCode) },
                    modifier = Modifier.weight(1f),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF0F172A),
                        focusedContainerColor = Color(0xFF1E293B),
                        contentColor = Color.White,
                        focusedContentColor = Color.White
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(8.dp)
                        )
                    )
                ) {
                    Text("🔑 Kodu Kopyala", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                TvButton(
                    onClick = {
                        val expected = com.kaynanamtv.domain.model.PremiumPricingConfig.getPrice(selectedPlan)
                        onSubmit(selectedPlan, expected)
                    },
                    modifier = Modifier.weight(1.3f),
                    enabled = !isSubmitting,
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF4F46E5),
                        focusedContainerColor = Color(0xFF6366F1),
                        contentColor = Color.White,
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
                        text = if (isSubmitting) "İletiliyor..." else "✅ Ödemeyi Yaptım",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // ── Notice Box ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1C1917))
                    .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "⚠️ ÖNEMLİ ÖDEME UYARISI",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB74D)
                    )
                    Text(
                        text = "• Ödeme yaparken açıklama bölümüne size özel ödeme kodunu ($paymentCode) eksiksiz yazınız.\n" +
                            "• Ödemeniz banka hesabımızdan kontrol edildikten sonra Premium üyeliğiniz yönetici tarafından aktif edilecektir.\n" +
                            "• 'Ödemeyi Yaptım' butonuna basmak Premium üyeliği otomatik olarak aktif etmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            if (paymentMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F2937))
                        .padding(12.dp)
                ) {
                    Text(
                        text = paymentMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF81C784),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentRequestsHistoryCard(requests: List<PaymentRequest>) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr"))
    InfoCard(
        title = "📋 Ödeme Bildirimleriniz",
        accentColor = AppColors.NeonCyan
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            requests.forEach { req ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF161B22))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Ödeme Kodu: ${req.paymentCode}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        )
                        Text(
                            text = "${req.plan.name} (${req.expectedPrice}) • ${dateFormat.format(Date(req.createdAt))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                    }
                    val (pillColor, pillText) = when (req.status) {
                        PaymentRequestStatus.APPROVED -> Pair(Color(0xFF4CAF50), "ONAYLANDI")
                        PaymentRequestStatus.REJECTED -> Pair(Color(0xFFEF5350), "REDDEDİLDİ")
                        else -> Pair(Color(0xFFFF9800), "BEKLEMEDE")
                    }
                    StatusPill(label = pillText, containerColor = pillColor.copy(alpha = 0.2f), contentColor = pillColor)
                }
            }
        }
    }
}

@Composable
private fun AccountDatesCard(
    createdAt: Long,
    trialExpiresAt: Long,
    premiumExpiresAt: Long,
    premiumPlan: PremiumPlan
) {
    val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("tr"))
    InfoCard(
        title = "Hesap & Süre Bilgileri",
        accentColor = AppColors.NeonCyan
    ) {
        InfoRow(
            label = "Hesap Oluşturma",
            value = if (createdAt > 0) dateFormat.format(Date(createdAt)) else "Bilinmiyor"
        )
        if (premiumPlan == PremiumPlan.YEARLY && premiumExpiresAt > 0) {
            InfoRow(
                label = "Yıllık Premium Bitiş",
                value = dateFormat.format(Date(premiumExpiresAt))
            )
        } else if (premiumPlan == PremiumPlan.LIFETIME) {
            InfoRow(
                label = "Üyelik Modeli",
                value = "Süresiz (Ömür Boyu) Premium"
            )
        } else {
            InfoRow(
                label = "Üyelik Modeli",
                value = "Süresiz Ücretsiz Kullanım"
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(accentColor.copy(alpha = 0.5f), accentColor.copy(alpha = 0.15f))
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0D1117))
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(color = accentColor.copy(alpha = 0.2f))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(2f)
        )
    }
}

@Composable
private fun MembershipCard(
    icon: String,
    title: String,
    subtitle: String,
    accentColor: Color
) {
    InfoCard(title = title, accentColor = accentColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(icon, fontSize = 24.sp)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
    }
}

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component1() = first
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component2() = second
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component3() = third
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component4() = fourth

@Composable
private fun WhatsAppSupportCard(
    userId: String?,
    onOpenWhatsApp: (String?) -> Unit
) {
    val paymentCode = userId?.let { "KTV-" + it.takeLast(6).uppercase() }
    val whatsappGreen = Color(0xFF25D366)
    val whatsappHover = Color(0xFF1EBE5D)

    InfoCard(
        title = "💬 WhatsApp Destek & İletişim",
        accentColor = whatsappGreen
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TvButton(
                onClick = { onOpenWhatsApp(paymentCode) },
                modifier = Modifier.fillMaxWidth(),
                shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF16A34A),
                    focusedContainerColor = Color(0xFF22C55E),
                    contentColor = Color.White,
                    focusedContentColor = Color.White
                ),
                border = ButtonDefaults.border(
                    focusedBorder = androidx.tv.material3.Border(
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                        shape = RoundedCornerShape(10.dp)
                    )
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🟢", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "WhatsApp Destek",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Text(
                text = "Ödeme ve Premium üyelik işlemleri için bize WhatsApp üzerinden ulaşabilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun openWhatsAppSupport(context: Context, paymentCode: String?) {
    val phone = "905510616174"
    val baseMessage = "Merhaba, KaynanamTV Premium hakkında destek almak istiyorum."
    val fullMessage = if (!paymentCode.isNullOrBlank()) {
        "$baseMessage\nÖdeme Kodum: $paymentCode"
    } else {
        baseMessage
    }

    try {
        val encodedMessage = URLEncoder.encode(fullMessage, "UTF-8")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=$encodedMessage")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "WhatsApp açılamadı. Lütfen cihazınızda WhatsApp'ın kullanılabilir olduğundan emin olun.",
            Toast.LENGTH_LONG
        ).show()
    }
}

private data class FeatureComparisonItem(
    val title: String,
    val description: String? = null,
    val isIncluded: Boolean,
    val feature: Feature? = null
)

private fun getFreeTierFeatures(): List<FeatureComparisonItem> {
    val items = mutableListOf<FeatureComparisonItem>()
    items.add(
        FeatureComparisonItem(
            title = "Sınırsız Canlı TV Yayını",
            description = "Xtream Codes, M3U ve Stalker Portalları ile sınırsız canlı yayın izleme.",
            isIncluded = true
        )
    )
    items.add(
        FeatureComparisonItem(
            title = "Sınırsız Film (VOD) ve Dizi İzleme",
            description = "Tüm film ve dizi arşivlerine tam ve süresiz erişim.",
            isIncluded = true
        )
    )
    items.add(
        FeatureComparisonItem(
            title = "Standart Elektronik Program Rehberi (EPG)",
            description = "Yayın akışı, şimdi & sonra bilgisi ve standart kanal rehberi.",
            isIncluded = true
        )
    )
    items.add(
        FeatureComparisonItem(
            title = "Yerel Favoriler & İzleme Geçmişi",
            description = "Cihaz üzerinde yerel favori kanal ve izleme geçmişi yönetimi.",
            isIncluded = true
        )
    )
    items.add(
        FeatureComparisonItem(
            title = "Dahili Hızlı Oynatıcı (Media3 + FFmpeg)",
            description = "Donanım hızlandırma ve kararlı yayın motoru.",
            isIncluded = true
        )
    )

    Feature.entries
        .filter { it != Feature.TIMESHIFT }
        .forEach { feature ->
            items.add(
                FeatureComparisonItem(
                    title = feature.displayName,
                    description = feature.description,
                    isIncluded = EntitlementManager.canUseFeature(feature, isPremium = false),
                    feature = feature
                )
            )
        }
    return items
}

private fun getPremiumTierFeatures(): List<FeatureComparisonItem> {
    val items = mutableListOf<FeatureComparisonItem>()
    items.add(
        FeatureComparisonItem(
            title = "Tüm Ücretsiz Özellikler",
            description = "Sınırsız Canlı TV, Film ve Dizi kataloğuna tam ve süresiz erişim dahil.",
            isIncluded = true
        )
    )

    Feature.entries
        .filter { it != Feature.TIMESHIFT }
        .forEach { feature ->
            items.add(
                FeatureComparisonItem(
                    title = feature.displayName,
                    description = feature.description,
                    isIncluded = EntitlementManager.canUseFeature(feature, isPremium = true),
                    feature = feature
                )
            )
        }
    return items
}

@Composable
private fun MembershipPlanComparisonCard(
    isPremium: Boolean,
    premiumPlan: PremiumPlan
) {
    val freeFeatures = remember { getFreeTierFeatures() }
    val premiumFeatures = remember { getPremiumTierFeatures() }

    InfoCard(
        title = "Paket Hakları ve Özellik Karşılaştırması",
        accentColor = if (isPremium) Color(0xFFFFD700) else Color(0xFF10B981)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // ── 1. FREE TIER CARD ───────────────────────────────────────────
            PlanFeatureBox(
                planName = "Ücretsiz (Free) Paket",
                planBadge = if (!isPremium) "ŞU ANKİ AKTİF PAKETİNİZ 🟢" else "TEMEL SEVİYE",
                planBadgeContainerColor = if (!isPremium) Color(0xFF10B981).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                planBadgeContentColor = if (!isPremium) Color(0xFF10B981) else Color.LightGray,
                accentColor = Color(0xFF10B981),
                isActive = !isPremium,
                features = freeFeatures
            )

            // ── 2. PREMIUM TIER CARD ────────────────────────────────────────
            PlanFeatureBox(
                planName = if (isPremium && premiumPlan == PremiumPlan.LIFETIME) "👑 Sınırsız (Lifetime) Premium Paket"
                           else if (isPremium) "⭐ Yıllık Premium Paket"
                           else "💎 Premium Paket (Yıllık / Sınırsız)",
                planBadge = if (isPremium) "ŞU ANKİ AKTİF PAKETİNİZ 👑" else "YÜKSELTİLEBİLİR 💎",
                planBadgeContainerColor = if (isPremium) Color(0xFFFFD700).copy(alpha = 0.25f) else Color(0xFFFFD700).copy(alpha = 0.15f),
                planBadgeContentColor = if (isPremium) Color(0xFFFFD700) else Color(0xFFFFD700),
                accentColor = Color(0xFFFFD700),
                isActive = isPremium,
                features = premiumFeatures
            )
        }
    }
}

@Composable
private fun PlanFeatureBox(
    planName: String,
    planBadge: String,
    planBadgeContainerColor: Color,
    planBadgeContentColor: Color,
    accentColor: Color,
    isActive: Boolean,
    features: List<FeatureComparisonItem>
) {
    val borderColor = if (isActive) {
        accentColor.copy(alpha = 0.85f)
    } else if (accentColor == Color(0xFFFFD700)) {
        Color(0xFFFFD700).copy(alpha = 0.4f)
    } else {
        Color.White.copy(alpha = 0.15f)
    }

    val bgColor = if (isActive) {
        accentColor.copy(alpha = 0.07f)
    } else {
        Color(0xFF0D1117).copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = planName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) accentColor else if (accentColor == Color(0xFFFFD700)) Color(0xFFFFD700) else AppColors.TextSecondary
                )
                StatusPill(
                    label = planBadge,
                    containerColor = planBadgeContainerColor,
                    contentColor = planBadgeContentColor
                )
            }

            HorizontalDivider(
                color = if (isActive) accentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                features.forEach { item ->
                    val isIncluded = item.isIncluded
                    val iconText = when {
                        !isIncluded -> "🔒"
                        item.feature != null && isActive -> "🔓"
                        isActive -> "✅"
                        else -> "⭐"
                    }
                    val titleColor = if (isIncluded) AppColors.TextPrimary else AppColors.TextSecondary.copy(alpha = 0.6f)
                    val descColor = if (isIncluded) AppColors.TextSecondary else AppColors.TextTertiary.copy(alpha = 0.5f)
                    val fontWeight = if (isIncluded) FontWeight.SemiBold else FontWeight.Normal

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = iconText,
                            fontSize = 16.sp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = titleColor,
                                    fontWeight = fontWeight
                                )
                                if (isActive && item.feature != null) {
                                    StatusPill(
                                        label = "AKTİF",
                                        containerColor = Color(0xFFFFD700).copy(alpha = 0.2f),
                                        contentColor = Color(0xFFFFD700)
                                    )
                                }
                            }
                            if (!item.description.isNullOrBlank()) {
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = descColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

