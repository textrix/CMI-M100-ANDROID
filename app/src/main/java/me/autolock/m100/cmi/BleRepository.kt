package me.autolock.m100.cmi

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule
import kotlin.coroutines.CoroutineContext

const val SERVICE_STRING = "434D492D-4D31-3030-0101-627567696969"
const val SERVICE_OTA_STRING = "434D492D-464F-5441-0101-627567696969"
const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"
const val OTA_CHUNK_SIZE = 512

/*
sealed class BleOpType {
    abstract val device: BluetoothDevice
}

data class RequestMTU(
    override val device: BluetoothDevice,
    val mtu: Int,
    val result: Int
) : BleOpType()
*/

class BleRepository : CoroutineScope  {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    private var bleConnection: BleConnection? = null

    // ble manager
    private val bleManager: BluetoothManager =
        CmiApplication.applicationContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    // ble adapter
    private val bleAdapter: BluetoothAdapter?
        get() = bleManager.adapter
    // ble Gatt
    private var bleGatt: BluetoothGatt? = null

    //val scanning = MutableLiveData(Event(false))
    val scanning = MutableLiveData<Boolean>(false)
    val connected = MutableLiveData(false)
    //val listUpdate = MutableLiveData<Event<ArrayList<BluetoothDevice>?>>()
    val listUpdate = MutableLiveData<ArrayList<BluetoothDevice>?>()

    // scan results
    var scanResults: ArrayList<BluetoothDevice>? = ArrayList()

    //
    val reportArray = MutableLiveData<ByteArray>()
    val version = MutableLiveData<String>()

    val relayResponse = MutableLiveData<String>()
    val fotaCmdResponse = MutableLiveData<String>()

    private var otaList = mutableListOf<ByteArray>()
    private var otaLength = 0
    private var otaCurrent = 0
    val otaLengthObserver = MutableLiveData<Int>(0)
    val otaCurrentObserver = MutableLiveData<Int>(0)

    private val mtuChannel = Channel<Int>()

    fun startScan() {

        // check ble adapter and ble enabled
        if (bleAdapter == null || !bleAdapter?.isEnabled!!) {
            outputLogLine("Scanning Failed: ble not enabled")
            requestEnableBLE()
            return
        }

        //scan filter
        val filters: MutableList<ScanFilter> = ArrayList()
        /*val scanFilter: ScanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(SERVICE_STRING)))
            .build()
        filters.add(scanFilter)*/

        // scan settings
        // set low power scan mode
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        // start scan
        bleAdapter?.bluetoothLeScanner?.startScan(filters, settings, BLEScanCallback)

        outputLogLine("Scanning started...")
        scanning.postValue(true)
        Timer("SettingUp", false).schedule(3000) { stopScan() }
    }

    private fun stopScan() {
        outputLogLine("Scanning stopped...")
        scanning.postValue(false)
        bleAdapter?.bluetoothLeScanner?.stopScan(BLEScanCallback)
        scanResults = ArrayList()
    }

    /**
     * BLE Scan Callback
     */
    private val BLEScanCallback: ScanCallback = @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            addScanResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results) {
                addScanResult(result)
            }
        }

        override fun onScanFailed(_error: Int) {
            outputLogLine("BLE scan failed with code $_error")
        }

        /**
         * Add scan result
         */
        private fun addScanResult(result: ScanResult) {
            // get scanned device
            val device = result.device
            // filter out devices with name starting with 'CMI-M100'
            if (device.name == null /*|| !device.name.startsWith("CMI-M100")*/) {
                return
            }
            // get scanned device MAC address
            val deviceAddress = device.address
            val deviceName = device.name
            // add the device to the result list
            for (dev in scanResults!!) {
                if (dev.address == deviceAddress) return
            }

            scanResults?.add(result.device)
            outputLogLine("add scanned device: $deviceName $deviceAddress")
            //listUpdate.postValue(Event(scanResults))
            listUpdate.postValue(scanResults)
        }
    }

    /**
     * Connect to the ble device
     */
    fun connectDevice(device: BluetoothDevice) {
        launch {
            // update the status
            outputLogLine("Connecting to ${device?.address}")

            val timeout = 5_000L

            try {
                val connection = BleConnection(device)

                connection.connect(timeout = timeout)

                connection.requestMTU(mtu = 517, timeout = timeout)

                connection.discoverServices(timeout = timeout)

                connection.findCharacteristic(CHARACTERISTIC_RX_STRING)?.let { rx ->
                    connection.setCharacteristicNotification(rx, true)
                    connection.setCharacteristicNotificationOnRemote(rx, true, timeout = timeout)
                }

                connection.findCharacteristic(CHARACTERISTIC_FOTA_STRING)?.let { ota ->
                    connection.setCharacteristicNotification(ota, true)
                    connection.setCharacteristicNotificationOnRemote(ota, true, timeout = timeout)
                }

                connection.findCharacteristic(CHARACTERISTIC_FOTA_CMD_STRING)?.let { ota_cmd ->
                    connection.setCharacteristicNotification(ota_cmd, true)
                    connection.setCharacteristicNotificationOnRemote(ota_cmd, true, timeout = timeout)
                }

                connection.findCharacteristic(CHARACTERISTIC_VERSION_STRING)?.let { ver ->
                    connection.read(ver)
                    val str = ver.getStringValue(0)
                    outputLogLine(str)
                }

                bleConnection = connection
                connected.postValue(true)
            }
            catch (e: Exception) {
                outputLogLine("Exception: ${e.message}")
            }
        }
    }

    /**
     * Disconnect Gatt Server
     */
    fun disconnectGattServer() {
        outputLogLine("Closing Gatt connection")
        // disconnect and close the gatt
        if (bleGatt != null) {
            bleGatt!!.disconnect()
            bleGatt!!.close()
            outputLogLine("Disconnected")
            connected.postValue(false)
        }
    }

    suspend fun read(uuidString: String) {
        //launch {
            try {
                val connection = bleConnection
                connection?.let {
                    connection.findCharacteristic(uuidString)?.let { characteristic ->
                        connection.read(characteristic)
                        when (uuidString) {
                            CHARACTERISTIC_VERSION_STRING -> version.postValue(characteristic.getStringValue(0))
                            CHARACTERISTIC_REPORT_STRING -> reportArray.postValue(characteristic.value)
                        }
                    }
                }
            }
            catch (e: Exception) {
                outputLogLine("Exception: ${e.message}")
            }
        //}
    }

    fun launchRead(uuidString: String) {
        launch {
            read(uuidString)
        }
    }

    suspend fun write(uuidString: String, data: ByteArray) {
        try {
            val connection = bleConnection
            connection?.let {
                connection.findCharacteristic(uuidString)?.let { characteristic ->
                    characteristic.value = data
                    connection.write(characteristic)
                }
            }
        }
        catch (e: Exception) {
            outputLogLine("Exception: ${e.message}")
        }
    }

    suspend fun writeAndResponse(uuidString: String, data: ByteArray) {
        try {
            val connection = bleConnection
            connection?.let {
                connection.findCharacteristic(uuidString)?.let { characteristic ->
                    characteristic.value = data
                    connection.writeAndResponse(characteristic)
                    //Log.d("ble", "write")
                    //connection.changed()
                    //Log.d("ble", "changed")
                    /*when (uuidString) {
                        CHARACTERISTIC_RX_STRING -> relayResponse.postValue(characteristic.getStringValue(0))
                        CHARACTERISTIC_FOTA_CMD_STRING -> fotaCmdResponse.postValue(characteristic.getStringValue(0))
                    }*/
                }
            }
        }
        catch (e: Exception) {
            outputLogLine("Exception: ${e.message}")
        }
    }

    fun launchWrite(uuidString: String, data: ByteArray) {
        launch {
            write(uuidString, data)
        }
    }
    fun launchWriteAndResponse(uuidString: String, data: ByteArray) {
        launch {
            writeAndResponse(uuidString, data)
        }
    }

    fun startOTA(list: MutableList<ByteArray>, length: Int) {
        launch {
            try {
                otaLengthObserver.postValue(length)
                val result = writeAndResponse(CHARACTERISTIC_FOTA_CMD_STRING, "FOTA:Begin".toByteArray())
                var current = 0
                otaCurrentObserver.postValue(current)
                var count = 0
                for (chunk in list) {
                    //val result = write(CHARACTERISTIC_FOTA_STRING, chunk)
                    //delay(2)
                    val result1 = writeAndResponse(CHARACTERISTIC_FOTA_STRING, chunk)
                    bleConnection?.findCharacteristic(CHARACTERISTIC_FOTA_STRING)?.let {
                        if (it?.getStringValue(0).isNotEmpty()) {
                            var str = it.getStringValue(0)
                            Log.d("ble", "$count $str")
                        }
                    }
                    current += chunk.size
                    otaCurrentObserver.postValue(current)
                    ++count
                }
                writeAndResponse(CHARACTERISTIC_FOTA_CMD_STRING, "FOTA:End".toByteArray())
            }
            catch (e: Exception) {
                outputLogLine("Exception: ${e.message}")
            }
        }
    }

    private fun writeOTA() {
        val otaCharacteristic = BleUtil.findOtaCharacteristic(bleGatt!!)
        // disconnect if the characteristic is not found
        if (otaCharacteristic == null) {
            outputLogLine("Unable to find ota characteristic")
            disconnectGattServer()
            return
        }

        if (otaList.isNotEmpty()) {
            otaCurrentObserver.postValue(otaCurrent)
            otaCurrent += otaList[0].size
            otaCharacteristic.value = otaList[0]
            val success: Boolean = bleGatt!!.writeCharacteristic(otaCharacteristic)
            // check the result
            if (!success) {
                outputLogLine("Failed to write command")
                //Timer().schedule(100) { writeOTA() }
            }
        }
        else {
            // end
        }
    }
}

private class GattResponse<out E>(val e: E, val status: Int) {
    inline val isSuccess get() = status == BluetoothGatt.GATT_SUCCESS
}
