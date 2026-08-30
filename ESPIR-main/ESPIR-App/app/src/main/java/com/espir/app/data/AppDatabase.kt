package com.espir.app.data

import android.content.Context
import androidx.room.*

@Database(
    entities = [Device::class, IRCommand::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun irCommandDao(): IRCommandDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "espir_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}