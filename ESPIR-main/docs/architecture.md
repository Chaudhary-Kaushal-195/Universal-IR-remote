# ESPIR System Architecture

## Overview
The ESPIR (ESP32 IR Blaster) system is a comprehensive solution for infrared device control consisting of three main components working together to provide seamless IR device management through a mobile interface.

## System Components

### 1. ESPIR-FW (Firmware)
**Platform**: ESP32 with Arduino Framework  
**Primary Functions**:
- IR signal transmission and reception
- BLE communication with Android app
- Device profile and command storage
- Real-time command processing

### 2. ESPIR-App (Android Application)
**Platform**: Android (API 21+)  
**Primary Functions**:
- User interface for device control
- BLE device discovery and communication
- Device configuration and management
- IR code learning and organization

### 3. ESPIR-HW (Hardware)
**Platform**: ESP32 Development Board  
**Primary Functions**:
- IR signal conditioning and amplification
- Power management and regulation
- Physical device enclosure and interfaces

## Communication Architecture

```
┌─────────────────────────────────────────┐
│                ESPIR-App                │
│           (Android Client)              │
│  ┌─────────────┐  ┌─────────────────┐   │
│  │     UI      │  │   BLE Client    │   │
│  │ Controller  │  │    Manager      │   │
│  └─────────────┘  └─────────────────┘   │
└─────────────┬───────────────────────────┘
              │ BLE Communication
              │ (JSON over BLE GATT)
              ▼
┌─────────────────────────────────────────┐
│               ESPIR-FW                  │
│           (ESP32 Firmware)              │
│  ┌─────────────┐  ┌─────────────────┐   │
│  │ BLE Server  │  │ Command         │   │
│  │   Manager   │  │ Processor       │   │
│  └─────────────┘  └─────────────────┘   │
│  ┌─────────────┐  ┌─────────────────┐   │
│  │ IR Manager  │  │ Device          │   │
│  │             │  │ Manager         │   │
│  └─────────────┘  └─────────────────┘   │
└─────────────┬───────────────────────────┘
              │ IR Signals
              ▼
┌─────────────────────────────────────────┐
│              Target Devices             │
│    (TVs, Air Conditioners, etc.)       │
└─────────────────────────────────────────┘
```

## Data Flow Architecture

### 1. Command Execution Flow
```
User Input → Android UI → BLE Command → ESP32 Processing → IR Transmission → Device Control
```

### 2. IR Learning Flow
```
IR Signal → ESP32 Reception → Code Processing → BLE Response → Android Storage → User Confirmation
```

### 3. Device Management Flow
```
User Config → Android Validation → BLE Transfer → ESP32 Storage → EEPROM Persistence
```

## Protocol Specifications

### BLE Communication Protocol

#### Service Configuration
- **Service UUID**: `12345678-1234-1234-1234-123456789abc`
- **Characteristic UUID**: `87654321-4321-4321-4321-cba987654321`
- **Properties**: Read, Write, Notify

#### Command Format (JSON)
```json
{
  "command": "COMMAND_NAME",
  "parameters": {
    "param1": "value1",
    "param2": "value2"
  },
  "requestId": "unique_identifier"
}
```

#### Response Format (JSON)
```json
{
  "status": "OK|ERROR|TIMEOUT",
  "message": "Human readable message",
  "data": {
    "result_data": "values"
  },
  "requestId": "matching_request_id"
}
```

### Supported Commands

#### Device Control Commands
- `TRANSMIT`: Send IR command to device
- `LEARN`: Start IR code learning mode
- `STOP_LEARN`: Stop learning mode

#### Device Management Commands
- `LIST_DEVICES`: Get stored device list
- `ADD_DEVICE`: Add new device profile
- `DELETE_DEVICE`: Remove device profile
- `LIST_COMMANDS`: Get device command list

#### System Commands
- `GET_STATUS`: Get system status
- `RESET`: Reset system to defaults

## Data Storage Architecture

### ESP32 EEPROM Layout
```
Address Range | Content
0x0000-0x0010 | Configuration header
0x0010-0x0100 | Device count and index
0x0100-0x1000 | Device profiles and IR codes
```

### Android SQLite Schema
```sql
-- Devices table
CREATE TABLE devices (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT,
    manufacturer TEXT,
    model TEXT
);

-- Commands table  
CREATE TABLE commands (
    id INTEGER PRIMARY KEY,
    device_id INTEGER,
    name TEXT NOT NULL,
    ir_code TEXT NOT NULL,
    FOREIGN KEY(device_id) REFERENCES devices(id)
);
```

## Security Considerations

### BLE Security
- Device pairing required for connection
- Command validation and sanitization
- Connection timeout and automatic disconnection
- Rate limiting for command processing

### Data Protection
- Local storage only (no cloud dependency)
- Encrypted device profiles (optional)
- Secure key exchange for sensitive operations

## Performance Specifications

### Response Times
- BLE command acknowledgment: <100ms
- IR transmission delay: <50ms
- Device discovery time: <5 seconds
- IR learning timeout: 15 seconds

### Range Specifications
- BLE communication range: 10-50 meters
- IR transmission range: 5-10 meters (line of sight)
- Supported IR frequencies: 36-40 kHz
- IR code storage: 50 devices × 20 commands

### Power Management
- ESP32 active current: ~120mA
- ESP32 sleep current: <10µA
- Battery operation: 8+ hours (2000mAh)
- USB power: Continuous operation

## Error Handling

### ESP32 Firmware
- Watchdog timer for system recovery
- Error logging to serial console
- Graceful degradation on component failure
- Automatic BLE reconnection attempts

### Android Application
- Connection state monitoring
- User notification for errors
- Offline mode for cached operations
- Crash reporting and recovery

## Scalability Considerations

### Firmware Extensibility
- Modular manager architecture
- Plugin system for new IR protocols
- OTA update capability
- Configuration management

### Application Extensibility
- MVP architecture for feature addition
- Device profile import/export
- Custom UI layouts
- Third-party integration APIs

## Testing Strategy

### Unit Testing
- Individual manager class testing
- Protocol validation testing
- IR code accuracy verification
- BLE communication reliability

### Integration Testing
- End-to-end command execution
- Multi-device scenario testing
- Range and interference testing
- Power consumption validation

### User Acceptance Testing
- Device compatibility testing
- User interface usability
- Performance benchmarking
- Real-world usage scenarios

## Deployment Architecture

### Development Environment
- ESP32: Arduino IDE or PlatformIO
- Android: Android Studio with Gradle
- Version control: Git with feature branches
- CI/CD: Automated build and testing

### Production Deployment
- Firmware: OTA updates via BLE
- Android: Google Play Store distribution
- Documentation: GitHub Pages hosting
- Support: Issue tracking and user guides

## Future Enhancements

### Planned Features
- WiFi connectivity for remote control
- Cloud device profile synchronization
- Voice control integration
- Machine learning for device optimization

### Hardware Improvements
- Integrated PCB design
- Multiple IR LED arrays for coverage
- Battery charging circuit
- Environmental sensors

### Software Enhancements
- Advanced device recognition
- Macro command sequencing
- Schedule-based automation
- Analytics and usage tracking