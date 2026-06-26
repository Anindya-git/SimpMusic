package com.maxrave.simpmusic.di

import com.maxrave.simpmusic.viewModel.LocalMusicViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module that registers [LocalMusicViewModel].
 *
 * Include this module in your existing Koin setup by adding
 *   `localMusicModule`
 * to the list of modules passed to `startKoin { modules(...) }`.
 *
 * The existing modules list is typically found in the Application class or
 * in the androidMain KoinInitializer.
 */
val localMusicModule = module {
    viewModel {
        LocalMusicViewModel(context = androidContext())
    }
}
