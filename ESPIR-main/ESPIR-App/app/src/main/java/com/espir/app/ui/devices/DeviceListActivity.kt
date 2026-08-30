package com.espir.app.ui.devices

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.espir.app.R

class DeviceListActivity : AppCompatActivity() {
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private val devicesList = ArrayList<BluetoothDevice>()
    private lateinit var adapter: DeviceAdapter
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device != null && !devicesList.contains(device)) {
                devicesList.add(device)
                adapter.notifyDataSetChanged()
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Toast.makeText(this@DeviceListActivity, "Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.device_list_title)
        
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        scanner = bluetoothAdapter?.bluetoothLeScanner
        
        if (bluetoothAdapter == null || scanner == null) {
            Toast.makeText(this, "Bluetooth LE not available", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        val recyclerView = findViewById<RecyclerView>(R.id.deviceRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = DeviceAdapter(devicesList) { device ->
            stopScanning()
            val intent = Intent()
            intent.putExtra("device_address", device.address)
            setResult(RESULT_OK, intent)
            finish()
        }
        recyclerView.adapter = adapter
    }
    
    override fun onResume() {
        super.onResume()
        devicesList.clear()
        adapter.notifyDataSetChanged()
        startScanning()
    }
    
    override fun onPause() {
        super.onPause()
        stopScanning()
    }
    
    private fun startScanning() {
        if (scanning) return
        scanning = true
        handler.postDelayed({ stopScanning() }, 15000) // Scan for 15 seconds
        try {
            scanner?.startScan(scanCallback)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Scan permission missing", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun stopScanning() {
        if (!scanning) return
        scanning = false
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            // Ignore
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private class DeviceAdapter(
        private val devices: List<BluetoothDevice>,
        private val onClick: (BluetoothDevice) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(48, 36, 48, 36)
            }
            
            val nameView = TextView(context).apply {
                textSize = 18f
                setTextColor(android.graphics.Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            val addressView = TextView(context).apply {
                textSize = 14f
                setTextColor(android.graphics.Color.LTGRAY)
                setPadding(0, 8, 0, 0)
            }
            
            layout.addView(nameView)
            layout.addView(addressView)
            
            return ViewHolder(layout, nameView, addressView)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            try {
                holder.nameView.text = device.name ?: "Unknown Device"
            } catch (e: SecurityException) {
                holder.nameView.text = "Permission Denied"
            }
            holder.addressView.text = device.address
            holder.itemView.setOnClickListener { onClick(device) }
        }
        
        override fun getItemCount(): Int = devices.size
        
        class ViewHolder(
            view: View,
            val nameView: TextView,
            val addressView: TextView
        ) : RecyclerView.ViewHolder(view)
    }
}