package com.example.universalhub

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

enum class BackupType {
    FULL_BACKUP,
    DEVICE_PROFILE,
    CUSTOM_BUTTONS,
    LEGACY
}

data class InspectionResult(
    val type: BackupType,
    val category: DeviceCategory? = null,
    val profileIndex: Int = 0,
    val profileName: String = "",
    val codeCount: Int = 0,
    val customButtonCount: Int = 0,
    val rawJson: String = ""
)

data class BackupProfileItem(
    val id: String,
    val category: DeviceCategory?,
    val profileIndex: Int,
    val profileName: String,
    val codeCount: Int,
    val icon: String,
    val isCustomButtons: Boolean = false,
    val customButtonCount: Int = 0,
    val codesObj: JSONObject? = null,
    val customButtonsArr: JSONArray? = null,
    var isSelected: Boolean = true
)

object BackupManager {

    private const val TAG = "BackupManager"

    // ====================================================================
    // 1. EXPORT GENERATION
    // ====================================================================

    /**
     * Creates a Full Backup JSON containing all device profiles, custom IR buttons,
     * and general preferences.
     */
    fun createFullBackupJson(
        sharedPref: SharedPreferences,
        profileManager: ProfileManager,
        customIrManager: CustomIrManager
    ): String {
        val root = JSONObject()
        root.put("backup_type", BackupType.FULL_BACKUP.name)
        root.put("version", 2)
        root.put("timestamp", System.currentTimeMillis())
        root.put("hubId", sharedPref.getString("hubId", "kaushal-ir-hub-97"))

        // Devices container
        val devicesObj = JSONObject()
        for (category in DeviceCategory.values()) {
            val catObj = JSONObject()
            catObj.put("active_profile", profileManager.getActiveProfileIndex(category))

            val profilesArray = JSONArray()
            for (pIndex in 0 until ProfileManager.MAX_PROFILES) {
                val pObj = JSONObject()
                pObj.put("index", pIndex)
                pObj.put("name", profileManager.getProfileName(category, pIndex))

                val codesObj = JSONObject()
                val prefix = "code_${category.id}_${pIndex}_"
                for ((key, value) in sharedPref.all) {
                    if (key.startsWith(prefix)) {
                        val btnId = key.substring(prefix.length)
                        try {
                            codesObj.put(btnId, JSONObject(value.toString()))
                        } catch (e: Exception) {
                            codesObj.put(btnId, value.toString())
                        }
                    }
                }
                // Also capture legacy keys for profile 0 if missing
                if (pIndex == 0) {
                    for ((key, value) in sharedPref.all) {
                        if (isLegacyKeyForCategory(key, category) && !codesObj.has(key)) {
                            try {
                                codesObj.put(key, JSONObject(value.toString()))
                            } catch (e: Exception) {
                                codesObj.put(key, value.toString())
                            }
                        }
                    }
                }
                pObj.put("codes", codesObj)
                profilesArray.put(pObj)
            }
            catObj.put("profiles", profilesArray)
            devicesObj.put(category.id, catObj)
        }
        root.put("devices", devicesObj)

        // Custom IR Buttons
        val customBtnsArray = JSONArray()
        val customBtns = customIrManager.getAllButtons()
        for (btn in customBtns) {
            customBtnsArray.put(btn.toJson())
        }
        root.put("custom_buttons", customBtnsArray)

        // Raw entries for backwards compatibility with older versions
        val rawObj = JSONObject()
        for ((key, value) in sharedPref.all) {
            if (key != "esp32Ip" && key != "hubId" && !key.startsWith("state_") && key != "acTemp") {
                try {
                    rawObj.put(key, JSONObject(value.toString()))
                } catch (e: Exception) {
                    rawObj.put(key, value.toString())
                }
            }
        }
        root.put("raw_entries", rawObj)

        return root.toString(2)
    }

    /**
     * Creates a Single Device Profile JSON (e.g. AC 1, AC 2, TV 1, etc.)
     */
    fun createDeviceProfileJson(
        category: DeviceCategory,
        profileIndex: Int,
        sharedPref: SharedPreferences,
        profileManager: ProfileManager
    ): String {
        val root = JSONObject()
        root.put("backup_type", BackupType.DEVICE_PROFILE.name)
        root.put("version", 2)
        root.put("timestamp", System.currentTimeMillis())
        root.put("category", category.id)
        root.put("profile_index", profileIndex)
        val profileName = profileManager.getProfileName(category, profileIndex)
        root.put("profile_name", profileName)

        val codesObj = JSONObject()
        val prefix = "code_${category.id}_${profileIndex}_"
        for ((key, value) in sharedPref.all) {
            if (key.startsWith(prefix)) {
                val btnId = key.substring(prefix.length)
                try {
                    codesObj.put(btnId, JSONObject(value.toString()))
                } catch (e: Exception) {
                    codesObj.put(btnId, value.toString())
                }
            }
        }
        // Fallback for profile 0 legacy keys
        if (profileIndex == 0) {
            for ((key, value) in sharedPref.all) {
                if (isLegacyKeyForCategory(key, category) && !codesObj.has(key)) {
                    try {
                        codesObj.put(key, JSONObject(value.toString()))
                    } catch (e: Exception) {
                        codesObj.put(key, value.toString())
                    }
                }
            }
        }
        root.put("codes", codesObj)
        root.put("code_count", codesObj.length())

        return root.toString(2)
    }

    /**
     * Creates a Backup JSON for Custom IR Buttons.
     */
    fun createCustomButtonsJson(customIrManager: CustomIrManager): String {
        val root = JSONObject()
        root.put("backup_type", BackupType.CUSTOM_BUTTONS.name)
        root.put("version", 2)
        root.put("timestamp", System.currentTimeMillis())
        val list = customIrManager.getAllButtons()
        root.put("button_count", list.size)

        val arr = JSONArray()
        for (btn in list) {
            arr.put(btn.toJson())
        }
        root.put("buttons", arr)

        return root.toString(2)
    }

    // ====================================================================
    // 2. INSPECT IMPORT PAYLOAD
    // ====================================================================

    fun inspectBackupJson(rawJson: String): InspectionResult {
        val trimmed = rawJson.trim()
        // Check if raw text is a JSON Array (e.g. custom buttons array)
        if (trimmed.startsWith("[")) {
            return try {
                val arr = JSONArray(trimmed)
                InspectionResult(
                    type = BackupType.CUSTOM_BUTTONS,
                    customButtonCount = arr.length(),
                    rawJson = trimmed
                )
            } catch (e: Exception) {
                InspectionResult(type = BackupType.LEGACY, rawJson = trimmed)
            }
        }

        return try {
            val obj = JSONObject(trimmed)
            val typeStr = obj.optString("backup_type", "")

            when (typeStr) {
                BackupType.DEVICE_PROFILE.name -> {
                    val catStr = obj.optString("category", "AC")
                    val category = DeviceCategory.values().firstOrNull { it.id.equals(catStr, ignoreCase = true) }
                        ?: DeviceCategory.AC
                    val pIndex = obj.optInt("profile_index", 0)
                    val pName = obj.optString("profile_name", "${category.defaultPrefix} ${pIndex + 1}")
                    val codesObj = obj.optJSONObject("codes") ?: obj
                    var count = 0
                    val keys = codesObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (!isMetadataKey(k)) count++
                    }

                    InspectionResult(
                        type = BackupType.DEVICE_PROFILE,
                        category = category,
                        profileIndex = pIndex,
                        profileName = pName,
                        codeCount = count,
                        rawJson = trimmed
                    )
                }
                BackupType.CUSTOM_BUTTONS.name -> {
                    val arr = obj.optJSONArray("buttons") ?: obj.optJSONArray("custom_buttons")
                    val count = arr?.length() ?: obj.optInt("button_count", 0)
                    InspectionResult(
                        type = BackupType.CUSTOM_BUTTONS,
                        customButtonCount = count,
                        rawJson = trimmed
                    )
                }
                BackupType.FULL_BACKUP.name -> {
                    InspectionResult(
                        type = BackupType.FULL_BACKUP,
                        rawJson = trimmed
                    )
                }
                else -> {
                    // Check for custom buttons container
                    val customArr = obj.optJSONArray("buttons") ?: obj.optJSONArray("custom_buttons")
                    if (customArr != null) {
                        InspectionResult(
                            type = BackupType.CUSTOM_BUTTONS,
                            customButtonCount = customArr.length(),
                            rawJson = trimmed
                        )
                    } else if (obj.has("category") && (obj.has("codes") || obj.length() > 2)) {
                        val catStr = obj.optString("category", "AC")
                        val category = DeviceCategory.values().firstOrNull { it.id.equals(catStr, ignoreCase = true) }
                            ?: DeviceCategory.AC
                        val pIndex = obj.optInt("profile_index", 0)
                        val pName = obj.optString("profile_name", "${category.defaultPrefix} ${pIndex + 1}")
                        val codesObj = obj.optJSONObject("codes") ?: obj
                        var count = 0
                        val keys = codesObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (!isMetadataKey(k)) count++
                        }
                        InspectionResult(
                            type = BackupType.DEVICE_PROFILE,
                            category = category,
                            profileIndex = pIndex,
                            profileName = pName,
                            codeCount = count,
                            rawJson = trimmed
                        )
                    } else if (obj.has("devices")) {
                        // Full backup structure without backup_type
                        InspectionResult(
                            type = BackupType.FULL_BACKUP,
                            rawJson = trimmed
                        )
                    } else {
                        // Direct flat dictionary from Web App or legacy export
                        var acCount = 0
                        var tvCount = 0
                        var lightCount = 0
                        var fanCount = 0
                        var otherCount = 0

                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (isMetadataKey(k)) continue
                            when {
                                k.startsWith("AC_") || k.contains("_AC_") -> acCount++
                                k.startsWith("TV_") || k.contains("_TV_") -> tvCount++
                                k.startsWith("LIGHT_") || k.contains("_LIGHT_") -> lightCount++
                                k.startsWith("FAN_") || k.contains("_FAN_") -> fanCount++
                                else -> otherCount++
                            }
                        }

                        // If all codes belong to one device category, route to slot chooser!
                        if (acCount > 0 && tvCount == 0 && lightCount == 0 && fanCount == 0) {
                            InspectionResult(
                                type = BackupType.DEVICE_PROFILE,
                                category = DeviceCategory.AC,
                                profileIndex = 0,
                                profileName = "Web App AC Remote",
                                codeCount = acCount,
                                rawJson = trimmed
                            )
                        } else if (tvCount > 0 && acCount == 0 && lightCount == 0 && fanCount == 0) {
                            InspectionResult(
                                type = BackupType.DEVICE_PROFILE,
                                category = DeviceCategory.TV,
                                profileIndex = 0,
                                profileName = "Web App TV Remote",
                                codeCount = tvCount,
                                rawJson = trimmed
                            )
                        } else if (lightCount > 0 && acCount == 0 && tvCount == 0 && fanCount == 0) {
                            InspectionResult(
                                type = BackupType.DEVICE_PROFILE,
                                category = DeviceCategory.LIGHT,
                                profileIndex = 0,
                                profileName = "Web App Light Remote",
                                codeCount = lightCount,
                                rawJson = trimmed
                            )
                        } else if (fanCount > 0 && acCount == 0 && tvCount == 0 && lightCount == 0) {
                            InspectionResult(
                                type = BackupType.DEVICE_PROFILE,
                                category = DeviceCategory.FAN,
                                profileIndex = 0,
                                profileName = "Web App Fan Remote",
                                codeCount = fanCount,
                                rawJson = trimmed
                            )
                        } else {
                            // Mixed categories or full database
                            InspectionResult(
                                type = BackupType.FULL_BACKUP,
                                rawJson = trimmed
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting backup JSON", e)
            InspectionResult(
                type = BackupType.LEGACY,
                rawJson = trimmed
            )
        }
    }

    private fun isMetadataKey(key: String): Boolean {
        return key == "backup_type" || key == "version" || key == "timestamp" ||
                key == "hubId" || key == "category" || key == "profile_name" ||
                key == "profile_index" || key == "code_count" || key == "button_count" ||
                key == "devices" || key == "custom_buttons" || key == "raw_entries" ||
                key == "buttons" || key == "codes"
    }

    // ====================================================================
    // 3. RESTORATION METHODS
    // ====================================================================

    /**
     * Restores a single device profile into the specified target profile slot (0..4).
     */
    fun restoreDeviceProfile(
        rawJson: String,
        targetIndex: Int,
        sharedPref: SharedPreferences,
        profileManager: ProfileManager
    ): Boolean {
        return try {
            val obj = JSONObject(rawJson)
            val catStr = obj.optString("category", "AC")
            val category = DeviceCategory.values().firstOrNull { it.id.equals(catStr, ignoreCase = true) }
                ?: DeviceCategory.AC
            val sourceName = obj.optString("profile_name", "")

            // Support both nested "codes" object and flat key-values
            val codesObj = obj.optJSONObject("codes") ?: obj

            val editor = sharedPref.edit()

            // Cleanly replace old codes in target slot with new ones
            val targetPrefix = "code_${category.id}_${targetIndex}_"
            for ((k, _) in sharedPref.all) {
                if (k.startsWith(targetPrefix)) {
                    editor.remove(k)
                }
            }

            // Update profile name if meaningful
            if (sourceName.isNotBlank() && !sourceName.startsWith("Web App ") && !sourceName.startsWith("Imported ")) {
                editor.putString("profile_name_${category.id}_$targetIndex", sourceName)
            }

            // Write all codes to target slot
            val keys = codesObj.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                if (isMetadataKey(rawKey)) continue

                // Clean prefix like "code_AC_0_AC_POWER" -> "AC_POWER"
                val btnId = rawKey.replaceFirst("^code_[A-Za-z0-9]+_[0-9]+_".toRegex(), "")
                val targetKey = "code_${category.id}_${targetIndex}_$btnId"
                val codeVal = codesObj.get(rawKey).toString()
                editor.putString(targetKey, codeVal)

                // Sync to legacy key if target is slot 0
                if (targetIndex == 0) {
                    editor.putString(btnId, codeVal)
                }
            }

            editor.apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring device profile", e)
            false
        }
    }

    /**
     * Restores Custom IR Buttons (either merging or replacing).
     */
    fun restoreCustomButtons(
        rawJson: String,
        customIrManager: CustomIrManager,
        replaceAll: Boolean
    ): Int {
        return try {
            val incomingList = mutableListOf<CustomIrButton>()
            val trimmed = rawJson.trim()

            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    incomingList.add(CustomIrButton.fromJson(arr.getJSONObject(i)))
                }
            } else {
                val obj = JSONObject(trimmed)
                val arr = obj.optJSONArray("buttons")
                    ?: obj.optJSONArray("custom_buttons")
                    ?: JSONArray()
                for (i in 0 until arr.length()) {
                    incomingList.add(CustomIrButton.fromJson(arr.getJSONObject(i)))
                }
            }

            if (replaceAll) {
                val existing = customIrManager.getAllButtons()
                for (btn in existing) {
                    customIrManager.deleteButton(btn.id)
                }
            }

            for (btn in incomingList) {
                customIrManager.saveButton(btn)
            }
            incomingList.size
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring custom buttons", e)
            0
        }
    }

    /**
     * Restores Full Hub Backup (All devices, profiles, and custom buttons).
     */
    fun restoreFullBackup(
        rawJson: String,
        sharedPref: SharedPreferences,
        profileManager: ProfileManager,
        customIrManager: CustomIrManager
    ): Boolean {
        return try {
            val root = JSONObject(rawJson)
            val editor = sharedPref.edit()

            // 1. Check for structured devices object (from Android or Web App export)
            val devicesObj = root.optJSONObject("devices")
            if (devicesObj != null) {
                for (category in DeviceCategory.values()) {
                    val catObj = devicesObj.optJSONObject(category.id)
                        ?: devicesObj.optJSONObject(category.id.lowercase())
                        ?: continue
                    val activeIdx = catObj.optInt("active_profile", 0)
                    editor.putInt("active_profile_${category.id}", activeIdx)

                    val profilesArr = catObj.optJSONArray("profiles") ?: continue
                    for (i in 0 until profilesArr.length()) {
                        val pObj = profilesArr.getJSONObject(i)
                        val pIdx = pObj.optInt("index", i)
                        val pName = pObj.optString("name", "")
                        if (pName.isNotBlank()) {
                            editor.putString("profile_name_${category.id}_$pIdx", pName)
                        }

                        val codesObj = pObj.optJSONObject("codes") ?: continue
                        val keys = codesObj.keys()
                        while (keys.hasNext()) {
                            val btnId = keys.next()
                            if (isMetadataKey(btnId)) continue
                            val cleanBtnId = btnId.replaceFirst("^code_[A-Za-z0-9]+_[0-9]+_".toRegex(), "")
                            val codeVal = codesObj.get(btnId).toString()
                            editor.putString("code_${category.id}_${pIdx}_$cleanBtnId", codeVal)
                            if (pIdx == 0) {
                                editor.putString(cleanBtnId, codeVal)
                            }
                        }
                    }
                }
            }

            // 2. Custom buttons
            val customBtnsArr = root.optJSONArray("custom_buttons")
                ?: root.optJSONArray("buttons")
            if (customBtnsArr != null) {
                for (i in 0 until customBtnsArr.length()) {
                    val btn = CustomIrButton.fromJson(customBtnsArr.getJSONObject(i))
                    customIrManager.saveButton(btn)
                }
            }

            // 3. Raw entries / Legacy fallback / Flat code map
            val rawEntries = root.optJSONObject("raw_entries") ?: root
            val keys = rawEntries.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (isMetadataKey(key)) continue

                val optObj = rawEntries.optJSONObject(key)
                val codeVal = optObj?.toString() ?: rawEntries.optString(key, "")
                if (codeVal.isNotBlank()) {
                    editor.putString(key, codeVal)

                    // If key is a clean button id (e.g. AC_POWER, TV_POWER), also map to Profile 0
                    val cleanKey = key.replaceFirst("^code_[A-Za-z0-9]+_[0-9]+_".toRegex(), "")
                    val cat = profileManager.getCategoryFromButtonId(cleanKey)
                    editor.putString("code_${cat.id}_0_$cleanKey", codeVal)
                }
            }

            editor.apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring full backup", e)
            false
        }
    }

    fun parseAvailableProfiles(rawJson: String): List<BackupProfileItem> {
        val result = mutableListOf<BackupProfileItem>()
        val trimmed = rawJson.trim()
        val obj = try { JSONObject(trimmed) } catch (e: Exception) { return result }

        // 1. Devices object
        val devicesObj = obj.optJSONObject("devices")
        if (devicesObj != null) {
            for (category in DeviceCategory.values()) {
                val catObj = devicesObj.optJSONObject(category.id) ?: continue
                val profilesArr = catObj.optJSONArray("profiles") ?: continue
                val catIcon = when (category) {
                    DeviceCategory.AC -> "❄️"
                    DeviceCategory.TV -> "📺"
                    DeviceCategory.LIGHT -> "💡"
                    DeviceCategory.FAN -> "🌀"
                }
                for (i in 0 until profilesArr.length()) {
                    val pObj = profilesArr.optJSONObject(i) ?: continue
                    val pIndex = pObj.optInt("index", i)
                    val pName = pObj.optString("name", "${category.defaultPrefix} ${pIndex + 1}")
                    val codesObj = pObj.optJSONObject("codes")
                    var count = 0
                    if (codesObj != null) {
                        val keys = codesObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (!isMetadataKey(k)) count++
                        }
                    }
                    if (count > 0) {
                        result.add(
                            BackupProfileItem(
                                id = "${category.id}_$pIndex",
                                category = category,
                                profileIndex = pIndex,
                                profileName = "${category.defaultPrefix} ${pIndex + 1}: $pName",
                                codeCount = count,
                                icon = catIcon,
                                codesObj = codesObj
                            )
                        )
                    }
                }
            }
        }

        // 2. Custom Buttons
        val customArr = obj.optJSONArray("buttons") ?: obj.optJSONArray("custom_buttons")
        if (customArr != null && customArr.length() > 0) {
            result.add(
                BackupProfileItem(
                    id = "CUSTOM_BUTTONS",
                    category = null,
                    profileIndex = -1,
                    profileName = "Custom IR Buttons",
                    codeCount = customArr.length(),
                    icon = "✨",
                    isCustomButtons = true,
                    customButtonCount = customArr.length(),
                    customButtonsArr = customArr
                )
            )
        }

        // 3. If raw_entries or flat dictionary from web app
        if (result.isEmpty()) {
            val rawEntries = obj.optJSONObject("raw_entries") ?: obj
            val acCodes = JSONObject()
            val tvCodes = JSONObject()
            val lightCodes = JSONObject()
            val fanCodes = JSONObject()

            val keys = rawEntries.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (isMetadataKey(k)) continue
                val v = rawEntries.get(k)
                when {
                    k.startsWith("AC_") || k.contains("_AC_") -> acCodes.put(k, v)
                    k.startsWith("TV_") || k.contains("_TV_") -> tvCodes.put(k, v)
                    k.startsWith("LIGHT_") || k.contains("_LIGHT_") -> lightCodes.put(k, v)
                    k.startsWith("FAN_") || k.contains("_FAN_") -> fanCodes.put(k, v)
                }
            }

            if (acCodes.length() > 0) {
                result.add(BackupProfileItem("AC_0", DeviceCategory.AC, 0, "AC 1: Main AC", acCodes.length(), "❄️", codesObj = acCodes))
            }
            if (tvCodes.length() > 0) {
                result.add(BackupProfileItem("TV_0", DeviceCategory.TV, 0, "TV 1: Main TV", tvCodes.length(), "📺", codesObj = tvCodes))
            }
            if (lightCodes.length() > 0) {
                result.add(BackupProfileItem("LIGHT_0", DeviceCategory.LIGHT, 0, "Light 1: Main Light", lightCodes.length(), "💡", codesObj = lightCodes))
            }
            if (fanCodes.length() > 0) {
                result.add(BackupProfileItem("FAN_0", DeviceCategory.FAN, 0, "Fan 1: Main Fan", fanCodes.length(), "🌀", codesObj = fanCodes))
            }
        }

        return result
    }

    fun restoreSelectedProfiles(
        items: List<BackupProfileItem>,
        sharedPref: SharedPreferences,
        profileManager: ProfileManager,
        customIrManager: CustomIrManager
    ): Int {
        var restoredCount = 0
        val editor = sharedPref.edit()

        for (item in items) {
            if (!item.isSelected) continue

            if (item.isCustomButtons) {
                val arr = item.customButtonsArr
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val btnObj = arr.optJSONObject(i) ?: continue
                        val button = CustomIrButton.fromJson(btnObj)
                        customIrManager.saveButton(button)
                    }
                    restoredCount++
                }
            } else if (item.category != null && item.codesObj != null) {
                val category = item.category
                val targetIndex = item.profileIndex

                // Clear old codes for that target slot so new one cleanly replaces it
                val targetPrefix = "code_${category.id}_${targetIndex}_"
                for ((k, _) in sharedPref.all) {
                    if (k.startsWith(targetPrefix)) {
                        editor.remove(k)
                    }
                }

                // Write codes
                val keys = item.codesObj.keys()
                while (keys.hasNext()) {
                    val rawKey = keys.next()
                    if (isMetadataKey(rawKey)) continue

                    val btnId = rawKey.replaceFirst("^code_[A-Za-z0-9]+_[0-9]+_".toRegex(), "")
                    val targetKey = "code_${category.id}_${targetIndex}_$btnId"
                    val codeVal = item.codesObj.get(rawKey).toString()
                    editor.putString(targetKey, codeVal)

                    if (targetIndex == 0) {
                        editor.putString(btnId, codeVal)
                    }
                }
                restoredCount++
            }
        }

        editor.apply()
        return restoredCount
    }

    private fun isLegacyKeyForCategory(key: String, category: DeviceCategory): Boolean {
        return when (category) {
            DeviceCategory.AC -> key.startsWith("AC_")
            DeviceCategory.TV -> key.startsWith("TV_")
            DeviceCategory.LIGHT -> key.startsWith("LIGHT_")
            DeviceCategory.FAN -> key.startsWith("FAN_")
        }
    }
}
