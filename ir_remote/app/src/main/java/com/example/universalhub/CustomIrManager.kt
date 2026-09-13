package com.example.universalhub

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class CustomIrManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("prefs_custom_ir_devices", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BUTTONS = "key_custom_ir_buttons_list"
    }

    fun getAllButtons(): List<CustomIrButton> {
        val jsonStr = prefs.getString(KEY_BUTTONS, null) ?: return emptyList()
        val list = mutableListOf<CustomIrButton>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val itemObj = jsonArray.getJSONObject(i)
                list.add(CustomIrButton.fromJson(itemObj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveAll(buttons: List<CustomIrButton>) {
        val jsonArray = JSONArray()
        for (btn in buttons) {
            jsonArray.put(btn.toJson())
        }
        prefs.edit().putString(KEY_BUTTONS, jsonArray.toString()).apply()
    }

    fun saveButton(button: CustomIrButton) {
        val current = getAllButtons().toMutableList()
        val index = current.indexOfFirst { it.id == button.id }
        if (index >= 0) {
            current[index] = button
        } else {
            current.add(button)
        }
        saveAll(current)
    }

    fun deleteButton(id: String) {
        val current = getAllButtons().toMutableList()
        current.removeAll { it.id == id }
        saveAll(current)
    }

    fun updateButton(button: CustomIrButton) {
        saveButton(button)
    }

    fun getButtonCount(): Int {
        return getAllButtons().size
    }
}
