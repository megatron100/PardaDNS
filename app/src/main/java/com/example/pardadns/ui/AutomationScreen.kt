package com.example.pardadns.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pardadns.data.repository.ProtectionSchedule
import com.example.pardadns.domain.model.VpnState
import com.example.pardadns.ui.components.PauseOptions
import com.example.pardadns.ui.components.ProUpsellBanner
import com.example.pardadns.ui.theme.StatusAmber
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private fun formatMinute(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(TimeFormat)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    viewModel: AutomationViewModel,
    vpnState: VpnState,
    onPause: (minutes: Int) -> Unit,
    onResume: () -> Unit,
    onBack: () -> Unit,
    onProRequired: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val connectOnBoot by viewModel.connectOnBoot.collectAsStateWithLifecycle()
    val connectOnLaunch by viewModel.connectOnLaunch.collectAsStateWithLifecycle()

    var editingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }

    fun guarded(action: () -> Unit) { if (isPro) action() else onProRequired() }

    editingStart?.let { isStart ->
        TimePickerDialog(
            title = if (isStart) "Protection turns on" else "Protection turns off",
            initialMinute = if (isStart) schedule.startMinute else schedule.endMinute,
            onDismiss = { editingStart = null },
            onConfirm = { minute ->
                editingStart = null
                viewModel.setSchedule(
                    if (isStart) schedule.copy(startMinute = minute) else schedule.copy(endMinute = minute),
                )
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule & Auto-connect", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp, end = 20.dp, top = 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
        ) {
            if (!isPro) {
                ProUpsellBanner(
                    title = "Automate your protection",
                    subtitle = "Schedules, quick pause and auto-connect",
                    onClick = onProRequired,
                )
                Spacer(Modifier.height(20.dp))
            }

            // ── PAUSE ─────────────────────────────────────────────────────────
            SectionLabel("PAUSE")
            Spacer(Modifier.height(8.dp))
            AutomationCard {
                PauseSection(
                    vpnState = vpnState,
                    onPause = { minutes -> guarded { onPause(minutes) } },
                    onResume = onResume,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── AUTO-CONNECT ──────────────────────────────────────────────────
            SectionLabel("AUTO-CONNECT")
            Spacer(Modifier.height(8.dp))
            AutomationCard {
                ToggleRow(
                    icon = Icons.Default.PowerSettingsNew,
                    iconBg = Color(0xFFE8F5E9),
                    iconTint = Color(0xFF2E7D32),
                    title = "Connect on device start",
                    subtitle = "Turn protection on after a reboot",
                    checked = isPro && connectOnBoot,
                    onCheckedChange = { on -> guarded { viewModel.setConnectOnBoot(on) } },
                )
                RowDivider()
                ToggleRow(
                    icon = Icons.Default.TouchApp,
                    iconBg = Color(0xFFE3F2FD),
                    iconTint = Color(0xFF1565C0),
                    title = "Connect when app opens",
                    subtitle = "Use your last provider automatically",
                    checked = isPro && connectOnLaunch,
                    onCheckedChange = { on -> guarded { viewModel.setConnectOnLaunch(on) } },
                )
                RowDivider()
                NavigationRow(
                    icon = Icons.Default.PhonelinkLock,
                    iconBg = Color(0xFFE3F2FD),
                    iconTint = Color(0xFF1565C0),
                    title = "Always-on VPN",
                    subtitle = "Most reliable — Android keeps Panda DNS running",
                    onClick = { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── SCHEDULE ──────────────────────────────────────────────────────
            SectionLabel("SCHEDULE")
            Spacer(Modifier.height(8.dp))
            AutomationCard {
                ToggleRow(
                    icon = Icons.Default.Schedule,
                    iconBg = Color(0xFFF3E5F5),
                    iconTint = Color(0xFF7B1FA2),
                    title = "Protection schedule",
                    subtitle = "Only protect during the hours you choose",
                    checked = isPro && schedule.enabled,
                    onCheckedChange = { on -> guarded { viewModel.setSchedule(schedule.copy(enabled = on)) } },
                )
                if (isPro && schedule.enabled) {
                    RowDivider()
                    ScheduleEditor(
                        schedule = schedule,
                        onEditStart = { guarded { editingStart = true } },
                        onEditEnd = { guarded { editingStart = false } },
                        onToggleDay = { day ->
                            guarded {
                                val bit = 1 shl (day.value - 1)
                                val days = schedule.days xor bit
                                if (days != 0) viewModel.setSchedule(schedule.copy(days = days))
                            }
                        },
                    )
                    if (!viewModel.canScheduleExact() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        RowDivider()
                        NavigationRow(
                            icon = Icons.Default.AlarmOn,
                            iconBg = Color(0xFFFFF3E0),
                            iconTint = Color(0xFFE65100),
                            title = "Allow precise timing",
                            subtitle = "Otherwise changes may run a few minutes late",
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(Uri.parse("package:${context.packageName}")),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PauseSection(vpnState: VpnState, onPause: (Int) -> Unit, onResume: () -> Unit) {
    val context = LocalContext.current
    val subtitle = when (vpnState) {
        is VpnState.Paused -> "Paused until ${DateFormat.getTimeFormat(context).format(Date(vpnState.untilMillis))}"
        is VpnState.Connected -> "Turn protection off briefly — it comes back on by itself"
        else -> "Connect to a provider first"
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(icon = Icons.Default.PauseCircle, iconBg = Color(0xFFFFF8E1), iconTint = StatusAmber)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Pause protection",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (vpnState) {
            is VpnState.Paused -> {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAmber, contentColor = Color.White),
                ) { Text("Resume now", fontWeight = FontWeight.SemiBold) }
            }
            is VpnState.Connected -> {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((minutes, label) in PauseOptions) {
                        FilterChip(
                            selected = false,
                            onClick = { onPause(minutes) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ScheduleEditor(
    schedule: ProtectionSchedule,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeField(label = "Turns on", value = formatMinute(schedule.startMinute), onClick = onEditStart, modifier = Modifier.weight(1f))
            TimeField(label = "Turns off", value = formatMinute(schedule.endMinute), onClick = onEditEnd, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (day in DayOfWeek.entries) {
                val selected = schedule.isDaySelected(day)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onToggleDay(day) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = scheduleSummary(schedule),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun TimeField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun scheduleSummary(s: ProtectionSchedule): String {
    val days = when (s.days) {
        ProtectionSchedule.ALL_DAYS -> "every day"
        0b0011111 -> "on weekdays"
        0b1100000 -> "on weekends"
        else -> "on " + DayOfWeek.entries.filter { s.isDaySelected(it) }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
    val overnight = if (s.endMinute <= s.startMinute) " (overnight)" else ""
    return "Protection turns on at ${formatMinute(s.startMinute)} and off at ${formatMinute(s.endMinute)}$overnight, $days. " +
        "Outside these hours DNS uses your network's default resolver."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (minuteOfDay: Int) -> Unit,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = remember { DateFormat.is24HourFormat(context) },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
