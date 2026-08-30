/**
 * Device Manager Implementation
 */

#include "device_manager.h"

DeviceManager::DeviceManager() : deviceCount(0), dataLoaded(false)
{
  memset(devices, 0, sizeof(devices));
}

DeviceManager::~DeviceManager()
{
  // Clean up any dynamically allocated memory
}

bool DeviceManager::begin()
{
  DEBUG_PRINTLN("Initializing Device Manager...");

  // Initialize EEPROM
  EEPROM.begin(EEPROM_SIZE);

  // Load devices from EEPROM
  if (!loadFromEEPROM())
  {
    DEBUG_PRINTLN("No valid device data found, starting fresh");
    deviceCount = 0;
  }

  dataLoaded = true;
  DEBUG_PRINTLN("Device Manager initialized successfully");
  DEBUG_PRINT("Loaded devices: ");
  DEBUG_PRINTLN(deviceCount);

  return true;
}

void DeviceManager::update()
{
  // Periodic maintenance tasks
  // Currently no periodic tasks required
}

bool DeviceManager::addDevice(const Device &device)
{
  if (deviceCount >= MAX_DEVICES)
  {
    DEBUG_PRINTLN("ERROR: Maximum device count reached");
    return false;
  }

  // Check if device already exists
  if (deviceExists(device.name))
  {
    DEBUG_PRINTLN("ERROR: Device already exists: " + device.name);
    return false;
  }

  // Add device to array
  devices[deviceCount] = device;
  devices[deviceCount].commandCount = 0; // Initialize command count
  deviceCount++;

  // Save to EEPROM
  saveToEEPROM();

  DEBUG_PRINTLN("Added device: " + device.name);
  return true;
}

bool DeviceManager::removeDevice(const String &deviceName)
{
  for (uint8_t i = 0; i < deviceCount; i++)
  {
    if (devices[i].name == deviceName)
    {
      // Shift remaining devices down
      for (uint8_t j = i; j < deviceCount - 1; j++)
      {
        devices[j] = devices[j + 1];
      }
      deviceCount--;

      // Save to EEPROM
      saveToEEPROM();

      DEBUG_PRINTLN("Removed device: " + deviceName);
      return true;
    }
  }

  DEBUG_PRINTLN("ERROR: Device not found: " + deviceName);
  return false;
}

bool DeviceManager::updateDevice(const Device &device)
{
  for (uint8_t i = 0; i < deviceCount; i++)
  {
    if (devices[i].name == device.name)
    {
      devices[i] = device;
      saveToEEPROM();
      DEBUG_PRINTLN("Updated device: " + device.name);
      return true;
    }
  }

  DEBUG_PRINTLN("ERROR: Device not found for update: " + device.name);
  return false;
}

Device *DeviceManager::getDevice(const String &deviceName)
{
  for (uint8_t i = 0; i < deviceCount; i++)
  {
    if (devices[i].name == deviceName)
    {
      return &devices[i];
    }
  }
  return nullptr;
}

bool DeviceManager::addCommand(const String &deviceName, const IRCommand &command)
{
  Device *device = getDevice(deviceName);
  if (!device)
  {
    DEBUG_PRINTLN("ERROR: Device not found: " + deviceName);
    return false;
  }

  if (device->commandCount >= MAX_COMMANDS)
  {
    DEBUG_PRINTLN("ERROR: Maximum command count reached for device: " + deviceName);
    return false;
  }

  // Check if command already exists
  if (commandExists(deviceName, command.name))
  {
    DEBUG_PRINTLN("ERROR: Command already exists: " + command.name);
    return false;
  }

  // Add command
  device->commands[device->commandCount] = command;
  device->commandCount++;

  // Save to EEPROM
  saveToEEPROM();

  DEBUG_PRINTLN("Added command: " + command.name + " to device: " + deviceName);
  return true;
}

bool DeviceManager::removeCommand(const String &deviceName, const String &commandName)
{
  Device *device = getDevice(deviceName);
  if (!device)
  {
    DEBUG_PRINTLN("ERROR: Device not found: " + deviceName);
    return false;
  }

  for (uint8_t i = 0; i < device->commandCount; i++)
  {
    if (device->commands[i].name == commandName)
    {
      // Shift remaining commands down
      for (uint8_t j = i; j < device->commandCount - 1; j++)
      {
        device->commands[j] = device->commands[j + 1];
      }
      device->commandCount--;

      // Save to EEPROM
      saveToEEPROM();

      DEBUG_PRINTLN("Removed command: " + commandName + " from device: " + deviceName);
      return true;
    }
  }

  DEBUG_PRINTLN("ERROR: Command not found: " + commandName);
  return false;
}

IRCommand *DeviceManager::getCommand(const String &deviceName, const String &commandName)
{
  Device *device = getDevice(deviceName);
  if (!device)
  {
    return nullptr;
  }

  for (uint8_t i = 0; i < device->commandCount; i++)
  {
    if (device->commands[i].name == commandName)
    {
      return &device->commands[i];
    }
  }

  return nullptr;
}

String DeviceManager::getDeviceList()
{
  DynamicJsonDocument doc(2048);
  JsonArray deviceArray = doc.createNestedArray("devices");

  for (uint8_t i = 0; i < deviceCount; i++)
  {
    JsonObject deviceObj = deviceArray.createNestedObject();
    deviceObj["name"] = devices[i].name;
    deviceObj["type"] = devices[i].type;
    deviceObj["manufacturer"] = devices[i].manufacturer;
    deviceObj["model"] = devices[i].model;
    deviceObj["commandCount"] = devices[i].commandCount;
  }

  doc["count"] = deviceCount;

  String result;
  serializeJson(doc, result);
  return result;
}

String DeviceManager::getCommandList(const String &deviceName)
{
  Device *device = getDevice(deviceName);
  if (!device)
  {
    return "{\"error\":\"Device not found\"}";
  }

  DynamicJsonDocument doc(2048);
  JsonArray commandArray = doc.createNestedArray("commands");

  for (uint8_t i = 0; i < device->commandCount; i++)
  {
    JsonObject commandObj = commandArray.createNestedObject();
    commandObj["name"] = device->commands[i].name;
    commandObj["description"] = device->commands[i].description;
  }

  doc["device"] = deviceName;
  doc["count"] = device->commandCount;

  String result;
  serializeJson(doc, result);
  return result;
}

String DeviceManager::exportDevices()
{
  DynamicJsonDocument doc(4096);
  JsonArray deviceArray = doc.createNestedArray("devices");

  for (uint8_t i = 0; i < deviceCount; i++)
  {
    JsonObject deviceObj = deviceArray.createNestedObject();
    deviceObj["name"] = devices[i].name;
    deviceObj["type"] = devices[i].type;
    deviceObj["manufacturer"] = devices[i].manufacturer;
    deviceObj["model"] = devices[i].model;

    JsonArray commandArray = deviceObj.createNestedArray("commands");
    for (uint8_t j = 0; j < devices[i].commandCount; j++)
    {
      JsonObject commandObj = commandArray.createNestedObject();
      commandObj["name"] = devices[i].commands[j].name;
      commandObj["description"] = devices[i].commands[j].description;
      // Note: IR code data would be serialized here in a real implementation
    }
  }

  doc["version"] = "1.0";
  doc["exported"] = millis();

  String result;
  serializeJson(doc, result);
  return result;
}

bool DeviceManager::importDevices(const String &jsonData)
{
  DynamicJsonDocument doc(4096);
  DeserializationError error = deserializeJson(doc, jsonData);

  if (error)
  {
    DEBUG_PRINTLN("ERROR: Failed to parse import data");
    return false;
  }

  // Reset current devices
  deviceCount = 0;

  JsonArray deviceArray = doc["devices"];
  for (JsonObject deviceObj : deviceArray)
  {
    if (deviceCount >= MAX_DEVICES)
      break;

    Device device;
    device.name = deviceObj["name"].as<String>();
    device.type = deviceObj["type"].as<String>();
    device.manufacturer = deviceObj["manufacturer"].as<String>();
    device.model = deviceObj["model"].as<String>();
    device.commandCount = 0;

    JsonArray commandArray = deviceObj["commands"];
    for (JsonObject commandObj : commandArray)
    {
      if (device.commandCount >= MAX_COMMANDS)
        break;

      IRCommand command;
      memset(&command, 0, sizeof(IRCommand)); // Initialize all fields
      command.name = commandObj["name"].as<String>();
      command.description = commandObj["description"].as<String>();
      // Initialize IR code data
      command.code.protocol = decode_type_t::UNKNOWN;
      command.code.data = 0;
      command.code.bits = 0;
      // Note: IR code data would be deserialized here

      device.commands[device.commandCount] = command;
      device.commandCount++;
    }

    devices[deviceCount] = device;
    deviceCount++;
  }

  // Save imported data
  saveToEEPROM();

  DEBUG_PRINTLN("Imported " + String(deviceCount) + " devices");
  return true;
}

bool DeviceManager::deviceExists(const String &deviceName)
{
  return getDevice(deviceName) != nullptr;
}

bool DeviceManager::commandExists(const String &deviceName, const String &commandName)
{
  return getCommand(deviceName, commandName) != nullptr;
}

void DeviceManager::printDeviceInfo(const Device &device)
{
  DEBUG_PRINTLN("Device: " + device.name);
  DEBUG_PRINTLN("  Type: " + device.type);
  DEBUG_PRINTLN("  Manufacturer: " + device.manufacturer);
  DEBUG_PRINTLN("  Model: " + device.model);
  DEBUG_PRINTLN("  Commands: " + String(device.commandCount));
}

String DeviceManager::getStatus()
{
  DynamicJsonDocument doc(256);
  doc["loaded"] = dataLoaded;
  doc["deviceCount"] = deviceCount;
  doc["maxDevices"] = MAX_DEVICES;
  doc["eepromSize"] = EEPROM_SIZE;

  String result;
  serializeJson(doc, result);
  return result;
}

void DeviceManager::reset()
{
  DEBUG_PRINTLN("Resetting Device Manager...");
  deviceCount = 0;
  clearEEPROM();
  DEBUG_PRINTLN("Device Manager reset complete");
}

void DeviceManager::saveToEEPROM()
{
  DEBUG_PRINTLN("Saving devices to EEPROM...");

  int address = CONFIG_ADDR;

  // Write header
  EEPROM.write(address++, 0xAA); // Magic byte 1
  EEPROM.write(address++, 0x55); // Magic byte 2
  EEPROM.write(address++, deviceCount);

  // Write devices
  for (uint8_t i = 0; i < deviceCount; i++)
  {
    // Write device name length and data
    uint8_t nameLen = devices[i].name.length();
    EEPROM.write(address++, nameLen);
    for (uint8_t j = 0; j < nameLen; j++)
    {
      EEPROM.write(address++, devices[i].name[j]);
    }

    // Write other string fields similarly
    uint8_t typeLen = devices[i].type.length();
    EEPROM.write(address++, typeLen);
    for (uint8_t j = 0; j < typeLen; j++)
    {
      EEPROM.write(address++, devices[i].type[j]);
    }

    // Write command count
    EEPROM.write(address++, devices[i].commandCount);

    // Note: In a full implementation, all device data would be serialized here
  }

  EEPROM.commit();
  DEBUG_PRINTLN("EEPROM save complete");
}

bool DeviceManager::loadFromEEPROM()
{
  DEBUG_PRINTLN("Loading devices from EEPROM...");

  int address = CONFIG_ADDR;

  // Check magic bytes
  if (EEPROM.read(address++) != 0xAA || EEPROM.read(address++) != 0x55)
  {
    DEBUG_PRINTLN("No valid EEPROM data found");
    return false;
  }

  // Read device count
  deviceCount = EEPROM.read(address++);
  if (deviceCount > MAX_DEVICES)
  {
    DEBUG_PRINTLN("Invalid device count in EEPROM");
    deviceCount = 0;
    return false;
  }

  // Read devices (simplified implementation)
  for (uint8_t i = 0; i < deviceCount; i++)
  {
    // Read device name
    uint8_t nameLen = EEPROM.read(address++);
    devices[i].name = "";
    for (uint8_t j = 0; j < nameLen; j++)
    {
      devices[i].name += (char)EEPROM.read(address++);
    }

    // Read device type
    uint8_t typeLen = EEPROM.read(address++);
    devices[i].type = "";
    for (uint8_t j = 0; j < typeLen; j++)
    {
      devices[i].type += (char)EEPROM.read(address++);
    }

    // Read command count
    devices[i].commandCount = EEPROM.read(address++);

    // Note: In a full implementation, all device data would be deserialized here
  }

  DEBUG_PRINTLN("EEPROM load complete");
  return true;
}

void DeviceManager::clearEEPROM()
{
  DEBUG_PRINTLN("Clearing EEPROM...");
  for (int i = 0; i < EEPROM_SIZE; i++)
  {
    EEPROM.write(i, 0xFF);
  }
  EEPROM.commit();
  DEBUG_PRINTLN("EEPROM cleared");
}