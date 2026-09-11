package com.slam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SlamToastTone { NEUTRAL, SUCCESS, WARNING, DANGER }

data class SlamToastMessage(
    val text: String,
    val tone: SlamToastTone = SlamToastTone.NEUTRAL,
    val durationMs: Long = 3_000L,
)

class SlamToastHostState {
    private val mutex = Mutex()
    private val _messages = MutableSharedFlow<SlamToastMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    suspend fun show(
        text: String,
        tone: SlamToastTone = SlamToastTone.NEUTRAL,
        durationMs: Long = 3_000L,
    ) {
        mutex.withLock {
            _messages.emit(SlamToastMessage(text, tone, durationMs))
        }
    }
}

val LocalSlamToastHostState = staticCompositionLocalOf<SlamToastHostState> {
    error("SlamToastHostState missing")
}

@Composable
fun SlamToastHost(
    hostState: SlamToastHostState,
    modifier: Modifier = Modifier,
    fromTop: Boolean = false,
) {
    var current by remember { mutableStateOf<SlamToastMessage?>(null) }
    val progress = remember { Animatable(1f) }
    val view = LocalView.current
    val haptics = LocalSlamHapticsEnabled.current

    LaunchedEffect(hostState) {
        hostState.messages.collect { message ->
            current = message
            if (message.tone == SlamToastTone.DANGER || message.tone == SlamToastTone.WARNING) {
                view.slamHaptic(haptics)
            }
            progress.snapTo(1f)
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(message.durationMs.toInt(), easing = LinearEasing),
            )
            delay(40)
            current = null
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (fromTop) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically { height -> if (fromTop) -height / 2 else height / 2 } + fadeIn(),
            exit = slideOutVertically { height -> if (fromTop) -height / 2 else height / 2 } + fadeOut(),
        ) {
            val message = current ?: return@AnimatedVisibility
            val accent = when (message.tone) {
                SlamToastTone.SUCCESS -> Color(0xFF22C55E)
                SlamToastTone.WARNING -> Color(0xFFF59E0B)
                SlamToastTone.DANGER -> MaterialTheme.colorScheme.error
                SlamToastTone.NEUTRAL -> MaterialTheme.colorScheme.primary
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(accent.copy(alpha = 0.2f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(accent),
                    )
                }
            }
        }
    }
}
