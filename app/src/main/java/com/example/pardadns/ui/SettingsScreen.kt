package com.example.pardadns.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Schedule
import com.example.pardadns.ui.components.ProBadge
import com.example.pardadns.ui.theme.ProIndigo
import com.example.pardadns.ui.theme.ProViolet
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.Settings
import com.example.pardadns.ui.theme.StatusGreen
import com.example.pardadns.ui.theme.ThemeMode
import androidx.compose.material3.ExperimentalMaterial3Api

private val AccentBlue = Color(0xFF1565C0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAppFilter: () -> Unit,
    onNavigateToBlocklist: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPro: () -> Unit = {},
    onNavigateToAutomation: () -> Unit = {},
    showProButton: Boolean = false,
    isPro: Boolean = false,
    onDebugProChange: ((Boolean) -> Unit)? = null,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    enforceDoh: Boolean = false,
    onEnforceDohChange: (Boolean) -> Unit = {},
    dnsMonitoring: Boolean = false,
    onDnsMonitoringChange: (Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current

    // Read version name from PackageManager
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }



    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(
                    top    = innerPadding.calculateTopPadding() + contentPadding.calculateTopPadding() + 24.dp,
                    bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding() + 16.dp,
                    start  = 20.dp,
                    end    = 20.dp,
                ),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = "Settings",
                    style      = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onBackground,
                )
                if (isPro) {
                    Spacer(Modifier.width(10.dp))
                    ProBadge()
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text  = "Configure the high-level security parameters of Panda DNS. All changes are applied instantly to your secure DNS tunnel.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(28.dp))

            // ── PRO upgrade banner ────────────────────────────────────────
            if (showProButton) {
                ProUpgradeBanner(onClick = onNavigateToPro)
                Spacer(Modifier.height(24.dp))
            }

            // ── CUSTOMIZATION section ──────────────────────────────────────
            SectionLabel("CUSTOMIZATION")
            Spacer(Modifier.height(8.dp))
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier  = Modifier.fillMaxWidth(),
            ) {
                NavigationRow(
                    icon        = Icons.Default.Apps,
                    iconBg      = Color(0xFFE8EAF6),
                    iconTint    = Color(0xFF3949AB),
                    title       = "App Filter",
                    subtitle    = "Control which apps use the secure tunnel",
                    onClick     = onNavigateToAppFilter,
                    showProBadge = !isPro,
                )
                RowDivider()
                NavigationRow(
                    icon        = Icons.Default.Block,
                    iconBg      = Color(0xFFFFEBEE),
                    iconTint    = Color(0xFFC62828),
                    title       = "Blocklists & Filters",
                    subtitle    = "Categories, ready-made lists, imports & custom domains",
                    onClick     = onNavigateToBlocklist,
                    showProBadge = !isPro,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── CONNECTION section ────────────────────────────────────────────
            SectionLabel("CONNECTION")
            Spacer(Modifier.height(8.dp))
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier  = Modifier.fillMaxWidth(),
            ) {
                ToggleRow(
                    icon        = Icons.Default.Lock,
                    iconBg      = Color(0xFFE8F5E9),
                    iconTint    = StatusGreen,
                    title       = "Enforce DNS-over-HTTPS",
                    subtitle    = "Only allow providers with DoH support",
                    checked     = enforceDoh,
                    onCheckedChange = onEnforceDohChange,
                )
                RowDivider()
                NavigationRow(
                    icon     = Icons.Default.Schedule,
                    iconBg   = Color(0xFFF3E5F5),
                    iconTint = Color(0xFF7B1FA2),
                    title    = "Schedule & Auto-connect",
                    subtitle = "Protection hours, pause and connect on start",
                    onClick  = onNavigateToAutomation,
                    showProBadge = !isPro,
                )
                RowDivider()
                // Always On Protection — system-controlled; tapping opens VPN settings
                NavigationRow(
                    icon     = Icons.Default.PhonelinkLock,
                    iconBg   = Color(0xFFE3F2FD),
                    iconTint = Color(0xFF1565C0),
                    title    = "Always on Protection",
                    subtitle = "Enable in system VPN settings",
                    onClick  = {
                        context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                    },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── MONITORING section (hidden when already unlocked) ─────────────
            if (!dnsMonitoring) {
                SectionLabel("MONITORING")
                Spacer(Modifier.height(8.dp))
                Card(
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier  = Modifier.fillMaxWidth(),
                ) {
                    ToggleRow(
                        icon        = Icons.Outlined.WorkspacePremium,
                        iconBg      = Color(0xFFFFF3E0),
                        iconTint    = Color(0xFFE65100),
                        title       = "DNS Monitoring",
                        subtitle    = "Record detailed query stats & latency",
                        checked     = dnsMonitoring,
                        onCheckedChange = onDnsMonitoringChange,
                    )
                }

                Spacer(Modifier.height(24.dp))
            }

            // ── APPEARANCE section ────────────────────────────────────────────
            SectionLabel("APPEARANCE")
            Spacer(Modifier.height(8.dp))
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier  = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsIcon(
                        icon     = when (themeMode) {
                            ThemeMode.DARK   -> Icons.Default.DarkMode
                            ThemeMode.LIGHT  -> Icons.Default.LightMode
                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                        },
                        iconBg   = Color(0xFFFFF8E1),
                        iconTint = Color(0xFFF57F17),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = "Theme",
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        val options = listOf(ThemeMode.LIGHT, ThemeMode.SYSTEM, ThemeMode.DARK)
                        val labels  = listOf("Light", "System", "Dark")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            options.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = themeMode == mode,
                                    onClick  = { onThemeModeChange(mode) },
                                    shape    = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                    label    = { Text(labels[index], style = MaterialTheme.typography.labelMedium) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── INFORMATION section ───────────────────────────────────────────
            SectionLabel("INFORMATION")
            Spacer(Modifier.height(8.dp))
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier  = Modifier.fillMaxWidth(),
            ) {
                InfoRow(
                    icon     = Icons.Default.Info,
                    iconBg   = Color(0xFFE3F2FD),
                    iconTint = Color(0xFF1565C0),
                    title    = "Version",
                    subtitle = "Current build: $versionName",
                    trailing = versionName,
                )
                RowDivider()
                NavigationRow(
                    icon     = Icons.Default.Person,
                    iconBg   = Color(0xFFE3F2FD),
                    iconTint = Color(0xFF1565C0),
                    title    = "About",
                    subtitle = "Developer info and app story",
                    onClick  = onNavigateToAbout,
                )
            }

            // ── DEVELOPER section (debuggable builds only) ────────────────────
            if (onDebugProChange != null) {
                Spacer(Modifier.height(24.dp))
                SectionLabel("DEVELOPER")
                Spacer(Modifier.height(8.dp))
                Card(
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier  = Modifier.fillMaxWidth(),
                ) {
                    ToggleRow(
                        icon        = Icons.Outlined.WorkspacePremium,
                        iconBg      = Color(0xFFEEF2FF),
                        iconTint    = ProIndigo,
                        title       = "Pro unlocked",
                        subtitle    = "Debug builds only — simulates a Pro purchase",
                        checked     = isPro,
                        onCheckedChange = onDebugProChange,
                    )
                }
            }
        }
    }
}

// ── Reusable row components ──────────────────────────────────────────────────

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color      = AccentBlue,
        letterSpacing = 1.sp,
    )
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 68.dp, end = 16.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
internal fun SettingsIcon(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
) {
    Box(
        modifier         = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(iconBg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = iconTint,
            modifier           = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun NavigationRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showProBadge: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon = icon, iconBg = iconBg, iconTint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            RowTitle(title, showProBadge)
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ToggleRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showProBadge: Boolean = false,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon = icon, iconBg = iconBg, iconTint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            RowTitle(title, showProBadge)
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = StatusGreen,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun RowTitle(title: String, showProBadge: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface,
        )
        if (showProBadge) {
            Spacer(Modifier.width(6.dp))
            ProBadge()
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailing: String,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SettingsIcon(icon = icon, iconBg = iconBg, iconTint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text  = trailing,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProUpgradeBanner(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(ProIndigo, ProViolet),
                    ),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WorkspacePremium,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Panda DNS Pro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "Unlock filters, schedules, stats & more",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}
