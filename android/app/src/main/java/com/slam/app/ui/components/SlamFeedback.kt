package com.slam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class SlamStatusTone { SUCCESS, WARNING, DANGER, NEUTRAL }

@Composable
fun SlamStatusChip(
    text: String,
    tone: SlamStatusTone,
    modifier: Modifier = Modifier,
) {
    val color = when (tone) {
        SlamStatusTone.SUCCESS -> Color(0xFF10B981)
        SlamStatusTone.WARNING -> Color(0xFFF59E0B)
        SlamStatusTone.DANGER -> MaterialTheme.colorScheme.error
        SlamStatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun SlamBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: SlamStatusTone = SlamStatusTone.NEUTRAL,
) {
    val accent = when (tone) {
        SlamStatusTone.SUCCESS -> Color(0xFF10B981)
        SlamStatusTone.WARNING -> Color(0xFFF59E0B)
        SlamStatusTone.DANGER -> MaterialTheme.colorScheme.error
        SlamStatusTone.NEUTRAL -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.12f), MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = accent, style = MaterialTheme.typography.titleMedium)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SlamSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val haptics = LocalSlamHapticsEnabled.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                view.slamHaptic(haptics || it)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
