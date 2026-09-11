package com.slam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.slam.app.data.SessionStore
import com.slam.app.data.UiPreferences
import com.slam.app.data.AccountLifecycleManager
import com.slam.app.navigation.SlamRoutes
import com.slam.app.ui.components.LocalSlamHapticsEnabled
import com.slam.app.ui.components.LocalSlamToastHostState
import com.slam.app.ui.components.SlamScaffold
import com.slam.app.ui.components.SlamToastHostState
import com.slam.app.ui.screens.ActivityScreen
import com.slam.app.ui.screens.ConsentScreen
import com.slam.app.ui.screens.DashboardScreen
import com.slam.app.ui.screens.HomeScreen
import com.slam.app.ui.screens.LoginScreen
import com.slam.app.ui.screens.RegisterScreen
import com.slam.app.ui.screens.SettingsScreen
import com.slam.app.ui.screens.SplashScreen
import com.slam.app.ui.theme.SlamTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlamTheme {
                val nav = rememberNavController()
                val context = LocalContext.current
                val store = remember { SessionStore(context) }
                val uiPreferences = remember { UiPreferences(context) }
                val hapticsEnabled by uiPreferences.hapticsEnabled.collectAsStateWithLifecycle(true)
                val scope = rememberCoroutineScope()
                var bootstrapped by remember { mutableStateOf(false) }
                var start by remember { mutableStateOf(SlamRoutes.SPLASH) }

                LaunchedEffect(Unit) {
                    val consent = store.consentAccepted.first()
                    val token = store.token.first()
                    start = when {
                        !consent -> SlamRoutes.CONSENT
                        token.isBlank() -> SlamRoutes.LOGIN
                        else -> SlamRoutes.MAIN
                    }
                    bootstrapped = true
                }

                CompositionLocalProvider(LocalSlamHapticsEnabled provides hapticsEnabled) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = nav,
                            startDestination = SlamRoutes.SPLASH,
                            enterTransition = { fadeIn(tween(180)) },
                            exitTransition = { fadeOut(tween(180)) },
                            popEnterTransition = { fadeIn(tween(180)) },
                            popExitTransition = { fadeOut(tween(180)) },
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
                                        nav.navigate(SlamRoutes.MAIN) {
                                            popUpTo(SlamRoutes.LOGIN) { inclusive = true }
                                        }
                                    },
                                    onCreateAccount = { nav.navigate(SlamRoutes.REGISTER) },
                                )
                            }
                            composable(SlamRoutes.REGISTER) {
                                RegisterScreen(
                                    onRegistered = {
                                        nav.navigate(SlamRoutes.MAIN) {
                                            popUpTo(SlamRoutes.LOGIN) { inclusive = true }
                                        }
                                    },
                                    onBackToLogin = { nav.popBackStack() },
                                )
                            }
                            composable(SlamRoutes.MAIN) {
                                MainTabs(
                                    onSignedOut = {
                                        nav.navigate(SlamRoutes.LOGIN) {
                                            popUpTo(SlamRoutes.MAIN) { inclusive = true }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabs(onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val accountLifecycle = remember { AccountLifecycleManager(context) }
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    val toastHost = remember { SlamToastHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: SlamRoutes.HOME

    fun switchTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalSlamToastHostState provides toastHost) {
        SlamScaffold(
            currentRoute = currentRoute,
            onTabSelected = ::switchTab,
            toastHostState = toastHost,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                NavHost(
                    navController = nav,
                    startDestination = SlamRoutes.HOME,
                    enterTransition = { fadeIn(tween(160)) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(160)) },
                    popExitTransition = { fadeOut(tween(160)) },
                ) {
                    composable(SlamRoutes.HOME) {
                        DashboardScreen(
                            onOpenTracking = { switchTab(SlamRoutes.TRACKING) },
                            onSessionExpired = {
                                scope.launch {
                                    accountLifecycle.signOutAndWipe()
                                    onSignedOut()
                                }
                            },
                        )
                    }
                    composable(SlamRoutes.TRACKING) {
                        HomeScreen()
                    }
                    composable(SlamRoutes.ACTIVITY) { ActivityScreen() }
                    composable(SlamRoutes.SETTINGS) {
                        SettingsScreen(
                            onSignOut = {
                                scope.launch {
                                    accountLifecycle.signOutAndWipe()
                                    onSignedOut()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
