# Security Policy

## Supported Versions

We actively support the following versions of ESPIR with security updates:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | ✅ Yes            |
| < 1.0   | ❌ No             |

## Reporting a Vulnerability

We take security seriously. If you discover a security vulnerability in ESPIR, please report it responsibly.

### How to Report

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, please send an email to: **security@espir.project** with:

- A clear description of the vulnerability
- Steps to reproduce the issue
- Potential impact assessment
- Any suggested fixes or mitigations

### What to Include

1. **Component affected**: Firmware, Android app, or hardware
2. **Vulnerability type**: Buffer overflow, authentication bypass, etc.
3. **Attack vector**: Local, network, physical access required
4. **Impact**: Data exposure, device control, denial of service
5. **Evidence**: Logs, screenshots, or proof-of-concept code

### Response Process

1. **Acknowledgment**: Within 48 hours
2. **Initial assessment**: Within 1 week
3. **Status updates**: Weekly until resolved
4. **Resolution**: Coordinated disclosure after fix

### Security Considerations

#### ESP32 Firmware
- BLE communication security
- Device authentication
- Code execution protection
- Memory safety

#### Android Application
- BLE permission handling
- Data storage security
- Network communication
- Input validation

#### Hardware
- Physical access protection
- Debug interface security
- Component tampering
- Side-channel attacks

### Disclosure Policy

- **Coordinated disclosure**: Work with us before public disclosure
- **Timeline**: 90 days for fix development
- **Credit**: Security researchers will be credited
- **CVE assignment**: For significant vulnerabilities

### Security Best Practices

#### For Users
- Keep firmware and app updated
- Use strong device pairing
- Secure physical access to device
- Monitor for unusual behavior

#### For Developers
- Follow secure coding practices
- Validate all inputs
- Use encryption where appropriate
- Implement proper authentication

Thank you for helping keep ESPIR secure! 🔒