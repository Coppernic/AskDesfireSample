package fr.coppernic.tools.askdemo.presentation.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import fr.coppernic.tools.askdemo.R
import fr.coppernic.tools.askdemo.databinding.ActivityMainBinding
import fr.coppernic.tools.askdemo.domain.model.AskBadge

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val mainViewModel: MainViewModel by lazy {
        ViewModelProvider(this, MainViewModelFactory())[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Observing state and data
        setUpViewModelStateObservers()


    }

    override fun onStart() {
        super.onStart()
        mainViewModel.startProcess()
    }

    override fun onStop() {
        mainViewModel.stopProcess()
        super.onStop()
    }

    //region States

    private fun setUpViewModelStateObservers() {
        lifecycleScope.launchWhenResumed {
            mainViewModel.state.collect {
                onStateChanged(it)
            }
        }
    }

    private fun onStateChanged(state: MainViewModel.State) = when (state) {
        MainViewModel.State.Idle -> setStateIdle()
        is MainViewModel.State.Scanning -> setStateScanning()
        is MainViewModel.State.Display -> setStateDisplay(state.badge)
        is MainViewModel.State.Error -> setStateError(state.error)
    }

    //endregion

    //region setStates

    private fun setStateIdle() {
        binding.tvMessage.text = getString(R.string.initialisation)
    }

    private fun setStateScanning() {
        binding.tvMessage.text = getString(R.string.scanning)
    }

    private fun setStateDisplay(badge: AskBadge) {
        binding.tvMessage.text = badge.badgeData ?: badge.uid
    }

    private fun setStateError(error: Throwable) {
        binding.tvMessage.text = error.localizedMessage
    }

    //endregion
}