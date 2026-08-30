/**
 * ESPIR-FW Configuration Header
 *
 * Contains all configuration constants and pin definitions
 */

#ifndef CONFIG_H
#define CONFIG_H

// Firmware version
#define FIRMWARE_VERSION "1.0.0"

// Pin Definitions
#define IR_TRANSMIT_PIN 4 // GPIO4 - IR LED (with MOSFET driver)
#define IR_RECEIVE_PIN 5  // GPIO5 - IR Receiver
#define STATUS_LED_PIN 2  // GPIO2 - Built-in LED for status

// IR Configuration
#define IR_FREQUENCY 38000   // 38 kHz carrier frequency
#define IR_DUTY_CYCLE 33     // 33% duty cycle
#define IR_TIMEOUT_MS 15000  // 15 second timeout for learning
#define MAX_IR_CODE_SIZE 512 // Maximum IR code length

// BLE Configuration
#define DEVICE_NAME "ESPIR-Device"
#define SERVICE_UUID "12345678-1234-1234-1234-123456789abc"
#define CHARACTERISTIC_UUID "87654321-4321-4321-4321-cba987654321"
#define BLE_TIMEOUT_MS 30000 // 30 second BLE timeout

// Device Management
#define MAX_DEVICES 50     // Maximum number of stored devices
#define MAX_DEVICE_NAME 32 // Maximum device name length
#define MAX_COMMANDS 20    // Maximum commands per device

// Memory Configuration
#define EEPROM_SIZE 4096 // EEPROM size for device storage
#define CONFIG_ADDR 0    // Configuration start address

// Debug Configuration
#ifdef DEBUG
#define DEBUG_PRINT(x) Serial.print(x)
#define DEBUG_PRINTLN(x) Serial.println(x)
#else
#define DEBUG_PRINT(x)
#define DEBUG_PRINTLN(x)
#endif

// Protocol Commands
#define CMD_LEARN "LEARN"
#define CMD_TRANSMIT "TRANSMIT"
#define CMD_LIST_DEVICES "LIST_DEVICES"
#define CMD_ADD_DEVICE "ADD_DEVICE"
#define CMD_DELETE_DEVICE "DELETE_DEVICE"
#define CMD_GET_STATUS "GET_STATUS"
#define CMD_RESET "RESET"

// Response Codes
#define RESP_OK "OK"
#define RESP_ERROR "ERROR"
#define RESP_TIMEOUT "TIMEOUT"
#define RESP_NOT_FOUND "NOT_FOUND"
#define RESP_INVALID "INVALID"

#endif // CONFIG_H