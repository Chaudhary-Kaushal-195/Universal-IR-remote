# ESPIR Project Makefile
# Comprehensive build system for ESP32 firmware, Android app, and hardware documentation
# 
# Usage:
#   make help          - Show available targets
#   make all           - Build all components
#   make firmware      - Build ESP32 firmware
#   make android       - Build Android app
#   make clean         - Clean all build artifacts
#   make deploy        - Deploy firmware and app
#   make test          - Run all tests
#   make docs          - Generate documentation

# Project configuration
PROJECT_NAME := ESPIR
VERSION := 1.0.0
BUILD_DATE := $(shell date +%Y%m%d_%H%M%S)

# Directories
FIRMWARE_DIR := .
ANDROID_DIR := ./ESPIR-App
HARDWARE_DIR := ./ESPIR-HW
DOCS_DIR := ./docs
BUILD_DIR := ./build
DIST_DIR := ./dist

# ESP32 Firmware configuration
PLATFORMIO := pio
FIRMWARE_ENV := esp32dev
FIRMWARE_TARGET := $(BUILD_DIR)/firmware.bin

# Android configuration
GRADLE := ./gradlew
ANDROID_DEBUG_APK := $(ANDROID_DIR)/app/build/outputs/apk/debug/app-debug.apk
ANDROID_RELEASE_APK := $(ANDROID_DIR)/app/build/outputs/apk/release/app-release.apk

# Tools
PYTHON := python3
ZIP := zip
TAR := tar
MKDIR := mkdir -p
RM := rm -rf
CP := cp -r

# Color codes for output
RED := \033[0;31m
GREEN := \033[0;32m
YELLOW := \033[1;33m
BLUE := \033[0;34m
NC := \033[0m # No Color

# Default target
.DEFAULT_GOAL := help

# Phony targets
.PHONY: help all clean firmware android hardware docs test deploy install deps check lint format release

##@ General

help: ## Display this help
	@echo "$(BLUE)ESPIR Project Build System$(NC)"
	@echo "Version: $(VERSION)"
	@echo ""
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z_0-9-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

all: firmware android docs ## Build all components

clean: clean-firmware clean-android clean-docs clean-build ## Clean all build artifacts
	@echo "$(GREEN)✓ All clean complete$(NC)"

##@ Build Targets

firmware: check-pio ## Build ESP32 firmware
	@echo "$(BLUE)Building ESP32 firmware...$(NC)"
	@$(MKDIR) $(BUILD_DIR)
	cd $(FIRMWARE_DIR) && $(PLATFORMIO) run --environment $(FIRMWARE_ENV)
	@if [ -f .pio/build/$(FIRMWARE_ENV)/firmware.bin ]; then \
		cp .pio/build/$(FIRMWARE_ENV)/firmware.bin $(FIRMWARE_TARGET); \
		echo "$(GREEN)✓ Firmware build complete: $(FIRMWARE_TARGET)$(NC)"; \
	else \
		echo "$(RED)✗ Firmware build failed$(NC)"; \
		exit 1; \
	fi

firmware-upload: firmware check-pio ## Build and upload ESP32 firmware
	@echo "$(BLUE)Uploading ESP32 firmware...$(NC)"
	cd $(FIRMWARE_DIR) && $(PLATFORMIO) run --target upload --environment $(FIRMWARE_ENV)
	@echo "$(GREEN)✓ Firmware upload complete$(NC)"

firmware-monitor: check-pio ## Monitor ESP32 serial output
	@echo "$(BLUE)Starting ESP32 serial monitor...$(NC)"
	cd $(FIRMWARE_DIR) && $(PLATFORMIO) device monitor

android: check-gradle ## Build Android debug APK
	@echo "$(BLUE)Building Android debug APK...$(NC)"
	cd $(ANDROID_DIR) && $(GRADLE) :app:assembleDebug
	@if [ -f $(ANDROID_DEBUG_APK) ]; then \
		echo "$(GREEN)✓ Android debug build complete: $(ANDROID_DEBUG_APK)$(NC)"; \
	else \
		echo "$(RED)✗ Android debug build failed$(NC)"; \
		exit 1; \
	fi

android-release: check-gradle ## Build Android release APK
	@echo "$(BLUE)Building Android release APK...$(NC)"
	cd $(ANDROID_DIR) && $(GRADLE) assembleRelease
	@if [ -f $(ANDROID_RELEASE_APK) ]; then \
		echo "$(GREEN)✓ Android release build complete: $(ANDROID_RELEASE_APK)$(NC)"; \
	else \
		echo "$(RED)✗ Android release build failed$(NC)"; \
		exit 1; \
	fi

android-install: android ## Build and install Android debug APK
	@echo "$(BLUE)Installing Android debug APK...$(NC)"
	cd $(ANDROID_DIR) && $(GRADLE) installDebug
	@echo "$(GREEN)✓ Android app installed$(NC)"

##@ Testing

test: test-firmware test-android ## Run all tests

test-firmware: check-pio ## Run ESP32 firmware tests
	@echo "$(BLUE)Running ESP32 firmware tests...$(NC)"
	cd $(FIRMWARE_DIR) && $(PLATFORMIO) test --environment $(FIRMWARE_ENV)
	@echo "$(GREEN)✓ Firmware tests complete$(NC)"

test-android: check-gradle ## Run Android unit tests
	@echo "$(BLUE)Running Android unit tests...$(NC)"
	cd $(ANDROID_DIR) && $(GRADLE) test
	@echo "$(GREEN)✓ Android tests complete$(NC)"

test-android-instrumented: check-gradle ## Run Android instrumented tests
	@echo "$(BLUE)Running Android instrumented tests...$(NC)"
	cd $(ANDROID_DIR) && $(GRADLE) connectedAndroidTest
	@echo "$(GREEN)✓ Android instrumented tests complete$(NC)"

##@ Code Quality

lint: lint-firmware lint-android ## Run linting on all code

lint-firmware: check-pio ## Run ESP32 firmware linting
	@echo "$(BLUE)Linting ESP32 firmware...$(NC)"
	cd $(FIRMWARE_DIR) && $(PLATFORMIO) check --environment $(FIRMWARE_ENV)
	@echo "$(GREEN)✓ Firmware linting complete$(NC)"

lint-android: check-gradle ## Run Android linting
	@echo "$(BLUE)Linting Android code...$(NC)"
	cd $(ANDROID_DIR) && $(GRADLE) lint
	@echo "$(GREEN)✓ Android linting complete$(NC)"

format: format-firmware format-android ## Format all source code

format-firmware: ## Format ESP32 firmware code
	@echo "$(BLUE)Formatting ESP32 firmware...$(NC)"
	@find src include -name "*.cpp" -o -name "*.h" | xargs clang-format -i || echo "$(YELLOW)⚠ clang-format not available$(NC)"
	@echo "$(GREEN)✓ Firmware formatting complete$(NC)"

format-android: check-gradle ## Format Android code
	@echo "$(BLUE)Formatting Android code...$(NC)"
	cd $(ANDROID_DIR) && $(GRADLE) ktlintFormat || echo "$(YELLOW)⚠ ktlint not configured$(NC)"
	@echo "$(GREEN)✓ Android formatting complete$(NC)"

##@ Documentation

docs: ## Generate all documentation
	@echo "$(BLUE)Generating documentation...$(NC)"
	@$(MKDIR) $(BUILD_DIR)/docs
	@if command -v doxygen >/dev/null 2>&1; then \
		doxygen Doxyfile 2>/dev/null || echo "$(YELLOW)⚠ Doxygen configuration not found$(NC)"; \
	else \
		echo "$(YELLOW)⚠ Doxygen not available for API documentation$(NC)"; \
	fi
	@echo "$(GREEN)✓ Documentation generation complete$(NC)"

##@ Deployment

deploy: firmware android ## Deploy firmware and app
	@echo "$(BLUE)Starting deployment...$(NC)"
	@$(MAKE) firmware-upload
	@$(MAKE) android-install
	@echo "$(GREEN)✓ Deployment complete$(NC)"

release: clean all test ## Create release package
	@echo "$(BLUE)Creating release package...$(NC)"
	@$(MKDIR) $(DIST_DIR)/$(PROJECT_NAME)-$(VERSION)
	@$(CP) $(BUILD_DIR)/firmware.bin $(DIST_DIR)/$(PROJECT_NAME)-$(VERSION)/
	@if [ -f $(ANDROID_RELEASE_APK) ]; then \
		cp $(ANDROID_RELEASE_APK) $(DIST_DIR)/$(PROJECT_NAME)-$(VERSION)/ESPIR-App.apk; \
	else \
		cp $(ANDROID_DEBUG_APK) $(DIST_DIR)/$(PROJECT_NAME)-$(VERSION)/ESPIR-App-debug.apk; \
	fi
	@$(CP) $(HARDWARE_DIR) $(DIST_DIR)/$(PROJECT_NAME)-$(VERSION)/
	@$(CP) $(DOCS_DIR) $(DIST_DIR)/$(PROJECT_NAME)-$(VERSION)/
	@$(CP) README.md $(DIST_DIR)/$(PROJECT_NAME)-$(VERSION)/
	cd $(DIST_DIR) && $(TAR) -czf $(PROJECT_NAME)-$(VERSION)-$(BUILD_DATE).tar.gz $(PROJECT_NAME)-$(VERSION)
	@echo "$(GREEN)✓ Release package created: $(DIST_DIR)/$(PROJECT_NAME)-$(VERSION)-$(BUILD_DATE).tar.gz$(NC)"

##@ Dependencies

deps: deps-firmware deps-android ## Install all dependencies

deps-firmware: check-pio ## Install ESP32 firmware dependencies
	@echo "$(BLUE)Installing ESP32 firmware dependencies...$(NC)"
	cd $(FIRMWARE_DIR) && $(PLATFORMIO) lib install
	@echo "$(GREEN)✓ Firmware dependencies installed$(NC)"

deps-android: check-gradle ## Install Android dependencies
	@echo "$(BLUE)Installing Android dependencies...$(NC)"
	cd $(ANDROID_DIR) && $(GRADLE) build --refresh-dependencies
	@echo "$(GREEN)✓ Android dependencies installed$(NC)"

##@ Maintenance

clean-firmware: ## Clean ESP32 firmware build artifacts
	@echo "$(BLUE)Cleaning ESP32 firmware...$(NC)"
	cd $(FIRMWARE_DIR) && $(PLATFORMIO) run --target clean || true
	@$(RM) .pio || true
	@echo "$(GREEN)✓ Firmware clean complete$(NC)"

clean-android: ## Clean Android build artifacts
	@echo "$(BLUE)Cleaning Android...$(NC)"
	cd $(ANDROID_DIR) && $(GRADLE) clean || true
	@echo "$(GREEN)✓ Android clean complete$(NC)"

clean-docs: ## Clean documentation artifacts
	@echo "$(BLUE)Cleaning documentation...$(NC)"
	@$(RM) $(BUILD_DIR)/docs || true
	@echo "$(GREEN)✓ Documentation clean complete$(NC)"

clean-build: ## Clean build directories
	@echo "$(BLUE)Cleaning build directories...$(NC)"
	@$(RM) $(BUILD_DIR) $(DIST_DIR) || true
	@echo "$(GREEN)✓ Build directories clean complete$(NC)"

##@ Development Tools

check: check-pio check-gradle check-tools ## Check all development tools

check-pio: ## Check PlatformIO installation
	@command -v $(PLATFORMIO) >/dev/null 2>&1 || { \
		echo "$(RED)✗ PlatformIO not found. Install from https://platformio.org/$(NC)"; \
		exit 1; \
	}
	@echo "$(GREEN)✓ PlatformIO found$(NC)"

check-gradle: ## Check Gradle wrapper
	@test -f $(ANDROID_DIR)/gradlew || { \
		echo "$(RED)✗ Gradle wrapper not found at $(ANDROID_DIR)/gradlew$(NC)"; \
		exit 1; \
	}
	@test -x $(ANDROID_DIR)/gradlew || { \
		echo "$(RED)✗ Gradle wrapper not executable$(NC)"; \
		exit 1; \
	}
	@echo "$(GREEN)✓ Gradle wrapper found$(NC)"
	@echo "$(GREEN)✓ Gradle wrapper found$(NC)"

check-tools: ## Check additional development tools
	@echo "$(BLUE)Checking development tools...$(NC)"
	@command -v git >/dev/null 2>&1 && echo "$(GREEN)✓ Git found$(NC)" || echo "$(YELLOW)⚠ Git not found$(NC)"
	@command -v python3 >/dev/null 2>&1 && echo "$(GREEN)✓ Python3 found$(NC)" || echo "$(YELLOW)⚠ Python3 not found$(NC)"
	@command -v clang-format >/dev/null 2>&1 && echo "$(GREEN)✓ clang-format found$(NC)" || echo "$(YELLOW)⚠ clang-format not found$(NC)"
	@command -v doxygen >/dev/null 2>&1 && echo "$(GREEN)✓ Doxygen found$(NC)" || echo "$(YELLOW)⚠ Doxygen not found$(NC)"

info: ## Show project information
	@echo "$(BLUE)ESPIR Project Information$(NC)"
	@echo "=========================="
	@echo "Project Name: $(PROJECT_NAME)"
	@echo "Version: $(VERSION)"
	@echo "Build Date: $(BUILD_DATE)"
	@echo ""
	@echo "Directories:"
	@echo "  Firmware: $(FIRMWARE_DIR)"
	@echo "  Android: $(ANDROID_DIR)"
	@echo "  Hardware: $(HARDWARE_DIR)"
	@echo "  Docs: $(DOCS_DIR)"
	@echo "  Build: $(BUILD_DIR)"
	@echo "  Dist: $(DIST_DIR)"
	@echo ""
	@echo "Build Targets:"
	@echo "  Firmware: $(FIRMWARE_TARGET)"
	@echo "  Android Debug: $(ANDROID_DEBUG_APK)"
	@echo "  Android Release: $(ANDROID_RELEASE_APK)"

##@ Hardware Tools

hardware-flash: firmware ## Flash firmware using external tools
	@echo "$(BLUE)Flashing firmware with esptool...$(NC)"
	@command -v esptool.py >/dev/null 2>&1 || { \
		echo "$(RED)✗ esptool.py not found. Install with: pip install esptool$(NC)"; \
		exit 1; \
	}
	esptool.py --chip esp32 --port /dev/cu.usbserial* --baud 921600 write_flash 0x10000 $(FIRMWARE_TARGET)
	@echo "$(GREEN)✓ Firmware flashed$(NC)"

hardware-erase: ## Erase ESP32 flash memory
	@echo "$(BLUE)Erasing ESP32 flash...$(NC)"
	@command -v esptool.py >/dev/null 2>&1 || { \
		echo "$(RED)✗ esptool.py not found. Install with: pip install esptool$(NC)"; \
		exit 1; \
	}
	esptool.py --chip esp32 --port /dev/cu.usbserial* erase_flash
	@echo "$(GREEN)✓ ESP32 flash erased$(NC)"

##@ Debug Tools

debug-firmware: ## Start firmware debugging session
	@echo "$(BLUE)Starting firmware debug session...$(NC)"
	cd $(FIRMWARE_DIR) && $(PLATFORMIO) debug --environment $(FIRMWARE_ENV)

debug-android: ## Start Android debugging
	@echo "$(BLUE)Starting Android debugging...$(NC)"
	cd $(ANDROID_DIR) && $(GRADLE) installDebug && adb logcat -c && adb logcat | grep ESPIR

monitor-serial: ## Monitor serial output
	@echo "$(BLUE)Monitoring serial output...$(NC)"
	cd $(FIRMWARE_DIR) && $(PLATFORMIO) device monitor --baud 115200

##@ CI/CD

ci: clean all test lint ## Complete CI pipeline
	@echo "$(GREEN)✓ CI pipeline complete$(NC)"

cd: ci release ## Complete CD pipeline
	@echo "$(GREEN)✓ CD pipeline complete$(NC)"

##@ Utilities

size: ## Show build sizes
	@echo "$(BLUE)Build Sizes:$(NC)"
	@if [ -f $(FIRMWARE_TARGET) ]; then \
		echo "Firmware: $$(du -h $(FIRMWARE_TARGET) | cut -f1)"; \
	fi
	@if [ -f $(ANDROID_DEBUG_APK) ]; then \
		echo "Android Debug APK: $$(du -h $(ANDROID_DEBUG_APK) | cut -f1)"; \
	fi
	@if [ -f $(ANDROID_RELEASE_APK) ]; then \
		echo "Android Release APK: $$(du -h $(ANDROID_RELEASE_APK) | cut -f1)"; \
	fi

watch: ## Watch for changes and rebuild
	@echo "$(BLUE)Watching for changes...$(NC)"
	@command -v fswatch >/dev/null 2>&1 || { \
		echo "$(RED)✗ fswatch not found. Install with: brew install fswatch$(NC)"; \
		exit 1; \
	}
	fswatch -o src include ESPIR-App/app/src | xargs -n1 -I{} $(MAKE) all

version: ## Show version information
	@echo "$(PROJECT_NAME) $(VERSION)"

# Include any local customizations
-include Makefile.local