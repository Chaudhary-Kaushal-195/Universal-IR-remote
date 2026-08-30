package com.espir.app.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.espir.app.R

class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}