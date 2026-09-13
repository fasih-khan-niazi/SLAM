package com.slam.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.theme.SlamTheme
import org.junit.Rule
import org.junit.Test

class AuthValidationUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun primaryButtonProvidesVisiblePressedActionSurface() {
        compose.setContent {
            SlamTheme(darkTheme = true) {
                SlamPrimaryButton(text = "Continue", onClick = {})
            }
        }

        compose.onNodeWithText("Continue")
            .assertIsDisplayed()
            .performClick()
    }
}
