package com.espir.app.data

import androidx.room.*

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String,
    val manufacturer: String? = null,
    val model: String? = null
)

@Entity(
    tableName = "ir_commands",
    foreignKeys = [ForeignKey(
        entity = Device::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class IRCommand(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val deviceId: Int,
    val name: String,
    val description: String? = null,
    val irCode: String, // JSON encoded IR code data
    val protocol: String? = null,
    val frequency: Int = 38000
)

data class DeviceWithCommands(
    @Embedded val device: Device,
    @Relation(
        parentColumn = "id",
        entityColumn = "deviceId"
    )
    val commands: List<IRCommand>
)