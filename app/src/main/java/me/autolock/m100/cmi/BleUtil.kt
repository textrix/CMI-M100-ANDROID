package me.autolock.m100.cmi

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import java.util.*


const val CHARACTERISTIC_REPORT_STRING = "434D492D-4D31-3030-0102-627567696969"
const val CHARACTERISTIC_RX_STRING = "434D492D-4D31-3030-0103-627567696969"
const val CHARACTERISTIC_VERSiON_STRING = "434D492D-464F-5441-0102-627567696969"


class BleUtil {
    companion object {
        //fun findCharacteristic(bleGatt: BluetoothGatt?, uuidString: String): BluetoothGattCharacteristic? {

        //}

        /**
         * Find characteristics of BLE
         * @param gatt gatt instance
         * @return list of found gatt characteristics
         */
        fun findBLECharacteristics(gatt: BluetoothGatt): List<BluetoothGattCharacteristic> {
            val matchingCharacteristics: MutableList<BluetoothGattCharacteristic> = ArrayList()
            val serviceList = gatt.services
            val service = findGattService(serviceList) ?: return matchingCharacteristics
            val characteristicList = service.characteristics
            for (characteristic in characteristicList) {
                if (isMatchingCharacteristic(characteristic)) {
                    matchingCharacteristics.add(characteristic)
                }
            }
            return matchingCharacteristics
        }

        /**
         * Find report characteristic of the peripheral device
         * @param gatt gatt instance
         * @return found characteristic
         */
        fun findReportCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
            return findCharacteristic(gatt, CHARACTERISTIC_REPORT_STRING)
        }

        /**
         * Find rx characteristic of the peripheral device
         * @param gatt gatt instance
         * @return found characteristic
         */
        fun findRxCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
            return findCharacteristic(gatt, CHARACTERISTIC_RX_STRING)
        }

        fun findVersionCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
            return findCharacteristic(gatt, CHARACTERISTIC_VERSiON_STRING)
        }

        /**
         * Try to match the given uuid with the service uuid
         * @param serviceUuidString service UUID as string
         * @return true if service uuid is matched
         */
        private fun matchServiceUUIDString(serviceUuidString: String): Boolean {
            return matchUUIDs(serviceUuidString, SERVICE_STRING) or matchUUIDs(serviceUuidString, SERVICE_OTA_STRING)
        }

        /**
         * Query the given uuid as string to the provided characteristics by the server
         * @param characteristicUuidString query uuid as string
         * @return true if the matched is found
         */
        private fun matchCharacteristicUUID(characteristicUuidString: String): Boolean {
            return matchUUIDs(
                characteristicUuidString,
                CHARACTERISTIC_REPORT_STRING,
                CHARACTERISTIC_RX_STRING,
                CHARACTERISTIC_VERSiON_STRING
            )
        }

        /**
         * Try to match a uuid with the given set of uuid
         * @param uuidString uuid to query
         * @param matches a set of uuid
         * @return true if matched
         */
        private fun matchUUIDs(uuidString: String, vararg matches: String): Boolean {
            for (match in matches) {
                if (uuidString.equals(match, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        /**
         * Find Gatt service that matches with the server's service
         * @param serviceList list of services
         * @return matched service if found
         */
        private fun findGattService(serviceList: List<BluetoothGattService>): BluetoothGattService? {
            for (service in serviceList) {
                val serviceUuidString = service.uuid.toString()
                if (matchServiceUUIDString(serviceUuidString)) {
                    return service
                }
            }
            return null
        }

        /**
         * Find the given uuid characteristic
         * @param gatt gatt instance
         * @param uuidString uuid to query as string
         */
        fun findCharacteristic(gatt: BluetoothGatt, uuidString: String): BluetoothGattCharacteristic? {
            val serviceList = gatt.services
            for (service in serviceList) {
                val characteristicList = service.characteristics
                for (characteristic in characteristicList) {
                    if (matchCharacteristic(characteristic, uuidString)) {
                        return characteristic
                    }
                }
            }
            return null
        }

        /**
         * Check if there is any matching characteristic
         * @param characteristic query characteristic
         */
        private fun isMatchingCharacteristic(characteristic: BluetoothGattCharacteristic?): Boolean {
            if (characteristic == null) {
                return false
            }
            val uuid: UUID = characteristic.uuid
            return matchCharacteristicUUID(uuid.toString())
        }

        /**
         * Match the given characteristic and a uuid string
         * @param characteristic one of found characteristic provided by the server
         * @param uuidString uuid as string to match
         * @return true if matched
         */
        private fun matchCharacteristic(characteristic: BluetoothGattCharacteristic?, uuidString: String): Boolean {
            if (characteristic == null) {
                return false
            }
            val uuid: UUID = characteristic.uuid
            return matchUUIDs(uuid.toString(), uuidString)
        }
    }
}