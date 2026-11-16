package com.example.pardadns.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pardadns.data.model.DnsProvider
import com.example.pardadns.ui.components.ProBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID

private val ipv4Regex = Regex(
    """^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$"""
)

private fun isValidIpv4(ip: String): Boolean = ipv4Regex.matches(ip.trim())

private fun isValidHostname(host: String): Boolean {
    if (host.isBlank()) return false
    val cleaned = host.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removeSuffix("/dns-query")
        .removeSuffix("/")
    return cleaned.contains('.') && !cleaned.contains(' ')
}

private enum class TestResult { IDLE, TESTING, SUCCESS, FAILURE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProviderScreen(
    onBack: () -> Unit,
    onSave: (DnsProvider) -> Unit,
    onSaveWithoutConnect: ((DnsProvider) -> Unit)? = null,
    availableProviders: List<DnsProvider> = emptyList(),
    onUnhideProvider: ((String) -> Unit)? = null,
    existingProviderNames: Set<String> = emptySet(),
    proProvidersUnlocked: Boolean = false,
    onProRequired: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var providerName by rememberSaveable { mutableStateOf("") }
    var dohHostname by rememberSaveable { mutableStateOf("") }
    var udpFallbackIp by rememberSaveable { mutableStateOf("") }
    var secondaryIp by rememberSaveable { mutableStateOf("") }

    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var ipError by rememberSaveable { mutableStateOf<String?>(null) }

    var testResult by remember { mutableStateOf(TestResult.IDLE) }
    var showDohWarning by remember { mutableStateOf(false) }
    var pendingProvider by remember { mutableStateOf<DnsProvider?>(null) }
    var optionalFieldsExpanded by rememberSaveable { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (optionalFieldsExpanded) 180f else 0f,
        label = "arrowRotation"
    )

    val scope = rememberCoroutineScope()

    fun validateFields(): Boolean {
        var valid = true
        nameError = if (providerName.isBlank()) {
            valid = false; "Provider name is required"
        } else if (providerName.trim().lowercase() in existingProviderNames.map { it.lowercase() }) {
            valid = false; "A provider with this name already exists"
        } else null
        ipError = if (udpFallbackIp.isBlank()) {
            valid = false; "IPv4 address is required"
        } else if (!isValidIpv4(udpFallbackIp)) {
            valid = false; "Enter a valid IPv4 address (e.g. 1.2.3.4)"
        } else null
        if (secondaryIp.isNotBlank() && !isValidIpv4(secondaryIp)) {
            ipError = "Secondary IP is not a valid IPv4 address"
            valid = false
        }
        return valid
    }

    fun buildProvider(): DnsProvider {
        val dohUrl = if (dohHostname.isNotBlank()) {
            val host = dohHostname.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .removeSuffix("/dns-query")
                .removeSuffix("/")
            "https://$host/dns-query"
        } else null
        return DnsProvider(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = providerName.trim(),
            primaryIp = udpFallbackIp.trim(),
            secondaryIp = if (secondaryIp.isNotBlank()) secondaryIp.trim() else udpFallbackIp.trim(),
            description = "Custom provider",
            dohUrl = dohUrl,
        )
    }

    fun trySave() {
        if (!validateFields()) return
        val provider = buildProvider()
        if (provider.dohUrl == null) {
            pendingProvider = provider
            showDohWarning = true
        } else {
            onSave(provider)
            providerName = ""; dohHostname = ""; udpFallbackIp = ""; secondaryIp = ""
        }
    }

    fun testConnection() {
        if (udpFallbackIp.isBlank() || !isValidIpv4(udpFallbackIp)) {
            ipError = "Enter a valid IPv4 address to test"
            return
        }
        testResult = TestResult.TESTING
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val query = byteArrayOf(
                        0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x61, 0x00, 0x00, 0x01, 0x00, 0x01,
                    )
                    DatagramSocket().use { socket ->
                        socket.soTimeout = 4_000
                        val addr = InetAddress.getByName(udpFallbackIp.trim())
                        socket.send(DatagramPacket(query, query.size, addr, 53))
                        socket.receive(DatagramPacket(ByteArray(64), 64))
                        true
                    }
                } catch (_: Exception) { false }
            }
            testResult = if (ok) TestResult.SUCCESS else TestResult.FAILURE
        }
    }

    // ── DoH warning dialog ──────────────────────────────────────────────────
    if (showDohWarning && pendingProvider != null) {
        AlertDialog(
            onDismissRequest = { showDohWarning = false; pendingProvider = null },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(32.dp),
                )
            },
            title = { Text("Unsecured DNS", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Without a DNS-over-HTTPS hostname, your DNS queries will " +
                        "be sent unencrypted. You can still connect, but your " +
                        "traffic won't be fully private.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val provider = pendingProvider!!
                        showDohWarning = false
                        pendingProvider = null
                        // Save without changing connection status, navigate to main page
                        (onSaveWithoutConnect ?: onSave)(provider)
                        providerName = ""; dohHostname = ""; udpFallbackIp = ""; secondaryIp = ""
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Provider")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDohWarning = false; pendingProvider = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add DNS Provider", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding(),
                ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Custom DNS",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Add your own private DNS provider with IPv4 address and optional DoH endpoint.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Form Card ───────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Provider Name
                        Text(
                            text = "Provider Name *",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = providerName,
                            onValueChange = { providerName = it; nameError = null },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. My Secure DNS") },
                            leadingIcon = {
                                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            isError = nameError != null,
                            supportingText = nameError?.let { err -> { Text(err, color = MaterialTheme.colorScheme.error) } },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        )

                        // IPv4 Address
                        Text(
                            text = "Primary IPv4 Address *",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = udpFallbackIp,
                            onValueChange = {
                                udpFallbackIp = it; ipError = null; testResult = TestResult.IDLE
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. 1.2.3.4") },
                            leadingIcon = {
                                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            isError = ipError != null,
                            supportingText = ipError?.let { err -> { Text(err, color = MaterialTheme.colorScheme.error) } },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        )

                        // Optional Fields Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { optionalFieldsExpanded = !optionalFieldsExpanded }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Optional Settings",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (optionalFieldsExpanded) "Collapse" else "Expand",
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(arrowRotation),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        AnimatedVisibility(
                            visible = optionalFieldsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                // Secondary IPv4 Address (optional)
                                Text(
                                    text = "Secondary IPv4 Address",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Fallback if primary is unreachable",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                                OutlinedTextField(
                                    value = secondaryIp,
                                    onValueChange = { secondaryIp = it; ipError = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("e.g. 1.0.0.1") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    ),
                                )

                                Spacer(Modifier.height(4.dp))

                                // DoH Hostname (optional)
                                Text(
                                    text = "DNS-over-HTTPS Hostname",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Enables encrypted DNS queries",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                                OutlinedTextField(
                                    value = dohHostname,
                                    onValueChange = { dohHostname = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("dns.example.com") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    ),
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // ── Test Connection result ──────────────────────────
                        AnimatedVisibility(
                            visible = testResult == TestResult.SUCCESS || testResult == TestResult.FAILURE,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                if (testResult == TestResult.SUCCESS) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF43A047),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "Connection successful",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF43A047),
                                        fontWeight = FontWeight.Medium,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "Connection failed — check the IP address",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        // ── Action buttons ──────────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Test Connection button
                            OutlinedButton(
                                onClick = { testConnection() },
                                modifier = Modifier.weight(1f),
                                enabled = testResult != TestResult.TESTING,
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                if (testResult == TestResult.TESTING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.NetworkCheck,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("Test", fontWeight = FontWeight.SemiBold)
                            }

                            // Add Provider button
                            Button(
                                onClick = { trySave() },
                                modifier = Modifier.weight(1f),
                                enabled = providerName.isNotBlank() && udpFallbackIp.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Provider", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── Available Providers Section (removed from homepage) ───────
            if (availableProviders.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Available Providers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${availableProviders.size} PROVIDERS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(availableProviders, key = { it.id }) { provider ->
                    PresetProviderRow(
                        provider = provider,
                        onAdd = {
                            if (provider.proOnly && !proProvidersUnlocked) onProRequired()
                            else onUnhideProvider?.invoke(provider.id)
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun PresetProviderRow(
    provider: DnsProvider,
    onAdd: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (provider.proOnly) {
                        Spacer(Modifier.width(6.dp))
                        ProBadge()
                    }
                }
                Text(
                    text = provider.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = provider.dohUrl
                        ?.removePrefix("https://")
                        ?.removeSuffix("/dns-query")
                        ?.removeSuffix("/")
                        ?: provider.primaryIp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            FilledTonalButton(
                onClick = onAdd,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
