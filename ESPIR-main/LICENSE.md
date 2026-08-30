# ESPIR Project License

## License Overview

The ESPIR (ESP32 IR Blaster) project is released under multiple licenses to accommodate different use cases and components:

### 1. Software Components (MIT License)

The following software components are licensed under the MIT License:

- **ESP32 Firmware** (`src/`, `include/`)
- **Android Application** (`ESPIR-App/`)
- **Build Scripts and Makefiles**
- **Documentation and Examples**

```
MIT License

Copyright (c) 2025 ESPIR Development Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### 2. Hardware Designs (CERN Open Hardware License v2)

The hardware designs and schematics in the `ESPIR-HW/` directory are licensed under the CERN Open Hardware License v2 (CERN-OHL-S):

```
CERN Open Hardware Licence Version 2 - Strongly Reciprocal

Copyright (c) 2025 ESPIR Development Team

This source describes Open Hardware and is licensed under the CERN-OHL-S v2.

You may redistribute and modify this source and make products using it under
the terms of the CERN-OHL-S v2 (https://ohwr.org/cern-ohl-s-v2.txt).

This source is distributed WITHOUT ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING
OF MERCHANTABILITY, SATISFACTORY QUALITY AND FITNESS FOR A PARTICULAR PURPOSE.
Please see the CERN-OHL-S v2 for applicable conditions.

Source location: https://github.com/espir-project/ESPIR-FW

As per CERN-OHL-S v2 section 4, should You produce hardware based on this
source, You must where practicable maintain the Source Location visible
on the external case of the device or other products you make using this source.
```

### 3. Third-Party Dependencies

This project uses several third-party libraries and components, each with their own licenses:

#### ESP32 Firmware Dependencies
- **IRremoteESP8266**: LGPL-2.1 License
- **NimBLE-Arduino**: Apache 2.0 License  
- **ArduinoJson**: MIT License
- **ESP32 Arduino Core**: LGPL-2.1 License

#### Android Application Dependencies
- **Android SDK**: Apache 2.0 License
- **Material Components**: Apache 2.0 License
- **Room Database**: Apache 2.0 License
- **Nordic BLE Library**: BSD 3-Clause License

#### Build Tools
- **PlatformIO**: Apache 2.0 License
- **Android Gradle Plugin**: Apache 2.0 License

### 4. Usage Guidelines

#### Commercial Use
- ✅ **Software**: Full commercial use permitted under MIT License
- ✅ **Hardware**: Commercial use permitted under CERN-OHL-S v2 with source disclosure requirements
- ⚠️ **Important**: If you create hardware products based on this design, you must make the source files available per CERN-OHL-S v2 requirements

#### Attribution Requirements
When using this project:

1. **Software**: Include MIT license notice in documentation
2. **Hardware**: Include CERN-OHL-S notice and source location on product
3. **Publications**: Cite as "ESPIR Project (https://github.com/espir-project/ESPIR-FW)"

#### Modifications and Derivatives
- Software modifications remain under MIT License
- Hardware modifications must remain under CERN-OHL-S v2
- Source disclosure required for hardware derivatives

### 5. Warranty Disclaimer

**THIS PROJECT IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND.** The developers make no warranties about:

- Functionality or fitness for purpose
- Compliance with local regulations
- Safety for any particular use case
- Electromagnetic compatibility
- FCC/CE certification

### 6. Regulatory Compliance

**Important Notice**: This project involves radio frequency (RF) transmission and may be subject to local regulations:

- **FCC (United States)**: Part 15 regulations may apply
- **CE (Europe)**: RED directive compliance may be required  
- **IC (Canada)**: ISED regulations may apply
- **Other regions**: Check local RF emission regulations

**Users are responsible for ensuring compliance with applicable regulations in their jurisdiction.**

### 7. Safety Notice

This project involves:
- High-frequency switching circuits
- Infrared LED with potentially high optical power
- Radio frequency transmission
- Electrical circuits operating at 3.3V/5V

**Always follow proper safety practices and electrical safety guidelines.**

### 8. Contact Information

For license questions or clarifications:
- **Project Website**: https://github.com/espir-project/ESPIR-FW
- **License Issues**: Create an issue on the project repository
- **Security Issues**: See SECURITY.md for responsible disclosure

### 9. License Compatibility

This dual-licensing approach ensures:
- Maximum flexibility for software reuse
- Strong copyleft protection for hardware designs
- Compatibility with most open-source projects
- Clear commercial use guidelines

---

**Last Updated**: October 5, 2025  
**License Version**: 1.0