package com.example.universalhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCompatibilityTest {

    @Test
    fun testEmptyAndMalformedJsonReturnsIncompatible() {
        val emptyResult = BackupManager.inspectBackupJson("")
        assertEquals(BackupType.INCOMPATIBLE, emptyResult.type)

        val whitespaceResult = BackupManager.inspectBackupJson("   \n\t  ")
        assertEquals(BackupType.INCOMPATIBLE, whitespaceResult.type)

        val malformedResult = BackupManager.inspectBackupJson("{ invalid json text ...")
        assertEquals(BackupType.INCOMPATIBLE, malformedResult.type)
    }

    @Test
    fun testNonIrJsonObjectReturnsIncompatible() {
        val nonIrJson = """
            {
                "user": "sample_user",
                "app": "some other app",
                "settings": { "theme": "dark" }
            }
        """.trimIndent()
        val result = BackupManager.inspectBackupJson(nonIrJson)
        assertEquals(BackupType.INCOMPATIBLE, result.type)
    }

    @Test
    fun testEmptyOrInvalidJsonArrayReturnsIncompatible() {
        val emptyArray = "[]"
        assertEquals(BackupType.INCOMPATIBLE, BackupManager.inspectBackupJson(emptyArray).type)

        val stringArray = """["one", "two", "three"]"""
        assertEquals(BackupType.INCOMPATIBLE, BackupManager.inspectBackupJson(stringArray).type)

        val numberArray = """[1, 2, 3, 4]"""
        assertEquals(BackupType.INCOMPATIBLE, BackupManager.inspectBackupJson(numberArray).type)
    }

    @Test
    fun testEmptyDeviceProfileReturnsIncompatible() {
        val emptyProfile = """
            {
                "backup_type": "DEVICE_PROFILE",
                "category": "AC",
                "profile_index": 0,
                "profile_name": "Empty AC",
                "codes": {}
            }
        """.trimIndent()
        assertEquals(BackupType.INCOMPATIBLE, BackupManager.inspectBackupJson(emptyProfile).type)
    }

    @Test
    fun testValidSingleDeviceProfileReturnsCompatible() {
        val validProfile = """
            {
                "backup_type": "DEVICE_PROFILE",
                "category": "AC",
                "profile_index": 0,
                "profile_name": "Living Room AC",
                "codes": {
                    "AC_POWER": { "type": "raw", "len": 196, "values": "3150,3200,450,400" }
                }
            }
        """.trimIndent()
        val result = BackupManager.inspectBackupJson(validProfile)
        assertEquals(BackupType.DEVICE_PROFILE, result.type)
        assertEquals(DeviceCategory.AC, result.category)
        assertEquals(1, result.codeCount)
    }

    @Test
    fun testValidCustomButtonsArrayReturnsCompatible() {
        val customArray = """
            [
                {
                    "id": "btn-1",
                    "name": "Projector On",
                    "icon": "📽️",
                    "codeJson": "{\"protocol\":\"NEC\",\"bits\":32,\"value\":\"0x20DF10EF\"}"
                }
            ]
        """.trimIndent()
        val result = BackupManager.inspectBackupJson(customArray)
        assertEquals(BackupType.CUSTOM_BUTTONS, result.type)
        assertEquals(1, result.customButtonCount)
    }

    @Test
    fun testUserAcBackupPayloadReturnsFullBackupCompatible() {
        val userJson = """
            {
              "backup_type": "FULL_BACKUP",
              "version": 2,
              "timestamp": 1789302375371,
              "hubId": "generic-ir-hub-01",
              "devices": {
                "AC": {
                  "active_profile": 0,
                  "profiles": [
                    {
                      "index": 0,
                      "name": "AC 1",
                      "codes": {
                        "active_profile_AC": "0",
                        "code_AC_0_AC_POWER_OFF": {
                          "type": "raw",
                          "len": 196,
                          "values": "3150,3200,450,400"
                        },
                        "code_AC_0_AC_POWER_ON": {
                          "type": "raw",
                          "len": 196,
                          "values": "3150,3200,450,450"
                        }
                      }
                    }
                  ]
                }
              },
              "custom_buttons": [],
              "raw_entries": {
                "active_profile_AC": "0"
              }
            }
        """.trimIndent()

        val result = BackupManager.inspectBackupJson(userJson)
        assertEquals(BackupType.FULL_BACKUP, result.type)

        val profiles = BackupManager.parseAvailableProfiles(userJson)
        assertEquals(1, profiles.size)
        assertEquals(DeviceCategory.AC, profiles[0].category)
        // Ensure active_profile_AC was excluded from learned code count
        assertEquals(2, profiles[0].codeCount)
    }

    @Test
    fun testMetadataKeyFiltering() {
        assertTrue(BackupManager.isMetadataKey("active_profile_AC"))
        assertTrue(BackupManager.isMetadataKey("active_profile_TV"))
        assertTrue(BackupManager.isMetadataKey("profile_name_AC_0"))
        assertTrue(BackupManager.isMetadataKey("state_ac"))
        assertTrue(BackupManager.isMetadataKey("acTemp"))
        assertTrue(BackupManager.isMetadataKey("esp32Ip"))
        assertTrue(BackupManager.isMetadataKey("hub_password"))
        assertTrue(BackupManager.isMetadataKey("is_logged_in"))

        assertFalse(BackupManager.isMetadataKey("AC_POWER"))
        assertFalse(BackupManager.isMetadataKey("code_AC_0_AC_POWER"))
        assertFalse(BackupManager.isMetadataKey("TV_MUTE"))
        assertFalse(BackupManager.isMetadataKey("LIGHT_POWER"))
    }
}
