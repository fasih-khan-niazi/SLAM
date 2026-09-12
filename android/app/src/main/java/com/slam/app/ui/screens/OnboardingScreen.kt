package com.slam.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slam.app.R
import com.slam.app.ui.components.SlamButtonStyle
import com.slam.app.ui.components.SlamLottie
import com.slam.app.ui.components.SlamPrimaryButton

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
fun OnboardingScreen(
    onFinished: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    val slide = slides[page]
    val last = page == slides.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(Modifier.height(24.dp))
            SlamLottie(
                resId = if (page == 0) R.raw.lottie_mark else R.raw.lottie_pulse,
                size = 160.dp,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "SLAM",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                slide.title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                slide.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "${page + 1} / ${slides.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            SlamPrimaryButton(
                text = if (last) "Finish" else "Next",
                onClick = {
                    if (last) onFinished() else page += 1
                },
            )
            if (!last) {
                Spacer(Modifier.height(8.dp))
                SlamPrimaryButton(
                    text = "Skip",
                    style = SlamButtonStyle.SECONDARY,
                    onClick = onFinished,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
