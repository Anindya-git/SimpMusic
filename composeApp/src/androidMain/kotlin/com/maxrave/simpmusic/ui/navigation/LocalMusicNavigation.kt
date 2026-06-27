package com.maxrave.simpmusic.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.maxrave.simpmusic.ui.navigation.destination.library.LocalMusicDestination
import com.maxrave.simpmusic.ui.screen.library.LocalMusicScreen

/**
 * Extension on [NavGraphBuilder] that registers the [LocalMusicScreen] route.
 *
 * Being defined in `androidMain`, this extension is automatically included for
 * Android builds and absent for Desktop builds — no `expect/actual` or `if`
 * guards needed anywhere.
 *
 * Call site (in commonMain/AppNavigationGraph.kt NavHost block):
 *
 *   localMusicRoute(innerPadding, navController, sharedViewModel, onScrolling)
 */
actual fun NavGraphBuilder.localMusicRoute(
    innerPadding: PaddingValues,
    navController: NavController,
    onScrolling: (Boolean) -> Unit,
) {
    composable<LocalMusicDestination> {
        LocalMusicScreen(
            innerPadding = innerPadding,
            navController = navController,
            onScrolling = onScrolling,
        )
    }
}
