package me.autolock.m100.cmi

import androidx.databinding.ObservableBoolean
import androidx.lifecycle.*

class MainViewModel(private val bleRepository: BleRepository) : ViewModel() {
    val statusText = MutableLiveData<String>()

    // bridge between viewModel.scanning and bleRepository.scanning.
    val scanningBridge: LiveData<Boolean>
        get() = bleRepository.scanning
    // When viewModel.scanning is changed, it can be detected in the layout.
    val scanning = ObservableBoolean(false)

    fun scanButtonOnClick() {
        bleRepository.startScan()
    }

    fun stopScan() {
    }
}
