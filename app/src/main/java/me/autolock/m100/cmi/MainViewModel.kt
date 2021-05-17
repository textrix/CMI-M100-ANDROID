package me.autolock.m100.cmi

import android.bluetooth.BluetoothDevice
import androidx.databinding.ObservableBoolean
import androidx.lifecycle.*
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule

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

    val reportArray: LiveData<ByteArray>
        get() = bleRepository.reportArray

    //val listUpdate : LiveData<Event<ArrayList<BluetoothDevice>?>>
    val listUpdate : LiveData<ArrayList<BluetoothDevice>?>
        get() = bleRepository.listUpdate

    val version: LiveData<String>
        get() = bleRepository.version

    fun scanButtonOnClick() {
        bleRepository.startScan()
    }

    fun disconnectButtonOnClick(){
        bleRepository.disconnectGattServer()
    }

    fun connectDevice(bluetoothDevice: BluetoothDevice) {
        bleRepository.connectDevice(bluetoothDevice)
    }

    fun relayOnButtonOnClick(num: Int) {
        var text = "O$num"
        bleRepository.writeData(text.toByteArray(Charset.defaultCharset()))
    }

    fun relayOffButtonOnClick(num: Int) {
        var text = "F$num"
        bleRepository.writeData(text.toByteArray(Charset.defaultCharset()))
    }

    fun readButtonOnClick() {
        Timer("read", false).schedule(0, 500) {
            if (connected.get()) {
                bleRepository.read(CHARACTERISTIC_REPORT_STRING)
            }
        }
    }

    fun versionButtonOnClick() {
        bleRepository.read(CHARACTERISTIC_VERSiON_STRING)
    }
}
