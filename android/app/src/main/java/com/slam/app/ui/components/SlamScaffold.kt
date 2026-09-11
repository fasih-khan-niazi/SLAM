package com.slam.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.slam.app.navigation.SlamRoutes

data class SlamTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val slamTabs = listOf(
    SlamTab(SlamRoutes.HOME, "Home", Icons.Outlined.Home),
    SlamTab(SlamRoutes.TRACKING, "Tracking", Icons.Outlined.LocationOn),
    SlamTab(SlamRoutes.ACTIVITY, "Activity", Icons.Outlined.History),
    SlamTab(SlamRoutes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun SlamScaffold(
    currentRoute: String,
    onTabSelected: (String) -> Unit,
    toastHostState: SlamToastHostState,
    modifier: Modifier = Modifier,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                slamTabs.forEach { tab ->
                    SlamNavigationItem(
                        tab = tab,
                        selected = currentRoute == tab.route,
                        onClick = { onTabSelected(tab.route) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(padding)
            SlamToastHost(
                hostState = toastHostState,
                fromTop = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun RowScope.SlamNavigationItem(
    tab: SlamTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val haptics = LocalSlamHapticsEnabled.current
    NavigationBarItem(
        selected = selected,
        onClick = {
            if (!selected) view.slamHaptic(haptics)
            onClick()
        },
        icon = {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
            )
        },
        label = { Text(tab.label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        ),
    )
}
