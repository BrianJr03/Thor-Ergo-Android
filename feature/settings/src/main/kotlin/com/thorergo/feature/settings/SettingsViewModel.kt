package com.thorergo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Settings as a standalone screen in the app. */
@HiltViewModel
class SettingsViewModel @Inject constructor(factory: SettingsStateHolder.Factory) : ViewModel() {
    private val holder = factory.create(viewModelScope)

    val state: StateFlow<SettingsUiState> = holder.state

    fun onIntent(intent: SettingsIntent) = holder.onIntent(intent)
}
