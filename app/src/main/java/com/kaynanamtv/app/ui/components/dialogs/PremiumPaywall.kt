package com.kaynanamtv.app.ui.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaynanamtv.app.ui.theme.TextPrimary
import com.kaynanamtv.app.ui.theme.TextSecondary
import com.kaynanamtv.domain.model.Feature

@Composable
fun PremiumPaywall(
    feature: Feature,
    onNavigateToMembership: () -> Unit,
    onDismiss: () -> Unit
) {
    PremiumDialog(
        title = "🔒 Premium Özellik",
        subtitle = feature.displayName,
        onDismissRequest = onDismiss,
        widthFraction = 0.45f,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${feature.displayName} özelliği KaynanamTV Premium üyelerine özeldir.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        },
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PremiumDialogFooterButton(
                    label = "Premium'a Geç",
                    onClick = {
                        onDismiss()
                        onNavigateToMembership()
                    },
                    emphasized = true
                )
                PremiumDialogFooterButton(
                    label = "Kapat",
                    onClick = onDismiss
                )
            }
        }
    )
}
