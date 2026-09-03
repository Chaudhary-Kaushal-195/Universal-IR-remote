package com.example.universalhub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.ConsumerIrManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.*
import kotlin.math.abs
import kotlin.math.max
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.concurrent.Executors
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val bgExecutor = Executors.newSingleThreadExecutor()

    private var broker = "tcp://broker.hivemq.com:1883"
    private val clientId = "AndroidAppClient_" + System.currentTimeMillis()
    private var hubId = "kaushal-ir-hub-97"
    private var topicTx = "universalo-hub/$hubId/rx"
    private var topicRx = "universalo-hub/$hubId/tx"

    private var mqttClient: MqttClient? = null
    private lateinit var sharedPref: SharedPreferences
    private lateinit var usbSerialManager: UsbSerialManager
    private lateinit var profileManager: ProfileManager
    private var consumerIrManager: ConsumerIrManager? = null
    private var hasInternalIr: Boolean = false

    // IR Learning / Programming State
    private var isLearning = false
    private var learningTargetId: String? = null

    // Device Power States
    private var isLightOn = true
    private var isAcOn = false
    private var isTvOn = false
    private var isFanOn = false
    private var acTemp = 24

    // UI Elements
    private lateinit var btnMenu: ImageView
    private lateinit var btnPerson: ImageView

    private lateinit var cardLight: RelativeLayout
    private lateinit var iconLight: ImageView
    private lateinit var textLight: TextView
    private lateinit var switchLight: SwitchCompat

    private lateinit var cardAc: RelativeLayout
    private lateinit var iconAc: ImageView
    private lateinit var textAc: TextView
    private lateinit var switchAc: SwitchCompat

    private lateinit var cardTv: RelativeLayout
    private lateinit var iconTv: ImageView
    private lateinit var textTv: TextView
    private lateinit var switchTv: SwitchCompat

    private lateinit var cardFan: RelativeLayout
    private lateinit var iconFan: ImageView
    private lateinit var textFan: TextView
    private lateinit var switchFan: SwitchCompat

    // Hardware Status Indicator UI
    private lateinit var hardwareStatusPill: LinearLayout
    private lateinit var hardwareStatusDot: TextView
    private lateinit var hardwareStatusText: TextView
    private var isEsp32MqttOnline: Boolean = false

    // Floating Popup HUD Elements
    private lateinit var toastBanner: View
    private lateinit var toastIcon: TextView
    private lateinit var toastMessage: TextView
    private var toastDismissRunnable: Runnable? = null
    private val toastHandler = Handler(Looper.getMainLooper())
    private var activeDialog: BottomSheetDialog? = null

    // Active Floating Notification State
    private var currentToastMessage: String? = null
    private var currentToastIcon: String = "📡"
    private var isToastShowing: Boolean = false

    // File Picker Intents for Backup
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
        profileManager = ProfileManager(sharedPref)

        consumerIrManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        hasInternalIr = consumerIrManager?.hasIrEmitter() == true

        // Load configuration and states
        hubId = sharedPref.getString("hubId", "kaushal-ir-hub-97") ?: "kaushal-ir-hub-97"
        isLightOn = sharedPref.getBoolean("state_light", true)
        isAcOn = sharedPref.getBoolean("state_ac", false)
        isTvOn = sharedPref.getBoolean("state_tv", false)
        isFanOn = sharedPref.getBoolean("state_fan", false)
        acTemp = sharedPref.getInt("acTemp", 24)

        topicTx = "universalo-hub/$hubId/rx"
        topicRx = "universalo-hub/$hubId/tx"

        bindViews()
        setupListeners()
        setupMQTT()
        setupUsbSerial()
        updateCardStates()
        updateHardwareStatusUI()
    }

    private fun bindViews() {
        btnMenu = findViewById(R.id.btn_menu)
        btnPerson = findViewById(R.id.btn_person)

        cardLight = findViewById(R.id.card_light)
        iconLight = findViewById(R.id.icon_light)
        textLight = findViewById(R.id.text_light)
        switchLight = findViewById(R.id.switch_light)

        cardAc = findViewById(R.id.card_ac)
        iconAc = findViewById(R.id.icon_ac)
        textAc = findViewById(R.id.text_ac)
        switchAc = findViewById(R.id.switch_ac)

        cardTv = findViewById(R.id.card_tv)
        iconTv = findViewById(R.id.icon_tv)
        textTv = findViewById(R.id.text_tv)
        switchTv = findViewById(R.id.switch_tv)

        cardFan = findViewById(R.id.card_fan)
        iconFan = findViewById(R.id.icon_fan)
        textFan = findViewById(R.id.text_fan)
        switchFan = findViewById(R.id.switch_fan)

        hardwareStatusPill = findViewById(R.id.hardware_status_pill)
        hardwareStatusDot = findViewById(R.id.hardware_status_dot)
        hardwareStatusText = findViewById(R.id.hardware_status_text)

        toastBanner = findViewById(R.id.toast_banner)
        toastIcon = findViewById(R.id.toast_icon)
        toastMessage = findViewById(R.id.toast_message)
    }

    private fun setupListeners() {
        // Menu -> Settings
        btnMenu.setOnClickListener {
            triggerVibration()
            showSettingsDialog()
        }

        // Hardware Status Pill -> Connection Diagnostics Dialog
        hardwareStatusPill.setOnClickListener {
            triggerVibration()
            showHardwareStatusDialog()
        }

        // Person -> Settings
        btnPerson.setOnClickListener {
            triggerVibration()
            showSettingsDialog()
        }

        // 1. SMART LIGHT
        cardLight.setOnClickListener {
            triggerVibration()
            showLightControlSheet()
        }
        cardLight.setOnLongClickListener {
            startLearningForButton("LIGHT_POWER")
            true
        }
        switchLight.setOnCheckedChangeListener { _, isChecked ->
            triggerVibration()
            isLightOn = isChecked
            sharedPref.edit().putBoolean("state_light", isLightOn).apply()
            updateCardStates()
            handleRemoteClick("LIGHT_POWER")
        }

        // 2. SMART AC
        cardAc.setOnClickListener {
            triggerVibration()
            showAcControlSheet()
        }
        cardAc.setOnLongClickListener {
            startLearningForButton("AC_POWER_ON")
            true
        }
        switchAc.setOnCheckedChangeListener { _, isChecked ->
            triggerVibration()
            isAcOn = isChecked
            sharedPref.edit().putBoolean("state_ac", isAcOn).apply()
            updateCardStates()
            handleRemoteClick(if (isAcOn) "AC_POWER_ON" else "AC_POWER_OFF")
        }

        // 3. SMART TV
        cardTv.setOnClickListener {
            triggerVibration()
            showTvControlSheet()
        }
        cardTv.setOnLongClickListener {
            startLearningForButton("TV_POWER")
            true
        }
        switchTv.setOnCheckedChangeListener { _, isChecked ->
            triggerVibration()
            isTvOn = isChecked
            sharedPref.edit().putBoolean("state_tv", isTvOn).apply()
            updateCardStates()
            handleRemoteClick("TV_POWER")
        }

        // 4. SMART FAN
        cardFan.setOnClickListener {
            triggerVibration()
            showFanControlSheet()
        }
        cardFan.setOnLongClickListener {
            startLearningForButton("FAN_POWER")
            true
        }
        switchFan.setOnCheckedChangeListener { _, isChecked ->
            triggerVibration()
            isFanOn = isChecked
            sharedPref.edit().putBoolean("state_fan", isFanOn).apply()
            updateCardStates()
            handleRemoteClick("FAN_POWER")
        }
    }

    private fun updateCardStates() {
        // Light Card
        switchLight.isChecked = isLightOn
        cardLight.setBackgroundResource(if (isLightOn) R.drawable.bg_device_card_on else R.drawable.bg_device_card_off)
        iconLight.imageTintList = ColorStateList.valueOf(if (isLightOn) Color.WHITE else Color.parseColor("#616161"))
        textLight.setTextColor(if (isLightOn) Color.WHITE else Color.BLACK)

        // AC Card
        switchAc.isChecked = isAcOn
        cardAc.setBackgroundResource(if (isAcOn) R.drawable.bg_device_card_on else R.drawable.bg_device_card_off)
        iconAc.imageTintList = ColorStateList.valueOf(if (isAcOn) Color.WHITE else Color.parseColor("#616161"))
        textAc.setTextColor(if (isAcOn) Color.WHITE else Color.BLACK)

        // TV Card
        switchTv.isChecked = isTvOn
        cardTv.setBackgroundResource(if (isTvOn) R.drawable.bg_device_card_on else R.drawable.bg_device_card_off)
        iconTv.imageTintList = ColorStateList.valueOf(if (isTvOn) Color.WHITE else Color.parseColor("#616161"))
        textTv.setTextColor(if (isTvOn) Color.WHITE else Color.BLACK)

        // Fan Card
        switchFan.isChecked = isFanOn
        cardFan.setBackgroundResource(if (isFanOn) R.drawable.bg_device_card_on else R.drawable.bg_device_card_off)
        iconFan.imageTintList = ColorStateList.valueOf(if (isFanOn) Color.WHITE else Color.parseColor("#616161"))
        textFan.setTextColor(if (isFanOn) Color.WHITE else Color.BLACK)
    }

    // ========================================================
    // IR LEARNING / BUTTON PROGRAMMING
    // ========================================================
    private fun startLearningForButton(buttonId: String) {
        triggerVibration()
        isLearning = true
        learningTargetId = buttonId
        val readableName = buttonId.replace("_", " ")
        val category = profileManager.getCategoryFromButtonId(buttonId)
        val activeProfileName = profileManager.getProfileName(category, profileManager.getActiveProfileIndex(category))
        showModernPopup("Point remote at ESP32 to clone $readableName ($activeProfileName)", "🎯")
    }

    // ========================================================
    // PROFILE SELECTOR & RENAMING HELPER
    // ========================================================
    private fun setupProfileSelector(
        sheetView: View,
        category: DeviceCategory,
        onProfileChanged: ((Int) -> Unit)? = null
    ) {
        val chipIds = intArrayOf(
            R.id.chip_profile_0,
            R.id.chip_profile_1,
            R.id.chip_profile_2,
            R.id.chip_profile_3,
            R.id.chip_profile_4
        )

        fun refreshChips() {
            val activeIndex = profileManager.getActiveProfileIndex(category)
            for (i in 0 until ProfileManager.MAX_PROFILES) {
                val chip = sheetView.findViewById<TextView>(chipIds[i]) ?: continue
                val name = profileManager.getProfileName(category, i)
                chip.text = name

                if (i == activeIndex) {
                    chip.setBackgroundResource(R.drawable.bg_profile_chip_active)
                    chip.setTextColor(Color.WHITE)
                } else {
                    chip.setBackgroundResource(R.drawable.bg_profile_chip_inactive)
                    chip.setTextColor(Color.parseColor("#475569"))
                }

                chip.setOnClickListener {
                    triggerVibration()
                    if (profileManager.getActiveProfileIndex(category) != i) {
                        profileManager.setActiveProfileIndex(category, i)
                        refreshChips()
                        val newName = profileManager.getProfileName(category, i)
                        showModernPopup("Active: $newName", "🏷️")
                        onProfileChanged?.invoke(i)
                    }
                }

                chip.setOnLongClickListener {
                    triggerVibration()
                    showRenameProfileDialog(category, i) {
                        refreshChips()
                    }
                    true
                }
            }
        }
        refreshChips()
    }

    private fun showRenameProfileDialog(
        category: DeviceCategory,
        index: Int,
        onRenamed: () -> Unit
    ) {
        val currentName = profileManager.getProfileName(category, index)
        val input = EditText(this).apply {
            setText(currentName)
            setSelection(text.length)
            setPadding(48, 32, 48, 32)
            setBackgroundResource(R.drawable.bg_sheet_btn)
            setTextColor(Color.parseColor("#1E293B"))
        }

        val container = FrameLayout(this).apply {
            val margin = (24 * resources.displayMetrics.density).toInt()
            setPadding(margin, margin / 2, margin, margin / 2)
            addView(input)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Rename Profile ${index + 1}")
            .setMessage("e.g. Living Room, Bedroom Mitsubishi, Daikin")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    profileManager.setProfileName(category, index, newName)
                    showModernPopup("Renamed to: $newName", "✏️")
                    onRenamed()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========================================================
    // REMOTE CONTROL SHEETS
    // ========================================================
    private fun createCleanBottomSheet(): BottomSheetDialog {
        val sheet = BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme)
        sheet.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        activeDialog = sheet

        sheet.setOnShowListener { dialog ->
            val d = dialog as? BottomSheetDialog
            activeDialog = d
            val bottomSheet = d?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                it.setBackgroundColor(Color.TRANSPARENT)
                it.background = ColorDrawable(Color.TRANSPARENT)
            }

            // Bring active notification immediately to the top of the dialog window
            if (isToastShowing && currentToastMessage != null) {
                toastBanner.visibility = View.GONE
                showModernPopup(currentToastMessage!!, currentToastIcon)
            }
        }

        sheet.setOnDismissListener {
            val hadToast = isToastShowing && currentToastMessage != null
            val msg = currentToastMessage
            val icon = currentToastIcon

            if (activeDialog == sheet) {
                activeDialog = null
            }

            // Restore active notification back to activity view if still valid
            if (hadToast && msg != null) {
                showModernPopup(msg, icon)
            }
        }
        return sheet
    }

    private fun showTvControlSheet() {
        val sheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.sheet_tv_remote, null)
        sheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

        view.findViewById<View>(R.id.sheet_btn_close_tv)?.setOnClickListener {
            triggerVibration()
            sheet.dismiss()
        }

        setupProfileSelector(view, DeviceCategory.TV)

        val tvButtons = listOf(
            "TV_POWER", "TV_MUTE", "TV_INPUT",
            "TV_UP", "TV_DOWN", "TV_LEFT", "TV_RIGHT", "TV_OK",
            "TV_VOL_UP", "TV_VOL_DOWN", "TV_CH_UP", "TV_CH_DOWN",
            "TV_BACK", "TV_HOME"
        )

        for (btnId in tvButtons) {
            val resId = view.resources.getIdentifier(btnId, "id", packageName)
            if (resId != 0) {
                val btn = view.findViewById<View>(resId)
                btn?.setOnClickListener {
                    handleRemoteClick(btnId)
                    if (btnId == "TV_POWER" && !isLearning) {
                        isTvOn = !isTvOn
                        sharedPref.edit().putBoolean("state_tv", isTvOn).apply()
                        updateCardStates()
                    }
                }
                // Long press shortcut to program button
                btn?.setOnLongClickListener {
                    startLearningForButton(btnId)
                    true
                }
            }
        }
        sheet.show()
    }

    private fun showAcControlSheet() {
        val sheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.sheet_ac_remote, null)
        sheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

        view.findViewById<View>(R.id.sheet_btn_close_ac)?.setOnClickListener {
            triggerVibration()
            sheet.dismiss()
        }

        setupProfileSelector(view, DeviceCategory.AC)

        var pendingTemp = acTemp

        val tempDisplay = view.findViewById<TextView>(R.id.sheet_ac_temp_display)
        tempDisplay?.text = "$acTemp"

        val tempWheel = view.findViewById<ArcTemperatureWheelView>(R.id.ac_temp_wheel)
        tempWheel?.setTemperature(acTemp, animate = false)

        val btnSendTemp = view.findViewById<ImageButton>(R.id.btn_send_ac_temp)

        fun updateSendButtonState(isDifferent: Boolean, animate: Boolean = true) {
            val targetAlpha = if (isDifferent) 1.0f else 0.32f
            val targetScale = if (isDifferent) 1.0f else 0.88f
            btnSendTemp?.isEnabled = isDifferent
            if (!animate) {
                btnSendTemp?.alpha = targetAlpha
                btnSendTemp?.scaleX = targetScale
                btnSendTemp?.scaleY = targetScale
            } else {
                btnSendTemp?.animate()
                    ?.alpha(targetAlpha)
                    ?.scaleX(targetScale)
                    ?.scaleY(targetScale)
                    ?.setDuration(220)
                    ?.setInterpolator(OvershootInterpolator(1.2f))
                    ?.start()
            }
        }

        // Initially at current set temp, send button is faded
        updateSendButtonState(isDifferent = false, animate = false)

        tempWheel?.onTempChangeListener = { continuousTemp ->
            val displayVal = kotlin.math.round(continuousTemp).toInt().coerceIn(tempWheel.minTemp, tempWheel.maxTemp)
            tempDisplay?.text = "$displayVal"
            pendingTemp = displayVal
            val isDiff = (pendingTemp != acTemp)
            updateSendButtonState(isDifferent = isDiff, animate = true)
        }

        tempWheel?.onTempSettledListener = { settledTemp ->
            pendingTemp = settledTemp
            tempDisplay?.text = "$pendingTemp"
            val isDiff = (pendingTemp != acTemp)
            updateSendButtonState(isDifferent = isDiff, animate = true)
        }

        tempWheel?.setOnLongClickListener {
            startLearningForButton("AC_TEMP_$pendingTemp")
            true
        }

        btnSendTemp?.setOnClickListener {
            triggerVibration()
            acTemp = pendingTemp
            tempDisplay?.text = "$acTemp"
            sharedPref.edit().putInt("acTemp", acTemp).apply()
            handleRemoteClick("AC_TEMP_$acTemp")
            updateSendButtonState(isDifferent = false, animate = true)
        }

        btnSendTemp?.setOnLongClickListener {
            startLearningForButton("AC_TEMP_$pendingTemp")
            true
        }

        view.findViewById<View>(R.id.AC_TEMP_DISPLAY_CONTAINER)?.setOnLongClickListener {
            startLearningForButton("AC_TEMP_$pendingTemp")
            true
        }

        val acButtons = listOf(
            "AC_POWER_ON", "AC_POWER_OFF",
            "AC_MODE", "AC_FAN", "AC_SWING",
            "AC_TIMER", "AC_SLEEP", "AC_LIGHT"
        )

        for (btnId in acButtons) {
            val resId = view.resources.getIdentifier(btnId, "id", packageName)
            if (resId != 0) {
                val btn = view.findViewById<View>(resId)
                btn?.setOnClickListener {
                    handleRemoteClick(btnId)
                    if (!isLearning) {
                        if (btnId == "AC_POWER_ON") {
                            isAcOn = true
                            sharedPref.edit().putBoolean("state_ac", isAcOn).apply()
                            updateCardStates()
                        } else if (btnId == "AC_POWER_OFF") {
                            isAcOn = false
                            sharedPref.edit().putBoolean("state_ac", isAcOn).apply()
                            updateCardStates()
                        }
                    }
                }
                // Long press shortcut to program button
                btn?.setOnLongClickListener {
                    startLearningForButton(btnId)
                    true
                }
            }
        }
        sheet.show()
    }

    private fun showLightControlSheet() {
        val sheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.sheet_light_remote, null)
        sheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

        view.findViewById<View>(R.id.sheet_btn_close_light)?.setOnClickListener {
            triggerVibration()
            sheet.dismiss()
        }

        setupProfileSelector(view, DeviceCategory.LIGHT)

        val lightButtons = listOf(
            "LIGHT_POWER", "LIGHT_BRIGHT_UP", "LIGHT_BRIGHT_DOWN",
            "LIGHT_WHITE", "LIGHT_RED", "LIGHT_BLUE", "LIGHT_GREEN"
        )

        for (btnId in lightButtons) {
            val resId = view.resources.getIdentifier(btnId, "id", packageName)
            if (resId != 0) {
                val btn = view.findViewById<View>(resId)
                btn?.setOnClickListener {
                    handleRemoteClick(btnId)
                    if (btnId == "LIGHT_POWER" && !isLearning) {
                        isLightOn = !isLightOn
                        sharedPref.edit().putBoolean("state_light", isLightOn).apply()
                        updateCardStates()
                    }
                }
                // Long press shortcut to program button
                btn?.setOnLongClickListener {
                    startLearningForButton(btnId)
                    true
                }
            }
        }
        sheet.show()
    }

    private fun showFanControlSheet() {
        val sheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.sheet_fan_remote, null)
        sheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

        view.findViewById<View>(R.id.sheet_btn_close_fan)?.setOnClickListener {
            triggerVibration()
            sheet.dismiss()
        }

        setupProfileSelector(view, DeviceCategory.FAN)

        val fanButtons = listOf(
            "FAN_POWER", "FAN_SPEED_1", "FAN_SPEED_2", "FAN_SPEED_3", "FAN_SPEED_4",
            "FAN_SWING", "FAN_TIMER"
        )

        for (btnId in fanButtons) {
            val resId = view.resources.getIdentifier(btnId, "id", packageName)
            if (resId != 0) {
                val btn = view.findViewById<View>(resId)
                btn?.setOnClickListener {
                    handleRemoteClick(btnId)
                    if (btnId == "FAN_POWER" && !isLearning) {
                        isFanOn = !isFanOn
                        sharedPref.edit().putBoolean("state_fan", isFanOn).apply()
                        updateCardStates()
                    }
                }
                // Long press shortcut to program button
                btn?.setOnLongClickListener {
                    startLearningForButton(btnId)
                    true
                }
            }
        }
        sheet.show()
    }

    // ========================================================
    // SETTINGS DIALOG WITH PROGRAMMING MODE TOGGLE & USB STATUS
    // ========================================================
    private fun showSettingsDialog() {
        triggerVibration()
        val bottomSheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

        val switchLearnMode = view.findViewById<SwitchCompat>(R.id.switch_learn_mode)
        val textUsbStatus = view.findViewById<TextView>(R.id.text_usb_status)
        val btnConnectUsb = view.findViewById<Button>(R.id.btn_connect_usb)
        val hubIdInput = view.findViewById<EditText>(R.id.input_hub_id)
        val ipInput = view.findViewById<EditText>(R.id.input_esp32_ip)
        val btnClose = view.findViewById<View>(R.id.btn_close_settings)
        val btnSave = view.findViewById<Button>(R.id.btn_save_config)
        val btnExport = view.findViewById<Button>(R.id.btn_export_json)
        val btnImport = view.findViewById<Button>(R.id.btn_import_json)

        hubIdInput.setText(hubId)
        ipInput.setText(sharedPref.getString("esp32Ip", ""))

        val textPhoneIrStatus = view.findViewById<TextView>(R.id.text_phone_ir_status)
        val badgePhoneIr = view.findViewById<TextView>(R.id.badge_phone_ir)
        if (hasInternalIr) {
            textPhoneIrStatus?.text = "Hardware IR Emitter Ready (Direct TX)"
            badgePhoneIr?.text = "Active"
            badgePhoneIr?.setTextColor(Color.parseColor("#15803D"))
        } else {
            textPhoneIrStatus?.text = "No IR emitter on this phone (Uses USB/WiFi)"
            badgePhoneIr?.text = "Unavailable"
            badgePhoneIr?.setTextColor(Color.parseColor("#64748B"))
        }

        fun updateUsbUi() {
            if (usbSerialManager.isConnected) {
                textUsbStatus?.text = "Connected: ${usbSerialManager.connectedDeviceName} (115200)"
                btnConnectUsb?.text = "Disconnect"
            } else {
                textUsbStatus?.text = "Arduino Uno / ESP32 (Disconnected)"
                btnConnectUsb?.text = "Connect"
            }
        }
        updateUsbUi()

        btnConnectUsb?.setOnClickListener {
            triggerVibration()
            if (usbSerialManager.isConnected) {
                usbSerialManager.disconnect()
                updateUsbUi()
                showModernPopup("USB Disconnected", "ℹ️")
            } else {
                usbSerialManager.connect(userInitiated = true)
                updateUsbUi()
            }
        }

        // Network Hub (ESP32 via Wi-Fi) Link
        val textNetworkStatus = view.findViewById<TextView>(R.id.text_network_status)
        val btnConnectNetwork = view.findViewById<Button>(R.id.btn_connect_network)

        fun updateNetworkUi() {
            if (isEsp32MqttOnline) {
                textNetworkStatus?.text = "ESP32 Hub Online via Wi-Fi (HiveMQ)"
                btnConnectNetwork?.text = "Disconnect"
            } else if (mqttClient != null && mqttClient!!.isConnected) {
                textNetworkStatus?.text = "Cloud Broker Ready (Waiting for ESP32)"
                btnConnectNetwork?.text = "Reconnect"
            } else {
                textNetworkStatus?.text = "ESP32 Wi-Fi (Disconnected)"
                btnConnectNetwork?.text = "Connect"
            }
        }
        updateNetworkUi()

        btnConnectNetwork?.setOnClickListener {
            triggerVibration()
            if (mqttClient != null && mqttClient!!.isConnected) {
                bgExecutor.execute {
                    try { mqttClient?.disconnect() } catch (e: Exception) {}
                    isEsp32MqttOnline = false
                    runOnUiThread {
                        updateNetworkUi()
                        updateHardwareStatusUI()
                        showModernPopup("Network Disconnected", "ℹ️")
                    }
                }
            } else {
                showModernPopup("Connecting to ESP32 via Network...", "🌐")
                setupMQTT { success, errMsg ->
                    runOnUiThread {
                        updateNetworkUi()
                        updateHardwareStatusUI()
                        if (success) {
                            showModernPopup("Connected to Network Broker! 🌐", "✅")
                        } else {
                            showModernPopup("Network Failed: $errMsg", "❌")
                        }
                    }
                }
            }
        }

        // IR Programming Mode Toggle
        switchLearnMode.isChecked = isLearning
        switchLearnMode.setOnCheckedChangeListener { _, isChecked ->
            triggerVibration()
            isLearning = isChecked
            if (isLearning) {
                showModernPopup("Programming Mode ON: Tap any button to clone", "🎯")
            } else {
                learningTargetId = null
                showModernPopup("Programming Mode OFF", "ℹ️")
            }
        }

        btnClose.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
        }

        btnSave.setOnClickListener {
            triggerVibration()
            val newHub = hubIdInput.text.toString().trim()
            if (newHub.isNotEmpty()) {
                hubId = newHub
                topicTx = "universalo-hub/$hubId/rx"
                topicRx = "universalo-hub/$hubId/tx"
                sharedPref.edit().putString("hubId", hubId).apply()
            }
            sharedPref.edit().putString("esp32Ip", ipInput.text.toString().trim()).apply()
            showModernPopup("Settings Saved", "💾")
            setupMQTT()
            bottomSheet.dismiss()
        }

        btnExport.setOnClickListener {
            triggerVibration()
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "ir_hub_backup.json")
            }
            exportJsonLauncher.launch(intent)
        }

        btnImport.setOnClickListener {
            triggerVibration()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            importJsonLauncher.launch(intent)
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun saveJsonToFile(uri: Uri) {
        try {
            val allEntries = sharedPref.all
            val jsonObject = JSONObject()
            for ((key, value) in allEntries) {
                if (key != "esp32Ip" && key != "hubId" && !key.startsWith("state_") && key != "acTemp") {
                    try {
                        val element = JSONObject(value.toString())
                        jsonObject.put(key, element)
                    } catch (e: Exception) {
                        jsonObject.put(key, value.toString())
                    }
                }
            }
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonObject.toString(2).toByteArray())
            }
            showModernPopup("Backup Exported!", "📤")
        } catch (e: Exception) {
            showModernPopup("Export Failed", "❌")
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
                val optObj = jsonObject.optJSONObject(key)
                if (optObj != null) {
                    editor.putString(key, optObj.toString())
                } else {
                    val strVal = jsonObject.optString(key, "")
                    if (key.startsWith("active_profile_")) {
                        editor.putInt(key, strVal.toIntOrNull() ?: 0)
                    } else {
                        editor.putString(key, strVal)
                    }
                }
            }
            editor.apply()
            showModernPopup("Backup Imported!", "📥")
        } catch (e: Exception) {
            showModernPopup("Import Failed", "❌")
        }
    }

    // ========================================================
    // MQTT & TRANSMISSION & CAPTURE
    // ========================================================
    private fun setupMQTT(onComplete: ((Boolean, String?) -> Unit)? = null) {
        bgExecutor.execute {
            try {
                if (mqttClient != null) {
                    try { if (mqttClient!!.isConnected) mqttClient?.disconnect() } catch (e: Exception) {}
                    try { mqttClient?.close() } catch (e: Exception) {}
                    mqttClient = null
                }

                val currentClientId = "AndroidHub_" + System.currentTimeMillis() + "_" + (1000..9999).random()
                val persistence = MemoryPersistence()
                val client = MqttClient(broker, currentClientId, persistence)
                mqttClient = client

                val connOpts = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 15
                    keepAliveInterval = 60
                    isAutomaticReconnect = true
                }

                client.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Log.d("MQTT", "Connected to HiveMQ")
                        try { client.subscribe(topicRx, 0) } catch (e: Exception) {}
                        updateHardwareStatusUI()
                    }
                    override fun connectionLost(cause: Throwable?) {
                        Log.w("MQTT", "Connection lost", cause)
                        isEsp32MqttOnline = false
                        updateHardwareStatusUI()
                    }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val rawData = message?.toString() ?: return
                        Log.d("MQTT", "Incoming on $topic: $rawData")
                        if (rawData == "STATUS:ONLINE") {
                            isEsp32MqttOnline = true
                            updateHardwareStatusUI()
                        } else if (rawData == "STATUS:OFFLINE") {
                            isEsp32MqttOnline = false
                            updateHardwareStatusUI()
                        }
                        handleIncomingSignal(rawData)
                    }
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                client.connect(connOpts)
                Log.d("MQTT", "Successfully connected to $broker with ID $currentClientId")
                onComplete?.invoke(true, null)
            } catch (e: Exception) {
                Log.e("MQTT", "Error connecting: ${e.message}", e)
                val errorMsg = when {
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> "No Internet (Cannot resolve broker)"
                    e.message?.contains("timed out", ignoreCase = true) == true -> "Connection Timed Out"
                    e.message?.contains("refused", ignoreCase = true) == true -> "Connection Refused"
                    e.message?.contains("32100") == true -> "Client was already active"
                    else -> e.localizedMessage ?: e.message ?: "Network Error"
                }
                onComplete?.invoke(false, errorMsg)
            }
        }
    }

    private fun handleIncomingSignal(rawMessage: String) {
        if (isLearning && learningTargetId != null && rawMessage.startsWith("RAW:")) {
            val parts = rawMessage.split(":")
            if (parts.size >= 3) {
                val len = parts[1]
                val values = parts[2]

                val jsonSignal = JSONObject().apply {
                    put("type", "raw")
                    put("len", len.toInt())
                    put("values", values)
                }

                val targetId = learningTargetId!!
                val category = profileManager.getCategoryFromButtonId(targetId)
                profileManager.saveCode(category, targetId, jsonSignal.toString())

                val targetName = targetId.replace("_", " ")
                val activeProfileName = profileManager.getProfileName(category, profileManager.getActiveProfileIndex(category))
                isLearning = false
                learningTargetId = null

                triggerVibration()
                runOnUiThread {
                    showModernPopup("Programmed: $targetName ($activeProfileName)! ✅", "✅")
                }
            }
        }
    }

    private fun handleRemoteClick(buttonId: String) {
        triggerVibration()
        val readableName = buttonId.replace("_", " ")
        val category = profileManager.getCategoryFromButtonId(buttonId)
        val activeProfileName = profileManager.getProfileName(category, profileManager.getActiveProfileIndex(category))

        // If Programming Mode is ON, clicking any button selects it to learn
        if (isLearning) {
            learningTargetId = buttonId
            showModernPopup("Point remote at USB Hub / ESP32 to clone $readableName ($activeProfileName)", "🎯")
            return
        }

        val savedSignal = profileManager.getSavedCode(category, buttonId)
        if (savedSignal == null) {
            showModernPopup("Not programmed for $activeProfileName! Turn ON Program Mode", "⚠️")
            return
        }

        var sentViaInternalIr = false
        if (hasInternalIr) {
            try {
                val jsonObj = JSONObject(savedSignal)
                val type = jsonObj.optString("type", "raw")
                val values = jsonObj.optString("values", "")
                if (type == "raw" && values.isNotEmpty()) {
                    val pattern = values.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
                    if (pattern.isNotEmpty()) {
                        consumerIrManager?.transmit(38000, pattern)
                        sentViaInternalIr = true
                        Log.d("ConsumerIR", "Transmitted ${pattern.size} pulses via internal IR")
                    }
                }
            } catch (e: Exception) {
                Log.e("ConsumerIR", "Internal IR transmit error", e)
            }
        }

        var sentViaUsb = false
        if (usbSerialManager.isConnected) {
            try {
                val jsonObj = JSONObject(savedSignal)
                val type = jsonObj.optString("type", "raw")
                val len = jsonObj.optInt("len", 0)
                val values = jsonObj.optString("values", "")

                val cmd = if (type == "raw" && len > 0 && values.isNotEmpty()) {
                    "SEND_RAW:$len:$values\n"
                } else {
                    "$savedSignal\n"
                }
                sentViaUsb = usbSerialManager.sendCommand(cmd)
            } catch (e: Exception) {
                sentViaUsb = usbSerialManager.sendCommand("SEND_RAW:$savedSignal\n")
            }
        }

        var sentViaMqtt = false
        if (mqttClient != null && mqttClient!!.isConnected) {
            bgExecutor.execute {
                try {
                    val message = MqttMessage(savedSignal.toByteArray())
                    mqttClient?.publish(topicTx, message)
                    Log.d("MQTT", "Dispatched: $savedSignal")
                } catch (e: Exception) {
                    Log.e("MQTT", "Failed to send MQTT message", e)
                }
            }
            sentViaMqtt = true
        }

        val channels = mutableListOf<String>()
        if (sentViaInternalIr) channels.add("Phone IR")
        if (sentViaUsb) channels.add("USB")
        if (sentViaMqtt) channels.add("WiFi Hub")

        when {
            channels.isNotEmpty() -> showModernPopup("Transmitting $readableName (${channels.joinToString(" + ")})", "⚡")
            else -> showModernPopup("Hub Offline! Connect USB or WiFi", "🔴")
        }
    }

    // ========================================================
    // SLEEK IN-APP ANIMATED FLOATING HUD PILL POPUP AT TOP
    // ========================================================
    private fun showModernPopup(message: String, icon: String = "📡") {
        runOnUiThread {
            if (!::toastBanner.isInitialized) return@runOnUiThread
            currentToastMessage = message
            currentToastIcon = icon
            isToastShowing = true

            toastDismissRunnable?.let { toastHandler.removeCallbacks(it) }

            val targetBanner: View
            val targetIcon: TextView
            val targetMsg: TextView

            val currentDialog = activeDialog
            if (currentDialog != null && currentDialog.isShowing) {
                // Ensure activity-level banner is hidden so it doesn't appear dimmed behind the dialog
                toastBanner.visibility = View.GONE

                val dialogDecor = (currentDialog.window?.decorView as? ViewGroup)
                    ?: currentDialog.findViewById<FrameLayout>(com.google.android.material.R.id.container)

                val horizontalMargin = (24 * resources.displayMetrics.density).toInt()
                val statusBarHeight = run {
                    val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
                    if (resId > 0) resources.getDimensionPixelSize(resId)
                    else (38 * resources.displayMetrics.density).toInt()
                }
                val exactTopMargin = if (toastBanner.isAttachedToWindow && toastBanner.height > 0) {
                    val loc = IntArray(2)
                    toastBanner.getLocationOnScreen(loc)
                    if (loc[1] > 0) loc[1] else statusBarHeight + (24 * resources.displayMetrics.density).toInt()
                } else {
                    statusBarHeight + (24 * resources.displayMetrics.density).toInt()
                }

                var dialogBanner = dialogDecor?.findViewById<View>(R.id.dialog_toast_banner)
                if (dialogBanner == null && dialogDecor != null) {
                    dialogBanner = layoutInflater.inflate(R.layout.view_top_toast_banner, dialogDecor, false)
                    dialogBanner.id = R.id.dialog_toast_banner
                    val params = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                        setMargins(horizontalMargin, exactTopMargin, horizontalMargin, 0)
                    }
                    dialogDecor.addView(dialogBanner, params)
                } else if (dialogBanner != null) {
                    (dialogBanner.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                        setMargins(horizontalMargin, exactTopMargin, horizontalMargin, 0)
                        dialogBanner.requestLayout()
                    }
                }

                if (dialogBanner != null) {
                    dialogBanner.bringToFront()
                    dialogBanner.elevation = 100f * resources.displayMetrics.density
                    dialogBanner.translationZ = 100f * resources.displayMetrics.density
                    targetBanner = dialogBanner
                    targetIcon = dialogBanner.findViewById(R.id.toast_icon)
                    targetMsg = dialogBanner.findViewById(R.id.toast_message)
                } else {
                    targetBanner = toastBanner
                    targetIcon = toastIcon
                    targetMsg = toastMessage
                }
            } else {
                activeDialog?.findViewById<View>(R.id.dialog_toast_banner)?.visibility = View.GONE
                targetBanner = toastBanner
                targetIcon = toastIcon
                targetMsg = toastMessage
            }

            targetIcon.text = icon
            targetMsg.text = message

            attachSwipeToDismiss(targetBanner)

            targetBanner.visibility = View.VISIBLE
            targetBanner.alpha = 0f
            targetBanner.translationX = 0f
            targetBanner.translationY = -35f

            targetBanner.animate()
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()

            toastDismissRunnable = Runnable {
                targetBanner.animate()
                    .alpha(0f)
                    .translationY(-35f)
                    .setDuration(200)
                    .withEndAction {
                        targetBanner.visibility = View.GONE
                        targetBanner.translationY = 0f
                        targetBanner.translationX = 0f
                        targetBanner.alpha = 1f
                        isToastShowing = false
                        currentToastMessage = null
                    }
                    .start()
            }
            toastHandler.postDelayed(toastDismissRunnable!!, 2600)
        }
    }

    private fun attachSwipeToDismiss(banner: View) {
        var downX = 0f
        var downY = 0f
        var isDragging = false
        val touchSlop = ViewConfiguration.get(banner.context).scaledTouchSlop

        banner.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    isDragging = false
                    toastDismissRunnable?.let { toastHandler.removeCallbacks(it) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY

                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }

                    if (isDragging) {
                        v.translationX = dx
                        v.translationY = if (dy < 0) dy else dy * 0.25f
                        val dist = max(abs(dx), if (dy < 0) -dy else 0f)
                        val maxDist = 180f * resources.displayMetrics.density
                        v.alpha = (1f - (dist / maxDist)).coerceIn(0.15f, 1f)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        val threshold = 40f * resources.displayMetrics.density

                        if (dy < -threshold || abs(dx) > threshold * 1.2f) {
                            val targetTx = if (abs(dx) > threshold * 1.2f) {
                                if (dx > 0) v.width.toFloat() * 1.5f else -v.width.toFloat() * 1.5f
                            } else {
                                0f
                            }
                            val targetTy = if (dy < -threshold) -80f * resources.displayMetrics.density else v.translationY

                            v.animate()
                                .translationX(targetTx)
                                .translationY(targetTy)
                                .alpha(0f)
                                .setDuration(180)
                                .withEndAction {
                                    v.visibility = View.GONE
                                    v.translationX = 0f
                                    v.translationY = 0f
                                    v.alpha = 1f
                                    isToastShowing = false
                                    currentToastMessage = null
                                }
                                .start()
                        } else {
                            v.animate()
                                .translationX(0f)
                                .translationY(0f)
                                .alpha(1f)
                                .setDuration(200)
                                .start()
                            toastDismissRunnable?.let { toastHandler.postDelayed(it, 2200) }
                        }
                    } else {
                        v.animate()
                            .alpha(0f)
                            .translationY(-40f)
                            .setDuration(160)
                            .withEndAction {
                                v.visibility = View.GONE
                                v.translationY = 0f
                                v.translationX = 0f
                                v.alpha = 1f
                                isToastShowing = false
                                currentToastMessage = null
                            }
                            .start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun triggerVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30)
            }
        } catch (e: Exception) {
            Log.e("Haptics", "Vibration error", e)
        }
    }

    private fun updateHardwareStatusUI() {
        runOnUiThread {
            if (!::hardwareStatusText.isInitialized) return@runOnUiThread
            val isUsb = usbSerialManager.isConnected
            val boardName = usbSerialManager.connectedDeviceName
            val isWifi = isEsp32MqttOnline

            when {
                isUsb && isWifi -> {
                    hardwareStatusDot.text = "⚡"
                    hardwareStatusText.text = "$boardName + ESP32 (Wi-Fi)"
                    hardwareStatusText.setTextColor(Color.parseColor("#15803d"))
                    hardwareStatusPill.setBackgroundResource(R.drawable.bg_profile_chip_active)
                }
                isUsb -> {
                    hardwareStatusDot.text = "🔌"
                    hardwareStatusText.text = "$boardName (USB Online)"
                    hardwareStatusText.setTextColor(Color.parseColor("#1e3a8a"))
                    hardwareStatusPill.setBackgroundColor(Color.parseColor("#dbeafe"))
                }
                isWifi -> {
                    hardwareStatusDot.text = "🌐"
                    hardwareStatusText.text = "ESP32 (Wi-Fi Online)"
                    hardwareStatusText.setTextColor(Color.parseColor("#15803d"))
                    hardwareStatusPill.setBackgroundColor(Color.parseColor("#dcfce7"))
                }
                mqttClient?.isConnected == true -> {
                    hardwareStatusDot.text = "⏳"
                    hardwareStatusText.text = "ESP32 (Wi-Fi Waiting...)"
                    hardwareStatusText.setTextColor(Color.parseColor("#b45309"))
                    hardwareStatusPill.setBackgroundColor(Color.parseColor("#fef3c7"))
                }
                else -> {
                    hardwareStatusDot.text = "🔴"
                    hardwareStatusText.text = "Hardware Offline"
                    hardwareStatusText.setTextColor(Color.parseColor("#b91c1c"))
                    hardwareStatusPill.setBackgroundResource(R.drawable.bg_profile_chip_inactive)
                }
            }
        }
    }

    private fun showHardwareStatusDialog() {
        val isUsb = usbSerialManager.isConnected
        val boardName = if (isUsb) usbSerialManager.connectedDeviceName else "Not Detected"
        val usbStatus = if (isUsb) "✅ ONLINE ($boardName via USB OTG)" else "❌ Disconnected"
        val wifiStatus = when {
            isEsp32MqttOnline -> "✅ ONLINE (ESP32 IR Hub via Wi-Fi)"
            mqttClient?.isConnected == true -> "⏳ HiveMQ Ready (Waiting for ESP32)"
            else -> "❌ Offline"
        }

        val msg = "🔌 USB Connection:\n• Status: $usbStatus\n• Board: $boardName\n\n🌐 Wi-Fi Hub:\n• Status: $wifiStatus\n• Target: ESP32 IR Hub\n• Broker: broker.hivemq.com\n• Topic: universalo-hub/$hubId/rx\n\n📱 Internal Phone IR:\n• Status: " + if (hasInternalIr) "✅ Available (Built-in IR)" else "❌ Not Available on this Phone"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("📡 Hardware Status & Diagnostics")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .setNegativeButton(if (mqttClient?.isConnected == true) "Reconnect Network" else "Connect via Network") { _, _ ->
                showModernPopup("Connecting to ESP32 via Network...", "🌐")
                setupMQTT { success, errMsg ->
                    runOnUiThread {
                        updateHardwareStatusUI()
                        if (success) {
                            showModernPopup("Connected to Network Broker! 🌐", "✅")
                        } else {
                            showModernPopup("Network Failed: $errMsg", "❌")
                        }
                    }
                }
            }
            .setNeutralButton("Re-Scan USB") { _, _ ->
                usbSerialManager.connect(userInitiated = true)
                updateHardwareStatusUI()
            }
            .show()
    }

    private fun setupUsbSerial() {
        usbSerialManager = UsbSerialManager(
            context = this,
            onStatusChange = { status, isConnected ->
                runOnUiThread {
                    updateHardwareStatusUI()
                    showModernPopup(status, if (isConnected) "🔌" else "ℹ️")
                }
            },
            onDataReceived = { line ->
                Log.d("USB_RX", line)
                runOnUiThread {
                    if (line == "STATUS:ONLINE") {
                        isEsp32MqttOnline = true
                        updateHardwareStatusUI()
                    } else if (line == "STATUS:OFFLINE") {
                        isEsp32MqttOnline = false
                        updateHardwareStatusUI()
                    }
                    handleIncomingSignal(line)
                }
            }
        )
        usbSerialManager.register()
    }

    override fun onDestroy() {
        super.onDestroy()
        usbSerialManager.unregister()
        toastDismissRunnable?.let { toastHandler.removeCallbacks(it) }
        bgExecutor.execute {
            try { mqttClient?.disconnect() } catch (e: Exception) {}
        }
        bgExecutor.shutdown()
    }
}
