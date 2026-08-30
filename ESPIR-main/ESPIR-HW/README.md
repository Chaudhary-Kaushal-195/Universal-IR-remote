# ESPIR-HW: Hardware Design Documentation

## Overview
The ESPIR hardware consists of an ESP32 development board with connected IR LED transmitter, IR receiver, and supporting circuitry for power management and signal conditioning.

## Components List

### Main Components
- **ESP32-WROOM-32**: Main microcontroller module
- **IR LED**: 940nm infrared LED for transmission (e.g., TSAL6200)
- **IR Receiver**: 38kHz IR receiver module (e.g., TSOP4838)
- **MOSFET**: N-channel MOSFET for IR LED driver (e.g., 2N7000)
- **Resistors**: Current limiting and pull-up resistors
- **Capacitors**: Power supply filtering

### Supporting Components
- **Power Supply**: 3.3V regulation from USB or external power
- **Status LED**: Built-in ESP32 LED (GPIO2)
- **Connectors**: USB-C or Micro-USB for programming and power

## Circuit Description

### ESP32 Pin Assignments
```
GPIO2  - Status LED (built-in)
GPIO4  - IR Transmit (to MOSFET gate)
GPIO5  - IR Receive (from IR receiver module)
3.3V   - Power supply for IR receiver
GND    - Common ground
```

### IR Transmitter Circuit
```
                     +3.3V
                       |
                      +-+
                      |R1| 10kΩ (pull-up)
                      +-+
                       |
GPIO4 ----[R2]----+---+
              1kΩ  |   |
                   |   G
                   |   |  2N7000
                   +---D  MOSFET
                       |
                       S
                       |
                      +-+
                      |R3| 100Ω (current limit)
                      +-+
                       |
                    +--|<+-- IR LED (TSAL6200)
                    |     |
                    +-----+
                       |
                      GND
```

### IR Receiver Circuit
```
             +3.3V
               |
               +--[C1]--GND (100nF decoupling)
               |
    +----------+----------+
    |                     |
    |   TSOP4838 IR RX    |
    |                     |
    +----------+----------+
               |
               +---- GPIO5
```

## Power Requirements

### Current Consumption
- ESP32 (active): ~80mA @ 3.3V
- ESP32 (BLE active): ~120mA @ 3.3V
- IR LED (transmitting): ~100mA @ 1.4V (through MOSFET)
- IR Receiver: ~5mA @ 3.3V
- **Total (worst case)**: ~225mA @ 3.3V

### Power Supply
- Input: 5V USB or 6-12V external
- Regulation: 3.3V linear regulator (AMS1117-3.3)
- Recommended: Use ESP32 development board with built-in regulation

## PCB Layout Considerations

### Signal Integrity
- Keep IR transmit traces short and wide
- Separate analog and digital grounds where possible
- Add ground plane for noise reduction

### Component Placement
- IR LED: Position for optimal beam angle and range
- IR Receiver: Shield from direct IR LED interference
- MOSFET: Close to IR LED to minimize trace resistance

### Thermal Management
- IR LED can dissipate significant heat during transmission
- Consider heat sinking for continuous operation
- MOSFET should handle peak current without overheating

## Mechanical Design

### Enclosure Requirements
- IR LED window: Clear IR-transparent material
- IR Receiver window: Protected but unobstructed view
- Ventilation: Slots for heat dissipation
- Status LED: Visible indicator opening
- USB port: Accessible for programming and power

### Mounting
- PCB mounting holes compatible with standard spacers
- Strain relief for external connections
- Stable base for tabletop operation

## Assembly Instructions

### Step 1: PCB Assembly
1. Mount ESP32 development board
2. Install IR receiver module
3. Mount MOSFET and associated resistors
4. Install IR LED with current limiting resistor
5. Add power supply filtering capacitors

### Step 2: Testing
1. Visual inspection of all connections
2. Power-on test (check 3.3V regulation)
3. IR receiver test (verify 38kHz response)
4. IR transmitter test (verify LED operation)
5. ESP32 programming test

### Step 3: Calibration
1. Measure IR LED forward voltage and current
2. Adjust current limiting resistor if necessary
3. Test IR range and reception sensitivity
4. Verify BLE communication range

## Specifications

### Performance
- **IR Range**: 5-10 meters (line of sight)
- **IR Frequency**: 38kHz carrier
- **BLE Range**: 10-50 meters (depending on environment)
- **Power Consumption**: <250mA @ 5V

### Environmental
- **Operating Temperature**: 0°C to 50°C
- **Storage Temperature**: -20°C to 70°C
- **Humidity**: 10-90% non-condensing
- **Size**: Approximately 60mm x 40mm x 20mm

## Bill of Materials (BOM)

| Ref | Component | Part Number | Qty | Notes |
|-----|-----------|-------------|-----|-------|
| U1 | ESP32 Dev Board | ESP32-WROOM-32 | 1 | Main controller |
| D1 | IR LED | TSAL6200 | 1 | 940nm, 100mA max |
| U2 | IR Receiver | TSOP4838 | 1 | 38kHz, 3.3V |
| Q1 | N-MOSFET | 2N7000 | 1 | Logic level |
| R1 | Resistor | 10kΩ | 1 | Pull-up |
| R2 | Resistor | 1kΩ | 1 | Gate resistor |
| R3 | Resistor | 100Ω | 1 | Current limit |
| C1 | Capacitor | 100nF | 1 | Decoupling |

## Safety Considerations

### Electrical Safety
- Ensure proper current limiting for IR LED
- Verify MOSFET ratings for switching application
- Use appropriate fusing for external power

### IR Safety
- 940nm IR is invisible to human eye
- Avoid looking directly into IR LED
- Power levels are well below eye safety limits
- Consider IR safety labeling

## Testing and Validation

### Functional Tests
1. **Power Supply Test**: Verify 3.3V regulation under load
2. **IR Transmit Test**: Confirm IR LED operation with oscilloscope
3. **IR Receive Test**: Verify receiver response to 38kHz modulated IR
4. **BLE Test**: Confirm ESP32 BLE connectivity
5. **Range Test**: Measure effective IR control range

### Compliance Testing
- FCC Part 15 (if required for commercial use)
- Bluetooth SIG qualification (if required)
- RoHS compliance for components

## Troubleshooting

### Common Issues
1. **No IR transmission**: Check MOSFET bias and IR LED polarity
2. **Poor reception**: Verify IR receiver power and positioning
3. **BLE connection issues**: Check antenna and power supply
4. **Overheating**: Verify current limiting and thermal design

### Debug Procedures
1. Use multimeter to verify voltages
2. Use oscilloscope to check IR signals
3. Use smartphone camera to visualize IR LED operation
4. Check serial console for firmware debug messages

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-10-04 | Initial design documentation |

## References

- [ESP32 Datasheet](https://www.espressif.com/sites/default/files/documentation/esp32_datasheet_en.pdf)
- [TSAL6200 IR LED Datasheet](https://www.vishay.com/docs/81010/tsal6200.pdf)
- [TSOP4838 IR Receiver Datasheet](https://www.vishay.com/docs/82491/tsop48.pdf)
- [2N7000 MOSFET Datasheet](https://www.onsemi.com/pub/Collateral/2N7000-D.PDF)