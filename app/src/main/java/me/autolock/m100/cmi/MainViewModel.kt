package me.autolock.m100.cmi

import android.util.Log
import androidx.databinding.ObservableBoolean
import androidx.lifecycle.*

class MainViewModel(private val bleRepository: BleRepository) : ViewModel() {
    val statusText = MutableLiveData<String>()
    val logText = MutableLiveData<String>()
    val logLine = MutableLiveData<String>()

    // bridge between viewModel.scanning and bleRepository.scanning.
    val scanningBridge: LiveData<Boolean>
        get() = bleRepository.scanning
    // When viewModel.scanning is changed, it can be detected in the layout.
    val scanning = ObservableBoolean(false)

    fun scanButtonOnClick() {
        logLine.postValue("start scan")
        bleRepository.startScan()
    }

    fun stopScan() {
    }
}
