package com.example.universalhub

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class AcTimerManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ac_timers_pref", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TIMERS = "ac_timers_json"
        const val ACTION_TRIGGER_TIMER = "com.example.universalhub.ACTION_TRIGGER_TIMER"
        const val EXTRA_TIMER_ID = "extra_timer_id"
    }

    fun getAllTimers(): MutableList<AcTimer> {
        val list = mutableListOf<AcTimer>()
        val rawJson = prefs.getString(KEY_TIMERS, null) ?: return list
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(AcTimer.fromJson(obj))
            }
        } catch (e: Exception) {
            Log.e("AcTimerManager", "Error parsing timers", e)
        }
        return list
    }

    fun saveAllTimers(timers: List<AcTimer>) {
        val jsonArray = JSONArray()
        for (timer in timers) {
            jsonArray.put(timer.toJson())
        }
        prefs.edit().putString(KEY_TIMERS, jsonArray.toString()).apply()
    }

    fun addOrUpdateTimer(timer: AcTimer) {
        val timers = getAllTimers()
        val index = timers.indexOfFirst { it.id == timer.id }
        if (index >= 0) {
            timers[index] = timer
        } else {
            timers.add(timer)
        }
        saveAllTimers(timers)

        if (timer.isEnabled) {
            scheduleAlarm(timer)
        } else {
            cancelAlarm(timer)
        }
    }

    fun deleteTimer(timerId: String) {
        val timers = getAllTimers()
        val timer = timers.find { it.id == timerId }
        if (timer != null) {
            cancelAlarm(timer)
            timers.removeAll { it.id == timerId }
            saveAllTimers(timers)
        }
    }

    fun setTimerEnabled(timerId: String, enabled: Boolean) {
        val timers = getAllTimers()
        val timer = timers.find { it.id == timerId } ?: return
        timer.isEnabled = enabled
        saveAllTimers(timers)

        if (enabled) {
            scheduleAlarm(timer)
        } else {
            cancelAlarm(timer)
        }
    }

    fun scheduleAlarm(timer: AcTimer) {
        if (!timer.isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTime = computeNextTriggerTime(timer)

        val intent = Intent(context, AcTimerReceiver::class.java).apply {
            action = ACTION_TRIGGER_TIMER
            putExtra(EXTRA_TIMER_ID, timer.id)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            timer.id.hashCode(),
            intent,
            flags
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d("AcTimerManager", "Scheduled timer '${timer.title}' for $triggerTime")
        } catch (e: SecurityException) {
            Log.e("AcTimerManager", "SecurityException scheduling exact alarm, falling back to inexact", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } catch (e: Exception) {
            Log.e("AcTimerManager", "Failed to schedule alarm", e)
        }
    }

    fun cancelAlarm(timer: AcTimer) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AcTimerReceiver::class.java).apply {
            action = ACTION_TRIGGER_TIMER
            putExtra(EXTRA_TIMER_ID, timer.id)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            timer.id.hashCode(),
            intent,
            flags
        )
        alarmManager.cancel(pendingIntent)
        Log.d("AcTimerManager", "Cancelled timer '${timer.title}'")
    }

    fun computeNextTriggerTime(timer: AcTimer): Long {
        val now = Calendar.getInstance()

        // Handle specific calendar date
        if (!timer.specificDate.isNullOrEmpty()) {
            try {
                val parts = timer.specificDate!!.split("-")
                val y = parts[0].toInt()
                val m = parts[1].toInt() - 1
                val d = parts[2].toInt()
                val target = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, m)
                    set(Calendar.DAY_OF_MONTH, d)
                    set(Calendar.HOUR_OF_DAY, timer.hour)
                    set(Calendar.MINUTE, timer.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return target.timeInMillis
            } catch (e: Exception) {
                Log.e("AcTimerManager", "Error parsing specificDate: ${timer.specificDate}", e)
            }
        }

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, timer.hour)
            set(Calendar.MINUTE, timer.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If target is in the past or right now, advance to next day
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val daysList = timer.getDaysList()
        if (daysList.isNotEmpty() && daysList.size < 7) {
            var attempts = 0
            while (!daysList.contains(target.get(Calendar.DAY_OF_WEEK)) && attempts < 14) {
                target.add(Calendar.DAY_OF_YEAR, 1)
                attempts++
            }
        }

        return target.timeInMillis
    }
}
