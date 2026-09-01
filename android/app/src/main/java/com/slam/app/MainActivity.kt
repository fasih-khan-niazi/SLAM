package com.slam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.slam.app.data.SessionStore
import com.slam.app.navigation.SlamRoutes
import com.slam.app.ui.screens.ConsentScreen
import com.slam.app.ui.screens.HomeScreen
import com.slam.app.ui.screens.LoginScreen
import com.slam.app.ui.screens.RegisterScreen
import com.slam.app.ui.screens.SplashScreen
import com.slam.app.ui.theme.SlamTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlamTheme(darkTheme = true) {
                val nav = rememberNavController()
                val context = LocalContext.current
                val store = remember { SessionStore(context) }
                val scope = rememberCoroutineScope()
                var bootstrapped by remember { mutableStateOf(false) }
                var start by remember { mutableStateOf(SlamRoutes.SPLASH) }

                LaunchedEffect(Unit) {
                    val consent = store.consentAccepted.first()
                    val token = store.token.first()
                    start = when {
                        !consent -> SlamRoutes.CONSENT
                        token.isBlank() -> SlamRoutes.LOGIN
                        else -> SlamRoutes.HOME
                    }
                    bootstrapped = true
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .systemBarsPadding(),
                ) {
                    NavHost(
                        navController = nav,
                        startDestination = SlamRoutes.SPLASH,
                    ) {
                        composable(SlamRoutes.SPLASH) {
                            SplashScreen(ready = bootstrapped) {
                                nav.navigate(start) {
                                    popUpTo(SlamRoutes.SPLASH) { inclusive = true }
                                }
                            }
                        }
                        composable(SlamRoutes.CONSENT) {
                            ConsentScreen {
                                scope.launch {
                                    store.setConsent(true)
                                    nav.navigate(SlamRoutes.LOGIN) {
                                        popUpTo(SlamRoutes.CONSENT) { inclusive = true }
                                    }
                                }
                            }
                        }
                        composable(SlamRoutes.LOGIN) {
                            LoginScreen(
                                onLoggedIn = {
                                    nav.navigate(SlamRoutes.HOME) {
                                        popUpTo(SlamRoutes.LOGIN) { inclusive = true }
                                    }
                                },
                                onCreateAccount = { nav.navigate(SlamRoutes.REGISTER) },
                            )
                        }
                        composable(SlamRoutes.REGISTER) {
                            RegisterScreen(
                                onRegistered = {
                                    nav.navigate(SlamRoutes.HOME) {
                                        popUpTo(SlamRoutes.LOGIN) { inclusive = true }
                                    }
                                },
                                onBackToLogin = { nav.popBackStack() },
                            )
                        }
                        composable(SlamRoutes.HOME) {
                            HomeScreen(
                                onSignedOut = {
                                    nav.navigate(SlamRoutes.LOGIN) {
                                        popUpTo(SlamRoutes.HOME) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
