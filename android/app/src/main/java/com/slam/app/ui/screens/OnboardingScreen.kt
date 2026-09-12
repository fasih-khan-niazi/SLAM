package com.slam.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.slam.app.R
import com.slam.app.ui.components.SlamLottie
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamTextButton

private data class OnboardingSlide(
    val title: String,
    val body: String,
)

private val slides = listOf(
    OnboardingSlide(
        title = "Location by SMS",
        body = "Trusted people can request this phone's location with a short SMS command. No mobile data needed for the reply.",
    ),
    OnboardingSlide(
        title = "Trusted numbers first",
        body = "Only numbers you add can locate this phone. Add at least one before you start listening.",
    ),
    OnboardingSlide(
        title = "Your tracking PIN",
        body = "Commands look like SLAM 1234 LOCATE. Your PIN stays with your account when you sign back in.",
    ),
    OnboardingSlide(
        title = "Permissions and listening",
        body = "Allow SMS, location (all the time), and notifications. Then start listening so requests can be answered.",
    ),
    OnboardingSlide(
        title = "Emergency and battery",
        body = "Emergency sends timed updates to trusted numbers. Keep battery unrestricted so listening is not paused.",
    ),
)

@Composable
fun SlamOnboardingModal(
    allowDismiss: Boolean,
    onFinished: () -> Unit,
    onDismiss: () -> Unit = onFinished,
) {
    var page by remember { mutableIntStateOf(0) }
    val slide = slides[page]
    val last = page == slides.lastIndex

    Dialog(
        onDismissRequest = { if (allowDismiss) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = allowDismiss,
            dismissOnClickOutside = allowDismiss,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.54f))
                .then(
                    if (allowDismiss) Modifier.clickable(onClick = onDismiss) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = false) {}
                    .padding(20.dp)
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SlamLottie(
                        resId = if (page == 0) R.raw.lottie_mark else R.raw.lottie_pulse,
                        size = 80.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SLAM",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        slide.title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        slide.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${page + 1} / ${slides.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                SlamPrimaryButton(
                    text = if (last) "Finish" else "Next",
                    onClick = {
                        if (last) onFinished() else page += 1
                    },
                )
                if (!last) {
                    SlamTextButton(
                        text = "Skip",
                        onClick = onFinished,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
