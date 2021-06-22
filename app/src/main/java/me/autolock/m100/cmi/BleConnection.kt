package me.autolock.m100.cmi

import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.Exception
import java.util.*
import kotlin.coroutines.CoroutineContext

class BleConnection(private val appContext: Context, private val bleDevice: BluetoothDevice, private val defaultTimeout: Long = 5_000L): CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    private var bluetoothGatt: BluetoothGatt? = null
    private fun requireGatt(): BluetoothGatt = bluetoothGatt ?: error("Call connect() first!")
    private val cxMap = mutableMapOf<String, BluetoothGattCharacteristic>()

    /*private val connectChannel = Channel<Response<Int>>()
    private val mtuChannel = Channel<Response<Int>>()
    private val rssiChannel = Channel<Response<Int>>()
    private val serviceChannel = Channel<Response<List<BluetoothGattService>>>()
    private val readDescriptorChannel = Channel<Response<BluetoothGattDescriptor>>()
    private val writeDescriptorChannel = Channel<Response<BluetoothGattDescriptor>>()
    private val readChannel = Channel<Response<BluetoothGattCharacteristic>>()
    private val writeChannel = Channel<Response<BluetoothGattCharacteristic>>()
    private val changedChannel = Channel<Response<BluetoothGattCharacteristic>>()*/

    private companion object {
        private const val CONNECT = 0
        private const val MTU = 1
        private const val RSSI = 2
        private const val SERVICE = 3
        private const val READ_DESCRIPTOR = 4
        private const val WRITE_DESCRIPTOR = 5
        private const val READ = 6
        private const val WRITE = 7
        private const val CHANGED = 8
    }

    private val channels = arrayOf(
        /*CONNECT*/         Channel<Response<Int>>(),
        /*MTU*/             Channel<Response<Int>>(),
        /*RSSI*/            Channel<Response<Int>>(),
        /*SERVICE*/         Channel<Response<List<BluetoothGattService>>>(),
        /*READ_DESCRIPTOR*/ Channel<Response<BluetoothGattDescriptor>>(),
        /*WRITE_DESCRIPTOR*/Channel<Response<BluetoothGattDescriptor>>(),
        /*READ*/            Channel<Response<BluetoothGattCharacteristic>>(),
        /*WRITE*/           Channel<Response<BluetoothGattCharacteristic>>(),
        /*CHANGED*/         Channel<Response<BluetoothGattCharacteristic>>()
    )

    @Suppress("UNCHECKED_CAST")
    private suspend inline fun <E> Any.send(e: E, s: Int) = (this as Channel<Response<E>>).send(Response(e, s))
    //private suspend inline fun <E> Any.receive(e: E, s: Int): E = (this as Channel<Response<E>>).receive().e
    private inline fun <reified E> Any.resume(e: E, status: Int) {
        launch {
            send(e, status)
        }
    }

    private val opMutex = Mutex()

    private val isClosed = false

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnectGattServer()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // update the connection status message
                outputLogLine("Connected to the GATT server")
                channels[CONNECT].resume(newState, status)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            outputLogLine("onMtuChanged")
            channels[MTU].resume(mtu, status)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            //rssiChannel.launchAndResume(rssi, status)
            channels[RSSI].resume(rssi, status)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("ble", "service")
            for (service in gatt.services) {
                for (cx in service.characteristics) {
                    cxMap[cx.uuid.toString().uppercase()] = cx
                    Log.d("ble", cx.uuid.toString())
                }
            }
            channels[SERVICE].resume(gatt.services, status)
        }

        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            channels[READ_DESCRIPTOR].resume(descriptor, status)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            outputLogLine("onDescriptorWrite")
            channels[WRITE_DESCRIPTOR].resume(descriptor, status)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            channels[READ].resume(characteristic, status)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            channels[WRITE].resume(characteristic, status)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            channels[CHANGED].resume(characteristic, BluetoothGatt.GATT_SUCCESS)
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
            //reliableWriteChannel.launchAndSendResponse(Unit, status)
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
        return cxMap[uuidString.uppercase()]
    }

    private fun disconnectGattServer() {
        for (ch in channels) {
            ch.close()
        }

        cxMap.clear()
        bluetoothGatt!!.disconnect()
        bluetoothGatt!!.close()
        throw ConnectionClosedException()
    }

    suspend fun connect(timeout: Long? = null) {
        //@@@ import splitties.init.appCtx
        bluetoothGatt = bleDevice.connectGatt(appContext,false, gattCallback)
        channels[CONNECT].receive()
    }

    fun setCharacteristicNotification(rxCharacteristic: BluetoothGattCharacteristic, enable: Boolean) {
        requireGatt().setCharacteristicNotification(rxCharacteristic, enable).if_failed_throw_OperationInitiationFailedException()
    }

    suspend fun setCharacteristicNotificationOnRemote(cx: BluetoothGattCharacteristic, enable: Boolean, timeout: Long? = null) {
        cx.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))?.let { descriptor ->
            descriptor.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            writeDescriptor(descriptor, timeout ?: defaultTimeout)
        }
    }

    suspend fun requestMTU(mtu: Int, timeout: Long? = null) =
        requestAndSuspend(MTU) {
            requestMtu(mtu)
        }

    suspend fun discoverServices(timeout: Long? = null) =
        requestAndSuspend(SERVICE) {
            discoverServices()
        }

    suspend fun writeDescriptor(descriptor: BluetoothGattDescriptor, timeout: Long? = null) =
        requestAndSuspend(WRITE_DESCRIPTOR) {
            writeDescriptor(descriptor)
        }

    suspend fun read(cx: BluetoothGattCharacteristic, timeout: Long? = null) =
        requestAndSuspend(READ) {
            readCharacteristic(cx)
        }

    suspend fun write(cx: BluetoothGattCharacteristic, timeout: Long? = null) =
        requestAndSuspend(WRITE) {
            writeCharacteristic(cx)
        }

    suspend fun writeAndResponse(cx: BluetoothGattCharacteristic, timeout: Long? = null) =
        requestAndSuspend(WRITE, true) {
            writeCharacteristic(cx)
        }

    /*
        wraps the callback's parameters, resumes suspended coroutine bye send.
     */
    private fun <E> SendChannel<Response<E>>.launchAndResume__(e: E, status: Int) {
        launch {
            send(Response(e, status))
        }
    }

    /*
        wraps the function that returns a boolean in Gatt and then suspends until the callback is called.
        wait for one operation to fully complete to avoid Gatt errors
     */
    //private suspend inline fun <reified E> requestAndSuspend(ch: Int, changed: Boolean = false, op: BluetoothGatt.() -> Boolean): E {
    private suspend inline fun requestAndSuspend(ch: Int, changed: Boolean = false, op: BluetoothGatt.() -> Boolean) {
        if_isClosed_throw_ConnectionClosedException()
        opMutex.withLock {
            if_isClosed_throw_ConnectionClosedException()
            requireGatt().op().if_failed_throw_OperationInitiationFailedException()
            val response = channels[ch].receive()
            if (!response.isSuccess) {
                throw OperationFailedException(response.status)
            }
            if (changed) {
                channels[CHANGED].receive()
            }
        }
    }

    /*
        contains the result of the callback
     */
    class Response<out E>(val e: E, val status: Int) {
        inline val isSuccess get() = status == BluetoothGatt.GATT_SUCCESS
    }

    private fun if_isClosed_throw_ConnectionClosedException() {
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
