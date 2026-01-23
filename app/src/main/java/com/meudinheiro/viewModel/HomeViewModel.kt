package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meudinheiro.funcoes.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val prefs: UserPreferences) : ViewModel() {
    // flui o nome salvo ou "" se não houver
    val userName: StateFlow<String> =
        prefs.userNameFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun updateUserName(name: String) {
        viewModelScope.launch { prefs.saveUserName(name) }
    }
}