package com.meudinheiro.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meudinheiro.funcoes.UserPreferences

class HomeViewModelFactory(private val prefs: UserPreferences)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}