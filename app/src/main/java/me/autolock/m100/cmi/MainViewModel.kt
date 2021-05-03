package me.autolock.m100.cmi

import androidx.lifecycle.*

class MainViewModel(private val bleRepository: BleRepository) : ViewModel() {
    val statusText = MutableLiveData<String>()
    val logText = MutableLiveData<String>()
    val logLine = MutableLiveData<String>()

    fun scanButtonOnClick() {
        logLine.value = "start scan"
        statusText.value = "Star scanning..."
    }
}
