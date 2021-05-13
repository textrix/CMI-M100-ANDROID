package me.autolock.m100.cmi

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule

const val SERVICE_STRING = "434D492D-4D31-3030-0101-627567696969"
const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"

class BleRepository {
    // ble manager
    val bleManager: BluetoothManager =
        CmiApplication.applicationContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    // ble adapter
    val bleAdapter: BluetoothAdapter?
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
            if (device.name == null || !result.device.name.startsWith("CMI-M100")) {
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
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer()
            }
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
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            //Log.d(TAG, "characteristic changed: " + characteristic.uuid.toString())
            readCharacteristic(characteristic)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                outputLogLine("Characteristic written successfully")
            } else {
                outputLogLine("Characteristic write unsuccessful, status: $status")
                disconnectGattServer()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                outputLogLine("Characteristic read successfully")
                readCharacteristic(characteristic)
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
            if (BleUtil.matchReportCharacteristic(characteristic)) {
                val bytes = characteristic.value
                outputLogLine(bytes.toHexString())
                reportArray.postValue(bytes)
            }
            else {
                val msg = characteristic.getStringValue(0)
                outputLogLine("read: $msg")
            }
        }
    }

    /**
     * Connect to the ble device
     */
    fun connectDevice(device: BluetoothDevice?) {
        // update the status
        outputLogLine("Connecting to ${device?.address}")
        bleGatt = device?.connectGatt(CmiApplication.applicationContext(), false, gattClientCallback)
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

    fun writeData(rxByteArray: ByteArray){
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

    fun readData() {
        val reportCharacteristic = BleUtil.findReportCharacteristic(bleGatt!!)
        // disconnect if the characteristic is not found
        if (reportCharacteristic == null) {
            outputLogLine("Unable to find report characteristic")
            disconnectGattServer()
            return
        }

        val success: Boolean = bleGatt!!.readCharacteristic(reportCharacteristic)
        if (!success) {
            outputLogLine("Failed to read command")
        }
    }
}
