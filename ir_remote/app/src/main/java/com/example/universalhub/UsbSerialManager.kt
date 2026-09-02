package com.example.universalhub

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException

class UsbSerialManager(
    private val context: Context,
    private val onStatusChange: (status: String, isConnected: Boolean) -> Unit,
    private val onDataReceived: (data: String) -> Unit
) : SerialInputOutputManager.Listener {

    private val ACTION_USB_PERMISSION = "com.example.universalhub.USB_PERMISSION"
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null

    val isConnected: Boolean
        get() = usbSerialPort != null && usbSerialPort!!.isOpen

    var connectedDeviceName: String = "None"
        private set

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (granted && device != null) {
                        openDevice(device)
                    } else {
                        onStatusChange("USB Permission Denied", false)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d("UsbSerial", "USB Device attached")
                    connect()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d("UsbSerial", "USB Device detached")
                    disconnect()
                    onStatusChange("USB Disconnected", false)
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        // Attempt initial connection if device is already plugged in
        connect()
    }

    fun unregister() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.w("UsbSerial", "Error unregistering receiver", e)
        }
        disconnect()
    }

    fun connect(userInitiated: Boolean = false) {
        try {
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            if (availableDrivers.isEmpty()) {
                if (userInitiated) {
                    onStatusChange("No USB Device Found", false)
                }
                return
            }

            val driver = availableDrivers[0]
            val device = driver.device

            if (!usbManager.hasPermission(device)) {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
                usbManager.requestPermission(device, permissionIntent)
                onStatusChange("Requesting USB Permission...", false)
                return
            }

            openDevice(device)
        } catch (e: Exception) {
            Log.e("UsbSerial", "Connection scan error", e)
            if (userInitiated) {
                onStatusChange("USB Error: ${e.message}", false)
            }
        }
    }

    private fun openDevice(device: UsbDevice) {
        try {
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val driver = availableDrivers.firstOrNull { it.device.deviceId == device.deviceId } ?: return

            val connection = usbManager.openDevice(driver.device) ?: run {
                onStatusChange("Cannot open USB device connection", false)
                return
            }

            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port.dtr = true
            port.rts = true

            usbSerialPort = port

            usbIoManager = SerialInputOutputManager(port, this).apply {
                start()
            }

            connectedDeviceName = when {
                device.vendorId == 0x2341 || device.vendorId == 0x2A03 -> "Arduino Uno"
                device.vendorId == 0x10C4 || device.vendorId == 0x303A -> "ESP32"
                device.vendorId == 0x1A86 -> "Arduino/ESP32 (CH340)"
                device.vendorId == 0x0403 -> "Arduino/ESP32 (FTDI)"
                else -> "USB Serial Device"
            }

            onStatusChange("Connected: $connectedDeviceName", true)
            Log.d("UsbSerial", "Connected to $connectedDeviceName at 115200 baud")
        } catch (e: Exception) {
            Log.e("UsbSerial", "Error opening USB serial port", e)
            onStatusChange("USB Error: ${e.message}", false)
            disconnect()
        }
    }

    fun sendCommand(data: String): Boolean {
        val port = usbSerialPort
        if (port == null || !port.isOpen) return false
        return try {
            val bytes = data.toByteArray()
            port.write(bytes, 2000)
            Log.d("UsbSerial", "Transmitted via USB: $data")
            true
        } catch (e: IOException) {
            Log.e("UsbSerial", "USB Write error", e)
            false
        }
    }

    fun disconnect() {
        try {
            usbIoManager?.stop()
            usbIoManager = null
            usbSerialPort?.close()
            usbSerialPort = null
            connectedDeviceName = "None"
        } catch (e: Exception) {
            Log.e("UsbSerial", "Disconnect error", e)
        }
    }

    private val rxBuffer = StringBuilder()

    override fun onNewData(data: ByteArray?) {
        if (data == null) return
        val str = String(data)
        val linesToDeliver = mutableListOf<String>()

        synchronized(rxBuffer) {
            rxBuffer.append(str)
            if (rxBuffer.length > 32768) {
                rxBuffer.setLength(0) // Prevent memory exhaustion on corrupted stream
            }
            while (rxBuffer.contains("\n")) {
                val idx = rxBuffer.indexOf("\n")
                val line = rxBuffer.substring(0, idx).trim()
                rxBuffer.delete(0, idx + 1)
                if (line.isNotEmpty()) {
                    linesToDeliver.add(line)
                }
            }
        }

        for (line in linesToDeliver) {
            Log.d("UsbSerial", "RX: $line")
            onDataReceived(line)
        }
    }

    override fun onRunError(e: Exception?) {
        Log.e("UsbSerial", "IO Manager error", e)
        disconnect()
        onStatusChange("USB Connection Lost", false)
    }
}
