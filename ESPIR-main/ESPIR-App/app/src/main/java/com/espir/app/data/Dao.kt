package com.espir.app.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY name ASC")
    fun getAllDevices(): LiveData<List<Device>>
    
    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun getDevice(id: Int): Device?
    
    @Query("SELECT * FROM devices WHERE name = :name")
    suspend fun getDeviceByName(name: String): Device?
    
    @Insert
    suspend fun insertDevice(device: Device): Long
    
    @Update
    suspend fun updateDevice(device: Device)
    
    @Delete
    suspend fun deleteDevice(device: Device)
    
    @Transaction
    @Query("SELECT * FROM devices ORDER BY name ASC")
    fun getDevicesWithCommands(): LiveData<List<DeviceWithCommands>>
    
    @Transaction
    @Query("SELECT * FROM devices WHERE id = :deviceId")
    suspend fun getDeviceWithCommands(deviceId: Int): DeviceWithCommands?
}

@Dao
interface IRCommandDao {
    @Query("SELECT * FROM ir_commands WHERE deviceId = :deviceId ORDER BY name ASC")
    fun getCommandsForDevice(deviceId: Int): LiveData<List<IRCommand>>
    
    @Query("SELECT * FROM ir_commands WHERE id = :id")
    suspend fun getCommand(id: Int): IRCommand?
    
    @Query("SELECT * FROM ir_commands WHERE deviceId = :deviceId AND name = :name")
    suspend fun getCommandByName(deviceId: Int, name: String): IRCommand?
    
    @Insert
    suspend fun insertCommand(command: IRCommand): Long
    
    @Update
    suspend fun updateCommand(command: IRCommand)
    
    @Delete
    suspend fun deleteCommand(command: IRCommand)
    
    @Query("DELETE FROM ir_commands WHERE deviceId = :deviceId")
    suspend fun deleteCommandsForDevice(deviceId: Int)
}