package com.slam.app.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

val LocalSlamHapticsEnabled = compositionLocalOf { true }

fun View.slamHaptic(enabled: Boolean = true) {
    if (!enabled) {
        isHapticFeedbackEnabled = false
        return
    }
    isHapticFeedbackEnabled = true
    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
        as? android.os.Vibrator
    if (vibrator != null && vibrator.hasVibrator()) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(28, android.os.VibrationEffect.DEFAULT_AMPLITUDE),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(28)
        }
        return
    }
    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

enum class SlamButtonStyle { PRIMARY, SECONDARY, DESTRUCTIVE }

@Composable
fun SlamPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    style: SlamButtonStyle = SlamButtonStyle.PRIMARY,
) {
    val view = LocalView.current
    val haptics = LocalSlamHapticsEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "button-press")
    val container = when (style) {
        SlamButtonStyle.PRIMARY -> MaterialTheme.colorScheme.primary
        SlamButtonStyle.SECONDARY -> MaterialTheme.colorScheme.surfaceVariant
        SlamButtonStyle.DESTRUCTIVE -> MaterialTheme.colorScheme.error
    }
    val content = when (style) {
        SlamButtonStyle.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        SlamButtonStyle.SECONDARY -> MaterialTheme.colorScheme.onSurface
        SlamButtonStyle.DESTRUCTIVE -> MaterialTheme.colorScheme.onError
    }
    Button(
        onClick = {
            view.slamHaptic(haptics)
            onClick()
        },
        enabled = enabled && !loading,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
        ),
        border = if (style == SlamButtonStyle.SECONDARY) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        } else null,
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = content,
                strokeWidth = 2.dp,
                modifier = Modifier.height(22.dp),
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SlamTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val haptics = LocalSlamHapticsEnabled.current
    TextButton(onClick = {
        view.slamHaptic(haptics)
        onClick()
    }, modifier = modifier) {
        Text(text, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SlamField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = supportingText?.let { text -> { Text(text) } },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

@Composable
fun SlamSkeleton(
    modifier: Modifier = Modifier,
    height: Int = 72,
) {
    val shimmer by rememberInfiniteTransition(label = "skel").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skel-anim",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.surfaceVariant,
        ),
        start = Offset(shimmer * 400f, 0f),
        end = Offset(shimmer * 400f + 200f, 200f),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(brush),
    )
}

@Composable
fun SlamCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        content()
    }
}
