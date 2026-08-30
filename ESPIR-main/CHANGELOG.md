# Changelog

All notable changes to the ESPIR project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial project setup and documentation

## [1.0.0] - 2025-10-05

### Added

#### ESP32 Firmware (ESPIR-FW)
- Complete Arduino/PlatformIO project setup
- IR transmission and reception using IRremoteESP8266 library
- BLE connectivity using NimBLE-Arduino library
- Device profile management with EEPROM storage
- JSON command protocol processing
- Modular architecture with separate managers
- Comprehensive error handling and logging
- Status LED indication
- Watchdog timer protection

#### Android Application (ESPIR-App)
- Modern Kotlin-based Android app
- Material Design 3 UI components
- BLE connectivity using Nordic Semiconductor's BLE library
- Room database for local data storage
- MVVM architecture with ViewModels and LiveData
- Device and command management interfaces
- IR learning mode functionality
- Offline operation capability

#### Hardware Design (ESPIR-HW)
- Complete circuit schematics for IR transmitter and receiver
- MOSFET-based IR LED driver circuit
- Component specifications and bill of materials
- PCB layout guidelines and considerations
- Assembly instructions and testing procedures
- Safety considerations and thermal management
- Power requirements and consumption analysis

#### Build System
- Comprehensive Makefile with 50+ targets
- Automated build processes for all components
- Cross-platform development support
- Testing and quality assurance tools
- Release packaging and distribution
- CI/CD pipeline configuration

#### Documentation
- Complete system architecture documentation
- Development setup and API guides
- Hardware assembly and testing procedures
- Design decisions and customization guidelines
- Contributing guidelines and code style standards
- Security policy and vulnerability reporting

### Technical Details

#### Supported IR Protocols
- NEC, Sony, RC5, RC6
- Raw IR signal capture and replay
- 38kHz carrier frequency support
- Configurable timing parameters

#### BLE Communication
- Service UUID: `12345678-1234-1234-1234-123456789abc`
- Characteristic UUID: `87654321-4321-4321-4321-cba987654321`
- JSON command protocol
- Error handling and status reporting

#### Hardware Specifications
- ESP32-WROOM-32 microcontroller
- IR LED with MOSFET driver (GPIO4)
- IR receiver module (GPIO5)
- Status LED (GPIO2)
- 3.3V operation with USB power
- ~225mA maximum current consumption

#### Dependencies
- **Firmware**: IRremoteESP8266 v2.8.6+, NimBLE-Arduino v1.4.0+, ArduinoJson v6.21.3+
- **Android**: Material Components, Room Database, BLE libraries
- **Build**: PlatformIO, Android Gradle Plugin, Make

### Known Issues
- Android BLE permissions may require manual enabling on some devices
- IR range varies depending on LED current and ambient conditions
- Some IR protocols may require timing adjustments for specific devices

### Migration Guide
This is the initial release, no migration required.

## Version History

- **v1.0.0** (2025-10-05): Initial complete implementation
- Project started: October 4, 2025
- Repository initialized: October 5, 2025

---

## Legend

- 🎉 **Added**: New features
- 🔧 **Changed**: Changes in existing functionality  
- 🚨 **Deprecated**: Soon-to-be removed features
- 🗑️ **Removed**: Removed features
- 🐛 **Fixed**: Bug fixes
- 🔒 **Security**: Security improvements