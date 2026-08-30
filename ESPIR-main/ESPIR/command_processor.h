/**
 * Command Processor - Handles BLE commands and coordinates operations
 */

#ifndef COMMAND_PROCESSOR_H
#define COMMAND_PROCESSOR_H

#include <Arduino.h>
#include <ArduinoJson.h>
#include "config.h"
#include "ir_manager.h"
#include "ble_manager.h"
#include "device_manager.h"

class CommandProcessor
{
private:
    IRManager *irManager;
    BLEManager *bleManager;
    DeviceManager *deviceManager;

    // Command handlers
    void handleLearnCommand(const JsonDocument &cmd);
    void handleTransmitCommand(const JsonDocument &cmd);
    void handleListDevicesCommand(const JsonDocument &cmd);
    void handleAddDeviceCommand(const JsonDocument &cmd);
    void handleDeleteDeviceCommand(const JsonDocument &cmd);
    void handleGetStatusCommand(const JsonDocument &cmd);
    void handleResetCommand(const JsonDocument &cmd);

    // Response helpers
    void sendResponse(const String &status, const String &message = "", DynamicJsonDocument *data = nullptr);
    void sendError(const String &error, const String &details = "");

    // Validation helpers
    bool validateCommand(const JsonDocument &cmd, const String requiredFields[], int fieldCount);

public:
    CommandProcessor();
    ~CommandProcessor();

    void begin(IRManager *ir, BLEManager *ble, DeviceManager *device);
    void update();

    // Main command processing
    void processCommand(const String &commandJson);

    // Status methods
    String getStatus();
};

#endif // COMMAND_PROCESSOR_H