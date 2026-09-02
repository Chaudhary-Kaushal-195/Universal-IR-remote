# 🌐 Universal IR Remote Hub (Android • ESP32 • Arduino Uno)

A complete, high-performance universal infrared remote control system combining a modern **Android 14 app** with **USB OTG Serial (Arduino Uno / ESP32)**, **Wireless Cloud MQTT (ESP32)**, and **Internal Phone IR Blaster** support.

---

## 📑 Table of Contents
1. [System Architecture](#-system-architecture)
2. [Hardware Pin Connections & Wiring](#-hardware-pin-connections--wiring)
   - [Arduino Uno Pinout](#1-arduino-uno-wiring)
   - [ESP32 Pinout](#2-esp32-wiring)
   - [High-Range IR Emitter Driver Circuit](#3-high-range-ir-emitter-driver-circuit-recommended)
3. [Software & Protocol Specifications](#-software--protocol-specifications)
   - [USB Serial Protocol](#usb-serial-protocol-115200-baud)
   - [WiFi / MQTT Protocol](#wifi--mqtt-cloud-protocol)
4. [Firmware Setup & Flashing Guide](#-firmware-setup--flashing-guide)
   - [Arduino Uno Setup](#flashing-arduino-uno)
   - [ESP32 Setup](#flashing-esp32)
5. [Android App Features & Usage](#-android-app-features--usage)
   - [Multi-Channel Transmission](#multi-channel-transmission-hierarchy)
   - [Multi-Profile Storage (5 Profiles Per Category)](#multi-profile-storage)
   - [Cloning / Programming New Remotes](#how-to-clone--program-remotes)
   - [Export & Import Backup](#backup--transfer)
6. [Troubleshooting](#-troubleshooting)

---

## 🏗 System Architecture

```
                       ┌───────────────────────────────┐
                       │     Universal Hub Android     │
                       │             App               │
                       └─┬───────────┬───────────────┬─┘
                         │           │               │
            Direct 38kHz │       USB │ OTG 115200    │ MQTT Cloud
             Phone Diode │           │ Serial        │ (broker.hivemq.com)
                         ▼           ▼               ▼
                   ┌──────────┐ ┌─────────┐   ┌─────────────┐
                   │ Internal │ │ Arduino │   │    ESP32    │
                   │ Phone IR │ │   Uno   │   │  Wireless   │
                   │ Blaster  │ └────┬────┘   └──────┬──────┘
                   └──────────┘      │               │
                                     ▼               ▼
                               ┌───────────────────────────┐
                               │     IR Transmit & Clone   │
                               │  AC • TV • Light • Fan    │
                               └───────────────────────────┘
```

---

## 🔌 Hardware Pin Connections & Wiring

### 1. Arduino Uno Wiring

The Arduino Uno sketch utilizes hardware Timer 2 for carrier modulation and Timer 1 for receiving.

| Component | Pin on Module | Arduino Uno Pin | Notes |
|---|---|---|---|
| **IR Receiver** (TSOP1738 / VS1838B) | **OUT / DATA** | **Digital Pin 7** | Digital input signal |
| | **VCC** | **5V** | Regulated supply |
| | **GND** | **GND** | Ground |
| **IR Emitter LED** (940nm) | **Anode (+)** | **Digital Pin 3** | Controlled via PWM timer (use 220Ω resistor or NPN driver) |
| | **Cathode (-)** | **GND** | Ground |
| **Built-in LED** | Internal | **Digital Pin 13** | Blinks on IR reception and transmission |

> [!IMPORTANT]
> On the Arduino Uno, the IR transmitter **must be connected to Digital Pin 3**. The `IRremote` library uses Timer 2 on the ATmega328P, which is hard-wired to Pin 3 for 38kHz PWM.

---

### 2. ESP32 Wiring

The ESP32 firmware uses the high-precision **RMT (Remote Control)** hardware peripheral for sub-microsecond pulse timing.

| Component | Pin on Module | ESP32 Pin | Notes |
|---|---|---|---|
| **IR Receiver** (TSOP1738 / VS1838B) | **OUT / DATA** | **GPIO 27** | Quieter pin with internal pull-up enabled |
| | **VCC** | **3.3V or 5V (VIN)** | 3.3V recommended to protect GPIO |
| | **GND** | **GND** | Common ground |
| **IR Emitter LED** (940nm) | **Anode (+)** | **GPIO 4** | Driven via RMT channel (use resistor or NPN driver) |
| | **Cathode (-)** | **GND** | Common ground |

---

### 3. High-Range IR Emitter Driver Circuit (Recommended)

Directly powering an IR LED from a microcontroller pin limits current to 12–20mA (resulting in 1–2 meter range). To achieve **8–12 meters range** (matching commercial remotes), use a simple NPN transistor driver:

```
  +5V (from Uno 5V or ESP32 VIN)
   │
  [ ] 10Ω - 22Ω (1/2 Watt Current Limiting Resistor)
   │
   ▼ Anode (+)
 [IR LED 940nm]
   │ Cathode (-)
   │
   ├── Collector (C)
  ┌┴┐
  │ │ NPN Transistor (2N2222 or BC547)
  └┬┘
   ├── Base (B) ──[ 1kΩ Resistor ]── Microcontroller TX Pin (Uno D3 or ESP32 GPIO 4)
   │
   └── Emitter (E)
   │
  GND
```

---

## 📡 Software & Protocol Specifications

### USB Serial Protocol (115200 Baud)

When connected via USB OTG cable to the phone, both Arduino Uno and ESP32 exchange messages using single-line ASCII strings:

#### 1. Device to Phone (IR Signal Received / Cloned)
```text
RAW:<pulse_count>:<usec_1,usec_2,usec_3,...>\n
```
*Example:*
```text
RAW:67:9000,4500,560,1690,560,560,560,1690,560,560...
```

#### 2. Phone to Device (Transmit Signal)
```text
SEND_RAW:<pulse_count>:<usec_1,usec_2,usec_3,...>\n
```
*Example:*
```text
SEND_RAW:67:9000,4500,560,1690,560,560,560,1690...
```

#### 3. Status Handshake
- **`HUB_READY`**: Broadcasted on boot.
- **`STATUS:REPLAYING_RAW_SIGNAL`**: Device paused receiver and started transmission.
- **`SEND_OK`**: Transmission complete; receiver re-enabled.

---

### WiFi / MQTT Cloud Protocol

For wire-free operation with the ESP32:
- **Broker**: `broker.hivemq.com` (Port 1883)
- **Topic TX (App Transmits / ESP32 Listens)**: `universalo-hub/{hubId}/rx`
- **Topic RX (ESP32 Publishes / App Listens)**: `universalo-hub/{hubId}/tx`

#### Payload Format (JSON):
```json
{
  "type": "raw",
  "len": 250,
  "values": "9000,4500,560,1690,560,560..."
}
```

---

## 💻 Firmware Setup & Flashing Guide

### Flashing Arduino Uno

1. Open the [arduino_uno_serial.ino](arduino_uno_serial.ino) sketch in **Arduino IDE**.
2. Go to **Sketch** ➔ **Include Library** ➔ **Manage Libraries...**
3. Install:
   - **IRremote** by *Armin Joachimsmeyer* (Version `4.x` or later).
4. In the menu, select:
   - **Board**: `Arduino Uno`
   - **Port**: Select your Uno's COM port.
5. Click **Upload**.
6. Open the Serial Monitor at **115200 baud**. You should see:
   ```
   HUB_READY
   DEBUG: Super-Buffer Active (450 pulses)
   ```

---

### Flashing ESP32

1. Open [esp32_ir_hub/esp32_ir_hub.ino](esp32_ir_hub/esp32_ir_hub.ino) in **Arduino IDE**.
2. Install the required libraries via Library Manager:
   - **IRremoteESP8266** by *David Conran*
   - **PubSubClient** by *Nick O'Leary*
   - **ArduinoJson** by *Benoît Blanchon* (Version 6.x)
3. In `esp32_ir_hub.ino`, update your WiFi credentials:
   ```cpp
   const char* ssid = "YOUR_WIFI_SSID";
   const char* password = "YOUR_WIFI_PASSWORD";
   const char* mqtt_topic_rx = "universalo-hub/your-hub-id/rx";
   const char* mqtt_topic_tx = "universalo-hub/your-hub-id/tx";
   ```
4. Select:
   - **Board**: `ESP32 Dev Module`
   - **Port**: Select your ESP32's COM port.
5. Click **Upload**.
6. Open Serial Monitor at **115200 baud** to verify:
   ```
   HUB_READY: USB Serial & MQTT Active (115200 baud)
   Connecting to MQTT Broker... SUCCESS!
   ```

---

## 📱 Android App Features & Usage

### Multi-Channel Transmission Hierarchy

When you press a button on any remote (e.g. AC Power, TV Volume, Lamp Toggle), the app automatically routes the signal through all connected hardware:
1. **Phone IR**: If your phone has a built-in IR blaster (`ConsumerIrManager`).
2. **USB OTG**: If an Arduino Uno or ESP32 is plugged in via USB-C cable.
3. **WiFi Hub**: If your wireless ESP32 hub is online.

The notification pill at the top will confirm the active channels:
> `⚡ Transmitting AC POWER ON (Phone IR + USB)`

---

### Multi-Profile Storage

Each appliance category supports **5 independent profiles** (e.g. 5 ACs, 5 TVs, 5 Lights, 5 Fans):
- **Switch Profile**: Tap any pill (`AC 1`, `AC 2`, `AC 3`, `AC 4`, `AC 5`) at the top of the remote modal.
- **Rename Profile**: **Long-press** any profile pill to open the rename dialog (e.g., rename `AC 1` to `Living Room`, `AC 2` to `Master Bedroom`).
- All learned IR codes are strictly isolated to their active profile.

---

### How to Clone / Program Remotes

1. Tap the **Menu icon** (top-left) to open **Settings**.
2. Turn **Programming Mode ON**.
3. Close Settings and open the remote sheet (e.g. **Smart AC Climate**).
4. Tap the button you want to teach (e.g. **Power On**).
5. The top HUD will prompt:
   > `🎯 Point remote at USB Hub / ESP32 to clone AC POWER ON (AC 1)`
6. Point your physical remote at the IR receiver module (Pin 7 on Uno or GPIO 27 on ESP32) and press the button.
7. The app will capture the signal, display `✅ Programmed: AC POWER ON!`, and save it automatically.
8. Turn **Programming Mode OFF** when finished.

---

### Backup & Transfer

Under **Settings**:
- **Export Backup**: Generates a clean JSON file (`ir_hub_backup.json`) containing all your learned codes and profile names for all devices.
- **Import Backup**: Restores all profiles and signals on any Android phone running the app.

---

## 🛠 Troubleshooting

| Issue | Cause | Solution |
|---|---|---|
| **USB Status: Disconnected** | OTG adapter not recognized | Ensure your phone's Android settings have **OTG storage/connection enabled** (some phones require toggling OTG in Settings). |
| **No IR signal emitted** | LED connected backwards | The long leg of the IR LED is the Anode (+). The flat edge is Cathode (-). |
| **Short IR range (1-2m)** | LED underpowered from GPIO pin | Wire the recommended NPN transistor driver circuit (2N2222 or BC547) to supply full 5V current. |
| **AC remote cloning fails** | Signal buffer overflow | AC remotes send long bursts (100–300 pulses). Ensure you are running the latest `arduino_uno_serial.ino` (with `RAW_BUFFER_LENGTH 750`) or `esp32_ir_hub.ino`. |
| **WiFi Hub status offline** | Incorrect MQTT Topic or WiFi | Check that `hubId` in the Android App Settings matches the topic configured in `esp32_ir_hub.ino`. |