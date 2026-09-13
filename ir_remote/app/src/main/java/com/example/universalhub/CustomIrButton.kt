package com.example.universalhub

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CustomIrButton(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var icon: String = "⚡",
    var category: String = "Universal",
    var codeJson: String? = null,
    var hasTimer: Boolean = false,
    var timeHour: Int = 12,
    var timeMinute: Int = 0,
    var isAm: Boolean = true,
    var days: List<Int> = emptyList(),
    var isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("icon", icon)
            put("category", category)
            put("codeJson", codeJson ?: "")
            put("hasTimer", hasTimer)
            put("timeHour", timeHour)
            put("timeMinute", timeMinute)
            put("isAm", isAm)
            put("days", JSONArray(days))
            put("isEnabled", isEnabled)
            put("createdAt", createdAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CustomIrButton {
            val daysList = mutableListOf<Int>()
            val daysArray = json.optJSONArray("days")
            if (daysArray != null) {
                for (i in 0 until daysArray.length()) {
                    val day = daysArray.optInt(i, -1)
                    if (day in 0..6) {
                        daysList.add(day)
                    }
                }
            }
            val rawCode = json.optString("codeJson", "")
            return CustomIrButton(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Custom Button"),
                icon = json.optString("icon", "⚡"),
                category = json.optString("category", "Universal"),
                codeJson = if (rawCode.isNotEmpty()) rawCode else null,
                hasTimer = json.optBoolean("hasTimer", false),
                timeHour = json.optInt("timeHour", 12),
                timeMinute = json.optInt("timeMinute", 0),
                isAm = json.optBoolean("isAm", true),
                days = daysList,
                isEnabled = json.optBoolean("isEnabled", true),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
