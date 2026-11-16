package com.example.pardadns.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pardadns.data.model.DnsProvider

private fun providerIcon(id: String): ImageVector = when (id) {
    "adguard"             -> Icons.Default.Shield       // green shield — ad blocking
    "cloudflare"          -> Icons.Default.Cloud        // orange cloud — Cloudflare brand
    "cloudflare-security" -> Icons.Default.Security     // lock+shield — malware blocking
    "cloudflare-family"   -> Icons.Default.GppGood      // family-safe
    "quad9"               -> Icons.Default.Dns          // DNS service
    "mullvad"             -> Icons.Default.Lock         // padlock — zero-log privacy
    "mullvad-adblock"     -> Icons.Default.Lock
    "nextdns"             -> Icons.Default.VerifiedUser // verified/customizable
    "google"              -> Icons.Default.Public       // globe — public DNS
    else                  -> Icons.Default.Shield
}

private fun providerAccent(id: String): Color = when (id) {
    "adguard"             -> Color(0xFF67B346)   // AdGuard official green
    "cloudflare"          -> Color(0xFFF48120)   // Cloudflare official orange
    "cloudflare-security" -> Color(0xFFF48120)   // same brand, security variant
    "cloudflare-family"   -> Color(0xFFF48120)   // same brand, family variant
    "quad9"               -> Color(0xFF2F4EA2)   // Quad9 official navy blue
    "mullvad"             -> Color(0xFF44AD8E)   // Mullvad official teal
    "mullvad-adblock"     -> Color(0xFF44AD8E)
    "nextdns"             -> Color(0xFF2951CC)   // NextDNS official blue
    "google"              -> Color(0xFF4285F4)   // Google blue
    else                  -> Color(0xFF546E7A)
}

private const val RECOMMENDED_PROVIDER_ID = "adguard"

/** Small tonal pill next to the provider name ("Recommended", "Fastest"). */
@Composable
private fun ProviderBadge(text: String, icon: ImageVector, lightColor: Color, darkColor: Color) {
    val color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) darkColor else lightColor
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderCard(
    provider: DnsProvider,
    isSelected: Boolean,
    onSelect: () -> Unit,
    latencyMs: Long? = null,
    isFastest: Boolean = false,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val cornerRadius = (screenWidth * 0.04f).coerceIn(10.dp, 16.dp)
    val cardHorizontalPadding = (screenWidth * 0.035f).coerceIn(10.dp, 16.dp)
    val cardVerticalPadding = (screenWidth * 0.025f).coerceIn(8.dp, 12.dp)
    val iconSize = (screenWidth * 0.075f).coerceIn(26.dp, 34.dp)
    val contentGap = (screenWidth * 0.025f).coerceIn(6.dp, 12.dp)

    val accent = providerAccent(provider.id)
    val cardColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(300),
        label = "cardColor",
    )
    Card(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = cardHorizontalPadding,
                vertical = cardVerticalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = providerIcon(provider.id),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.width(contentGap))
            Column(modifier = Modifier.weight(1f)) {
                // Badges wrap under the name when they don't fit on one line.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (provider.proOnly) ProBadge()
                    if (provider.id == RECOMMENDED_PROVIDER_ID) {
                        ProviderBadge(
                            text = "Recommended",
                            icon = Icons.Default.Star,
                            lightColor = Color(0xFF8A5A00),
                            darkColor = Color(0xFFFFC94D),
                        )
                    }
                    if (isFastest) {
                        ProviderBadge(
                            text = "Fastest",
                            icon = Icons.Default.Bolt,
                            lightColor = Color(0xFF0B57D0),
                            darkColor = Color(0xFF9ECAFF),
                        )
                    }
                }
                Text(
                    text = provider.dohUrl
                        ?.removePrefix("https://")
                        ?.removeSuffix("/dns-query")
                        ?.removeSuffix("/")
                        ?: provider.primaryIp,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = provider.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // ── Right side: remove + checkmark + latency ──────────────────────
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                if (onRemove != null) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (isSelected) {
                    Icon(
                        imageVector        = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(20.dp),
                    )
                }
                if (latencyMs != null) {
                    Text(
                        text  = "$latencyMs ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            latencyMs < 50  -> Color(0xFF43A047)
                            latencyMs < 150 -> Color(0xFFFFB300)
                            else            -> Color(0xFFE53935)
                        },
                    )
                }
            }
        }
    }
}
