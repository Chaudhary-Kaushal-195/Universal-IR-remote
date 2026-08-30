/**
 * BLE Manager Implementation
 */

#include "ble_manager.h"
#include <ArduinoJson.h>

// Server Callbacks Implementation
void BLEManager::ServerCallbacks::onConnect(NimBLEServer *pServer)
{
    manager->deviceConnected = true;
    manager->oldDeviceConnected = manager->deviceConnected;
    Serial.println("BLE Client connected");
}

void BLEManager::ServerCallbacks::onDisconnect(NimBLEServer *pServer)
{
    manager->deviceConnected = false;
    Serial.println("BLE Client disconnected");
    // Start advertising again
    pServer->startAdvertising();
}

// Characteristic Callbacks Implementation
void BLEManager::CharacteristicCallbacks::onWrite(NimBLECharacteristic *pCharacteristic)
{
    std::string value = pCharacteristic->getValue();
    if (value.length() > 0 && manager->commandCallback)
    {
        String command = String(value.c_str());
        Serial.println("Received BLE command: " + command);
        manager->commandCallback(command);
    }
}

BLEManager::BLEManager() : pServer(nullptr),
                           pService(nullptr),
                           pCharacteristic(nullptr),
                           deviceConnected(false),
                           oldDeviceConnected(false),
                           serverCallbacks(nullptr),
                           charCallbacks(nullptr)
{
}

BLEManager::~BLEManager()
{
    if (serverCallbacks)
        delete serverCallbacks;
    if (charCallbacks)
        delete charCallbacks;
}

bool BLEManager::begin()
{
    DEBUG_PRINTLN("Initializing BLE Manager...");

    // Initialize BLE device
    NimBLEDevice::init(DEVICE_NAME);

    // Create BLE server
    pServer = NimBLEDevice::createServer();
    serverCallbacks = new ServerCallbacks(this);
    pServer->setCallbacks(serverCallbacks);

    // Create BLE service
    pService = pServer->createService(SERVICE_UUID);

    // Create BLE characteristic
    pCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID,
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::NOTIFY);

    charCallbacks = new CharacteristicCallbacks(this);
    pCharacteristic->setCallbacks(charCallbacks);

    // Start the service
    pService->start();

    // Start advertising
    startAdvertising();

    DEBUG_PRINTLN("BLE Manager initialized successfully");
    return true;
}

void BLEManager::update()
{
    // Handle connection state changes
    if (!deviceConnected && oldDeviceConnected)
    {
        delay(500);                  // Give the bluetooth stack time to get ready
        pServer->startAdvertising(); // Restart advertising
        Serial.println("BLE advertising restarted");
        oldDeviceConnected = deviceConnected;
    }

    // Handle new connections
    if (deviceConnected && !oldDeviceConnected)
    {
        oldDeviceConnected = deviceConnected;
    }
}

bool BLEManager::sendResponse(const String &response)
{
    if (!deviceConnected || !pCharacteristic)
    {
        return false;
    }

    DEBUG_PRINT("Sending BLE response: ");
    DEBUG_PRINTLN(response);

    pCharacteristic->setValue(response.c_str());
    pCharacteristic->notify();
    return true;
}

bool BLEManager::sendNotification(const String &notification)
{
    return sendResponse(notification);
}

void BLEManager::setCommandCallback(std::function<void(const String &)> callback)
{
    commandCallback = callback;
}

void BLEManager::disconnect()
{
    if (pServer && deviceConnected)
    {
        pServer->disconnect(pServer->getConnectedCount() - 1);
    }
}

String BLEManager::getDeviceAddress()
{
    return String(NimBLEDevice::getAddress().toString().c_str());
}

String BLEManager::getStatus()
{
    DynamicJsonDocument doc(256);
    doc["connected"] = deviceConnected;
    doc["advertising"] = pServer ? pServer->getAdvertising()->isAdvertising() : false;
    doc["address"] = getDeviceAddress();

    String result;
    serializeJson(doc, result);
    return result;
}

void BLEManager::startAdvertising()
{
    if (pServer)
    {
        NimBLEAdvertising *pAdvertising = pServer->getAdvertising();
        pAdvertising->addServiceUUID(SERVICE_UUID);
        pAdvertising->setScanResponse(true);
        pAdvertising->setMinPreferred(0x06); // Functions that help with iPhone connections issue
        pAdvertising->setMinPreferred(0x12);
        pAdvertising->start();
        Serial.println("BLE advertising started");
    }
}

void BLEManager::stopAdvertising()
{
    if (pServer)
    {
        pServer->getAdvertising()->stop();
        Serial.println("BLE advertising stopped");
    }
}