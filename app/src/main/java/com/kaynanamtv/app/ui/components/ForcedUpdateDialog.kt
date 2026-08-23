package com.kaynanamtv.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.ui.interaction.TvButton

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForcedUpdateDialog(
    currentVersionName: String,
    requiredVersionName: String = "1.0.67",
    onUpdateClick: () -> Unit
) {
    val context = LocalContext.current
    val exitApp: () -> Unit = {
        val activity = context.findActivity()
        if (activity != null) {
            activity.finishAffinity()
        } else {
            kotlin.system.exitProcess(0)
        }
    }

    BackHandler(enabled = true) {
        exitApp()
    }

    BasicAlertDialog(
        onDismissRequest = { /* Non-dismissible: cannot be dismissed via back button or outside click */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val borderBrush = Brush.linearGradient(
            colors = listOf(Color(0xFFEF5350), AppColors.Brand)
        )
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.8f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F0B10))
                .border(1.5.dp, borderBrush, RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF5350).copy(alpha = 0.2f))
                ) {
                    Text("🔄", fontSize = 28.sp)
                }

                Text(
                    text = "Güncelleme Gerekli",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "KaynanamTV'nin bu sürümü artık desteklenmiyor.\n\n" +
                        "Devam edebilmek için KaynanamTV $requiredVersionName veya daha yeni bir sürüme güncellemeniz gerekiyor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                TvButton(
                    onClick = onUpdateClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.colors(
                        containerColor = AppColors.Brand,
                        focusedContainerColor = AppColors.BrandStrong
                    )
                ) {
                    Text(
                        text = "🚀 GÜNCELLE",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                TvButton(
                    onClick = exitApp,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF3F1D1D).copy(alpha = 0.6f),
                        focusedContainerColor = Color(0xFFDC2626),
                        contentColor = Color(0xFFFCA5A5),
                        focusedContentColor = Color.White
                    )
                ) {
                    Text(
                        text = "🚪 UYGULAMADAN ÇIK",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
