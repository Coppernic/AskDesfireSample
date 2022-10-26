package fr.coppernic.tools.askdemo.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.coppernic.tools.askdemo.domain.model.AskBadge
import fr.coppernic.tools.askdemo.domain.repository.BadgeRepository
import fr.coppernic.tools.askdemo.presentation.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            MainViewModel(
                App.components.badgeRepository
            ) as T
        } else {
            throw IllegalArgumentException("ViewModel Not Found")
        }
    }
}

class MainViewModel constructor(
    private val badgeRepository: BadgeRepository
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    sealed class State {
        object Idle : State()
        object Scanning : State()
        data class Display(val badge : AskBadge) : State()
        data class Error(val error : Throwable) : State()
    }

    fun startProcess() {
        if (_state.value !is State.Display) {
            viewModelScope.launch {
                withContext(Dispatchers.Main) {
                    _state.emit(State.Scanning)
                    badgeRepository.startScan()
                        .collect { result ->
                            result.fold(
                                { badge -> _state.emit(State.Display(badge)) },
                                { ex -> _state.emit(State.Error(ex)) })
                        }
                }
            }
        }
    }

    fun stopProcess() {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                badgeRepository.stopScan()
                _state.emit(State.Idle)
            }
        }
    }
}