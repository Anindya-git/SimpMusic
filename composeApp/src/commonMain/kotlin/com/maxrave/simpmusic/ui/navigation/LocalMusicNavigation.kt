package com.maxrave.simpmusic.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

expect fun NavGraphBuilder.localMusicRoute(
    innerPadding: PaddingValues,
    navController: NavController,
    onScrolling: (Boolean) -> Unit,
)
