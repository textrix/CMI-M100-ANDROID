package me.autolock.m100.cmi

import android.bluetooth.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.Exception
import java.util.*
import kotlin.coroutines.CoroutineContext

class BleConnection(private val bleDevice: BluetoothDevice): CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    private var bluetoothGatt: BluetoothGatt? = null
    private fun requireGatt(): BluetoothGatt = bluetoothGatt ?: error("Call connect() first!")

    private val connectChannel = Channel<Int>()
    private val mtuChannel = Channel<Response<Int>>()
    private val rssiChannel = Channel<Response<Int>>()
    private val serviceChannel = Channel<Response<List<BluetoothGattService>>>()
    private val readDescriptorChannel = Channel<Response<BluetoothGattDescriptor>>()
    private val writeDescriptorChannel = Channel<Response<BluetoothGattDescriptor>>()
    private val readChannel = Channel<Response<BluetoothGattCharacteristic>>()
    private val writeChannel = Channel<Response<BluetoothGattCharacteristic>>()
    private val changedChannel = Channel<Response<BluetoothGattCharacteristic>>()

    private val opMutex = Mutex()

    private val isClosed = false

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
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
                    connectChannel.send(0)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer()
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            rssiChannel.launchAndResume(rssi, status)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            //servicesDiscoveryChannel.launchAndSendResponse(gatt.services, status)
            outputLogLine("onServicesDiscovered")
            serviceChannel.launchAndResume(gatt.services, status)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            readChannel.launchAndResume(characteristic, status)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            //outputLogLine("onCharacteristicWrite")
            //Log.d("ble", "onCharacteristicWrite")
            writeChannel.launchAndResume(characteristic, status)
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
            //reliableWriteChannel.launchAndSendResponse(Unit, status)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            //outputLogLine("onCharacteristicChanged")
            //Log.d("ble", "onCharacteristicChanged")
            changedChannel.launchAndResume(characteristic, BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            readDescriptorChannel.launchAndResume(descriptor, status)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            outputLogLine("onDescriptorWrite")
            writeDescriptorChannel.launchAndResume(descriptor, status)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            outputLogLine("onMtuChanged")
            mtuChannel.launchAndResume(mtu, status)
        }

        override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            /*phyReadChannel.launchAndSendResponse(
                GattConnection.Phy(
                    tx = txPhy,
                    rx = rxPhy
                ), status
            )*/
        }
    }

    fun findCharacteristic(uuidString: String): BluetoothGattCharacteristic? {
        val gatt = bluetoothGatt
        gatt?.let {
            for (service in gatt.services) {
                for (characteristic in service.characteristics) {
                    if (characteristic.uuid.toString().uppercase() == uuidString) {
                        return characteristic
                    }
                }
            }
        }
        return null
    }

    private fun disconnectGattServer() {
        mtuChannel.close()
        rssiChannel.close()
        serviceChannel.close()
        readDescriptorChannel.close()
        writeDescriptorChannel.close()
        readChannel.close()
        writeChannel.close()
        changedChannel.close()

        bluetoothGatt!!.disconnect()
        bluetoothGatt!!.close()
        throw ConnectionClosedException()
    }

    suspend fun connect(timeout: Long = 3_000L) {
        //@@@ import splitties.init.appCtx
        bluetoothGatt = bleDevice.connectGatt(CmiApplication.applicationContext(),false, gattCallback)
        connectChannel.receive()
    }

    fun setCharacteristicNotification(rxCharacteristic: BluetoothGattCharacteristic, enable: Boolean) {
        requireGatt().setCharacteristicNotification(rxCharacteristic, enable).if_failed_throw_OperationInitiationFailedException()
    }

    suspend fun setCharacteristicNotificationOnRemote(charx: BluetoothGattCharacteristic, enable: Boolean, timeout: Long) {
        charx.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))?.let { descriptor ->
            descriptor.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            writeDescriptor(descriptor, timeout)
        }
    }

    suspend fun requestMTU(mtu: Int, timeout: Long) = requestAndSuspend(mtuChannel) {
        requestMtu(mtu)
    }

    suspend fun discoverServices(timeout: Long) = requestAndSuspend(serviceChannel) {
        discoverServices()
    }

    suspend fun writeDescriptor(descriptor: BluetoothGattDescriptor, timeout: Long) = requestAndSuspend(writeDescriptorChannel) {
        writeDescriptor(descriptor)
    }

    suspend fun read(charx: BluetoothGattCharacteristic) = requestAndSuspend(readChannel) {
        readCharacteristic(charx)
    }

    suspend fun write(charx: BluetoothGattCharacteristic) = requestAndSuspend(writeChannel) {
        writeCharacteristic(charx)
    }

    suspend fun writeAndResponse(cx: BluetoothGattCharacteristic) = requestAndSuspend(writeChannel, true) {
        writeCharacteristic(cx)
    }

    suspend fun writeAndResponse1(cx: BluetoothGattCharacteristic) {
        if_isClosed_throw_ConnectionClosedException()
        return opMutex.withLock {
            if_isClosed_throw_ConnectionClosedException()
            requireGatt().writeCharacteristic(cx).if_failed_throw_OperationInitiationFailedException()

            var count = 0
            var response: Response<BluetoothGattCharacteristic>? = null
            while (count < 2) {
                response = select<Response<BluetoothGattCharacteristic>> {
                    writeChannel.onReceive {
                        ++count
                        it
                    }
                    changedChannel.onReceive {
                        ++count
                        it
                    }
                }
            }
            if (!response!!.isSuccess) {
                throw OperationFailedException(response.status)
            }
            response.e
        }
    }

    /*
        wraps the callback's parameters, resumes suspended coroutine bye send.
     */
    private fun <E> SendChannel<Response<E>>.launchAndResume(e: E, status: Int) {
        launch {
            send(Response(e, status))
        }
    }

    /*
        wraps the function that returns a boolean in Gatt and then suspends until the callback is called.
        wait for one operation to fully complete to avoid Gatt errors
     */
    private suspend inline fun <E> requestAndSuspend(ch: ReceiveChannel<Response<E>>, changed: Boolean = false, op: BluetoothGatt.() -> Boolean): E {
        if_isClosed_throw_ConnectionClosedException()
        return opMutex.withLock {
            if_isClosed_throw_ConnectionClosedException()
            requireGatt().op().if_failed_throw_OperationInitiationFailedException()
            val response = ch.receive()
            if (changed) {
                changedChannel.receive()
            }
            if (!response.isSuccess) {
                throw OperationFailedException(response.status)
            }
            response.e
        }
    }

    /*
        contains the result of the callback
     */
    private class Response<out E>(val e: E, val status: Int) {
        inline val isSuccess get() = status == BluetoothGatt.GATT_SUCCESS
    }

    private inline fun if_isClosed_throw_ConnectionClosedException() {
        if (isClosed) throw ConnectionClosedException()
    }

    private fun Boolean.if_failed_throw_OperationInitiationFailedException() {
        if (!this) throw OperationInitiationFailedException()
    }
}

sealed class GattException(message: String? = null) : Exception(message) {
    companion object {
        /**
         * See all codes here: [https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-5.1.0_r1/stack/include/gatt_api.h]
         */
        fun statusText(status: Int) = when (status) {
            BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> "GATT_READ_NOT_PERMITTED"
            BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "GATT_WRITE_NOT_PERMITTED"
            BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> "GATT_INSUFFICIENT_AUTHENTICATION"
            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "GATT_REQUEST_NOT_SUPPORTED"
            BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "GATT_INSUFFICIENT_ENCRYPTION"
            BluetoothGatt.GATT_INVALID_OFFSET -> "GATT_INVALID_OFFSET"
            BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "GATT_INVALID_ATTRIBUTE_LENGTH"
            BluetoothGatt.GATT_CONNECTION_CONGESTED -> "GATT_CONNECTION_CONGESTED"
            BluetoothGatt.GATT_FAILURE -> "GATT_FAILURE"
            else -> "$status"
        }
    }
}

class OperationInitiationFailedException : GattException()

class OperationFailedException(status: Int)
    : GattException("status: ${statusText(status)}")

class ConnectionClosedException internal constructor(cause: Throwable? = null, messageSuffix: String = "")
    : CancellationException("The connection has been irrevocably closed. $messageSuffix.") {
    init {
        initCause(cause)
    }
}
