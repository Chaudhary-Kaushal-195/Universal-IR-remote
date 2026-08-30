/**
 * ESPIR-FW: ESP32 IR Blaster Firmware
 *
 * Main application entry point
 * Handles initialization of all subsystems:
 * - IR transmitter and receiver
 * - BLE communication
 * - Device configuration
 * - Command processing
 *
 * Author: ESPIR Development Team
 * License: Proprietary
 */

#include <Arduino.h>
#include <ArduinoJson.h>
#include "config.h"
#include "ir_manager.h"
#include "ble_manager.h"
#include "device_manager.h"
#include "command_processor.h"

// Global instances
IRManager irManager;
BLEManager bleManager;
DeviceManager deviceManager;
CommandProcessor cmdProcessor;

void setup()
{
    Serial.begin(115200);
    while (!Serial)
    {
        delay(10);
    }

    Serial.println("ESPIR-FW Starting...");
    Serial.println("Version: " + String(FIRMWARE_VERSION));
    Serial.println("Build: " + String(__DATE__) + " " + String(__TIME__));

    // Initialize LED for status indication
    pinMode(STATUS_LED_PIN, OUTPUT);
    digitalWrite(STATUS_LED_PIN, LOW);

    // Initialize all managers
    if (!irManager.begin())
    {
        Serial.println("ERROR: Failed to initialize IR Manager");
        while (1)
        {
            digitalWrite(STATUS_LED_PIN, HIGH);
            delay(200);
            digitalWrite(STATUS_LED_PIN, LOW);
            delay(200);
        }
    }

    if (!bleManager.begin())
    {
        Serial.println("ERROR: Failed to initialize BLE Manager");
        while (1)
        {
            digitalWrite(STATUS_LED_PIN, HIGH);
            delay(500);
            digitalWrite(STATUS_LED_PIN, LOW);
            delay(500);
        }
    }

    if (!deviceManager.begin())
    {
        Serial.println("ERROR: Failed to initialize Device Manager");
        while (1)
        {
            digitalWrite(STATUS_LED_PIN, HIGH);
            delay(1000);
            digitalWrite(STATUS_LED_PIN, LOW);
            delay(1000);
        }
    }

    cmdProcessor.begin(&irManager, &bleManager, &deviceManager);

    // Set up BLE command callback
    bleManager.setCommandCallback([](const String &command)
                                  { cmdProcessor.processCommand(command); });

    Serial.println("ESPIR-FW Ready!");
    digitalWrite(STATUS_LED_PIN, HIGH);
}

void loop()
{
    // Update all managers
    bleManager.update();
    irManager.update();
    deviceManager.update();
    cmdProcessor.update();

    // Yield to allow other tasks
    yield();
}