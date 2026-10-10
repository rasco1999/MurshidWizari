package com.muriq.murshid.iosparity.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Derived from the iOS patches/ThemeV34.swift rather than a prior Android application.
val MurshidNavy = Color(0xFF0B1F33)
val MurshidBlue = Color(0xFF2D6CDF)
val MurshidGold = Color(0xFFF5B83D)
private val DarkSurface = Color(0xFF17283D)
private val LightBackground = Color(0xFFF2F4F8)

@Composable
fun MurshidTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF8AB3FF), onPrimary = MurshidNavy,
        background = Color(0xFF0D1724), onBackground = Color(0xFFF2F6FB),
        surface = DarkSurface, onSurface = Color(0xFFF2F6FB),
        surfaceVariant = Color(0xFF22354C), onSurfaceVariant = Color(0xFFC0D0E1)
    ) else lightColorScheme(
        primary = MurshidBlue, onPrimary = Color.White,
        background = LightBackground, onBackground = MurshidNavy,
        surface = Color.White, onSurface = MurshidNavy,
        surfaceVariant = Color(0xFFEEF2FA), onSurfaceVariant = Color(0xFF52657A)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun MurshidCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun PrimaryAction(
    title: String,
    action: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = action,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Brush.linearGradient(listOf(MurshidBlue, Color(0xFF1748AE)))),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF9EACBF),
            disabledContentColor = Color.White
        ),
        shape = RoundedCornerShape(17.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryAction(
    title: String,
    action: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = action,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun PrimaryAction(
    title: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    PrimaryAction(title, action = onClick, enabled = enabled, modifier = modifier)
}

@Composable
fun SecondaryAction(
    title: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SecondaryAction(title, action = onClick, enabled = enabled, modifier = modifier)
}

@Composable
fun SectionTitle(kicker: String, title: String, subtitle: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(kicker, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SmallPill(text: String, color: Color = MurshidBlue) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.11f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun HeroPanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(MurshidNavy, Color(0xFF0D3B85), MurshidBlue)
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(subtitle, fontSize = 14.sp, color = Color(0xFFDCE8FC))
        body()
    }
}

@Composable
fun LoadingPanel(label: String = "جارٍ تحميل البيانات…") {
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyPanel(title: String, description: String = "", action: (@Composable () -> Unit)? = null) {
    MurshidCard {
        Text(title, fontWeight = FontWeight.Bold)
        if (description.isNotBlank()) Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
