/**
 * Device Manager - Handles device profiles and IR code storage
 */

#ifndef DEVICE_MANAGER_H
#define DEVICE_MANAGER_H

#include <Arduino.h>
#include <ArduinoJson.h>
#include <EEPROM.h>
#include "config.h"
#include "ir_manager.h"

struct IRCommand
{
    String name;
    String description;
    IRCode code;
};

struct Device
{
    String name;
    String type;
    String manufacturer;
    String model;
    IRCommand commands[MAX_COMMANDS];
    uint8_t commandCount;
};

class DeviceManager
{
private:
    Device devices[MAX_DEVICES];
    uint8_t deviceCount;
    bool dataLoaded;

    // EEPROM management
    void saveToEEPROM();
    bool loadFromEEPROM();
    void clearEEPROM();

    // JSON serialization
    String deviceToJson(const Device &device);
    Device jsonToDevice(const String &json);

public:
    DeviceManager();
    ~DeviceManager();

    bool begin();
    void update();

    // Device management
    bool addDevice(const Device &device);
    bool removeDevice(const String &deviceName);
    bool updateDevice(const Device &device);
    Device *getDevice(const String &deviceName);

    // Command management
    bool addCommand(const String &deviceName, const IRCommand &command);
    bool removeCommand(const String &deviceName, const String &commandName);
    IRCommand *getCommand(const String &deviceName, const String &commandName);

    // Listing methods
    String getDeviceList();
    String getCommandList(const String &deviceName);
    uint8_t getDeviceCount() { return deviceCount; }

    // Import/Export
    String exportDevices();
    bool importDevices(const String &jsonData);

    // Utility methods
    bool deviceExists(const String &deviceName);
    bool commandExists(const String &deviceName, const String &commandName);
    void printDeviceInfo(const Device &device);

    // Status methods
    String getStatus();
    void reset();
};

#endif // DEVICE_MANAGER_H