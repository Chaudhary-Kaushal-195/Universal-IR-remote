package com.example.universalhub

import android.content.SharedPreferences

enum class DeviceCategory(val id: String, val defaultPrefix: String) {
    AC("AC", "AC"),
    TV("TV", "TV"),
    LIGHT("LIGHT", "Light"),
    FAN("FAN", "Fan")
}

class ProfileManager(private val prefs: SharedPreferences) {

    companion object {
        const val MAX_PROFILES = 5
    }

    fun getActiveProfileIndex(category: DeviceCategory): Int {
        val key = "active_profile_${category.id}"
        return try {
            prefs.getInt(key, 0).coerceIn(0, MAX_PROFILES - 1)
        } catch (e: Exception) {
            // Self-heal corrupted string or invalid type in SharedPreferences
            val fallback = try {
                val rawVal = prefs.all[key]
                when (rawVal) {
                    is Number -> rawVal.toInt().coerceIn(0, MAX_PROFILES - 1)
                    is String -> rawVal.trim().toIntOrNull()?.coerceIn(0, MAX_PROFILES - 1) ?: 0
                    else -> 0
                }
            } catch (_: Exception) {
                0
            }
            try {
                // Remove corrupted String/other type and store cleanly as Integer
                prefs.edit().remove(key).putInt(key, fallback).apply()
            } catch (_: Exception) {}
            fallback
        }
    }

    fun setActiveProfileIndex(category: DeviceCategory, index: Int) {
        val key = "active_profile_${category.id}"
        val cleanIndex = index.coerceIn(0, MAX_PROFILES - 1)
        prefs.edit().remove(key).putInt(key, cleanIndex).apply()
    }

    fun getProfileName(category: DeviceCategory, index: Int): String {
        val defaultName = "${category.defaultPrefix} ${index + 1}"
        val key = "profile_name_${category.id}_$index"
        return try {
            prefs.getString(key, defaultName) ?: defaultName
        } catch (e: Exception) {
            try {
                prefs.all[key]?.toString()?.ifBlank { defaultName } ?: defaultName
            } catch (_: Exception) {
                defaultName
            }
        }
    }

    fun setProfileName(category: DeviceCategory, index: Int, name: String) {
        val cleanName = name.trim().ifEmpty { "${category.defaultPrefix} ${index + 1}" }
        prefs.edit().putString("profile_name_${category.id}_$index", cleanName).apply()
    }

    fun getButtonStorageKey(category: DeviceCategory, profileIndex: Int, buttonId: String): String {
        return "code_${category.id}_${profileIndex}_$buttonId"
    }

    fun getSavedCode(category: DeviceCategory, buttonId: String): String? {
        val activeIndex = getActiveProfileIndex(category)
        val key = getButtonStorageKey(category, activeIndex, buttonId)
        val code = try {
            prefs.getString(key, null)
        } catch (_: Exception) {
            prefs.all[key]?.toString()
        }
        if (code != null) return code

        // Backward compatibility: If profile 0 and no new key, fallback to legacy buttonId
        if (activeIndex == 0) {
            return try {
                prefs.getString(buttonId, null)
            } catch (_: Exception) {
                prefs.all[buttonId]?.toString()
            }
        }
        return null
    }

    fun saveCode(category: DeviceCategory, buttonId: String, codeJson: String) {
        val activeIndex = getActiveProfileIndex(category)
        val key = getButtonStorageKey(category, activeIndex, buttonId)
        val editor = prefs.edit().putString(key, codeJson)
        // Also sync to legacy key if activeIndex == 0 for backward compatibility
        if (activeIndex == 0) {
            editor.putString(buttonId, codeJson)
        }
        editor.apply()
    }

    fun getCategoryFromButtonId(buttonId: String): DeviceCategory {
        return when {
            buttonId.startsWith("AC_") || buttonId.contains("_AC_") -> DeviceCategory.AC
            buttonId.startsWith("TV_") || buttonId.contains("_TV_") -> DeviceCategory.TV
            buttonId.startsWith("LIGHT_") || buttonId.contains("_LIGHT_") -> DeviceCategory.LIGHT
            buttonId.startsWith("FAN_") || buttonId.contains("_FAN_") -> DeviceCategory.FAN
            else -> DeviceCategory.AC
        }
    }
}

/**
 * Safe SharedPreferences extension functions that never throw ClassCastException
 * when keys were previously stored as String instead of Int/Boolean or vice-versa.
 */
fun SharedPreferences.getSafeInt(key: String, defValue: Int): Int {
    return try {
        this.getInt(key, defValue)
    } catch (_: Exception) {
        val raw = this.all[key]
        when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull() ?: defValue
            is Boolean -> if (raw) 1 else 0
            else -> defValue
        }
    }
}

fun SharedPreferences.getSafeBoolean(key: String, defValue: Boolean): Boolean {
    return try {
        this.getBoolean(key, defValue)
    } catch (_: Exception) {
        val raw = this.all[key]
        when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> {
                val s = raw.trim().lowercase()
                if (s == "true" || s == "1") true
                else if (s == "false" || s == "0") false
                else defValue
            }
            else -> defValue
        }
    }
}

fun SharedPreferences.getSafeString(key: String, defValue: String? = null): String? {
    return try {
        this.getString(key, defValue) ?: defValue
    } catch (_: Exception) {
        this.all[key]?.toString() ?: defValue
    }
}
