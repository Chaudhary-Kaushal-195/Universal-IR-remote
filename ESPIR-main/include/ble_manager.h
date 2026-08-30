/**
 * BLE Manager - Handles Bluetooth Low Energy communication
 */

#ifndef BLE_MANAGER_H
#define BLE_MANAGER_H

#include <Arduino.h>
#include <NimBLEDevice.h>
#include <NimBLEServer.h>
#include <NimBLEUtils.h>
#include <NimBLEDescriptor.h>
#include "config.h"

class BLEManager
{
private:
    NimBLEServer *pServer;
    NimBLEService *pService;
    NimBLECharacteristic *pCharacteristic;
    bool deviceConnected;
    bool oldDeviceConnected;
    std::function<void(const String &)> commandCallback;

    class ServerCallbacks : public NimBLEServerCallbacks
    {
        BLEManager *manager;

    public:
        ServerCallbacks(BLEManager *mgr) : manager(mgr) {}
        void onConnect(NimBLEServer *pServer);
        void onDisconnect(NimBLEServer *pServer);
    };

    class CharacteristicCallbacks : public NimBLECharacteristicCallbacks
    {
        BLEManager *manager;

    public:
        CharacteristicCallbacks(BLEManager *mgr) : manager(mgr) {}
        void onWrite(NimBLECharacteristic *pCharacteristic);
    };

    ServerCallbacks *serverCallbacks;
    CharacteristicCallbacks *charCallbacks;

public:
    BLEManager();
    ~BLEManager();

    bool begin();
    void update();

    // Connection management
    bool isConnected() { return deviceConnected; }
    void disconnect();
    String getDeviceAddress();

    // Communication methods
    bool sendResponse(const String &response);
    bool sendNotification(const String &notification);
    void setCommandCallback(std::function<void(const String &)> callback);

    // Status methods
    String getStatus();
    void startAdvertising();
    void stopAdvertising();

    friend class ServerCallbacks;
    friend class CharacteristicCallbacks;
};

#endif // BLE_MANAGER_H