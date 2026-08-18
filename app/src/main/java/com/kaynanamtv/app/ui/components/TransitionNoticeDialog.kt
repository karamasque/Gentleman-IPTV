package com.kaynanamtv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.ui.interaction.TvButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitionNoticeDialog(
    onDismiss: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val borderBrush = Brush.linearGradient(
            colors = listOf(AppColors.Brand, AppColors.NeonCyan)
        )
        Box(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0D111A))
                .border(1.5.dp, borderBrush, RoundedCornerShape(24.dp))
                .padding(28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(AppColors.Brand, AppColors.NeonCyan)
                            )
                        )
                ) {
                    Text("⭐", fontSize = 28.sp)
                }

                Text(
                    text = "KaynanamTV Premium'a Geçiyor",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "KaynanamTV'yi uzun vadede geliştirmeye devam edebilmek ve daha stabil bir kullanım deneyimi sunabilmek için Premium üyelik sistemine geçiyoruz.\n\n" +
                        "KaynanamTV kullanıcı sayısı ve kullanılan özellikler arttıkça sunucu, veritabanı, senkronizasyon, bakım ve geliştirme altyapısının maliyetleri de artmaktadır.\n\n" +
                        "KaynanamTV herhangi bir IPTV yayını veya IPTV aboneliği satmaz. Uygulama, kullanıcıların kendi IPTV sağlayıcılarını ve hesaplarını kullanmalarına ve yönetmelerine olanak sağlayan bir IPTV oynatıcı platformudur.\n\n" +
                        "Premium üyelik sistemi sayesinde gelişmiş özellikleri geliştirmeye devam edebilir, altyapımızı sürdürebilir ve KaynanamTV'yi daha stabil hale getirebiliriz.\n\n" +
                        "Bu geçiş sırasında mevcut kullanıcılarımızın mağdur olmaması için tüm uygun mevcut hesaplara son kez 7 günlük Premium kullanım hakkı tanımlıyoruz.\n\n" +
                        "7 günlük süreniz sona erdiğinde hesabınız veya IPTV bilgileriniz silinmeyecektir. Hesabınız Free üyelik seviyesine geçecek ve Premium özellikler için üyelik satın alabileceksiniz.\n\n" +
                        "💎 Yıllık Premium — 349 TL\n" +
                        "👑 Sınırsız Premium — 749 TL\n\n" +
                        "Desteğiniz için teşekkür ederiz.\n\n" +
                        "KaynanamTV",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Start,
                    lineHeight = 22.sp
                )

                Spacer(Modifier.height(8.dp))

                TvButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.colors(
                        containerColor = AppColors.Brand,
                        focusedContainerColor = AppColors.BrandStrong
                    )
                ) {
                    Text(
                        text = "Anladım, Teşekkürler",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
