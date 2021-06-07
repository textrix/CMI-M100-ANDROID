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
            if (device.name == null || !device.name.startsWith("CMI-M100")) {
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
     * BLE gattClientCallback
     */
    private val gattClientCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            outputLogLine("connection state: $status $newState")
            if (status == BluetoothGatt.GATT_FAILURE) {
                disconnectGattServer()
                return
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnectGattServer()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // update the connection status message
                outputLogLine("Connected to the GATT server")
                launch {
                    val result = withTimeoutOrNull(1000L) {
                        gatt.requestMtu(517)
                        Log.d("ble", "gatt.requestMtu begin")
                        mtuChannel.receive()
                        Log.d("ble", "gatt.requestMtu end")
                        gatt.discoverServices()
                        //mtuChannel.receive()
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d("ble", "onMtuChanged")
            launch {
                if (mtuChannel.trySend(0).isFailure) {
                    outputLogLine("Changing MTU failed.")
                }
            }
            //gatt?.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            // check if the discovery failed
            if (status != BluetoothGatt.GATT_SUCCESS) {
                outputLogLine("Device service discovery failed, status: $status")
                return
            }

            // log for successful discovery
            outputLogLine("Services discovery is successful")
            connected.postValue(true)

            // find report characteristics from the GATT server
            val rxCharacteristic = gatt?.let { BleUtil.findRxCharacteristic(it) }
            // disconnect if the characteristic is not found
            if (rxCharacteristic == null) {
                outputLogLine("Unable to find report characteristic")
                disconnectGattServer()
                return
            }

            gatt.setCharacteristicNotification(rxCharacteristic, true)

            // UUID for notification
            val descriptor: BluetoothGattDescriptor = rxCharacteristic.getDescriptor(
                UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)
            )
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)

            val verCharacteristic = gatt?.let { BleUtil.findVersionCharacteristic(it) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            outputLogLine("${descriptor?.characteristic?.uuid.toString().uppercase()} onDescriptorWrite")
            if (descriptor?.characteristic?.uuid.toString().uppercase() == CHARACTERISTIC_RX_STRING) {
                // find ota characteristics from the GATT server
                val otaCharacteristic = gatt?.let { BleUtil.findOtaCharacteristic(it) }
                // disconnect if the characteristic is not found
                if (otaCharacteristic == null) {
                    outputLogLine("Unable to find ota characteristic")
                    disconnectGattServer()
                    return
                }

                gatt.setCharacteristicNotification(otaCharacteristic, true)

                // UUID for notification
                val otaDescriptor: BluetoothGattDescriptor = otaCharacteristic.getDescriptor(
                    UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)
                )
                otaDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(otaDescriptor)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            //Log.d(TAG, "characteristic changed: " + characteristic.uuid.toString())
            readCharacteristic(characteristic)

            if (characteristic.uuid.toString().uppercase() == CHARACTERISTIC_FOTA_STRING) {
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //outputLogLine("Characteristic written successfully")
                when(characteristic?.uuid.toString().uppercase()) {
                    CHARACTERISTIC_FOTA_STRING -> {
                        otaCurrentObserver.postValue(otaCurrent)
                        otaList.removeAt(0)
                        writeOTA()
                    }
                }
            } else {
                outputLogLine("Characteristic write unsuccessful, status: $status")
                disconnectGattServer()
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //outputLogLine("Characteristic read successfully")
                readCharacteristic(characteristic)
                when(characteristic.uuid.toString().uppercase()) {
                    CHARACTERISTIC_FOTA_STRING -> {

                    }
                }
            } else {
                outputLogLine("Characteristic read unsuccessful, status: $status")
                // Trying to read from the Time Characteristic? It doesnt have the property or permissions
                // set to allow this. Normally this would be an error and you would want to:
                // disconnectGattServer()
            }
        }

        /**
         * Log the value of the characteristic
         * @param characteristic
         */
        private fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
            when(characteristic.uuid.toString().uppercase()) {
                CHARACTERISTIC_REPORT_STRING -> {
                    val bytes = characteristic.value
                    //outputLogLine(bytes.toHexString())
                    reportArray.postValue(bytes)
                }
                CHARACTERISTIC_RX_STRING -> {
                    val msg = characteristic.getStringValue(0)
                    outputLogLine("read: $msg")
                }
                CHARACTERISTIC_VERSiON_STRING -> {
                    val ver = characteristic.getStringValue(0)
                    version.postValue(ver)
                }
                CHARACTERISTIC_FOTA_STRING -> {
                    val result = characteristic.getStringValue(0)
                }
            }
        }
    }

    /**
     * Connect to the ble device
     */
    fun connectDevice(device: BluetoothDevice?) {
        launch(Dispatchers.IO) {
            // update the status
            outputLogLine("Connecting to ${device?.address}")
            device?.let {
                bleGatt = device.connectGatt(CmiApplication.applicationContext(),false, gattClientCallback)
                mtuChannel.receive()
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

    fun writeData(rxByteArray: ByteArray) {
        val rxCharacteristic = BleUtil.findRxCharacteristic(bleGatt!!)
        // disconnect if the characteristic is not found
        if (rxCharacteristic == null) {
            outputLogLine("Unable to find rx characteristic")
            disconnectGattServer()
            return
        }

        rxCharacteristic.value = rxByteArray
        val success: Boolean = bleGatt!!.writeCharacteristic(rxCharacteristic)
        // check the result
        if( !success ) {
            outputLogLine("Failed to write command")
        }
    }

    fun read(uuidString: String) {
        val chx = BleUtil.findCharacteristic(bleGatt!!, uuidString)
        if (null == chx) {
            outputLogLine("Unable to find report characteristic")
            disconnectGattServer()
            return
        }

        val success: Boolean = bleGatt!!.readCharacteristic(chx)
        if (!success) {
            outputLogLine("Failed to read command")
        }
    }

    fun startOTA(list: MutableList<ByteArray>, length: Int) {
        otaList = list
        otaLength = length
        otaCurrent = 0
        otaLengthObserver.postValue(otaLength)
        otaCurrentObserver.postValue(otaCurrent)
        writeOTA()
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

    /*
    suspend fun requestMtu(mut: Int) {
        runCatching {
            launch {
                mtuChannel.send(0)
            }
        }
    }*/
}

private class GattResponse<out E>(val e: E, val status: Int) {
    inline val isSuccess get() = status == BluetoothGatt.GATT_SUCCESS
}
