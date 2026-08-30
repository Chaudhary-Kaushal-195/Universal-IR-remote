package com.espir.app.ui.devices

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.espir.app.R
import com.espir.app.data.IRCommand
import com.espir.app.viewmodel.MainViewModel

class DeviceControlActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private var deviceId: Int = -1
    private lateinit var deviceName: String
    private var learnedCommands: List<IRCommand> = emptyList()
    
    private var pendingLearnButton: String? = null
    private var learningDialog: AlertDialog? = null

    private var btnPowerName = "Power"
    private var btnVolUpName = "Volume Up"
    private var btnVolDownName = "Volume Down"
    private var btnChUpName = "Channel Up"
    private var btnChDownName = "Channel Down"
    private var btnMuteName = "Mute"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_control)

        deviceId = intent.getIntExtra("device_id", -1)
        deviceName = intent.getStringExtra("device_name") ?: "Unknown Device"
        val deviceType = intent.getStringExtra("device_type") ?: "TV"

        if (deviceType.equals("AC", ignoreCase = true)) {
            btnVolUpName = "Temp +"
            btnVolDownName = "Temp -"
            btnChUpName = "Mode"
            btnChDownName = "Fan"
            btnMuteName = "Swing"
            
            findViewById<Button>(R.id.btn_volume_up).text = btnVolUpName
            findViewById<Button>(R.id.btn_volume_down).text = btnVolDownName
            findViewById<Button>(R.id.btn_channel_up).text = btnChUpName
            findViewById<Button>(R.id.btn_channel_down).text = btnChDownName
            findViewById<Button>(R.id.btn_mute).text = btnMuteName
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = deviceName

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        val titleView = findViewById<TextView>(R.id.control_title)
        val statusView = findViewById<TextView>(R.id.control_status)
        titleView.text = "$deviceName Control"

        // Observe connection state
        viewModel.connectionStatus.observe(this) { status ->
            statusView.text = "Status: $status"
        }

        // Observe learned commands list for this device
        viewModel.devicesWithCommands.observe(this) { list ->
            val deviceWithCmds = list.find { it.device.id == deviceId }
            learnedCommands = deviceWithCmds?.commands ?: emptyList()
            updateButtonColors()
        }

        // Observe status and error messages from ViewModel
        viewModel.statusMessage.observe(this) { msg ->
            if (!msg.isNullOrEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearStatusMessage()
            }
        }

        viewModel.errorMessage.observe(this) { err ->
            if (!err.isNullOrEmpty()) {
                Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
                learningDialog?.dismiss()
            }
        }

        // Observe when a code is learned via BLE
        viewModel.lastLearnedCode.observe(this) { formattedCode ->
            val buttonName = pendingLearnButton
            if (buttonName != null && !formattedCode.isNullOrEmpty()) {
                // Save command to local DB
                viewModel.addCommand(deviceId, buttonName, "Learned Button Command", formattedCode)
                
                // Reset pending state and close loading dialog
                pendingLearnButton = null
                learningDialog?.dismiss()
                Toast.makeText(this, "Successfully programmed $buttonName!", Toast.LENGTH_LONG).show()
            }
        }

        // Setup remote buttons click listeners
        setupRemoteButton(R.id.btn_power, btnPowerName)
        setupRemoteButton(R.id.btn_volume_up, btnVolUpName)
        setupRemoteButton(R.id.btn_volume_down, btnVolDownName)
        setupRemoteButton(R.id.btn_channel_up, btnChUpName)
        setupRemoteButton(R.id.btn_channel_down, btnChDownName)
        setupRemoteButton(R.id.btn_mute, btnMuteName)

        // Learn button click listener
        val btnLearn = findViewById<Button>(R.id.btn_learn)
        btnLearn.setOnClickListener {
            showSelectButtonToLearnDialog()
        }
    }

    private fun setupRemoteButton(buttonId: Int, buttonName: String) {
        val button = findViewById<Button>(buttonId)
        button.setOnClickListener {
            val cmd = learnedCommands.find { it.name.equals(buttonName, ignoreCase = true) }
            if (cmd != null) {
                // Already learned, transmit it
                viewModel.sendIRCommand(deviceName, buttonName)
            } else {
                // Not learned, offer to learn it
                AlertDialog.Builder(this)
                    .setTitle("Button Not Programmed")
                    .setMessage("$buttonName has not been programmed yet. Would you like to program it now?")
                    .setPositiveButton("Yes") { _, _ ->
                        startLearningWorkflow(buttonName)
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        }
    }

    private fun startLearningWorkflow(buttonName: String) {
        if (viewModel.isConnected.value != true) {
            Toast.makeText(this, "Please connect to the ESP32 first", Toast.LENGTH_SHORT).show()
            return
        }

        pendingLearnButton = buttonName
        viewModel.startLearningMode()

        learningDialog = AlertDialog.Builder(this)
            .setTitle("Learning Mode")
            .setMessage("Point your physical remote at the ESP32 receiver and press the $buttonName button...")
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                pendingLearnButton = null
            }
            .show()
    }

    private fun showSelectButtonToLearnDialog() {
        val buttonsList = arrayOf(btnPowerName, btnVolUpName, btnVolDownName, btnChUpName, btnChDownName, btnMuteName)
        AlertDialog.Builder(this)
            .setTitle("Select Button to Program")
            .setItems(buttonsList) { _, which ->
                startLearningWorkflow(buttonsList[which])
            }
            .show()
    }

    private fun updateButtonColors() {
        // Change button styling/colors if they are learned or not
        setButtonState(R.id.btn_power, btnPowerName)
        setButtonState(R.id.btn_volume_up, btnVolUpName)
        setButtonState(R.id.btn_volume_down, btnVolDownName)
        setButtonState(R.id.btn_channel_up, btnChUpName)
        setButtonState(R.id.btn_channel_down, btnChDownName)
        setButtonState(R.id.btn_mute, btnMuteName)
    }

    private fun setButtonState(buttonId: Int, buttonName: String) {
        val button = findViewById<Button>(buttonId)
        val isLearned = learnedCommands.any { it.name.equals(buttonName, ignoreCase = true) }
        
        if (isLearned) {
            if (buttonId == R.id.btn_power) {
                button.setBackgroundColor(android.graphics.Color.parseColor("#E53935")) // Red for active Power
            } else {
                button.setBackgroundColor(android.graphics.Color.parseColor("#1E88E5")) // Blue for active buttons
            }
            button.alpha = 1.0f
        } else {
            button.setBackgroundColor(android.graphics.Color.parseColor("#424242")) // Dark grey for unprogrammed buttons
            button.alpha = 0.6f
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
