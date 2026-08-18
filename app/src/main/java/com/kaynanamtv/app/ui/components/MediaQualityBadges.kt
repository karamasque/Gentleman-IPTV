package com.kaynanamtv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaynanamtv.app.ui.design.AppColors.Brand as Primary

@Composable
fun GlassmorphicBadge(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Primary,
    isHighlighted: Boolean = false
) {
    val bgBrush = if (isHighlighted) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.35f),
                accentColor.copy(alpha = 0.15f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.05f)
            )
        )
    }

    val borderBrush = if (isHighlighted) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.70f),
                accentColor.copy(alpha = 0.30f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    }

    Box(
        modifier = modifier
            .background(
                brush = bgBrush,
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = if (isHighlighted) Color.White else Color.White.copy(alpha = 0.90f)
        )
    }
}

@Composable
fun GlassmorphicQualityRow(
    modifier: Modifier = Modifier,
    qualityLabel: String? = "4K ULTRA HD",
    fpsLabel: String? = "60 FPS",
    audioLabel: String? = "DOLBY 5.1",
    ratingLabel: String? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        qualityLabel?.let {
            GlassmorphicBadge(
                text = it,
                isHighlighted = true,
                accentColor = Color(0xFF6366F1)
            )
        }
        fpsLabel?.let {
            GlassmorphicBadge(
                text = it,
                accentColor = Color(0xFF10B981)
            )
        }
        audioLabel?.let {
            GlassmorphicBadge(
                text = it,
                accentColor = Color(0xFFF59E0B)
            )
        }
        ratingLabel?.let {
            GlassmorphicBadge(
                text = "⭐ $it",
                isHighlighted = true,
                accentColor = Color(0xFFEAB308)
            )
        }
    }
}
