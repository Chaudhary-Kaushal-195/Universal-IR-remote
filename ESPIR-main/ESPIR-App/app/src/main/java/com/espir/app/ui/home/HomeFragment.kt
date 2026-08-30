package com.espir.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.espir.app.MainActivity
import com.espir.app.R
import com.espir.app.ui.devices.DeviceListActivity
import com.espir.app.viewmodel.MainViewModel

class HomeFragment : Fragment() {

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        
        val statusTextView = view.findViewById<TextView>(R.id.connection_status)
        val connectButton = view.findViewById<Button>(R.id.connect_button)
        
        viewModel.connectionStatus.observe(viewLifecycleOwner) { status ->
            statusTextView.text = status
        }
        
        viewModel.isConnected.observe(viewLifecycleOwner) { isConnected ->
            if (isConnected) {
                connectButton.text = "Disconnect"
            } else {
                connectButton.text = "Connect"
            }
        }

        connectButton.setOnClickListener {
            val isConnected = viewModel.isConnected.value ?: false
            if (isConnected) {
                viewModel.disconnectDevice()
            } else {
                val mainActivity = activity as? MainActivity
                if (mainActivity != null) {
                    // Check if bluetooth is enabled and request scan or enable
                    // Request code 3 is REQUEST_CONNECT_DEVICE
                    mainActivity.startActivityForResult(
                        Intent(mainActivity, DeviceListActivity::class.java),
                        3
                    )
                }
            }
        }
    }
}