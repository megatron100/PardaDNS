package com.example.pardadns.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pardadns.R

@Composable
fun BatteryOptimizationDialog(
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onDismiss: (dontAskAgain: Boolean) -> Unit,
) {
    var dontAskAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss(dontAskAgain) },
        title = { Text(stringResource(R.string.battery_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.battery_dialog_message))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = dontAskAgain,
                        onCheckedChange = { dontAskAgain = it },
                    )
                    Text(
                        text = stringResource(R.string.battery_dialog_dont_ask),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dontAskAgain) }) {
                Text(stringResource(R.string.battery_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(dontAskAgain) }) {
                Text(stringResource(R.string.battery_dialog_dismiss))
            }
        },
    )
}
