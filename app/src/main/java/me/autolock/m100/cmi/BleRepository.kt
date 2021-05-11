package me.autolock.m100.cmi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.MutableLiveData
import java.util.*
import kotlin.concurrent.schedule

fun outputLogLine(str: String) {
    handler?.obtainMessage(1, str)?.sendToTarget()
}

class BleRepository {
    // ble manager
    val bleManager: BluetoothManager =
        CmiApplication.applicationContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    // ble adapter
    val bleAdapter: BluetoothAdapter?
        get() = bleManager.adapter
    // ble Gatt
    private var bleGatt: BluetoothGatt? = null

   // val scanning = MutableLiveData(Event(false))
    val scanning = MutableLiveData<Boolean>(false)

    fun startScan() {

        /*
        // check ble adapter and ble enabled
        if (bleAdapter == null || !bleAdapter?.isEnabled!!) {
            requestEnableBLE.postValue(Event(true))
            statusTxt ="Scanning Failed: ble not enabled"
            isStatusChange = true
            return
        }
        //scan filter
        val filters: MutableList<ScanFilter> = ArrayList()
        val scanFilter: ScanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(SERVICE_STRING)))
            .build()
        filters.add(scanFilter)
        // scan settings
        // set low power scan mode
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()
        // start scan
        bleAdapter?.bluetoothLeScanner?.startScan(filters, settings, BLEScanCallback)
        //bleAdapter?.bluetoothLeScanner?.startScan(BLEScanCallback)

        statusTxt = "Scanning...."
        isStatusChange = true
        isScanning.postValue(Event(true))

        Timer("SettingUp", false).schedule(3000) { stopScan() }

         */

        outputLogLine("Scanning started...")
        scanning.postValue(true)
        Timer("SettingUp", false).schedule(3000) { stopScan() }
    }

    private fun stopScan() {
        outputLogLine("Scanning stopped...")
        scanning.postValue(false)
    }

}
