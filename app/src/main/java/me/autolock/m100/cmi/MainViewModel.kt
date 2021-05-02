package me.autolock.m100.cmi

import androidx.lifecycle.*

class MainViewModel(private val bleRepository: BleRepository) : ViewModel() {
    val logText = MutableLiveData<String>()
    var line = 1

    fun scanButtonOnClick() {
        logText.value = "${line}: start scan\n"
        line++
    }
}
