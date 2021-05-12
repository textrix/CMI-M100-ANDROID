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
            /*
            // find command characteristics from the GATT server
            val respCharacteristic = gatt?.let { BluetoothUtils.findResponseCharacteristic(it) }
            // disconnect if the characteristic is not found
            if (respCharacteristic == null) {
                outputLogLine("Unable to find cmd characteristic")
                disconnectGattServer()
                return
            }
            */

            //gatt.setCharacteristicNotification(respCharacteristic, true)

            /*
            // UUID for notification
            val descriptor: BluetoothGattDescriptor = respCharacteristic.getDescriptor(
                UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)
            )
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)

             */
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

}
