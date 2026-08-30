/**
 * Command Processor Implementation
 */

#include "command_processor.h"

CommandProcessor::CommandProcessor() : irManager(nullptr), bleManager(nullptr), deviceManager(nullptr)
{
}

CommandProcessor::~CommandProcessor()
{
}

void CommandProcessor::begin(IRManager *ir, BLEManager *ble, DeviceManager *device)
{
  irManager = ir;
  bleManager = ble;
  deviceManager = device;

  DEBUG_PRINTLN("Command Processor initialized");
}

void CommandProcessor::update()
{
  // Check for any pending operations or timeouts
  // Currently no periodic tasks required
}

void CommandProcessor::processCommand(const String &commandJson)
{
  DEBUG_PRINTLN("Processing command: " + commandJson);

  DynamicJsonDocument doc(1024);
  DeserializationError error = deserializeJson(doc, commandJson);

  if (error)
  {
    sendError("INVALID_JSON", "Failed to parse command JSON");
    return;
  }

  String command = doc["command"];
  if (command.isEmpty())
  {
    sendError("MISSING_COMMAND", "Command field is required");
    return;
  }

  // Route to appropriate handler
  if (command == CMD_LEARN)
  {
    handleLearnCommand(doc);
  }
  else if (command == CMD_TRANSMIT)
  {
    handleTransmitCommand(doc);
  }
  else if (command == CMD_LIST_DEVICES)
  {
    handleListDevicesCommand(doc);
  }
  else if (command == CMD_ADD_DEVICE)
  {
    handleAddDeviceCommand(doc);
  }
  else if (command == CMD_DELETE_DEVICE)
  {
    handleDeleteDeviceCommand(doc);
  }
  else if (command == CMD_GET_STATUS)
  {
    handleGetStatusCommand(doc);
  }
  else if (command == CMD_RESET)
  {
    handleResetCommand(doc);
  }
  else
  {
    sendError("UNKNOWN_COMMAND", "Command not recognized: " + command);
  }
}

void CommandProcessor::handleLearnCommand(const JsonDocument &cmd)
{
  DEBUG_PRINTLN("Handling LEARN command");

  if (!irManager)
  {
    sendError("IR_MANAGER_ERROR", "IR Manager not available");
    return;
  }

  if (!cmd.containsKey("parameters") || !cmd["parameters"].is<JsonObject>())
  {
    sendError("MISSING_PARAMETERS", "Parameters must be a valid object");
    return;
  }

  int timeout = cmd["parameters"]["timeout"] | IR_TIMEOUT_MS;

  if (irManager->startLearning())
  {
    DynamicJsonDocument responseData(256);
    responseData["timeout"] = timeout;
    responseData["status"] = "learning";

    sendResponse(RESP_OK, "IR learning started", &responseData);

    // Wait for learning completion or timeout
    unsigned long startTime = millis();
    while (irManager->isLearning() && (millis() - startTime < timeout))
    {
      irManager->update();
      delay(50);
    }

    if (irManager->hasLearnedCode())
    {
      IRCode learnedCode = irManager->getLearnedCode();
      DynamicJsonDocument learnedData(512);
      learnedData["protocol"] = typeToString(learnedCode.protocol);
      learnedData["value"] = String(learnedCode.data, HEX);
      learnedData["bits"] = learnedCode.bits;

      sendResponse(RESP_OK, "IR code learned successfully", &learnedData);
    }
    else
    {
      sendResponse(RESP_TIMEOUT, "Learning timeout - no IR signal received");
    }
  }
  else
  {
    sendError("LEARN_ERROR", "Failed to start IR learning");
  }
}

void CommandProcessor::handleTransmitCommand(const JsonDocument &cmd)
{
  DEBUG_PRINTLN("Handling TRANSMIT command");

  if (!irManager || !deviceManager)
  {
    sendError("MANAGER_ERROR", "Required managers not available");
    return;
  }

  const String requiredFields[] = {"device", "command"};
  if (!validateCommand(cmd, requiredFields, 2))
  {
    sendError("MISSING_PARAMETERS", "Device and command parameters required");
    return;
  }

  String deviceName = cmd["parameters"]["device"];
  String commandName = cmd["parameters"]["command"];

  IRCommand *irCommand = deviceManager->getCommand(deviceName, commandName);
  if (!irCommand)
  {
    sendError("COMMAND_NOT_FOUND", "Command '" + commandName + "' not found for device '" + deviceName + "'");
    return;
  }

  if (irManager->transmitCode(irCommand->code))
  {
    DynamicJsonDocument responseData(256);
    responseData["device"] = deviceName;
    responseData["command"] = commandName;

    sendResponse(RESP_OK, "IR command transmitted successfully", &responseData);
  }
  else
  {
    sendError("TRANSMIT_ERROR", "Failed to transmit IR command");
  }
}

void CommandProcessor::handleListDevicesCommand(const JsonDocument &cmd)
{
  DEBUG_PRINTLN("Handling LIST_DEVICES command");

  if (!deviceManager)
  {
    sendError("DEVICE_MANAGER_ERROR", "Device Manager not available");
    return;
  }

  String deviceListJson = deviceManager->getDeviceList();
  DynamicJsonDocument deviceList(2048);
  deserializeJson(deviceList, deviceListJson);

  sendResponse(RESP_OK, "Device list retrieved", &deviceList);
}

void CommandProcessor::handleAddDeviceCommand(const JsonDocument &cmd)
{
  DEBUG_PRINTLN("Handling ADD_DEVICE command");

  if (!deviceManager)
  {
    sendError("DEVICE_MANAGER_ERROR", "Device Manager not available");
    return;
  }

  const String requiredFields[] = {"name", "type"};
  if (!validateCommand(cmd, requiredFields, 2))
  {
    sendError("MISSING_PARAMETERS", "Name and type parameters required");
    return;
  }

  Device device;
  device.name = cmd["parameters"]["name"].as<String>();
  device.type = cmd["parameters"]["type"].as<String>();
  device.manufacturer = cmd["parameters"]["manufacturer"] | String("");
  device.model = cmd["parameters"]["model"] | String("");
  device.commandCount = 0;

  if (deviceManager->addDevice(device))
  {
    DynamicJsonDocument responseData(256);
    responseData["device"] = device.name;
    responseData["type"] = device.type;

    sendResponse(RESP_OK, "Device added successfully", &responseData);
  }
  else
  {
    sendError("ADD_DEVICE_ERROR", "Failed to add device (may already exist or storage full)");
  }
}

void CommandProcessor::handleDeleteDeviceCommand(const JsonDocument &cmd)
{
  DEBUG_PRINTLN("Handling DELETE_DEVICE command");

  if (!deviceManager)
  {
    sendError("DEVICE_MANAGER_ERROR", "Device Manager not available");
    return;
  }

  const String requiredFields[] = {"name"};
  if (!validateCommand(cmd, requiredFields, 1))
  {
    sendError("MISSING_PARAMETERS", "Name parameter required");
    return;
  }

  String deviceName = cmd["parameters"]["name"];

  if (deviceManager->removeDevice(deviceName))
  {
    DynamicJsonDocument responseData(256);
    responseData["device"] = deviceName;

    sendResponse(RESP_OK, "Device deleted successfully", &responseData);
  }
  else
  {
    sendError("DELETE_DEVICE_ERROR", "Failed to delete device (not found)");
  }
}

void CommandProcessor::handleGetStatusCommand(const JsonDocument &cmd)
{
  DEBUG_PRINTLN("Handling GET_STATUS command");

  DynamicJsonDocument statusData(512);

  if (irManager)
  {
    DynamicJsonDocument irStatus(256);
    deserializeJson(irStatus, irManager->getStatus());
    statusData["ir"] = irStatus;
  }

  if (bleManager)
  {
    DynamicJsonDocument bleStatus(256);
    deserializeJson(bleStatus, bleManager->getStatus());
    statusData["ble"] = bleStatus;
  }

  if (deviceManager)
  {
    DynamicJsonDocument deviceStatus(256);
    deserializeJson(deviceStatus, deviceManager->getStatus());
    statusData["devices"] = deviceStatus;
  }

  statusData["firmware"] = FIRMWARE_VERSION;
  statusData["uptime"] = millis();
  statusData["freeHeap"] = ESP.getFreeHeap();

  sendResponse(RESP_OK, "System status retrieved", &statusData);
}

void CommandProcessor::handleResetCommand(const JsonDocument &cmd)
{
  DEBUG_PRINTLN("Handling RESET command");

  String resetType = "soft"; // Default value
  if (cmd.containsKey("parameters"))
  {
    resetType = cmd["parameters"]["type"] | String("soft");
  }

  if (resetType == "factory")
  {
    // Factory reset - clear all stored data
    if (deviceManager)
    {
      deviceManager->reset();
    }
    sendResponse(RESP_OK, "Factory reset completed");
    delay(1000);
    ESP.restart();
  }
  else
  {
    // Soft reset - restart system
    sendResponse(RESP_OK, "System restart initiated");
    delay(1000);
    ESP.restart();
  }
}

void CommandProcessor::sendResponse(const String &status, const String &message, DynamicJsonDocument *data)
{
  DynamicJsonDocument response(1024);

  response["status"] = status;
  response["message"] = message;
  response["timestamp"] = millis();

  if (data != nullptr)
  {
    response["data"] = *data;
  }

  String responseJson;
  serializeJson(response, responseJson);

  if (bleManager)
  {
    bleManager->sendResponse(responseJson);
  }

  DEBUG_PRINTLN("Response sent: " + responseJson);
}

void CommandProcessor::sendError(const String &error, const String &details)
{
  DynamicJsonDocument errorData(256);
  errorData["error"] = error;
  if (!details.isEmpty())
  {
    errorData["details"] = details;
  }

  sendResponse(RESP_ERROR, "Command failed", &errorData);
}

bool CommandProcessor::validateCommand(const JsonDocument &cmd, const String requiredFields[], int fieldCount)
{
  if (!cmd.containsKey("parameters"))
  {
    DEBUG_PRINTLN("Missing parameters object");
    return false;
  }

  if (!cmd["parameters"].is<JsonObject>())
  {
    DEBUG_PRINTLN("Parameters is not an object");
    return false;
  }

  for (int i = 0; i < fieldCount; i++)
  {
    if (!cmd["parameters"].containsKey(requiredFields[i]))
    {
      DEBUG_PRINTLN("Missing required field: " + requiredFields[i]);
      return false;
    }
  }

  return true;
}

String CommandProcessor::getStatus()
{
  DynamicJsonDocument doc(256);
  doc["initialized"] = (irManager != nullptr && bleManager != nullptr && deviceManager != nullptr);
  doc["commandsProcessed"] = 0; // Could add a counter in future

  String result;
  serializeJson(doc, result);
  return result;
}