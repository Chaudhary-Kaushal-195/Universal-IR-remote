package com.espir.app.ui.devices

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.espir.app.R
import com.espir.app.data.Device
import com.espir.app.viewmodel.MainViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DevicesFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var recyclerView: RecyclerView
    private val devicesList = ArrayList<Device>()
    private lateinit var adapter: DevicesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_devices, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        
        recyclerView = view.findViewById(R.id.devices_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        adapter = DevicesAdapter(devicesList) { device ->
            val intent = Intent(context, DeviceControlActivity::class.java).apply {
                putExtra("device_id", device.id)
                putExtra("device_name", device.name)
                putExtra("device_type", device.type)
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        
        // Observe device list and update adapter
        viewModel.devices.observe(viewLifecycleOwner) { devices ->
            devicesList.clear()
            devicesList.addAll(devices)
            adapter.notifyDataSetChanged()
        }
        
        val fabAddDevice = view.findViewById<FloatingActionButton>(R.id.fab_add_device)
        fabAddDevice.setOnClickListener {
            showAddDeviceDialog()
        }
    }
    
    private fun showAddDeviceDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val nameInput = EditText(context).apply {
            hint = "Device Name (e.g. Living Room TV)"
            setSingleLine()
        }

        val typeInput = EditText(context).apply {
            hint = "Device Type (e.g. TV, AC)"
            setSingleLine()
        }

        layout.addView(nameInput)
        
        // Add divider/spacing programmatically
        val spacing = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 24)
        }
        layout.addView(spacing)
        layout.addView(typeInput)

        AlertDialog.Builder(context)
            .setTitle("Add New Device")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val type = typeInput.text.toString().trim()
                if (name.isNotEmpty() && type.isNotEmpty()) {
                    viewModel.addDevice(name, type)
                } else {
                    Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private class DevicesAdapter(
        private val devices: List<Device>,
        private val onClick: (Device) -> Unit
    ) : RecyclerView.Adapter<DevicesAdapter.ViewHolder>() {
        
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
            
            val typeView = TextView(context).apply {
                textSize = 14f
                setTextColor(android.graphics.Color.LTGRAY)
                setPadding(0, 8, 0, 0)
            }
            
            layout.addView(nameView)
            layout.addView(typeView)
            
            return ViewHolder(layout, nameView, typeView)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            holder.nameView.text = device.name
            holder.typeView.text = device.type
            holder.itemView.setOnClickListener { onClick(device) }
        }
        
        override fun getItemCount(): Int = devices.size
        
        class ViewHolder(
            view: View,
            val nameView: TextView,
            val typeView: TextView
        ) : RecyclerView.ViewHolder(view)
    }
}