package com.slam.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.slam.app.ui.components.SlamPrimaryButton

@Composable
fun ConsentScreen(onAccept: () -> Unit) {
    var owner by remember { mutableStateOf(false) }
    var tracking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Before you continue", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "SLAM lets trusted contacts request this phone’s location by SMS. " +
                "Install it only on a device you own or have permission to manage.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = owner,
                role = Role.Checkbox,
                onValueChange = { owner = it },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = owner, onCheckedChange = null)
            Text("I own this phone or have the owner’s permission.")
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = tracking,
                role = Role.Checkbox,
                onValueChange = { tracking = it },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = tracking, onCheckedChange = null)
            Text("I understand trusted numbers can request location over SMS.")
        }
        Spacer(Modifier.height(32.dp))
        SlamPrimaryButton(
            text = "Continue",
            onClick = onAccept,
            enabled = owner && tracking,
        )
    }
}
