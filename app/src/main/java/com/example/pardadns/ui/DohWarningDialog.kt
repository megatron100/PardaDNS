package com.example.pardadns.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DohWarningDialog(
    providerName: String,
    onConnectAnyway: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Insecure DNS Provider") },
        text = {
            Column {
                Text(
                    text = "\"$providerName\" does not support DNS-over-HTTPS. " +
                            "Your DNS queries will be sent as plain-text UDP, " +
                            "which can be intercepted on the network.",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You can disable \"Enforce DNS-over-HTTPS\" in Settings to skip this warning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConnectAnyway) {
                Text("Connect Anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
    )
}
