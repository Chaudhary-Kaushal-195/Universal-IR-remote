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
        return prefs.getInt("active_profile_${category.id}", 0).coerceIn(0, MAX_PROFILES - 1)
    }

    fun setActiveProfileIndex(category: DeviceCategory, index: Int) {
        prefs.edit().putInt("active_profile_${category.id}", index.coerceIn(0, MAX_PROFILES - 1)).apply()
    }

    fun getProfileName(category: DeviceCategory, index: Int): String {
        val defaultName = "${category.defaultPrefix} ${index + 1}"
        return prefs.getString("profile_name_${category.id}_$index", defaultName) ?: defaultName
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
        val code = prefs.getString(key, null)
        if (code != null) return code

        // Backward compatibility: If profile 0 and no new key, fallback to legacy buttonId
        if (activeIndex == 0) {
            return prefs.getString(buttonId, null)
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
            buttonId.startsWith("AC_") -> DeviceCategory.AC
            buttonId.startsWith("TV_") -> DeviceCategory.TV
            buttonId.startsWith("LIGHT_") -> DeviceCategory.LIGHT
            buttonId.startsWith("FAN_") -> DeviceCategory.FAN
            else -> DeviceCategory.AC
        }
    }
}
