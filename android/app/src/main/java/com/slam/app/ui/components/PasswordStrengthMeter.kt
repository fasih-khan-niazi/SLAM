package com.slam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.slam.app.feature.auth.PasswordRules
import com.slam.app.feature.auth.PasswordStrength

@Composable
fun PasswordStrengthMeter(
    password: String,
    modifier: Modifier = Modifier,
) {
    val result = PasswordRules.evaluate(password)
    val label = when (result.strength) {
        PasswordStrength.WEAK -> "Weak"
        PasswordStrength.MEDIUM -> "Medium"
        PasswordStrength.STRONG -> "Strong"
    }
    val color = when (result.strength) {
        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
        PasswordStrength.MEDIUM -> Color(0xFFD97706)
        PasswordStrength.STRONG -> Color(0xFF16A34A)
    }
    val filled = when (result.strength) {
        PasswordStrength.WEAK -> 1
        PasswordStrength.MEDIUM -> 2
        PasswordStrength.STRONG -> 3
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (index < filled) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (password.isBlank()) "Password strength" else label,
            style = MaterialTheme.typography.labelLarge,
            color = if (password.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else color,
        )
        if (password.isNotBlank() && !result.isAcceptable) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Need 8+ chars, upper & lower, a number, and a special character.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
