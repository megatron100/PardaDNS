package com.example.pardadns.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pardadns.R
import com.example.pardadns.ui.theme.StatusGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val packageInfo = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "1.0"
    val versionCode = packageInfo?.longVersionCode ?: 1L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top    = innerPadding.calculateTopPadding() + 32.dp,
                    bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding() + 24.dp,
                    start  = 20.dp,
                    end    = 20.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── App icon + name ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1565C0)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter            = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Panda DNS",
                    modifier           = Modifier.size(100.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text       = "Panda DNS",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Secure DNS over HTTPS",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "Version $versionName (build $versionCode)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(28.dp))

            // ── About Me ──────────────────────────────────────────────────────
            val accentBlue = Color(0xFF1565C0)
            Text(
                text          = "ABOUT ME",
                style         = MaterialTheme.typography.labelMedium,
                fontWeight    = FontWeight.Bold,
                color         = accentBlue,
                letterSpacing = 1.sp,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier  = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE3F2FD)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text(
                                text       = "Shubham",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text  = "Developer & Creator",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "I'm sick of invasive trackers and ads that follow you across the internet. " +
                               "Changing the Private DNS setting buried deep in Android settings every time felt like too much work — " +
                               "so I built Panda DNS to make it dead simple.",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Panda DNS routes all your DNS queries through encrypted HTTPS connections " +
                               "using a local VPN interface — no root required, no data collected, no accounts needed. " +
                               "Just tap and go.",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── How it works ──────────────────────────────────────────────────
            Text(
                text          = "HOW IT WORKS",
                style         = MaterialTheme.typography.labelMedium,
                fontWeight    = FontWeight.Bold,
                color         = accentBlue,
                letterSpacing = 1.sp,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier  = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    HowItWorksItem(
                        icon = Icons.Default.Shield,
                        iconBg = Color(0xFFE3F2FD),
                        iconTint = accentBlue,
                        title = "Local VPN tunnel",
                        description = "Creates a lightweight VPN on your device — no external servers, no traffic leaves your phone unencrypted.",
                    )
                    Spacer(Modifier.height(12.dp))
                    HowItWorksItem(
                        icon = Icons.Default.Security,
                        iconBg = Color(0xFFE8F5E9),
                        iconTint = StatusGreen,
                        title = "DNS-over-HTTPS",
                        description = "Every DNS query is wrapped in HTTPS, keeping your browsing activity hidden from your ISP and network snoopers.",
                    )
                    Spacer(Modifier.height(12.dp))
                    HowItWorksItem(
                        icon = Icons.Default.VerifiedUser,
                        iconBg = Color(0xFFFFF8E1),
                        iconTint = Color(0xFFF57F17),
                        title = "Ad & tracker blocking",
                        description = "Pair with ad-blocking DNS providers like AdGuard or NextDNS to block ads and trackers system-wide.",
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Privacy Policy ────────────────────────────────────────────────
            Text(
                text          = "PRIVACY POLICY",
                style         = MaterialTheme.typography.labelMedium,
                fontWeight    = FontWeight.Bold,
                color         = accentBlue,
                letterSpacing = 1.sp,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier  = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Policy,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text       = "Your privacy is everything",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    PrivacyBullet("Panda DNS does not collect, store, or transmit any personal data.")
                    Spacer(Modifier.height(6.dp))
                    PrivacyBullet("All DNS queries are resolved directly between your device and your chosen DNS provider — we never see them.")
                    Spacer(Modifier.height(6.dp))
                    PrivacyBullet("Anonymous crash reports help us fix bugs. No DNS or browsing data is ever collected.")
                    Spacer(Modifier.height(6.dp))
                    PrivacyBullet("Query statistics shown in the app are computed and stored locally on your device only.")
                    Spacer(Modifier.height(6.dp))
                    PrivacyBullet("No account creation or sign-in required. The app works entirely offline after installation.")
                    Spacer(Modifier.height(6.dp))
                    PrivacyBullet("The VPN interface is used solely to intercept DNS traffic — no other network traffic is routed through it.")
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Footer ────────────────────────────────────────────────────────
            Icon(
                imageVector        = Icons.Default.Favorite,
                contentDescription = null,
                tint               = Color(0xFFE53935),
                modifier           = Modifier.size(18.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = "Built with care for your privacy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HowItWorksItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    description: String,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text       = description,
                style      = MaterialTheme.typography.bodySmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun PrivacyBullet(text: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text  = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = StatusGreen,
            modifier = Modifier.padding(end = 8.dp, top = 1.dp),
        )
        Text(
            text       = text,
            style      = MaterialTheme.typography.bodySmall,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
            modifier   = Modifier.weight(1f),
        )
    }
}
