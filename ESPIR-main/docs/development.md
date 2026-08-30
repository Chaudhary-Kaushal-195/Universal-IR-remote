# ESPIR Development Guide

## Development Environment Setup

### ESP32 Firmware Development

#### Prerequisites
- **Arduino IDE 2.0+** or **PlatformIO**
- **ESP32 Board Support Package**
- **USB drivers** for ESP32 development board

#### Arduino IDE Setup
1. Install Arduino IDE from [arduino.cc](https://www.arduino.cc/)
2. Add ESP32 board manager URL:
   ```
   https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
   ```
3. Install ESP32 boards via Board Manager
4. Select board: "ESP32 Dev Module"

#### PlatformIO Setup (Recommended)
1. Install VS Code
2. Install PlatformIO extension
3. Open project folder
4. PlatformIO will automatically install dependencies

#### Required Libraries
```ini
# Installed automatically via platformio.ini
lib_deps = 
    crankyoldgit/IRremoteESP8266@^2.8.6
    h2zero/NimBLE-Arduino@^1.4.0
    bblanchon/ArduinoJson@^6.21.3
```

### Android Development

#### Prerequisites
- **Android Studio** (latest stable version)
- **Android SDK** (API 21-34)
- **Java 8+** or **Kotlin 1.9+**

#### Project Setup
1. Open `ESPIR-App` folder in Android Studio
2. Sync Gradle dependencies
3. Configure signing certificates for release builds
4. Enable USB debugging on target device

#### Required Permissions
The app requires the following permissions:
- `BLUETOOTH`
- `BLUETOOTH_ADMIN`
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_FINE_LOCATION`

## Build Instructions

### ESP32 Firmware Build

#### Using PlatformIO (Command Line)
```bash
# Navigate to firmware directory
cd ESPIR-FW

# Clean previous builds
pio run --target clean

# Build firmware
pio run

# Upload to ESP32
pio run --target upload

# Monitor serial output
pio device monitor
```

#### Using Arduino IDE
1. Open `src/main.cpp` in Arduino IDE
2. Select board: "ESP32 Dev Module"
3. Configure upload settings:
   - Upload Speed: 921600
   - Flash Frequency: 80MHz
   - Flash Mode: QIO
   - Flash Size: 4MB
4. Click Upload button

### Android App Build

#### Using Android Studio
1. Open project in Android Studio
2. Select build variant (debug/release)
3. Build → Make Project
4. Run → Run 'app' for debug installation

#### Using Command Line (Gradle)
```bash
# Navigate to Android project
cd ESPIR-App

# Debug build
./gradlew assembleDebug

# Release build (requires signing)
./gradlew assembleRelease

# Install debug APK
./gradlew installDebug

# Run tests
./gradlew test
```

## Development Workflow

### Code Organization

#### ESP32 Firmware Structure
```
ESPIR-FW/
├── src/
│   ├── main.cpp           # Main application entry
│   ├── ir_manager.cpp     # IR transmission/reception
│   ├── ble_manager.cpp    # BLE communication
│   └── device_manager.cpp # Device storage
├── include/
│   ├── config.h           # Configuration constants
│   ├── ir_manager.h       # IR manager interface
│   ├── ble_manager.h      # BLE manager interface
│   └── device_manager.h   # Device manager interface
└── platformio.ini         # Build configuration
```

#### Android App Structure
```
ESPIR-App/
├── app/src/main/
│   ├── java/com/espir/app/
│   │   ├── MainActivity.kt          # Main activity
│   │   ├── ble/BleManager.kt       # BLE communication
│   │   ├── data/DeviceRepository.kt # Data management
│   │   ├── ui/                     # UI components
│   │   └── viewmodel/              # ViewModels
│   ├── res/
│   │   ├── layout/                 # XML layouts
│   │   ├── values/                 # Strings, styles
│   │   └── drawable/               # Images, icons
│   └── AndroidManifest.xml         # App configuration
└── build.gradle                    # Dependencies
```

### Testing Strategy

#### Unit Testing (ESP32)
```cpp
// Example test for IR Manager
void test_ir_transmission() {
    IRManager manager;
    assert(manager.begin() == true);
    
    IRCode testCode;
    testCode.protocol = NEC;
    testCode.value = 0xFF00FF;
    testCode.bits = 32;
    
    assert(manager.transmitCode(testCode) == true);
    Serial.println("IR transmission test passed");
}
```

#### Unit Testing (Android)
```kotlin
@Test
fun testBleConnection() {
    val bleManager = BleManager(context)
    
    // Mock BLE device
    val mockDevice = mockk<BluetoothDevice>()
    every { mockDevice.address } returns "AA:BB:CC:DD:EE:FF"
    
    // Test connection
    bleManager.connect(mockDevice)
    
    // Verify connection state
    assertTrue(bleManager.isConnected())
}
```

### Debugging

#### ESP32 Debugging
```cpp
// Enable debug output in config.h
#define DEBUG 1

// Use debug macros
DEBUG_PRINTLN("Starting IR learning mode");
DEBUG_PRINT("Received command: ");
DEBUG_PRINTLN(command);

// Monitor serial output
// PlatformIO: pio device monitor
// Arduino IDE: Tools → Serial Monitor
```

#### Android Debugging
```kotlin
// Use Android Log
Log.d("BleManager", "Connection established")
Log.e("IRCommand", "Failed to transmit", exception)

// View logs
// adb logcat
// Android Studio → Logcat window
```

## API Documentation

### ESP32 BLE API

#### Command Processing
```cpp
// Command structure
struct Command {
    String type;
    JsonObject parameters;
    String requestId;
};

// Response structure  
struct Response {
    String status;      // "OK", "ERROR", "TIMEOUT"
    String message;     // Human readable message
    JsonObject data;    // Response data
    String requestId;   // Matching request ID
};
```

#### Supported Commands

##### TRANSMIT Command
```json
{
  "command": "TRANSMIT",
  "parameters": {
    "device": "Samsung_TV",
    "action": "power_on"
  },
  "requestId": "req_001"
}
```

##### LEARN Command
```json
{
  "command": "LEARN",
  "parameters": {
    "timeout": 15000
  },
  "requestId": "req_002"
}
```

##### ADD_DEVICE Command
```json
{
  "command": "ADD_DEVICE",
  "parameters": {
    "name": "Samsung_TV",
    "type": "television",
    "manufacturer": "Samsung",
    "model": "UN55TU7000"
  },
  "requestId": "req_003"
}
```

### Android BLE API

#### Connection Management
```kotlin
class BleManager(private val context: Context) {
    
    fun startScan(callback: ScanCallback)
    fun connect(device: BluetoothDevice): Boolean
    fun disconnect()
    fun isConnected(): Boolean
    
    fun sendCommand(command: Command): Boolean
    fun setResponseCallback(callback: (Response) -> Unit)
}
```

#### Device Repository
```kotlin
class DeviceRepository(private val database: AppDatabase) {
    
    suspend fun getAllDevices(): List<Device>
    suspend fun getDevice(id: Int): Device?
    suspend fun insertDevice(device: Device)
    suspend fun deleteDevice(device: Device)
    
    suspend fun getCommands(deviceId: Int): List<IRCommand>
    suspend fun insertCommand(command: IRCommand)
}
```

## Configuration

### ESP32 Configuration (`config.h`)
```cpp
// Pin assignments
#define IR_TRANSMIT_PIN     4
#define IR_RECEIVE_PIN      5  
#define STATUS_LED_PIN      2

// BLE settings
#define DEVICE_NAME         "ESPIR-Device"
#define SERVICE_UUID        "12345678-1234-1234-1234-123456789abc"

// Performance settings
#define IR_FREQUENCY        38000
#define MAX_DEVICES         50
#define BLE_TIMEOUT_MS      30000
```

### Android Configuration (`build.gradle`)
```gradle
android {
    compileSdkVersion 34
    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 34
        versionCode 1
        versionName "1.0.0"
    }
}

dependencies {
    implementation 'no.nordicsemi.android:ble:2.6.1'
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

## Performance Optimization

### ESP32 Optimization
- Use `yield()` in main loop to prevent watchdog resets
- Minimize memory allocation in interrupt handlers
- Use RTOS tasks for concurrent operations
- Optimize IR timing for better compatibility

### Android Optimization
- Use background threads for BLE operations
- Implement connection pooling for multiple devices
- Cache device profiles for offline operation
- Optimize UI updates with data binding

## Troubleshooting

### Common ESP32 Issues

#### Upload Failures
- Check USB cable and drivers
- Press BOOT button during upload
- Verify correct COM port selection
- Reset ESP32 before upload

#### BLE Connection Issues
- Clear device cache on Android
- Check BLE permissions
- Verify service UUID match
- Monitor serial output for errors

#### IR Transmission Problems
- Check IR LED wiring and polarity
- Verify MOSFET operation
- Test with smartphone camera
- Adjust current limiting resistor

### Common Android Issues

#### Permission Denied
- Request runtime permissions for BLE
- Enable location services
- Check app permissions in settings

#### Build Failures
- Clean and rebuild project
- Update Gradle and dependencies
- Check ProGuard rules for release builds
- Verify Android SDK installation

## Release Process

### Version Management
- Use semantic versioning (MAJOR.MINOR.PATCH)
- Tag releases in Git
- Update version numbers in all components
- Maintain CHANGELOG.md

### ESP32 Release
1. Test firmware on hardware
2. Update version in `config.h`
3. Build release binary
4. Test OTA update process
5. Document changes and known issues

### Android Release
1. Run full test suite
2. Update version in `build.gradle`
3. Generate signed APK
4. Test on multiple devices
5. Prepare store listing materials

## Contributing Guidelines

### Code Style
- Follow Arduino style guide for ESP32 code
- Use Kotlin coding conventions for Android
- Include comprehensive comments
- Write unit tests for new features

### Pull Request Process
1. Create feature branch from main
2. Implement changes with tests
3. Update documentation
4. Submit pull request with description
5. Address review feedback

### Issue Reporting
- Use provided issue templates
- Include system information
- Provide steps to reproduce
- Attach relevant logs and screenshots