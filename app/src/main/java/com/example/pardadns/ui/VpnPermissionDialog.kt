package com.example.pardadns.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shown after the user turns down Android's VPN dialog, and on their next connect tap. */
@Composable
fun VpnPermissionDialog(
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
        title = { Text("VPN permission needed") },
        text = {
            Column {
                Text(
                    text = "Panda DNS needs VPN permission to block ads and trackers. " +
                            "Without it, the app won't work.",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The VPN runs on your phone. Tap Try again, then tap OK when Android asks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("Try again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        },
    )
}
