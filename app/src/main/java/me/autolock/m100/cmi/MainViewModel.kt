package me.autolock.m100.cmi

import android.bluetooth.BluetoothDevice
import androidx.databinding.ObservableBoolean
import androidx.lifecycle.*

class MainViewModel(private val bleRepository: BleRepository) : ViewModel() {
    val statusText = MutableLiveData<String>()

    // bridge between viewModel.scanning and bleRepository.scanning.
    val scanningBridge: LiveData<Boolean>
        get() = bleRepository.scanning
    // When viewModel.scanning is changed, it can be detected in the layout.
    val scanning = ObservableBoolean(false)
    val connectedBridge: LiveData<Boolean>
        get() = bleRepository.connected
    var connected = ObservableBoolean(false)


    //val listUpdate : LiveData<Event<ArrayList<BluetoothDevice>?>>
    val listUpdate : LiveData<ArrayList<BluetoothDevice>?>
        get() = bleRepository.listUpdate

    fun scanButtonOnClick() {
        bleRepository.startScan()
    }

    fun disconnectButtonOnClick(){
        bleRepository.disconnectGattServer()
    }

    fun connectDevice(bluetoothDevice: BluetoothDevice) {
        bleRepository.connectDevice(bluetoothDevice)
    }

}
