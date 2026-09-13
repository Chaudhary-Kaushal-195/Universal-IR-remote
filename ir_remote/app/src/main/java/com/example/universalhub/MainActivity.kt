package com.example.universalhub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.ClipboardManager
import android.content.ClipData
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.concurrent.Executors
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import android.app.TimePickerDialog
import android.app.DatePickerDialog
import android.graphics.Typeface
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class QuickOption(val name: String, val codeId: String, val icon: String = "⚡")

class MainActivity : AppCompatActivity() {

    companion object {
        var instance: MainActivity? = null
    }

    private lateinit var acTimerManager: AcTimerManager
    private lateinit var customIrManager: CustomIrManager
    private var customIrLearningCallback: ((String) -> Unit)? = null
    private lateinit var cardCustomIr: RelativeLayout

    // AC Discrete States
    private var isAcDisplayOn = true
    private var acModeIndex = 0 // 0: Cool, 1: Heat, 2: Warm, 3: Dry, 4: Auto, 5: Fan
    private var acFanSpeedIndex = 0 // 0: 1, 1: 2, 2: 3, 3: 4, 4: Auto
    private var isAcSwingOn = false
    private var isAcSleepOn = false

    private val bgExecutor = Executors.newSingleThreadExecutor()

    private var broker = "tcp://broker.hivemq.com:1883"
    private val clientId = "AndroidAppClient_" + System.currentTimeMillis()
    private var hubId = "kaushal-ir-hub-97"
    private var hubPassword = "TestKaushalSecure2026"
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

    // State for pending export payload
    private var pendingExportJson: String? = null
    private var pendingExportFileName: String = "ir_hub_backup.json"

    // File Picker Intents for Backup
    private val exportJsonLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val dataToSave = pendingExportJson ?: BackupManager.createFullBackupJson(sharedPref, profileManager, customIrManager)
                    try {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(dataToSave.toByteArray())
                        }
                        showModernPopup("Backup Exported! 📤", "✅")
                    } catch (e: Exception) {
                        Log.e("Backup", "Export error", e)
                        showModernPopup("Export Failed", "❌")
                    }
                }
            }
        }

    private val importJsonLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    try {
                        val stringBuilder = StringBuilder()
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.bufferedReader().useLines { lines ->
                                lines.forEach { stringBuilder.append(it) }
                            }
                        }
                        val rawJson = stringBuilder.toString()
                        processImportedJson(rawJson)
                    } catch (e: Exception) {
                        Log.e("Backup", "Import read error", e)
                        showModernPopup("Failed to read file", "❌")
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Force IPv4 networking to eliminate broken IPv6 route drops on cellular/Wi-Fi
        System.setProperty("java.net.preferIPv4Stack", "true")
        System.setProperty("java.net.preferIPv6Addresses", "false")

        sharedPref = getSharedPreferences("LearnedCodes", Context.MODE_PRIVATE)
        profileManager = ProfileManager(sharedPref)

        consumerIrManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        hasInternalIr = consumerIrManager?.hasIrEmitter() == true

        // Load configuration and states
        hubId = sharedPref.getString("hubId", "kaushal-ir-hub-97") ?: "kaushal-ir-hub-97"
        hubPassword = sharedPref.getString("hub_password", "TestKaushalSecure2026") ?: "TestKaushalSecure2026"
        isLightOn = sharedPref.getBoolean("state_light", true)
        isAcOn = sharedPref.getBoolean("state_ac", false)
        isTvOn = sharedPref.getBoolean("state_tv", false)
        isFanOn = sharedPref.getBoolean("state_fan", false)
        acTemp = sharedPref.getInt("acTemp", 24).coerceIn(16, 32)
        isAcDisplayOn = sharedPref.getBoolean("state_ac_display", true)
        acModeIndex = sharedPref.getInt("state_ac_mode", 0)
        acFanSpeedIndex = sharedPref.getInt("state_ac_fan", 0)
        isAcSwingOn = sharedPref.getBoolean("state_ac_swing", false)
        isAcSleepOn = sharedPref.getBoolean("state_ac_sleep", false)

        instance = this
        acTimerManager = AcTimerManager(this)
        customIrManager = CustomIrManager(this)

        topicTx = "universalo-hub/$hubId/rx"
        topicRx = "universalo-hub/$hubId/tx"

        bindViews()
        setupListeners()
        setupMQTT()
        setupUsbSerial()
        updateCardStates()
        updateHardwareStatusUI()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
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

        cardCustomIr = findViewById(R.id.card_custom_ir)
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

        // Person -> User Account Security & Login
        btnPerson.setOnClickListener {
            triggerVibration()
            showUserAccountSecurityDialog()
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

        // 5. CUSTOM UNIVERSAL IR REMOTE
        cardCustomIr.setOnClickListener {
            triggerVibration()
            showCustomIrSheet()
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

        // Custom IR Remote Card
        if (::customIrManager.isInitialized) {
            val count = customIrManager.getButtonCount()
            val subText = findViewById<TextView?>(R.id.text_custom_ir_subtitle)
            if (count > 0) {
                subText?.text = "$count Custom Set${if (count > 1) "s" else ""} • Tap to control"
            } else {
                subText?.text = "Projector, Audio, LED & Any IR Device"
            }
        }
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

        val customSection = view.findViewById<View>(R.id.ac_custom_buttons_section)
        val customContainer = view.findViewById<LinearLayout>(R.id.ac_custom_buttons_container)

        fun refreshAcCustomButtons() {
            val activeIndex = profileManager.getActiveProfileIndex(DeviceCategory.AC)
            val customButtons = getCustomBrandButtons(activeIndex)
            if (customButtons.isEmpty()) {
                customSection?.visibility = View.GONE
            } else {
                customSection?.visibility = View.VISIBLE
                customContainer?.removeAllViews()
                for (btnName in customButtons) {
                    val cleanId = "AC_CUSTOM_${btnName.uppercase(Locale.ROOT).replace("[^A-Z0-9]".toRegex(), "_")}"
                    val chip = androidx.appcompat.widget.AppCompatButton(this).apply {
                        text = btnName
                        isAllCaps = false
                        textSize = 12.5f
                        setTextColor(Color.parseColor("#1E293B"))
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                        setBackgroundResource(R.drawable.bg_sheet_btn)
                        val padH = (14 * resources.displayMetrics.density).toInt()
                        setPadding(padH, 0, padH, 0)
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            (42 * resources.displayMetrics.density).toInt()
                        ).apply {
                            marginEnd = (8 * resources.displayMetrics.density).toInt()
                        }
                        layoutParams = lp
                        stateListAnimator = null
                        setOnClickListener {
                            handleRemoteClick(cleanId)
                        }
                        setOnLongClickListener {
                            startLearningForButton(cleanId)
                            true
                        }
                    }
                    customContainer?.addView(chip)
                }
            }
        }

        setupProfileSelector(view, DeviceCategory.AC) {
            refreshAcCustomButtons()
        }
        refreshAcCustomButtons()

        view.findViewById<View>(R.id.sheet_btn_config_ac)?.setOnClickListener {
            triggerVibration()
            showAcCustomButtonsConfigDialog {
                refreshAcCustomButtons()
            }
        }

        var pendingTemp = acTemp

        val tempDisplay = view.findViewById<TextView>(R.id.sheet_ac_temp_display)
        tempDisplay?.text = "$acTemp"

        val tempWheel = view.findViewById<ArcTemperatureWheelView>(R.id.ac_temp_wheel)
        tempWheel?.minTemp = 16
        tempWheel?.maxTemp = 32
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

        // Power ON & Power OFF
        val btnPowerOn = view.findViewById<View>(R.id.AC_POWER_ON)
        btnPowerOn?.setOnClickListener {
            handleRemoteClick("AC_POWER_ON")
            if (!isLearning) {
                isAcOn = true
                sharedPref.edit().putBoolean("state_ac", isAcOn).apply()
                updateCardStates()
            }
        }
        btnPowerOn?.setOnLongClickListener {
            startLearningForButton("AC_POWER_ON")
            true
        }

        val btnPowerOff = view.findViewById<View>(R.id.AC_POWER_OFF)
        btnPowerOff?.setOnClickListener {
            handleRemoteClick("AC_POWER_OFF")
            if (!isLearning) {
                isAcOn = false
                sharedPref.edit().putBoolean("state_ac", isAcOn).apply()
                updateCardStates()
            }
        }
        btnPowerOff?.setOnLongClickListener {
            startLearningForButton("AC_POWER_OFF")
            true
        }

        // Mode Button with Multi-Mode Cycle & Quick Picker
        val modeNames = listOf("Cool", "Heat", "Warm", "Dry", "Auto", "Fan")
        val modeIds =
            listOf("AC_MODE_COOL", "AC_MODE_HOT", "AC_MODE_WARM", "AC_MODE_DRY", "AC_MODE_AUTO", "AC_MODE_FAN")
        val modeIcons = listOf("❄️", "☀️", "🌤️", "💧", "🔄", "💨")
        val btnMode = view.findViewById<Button>(R.id.AC_MODE)
        fun updateModeButtonText() {
            val safeIdx = acModeIndex.coerceIn(0, modeNames.size - 1)
            btnMode?.text = "Mode: ${modeNames[safeIdx]}"
        }
        updateModeButtonText()

        btnMode?.setOnClickListener {
            if (isLearning) {
                startLearningForButton(modeIds[acModeIndex.coerceIn(0, modeIds.size - 1)])
                return@setOnClickListener
            }
            acModeIndex = (acModeIndex + 1) % modeNames.size
            sharedPref.edit().putInt("state_ac_mode", acModeIndex).apply()
            updateModeButtonText()
            handleRemoteClickWithFallback(modeIds[acModeIndex], "AC_MODE")
        }

        btnMode?.setOnLongClickListener {
            showAcQuickPicker(
                title = "Select AC Mode",
                options = modeNames.mapIndexed { idx, name ->
                    QuickOption(name, modeIds[idx], modeIcons[idx])
                }
            ) { selectedIdx ->
                acModeIndex = selectedIdx
                sharedPref.edit().putInt("state_ac_mode", acModeIndex).apply()
                updateModeButtonText()
                handleRemoteClickWithFallback(modeIds[acModeIndex], "AC_MODE")
            }
            true
        }

        // Fan Speed Button with Multi-Speed Cycle (1, 2, 3, 4, Auto) & Quick Picker
        val fanNames = listOf("1", "2", "3", "4", "Auto")
        val fanIds = listOf("AC_FAN_1", "AC_FAN_2", "AC_FAN_3", "AC_FAN_4", "AC_FAN_AUTO")
        val btnFan = view.findViewById<Button>(R.id.AC_FAN)
        fun updateFanButtonText() {
            val safeIdx = acFanSpeedIndex.coerceIn(0, fanNames.size - 1)
            btnFan?.text = "Fan: ${fanNames[safeIdx]}"
        }
        updateFanButtonText()

        btnFan?.setOnClickListener {
            if (isLearning) {
                startLearningForButton(fanIds[acFanSpeedIndex.coerceIn(0, fanIds.size - 1)])
                return@setOnClickListener
            }
            acFanSpeedIndex = (acFanSpeedIndex + 1) % fanNames.size
            sharedPref.edit().putInt("state_ac_fan", acFanSpeedIndex).apply()
            updateFanButtonText()
            handleRemoteClickWithFallback(fanIds[acFanSpeedIndex], "AC_FAN")
        }

        btnFan?.setOnLongClickListener {
            showAcQuickPicker(
                title = "Select Fan Speed",
                options = fanNames.mapIndexed { idx, name ->
                    QuickOption(if (name == "Auto") "Speed Auto" else "Speed $name", fanIds[idx], "💨")
                }
            ) { selectedIdx ->
                acFanSpeedIndex = selectedIdx
                sharedPref.edit().putInt("state_ac_fan", acFanSpeedIndex).apply()
                updateFanButtonText()
                handleRemoteClickWithFallback(fanIds[acFanSpeedIndex], "AC_FAN")
            }
            true
        }

        // Swing Button: Toggle ON / OFF with Fallback
        val btnSwing = view.findViewById<Button>(R.id.AC_SWING)
        fun updateSwingButtonText() {
            btnSwing?.text = if (isAcSwingOn) "Swing: ON" else "Swing: OFF"
        }
        updateSwingButtonText()

        btnSwing?.setOnClickListener {
            if (isLearning) {
                startLearningForButton(if (isAcSwingOn) "AC_SWING_ON" else "AC_SWING_OFF")
                return@setOnClickListener
            }
            isAcSwingOn = !isAcSwingOn
            sharedPref.edit().putBoolean("state_ac_swing", isAcSwingOn).apply()
            updateSwingButtonText()
            val target = if (isAcSwingOn) "AC_SWING_ON" else "AC_SWING_OFF"
            handleRemoteClickWithFallback(target, "AC_SWING")
        }

        btnSwing?.setOnLongClickListener {
            showAcQuickPicker(
                title = "Swing Settings",
                options = listOf(
                    QuickOption("Swing ON", "AC_SWING_ON", "↔️"),
                    QuickOption("Swing OFF", "AC_SWING_OFF", "⏹️"),
                    QuickOption("Swing Toggle", "AC_SWING", "🔄")
                )
            ) { selectedIdx ->
                isAcSwingOn = (selectedIdx == 0)
                sharedPref.edit().putBoolean("state_ac_swing", isAcSwingOn).apply()
                updateSwingButtonText()
                val target = when (selectedIdx) {
                    0 -> "AC_SWING_ON"; 1 -> "AC_SWING_OFF"; else -> "AC_SWING"
                }
                handleRemoteClickWithFallback(target, "AC_SWING")
            }
            true
        }

        // Sleep Button: Toggle ON / OFF
        val btnSleep = view.findViewById<Button>(R.id.AC_SLEEP)
        fun updateSleepButtonText() {
            btnSleep?.text = if (isAcSleepOn) "Sleep: ON" else "Sleep: OFF"
        }
        updateSleepButtonText()

        btnSleep?.setOnClickListener {
            if (isLearning) {
                startLearningForButton(if (isAcSleepOn) "AC_SLEEP_ON" else "AC_SLEEP_OFF")
                return@setOnClickListener
            }
            isAcSleepOn = !isAcSleepOn
            sharedPref.edit().putBoolean("state_ac_sleep", isAcSleepOn).apply()
            updateSleepButtonText()
            val target = if (isAcSleepOn) "AC_SLEEP_ON" else "AC_SLEEP_OFF"
            handleRemoteClickWithFallback(target, "AC_SLEEP")
        }

        btnSleep?.setOnLongClickListener {
            showAcQuickPicker(
                title = "Sleep Mode Settings",
                options = listOf(
                    QuickOption("Sleep ON", "AC_SLEEP_ON", "🌙"),
                    QuickOption("Sleep OFF", "AC_SLEEP_OFF", "☀️"),
                    QuickOption("Sleep Toggle", "AC_SLEEP", "🔄")
                )
            ) { selectedIdx ->
                isAcSleepOn = (selectedIdx == 0)
                sharedPref.edit().putBoolean("state_ac_sleep", isAcSleepOn).apply()
                updateSleepButtonText()
                val target = when (selectedIdx) {
                    0 -> "AC_SLEEP_ON"; 1 -> "AC_SLEEP_OFF"; else -> "AC_SLEEP"
                }
                handleRemoteClickWithFallback(target, "AC_SLEEP")
            }
            true
        }

        // Display Button: Toggle ON / OFF (AC_DISPLAY_ON / AC_DISPLAY_OFF with fallback to AC_LIGHT)
        val btnDisplay = view.findViewById<Button>(R.id.AC_LIGHT)
        fun updateDisplayButtonText() {
            btnDisplay?.text = if (isAcDisplayOn) "Display: ON" else "Display: OFF"
        }
        updateDisplayButtonText()

        btnDisplay?.setOnClickListener {
            if (isLearning) {
                startLearningForButton(if (isAcDisplayOn) "AC_DISPLAY_ON" else "AC_DISPLAY_OFF")
                return@setOnClickListener
            }
            isAcDisplayOn = !isAcDisplayOn
            sharedPref.edit().putBoolean("state_ac_display", isAcDisplayOn).apply()
            updateDisplayButtonText()
            val target = if (isAcDisplayOn) "AC_DISPLAY_ON" else "AC_DISPLAY_OFF"
            handleRemoteClickWithFallback(target, "AC_LIGHT")
        }

        btnDisplay?.setOnLongClickListener {
            showAcQuickPicker(
                title = "Display Settings",
                options = listOf(
                    QuickOption("Display ON", "AC_DISPLAY_ON", "💡"),
                    QuickOption("Display OFF", "AC_DISPLAY_OFF", "🌑"),
                    QuickOption("Display Toggle", "AC_LIGHT", "🔄")
                )
            ) { selectedIdx ->
                isAcDisplayOn = (selectedIdx == 0)
                sharedPref.edit().putBoolean("state_ac_display", isAcDisplayOn).apply()
                updateDisplayButtonText()
                val target = when (selectedIdx) {
                    0 -> "AC_DISPLAY_ON"; 1 -> "AC_DISPLAY_OFF"; else -> "AC_LIGHT"
                }
                handleRemoteClickWithFallback(target, "AC_LIGHT")
            }
            true
        }

        // Timer Button: Open Smart AC Timers Modal Box
        val btnTimer = view.findViewById<View>(R.id.AC_TIMER)
        btnTimer?.setOnClickListener {
            triggerVibration()
            showAcTimersSheet()
        }
        btnTimer?.setOnLongClickListener {
            triggerVibration()
            showAcTimersSheet()
            true
        }

        sheet.show()
    }

    private fun getCustomBrandButtons(profileIndex: Int): MutableList<String> {
        val raw = sharedPref.getString("ac_custom_brand_buttons_$profileIndex", "") ?: ""
        if (raw.isEmpty()) return mutableListOf()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    private fun saveCustomBrandButtons(profileIndex: Int, buttons: List<String>) {
        val raw = buttons.joinToString(",")
        sharedPref.edit().putString("ac_custom_brand_buttons_$profileIndex", raw).apply()
    }

    private fun showAcQuickPicker(
        title: String,
        options: List<QuickOption>,
        onSelected: (Int) -> Unit
    ) {
        val pickerSheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.dialog_ac_quick_picker, null)
        pickerSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

        view.findViewById<TextView>(R.id.tv_quick_picker_title)?.text = title
        view.findViewById<View>(R.id.btn_close_quick_picker)?.setOnClickListener {
            triggerVibration()
            pickerSheet.dismiss()
        }

        val container = view.findViewById<LinearLayout>(R.id.container_quick_picker_items)
        container?.removeAllViews()

        for ((idx, opt) in options.withIndex()) {
            val itemView = layoutInflater.inflate(R.layout.item_custom_button, container, false)
            itemView.findViewById<TextView>(R.id.tv_custom_btn_name).text = "${opt.icon}  ${opt.name}"
            itemView.findViewById<TextView>(R.id.tv_custom_btn_id).text = opt.codeId

            val category = profileManager.getCategoryFromButtonId(opt.codeId)
            val isLearned = profileManager.getSavedCode(category, opt.codeId) != null
            val statusBadge = itemView.findViewById<TextView>(R.id.tv_btn_status_badge)
            statusBadge.text = if (isLearned) "🟢 Programmed" else "⚪ Fallback"
            statusBadge.setTextColor(if (isLearned) Color.parseColor("#15803D") else Color.parseColor("#64748B"))

            val btnTest = itemView.findViewById<Button>(R.id.btn_action_test)
            btnTest.text = "Select & Send"
            btnTest.setOnClickListener {
                triggerVibration()
                onSelected(idx)
                pickerSheet.dismiss()
            }

            val btnLearn = itemView.findViewById<Button>(R.id.btn_action_learn)
            btnLearn.text = "🎯 Learn"
            btnLearn.setOnClickListener {
                pickerSheet.dismiss()
                startLearningForButton(opt.codeId)
            }

            val btnDelete = itemView.findViewById<Button>(R.id.btn_action_delete)
            btnDelete.visibility = View.GONE

            container?.addView(itemView)
        }
        pickerSheet.show()
    }

    private fun showAcTimersSheet() {
        val sheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.sheet_ac_timers, null)
        sheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

        val btnClose = view.findViewById<View>(R.id.sheet_btn_close_timers)
        val btnAdd = view.findViewById<View>(R.id.btn_add_ac_timer)
        val btnEmptyAdd = view.findViewById<View>(R.id.btn_empty_create_timer)
        val emptyLayout = view.findViewById<View>(R.id.layout_timers_empty)
        val container = view.findViewById<LinearLayout>(R.id.container_timers_list)

        btnClose?.setOnClickListener {
            triggerVibration()
            sheet.dismiss()
        }

        fun refreshTimers() {
            container?.removeAllViews()
            val timers = acTimerManager.getAllTimers()
            if (timers.isEmpty()) {
                emptyLayout?.visibility = View.VISIBLE
            } else {
                emptyLayout?.visibility = View.GONE
                for (timer in timers) {
                    val itemView = layoutInflater.inflate(R.layout.item_ac_timer, container, false)
                    itemView.findViewById<TextView>(R.id.tv_timer_time).text = timer.getTimeDigits()
                    itemView.findViewById<TextView>(R.id.tv_timer_ampm).text = timer.getAmPm()
                    itemView.findViewById<TextView>(R.id.tv_timer_title).text = timer.title
                    itemView.findViewById<TextView>(R.id.tv_timer_action).text = timer.getActionDescription()
                    itemView.findViewById<TextView>(R.id.tv_timer_repeat).text = timer.getRepeatDescription()

                    val switch = itemView.findViewById<SwitchCompat>(R.id.timer_switch)
                    switch.isChecked = timer.isEnabled
                    switch.setOnCheckedChangeListener { _, isChecked ->
                        triggerVibration()
                        acTimerManager.setTimerEnabled(timer.id, isChecked)
                        showModernPopup(
                            if (isChecked) "Timer Activated: ${timer.title}" else "Timer Deactivated: ${timer.title}",
                            "⏰"
                        )
                    }

                    itemView.findViewById<View>(R.id.btn_timer_run_now)?.setOnClickListener {
                        triggerVibration()
                        executeTimerAction(timer)
                    }

                    itemView.findViewById<View>(R.id.btn_timer_delete)?.setOnClickListener {
                        triggerVibration()
                        acTimerManager.deleteTimer(timer.id)
                        refreshTimers()
                        showModernPopup("Timer Deleted: ${timer.title}", "🗑️")
                    }

                    itemView.setOnClickListener {
                        triggerVibration()
                        showEditAcTimerDialog(timer) {
                            refreshTimers()
                        }
                    }

                    container?.addView(itemView)
                }
            }
        }

        btnAdd?.setOnClickListener {
            triggerVibration()
            showEditAcTimerDialog(null) {
                refreshTimers()
            }
        }

        btnEmptyAdd?.setOnClickListener {
            triggerVibration()
            showEditAcTimerDialog(null) {
                refreshTimers()
            }
        }

        refreshTimers()
        sheet.show()
    }

    private fun showEditAcTimerDialog(timerToEdit: AcTimer?, onSaved: () -> Unit) {
        val editSheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.dialog_edit_ac_timer, null)
        editSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)
        editSheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        editSheet.behavior.skipCollapsed = true

        val inputName = view.findViewById<EditText>(R.id.input_timer_name)
        inputName?.setText(timerToEdit?.title ?: "Morning Cool")

        // 1. Time Wheel Setup (Samsung Clock 3-Column Rolling Wheel: Hour : Minute am/pm)
        val pickerHour = view.findViewById<NumberPicker>(R.id.picker_samsung_hour)
        val pickerMinute = view.findViewById<NumberPicker>(R.id.picker_samsung_minute)
        val pickerAmPm = view.findViewById<NumberPicker>(R.id.picker_samsung_ampm)

        fun setupSamsungNumberPicker(picker: NumberPicker) {
            picker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            picker.wrapSelectorWheel = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                picker.textColor = Color.parseColor("#0F172A")
                picker.textSize = 72f
                picker.selectionDividerHeight = 0
            }
            try {
                val f = NumberPicker::class.java.getDeclaredField("mSelectionDivider")
                f.isAccessible = true
                f.set(picker, ColorDrawable(Color.TRANSPARENT))
            } catch (_: Exception) {
            }
        }

        setupSamsungNumberPicker(pickerHour)
        pickerHour.minValue = 1
        pickerHour.maxValue = 12

        setupSamsungNumberPicker(pickerMinute)
        pickerMinute.minValue = 0
        pickerMinute.maxValue = 59
        pickerMinute.setFormatter { String.format(Locale.getDefault(), "%02d", it) }

        setupSamsungNumberPicker(pickerAmPm)
        pickerAmPm.minValue = 0
        pickerAmPm.maxValue = 1
        pickerAmPm.displayedValues = arrayOf("am", "pm")

        val initH24 = timerToEdit?.hour ?: 6
        val initMin = timerToEdit?.minute ?: 30
        val initH12 = when {
            initH24 == 0 -> 12
            initH24 > 12 -> initH24 - 12
            else -> initH24
        }
        val initAmPm = if (initH24 < 12) 0 else 1
        pickerHour.value = initH12
        pickerMinute.value = initMin
        pickerAmPm.value = initAmPm

        // 2. Day & Date Selector Setup (S M T W T F S & Calendar Picker)
        var chosenSpecificDate: String? = timerToEdit?.specificDate
        val selectedDaysSet = (timerToEdit?.getDaysList() ?: listOf(1, 2, 3, 4, 5, 6, 7)).toMutableSet()
        val tvNextTrigger = view.findViewById<TextView>(R.id.tv_next_trigger_date)
        val tvSpecificDateSubtext = view.findViewById<TextView>(R.id.tv_specific_date_subtext)
        val btnClearSpecificDate = view.findViewById<TextView>(R.id.btn_clear_specific_date)
        val iconCalendar = view.findViewById<TextView>(R.id.icon_calendar)
        val layoutDateHeader = view.findViewById<View>(R.id.layout_date_header_container)

        val dayViews = listOf(
            Triple(1, view.findViewById<TextView>(R.id.btn_day_sun), true),
            Triple(2, view.findViewById<TextView>(R.id.btn_day_mon), false),
            Triple(3, view.findViewById<TextView>(R.id.btn_day_tue), false),
            Triple(4, view.findViewById<TextView>(R.id.btn_day_wed), false),
            Triple(5, view.findViewById<TextView>(R.id.btn_day_thu), false),
            Triple(6, view.findViewById<TextView>(R.id.btn_day_fri), false),
            Triple(7, view.findViewById<TextView>(R.id.btn_day_sat), false)
        )

        fun updateNextTriggerDate() {
            val h12 = pickerHour.value
            val isPm = pickerAmPm.value == 1
            val h24 = if (isPm) {
                if (h12 == 12) 12 else h12 + 12
            } else {
                if (h12 == 12) 0 else h12
            }
            val min = pickerMinute.value
            val now = Calendar.getInstance()

            if (!chosenSpecificDate.isNullOrEmpty()) {
                try {
                    val parts = chosenSpecificDate!!.split("-")
                    val y = parts[0].toInt()
                    val m = parts[1].toInt() - 1
                    val d = parts[2].toInt()
                    val target = Calendar.getInstance().apply {
                        set(Calendar.YEAR, y)
                        set(Calendar.MONTH, m)
                        set(Calendar.DAY_OF_MONTH, d)
                        set(Calendar.HOUR_OF_DAY, h24)
                        set(Calendar.MINUTE, min)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val dayName = when (target.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.SUNDAY -> "Sun"
                        Calendar.MONDAY -> "Mon"
                        Calendar.TUESDAY -> "Tue"
                        Calendar.WEDNESDAY -> "Wed"
                        Calendar.THURSDAY -> "Thu"
                        Calendar.FRIDAY -> "Fri"
                        else -> "Sat"
                    }
                    val monthNames =
                        arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
                    val monthName = monthNames.getOrElse(m) { "Sept" }

                    val isToday =
                        target.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) && target.get(Calendar.YEAR) == now.get(
                            Calendar.YEAR
                        )
                    val isTomorrow =
                        target.get(Calendar.DAY_OF_YEAR) == (now.get(Calendar.DAY_OF_YEAR) + 1) && target.get(Calendar.YEAR) == now.get(
                            Calendar.YEAR
                        )
                    val prefix = when {
                        isToday -> "Today • "
                        isTomorrow -> "Tomorrow • "
                        else -> ""
                    }
                    tvNextTrigger?.text = "$prefix$dayName, $d $monthName $y"

                    val diffDays = ((target.timeInMillis - now.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
                    val relativeText = when {
                        isToday -> "Executes today at set time"
                        isTomorrow -> "Executes tomorrow at set time"
                        diffDays > 0 -> "One-time schedule • In $diffDays days"
                        else -> "One-time schedule • Specific date"
                    }
                    tvSpecificDateSubtext?.text = "📅 $relativeText"
                    tvSpecificDateSubtext?.visibility = View.VISIBLE
                    btnClearSpecificDate?.visibility = View.VISIBLE
                    iconCalendar?.setBackgroundResource(R.drawable.bg_samsung_chip_selected)
                    return
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error formatting specific date", e)
                }
            }

            // Normal recurring schedule
            tvSpecificDateSubtext?.visibility = View.GONE
            btnClearSpecificDate?.visibility = View.GONE
            iconCalendar?.setBackgroundResource(R.drawable.bg_samsung_chip_unselected)

            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h24)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            if (selectedDaysSet.isNotEmpty() && selectedDaysSet.size < 7) {
                var count = 0
                while (!selectedDaysSet.contains(target.get(Calendar.DAY_OF_WEEK)) && count < 14) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                    count++
                }
            }

            val isToday =
                target.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) && target.get(Calendar.YEAR) == now.get(
                    Calendar.YEAR
                )
            val isTomorrow =
                target.get(Calendar.DAY_OF_YEAR) == (now.get(Calendar.DAY_OF_YEAR) + 1) && target.get(Calendar.YEAR) == now.get(
                    Calendar.YEAR
                )
            val prefix = when {
                selectedDaysSet.isEmpty() -> "Once • "
                isToday -> "Today • "
                isTomorrow -> "Tomorrow • "
                else -> ""
            }
            val dayName = when (target.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "Sun"
                Calendar.MONDAY -> "Mon"
                Calendar.TUESDAY -> "Tue"
                Calendar.WEDNESDAY -> "Wed"
                Calendar.THURSDAY -> "Thu"
                Calendar.FRIDAY -> "Fri"
                else -> "Sat"
            }
            val monthNames =
                arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
            val monthName = monthNames.getOrElse(target.get(Calendar.MONTH)) { "Sept" }
            val dayNum = target.get(Calendar.DAY_OF_MONTH)
            tvNextTrigger?.text = "$prefix$dayName, $dayNum $monthName"
        }

        fun updateDayButtonsUI() {
            if (!chosenSpecificDate.isNullOrEmpty()) {
                val targetDayOfWeek = try {
                    val parts = chosenSpecificDate!!.split("-")
                    val cal = Calendar.getInstance().apply {
                        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                    }
                    cal.get(Calendar.DAY_OF_WEEK)
                } catch (_: Exception) {
                    -1
                }

                for ((day, btn, isSun) in dayViews) {
                    if (btn == null) continue
                    if (day == targetDayOfWeek) {
                        btn.setBackgroundResource(if (isSun) R.drawable.bg_samsung_day_selected_sun else R.drawable.bg_samsung_day_selected)
                        btn.setTextColor(Color.WHITE)
                    } else {
                        btn.setBackgroundResource(R.drawable.bg_samsung_day_unselected)
                        btn.setTextColor(if (isSun) Color.parseColor("#EF4444") else Color.parseColor("#64748B"))
                    }
                }
            } else {
                for ((day, btn, isSun) in dayViews) {
                    if (btn == null) continue
                    val isSelected = selectedDaysSet.contains(day)
                    if (isSelected) {
                        btn.setBackgroundResource(if (isSun) R.drawable.bg_samsung_day_selected_sun else R.drawable.bg_samsung_day_selected)
                        btn.setTextColor(Color.WHITE)
                    } else {
                        btn.setBackgroundResource(R.drawable.bg_samsung_day_unselected)
                        btn.setTextColor(if (isSun) Color.parseColor("#EF4444") else Color.parseColor("#64748B"))
                    }
                }
            }
            updateNextTriggerDate()
        }

        for ((day, btn, _) in dayViews) {
            btn?.setOnClickListener {
                triggerVibration()
                if (chosenSpecificDate != null) {
                    chosenSpecificDate = null
                    selectedDaysSet.clear()
                    selectedDaysSet.add(day)
                } else {
                    if (selectedDaysSet.contains(day)) {
                        selectedDaysSet.remove(day)
                    } else {
                        selectedDaysSet.add(day)
                    }
                }
                updateDayButtonsUI()
            }
        }

        fun openSpecificDatePicker() {
            triggerVibration()
            val cal = Calendar.getInstance()
            if (!chosenSpecificDate.isNullOrEmpty()) {
                try {
                    val parts = chosenSpecificDate!!.split("-")
                    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                } catch (_: Exception) {
                }
            }
            val dpd = DatePickerDialog(
                this@MainActivity,
                R.style.Theme_AppDatePicker,
                { _, year, monthOfYear, dayOfMonth ->
                    triggerVibration()
                    chosenSpecificDate = String.format(Locale.US, "%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
                    updateDayButtonsUI()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
            dpd.datePicker.minDate = System.currentTimeMillis() - 1000

            // Clean rounded dialog styling matching the app
            dpd.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_calendar)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dpd.window?.decorView?.isForceDarkAllowed = false
            }
            dpd.window?.decorView?.clipToOutline = true

            dpd.show()

            try {
                dpd.getButton(DatePickerDialog.BUTTON_POSITIVE)?.apply {
                    setTextColor(Color.parseColor("#2563EB"))
                    setTypeface(null, Typeface.BOLD)
                }
                dpd.getButton(DatePickerDialog.BUTTON_NEGATIVE)?.apply {
                    setTextColor(Color.parseColor("#64748B"))
                    setTypeface(null, Typeface.BOLD)
                }
            } catch (_: Exception) {
            }
        }

        iconCalendar?.setOnClickListener { openSpecificDatePicker() }
        layoutDateHeader?.setOnClickListener { openSpecificDatePicker() }

        btnClearSpecificDate?.setOnClickListener {
            triggerVibration()
            chosenSpecificDate = null
            updateDayButtonsUI()
        }

        pickerHour.setOnValueChangedListener { _, _, _ -> updateNextTriggerDate() }
        pickerMinute.setOnValueChangedListener { _, _, _ -> updateNextTriggerDate() }
        pickerAmPm.setOnValueChangedListener { _, _, _ -> updateNextTriggerDate() }
        updateDayButtonsUI()

        // 3. Action Selection & Parameter Views
        var chosenAction = timerToEdit?.actionType ?: "POWER_OFF"
        var chosenTemp = timerToEdit?.actionValue?.toIntOrNull() ?: 24
        var chosenMode = if (timerToEdit?.actionType == "SET_MODE") (timerToEdit?.actionValue ?: "COOL") else "COOL"
        var chosenFan = if (timerToEdit?.actionType == "SET_FAN") (timerToEdit?.actionValue ?: "1") else "1"
        var chosenToggle =
            if (timerToEdit?.actionType in listOf("SLEEP", "DISPLAY")) (timerToEdit?.actionValue ?: "ON") else "ON"

        val tvActionSubtext = view.findViewById<TextView>(R.id.tv_action_subtext)
        val layoutTemp = view.findViewById<View>(R.id.layout_samsung_temp)
        val layoutMode = view.findViewById<View>(R.id.layout_samsung_mode)
        val layoutFan = view.findViewById<View>(R.id.layout_samsung_fan)
        val layoutToggle = view.findViewById<View>(R.id.layout_samsung_toggle)
        val tvToggleTitle = view.findViewById<TextView>(R.id.tv_samsung_toggle_title)

        val chipPowerOff = view.findViewById<TextView>(R.id.chip_power_off)
        val chipPowerOn = view.findViewById<TextView>(R.id.chip_power_on)
        val chipTemp = view.findViewById<TextView>(R.id.chip_temp)
        val chipMode = view.findViewById<TextView>(R.id.chip_mode)
        val chipFan = view.findViewById<TextView>(R.id.chip_fan)
        val chipSleep = view.findViewById<TextView>(R.id.chip_sleep)
        val chipDisplay = view.findViewById<TextView>(R.id.chip_display)

        val actionChips = listOf(
            Pair("POWER_OFF", chipPowerOff),
            Pair("POWER_ON", chipPowerOn),
            Pair("SET_TEMP", chipTemp),
            Pair("SET_MODE", chipMode),
            Pair("SET_FAN", chipFan),
            Pair("SLEEP", chipSleep),
            Pair("DISPLAY", chipDisplay)
        )

        fun updateActionUI() {
            for ((type, chip) in actionChips) {
                if (chip == null) continue
                val isSelected = type == chosenAction
                chip.setBackgroundResource(if (isSelected) R.drawable.bg_samsung_chip_selected else R.drawable.bg_samsung_chip_unselected)
                chip.setTextColor(if (isSelected) Color.parseColor("#1D4ED8") else Color.parseColor("#64748B"))
            }

            layoutTemp?.visibility = if (chosenAction == "SET_TEMP") View.VISIBLE else View.GONE
            layoutMode?.visibility = if (chosenAction == "SET_MODE") View.VISIBLE else View.GONE
            layoutFan?.visibility = if (chosenAction == "SET_FAN") View.VISIBLE else View.GONE
            layoutToggle?.visibility = if (chosenAction in listOf("SLEEP", "DISPLAY")) View.VISIBLE else View.GONE

            if (chosenAction == "SLEEP") {
                tvToggleTitle?.text = "Sleep Mode State"
            } else if (chosenAction == "DISPLAY") {
                tvToggleTitle?.text = "Display Light State"
            }

            tvActionSubtext?.text = when (chosenAction) {
                "POWER_OFF" -> "🔴 Power OFF"
                "POWER_ON" -> "⚡ Power ON"
                "SET_TEMP" -> "🌡️ $chosenTemp°C"
                "SET_MODE" -> "❄️ Mode: ${chosenMode.lowercase().replaceFirstChar { it.uppercase() }}"
                "SET_FAN" -> "💨 Fan: Speed $chosenFan"
                "SLEEP" -> "🌙 Sleep: ${chosenToggle.uppercase(Locale.ROOT)}"
                "DISPLAY" -> "💡 Display: ${chosenToggle.uppercase(Locale.ROOT)}"
                else -> "⚡ Action"
            }
        }

        for ((type, chip) in actionChips) {
            chip?.setOnClickListener {
                triggerVibration()
                chosenAction = type
                updateActionUI()
            }
        }

        // Temperature Stepper Controls (16 to 32°C)
        val tvTempVal = view.findViewById<TextView>(R.id.tv_samsung_temp_val)
        fun updateTempDisplay() {
            tvTempVal?.text = "$chosenTemp°C"
            updateActionUI()
        }
        updateTempDisplay()

        view.findViewById<View>(R.id.btn_samsung_temp_minus)?.setOnClickListener {
            triggerVibration()
            if (chosenTemp > 16) {
                chosenTemp--
                updateTempDisplay()
            }
        }
        view.findViewById<View>(R.id.btn_samsung_temp_plus)?.setOnClickListener {
            triggerVibration()
            if (chosenTemp < 32) {
                chosenTemp++
                updateTempDisplay()
            }
        }

        // Mode Chips
        val modeChips = listOf(
            Pair("COOL", view.findViewById<TextView>(R.id.chip_mode_cool)),
            Pair("HEAT", view.findViewById<TextView>(R.id.chip_mode_heat)),
            Pair("WARM", view.findViewById<TextView>(R.id.chip_mode_warm)),
            Pair("DRY", view.findViewById<TextView>(R.id.chip_mode_dry)),
            Pair("AUTO", view.findViewById<TextView>(R.id.chip_mode_auto)),
            Pair("FAN", view.findViewById<TextView>(R.id.chip_mode_fan))
        )

        fun updateModeChipsUI() {
            for ((mode, chip) in modeChips) {
                if (chip == null) continue
                val isSelected = mode.equals(chosenMode, true)
                chip.setBackgroundResource(if (isSelected) R.drawable.bg_samsung_chip_selected else R.drawable.bg_samsung_chip_unselected)
                chip.setTextColor(if (isSelected) Color.parseColor("#1D4ED8") else Color.parseColor("#64748B"))
            }
            updateActionUI()
        }
        for ((mode, chip) in modeChips) {
            chip?.setOnClickListener {
                triggerVibration()
                chosenMode = mode
                updateModeChipsUI()
            }
        }
        updateModeChipsUI()

        // Fan Speed Chips
        val fanChips = listOf(
            Pair("1", view.findViewById<TextView>(R.id.chip_fan_1)),
            Pair("2", view.findViewById<TextView>(R.id.chip_fan_2)),
            Pair("3", view.findViewById<TextView>(R.id.chip_fan_3)),
            Pair("4", view.findViewById<TextView>(R.id.chip_fan_4)),
            Pair("AUTO", view.findViewById<TextView>(R.id.chip_fan_auto))
        )

        fun updateFanChipsUI() {
            for ((fan, chip) in fanChips) {
                if (chip == null) continue
                val isSelected = fan.equals(chosenFan, true)
                chip.setBackgroundResource(if (isSelected) R.drawable.bg_samsung_chip_selected else R.drawable.bg_samsung_chip_unselected)
                chip.setTextColor(if (isSelected) Color.parseColor("#1D4ED8") else Color.parseColor("#64748B"))
            }
            updateActionUI()
        }
        for ((fan, chip) in fanChips) {
            chip?.setOnClickListener {
                triggerVibration()
                chosenFan = fan
                updateFanChipsUI()
            }
        }
        updateFanChipsUI()

        // Toggle Chips (Turn ON vs Turn OFF)
        val chipToggleOn = view.findViewById<TextView>(R.id.chip_toggle_on)
        val chipToggleOff = view.findViewById<TextView>(R.id.chip_toggle_off)
        fun updateToggleChipsUI() {
            val isOn = chosenToggle.equals("ON", true)
            chipToggleOn?.setBackgroundResource(if (isOn) R.drawable.bg_samsung_chip_selected else R.drawable.bg_samsung_chip_unselected)
            chipToggleOn?.setTextColor(if (isOn) Color.parseColor("#1D4ED8") else Color.parseColor("#64748B"))
            chipToggleOff?.setBackgroundResource(if (!isOn) R.drawable.bg_samsung_chip_selected else R.drawable.bg_samsung_chip_unselected)
            chipToggleOff?.setTextColor(if (!isOn) Color.parseColor("#1D4ED8") else Color.parseColor("#64748B"))
            updateActionUI()
        }
        chipToggleOn?.setOnClickListener {
            triggerVibration()
            chosenToggle = "ON"
            updateToggleChipsUI()
        }
        chipToggleOff?.setOnClickListener {
            triggerVibration()
            chosenToggle = "OFF"
            updateToggleChipsUI()
        }
        updateToggleChipsUI()
        updateActionUI()

        // 4. Bottom Floating Pill Bar (Cancel | Save)
        view.findViewById<View>(R.id.btn_samsung_cancel)?.setOnClickListener {
            triggerVibration()
            editSheet.dismiss()
        }

        view.findViewById<View>(R.id.btn_samsung_save)?.setOnClickListener {
            triggerVibration()
            val titleText = inputName?.text?.toString()?.trim().let {
                if (it.isNullOrEmpty()) "AC Scheduled Timer" else it
            }

            val h12 = pickerHour.value
            val isPm = pickerAmPm.value == 1
            val finalHour24 = if (isPm) {
                if (h12 == 12) 12 else h12 + 12
            } else {
                if (h12 == 12) 0 else h12
            }
            val finalMinute = pickerMinute.value

            val actionVal = when (chosenAction) {
                "SET_TEMP" -> chosenTemp.toString()
                "SET_MODE" -> chosenMode
                "SET_FAN" -> chosenFan
                "SLEEP", "DISPLAY" -> chosenToggle
                else -> ""
            }

            val timer = AcTimer(
                id = timerToEdit?.id ?: java.util.UUID.randomUUID().toString(),
                title = titleText,
                hour = finalHour24,
                minute = finalMinute,
                actionType = chosenAction,
                actionValue = actionVal,
                specificDate = chosenSpecificDate,
                isEnabled = true,
                profileIndex = profileManager.getActiveProfileIndex(DeviceCategory.AC)
            )
            if (chosenSpecificDate != null) {
                timer.repeatType = "SPECIFIC_DATE"
                timer.selectedDays = ""
            } else {
                timer.setDaysList(selectedDaysSet.toList())
            }

            acTimerManager.addOrUpdateTimer(timer)
            showModernPopup("⏰ Timer '${timer.title}' Scheduled!", "✅")
            onSaved()
            editSheet.dismiss()
        }

        editSheet.show()
    }

    private fun showAcCustomButtonsConfigDialog(onDismissed: (() -> Unit)? = null) {
        val sheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.dialog_ac_custom_buttons, null)
        sheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

        val activeProfileIndex = profileManager.getActiveProfileIndex(DeviceCategory.AC)
        val profileName = profileManager.getProfileName(DeviceCategory.AC, activeProfileIndex)
        view.findViewById<TextView>(R.id.tv_config_profile_subtitle)?.text =
            "Universal IR Mapping • Active Profile: $profileName"

        view.findViewById<View>(R.id.btn_close_custom_config)?.setOnClickListener {
            triggerVibration()
            sheet.dismiss()
        }
        sheet.setOnDismissListener {
            onDismissed?.invoke()
        }

        val container = view.findViewById<LinearLayout>(R.id.container_custom_buttons_list)
        val addBar = view.findViewById<View>(R.id.layout_add_custom_btn_bar)
        val inputNewCustomName = view.findViewById<EditText>(R.id.input_new_custom_btn_name)
        val btnCreateCustom = view.findViewById<View>(R.id.btn_create_custom_btn)

        var currentTab = "ALL"

        val tabAll = view.findViewById<Button>(R.id.tab_cat_all)
        val tabModes = view.findViewById<Button>(R.id.tab_cat_modes)
        val tabFans = view.findViewById<Button>(R.id.tab_cat_fans)
        val tabToggles = view.findViewById<Button>(R.id.tab_cat_toggles)
        val tabTemps = view.findViewById<Button>(R.id.tab_cat_temps)
        val tabCustom = view.findViewById<Button>(R.id.tab_cat_custom)
        val tabs = listOf(tabAll, tabModes, tabFans, tabToggles, tabTemps, tabCustom)

        fun updateTabsUi(activeButton: Button) {
            for (t in tabs) {
                if (t == activeButton) {
                    t.setBackgroundResource(R.drawable.bg_sheet_btn_active)
                    t.setTextColor(Color.WHITE)
                } else {
                    t.setBackgroundResource(R.drawable.bg_sheet_btn)
                    t.setTextColor(Color.parseColor("#1E293B"))
                }
            }
        }

        data class ConfigItem(val name: String, val id: String, val category: String)

        fun loadItemsForTab(tab: String): List<ConfigItem> {
            val list = mutableListOf<ConfigItem>()

            if (tab == "ALL") {
                list.add(ConfigItem("Power ON", "AC_POWER_ON", "POWER"))
                list.add(ConfigItem("Power OFF", "AC_POWER_OFF", "POWER"))
            }

            if (tab == "ALL" || tab == "MODES") {
                list.add(ConfigItem("Mode: Cool", "AC_MODE_COOL", "MODES"))
                list.add(ConfigItem("Mode: Heat / Hot", "AC_MODE_HOT", "MODES"))
                list.add(ConfigItem("Mode: Warm", "AC_MODE_WARM", "MODES"))
                list.add(ConfigItem("Mode: Dry / Normal", "AC_MODE_DRY", "MODES"))
                list.add(ConfigItem("Mode: Auto", "AC_MODE_AUTO", "MODES"))
                list.add(ConfigItem("Mode: Fan", "AC_MODE_FAN", "MODES"))
                list.add(ConfigItem("Mode: Cycle Toggle", "AC_MODE", "MODES"))
            }

            if (tab == "ALL" || tab == "FANS") {
                list.add(ConfigItem("Fan Speed: 1", "AC_FAN_1", "FANS"))
                list.add(ConfigItem("Fan Speed: 2", "AC_FAN_2", "FANS"))
                list.add(ConfigItem("Fan Speed: 3", "AC_FAN_3", "FANS"))
                list.add(ConfigItem("Fan Speed: 4", "AC_FAN_4", "FANS"))
                list.add(ConfigItem("Fan Speed: Auto", "AC_FAN_AUTO", "FANS"))
                list.add(ConfigItem("Fan: Cycle Toggle", "AC_FAN", "FANS"))
            }

            if (tab == "ALL" || tab == "TOGGLES") {
                list.add(ConfigItem("Display: ON", "AC_DISPLAY_ON", "TOGGLES"))
                list.add(ConfigItem("Display: OFF", "AC_DISPLAY_OFF", "TOGGLES"))
                list.add(ConfigItem("Display: Toggle", "AC_LIGHT", "TOGGLES"))
                list.add(ConfigItem("Swing: ON", "AC_SWING_ON", "TOGGLES"))
                list.add(ConfigItem("Swing: OFF", "AC_SWING_OFF", "TOGGLES"))
                list.add(ConfigItem("Swing: Toggle", "AC_SWING", "TOGGLES"))
                list.add(ConfigItem("Sleep: ON", "AC_SLEEP_ON", "TOGGLES"))
                list.add(ConfigItem("Sleep: OFF", "AC_SLEEP_OFF", "TOGGLES"))
                list.add(ConfigItem("Sleep: Toggle", "AC_SLEEP", "TOGGLES"))
            }

            if (tab == "ALL" || tab == "TEMPS") {
                for (temp in 16..32) {
                    list.add(ConfigItem("Temp $temp°C", "AC_TEMP_$temp", "TEMPS"))
                }
            }

            if (tab == "ALL" || tab == "CUSTOM") {
                val customButtons = getCustomBrandButtons(activeProfileIndex)
                for (btnName in customButtons) {
                    val cleanId = "AC_CUSTOM_${btnName.uppercase(Locale.ROOT).replace("[^A-Z0-9]".toRegex(), "_")}"
                    list.add(ConfigItem("Brand Custom: $btnName", cleanId, "CUSTOM"))
                }
            }
            return list
        }

        fun refreshList() {
            container?.removeAllViews()
            addBar?.visibility = if (currentTab == "CUSTOM") View.VISIBLE else View.GONE
            val items = loadItemsForTab(currentTab)

            for (item in items) {
                val row = layoutInflater.inflate(R.layout.item_custom_button, container, false)
                row.findViewById<TextView>(R.id.tv_custom_btn_name).text = item.name
                row.findViewById<TextView>(R.id.tv_custom_btn_id).text = item.id

                val saved = profileManager.getSavedCode(DeviceCategory.AC, item.id)
                val badge = row.findViewById<TextView>(R.id.tv_btn_status_badge)
                if (saved != null) {
                    badge.text = "🟢 Programmed"
                    badge.setTextColor(Color.parseColor("#15803D"))
                } else {
                    badge.text = "⚪ Not Learned"
                    badge.setTextColor(Color.parseColor("#64748B"))
                }

                row.findViewById<View>(R.id.btn_action_test)?.setOnClickListener {
                    triggerVibration()
                    handleRemoteClick(item.id)
                }

                row.findViewById<View>(R.id.btn_action_learn)?.setOnClickListener {
                    triggerVibration()
                    sheet.dismiss()
                    startLearningForButton(item.id)
                }

                val btnDelete = row.findViewById<View>(R.id.btn_action_delete)
                btnDelete?.setOnClickListener {
                    triggerVibration()
                    if (item.id.startsWith("AC_CUSTOM_")) {
                        val customButtons = getCustomBrandButtons(activeProfileIndex)
                        val btnName = item.name.removePrefix("Brand Custom: ")
                        customButtons.remove(btnName)
                        saveCustomBrandButtons(activeProfileIndex, customButtons)
                        profileManager.saveCode(DeviceCategory.AC, item.id, "")
                        showModernPopup("Removed custom button: $btnName", "🗑️")
                    } else {
                        profileManager.saveCode(DeviceCategory.AC, item.id, "")
                        showModernPopup("Cleared code for: ${item.name}", "🗑️")
                    }
                    refreshList()
                }

                container?.addView(row)
            }
        }

        tabAll.setOnClickListener { currentTab = "ALL"; updateTabsUi(tabAll); refreshList() }
        tabModes.setOnClickListener { currentTab = "MODES"; updateTabsUi(tabModes); refreshList() }
        tabFans.setOnClickListener { currentTab = "FANS"; updateTabsUi(tabFans); refreshList() }
        tabToggles.setOnClickListener { currentTab = "TOGGLES"; updateTabsUi(tabToggles); refreshList() }
        tabTemps.setOnClickListener { currentTab = "TEMPS"; updateTabsUi(tabTemps); refreshList() }
        tabCustom.setOnClickListener { currentTab = "CUSTOM"; updateTabsUi(tabCustom); refreshList() }

        btnCreateCustom?.setOnClickListener {
            triggerVibration()
            val newName = inputNewCustomName?.text?.toString()?.trim() ?: ""
            if (newName.isNotEmpty()) {
                val customButtons = getCustomBrandButtons(activeProfileIndex)
                if (!customButtons.contains(newName)) {
                    customButtons.add(newName)
                    saveCustomBrandButtons(activeProfileIndex, customButtons)
                    inputNewCustomName?.setText("")
                    showModernPopup("Added Custom Button: $newName! Tap Learn to clone signal", "✨")
                    refreshList()
                } else {
                    showModernPopup("Button '$newName' already exists", "⚠️")
                }
            }
        }

        refreshList()
        sheet.show()
    }

    fun executeTimerAction(timer: AcTimer) {
        val targetButtonId = when (timer.actionType) {
            "POWER_ON" -> {
                isAcOn = true
                sharedPref.edit().putBoolean("state_ac", isAcOn).apply()
                updateCardStates()
                "AC_POWER_ON"
            }

            "POWER_OFF" -> {
                isAcOn = false
                sharedPref.edit().putBoolean("state_ac", isAcOn).apply()
                updateCardStates()
                "AC_POWER_OFF"
            }

            "SET_TEMP" -> {
                val temp = timer.actionValue.toIntOrNull() ?: 24
                acTemp = temp.coerceIn(16, 32)
                sharedPref.edit().putInt("acTemp", acTemp).apply()
                "AC_TEMP_$acTemp"
            }

            "SET_MODE" -> {
                val modeVal = timer.actionValue.uppercase(Locale.ROOT)
                val modeIdx = listOf("COOL", "HOT", "WARM", "DRY", "AUTO", "FAN").indexOf(modeVal)
                if (modeIdx >= 0) {
                    acModeIndex = modeIdx
                    sharedPref.edit().putInt("state_ac_mode", acModeIndex).apply()
                }
                "AC_MODE_$modeVal"
            }

            "SET_FAN" -> {
                val fanVal = timer.actionValue.uppercase(Locale.ROOT)
                val fanIdx = listOf("1", "2", "3", "4", "AUTO").indexOf(fanVal)
                if (fanIdx >= 0) {
                    acFanSpeedIndex = fanIdx
                    sharedPref.edit().putInt("state_ac_fan", acFanSpeedIndex).apply()
                }
                "AC_FAN_$fanVal"
            }

            "SLEEP" -> {
                isAcSleepOn = timer.actionValue.equals("ON", true)
                sharedPref.edit().putBoolean("state_ac_sleep", isAcSleepOn).apply()
                if (isAcSleepOn) "AC_SLEEP_ON" else "AC_SLEEP_OFF"
            }

            "DISPLAY" -> {
                isAcDisplayOn = timer.actionValue.equals("ON", true)
                sharedPref.edit().putBoolean("state_ac_display", isAcDisplayOn).apply()
                if (isAcDisplayOn) "AC_DISPLAY_ON" else "AC_DISPLAY_OFF"
            }

            "CUSTOM" -> timer.actionValue
            else -> "AC_POWER_ON"
        }

        val fallbackId = when {
            targetButtonId.startsWith("AC_MODE_") -> "AC_MODE"
            targetButtonId.startsWith("AC_FAN_") -> "AC_FAN"
            targetButtonId.startsWith("AC_DISPLAY_") -> "AC_LIGHT"
            targetButtonId.startsWith("AC_SLEEP_") -> "AC_SLEEP"
            else -> null
        }

        handleRemoteClickWithFallback(targetButtonId, fallbackId)
        showModernPopup("⏰ Timer: ${timer.title} (${timer.getActionDescription()})", "⏰")
    }

    private fun handleRemoteClickWithFallback(buttonId: String, fallbackButtonId: String? = null) {
        val category = profileManager.getCategoryFromButtonId(buttonId)
        val saved = profileManager.getSavedCode(category, buttonId)
        if (saved != null || fallbackButtonId == null || isLearning) {
            handleRemoteClick(buttonId)
        } else {
            val fallbackSaved = profileManager.getSavedCode(category, fallbackButtonId)
            if (fallbackSaved != null) {
                handleRemoteClick(fallbackButtonId)
            } else {
                handleRemoteClick(buttonId)
            }
        }
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
        bottomSheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        bottomSheet.behavior.skipCollapsed = true

        val switchLearnMode = view.findViewById<SwitchCompat>(R.id.switch_learn_mode)
        val textUsbStatus = view.findViewById<TextView>(R.id.text_usb_status)
        val btnConnectUsb = view.findViewById<Button>(R.id.btn_connect_usb)
        val hubIdInput = view.findViewById<EditText>(R.id.input_hub_id)
        val hubPassInput = view.findViewById<EditText>(R.id.input_hub_password)
        val ipInput = view.findViewById<EditText>(R.id.input_esp32_ip)
        val btnClose = view.findViewById<View>(R.id.btn_close_settings)
        val btnSave = view.findViewById<Button>(R.id.btn_save_config)
        val btnExport = view.findViewById<Button>(R.id.btn_export_json)
        val btnImport = view.findViewById<Button>(R.id.btn_import_json)
        val btnPaste = view.findViewById<Button>(R.id.btn_paste_json)

        hubIdInput.setText(hubId)
        hubPassInput?.setText(hubPassword)
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
                    try {
                        mqttClient?.disconnect()
                    } catch (e: Exception) {
                    }
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
            val newPass = hubPassInput?.text?.toString()?.trim() ?: ""
            if (newPass.isNotEmpty()) {
                hubPassword = newPass
                sharedPref.edit().putString("hub_password", hubPassword).apply()
            }
            sharedPref.edit().putString("esp32Ip", ipInput.text.toString().trim()).apply()
            showModernPopup("Settings Saved & Security Updated", "💾")
            setupMQTT()
            bottomSheet.dismiss()
        }

        btnExport.setOnClickListener {
            triggerVibration()
            showExportPickerSheet()
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

        btnPaste?.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
            handlePasteJsonBackup()
        }

        bottomSheet.show()
    }

    private fun copyTextToClipboard(label: String, text: String, successMessage: String = "Copied to Clipboard! 📋") {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            triggerVibration()
            showModernPopup(successMessage, "📋")
        } catch (e: Exception) {
            Log.e("Backup", "Clipboard copy error", e)
            showModernPopup("Failed to copy to clipboard", "❌")
        }
    }

    private fun handlePasteJsonBackup() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = clipboard?.primaryClip?.let { clip ->
            if (clip.itemCount > 0) clip.getItemAt(0)?.coerceToText(this)?.toString() else null
        }?.trim()

        showPasteJsonSheet(initialText = clipText)
    }

    private fun showPasteJsonSheet(initialText: String? = null) {
        val bottomSheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.dialog_paste_json, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)
        bottomSheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        bottomSheet.behavior.skipCollapsed = true

        val btnClose = view.findViewById<View>(R.id.btn_close_paste)
        val btnPasteClip = view.findViewById<View>(R.id.btn_paste_from_clip)
        val btnClear = view.findViewById<View>(R.id.btn_clear_paste_text)
        val etContent = view.findViewById<EditText>(R.id.et_paste_json_content)
        val btnSubmit = view.findViewById<Button>(R.id.btn_submit_paste)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel_paste)

        if (!initialText.isNullOrBlank() && (initialText.startsWith("{") || initialText.startsWith("["))) {
            etContent.setText(initialText)
            showModernPopup("Clipboard JSON loaded into box! 📋", "📋")
        }

        btnPasteClip.setOnClickListener {
            triggerVibration()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = clipboard?.primaryClip?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0)?.coerceToText(this)?.toString() else null
            }?.trim()
            if (!text.isNullOrBlank()) {
                etContent.setText(text)
                showModernPopup("Pasted from Clipboard! 📋", "📋")
            } else {
                showModernPopup("Clipboard is empty", "⚠️")
            }
        }

        btnClear.setOnClickListener {
            triggerVibration()
            etContent.setText("")
        }

        btnCancel.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
        }

        btnClose.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
        }

        btnSubmit.setOnClickListener {
            triggerVibration()
            val text = etContent.text.toString().trim()
            if (text.isEmpty()) {
                showModernPopup("Please paste or enter JSON text", "⚠️")
                return@setOnClickListener
            }
            bottomSheet.dismiss()
            processImportedJson(text)
        }

        bottomSheet.show()
    }

    private fun processImportedJson(rawJson: String) {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            showModernPopup("JSON data is empty", "⚠️")
            return
        }
        try {
            val inspection = BackupManager.inspectBackupJson(trimmed)
            when (inspection.type) {
                BackupType.DEVICE_PROFILE -> {
                    showImportSlotPickerSheet(inspection)
                }
                BackupType.CUSTOM_BUTTONS -> {
                    showImportCustomButtonsSheet(inspection)
                }
                BackupType.FULL_BACKUP, BackupType.LEGACY -> {
                    val available = BackupManager.parseAvailableProfiles(trimmed)
                    if (available.size > 1) {
                        showSelectiveImportSheet(trimmed, available)
                    } else {
                        showConfirmFullRestoreDialog(trimmed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Backup", "Error processing imported JSON", e)
            showModernPopup("Invalid or Corrupted JSON format", "❌")
        }
    }

    private fun showSelectiveImportSheet(rawJson: String, items: List<BackupProfileItem>) {
        val bottomSheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.dialog_selective_import, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)
        bottomSheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        bottomSheet.behavior.skipCollapsed = true

        val textCount = view.findViewById<TextView>(R.id.text_selective_found_count)
        val btnSelectAll = view.findViewById<View>(R.id.btn_select_all)
        val btnDeselectAll = view.findViewById<View>(R.id.btn_deselect_all)
        val container = view.findViewById<LinearLayout>(R.id.container_selective_items)
        val btnRestoreSelected = view.findViewById<Button>(R.id.btn_restore_selected)
        val btnRestoreAll = view.findViewById<Button>(R.id.btn_restore_entire_hub)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel_selective)
        val btnClose = view.findViewById<View>(R.id.btn_close_selective)

        textCount.text = "${items.size} REMOTES / PROFILES IN BACKUP"

        fun updateSelectedButtonText() {
            val selCount = items.count { it.isSelected }
            btnRestoreSelected.text = "📥 Restore Selected ($selCount Remotes)"
            btnRestoreSelected.isEnabled = selCount > 0
            btnRestoreSelected.alpha = if (selCount > 0) 1.0f else 0.5f
        }

        fun populateList() {
            container.removeAllViews()
            for (item in items) {
                val row = layoutInflater.inflate(R.layout.item_selective_import_row, container, false)
                val iconView = row.findViewById<TextView>(R.id.text_selective_icon)
                val titleView = row.findViewById<TextView>(R.id.text_selective_title)
                val subView = row.findViewById<TextView>(R.id.text_selective_subtitle)
                val checkBox = row.findViewById<CheckBox>(R.id.check_selective_item)

                iconView.text = item.icon
                titleView.text = item.profileName
                subView.text = if (item.isCustomButtons) "${item.customButtonCount} Custom Buttons" else "${item.codeCount} Learned Codes"
                checkBox.isChecked = item.isSelected

                row.setOnClickListener {
                    triggerVibration()
                    item.isSelected = !item.isSelected
                    checkBox.isChecked = item.isSelected
                    updateSelectedButtonText()
                }
                container.addView(row)
            }
            updateSelectedButtonText()
        }

        btnSelectAll.setOnClickListener {
            triggerVibration()
            items.forEach { it.isSelected = true }
            populateList()
        }

        btnDeselectAll.setOnClickListener {
            triggerVibration()
            items.forEach { it.isSelected = false }
            populateList()
        }

        btnRestoreSelected.setOnClickListener {
            triggerVibration()
            val restored = BackupManager.restoreSelectedProfiles(items, sharedPref, profileManager, customIrManager)
            bottomSheet.dismiss()
            updateCardStates()
            showModernPopup("$restored Devices Restored & Replaced! 📥", "✅")
        }

        btnRestoreAll.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
            showConfirmFullRestoreDialog(rawJson)
        }

        btnCancel.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
        }

        btnClose.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
        }

        populateList()
        bottomSheet.show()
    }

    private fun launchExportFilePicker(fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        exportJsonLauncher.launch(intent)
    }

    private fun getCodeCountForProfile(category: DeviceCategory, profileIndex: Int): Int {
        val prefix = "code_${category.id}_${profileIndex}_"
        var count = 0
        for ((key, _) in sharedPref.all) {
            if (key.startsWith(prefix)) count++
        }
        if (profileIndex == 0 && count == 0) {
            for ((key, _) in sharedPref.all) {
                val isLegacy = when (category) {
                    DeviceCategory.AC -> key.startsWith("AC_")
                    DeviceCategory.TV -> key.startsWith("TV_")
                    DeviceCategory.LIGHT -> key.startsWith("LIGHT_")
                    DeviceCategory.FAN -> key.startsWith("FAN_")
                }
                if (isLegacy) count++
            }
        }
        return count
    }

    private fun showExportPickerSheet() {
        val bottomSheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.dialog_export_picker, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

        val btnClose = view.findViewById<View>(R.id.btn_close_export)
        val cardFull = view.findViewById<View>(R.id.card_export_full)
        val btnExportFullCopy = view.findViewById<View>(R.id.btn_export_full_copy)
        val btnExportFullFile = view.findViewById<View>(R.id.btn_export_full_file)

        val cardCustom = view.findViewById<View>(R.id.card_export_custom_ir)
        val textCustomCount = view.findViewById<TextView>(R.id.text_export_custom_count)
        val btnExportCustomCopy = view.findViewById<View>(R.id.btn_export_custom_copy)
        val btnExportCustomFile = view.findViewById<View>(R.id.btn_export_custom_file)

        val containerAc = view.findViewById<LinearLayout>(R.id.container_export_ac)
        val containerTv = view.findViewById<LinearLayout>(R.id.container_export_tv)
        val containerLight = view.findViewById<LinearLayout>(R.id.container_export_light)
        val containerFan = view.findViewById<LinearLayout>(R.id.container_export_fan)

        // Custom Buttons Count
        val customCount = customIrManager.getButtonCount()
        textCustomCount.text = "$customCount Custom Buttons & Remote Codes"

        // 1. Full Database
        fun exportFullFile() {
            triggerVibration()
            pendingExportJson = BackupManager.createFullBackupJson(sharedPref, profileManager, customIrManager)
            pendingExportFileName = "ir_hub_full_backup.json"
            bottomSheet.dismiss()
            launchExportFilePicker(pendingExportFileName)
        }

        fun copyFullJson() {
            triggerVibration()
            val json = BackupManager.createFullBackupJson(sharedPref, profileManager, customIrManager)
            copyTextToClipboard("Full Hub Backup", json, "Full Hub Backup Copied! 📋")
        }

        cardFull.setOnClickListener { exportFullFile() }
        btnExportFullFile?.setOnClickListener { exportFullFile() }
        btnExportFullCopy?.setOnClickListener { copyFullJson() }

        // 2. Custom Buttons
        fun exportCustomFile() {
            triggerVibration()
            if (customCount == 0) {
                showModernPopup("No Custom Buttons to Export", "ℹ️")
                return
            }
            pendingExportJson = BackupManager.createCustomButtonsJson(customIrManager)
            pendingExportFileName = "custom_ir_buttons_backup.json"
            bottomSheet.dismiss()
            launchExportFilePicker(pendingExportFileName)
        }

        fun copyCustomJson() {
            triggerVibration()
            if (customCount == 0) {
                showModernPopup("No Custom Buttons to Copy", "ℹ️")
                return
            }
            val json = BackupManager.createCustomButtonsJson(customIrManager)
            copyTextToClipboard("Custom Buttons", json, "Custom Buttons Copied! 📋")
        }

        cardCustom.setOnClickListener { exportCustomFile() }
        btnExportCustomFile?.setOnClickListener { exportCustomFile() }
        btnExportCustomCopy?.setOnClickListener { copyCustomJson() }

        // Populate device categories helper
        fun populateCategoryRows(
            category: DeviceCategory,
            container: LinearLayout,
            icon: String,
            fileSuffix: String
        ) {
            container.removeAllViews()
            for (pIndex in 0 until ProfileManager.MAX_PROFILES) {
                val name = profileManager.getProfileName(category, pIndex)
                val codeCount = getCodeCountForProfile(category, pIndex)
                val row = layoutInflater.inflate(R.layout.item_export_profile_row, container, false)
                row.findViewById<TextView>(R.id.text_row_icon).text = icon
                row.findViewById<TextView>(R.id.text_row_title).text = "${category.defaultPrefix} ${pIndex + 1}: $name"
                row.findViewById<TextView>(R.id.text_row_subtitle).text = "$codeCount Learned Codes"

                val btnRowCopy = row.findViewById<View>(R.id.btn_row_copy)
                val btnRowAction = row.findViewById<View>(R.id.btn_row_action)

                fun doExport() {
                    triggerVibration()
                    val safeName = name.lowercase().replace("[^a-z0-9]".toRegex(), "_").trim('_')
                    pendingExportJson = BackupManager.createDeviceProfileJson(category, pIndex, sharedPref, profileManager)
                    pendingExportFileName = "${safeName}_${fileSuffix}_backup.json"
                    bottomSheet.dismiss()
                    launchExportFilePicker(pendingExportFileName)
                }

                fun doCopy() {
                    triggerVibration()
                    val json = BackupManager.createDeviceProfileJson(category, pIndex, sharedPref, profileManager)
                    copyTextToClipboard("${category.defaultPrefix} Profile", json, "${category.defaultPrefix} ${pIndex + 1} Copied! 📋")
                }

                btnRowCopy?.setOnClickListener { doCopy() }
                btnRowAction?.setOnClickListener { doExport() }
                row.setOnClickListener { doExport() }
                container.addView(row)
            }
        }

        populateCategoryRows(DeviceCategory.AC, containerAc, "❄️", "ac")
        populateCategoryRows(DeviceCategory.TV, containerTv, "📺", "tv")
        populateCategoryRows(DeviceCategory.LIGHT, containerLight, "💡", "light")
        populateCategoryRows(DeviceCategory.FAN, containerFan, "🌀", "fan")

        btnClose.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showImportSlotPickerSheet(inspection: InspectionResult) {
        val category = inspection.category ?: DeviceCategory.AC
        val bottomSheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.dialog_import_profile_slot, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

        val textTitle = view.findViewById<TextView>(R.id.text_import_title)
        val iconView = view.findViewById<TextView>(R.id.text_import_source_icon)
        val nameView = view.findViewById<TextView>(R.id.text_import_source_name)
        val codesView = view.findViewById<TextView>(R.id.text_import_source_codes)
        val containerSlots = view.findViewById<LinearLayout>(R.id.container_import_slots)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel_import_slot)
        val btnClose = view.findViewById<View>(R.id.btn_close_import_slot)

        val catIcon = when (category) {
            DeviceCategory.AC -> "❄️"
            DeviceCategory.TV -> "📺"
            DeviceCategory.LIGHT -> "💡"
            DeviceCategory.FAN -> "🌀"
        }
        textTitle.text = "Import ${category.defaultPrefix} Profile"
        iconView.text = catIcon
        nameView.text = inspection.profileName.ifBlank { "${category.defaultPrefix} Profile" }
        codesView.text = "${inspection.codeCount} Learned Codes Ready to Import"

        containerSlots.removeAllViews()
        for (i in 0 until ProfileManager.MAX_PROFILES) {
            val currentName = profileManager.getProfileName(category, i)
            val currentCodes = getCodeCountForProfile(category, i)
            val slotRow = layoutInflater.inflate(R.layout.item_import_slot_row, containerSlots, false)
            slotRow.findViewById<TextView>(R.id.text_slot_number).text = (i + 1).toString()
            slotRow.findViewById<TextView>(R.id.text_slot_title).text = "${category.defaultPrefix} ${i + 1}"
            val statusText = if (currentCodes > 0) "Current: $currentName • $currentCodes Codes • Tap to Replace" else "Slot Empty • Tap to Install"
            slotRow.findViewById<TextView>(R.id.text_slot_status).text = statusText

            slotRow.setOnClickListener {
                triggerVibration()
                val success = BackupManager.restoreDeviceProfile(inspection.rawJson, i, sharedPref, profileManager)
                bottomSheet.dismiss()
                if (success) {
                    updateCardStates()
                    val targetName = profileManager.getProfileName(category, i)
                    showModernPopup("${category.defaultPrefix} ${i + 1} ($targetName) Replaced & Updated! 📥", "✅")
                } else {
                    showModernPopup("Failed to import profile", "❌")
                }
            }
            containerSlots.addView(slotRow)
        }

        btnCancel.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
        }
        btnClose.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showImportCustomButtonsSheet(inspection: InspectionResult) {
        val bottomSheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.dialog_import_custom_buttons, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

        val textCount = view.findViewById<TextView>(R.id.text_import_custom_count)
        val btnMerge = view.findViewById<Button>(R.id.btn_import_custom_merge)
        val btnReplace = view.findViewById<Button>(R.id.btn_import_custom_replace)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel_import_custom)
        val btnClose = view.findViewById<View>(R.id.btn_close_import_custom)

        textCount.text = "${inspection.customButtonCount} Custom Buttons in Backup File"

        btnMerge.setOnClickListener {
            triggerVibration()
            val count = BackupManager.restoreCustomButtons(inspection.rawJson, customIrManager, replaceAll = false)
            bottomSheet.dismiss()
            updateCardStates()
            showModernPopup("$count Custom Buttons Merged! 📥", "✨")
        }

        btnReplace.setOnClickListener {
            triggerVibration()
            val count = BackupManager.restoreCustomButtons(inspection.rawJson, customIrManager, replaceAll = true)
            bottomSheet.dismiss()
            updateCardStates()
            showModernPopup("$count Custom Buttons Restored! 📥", "✨")
        }

        btnCancel.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
        }
        btnClose.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showConfirmFullRestoreDialog(rawJson: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("📥 Restore Full Hub Backup")
            .setMessage("This will restore all device profiles (AC, TV, Light, Fan), learned IR codes, custom buttons, and settings from this backup.\n\nDo you want to proceed?")
            .setPositiveButton("Restore All") { _, _ ->
                triggerVibration()
                val success = BackupManager.restoreFullBackup(rawJson, sharedPref, profileManager, customIrManager)
                updateCardStates()
                if (success) {
                    showModernPopup("Full Hub Backup Restored! 📥", "✅")
                } else {
                    showModernPopup("Restore Failed", "❌")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========================================================
    // MQTT & TRANSMISSION & CAPTURE
    // ========================================================
    private fun resolveBrokerUrl(originalBroker: String): String {
        return try {
            val uri = java.net.URI(originalBroker)
            val host = uri.host ?: return originalBroker
            val port = if (uri.port > 0) uri.port else 1883
            val scheme = uri.scheme ?: "tcp"

            // Force IPv4 lookup to bypass broken IPv6 mobile routes
            val ipv4 = java.net.InetAddress.getAllByName(host)
                .firstOrNull { it is java.net.Inet4Address }
                ?.hostAddress

            if (ipv4 != null) {
                "$scheme://$ipv4:$port"
            } else {
                originalBroker
            }
        } catch (e: Exception) {
            Log.w("MQTT", "Failed to resolve IPv4, using original", e)
            originalBroker
        }
    }

    private fun setupMQTT(onComplete: ((Boolean, String?) -> Unit)? = null) {
        bgExecutor.execute {
            try {
                if (mqttClient != null) {
                    try {
                        if (mqttClient!!.isConnected) mqttClient?.disconnect()
                    } catch (e: Exception) {
                    }
                    try {
                        mqttClient?.close()
                    } catch (e: Exception) {
                    }
                    mqttClient = null
                }

                val resolvedBroker = resolveBrokerUrl(broker)
                Log.d("MQTT", "Connecting to resolved broker: $resolvedBroker (original: $broker)")

                val currentClientId = "AndroidHub_" + System.currentTimeMillis() + "_" + (1000..9999).random()
                val persistence = MemoryPersistence()
                val client = MqttClient(resolvedBroker, currentClientId, persistence)
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
                        try {
                            client.subscribe(topicRx, 0)
                            // Send a heartbeat ping to ask the ESP32 to announce itself
                            val pingMsg = MqttMessage("{\"cmd\":\"PING\"}".toByteArray())
                            client.publish(topicTx, pingMsg)
                        } catch (e: Exception) {
                            Log.w("MQTT", "Subscribe/Ping error", e)
                        }
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
                    e.message?.contains(
                        "Unable to resolve host",
                        ignoreCase = true
                    ) == true -> "No Internet (Cannot resolve broker)"

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
        if (isLearning && customIrLearningCallback != null && rawMessage.startsWith("RAW:")) {
            val parts = rawMessage.split(":")
            if (parts.size >= 3) {
                val len = parts[1]
                val values = parts[2]

                val jsonSignal = JSONObject().apply {
                    put("type", "raw")
                    put("len", len.toInt())
                    put("values", values)
                }

                val cb = customIrLearningCallback
                isLearning = false
                learningTargetId = null
                customIrLearningCallback = null

                triggerVibration()
                runOnUiThread {
                    cb?.invoke(jsonSignal.toString())
                    showModernPopup("Custom IR Signal Learned! ✅", "🎯")
                }
                return
            }
        }

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
                val activeProfileName =
                    profileManager.getProfileName(category, profileManager.getActiveProfileIndex(category))
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
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", true)
        val hasOwner = sharedPref.getString("owner_username", null) != null
        if (hasOwner && !isLoggedIn) {
            triggerVibration()
            showModernPopup("🔒 Remote is Locked! Tap Account icon to log in", "⛔")
            showUserAccountSecurityDialog()
            return
        }

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
                    val jsonObj = JSONObject(savedSignal).apply {
                        put("auth", hubPassword)
                    }
                    val message = MqttMessage(jsonObj.toString().toByteArray())
                    mqttClient?.publish(topicTx, message)
                    Log.d("MQTT", "Dispatched (Authorized): ${jsonObj.toString()}")
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
            channels.isNotEmpty() -> showModernPopup(
                "Transmitting $readableName (${channels.joinToString(" + ")})",
                "⚡"
            )

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
                            val targetTy =
                                if (dy < -threshold) -80f * resources.displayMetrics.density else v.translationY

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
                    hardwareStatusText.text = "$boardName + ESP32"
                    hardwareStatusText.setTextColor(Color.parseColor("#0369a1"))
                    hardwareStatusPill.setBackgroundResource(R.drawable.bg_hardware_pill_dual)
                }

                isUsb -> {
                    hardwareStatusDot.text = "🔌"
                    hardwareStatusText.text = "$boardName (USB)"
                    hardwareStatusText.setTextColor(Color.parseColor("#0284c7"))
                    hardwareStatusPill.setBackgroundResource(R.drawable.bg_hardware_pill_dual)
                }

                isWifi -> {
                    hardwareStatusDot.text = "🌐"
                    hardwareStatusText.text = "ESP32 (Wi-Fi Online)"
                    hardwareStatusText.setTextColor(Color.parseColor("#15803d"))
                    hardwareStatusPill.setBackgroundResource(R.drawable.bg_hardware_pill_online)
                }

                mqttClient?.isConnected == true -> {
                    hardwareStatusDot.text = "⏳"
                    hardwareStatusText.text = "ESP32 (Wi-Fi Waiting...)"
                    hardwareStatusText.setTextColor(Color.parseColor("#b45309"))
                    hardwareStatusPill.setBackgroundResource(R.drawable.bg_hardware_pill_waiting)
                }

                else -> {
                    hardwareStatusDot.text = "🔴"
                    hardwareStatusText.text = "Hardware Offline"
                    hardwareStatusText.setTextColor(Color.parseColor("#64748b"))
                    hardwareStatusPill.setBackgroundResource(R.drawable.bg_hardware_pill_offline)
                }
            }
            val padH = (14 * resources.displayMetrics.density).toInt()
            hardwareStatusPill.setPadding(padH, 0, padH, 0)
        }
    }

    private fun showHardwareStatusDialog() {
        triggerVibration()
        val bottomSheet = createCleanBottomSheet()
        val view = layoutInflater.inflate(R.layout.dialog_hardware_diagnostics, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

        val badgeWifi = view.findViewById<TextView>(R.id.badge_diag_wifi)
        val textWifiTopic = view.findViewById<TextView>(R.id.text_diag_wifi_topic)
        val btnNetworkAction = view.findViewById<Button>(R.id.btn_diag_network_action)

        val badgeUsb = view.findViewById<TextView>(R.id.badge_diag_usb)
        val textUsbBoard = view.findViewById<TextView>(R.id.text_diag_usb_board)
        val btnUsbRescan = view.findViewById<Button>(R.id.btn_diag_usb_rescan)

        val badgeInternalIr = view.findViewById<TextView>(R.id.badge_diag_internal_ir)
        val textInternalIr = view.findViewById<TextView>(R.id.text_diag_internal_ir)
        val btnDone = view.findViewById<Button>(R.id.btn_diag_done)

        // 1. Wi-Fi Status
        textWifiTopic?.text = "Topic: universalo-hub/$hubId/rx"
        val isWifi = isEsp32MqttOnline
        when {
            isWifi -> {
                badgeWifi?.text = "🟢 Online"
                badgeWifi?.setTextColor(Color.parseColor("#16A34A"))
                badgeWifi?.setBackgroundResource(R.drawable.bg_sheet_btn_green)
                btnNetworkAction?.text = "Reconnect Network"
            }

            mqttClient?.isConnected == true -> {
                badgeWifi?.text = "⏳ Waiting for ESP32"
                badgeWifi?.setTextColor(Color.parseColor("#D97706"))
                badgeWifi?.setBackgroundResource(R.drawable.bg_hardware_pill_waiting)
                btnNetworkAction?.text = "Reconnect Network"
            }

            else -> {
                badgeWifi?.text = "🔴 Offline"
                badgeWifi?.setTextColor(Color.parseColor("#DC2626"))
                badgeWifi?.setBackgroundResource(R.drawable.bg_sheet_btn_red)
                btnNetworkAction?.text = "Connect via Network"
            }
        }

        btnNetworkAction?.setOnClickListener {
            triggerVibration()
            showModernPopup("Connecting to ESP32 via Network...", "🌐")
            setupMQTT { success, errMsg ->
                runOnUiThread {
                    updateHardwareStatusUI()
                    if (success) {
                        showModernPopup("Connected to Network Broker! 🌐", "✅")
                        badgeWifi?.text = "🟢 Online"
                        badgeWifi?.setTextColor(Color.parseColor("#16A34A"))
                        badgeWifi?.setBackgroundResource(R.drawable.bg_sheet_btn_green)
                    } else {
                        showModernPopup("Network Failed: $errMsg", "❌")
                    }
                }
            }
        }

        // 2. USB Status
        val isUsb = usbSerialManager.isConnected
        val boardName = usbSerialManager.connectedDeviceName
        if (isUsb) {
            badgeUsb?.text = "🟢 Connected"
            badgeUsb?.setTextColor(Color.parseColor("#16A34A"))
            badgeUsb?.setBackgroundResource(R.drawable.bg_sheet_btn_green)
            textUsbBoard?.text = "Board: $boardName (Active)"
        } else {
            badgeUsb?.text = "🔴 Disconnected"
            badgeUsb?.setTextColor(Color.parseColor("#64748B"))
            badgeUsb?.setBackgroundResource(R.drawable.bg_sheet_btn)
            textUsbBoard?.text = "Board: None Detected"
        }

        btnUsbRescan?.setOnClickListener {
            triggerVibration()
            usbSerialManager.connect(userInitiated = true)
            updateHardwareStatusUI()
            val reconnected = usbSerialManager.isConnected
            if (reconnected) {
                badgeUsb?.text = "🟢 Connected"
                badgeUsb?.setTextColor(Color.parseColor("#16A34A"))
                badgeUsb?.setBackgroundResource(R.drawable.bg_sheet_btn_green)
                textUsbBoard?.text = "Board: ${usbSerialManager.connectedDeviceName} (Active)"
            }
        }

        // 3. Internal Phone IR
        if (hasInternalIr) {
            badgeInternalIr?.text = "✅ Active"
            badgeInternalIr?.setTextColor(Color.parseColor("#16A34A"))
            badgeInternalIr?.setBackgroundResource(R.drawable.bg_sheet_btn_green)
            textInternalIr?.text = "Consumer IR Transmitter Ready"
        } else {
            badgeInternalIr?.text = "❌ N/A"
            badgeInternalIr?.setTextColor(Color.parseColor("#64748B"))
            badgeInternalIr?.setBackgroundResource(R.drawable.bg_sheet_btn)
            textInternalIr?.text = "Not Supported on this Device"
        }

        btnDone?.setOnClickListener {
            triggerVibration()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showUserAccountSecurityDialog() {
        val ownerName = sharedPref.getString("owner_username", null)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", true)

        if (ownerName != null && isLoggedIn) {
            // 1. LOGGED IN OWNER PROFILE BOTTOM SHEET
            val bottomSheet = createCleanBottomSheet()
            val view = layoutInflater.inflate(R.layout.dialog_user_profile, null)
            bottomSheet.setContentView(view)
            (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
            (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

            val textDisplayName = view.findViewById<TextView>(R.id.text_owner_display_name)
            val textHubKey = view.findViewById<TextView>(R.id.text_hub_key_masked)
            val btnLock = view.findViewById<Button>(R.id.btn_lock_remote)
            val btnClose = view.findViewById<Button>(R.id.btn_close_profile)

            textDisplayName?.text = ownerName
            var isRevealed = false
            textHubKey?.text = "••••••••••••"
            textHubKey?.setOnClickListener {
                isRevealed = !isRevealed
                textHubKey.text = if (isRevealed) hubPassword else "••••••••••••"
            }

            btnLock?.setOnClickListener {
                triggerVibration()
                sharedPref.edit().putBoolean("is_logged_in", false).apply()
                bottomSheet.dismiss()
                showModernPopup("Remote Locked! Tap Account to unlock", "🔒")
            }

            btnClose?.setOnClickListener {
                triggerVibration()
                bottomSheet.dismiss()
            }

            bottomSheet.show()
        } else {
            // 2. SIGN UP OR UNLOCK BOTTOM SHEET
            val bottomSheet = createCleanBottomSheet()
            val view = layoutInflater.inflate(R.layout.dialog_user_auth, null)
            bottomSheet.setContentView(view)
            (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
            (view.parent as? View)?.background = ColorDrawable(Color.TRANSPARENT)

            val titleView = view.findViewById<TextView>(R.id.text_auth_title)
            val subtitleView = view.findViewById<TextView>(R.id.text_auth_subtitle)
            val labelUser = view.findViewById<TextView>(R.id.label_auth_username)
            val inputUser = view.findViewById<EditText>(R.id.input_auth_username)
            val inputPass = view.findViewById<EditText>(R.id.input_auth_password)
            val labelHubKey = view.findViewById<TextView>(R.id.label_auth_hub_key)
            val inputHubKey = view.findViewById<EditText>(R.id.input_auth_hub_key)
            val btnSubmit = view.findViewById<Button>(R.id.btn_auth_submit)
            val btnCancel = view.findViewById<Button>(R.id.btn_auth_cancel)

            if (ownerName == null) {
                // Sign Up Mode
                titleView?.text = "Create Owner Account"
                subtitleView?.text = "Lock your device and cloned codes so only you can control appliances."
                labelUser?.visibility = View.VISIBLE
                inputUser?.visibility = View.VISIBLE
                inputUser?.setText("Kaushal")
                labelHubKey?.visibility = View.VISIBLE
                inputHubKey?.visibility = View.VISIBLE
                inputHubKey?.setText(hubPassword)
                btnSubmit?.text = "Create Account & Protect"

                btnSubmit?.setOnClickListener {
                    triggerVibration()
                    val user = inputUser?.text?.toString()?.trim() ?: ""
                    val pass = inputPass?.text?.toString()?.trim() ?: ""
                    val hubKey = inputHubKey?.text?.toString()?.trim() ?: ""
                    if (user.isNotEmpty() && pass.isNotEmpty()) {
                        sharedPref.edit()
                            .putString("owner_username", user)
                            .putString("owner_password", pass)
                            .putString("hub_password", if (hubKey.isNotEmpty()) hubKey else hubPassword)
                            .putBoolean("is_logged_in", true)
                            .apply()
                        if (hubKey.isNotEmpty()) hubPassword = hubKey
                        bottomSheet.dismiss()
                        showModernPopup("Owner Account Created! Protected 🔒", "✅")
                    } else {
                        showModernPopup("Please enter username and password", "⚠️")
                    }
                }
            } else {
                // Login / Unlock Mode
                titleView?.text = "Unlock Remote: $ownerName"
                subtitleView?.text = "Enter your security password to control appliances."
                labelUser?.visibility = View.GONE
                inputUser?.visibility = View.GONE
                labelHubKey?.visibility = View.GONE
                inputHubKey?.visibility = View.GONE
                btnSubmit?.text = "Unlock Remote 🔓"

                btnSubmit?.setOnClickListener {
                    triggerVibration()
                    val enteredPass = inputPass?.text?.toString()?.trim() ?: ""
                    val savedPass = sharedPref.getString("owner_password", "")
                    if (enteredPass == savedPass) {
                        sharedPref.edit().putBoolean("is_logged_in", true).apply()
                        bottomSheet.dismiss()
                        showModernPopup("Welcome back, $ownerName! Unlocked 🔓", "✅")
                    } else {
                        showModernPopup("Incorrect password! Access denied ⛔", "❌")
                    }
                }
            }

            btnCancel?.setOnClickListener {
                triggerVibration()
                bottomSheet.dismiss()
            }

            bottomSheet.show()
        }
    }

    // ========================================================
    // UNIVERSAL CUSTOM IR REMOTE CONTROLLER
    // ========================================================
    fun transmitCustomSignal(codeJson: String?, readableName: String) {
        if (codeJson.isNullOrEmpty()) {
            triggerVibration()
            showModernPopup("No IR signal stored for $readableName! Learn or enter code", "⚠️")
            return
        }

        triggerVibration()
        var sentViaInternalIr = false
        if (hasInternalIr) {
            try {
                val jsonObj = JSONObject(codeJson)
                val type = jsonObj.optString("type", "raw")
                val values = jsonObj.optString("values", "")
                if (type == "raw" && values.isNotEmpty()) {
                    val pattern = values.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
                    if (pattern.isNotEmpty()) {
                        consumerIrManager?.transmit(38000, pattern)
                        sentViaInternalIr = true
                        Log.d("ConsumerIR", "Transmitted custom IR (${pattern.size} pulses)")
                    }
                }
            } catch (e: Exception) {
                Log.e("ConsumerIR", "Custom IR transmit error", e)
            }
        }

        var sentViaUsb = false
        if (usbSerialManager.isConnected) {
            try {
                val jsonObj = JSONObject(codeJson)
                val type = jsonObj.optString("type", "raw")
                val len = jsonObj.optInt("len", 0)
                val values = jsonObj.optString("values", "")

                val cmd = if (type == "raw" && len > 0 && values.isNotEmpty()) {
                    "SEND_RAW:$len:$values\n"
                } else {
                    "$codeJson\n"
                }
                sentViaUsb = usbSerialManager.sendCommand(cmd)
            } catch (e: Exception) {
                sentViaUsb = usbSerialManager.sendCommand("SEND_RAW:$codeJson\n")
            }
        }

        var sentViaMqtt = false
        if (mqttClient != null && mqttClient!!.isConnected) {
            bgExecutor.execute {
                try {
                    val jsonObj = JSONObject(codeJson).apply {
                        put("auth", hubPassword)
                    }
                    val message = MqttMessage(jsonObj.toString().toByteArray())
                    mqttClient?.publish(topicTx, message)
                    Log.d("MQTT", "Dispatched Custom IR: ${jsonObj.toString()}")
                } catch (e: Exception) {
                    Log.e("MQTT", "Failed to send custom MQTT message", e)
                }
            }
            sentViaMqtt = true
        }

        val channels = mutableListOf<String>()
        if (sentViaInternalIr) channels.add("Phone IR")
        if (sentViaUsb) channels.add("USB")
        if (sentViaMqtt) channels.add("WiFi Hub")

        when {
            channels.isNotEmpty() -> showModernPopup(
                "Transmitting $readableName (${channels.joinToString(" + ")})",
                "⚡"
            )

            else -> showModernPopup("Hub Offline! Connect USB or WiFi", "🔴")
        }
    }

    private fun showCustomIrSheet() {
        val sheetView = layoutInflater.inflate(R.layout.dialog_custom_ir_sheet, null)
        val dialog = BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme)
        dialog.setContentView(sheetView)

        val btnClose = sheetView.findViewById<View>(R.id.btn_close_custom_sheet)
        val btnHeaderAdd = sheetView.findViewById<View>(R.id.btn_header_add_custom)
        val btnBottomAdd = sheetView.findViewById<View>(R.id.btn_bottom_add_custom)
        val btnEmptyAdd = sheetView.findViewById<View>(R.id.btn_empty_add_custom)
        val layoutEmpty = sheetView.findViewById<View>(R.id.layout_custom_empty)
        val container = sheetView.findViewById<LinearLayout>(R.id.container_custom_buttons)
        val tvSubtitle = sheetView.findViewById<TextView>(R.id.tv_custom_ir_subtitle)

        btnClose.setOnClickListener {
            triggerVibration()
            dialog.dismiss()
        }

        fun populateList() {
            container.removeAllViews()
            val buttons = customIrManager.getAllButtons()
            updateCardStates()

            if (buttons.isEmpty()) {
                layoutEmpty.visibility = View.VISIBLE
                btnBottomAdd.visibility = View.GONE
                tvSubtitle.text = "Universal Controller • 0 Custom Sets"
            } else {
                layoutEmpty.visibility = View.GONE
                btnBottomAdd.visibility = View.VISIBLE
                tvSubtitle.text =
                    "Universal Controller • ${buttons.size} Custom Set${if (buttons.size > 1) "s" else ""}"

                for (btn in buttons) {
                    val itemView = layoutInflater.inflate(R.layout.item_custom_ir_button, container, false)
                    val tvIcon = itemView.findViewById<TextView>(R.id.tv_item_icon)
                    val tvName = itemView.findViewById<TextView>(R.id.tv_item_name)
                    val tvStatus = itemView.findViewById<TextView>(R.id.tv_item_status)
                    val tvTimerBadge = itemView.findViewById<TextView>(R.id.tv_item_timer_badge)
                    val btnBlast = itemView.findViewById<View>(R.id.btn_item_blast)
                    val btnEdit = itemView.findViewById<View>(R.id.btn_item_edit)
                    val btnDelete = itemView.findViewById<View>(R.id.btn_item_delete)

                    tvIcon.text = btn.icon
                    tvName.text = btn.name

                    if (!btn.codeJson.isNullOrEmpty()) {
                        val pulses = try {
                            JSONObject(btn.codeJson!!).optInt("len", 0)
                        } catch (_: Exception) {
                            0
                        }
                        if (pulses > 0) {
                            tvStatus.text = "● Ready ($pulses pulses)"
                        } else {
                            tvStatus.text = "● Ready to transmit"
                        }
                        tvStatus.setTextColor(Color.parseColor("#166534"))
                    } else {
                        tvStatus.text = "⚠️ Tap to learn code"
                        tvStatus.setTextColor(Color.parseColor("#B45309"))
                    }

                    if (btn.hasTimer) {
                        tvTimerBadge.visibility = View.VISIBLE
                        tvTimerBadge.text = String.format(
                            Locale.US,
                            "⏰ %02d:%02d %s",
                            btn.timeHour,
                            btn.timeMinute,
                            if (btn.isAm) "AM" else "PM"
                        )
                    } else {
                        tvTimerBadge.visibility = View.GONE
                    }

                    btnBlast.setOnClickListener {
                        transmitCustomSignal(btn.codeJson, btn.name)
                    }

                    val openEditor = View.OnClickListener {
                        triggerVibration()
                        showCustomIrEditorDialog(btn) {
                            populateList()
                        }
                    }
                    itemView.setOnClickListener(openEditor)
                    btnEdit.setOnClickListener(openEditor)

                    btnDelete.setOnClickListener {
                        triggerVibration()
                        customIrManager.deleteButton(btn.id)
                        showModernPopup("Deleted '${btn.name}'", "🗑")
                        populateList()
                    }

                    container.addView(itemView)
                }
            }
        }

        val onAddClick = View.OnClickListener {
            triggerVibration()
            showCustomIrEditorDialog(null) {
                populateList()
            }
        }
        btnHeaderAdd.setOnClickListener(onAddClick)
        btnBottomAdd.setOnClickListener(onAddClick)
        btnEmptyAdd.setOnClickListener(onAddClick)

        populateList()
        dialog.show()
    }

    private fun showCustomIrEditorDialog(buttonToEdit: CustomIrButton?, onSaved: (() -> Unit)? = null) {
        val sheetView = layoutInflater.inflate(R.layout.dialog_edit_custom_ir, null)
        val dialog = BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme)
        dialog.setContentView(sheetView)

        val tvDialogTitle = sheetView.findViewById<TextView>(R.id.tv_edit_custom_dialog_title)
        val btnClose = sheetView.findViewById<View>(R.id.btn_edit_custom_close)
        val etName = sheetView.findViewById<EditText>(R.id.et_custom_btn_name)
        val layoutIconChips = sheetView.findViewById<LinearLayout>(R.id.layout_icon_chips)
        val tvStatusDot = sheetView.findViewById<TextView>(R.id.tv_learn_status_dot)
        val tvStatusText = sheetView.findViewById<TextView>(R.id.tv_learn_status_text)
        val btnLearn = sheetView.findViewById<TextView>(R.id.btn_custom_learn_code)
        val btnTest = sheetView.findViewById<View>(R.id.btn_custom_test_code)
        val tvToggleManual = sheetView.findViewById<TextView>(R.id.tv_toggle_manual_code)
        val etManualCode = sheetView.findViewById<EditText>(R.id.et_manual_code)
        val switchTimer = sheetView.findViewById<SwitchCompat>(R.id.switch_custom_timer_enable)
        val layoutTimerDetails = sheetView.findViewById<View>(R.id.layout_custom_timer_details)
        val pickerHour = sheetView.findViewById<NumberPicker>(R.id.picker_custom_hour)
        val pickerMinute = sheetView.findViewById<NumberPicker>(R.id.picker_custom_minute)
        val pickerAmPm = sheetView.findViewById<NumberPicker>(R.id.picker_custom_ampm)
        val btnCancel = sheetView.findViewById<View>(R.id.btn_custom_cancel)
        val btnSave = sheetView.findViewById<View>(R.id.btn_custom_save)

        tvDialogTitle.text = if (buttonToEdit == null) "Add Custom Button" else "Edit Custom Button"
        etName.setText(buttonToEdit?.name ?: "")

        var selectedIcon = buttonToEdit?.icon ?: "✨"
        var currentCodeJson: String? = buttonToEdit?.codeJson

        val iconOptions = listOf(
            Pair("✨", "Universal"),
            Pair("📽️", "Projector"),
            Pair("🔊", "Audio"),
            Pair("💡", "Light"),
            Pair("📺", "Display"),
            Pair("⚡", "Power"),
            Pair("❄️", "Cooling"),
            Pair("🎮", "Media"),
            Pair("⚙️", "Other")
        )

        fun updateIconChipsUI() {
            layoutIconChips.removeAllViews()
            for ((icon, label) in iconOptions) {
                val isSelected = icon == selectedIcon
                val chip = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                    setBackgroundResource(if (isSelected) R.drawable.bg_samsung_chip_selected else R.drawable.bg_samsung_chip_unselected)
                    setPadding(32, 18, 32, 18)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 16, 0)
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        triggerVibration()
                        selectedIcon = icon
                        updateIconChipsUI()
                    }
                }
                val tv = TextView(this).apply {
                    text = "$icon $label"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isSelected) Color.parseColor("#2563EB") else Color.parseColor("#0F172A"))
                }
                chip.addView(tv)
                layoutIconChips.addView(chip)
            }
        }
        updateIconChipsUI()

        fun updateCodeStatusUI() {
            btnLearn.setTextColor(Color.parseColor("#2563EB"))
            btnLearn.setBackgroundResource(R.drawable.bg_sheet_btn)
            if (!currentCodeJson.isNullOrEmpty()) {
                tvStatusDot.text = "🟢"
                val pulses = try {
                    JSONObject(currentCodeJson!!).optInt("len", 0)
                } catch (_: Exception) {
                    0
                }
                tvStatusText.text =
                    if (pulses > 0) "Signal Stored ($pulses pulses) • Ready" else "Signal Stored • Ready"
                tvStatusText.setTextColor(Color.parseColor("#166534"))
                btnTest.isEnabled = true
                btnTest.alpha = 1.0f
                btnLearn.text = "🎯 Re-Learn Signal"
            } else {
                tvStatusDot.text = "⚪"
                tvStatusText.text = "No code stored yet"
                tvStatusText.setTextColor(Color.parseColor("#64748B"))
                btnTest.isEnabled = false
                btnTest.alpha = 0.5f
                btnLearn.text = "🎯 Learn from Remote"
            }
        }
        updateCodeStatusUI()

        btnLearn.setOnClickListener {
            triggerVibration()
            isLearning = true
            learningTargetId = "CUSTOM_IR"
            btnLearn.text = "Listening... Press Remote"
            btnLearn.setBackgroundResource(R.drawable.bg_sheet_btn_blue)
            btnLearn.setTextColor(Color.WHITE)
            showModernPopup("Point remote at ESP32 / USB Hub & press button", "🎯")

            customIrLearningCallback = { capturedCode ->
                runOnUiThread {
                    currentCodeJson = capturedCode
                    updateCodeStatusUI()
                    etManualCode.setText(capturedCode)
                }
            }
        }

        btnTest.setOnClickListener {
            val name = etName.text.toString().trim().ifEmpty { "Test Signal" }
            transmitCustomSignal(currentCodeJson, name)
        }

        var isManualExpanded = false
        tvToggleManual.setOnClickListener {
            triggerVibration()
            isManualExpanded = !isManualExpanded
            if (isManualExpanded) {
                tvToggleManual.text = "▼ Manual Code Entry"
                etManualCode.visibility = View.VISIBLE
                etManualCode.setText(currentCodeJson ?: "")
            } else {
                tvToggleManual.text = "▶ Manual Code Entry (Optional)"
                etManualCode.visibility = View.GONE
            }
        }

        // Timer Pickers
        pickerHour.minValue = 1
        pickerHour.maxValue = 12
        pickerHour.value = buttonToEdit?.timeHour ?: 12

        pickerMinute.minValue = 0
        pickerMinute.maxValue = 59
        pickerMinute.setFormatter { String.format(Locale.US, "%02d", it) }
        pickerMinute.value = buttonToEdit?.timeMinute ?: 0

        pickerAmPm.minValue = 0
        pickerAmPm.maxValue = 1
        pickerAmPm.displayedValues = arrayOf("AM", "PM")
        pickerAmPm.value = if (buttonToEdit?.isAm == false) 1 else 0

        switchTimer.isChecked = buttonToEdit?.hasTimer ?: false
        layoutTimerDetails.visibility = if (switchTimer.isChecked) View.VISIBLE else View.GONE

        switchTimer.setOnCheckedChangeListener { _, isChecked ->
            triggerVibration()
            layoutTimerDetails.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        btnClose.setOnClickListener {
            triggerVibration()
            dialog.dismiss()
        }
        btnCancel.setOnClickListener {
            triggerVibration()
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            triggerVibration()
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = "Please enter button title"
                etName.requestFocus()
                return@setOnClickListener
            }

            // If user manually edited code
            if (etManualCode.visibility == View.VISIBLE) {
                val manualText = etManualCode.text.toString().trim()
                if (manualText.isNotEmpty() && manualText != currentCodeJson) {
                    currentCodeJson = if (manualText.startsWith("{")) {
                        manualText
                    } else {
                        val cleanValues = manualText.replace(" ", "")
                        val count = cleanValues.split(",").filter { it.isNotEmpty() }.size
                        JSONObject().apply {
                            put("type", "raw")
                            put("len", count)
                            put("values", cleanValues)
                        }.toString()
                    }
                }
            }

            val item = CustomIrButton(
                id = buttonToEdit?.id ?: UUID.randomUUID().toString(),
                name = name,
                icon = selectedIcon,
                category = "Universal",
                codeJson = currentCodeJson,
                hasTimer = switchTimer.isChecked,
                timeHour = pickerHour.value,
                timeMinute = pickerMinute.value,
                isAm = pickerAmPm.value == 0,
                isEnabled = true
            )

            customIrManager.saveButton(item)
            showModernPopup("Custom Button '$name' Saved! ✅", "✅")
            onSaved?.invoke()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            if (isLearning && customIrLearningCallback != null) {
                isLearning = false
                learningTargetId = null
                customIrLearningCallback = null
            }
        }

        dialog.show()
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
        if (instance == this) {
            instance = null
        }
        usbSerialManager.unregister()
        toastDismissRunnable?.let { toastHandler.removeCallbacks(it) }
        bgExecutor.execute {
            try {
                mqttClient?.disconnect()
            } catch (e: Exception) {
            }
        }
        bgExecutor.shutdown()
    }
}
