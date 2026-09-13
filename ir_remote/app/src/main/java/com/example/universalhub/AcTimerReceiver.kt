package com.example.universalhub

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.ConsumerIrManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.concurrent.Executors

class AcTimerReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "ac_timers_channel"
        private const val CHANNEL_NAME = "Smart AC Timers"
        private val bgExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("AcTimerReceiver", "Device rebooted. Rescheduling all active AC timers.")
            val timerManager = AcTimerManager(context)
            for (t in timerManager.getAllTimers()) {
                if (t.isEnabled) {
                    timerManager.scheduleAlarm(t)
                }
            }
            return
        }

        val timerId = intent.getStringExtra(AcTimerManager.EXTRA_TIMER_ID) ?: return
        val timerManager = AcTimerManager(context)
        val timer = timerManager.getAllTimers().find { it.id == timerId } ?: return

        if (!timer.isEnabled) return

        Log.d("AcTimerReceiver", "Timer fired: ${timer.title} - ${timer.getActionDescription()}")

        // 1. Post Android System Notification
        showNotification(context, timer)

        // 2. Execute the IR Action
        val mainAct = MainActivity.instance
        if (mainAct != null && !mainAct.isFinishing) {
            Handler(Looper.getMainLooper()).post {
                mainAct.executeTimerAction(timer)
            }
        } else {
            executeActionDirectly(context, timer)
        }

        // 3. Handle Repeat vs Once / Specific Date
        if (timer.repeatType == "ONCE" || !timer.specificDate.isNullOrEmpty()) {
            timer.isEnabled = false
            timerManager.addOrUpdateTimer(timer)
        } else {
            // Re-arm for the next scheduled occurrence
            timerManager.scheduleAlarm(timer)
        }
    }

    private fun showNotification(context: Context, timer: AcTimer) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Automated triggers for AC Climate control"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏰ AC Timer: ${timer.title}")
            .setContentText("Executed: ${timer.getActionDescription()}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(timer.id.hashCode(), notification)
    }

    private fun executeActionDirectly(context: Context, timer: AcTimer) {
        val prefs = context.getSharedPreferences("UniversalHubPrefs", Context.MODE_PRIVATE)
        val profileManager = ProfileManager(prefs)
        val hubId = prefs.getSafeString("hubId", "universal-ir-hub-01") ?: "universal-ir-hub-01"
        val hubPassword = prefs.getSafeString("hub_password", "HubSecureKey2026") ?: "HubSecureKey2026"
        val topicTx = "universalo-hub/$hubId/rx"

        // Map timer action to target buttonId
        val targetButtonId = when (timer.actionType) {
            "POWER_ON" -> "AC_POWER_ON"
            "POWER_OFF" -> "AC_POWER_OFF"
            "SET_TEMP" -> "AC_TEMP_${timer.actionValue}"
            "SET_MODE" -> "AC_MODE_${timer.actionValue.uppercase()}"
            "SET_FAN" -> "AC_FAN_${timer.actionValue.uppercase()}"
            "SLEEP" -> if (timer.actionValue.equals("OFF", true)) "AC_SLEEP_OFF" else "AC_SLEEP_ON"
            "DISPLAY" -> if (timer.actionValue.equals("OFF", true)) "AC_DISPLAY_OFF" else "AC_DISPLAY_ON"
            "CUSTOM" -> timer.actionValue
            else -> "AC_POWER_ON"
        }

        // Check for learned signal in ProfileManager (with fallback to parent ID)
        val category = DeviceCategory.AC
        var savedSignal = profileManager.getSavedCode(category, targetButtonId)
        if (savedSignal == null) {
            // Fallback to legacy single buttons if discrete wasn't learned
            val fallbackId = when {
                targetButtonId.startsWith("AC_MODE_") -> "AC_MODE"
                targetButtonId.startsWith("AC_FAN_") -> "AC_FAN"
                targetButtonId.startsWith("AC_DISPLAY_") -> "AC_LIGHT"
                targetButtonId.startsWith("AC_SLEEP_") -> "AC_SLEEP"
                else -> null
            }
            if (fallbackId != null) {
                savedSignal = profileManager.getSavedCode(category, fallbackId)
            }
        }

        if (savedSignal == null) {
            Log.w("AcTimerReceiver", "No IR code programmed for $targetButtonId")
            return
        }

        // 1. Phone Internal IR Emitter if available
        val consumerIr = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        if (consumerIr != null && consumerIr.hasIrEmitter()) {
            try {
                val jsonObj = JSONObject(savedSignal)
                val type = jsonObj.optString("type", "raw")
                val values = jsonObj.optString("values", "")
                if (type == "raw" && values.isNotEmpty()) {
                    val pattern = values.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
                    if (pattern.isNotEmpty()) {
                        consumerIr.transmit(38000, pattern)
                        Log.d("AcTimerReceiver", "Dispatched IR via ConsumerIR")
                    }
                }
            } catch (e: Exception) {
                Log.e("AcTimerReceiver", "Internal IR error", e)
            }
        }

        // 2. MQTT Hub over Wi-Fi
        bgExecutor.execute {
            try {
                val broker = "tcp://broker.hivemq.com:1883"
                val clientId = "TimerReceiver_" + System.currentTimeMillis()
                val client = MqttClient(broker, clientId, MemoryPersistence())
                val connOpts = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 5
                }
                client.connect(connOpts)

                val jsonObj = JSONObject(savedSignal).apply {
                    put("auth", hubPassword)
                }
                val message = MqttMessage(jsonObj.toString().toByteArray())
                client.publish(topicTx, message)
                client.disconnect()
                client.close()
                Log.d("AcTimerReceiver", "Dispatched IR via MQTT to $topicTx")
            } catch (e: Exception) {
                Log.e("AcTimerReceiver", "Failed to dispatch timer IR via MQTT", e)
            }
        }
    }
}
