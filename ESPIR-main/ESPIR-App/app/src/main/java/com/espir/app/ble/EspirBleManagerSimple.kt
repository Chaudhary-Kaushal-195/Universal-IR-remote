package com.espir.app.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.UUID

class EspirBleManagerSimple(context: Context) : BleManager(context), ConnectionObserver {
    
    init {
        setConnectionObserver(this)
    }
    
    companion object {
        private const val TAG = "EspirBleManagerSimple"
        private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        private val CHARACTERISTIC_UUID = UUID.fromString("87654321-4321-4321-4321-cba987654321")
        
        @Volatile
        private var INSTANCE: EspirBleManagerSimple? = null
        
        fun getInstance(context: Context): EspirBleManagerSimple {
            return INSTANCE ?: synchronized(this) {
                val instance = EspirBleManagerSimple(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState
    
    private var characteristic: BluetoothGattCharacteristic? = null
    
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }
    
    override fun getGattCallback(): BleManagerGattCallback {
        return EspirGattCallback()
    }
    
    private inner class EspirGattCallback : BleManagerGattCallback() {
        
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service = gatt.getService(SERVICE_UUID)
            if (service != null) {
                characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
            }
            return characteristic != null
        }
        
        override fun onServicesInvalidated() {
            characteristic = null
        }
        
        override fun initialize() {
            if (characteristic != null) {
                setNotificationCallback(characteristic).with { device, data ->
                    val text = data.getStringValue(0)
                    if (text != null) {
                        responseCallback?.invoke(text)
                    }
                }
                enableNotifications(characteristic).enqueue()
            }
        }
    }
    
    // Connection state changed internally in BleManager
    fun onConnectionStateChanged(device: BluetoothDevice, state: ConnectionState) {
        _connectionState.postValue(state)
    }

    override fun onDeviceConnecting(device: BluetoothDevice) {
        onConnectionStateChanged(device, ConnectionState.CONNECTING)
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        onConnectionStateChanged(device, ConnectionState.CONNECTED)
    }

    override fun onDeviceDisconnecting(device: BluetoothDevice) {
        onConnectionStateChanged(device, ConnectionState.DISCONNECTING)
    }

    override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
        onConnectionStateChanged(device, ConnectionState.DISCONNECTED)
    }

    override fun onDeviceReady(device: BluetoothDevice) {
        Log.d(TAG, "Device ready: ${device.address}")
    }

    override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
        Log.e(TAG, "Device failed to connect: ${device.address}, reason: $reason")
        onConnectionStateChanged(device, ConnectionState.DISCONNECTED)
    }
    
    // Simple command sending implementation
    fun sendCommand(command: String) {
        Log.d(TAG, "Sending command: $command")
        if (characteristic != null) {
            writeCharacteristic(characteristic, command.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .with { _, _ -> Log.d(TAG, "Command sent successfully: $command") }
                .fail { _, status -> Log.e(TAG, "Failed to send command: $status") }
                .enqueue()
        } else {
            Log.e(TAG, "Cannot send command, not connected or characteristic not found")
        }
    }
    
    // Simple response callback implementation
    private var responseCallback: ((String) -> Unit)? = null
    
    fun setResponseCallback(callback: (String) -> Unit) {
        responseCallback = callback
    }
    
    // Bluetooth enablement check
    fun isEnabled(): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        return adapter?.isEnabled == true
    }
}