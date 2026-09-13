package com.example.universalhub

import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class AcTimer(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var hour: Int,
    var minute: Int,
    var actionType: String,
    var actionValue: String = "",
    var repeatType: String = "DAILY", // "ONCE", "DAILY", "WEEKDAYS", "WEEKENDS", "CUSTOM", "SPECIFIC_DATE"
    var selectedDays: String = "1,2,3,4,5,6,7", // 1=Sun, 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri, 7=Sat
    var specificDate: String? = null, // "YYYY-MM-DD"
    var isEnabled: Boolean = true,
    var profileIndex: Int = 0
) {

    fun getDaysList(): List<Int> {
        if (repeatType == "ONCE" || repeatType == "SPECIFIC_DATE" || selectedDays.isEmpty()) return emptyList()
        return selectedDays.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun isDaySelected(dayOfWeek: Int): Boolean {
        if (repeatType == "ONCE" || repeatType == "SPECIFIC_DATE") return false
        return getDaysList().contains(dayOfWeek)
    }

    fun setDaysList(days: List<Int>) {
        if (days.isEmpty()) {
            repeatType = "ONCE"
            selectedDays = ""
        } else {
            val sorted = days.distinct().sorted()
            selectedDays = sorted.joinToString(",")
            repeatType = when {
                sorted.size == 7 -> "DAILY"
                sorted == listOf(2, 3, 4, 5, 6) -> "WEEKDAYS"
                sorted == listOf(1, 7) -> "WEEKENDS"
                else -> "CUSTOM"
            }
        }
    }

    fun getFormattedTime(): String {
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val amPm = if (hour < 12) "AM" else "PM"
        return String.format(Locale.getDefault(), "%02d:%02d %s", h12, minute, amPm)
    }

    fun getTimeDigits(): String {
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.getDefault(), "%02d:%02d", h12, minute)
    }

    fun getAmPm(): String {
        return if (hour < 12) "AM" else "PM"
    }

    fun getActionDescription(): String {
        return when (actionType) {
            "POWER_ON" -> "⚡ Power ON"
            "POWER_OFF" -> "🔴 Power OFF"
            "SET_TEMP" -> "🌡️ Temp: ${actionValue}°C"
            "SET_MODE" -> "❄️ Mode: ${actionValue.lowercase().replaceFirstChar { it.uppercase() }}"
            "SET_FAN" -> "💨 Fan: Speed ${actionValue}"
            "SLEEP" -> "🌙 Sleep: ${actionValue.uppercase(Locale.ROOT)}"
            "DISPLAY" -> "💡 Display: ${actionValue.uppercase(Locale.ROOT)}"
            "CUSTOM" -> "🎯 $actionValue"
            else -> "⚡ Action: $actionType"
        }
    }

    fun getRepeatDescription(): String {
        if (!specificDate.isNullOrEmpty()) {
            return try {
                val parts = specificDate!!.split("-")
                val y = parts[0].toInt()
                val m = parts[1].toInt() - 1
                val d = parts[2].toInt()
                val cal = Calendar.getInstance().apply {
                    set(y, m, d)
                }
                val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
                val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                "📅 ${dayNames[dayOfWeek - 1]}, $d ${monthNames[m]}"
            } catch (_: Exception) {
                "📅 $specificDate"
            }
        }
        val days = getDaysList()
        if (days.isEmpty() || repeatType == "ONCE") return "Once"
        if (days.size == 7) return "Every Day"
        if (days == listOf(2, 3, 4, 5, 6)) return "Mon – Fri"
        if (days == listOf(1, 7)) return "Sat – Sun"
        val dayNames = mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
        return days.mapNotNull { dayNames[it] }.joinToString(", ")
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("hour", hour)
            put("minute", minute)
            put("actionType", actionType)
            put("actionValue", actionValue)
            put("repeatType", repeatType)
            put("selectedDays", selectedDays)
            put("specificDate", specificDate ?: "")
            put("isEnabled", isEnabled)
            put("profileIndex", profileIndex)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AcTimer {
            val repeat = json.optString("repeatType", "DAILY")
            val defaultDays = when (repeat) {
                "ONCE", "SPECIFIC_DATE" -> ""
                "WEEKDAYS" -> "2,3,4,5,6"
                "WEEKENDS" -> "1,7"
                else -> "1,2,3,4,5,6,7"
            }
            val specDate = json.optString("specificDate", "").takeIf { it.isNotEmpty() && it != "null" }
            return AcTimer(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "AC Timer"),
                hour = json.optInt("hour", 6),
                minute = json.optInt("minute", 30),
                actionType = json.optString("actionType", "POWER_ON"),
                actionValue = json.optString("actionValue", ""),
                repeatType = repeat,
                selectedDays = json.optString("selectedDays", defaultDays),
                specificDate = specDate,
                isEnabled = json.optBoolean("isEnabled", true),
                profileIndex = json.optInt("profileIndex", 0)
            )
        }
    }
}
