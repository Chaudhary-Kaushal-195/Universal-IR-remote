package com.example.universalhub

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val broker = "tcp://broker.hivemq.com:1883" 
    private val clientId = "AndroidAppClient_" + System.currentTimeMillis()
    private val topicTx = "universalo-hub/kaushal-ir-hub-97/rx" 
    private val topicRx = "universalo-hub/kaushal-ir-hub-97/tx" 
    
    private var mqttClient: MqttClient? = null
    private lateinit var sharedPref: SharedPreferences

    private var isLearning = false
    private var learningTargetId: String? = null
    private var acTemp = 24
    
    // UI Elements
    private lateinit var statusIndicator: TextView
    private lateinit var learnBtn: Button
    private lateinit var settingsBtn: Button
    private lateinit var learningStatus: TextView
    
    private lateinit var tabTv: Button
    private lateinit var tabAc: Button
    private lateinit var layoutTv: GridLayout
    private lateinit var layoutAc: GridLayout
    private lateinit var acTempDisplay: TextView

    private val standardButtons = listOf(
        "TV_POWER", "TV_MUTE", "TV_INPUT", 
        "TV_VOL_UP", "TV_VOL_DOWN", "TV_CH_UP", "TV_CH_DOWN",
        "TV_UP", "TV_DOWN", "TV_LEFT", "TV_RIGHT", "TV_OK",
        "TV_HOME", "TV_BACK",
        "AC_POWER_ON", "AC_POWER_OFF", 
        "AC_MODE", "AC_FAN", "AC_SWING", 
        "AC_TIMER", "AC_SLEEP", "AC_LIGHT"
    )

    // File Picker Intents
    private val exportJsonLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { saveJsonToFile(it) }
        }
    }

    private val importJsonLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { loadJsonFromFile(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPref = getSharedPreferences("LearnedCodes", Context.MODE_PRIVATE)
        
        statusIndicator = findViewById(R.id.connection_status)
        learnBtn = findViewById(R.id.learn_btn)
        settingsBtn = findViewById(R.id.settings_btn)
        learningStatus = findViewById(R.id.learning_status)
        
        tabTv = findViewById(R.id.tab_tv)
        tabAc = findViewById(R.id.tab_ac)
        layoutTv = findViewById(R.id.layout_tv)
        layoutAc = findViewById(R.id.layout_ac)
        acTempDisplay = findViewById(R.id.ac_temp_display)

        setupMQTT()
        setupTabs()
        setupACLogic()
        setupUI()
    }

    private fun triggerVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun showSettingsDialog() {
        triggerVibration()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val ipInput = EditText(this).apply {
            hint = "ESP32 IP (e.g. 192.168.1.100)"
            setText(sharedPref.getString("esp32Ip", ""))
        }
        layout.addView(ipInput)

        val saveIpBtn = Button(this).apply {
            text = "Save IP Configuration"
            setOnClickListener {
                triggerVibration()
                sharedPref.edit().putString("esp32Ip", ipInput.text.toString()).apply()
                Toast.makeText(context, "ESP32 IP Saved!", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(saveIpBtn)

        val exportBtn = Button(this).apply {
            text = "Export Codes (JSON)"
            setOnClickListener {
                triggerVibration()
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "ir_hub_backup.json")
                }
                exportJsonLauncher.launch(intent)
            }
        }
        layout.addView(exportBtn)

        val importBtn = Button(this).apply {
            text = "Import Codes (JSON)"
            setOnClickListener {
                triggerVibration()
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                importJsonLauncher.launch(intent)
            }
        }
        layout.addView(importBtn)

        AlertDialog.Builder(this)
            .setTitle("⚙️ Settings")
            .setView(layout)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun saveJsonToFile(uri: Uri) {
        try {
            val allEntries = sharedPref.all
            val jsonObject = JSONObject()
            for ((key, value) in allEntries) {
                if (key != "esp32Ip") {
                    try {
                        val element = JSONObject(value.toString())
                        jsonObject.put(key, element)
                    } catch (e: Exception) {}
                }
            }
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonObject.toString(2).toByteArray())
            }
            Toast.makeText(this, "Export Successful!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadJsonFromFile(uri: Uri) {
        try {
            val stringBuilder = StringBuilder()
            contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { stringBuilder.append(it) }
                }
            }
            
            val jsonObject = JSONObject(stringBuilder.toString())
            val editor = sharedPref.edit()
            
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObject.getJSONObject(key).toString()
                editor.putString(key, value)
            }
            editor.apply()
            updateButtonVisuals()
            Toast.makeText(this, "Import Successful!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import Failed: Invalid format", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMQTT() {
        try {
            val persistence = MemoryPersistence()
            mqttClient = MqttClient(broker, clientId, persistence)
            val connOpts = MqttConnectOptions()
            connOpts.isCleanSession = true

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    runOnUiThread {
                        statusIndicator.text = "🔴 Offline"
                        statusIndicator.setTextColor(Color.RED)
                    }
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val rawData = message?.toString() ?: return
                    Log.d("MQTT", "Message Arrived: $rawData")
                    runOnUiThread { handleIncomingMessage(rawData) }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient?.connect(connOpts)
            
            runOnUiThread {
                statusIndicator.text = "🟢 Hub Online"
                statusIndicator.setTextColor(Color.parseColor("#22c55e"))
                statusIndicator.setBackgroundColor(Color.parseColor("#1a22c55e"))
            }
            
            mqttClient?.subscribe(topicRx)
            
        } catch (me: MqttException) {
            Log.e("MQTT", "Error establishing MQTT connection", me)
            statusIndicator.text = "🔴 Connection Failed"
            statusIndicator.setTextColor(Color.RED)
        }
    }

    private fun handleIncomingMessage(message: String) {
        if (message == "STATUS:ONLINE") {
            statusIndicator.text = "🟢 Hub Online"
            statusIndicator.setTextColor(Color.parseColor("#22c55e"))
            statusIndicator.setBackgroundColor(Color.parseColor("#1a22c55e"))
        } else if (message == "STATUS:OFFLINE") {
            statusIndicator.text = "🔴 Hub Offline"
            statusIndicator.setTextColor(Color.RED)
            statusIndicator.setBackgroundColor(Color.parseColor("#1aef4444"))
        } else if (message.startsWith("RAW:") && isLearning && learningTargetId != null) {
            val parts = message.split(":")
            if (parts.size >= 3) {
                val len = parts[1]
                val values = parts[2]
                
                val jsonSignal = JSONObject()
                jsonSignal.put("type", "raw")
                jsonSignal.put("len", len.toInt())
                jsonSignal.put("values", values)
                
                sharedPref.edit().putString(learningTargetId, jsonSignal.toString()).apply()
                
                isLearning = false
                val targetName = learningTargetId?.replace("_", " ")
                learningTargetId = null
                
                learnBtn.text = "🎤 Enter Learning Mode"
                learnBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6366f1"))
                learningStatus.text = "Success! \"$targetName\" cloned via WiFi!"
                
                triggerVibration()
                updateButtonVisuals()
            }
        }
    }

    private fun setupTabs() {
        tabTv.setOnClickListener {
            triggerVibration()
            layoutTv.visibility = View.VISIBLE
            layoutAc.visibility = View.GONE
            tabTv.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6366f1"))
            tabTv.setTextColor(Color.WHITE)
            tabAc.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#121217"))
            tabAc.setTextColor(Color.parseColor("#94a3b8"))
        }

        tabAc.setOnClickListener {
            triggerVibration()
            layoutTv.visibility = View.GONE
            layoutAc.visibility = View.VISIBLE
            tabAc.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6366f1"))
            tabAc.setTextColor(Color.WHITE)
            tabTv.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#121217"))
            tabTv.setTextColor(Color.parseColor("#94a3b8"))
        }
    }

    private fun setupACLogic() {
        val btnTempUp = findViewById<Button>(R.id.AC_TEMP_UP)
        val btnTempDown = findViewById<Button>(R.id.AC_TEMP_DOWN)
        val tempContainer = findViewById<LinearLayout>(R.id.AC_TEMP_DISPLAY_CONTAINER)

        val updateTempUI = {
            acTempDisplay.text = "$acTemp°"
            updateButtonVisuals()
        }

        btnTempUp.setOnClickListener {
            if (acTemp < 30) acTemp++
            updateTempUI()
            if (!isLearning) handleRemoteClick("AC_TEMP_$acTemp")
        }

        btnTempDown.setOnClickListener {
            if (acTemp > 16) acTemp--
            updateTempUI()
            if (!isLearning) handleRemoteClick("AC_TEMP_$acTemp")
        }

        tempContainer.setOnClickListener {
            handleRemoteClick("AC_TEMP_$acTemp")
        }
    }

    private fun getButtonResId(btnId: String): Int {
        var resId = resources.getIdentifier(btnId, "id", packageName)
        if (resId == 0) {
            resId = resources.getIdentifier(btnId.lowercase(), "id", packageName)
        }
        return resId
    }

    private fun setupUI() {
        for (btnId in standardButtons) {
            val resId = getButtonResId(btnId)
            if (resId != 0) {
                findViewById<View>(resId).setOnClickListener {
                    handleRemoteClick(btnId)
                }
            }
        }

        learnBtn.setOnClickListener {
            triggerVibration()
            isLearning = !isLearning
            if (isLearning) {
                learnBtn.text = "Cancel Learning"
                learnBtn.backgroundTintList = ColorStateList.valueOf(Color.RED)
                learningStatus.text = "Click a virtual button above to map it..."
            } else {
                learnBtn.text = "🎤 Enter Learning Mode"
                learnBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6366f1"))
                learningStatus.text = ""
                learningTargetId = null
            }
        }
        
        settingsBtn.setOnClickListener {
            showSettingsDialog()
        }
        
        updateButtonVisuals()
    }

    private fun handleRemoteClick(buttonId: String) {
        triggerVibration()
        if (isLearning) {
            learningTargetId = buttonId
            learningStatus.text = "Point physical remote at ESP32 and press button for $buttonId..."
        } else {
            val savedSignal = sharedPref.getString(buttonId, null)
            if (savedSignal == null) {
                Toast.makeText(this, "Button $buttonId not programmed yet!", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (mqttClient != null && mqttClient!!.isConnected) {
                try {
                    val message = MqttMessage(savedSignal.toByteArray())
                    mqttClient?.publish(topicTx, message)
                    Log.d("MQTT", "Dispatched: $savedSignal")
                    Toast.makeText(this, "Transmitting...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to send MQTT message", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "MQTT Offline. Check Connection.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateButtonVisuals() {
        for (btnId in standardButtons) {
            val resId = getButtonResId(btnId)
            if (resId != 0) {
                val btn = findViewById<Button>(resId)
                if (sharedPref.contains(btnId)) {
                    btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3a6366f1"))
                } else {
                    if (btnId == "AC_POWER_ON") btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1a22c55e"))
                    else if (btnId == "AC_POWER_OFF" || btnId == "TV_POWER") btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1aef4444"))
                    else if (btnId == "TV_OK") btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6366f1"))
                    else btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1affffff"))
                }
            }
        }

        val tempContainer = findViewById<LinearLayout>(R.id.AC_TEMP_DISPLAY_CONTAINER)
        if (sharedPref.contains("AC_TEMP_$acTemp")) {
            tempContainer.setBackgroundColor(Color.parseColor("#3a6366f1"))
        } else {
            tempContainer.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
