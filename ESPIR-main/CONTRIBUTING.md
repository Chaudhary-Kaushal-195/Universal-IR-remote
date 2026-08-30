# ESPIR Contributing Guidelines

## Getting Started

Thank you for your interest in contributing to the ESPIR project! This document provides guidelines for contributing to our IR blaster system.

## Development Setup

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd ESPIR-FW
   ```

2. **Check development environment:**
   ```bash
   make check
   ```

3. **Install dependencies:**
   ```bash
   make deps
   ```

4. **Build all components:**
   ```bash
   make all
   ```

## Branch Strategy

- `main` - Stable release branch
- `develop` - Development integration branch  
- `feature/*` - Feature development branches
- `fix/*` - Bug fix branches
- `release/*` - Release preparation branches

## Commit Guidelines

We use conventional commits with the following types:

- **feat**: New features
- **fix**: Bug fixes
- **docs**: Documentation changes
- **style**: Code style changes (formatting, etc.)
- **refactor**: Code refactoring
- **test**: Adding or updating tests
- **chore**: Maintenance tasks
- **build**: Build system changes
- **ci**: CI/CD changes
- **perf**: Performance improvements
- **hw**: Hardware changes
- **android**: Android app specific changes
- **firmware**: ESP32 firmware specific changes

### Commit Message Format

```
type(scope): brief description

Detailed explanation of what was changed and why.

Fixes #123
```

### Examples

```bash
feat(firmware): add support for Samsung IR protocol
fix(android): resolve BLE connection timeout issue
docs(hardware): update PCB layout guidelines
hw(circuit): increase IR LED current limit to 150mA
```

## Code Style

### ESP32 Firmware (C++)
- Follow Arduino coding style
- Use descriptive variable names
- Comment complex algorithms
- Include error handling

```cpp
// Good
bool IRManager::transmitCode(const IRCode& code) {
    if (!irSend) {
        DEBUG_PRINTLN("ERROR: IR transmitter not initialized");
        return false;
    }
    // ... implementation
}
```

### Android App (Kotlin)
- Follow Kotlin coding conventions
- Use meaningful class and method names
- Implement proper error handling
- Use coroutines for async operations

```kotlin
// Good
class EspirBleManager(context: Context) : BleManager(context) {
    suspend fun sendCommand(command: String): Result<String> {
        return try {
            // ... implementation
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

## Testing

### Running Tests
```bash
# All tests
make test

# Firmware tests only
make test-firmware

# Android tests only
make test-android
```

### Writing Tests

#### ESP32 Firmware
- Write unit tests for manager classes
- Test error conditions
- Verify protocol compliance

#### Android App
- Write unit tests for ViewModels
- Test BLE communication
- Verify UI interactions

## Pull Request Process

1. **Create a feature branch:**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes:**
   - Write code following style guidelines
   - Add tests for new functionality
   - Update documentation as needed

3. **Test your changes:**
   ```bash
   make test
   make lint
   ```

4. **Commit your changes:**
   ```bash
   git add .
   git commit -m "feat(scope): your feature description"
   ```

5. **Push to your fork:**
   ```bash
   git push origin feature/your-feature-name
   ```

6. **Create a Pull Request:**
   - Provide clear description of changes
   - Link to related issues
   - Include screenshots for UI changes
   - Ensure all checks pass

## Component-Specific Guidelines

### ESP32 Firmware
- Test on actual hardware when possible
- Verify memory usage and performance
- Ensure proper power management
- Test BLE connectivity thoroughly

### Android App
- Test on multiple Android versions
- Verify BLE permissions on different devices
- Test offline functionality
- Ensure proper lifecycle handling

### Hardware
- Validate circuit designs with simulation
- Test component selections for availability
- Verify thermal characteristics
- Consider manufacturing constraints

## Documentation

- Update relevant documentation for changes
- Include code examples in API docs
- Update README.md for significant features
- Add comments for complex algorithms

## Issue Reporting

When reporting issues, please include:

- **Environment details**: OS, device, versions
- **Steps to reproduce**: Clear, step-by-step instructions
- **Expected behavior**: What should happen
- **Actual behavior**: What actually happens
- **Logs**: Relevant console output or logs
- **Screenshots**: For UI-related issues

## Release Process

1. **Version bumping**: Update version numbers in:
   - `include/config.h` (firmware)
   - `ESPIR-App/app/build.gradle` (Android)
   - `Makefile` (build system)

2. **Testing**: Ensure all tests pass
   ```bash
   make ci
   ```

3. **Documentation**: Update CHANGELOG.md

4. **Release**: Create release package
   ```bash
   make release
   ```

## Getting Help

- **Documentation**: Check `docs/` directory
- **Issues**: Search existing issues first
- **Discussions**: Use GitHub Discussions for questions
- **Email**: Contact maintainers for security issues

## Code of Conduct

- Be respectful and inclusive
- Focus on constructive feedback
- Help others learn and grow
- Maintain a professional environment

Thank you for contributing to ESPIR! 🚀