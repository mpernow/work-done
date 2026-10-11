package com.mpernow.workdone.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mpernow.workdone.WorkDoneApplication
import com.mpernow.workdone.di.AppContainer

/**
 * Builds a ViewModel factory that has access to the [AppContainer]. Usage:
 * `viewModel(factory = appViewModelFactory { MyViewModel(it.someRepository) })`
 */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[APPLICATION_KEY] as WorkDoneApplication
        create(app.container)
    }
}
