package me.autolock.m100.cmi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.Uri
import androidx.databinding.ObservableBoolean
import androidx.lifecycle.*
import java.io.FileNotFoundException
import java.io.IOException
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

    val otaLength: LiveData<Int>
        get() = bleRepository.otaLengthObserver
    val otaCurrent: LiveData<Int>
        get() = bleRepository.otaCurrentObserver

    private var readTimer: Timer? = null

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

    fun readSwitchOnCheckedChanged(checked: Boolean) {
        if (checked) {
            readTimer = Timer("read", false)
            readTimer?.schedule(0, 500) {
                if (connected.get()) {
                    bleRepository.read(CHARACTERISTIC_REPORT_STRING)
                }
            }
        }
        else {
            readTimer?.cancel()
        }
    }

    fun versionButtonOnClick() {
        bleRepository.read(CHARACTERISTIC_VERSION_STRING)
    }

    fun startOTA(list: MutableList<ByteArray>, length: Int) {
        bleRepository.startOTA(list, length)
    }

    fun loadBinFile(context: Context, uri: Uri): Pair<MutableList<ByteArray>, Int> {
        val list = mutableListOf<ByteArray>()
        var length = 0
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                input?.let {
                    length = input.available()
                    var remain = length
                    var current = 0
                    while (true) {
                        val buff = ByteArray(if (512 < length) 512 else remain)
                        val size = input.read(buff)
                        if (size <= 0)
                            break
                        list.add(buff)
                        current += size
                        remain -= size
                    }
                }
            }
        }
        catch (e: FileNotFoundException) {
        }
        catch (e: IOException) {
        }
        return Pair(list, length)
    }

    /*
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun _loadBinFile(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                otaLength.postValue(0)
                otaCurrent.postValue(0)
                val result = context.contentResolver.openInputStream(uri).use { input ->
                    input?.let {
                        val fileLength = input.available()
                        var remain = fileLength
                        otaLength.postValue(fileLength)
                        var current = 0
                        while (true) {
                            val buff = ByteArray(if (512 < fileLength) 512 else remain)
                            val size = input.read(buff)
                            if (size <= 0)
                                break
                            otaList.add(buff)
                            current += size
                            remain -= size
                            otaCurrent.postValue(current)
                            delay(1)
                        }
                        //viewModel.startOTA(list, fileLength)
                    }
                }
            }
            catch (e: FileNotFoundException) {
            }
            catch (e: IOException) {
            }
        }
    }
    */

}
