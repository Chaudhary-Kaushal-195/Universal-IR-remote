# ESPIR Design Decisions and Questions

## Overview
This document outlines key design decisions made during the ESPIR project implementation and identifies areas where you may want to customize or provide additional specifications.

## 📋 Design Decisions Made

### 1. Communication Protocol
**Decision**: JSON over BLE GATT characteristic
- **Rationale**: Human-readable, widely supported, easy to debug
- **Alternative**: Binary protocol for efficiency
- **Customization Needed**: You may want to optimize for speed vs. readability

### 2. IR Code Storage Format
**Decision**: JSON serialization with protocol-specific fields
- **Current Implementation**: Stores protocol type, value, bits, and raw data
- **Question for You**: Do you need support for specific IR protocols beyond NEC, Sony, RC5, RC6?

### 3. Device Profile Schema
**Decision**: Simple hierarchical structure (Device -> Commands)
- **Fields**: name, type, manufacturer, model, commands[]
- **Question for You**: Do you need additional metadata fields (room, category, icon, etc.)?

Answer: Nope, a simple heirarchy is fine.

### 4. BLE Security
**Decision**: Basic pairing without additional encryption
- **Current**: Standard BLE pairing
- **Question for You**: Do you need additional security layers for sensitive environments?

Answer: Nope, this will be used in the general public environment, no special security concerns. Ease of use is critical.

### 5. Error Handling Strategy
**Decision**: Graceful degradation with user feedback
- **Firmware**: Watchdog timers, status LEDs, serial logging
- **Android**: User notifications, offline mode, crash recovery
- **Question for You**: What specific error scenarios are most critical for your use case?

## 🔧 Implementation Details Requiring Your Input

### 1. IR Protocol Support
```cpp
// Current protocols supported:
// - NEC, Sony, RC5, RC6, Raw
// 
// Question: Do you need support for:
// - Samsung, LG, Panasonic protocols?
// - Custom/proprietary protocols?
// - Air conditioner protocols (complex state)?
```
Answer: The basics is fine.

### 2. Device Categories
```kotlin
// Current device types are free-form strings
// 
// Question: Should we use predefined categories?
// - Television, Audio, Climate, Lighting, etc.
// - Custom icons for each category?
// - Room-based organization?
```
Answer: Free-form is fine.

### 3. User Interface Preferences
```xml
<!-- Current UI is basic Material Design -->
<!-- 
Question: Do you prefer:
- Dark/Light theme preference?
- Custom color schemes?
- Advanced vs. Simple mode?
- Tablet-specific layouts?
-->
```

Answer: Dark/light toggle would be excellent. No other special features required. Priority should be ease of use.

### 4. Hardware Customizations
```
Current Design:
- Single IR LED with MOSFET driver
- 38kHz carrier frequency
- GPIO4 (TX), GPIO5 (RX), GPIO2 (Status)

Questions:
- Do you need multiple IR LEDs for wider coverage?
- Different carrier frequencies for specific devices?
- Additional I/O for sensors or displays?
- Battery operation requirements?
```

Answer: Not at this time. Some of these may be considered for the future.

### 5. Cloud Integration
```
Current: Local-only operation

Questions:
- Do you want cloud backup of device profiles?
- Remote control capability over internet?
- Device sharing between users?
- Analytics and usage tracking?
```

Answer: None of these are necessary at this time.

### 6. Advanced Features
```
Potential Enhancements:

1. Macro Commands
   - Sequence multiple IR commands
   - Delays between commands
   - Conditional logic

2. Voice Control
   - Integration with Google Assistant/Alexa
   - Voice command recognition

3. Automation
   - Time-based triggers
   - Sensor-based automation
   - Integration with home automation systems

4. Learning Intelligence
   - Automatic device recognition
   - Usage pattern learning
   - Command optimization

Question: Which of these features are priorities?
```

Answer: Priority should be given to basic functionality and ease of use.

## 📝 Configuration Templates

### Device Profile Template
Fill out this template for your specific devices:

```json
{
  "name": "YOUR_DEVICE_NAME",
  "type": "television|audio|climate|lighting|other",
  "manufacturer": "MANUFACTURER_NAME",
  "model": "MODEL_NUMBER",
  "room": "OPTIONAL_ROOM_NAME",
  "notes": "OPTIONAL_NOTES",
  "commands": [
    {
      "name": "power",
      "description": "Power on/off",
      "category": "basic",
      "icon": "power_button"
    }
  ]
}
```

### IR Protocol Requirements
Fill out for each device type you need to support:

```
Device Type: ________________
Manufacturer: _______________
IR Protocol: ________________
Carrier Frequency: __________kHz
Special Requirements: _______
Test Remote Model: __________
```

### UI Customization Preferences
```
Theme Preference: Light/Dark/Auto
Primary Color: ______________
Layout Style: Simple/Advanced
Default View: Devices/Rooms/Recent
Button Size: Small/Medium/Large
Haptic Feedback: Yes/No
```

## 🚀 Deployment Configuration

### Environment Settings
```ini
# Development Environment
DEBUG_MODE=true
LOG_LEVEL=verbose
BLE_TIMEOUT=30000
IR_TIMEOUT=15000

# Production Environment  
DEBUG_MODE=false
LOG_LEVEL=error
BLE_TIMEOUT=10000
IR_TIMEOUT=10000
```

### Build Configuration
```
Firmware Options:
- OTA Updates: Yes/No
- Deep Sleep: Yes/No
- Watchdog Timer: Yes/No
- Serial Debug: Yes/No

Android Options:
- Crash Reporting: Yes/No
- Analytics: Yes/No
- Auto-Updates: Yes/No
- Multiple Device Support: Yes/No
```

## 📋 Next Steps Checklist

Please review and provide specifications for:

- [ ] **IR Protocol Requirements**: List specific devices and protocols needed
- [ ] **UI/UX Preferences**: Specify theme, layout, and interaction preferences  
- [ ] **Hardware Customizations**: Any modifications to the basic circuit
- [ ] **Security Requirements**: Additional security measures if needed
- [ ] **Feature Priorities**: Which advanced features to implement first
- [ ] **Deployment Environment**: Development vs. production configurations
- [ ] **Testing Devices**: Specific remote controls and devices for testing
- [ ] **Integration Requirements**: Any external systems to integrate with

## 📞 Implementation Support

Once you've filled out the above specifications, the following can be implemented:

1. **Custom IR Protocol Support**: Add specific protocol handlers
2. **UI Customization**: Implement your preferred theme and layout
3. **Advanced Features**: Develop prioritized enhancement features
4. **Hardware Modifications**: Update schematics for any circuit changes
5. **Security Enhancements**: Implement additional security measures
6. **Integration Modules**: Develop connections to external systems

## 🔗 References

- [IRremoteESP8266 Protocol Support](https://github.com/crankyoldgit/IRremoteESP8266)
- [Android BLE Best Practices](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
- [ESP32 Hardware Design Guidelines](https://docs.espressif.com/projects/esp-idf/en/latest/esp32/hw-reference/index.html)
- [Material Design Guidelines](https://material.io/design)

---

**Note**: This document serves as a template for gathering your specific requirements. Please modify and expand based on your particular use case and preferences.