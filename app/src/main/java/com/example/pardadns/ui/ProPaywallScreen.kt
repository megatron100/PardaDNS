package com.example.pardadns.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VerifiedUser
import com.example.pardadns.ui.theme.ProGold
import com.example.pardadns.ui.theme.ProIndigo
import com.example.pardadns.ui.theme.ProViolet
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ProGradientStart = ProIndigo
private val ProGradientEnd = ProViolet
private val ProAccent = ProIndigo
private val GoldBadge = ProGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPaywallScreen(
    title: String,
    onBack: () -> Unit,
    onUnlockPro: () -> Unit = {},
    isPro: Boolean = false,
    price: String? = null,
    pending: Boolean = false,
    onRestore: () -> Unit = {},
    messages: Flow<String> = emptyFlow(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(messages) {
        messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = contentPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Hero section with gradient background ────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ProGradientStart.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Pulsing lock badge
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .scale(pulseScale)
                                .alpha(glowAlpha)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            ProGradientStart.copy(alpha = 0.3f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(ProGradientStart, ProGradientEnd),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // PRO badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(GoldBadge, Color(0xFFFF8F00)),
                                ),
                            )
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "PRO",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 2.sp,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Unlock Premium Features",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Smart filters, schedules, one-tap blocking\nand total control over your DNS privacy.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Feature cards ────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Everything in Pro",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                ProFeatureRow(
                    icon = Icons.Outlined.FilterList,
                    title = "Filter Categories",
                    subtitle = "One-tap blocking of ads, malware & adult sites",
                    iconTint = Color(0xFFC62828),
                    iconBg = Color(0xFFFFEBEE),
                )
                ProFeatureRow(
                    icon = Icons.Outlined.CloudDownload,
                    title = "Blocklist Import",
                    subtitle = "OISD, StevenBlack & more — or your own URL/file",
                    iconTint = Color(0xFF1565C0),
                    iconBg = Color(0xFFE3F2FD),
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Schedule,
                    title = "Schedules, Pause & Auto-connect",
                    subtitle = "Protect on your hours, pause in one tap",
                    iconTint = Color(0xFF7B1FA2),
                    iconBg = Color(0xFFF3E5F5),
                )
                ProFeatureRow(
                    icon = Icons.Outlined.VerifiedUser,
                    title = "Pro DNS Providers",
                    subtitle = "Family-safe and extra-filtering resolvers",
                    iconTint = Color(0xFF2E7D32),
                    iconBg = Color(0xFFE8F5E9),
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Shield,
                    title = "Custom Blocklist",
                    subtitle = "Block any domain you want manually",
                    iconTint = Color(0xFFEC4899),
                    iconBg = Color(0xFFFDF2F8),
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Apps,
                    title = "App Filter",
                    subtitle = "Choose which apps use the secure tunnel",
                    iconTint = Color(0xFF3949AB),
                    iconBg = Color(0xFFE8EAF6),
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Speed,
                    title = "DNS Performance",
                    subtitle = "Compare response times across providers",
                    iconTint = Color(0xFFF59E0B),
                    iconBg = Color(0xFFFFFBEB),
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Dns,
                    title = "Advanced DNS Logs",
                    subtitle = "Full query history with search & filter",
                    iconTint = Color(0xFF0EA5E9),
                    iconBg = Color(0xFFF0F9FF),
                )
                ProFeatureRow(
                    icon = Icons.Outlined.Block,
                    title = "Block from Stats",
                    subtitle = "Block any domain right from your query stats",
                    iconTint = Color(0xFFEF4444),
                    iconBg = Color(0xFFFEF2F2),
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── CTA Button ───────────────────────────────────────────────
            Button(
                onClick = onUnlockPro,
                enabled = !isPro && !pending,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                ),
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ProGradientStart, ProGradientEnd),
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            isPro -> "Pro is active"
                            price != null -> "Upgrade to Pro · $price"
                            else -> "Upgrade to Pro"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            if (!isPro) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "One-time purchase · yours forever",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Restore purchases link ───────────────────────────────────
            Text(
                text = "Restore purchases",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRestore)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            if (pending && !isPro) {
                Spacer(Modifier.height(16.dp))
                PendingNote()
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Shown while Play is waiting for payment (e.g. cash or bank transfer). */
@Composable
private fun PendingNote() {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GoldBadge.copy(alpha = 0.4f),
                        Color(0xFFFF8F00).copy(alpha = 0.3f),
                    ),
                ),
                shape = RoundedCornerShape(16.dp),
            )
            .background(GoldBadge.copy(alpha = 0.06f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Payment pending. Pro unlocks once Google Play confirms it.",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFD97706),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    iconBg: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = ProAccent.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
