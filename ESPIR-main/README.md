# ESPIR - ESP32 IR Blaster System

A comprehensive IR blaster project consisting of ESP32 firmware, Android application, and hardware design for controlling infrared devices.

## Project Overview

ESPIR is a professional-grade IR blaster system that allows you to control infrared devices (TVs, air conditioners, audio systems, etc.) using an Android smartphone connected via Bluetooth Low Energy (BLE) to a custom ESP32-based hardware device.

## Project Components

### 1. ESPIR-FW (Firmware)
- **Location**: `./` (root directory)
- **Platform**: ESP32 with Arduino framework
- **Features**:
  - IR transmission and reception using IRremoteESP8266 library
  - BLE connectivity for Android app communication
  - Device configuration and IR code storage
  - Real-time IR signal capture and replay

### 2. ESPIR-App (Android Application)
- **Location**: `./ESPIR-App/`
- **Platform**: Android (Java/Kotlin)
- **Features**:
  - BLE device discovery and connection
  - IR remote control interface
  - Device management and configuration
  - IR code learning and programming
  - Custom button layouts and device profiles

### 3. ESPIR-HW (Hardware Design)
- **Location**: `./ESPIR-HW/`
- **Components**:
  - ESP32 development board
  - IR LED with MOSFET driver circuit
  - IR receiver module
  - Power management
  - Enclosure design

## Quick Start

### Prerequisites
- ESP32 development board (ESP32-WROOM-32 recommended)
- Android device with BLE support (Android 5.0+)
- Arduino IDE or PlatformIO
- Android Studio (for app development)

### Firmware Setup
1. Install Arduino IDE and ESP32 board support
2. Install required libraries (see `./platformio.ini`)
3. Flash the firmware to your ESP32
4. Wire the hardware according to the schematic in `./ESPIR-HW/`

### Android App Setup
1. Open `./ESPIR-App/` in Android Studio
2. Build and install the APK on your Android device
3. Pair with the ESP32 device via BLE

## Architecture

```
┌─────────────────┐    BLE     ┌─────────────────┐    IR     ┌─────────────────┐
│   Android App   │ ◄────────► │   ESP32 FW      │ ◄───────► │   IR Devices    │
│   (ESPIR-App)   │            │   (ESPIR-FW)    │           │   (TV, AC, etc) │
└─────────────────┘            └─────────────────┘           └─────────────────┘
        │                               │
        │                               │
        ▼                               ▼
┌─────────────────┐            ┌─────────────────┐
│  UI & Controls  │            │  Hardware       │
│  BLE Manager    │            │  (ESPIR-HW)     │
│  Device Profiles│            │  IR TX/RX       │
└─────────────────┘            │  Power & I/O    │
                               └─────────────────┘
```

## Development Status

- [x] Project structure created
- [ ] ESP32 firmware development
- [ ] Android application development  
- [ ] Hardware design documentation
- [ ] Integration testing
- [ ] Documentation completion

## Contributing

This is a personal project by the development team. Please refer to individual component READMEs for specific development guidelines.

## License

This project is proprietary. All rights reserved.

## Support

For technical questions and support, please refer to the documentation in the `./docs/` directory.