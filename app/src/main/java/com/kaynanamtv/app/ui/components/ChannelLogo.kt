package com.kaynanamtv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.kaynanamtv.app.ui.design.AppColors
import java.util.Locale

private val LOGO_FALLBACK_GRADIENTS = listOf(
    listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)), // Indigo / Blue
    listOf(Color(0xFF581C87), Color(0xFF8B5CF6)), // Purple / Violet
    listOf(Color(0xFF065F46), Color(0xFF10B981)), // Emerald / Teal
    listOf(Color(0xFF92400E), Color(0xFFF59E0B)), // Amber
    listOf(Color(0xFF9D174D), Color(0xFFEC4899)), // Rose / Pink
    listOf(Color(0xFF155E75), Color(0xFF06B6D4)), // Cyan
    listOf(Color(0xFF86198F), Color(0xFFD946EF)), // Fuchsia
    listOf(Color(0xFF1E293B), Color(0xFF334155))  // Slate
)

internal fun getChannelFallbackBrush(name: String): Brush {
    val clean = name.trim().lowercase(Locale.ROOT)
    val hash = kotlin.math.abs(clean.hashCode())
    val colors = LOGO_FALLBACK_GRADIENTS[hash % LOGO_FALLBACK_GRADIENTS.size]
    return Brush.linearGradient(colors)
}

internal fun extractCleanChannelName(rawName: String): String {
    if (rawName.isBlank()) return "TV"

    var cleaned = rawName.trim()

    // 1. Strip leading channel numbers e.g. "12 ", "03 - ", "1. "
    cleaned = cleaned.replace(Regex("^\\s*\\d{1,4}\\s*[-:.)]\\s*"), "")
    cleaned = cleaned.replace(Regex("^\\s*\\d{1,4}\\s+"), "")

    // 2. Strip leading decorative emojis / non-letter/digit symbols
    cleaned = cleaned.replace(Regex("^[^\\p{L}\\p{N}]+"), "")
    cleaned = cleaned.replace(Regex("[^\\p{L}\\p{N}]+$"), "")

    // 3. Strip country/language prefixes (e.g. TR:, TR-, [TR], (TR), TUR:, EN:, DE:, FR:, AZ:, EX-YU:, etc.)
    cleaned = cleaned.replace(Regex("^(?:TR|TUR|EN|DE|FR|AZ|AR|NL|IT|ES|RU|US|UK|EX-YU)\\s*[:|\\-\\])]\\s*", RegexOption.IGNORE_CASE), "")
    cleaned = cleaned.replace(Regex("^\\[(?:TR|TUR|EN|DE|FR|AZ|AR|NL|IT|ES|RU|US|UK|EX-YU)\\]\\s*", RegexOption.IGNORE_CASE), "")
    cleaned = cleaned.replace(Regex("^\\((?:TR|TUR|EN|DE|FR|AZ|AR|NL|IT|ES|RU|US|UK|EX-YU)\\)\\s*", RegexOption.IGNORE_CASE), "")

    // 4. Strip leftover numbers after country tag e.g. "TR: 03 TRT 1"
    cleaned = cleaned.replace(Regex("^\\s*\\d{1,4}\\s*[-:.)]\\s*"), "")
    cleaned = cleaned.replace(Regex("^\\s*\\d{1,4}\\s+"), "")

    // 5. Strip any leftover symbols at start
    cleaned = cleaned.replace(Regex("^[^\\p{L}\\p{N}]+"), "").trim()

    // 6. Strip trailing quality tokens (case-insensitive)
    val qualityRegex = Regex("(?i)(?:\\s*[-_|/]\\s*|\\s+)(?:FHD|UHD|QHD|4K|8K|HD|SD|HEVC|H\\.?265|H\\.?264|RAW|1080P|720P|576P|50FPS|60FPS|\\+18)\\b")
    repeat(3) {
        cleaned = cleaned.replace(qualityRegex, "").trim()
    }

    // Strip any trailing symbols left after quality removal
    cleaned = cleaned.replace(Regex("[^\\p{L}\\p{N}]+$"), "").trim()

    return cleaned.ifBlank { "TV" }
}

internal fun channelInitials(name: String): String = extractCleanChannelName(name)

@Composable
fun ChannelLogoBadge(
    channelName: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = AppColors.SurfaceEmphasis,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    textColor: Color = Color.White
) {
    val cleanLogoUrl = logoUrl?.trim()?.takeIf { it.isNotBlank() }
    android.util.Log.i("ChannelLogo", "[RENDER] name='$channelName' rawLogoUrl='$logoUrl' cleanUrl='$cleanLogoUrl'")
    val model = rememberCrossfadeImageModel(cleanLogoUrl)
    var isImageLoaded by remember(cleanLogoUrl) { mutableStateOf(false) }
    var isImageError by remember(cleanLogoUrl) { mutableStateOf(false) }
    val showFallback = model == null || isImageError || !isImageLoaded

    val cleanName = remember(channelName) { extractCleanChannelName(channelName) }
    val fallbackBrush = remember(channelName) { getChannelFallbackBrush(channelName) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (isImageLoaded) Brush.linearGradient(listOf(backgroundColor, backgroundColor))
                else fallbackBrush
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showFallback) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = cleanName,
                    style = textStyle.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = channelName,
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    android.util.Log.i("ChannelLogo", "[COIL_OK] loaded for '$channelName' url='$cleanLogoUrl'")
                    isImageLoaded = true
                    isImageError = false
                },
                onError = { state ->
                    android.util.Log.w("ChannelLogo", "[COIL_ERR] failed for '$channelName' url='$cleanLogoUrl' err=${state.result.throwable.message}", state.result.throwable)
                    isImageError = true
                    isImageLoaded = false
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            )
        }
    }
}