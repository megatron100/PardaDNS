package com.example.pardadns.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.text.format.DateFormat
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.pardadns.ui.theme.StatusAmber
import java.text.NumberFormat
import java.util.Date
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.example.pardadns.R
import com.example.pardadns.domain.model.VpnState
import com.example.pardadns.service.TrafficSnapshot
import com.example.pardadns.ui.theme.StatusGreen

/** Pause durations offered in the header menu and on the Automation screen (minutes to label). */
val PauseOptions: List<Pair<Int, String>> = listOf(
    5 to "5 min",
    15 to "15 min",
    60 to "1 hour",
    480 to "8 hours",
)

/**
 * Adaptive shield header that morphs between expanded (full card with halo, stats)
 * and collapsed (compact sticky pill) based on [collapseProgress] 0f..1f.
 */
@Composable
fun ShieldHeader(
    vpnState: VpnState,
    trafficStats: TrafficSnapshot = TrafficSnapshot.EMPTY,
    onTap: () -> Unit = {},
    collapseProgress: Float = 0f,
    modifier: Modifier = Modifier,
    pauseUnlocked: Boolean = false,
    onPause: (minutes: Int) -> Unit = {},
    onResume: () -> Unit = {},
    onProRequired: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val context = LocalContext.current

    // ── Shield color ──────────────────────────────────────────────────────────
    val shieldColor by animateColorAsState(
        targetValue = when (vpnState) {
            is VpnState.Connected    -> StatusGreen
            is VpnState.Connecting   -> Color(0xFFF57F17)
            is VpnState.Error        -> Color(0xFFC62828)
            is VpnState.Disconnected -> Color(0xFF546E7A)
            is VpnState.Paused       -> StatusAmber
        },
        animationSpec = tween(600),
        label = "shieldColor",
    )

    val pausedUntil = (vpnState as? VpnState.Paused)?.let {
        remember(it.untilMillis) { DateFormat.getTimeFormat(context).format(Date(it.untilMillis)) }
    }
    val statusText = when (vpnState) {
        is VpnState.Connected    -> stringResource(R.string.status_connected, vpnState.providerName)
        is VpnState.Connecting   -> stringResource(R.string.status_connecting)
        is VpnState.Error        -> stringResource(R.string.status_error)
        is VpnState.Disconnected -> stringResource(R.string.status_disconnected)
        is VpnState.Paused       -> stringResource(
            if (vpnState.bySchedule) R.string.status_paused_schedule else R.string.status_paused,
        )
    }
    val subText = when (vpnState) {
        is VpnState.Connected    -> stringResource(R.string.sub_connected)
        is VpnState.Connecting   -> stringResource(R.string.sub_connecting)
        is VpnState.Error        -> stringResource(R.string.sub_disconnected)
        is VpnState.Disconnected -> stringResource(R.string.sub_disconnected)
        is VpnState.Paused       -> stringResource(R.string.sub_paused, pausedUntil.orEmpty())
    }
    val compactText = when (vpnState) {
        is VpnState.Connected    -> statusText
        is VpnState.Connecting   -> stringResource(R.string.status_connecting)
        is VpnState.Error        -> stringResource(R.string.status_error)
        is VpnState.Disconnected -> "Tap to connect"
        is VpnState.Paused       -> "Paused · until $pausedUntil"
    }

    // ── Pulse animation ──────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val outerAlpha = if (vpnState is VpnState.Connecting) pulseAlpha else 0.12f
    val isConnected = vpnState is VpnState.Connected

    // ── Risk icon bobbing ────────────────────────────────────────────────────
    val iconTransition = rememberInfiniteTransition(label = "riskIcons")
    val bob1 by iconTransition.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "bob1",
    )
    val bob2 by iconTransition.animateFloat(
        initialValue = 2f, targetValue = -2f,
        animationSpec = infiniteRepeatable(tween(1450), RepeatMode.Reverse), label = "bob2",
    )
    val bob3 by iconTransition.animateFloat(
        initialValue = -1f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1320), RepeatMode.Reverse), label = "bob3",
    )

    // ── Dimension interpolation based on collapse progress ───────────────────
    // Halo & icon sizing
    val expandedHaloSize = (screenWidth * 0.28f).coerceIn(80.dp, 104.dp)
    val expandedInnerHalo = expandedHaloSize * 0.75f
    val expandedIconSize = expandedHaloSize * 0.43f
    val collapsedIconSize = 22.dp

    // Fade-out thresholds: expanded content fades before halfway
    val expandedContentAlpha = (1f - (collapseProgress / 0.35f)).coerceIn(0f, 1f)
    // Collapsed content fades in after 60%
    val collapsedContentAlpha = ((collapseProgress - 0.6f) / 0.4f).coerceIn(0f, 1f)

    Box(
        modifier = modifier.clickable { onTap() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = lerp(8.dp, 4.dp, collapseProgress)),
        ) {
            // ── EXPANDED CONTENT ─────────────────────────────────────────
            if (expandedContentAlpha > 0f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = expandedContentAlpha }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Halo + shield icon
                    Box(
                        modifier = Modifier
                            .size(expandedHaloSize)
                            .background(shieldColor.copy(alpha = outerAlpha), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(expandedInnerHalo)
                                .background(shieldColor.copy(alpha = 0.22f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = statusText,
                                tint = shieldColor,
                                modifier = Modifier.size(expandedIconSize),
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))

                    if (isConnected) {
                        // Blocked today — same footprint as the risk-icon row
                        BlockedTodayPill(
                            count = trafficStats.blockedToday,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 18.dp),
                        )
                    } else if (vpnState is VpnState.Paused) {
                        // Resume pill — same footprint as the risk-icon row
                        Surface(
                            onClick = onResume,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 18.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = StatusAmber.copy(alpha = 0.15f),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = StatusAmber,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Resume now",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StatusAmber,
                                )
                            }
                        }
                    } else {
                        // Risk icons
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 18.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp).offset(y = bob1.dp),
                                )
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp).offset(y = bob2.dp),
                                )
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp).offset(y = bob3.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Pause chip (top-right, connected only) ───────────────────
            if (isConnected && expandedContentAlpha > 0f) {
                PauseChip(
                    pauseUnlocked = pauseUnlocked,
                    onPause = onPause,
                    onProRequired = onProRequired,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer { alpha = expandedContentAlpha },
                )
            }

            // ── COLLAPSED CONTENT (compact pill) ─────────────────────────
            if (collapsedContentAlpha > 0f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = collapsedContentAlpha }
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    // Small color-tinted circle with shield icon
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(shieldColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = shieldColor,
                            modifier = Modifier.size(collapsedIconSize),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = compactText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Blocked-today count when connected
                    if (isConnected) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${formatCount(trafficStats.blockedToday)} blocked",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusGreen,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(shieldColor, CircleShape),
                    )
                }
            }
        }
    }
}

// ── Pause chip + duration menu ────────────────────────────────────────────────

@Composable
private fun PauseChip(
    pauseUnlocked: Boolean,
    onPause: (Int) -> Unit,
    onProRequired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { if (pauseUnlocked) menuOpen = true else onProRequired() },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Pause",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!pauseUnlocked) {
                    Spacer(Modifier.width(4.dp))
                    ProBadge()
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Text(
                text = "Pause protection for",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            for ((minutes, label) in PauseOptions) {
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        menuOpen = false
                        onPause(minutes)
                    },
                )
            }
        }
    }
}

// ── Blocked-today pill ────────────────────────────────────────────────────────

/** One line: "1,240 ads & trackers blocked today". */
@Composable
private fun BlockedTodayPill(count: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(StatusGreen.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = NumberFormat.getIntegerInstance().format(count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = StatusGreen,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "ads & trackers blocked today",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000f)}M"
    n >= 1_000     -> "${"%.1f".format(n / 1_000f)}K"
    else           -> n.toString()
}
