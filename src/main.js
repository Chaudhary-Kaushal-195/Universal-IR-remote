import './style.css';
import { registerSW } from 'virtual:pwa-register';
import { isSupabaseEnabled, supabase } from './supabaseClient.js';
import { state } from './state.js';
import { setupMQTT, connectUSB, performUSBConnect, scanHardware, fireSignal, handleCapture, enableHubWifi, disableHubWifi, startLearning, stopLearning } from './hardware.js';
import { renderRemote, updateStatusIndicator, toggleAC, updateAuthUI, renderSignalDebugger } from './ui.js';
import { initAuth, fetchCloudRemotes, loadGlobalDatabase, syncCloudRemotes } from './api.js';

// Initialize PWA Service Worker aggressively
registerSW({ immediate: true });

// DOM Elements
const authForm = document.getElementById('auth-form');
const emailInput = document.getElementById('auth-email');
const passwordInput = document.getElementById('auth-password');
const loginBtn = document.getElementById('login-btn');
const signupBtn = document.getElementById('signup-btn');
const logoutBtn = document.getElementById('logout-btn');

const remoteContent = document.getElementById('remote-content');
const tabs = document.querySelectorAll('.tab');
const configBtn = document.getElementById('config-btn');
const configModal = document.getElementById('config-modal');
const statusIndicator = document.getElementById('connection-status');

const learnBtn = document.getElementById('learn-btn');
const learningStatus = document.getElementById('learning-status');

const dbSelect = document.getElementById('db-select');
const dbImportBtn = document.getElementById('db-import-btn');

const usbConnectBtn = document.getElementById('usb-connect');
const wifiConnectBtn = document.getElementById('wifi-connect');
const wifiConfig = document.getElementById('wifi-config');
const saveWifiBtn = document.getElementById('save-config');
const ipInput = document.getElementById('esp-ip');
const btnEnableHubWifi = document.getElementById('btn-enable-hub-wifi');
const btnDisableHubWifi = document.getElementById('btn-disable-hub-wifi');

const hardwareModal = document.getElementById('hardware-modal');
const closeHardwareModal = document.getElementById('close-hardware-modal');
const closeConfigModal = document.getElementById('close-config-modal');
const hwRefreshBtn = document.getElementById('hw-refresh-btn');
const modalTabs = document.querySelectorAll('.modal-tab');

const exportCodesBtn = document.getElementById('export-codes-btn');
const importCodesBtn = document.getElementById('import-codes-btn');
const copyCodesBtn = document.getElementById('copy-codes-btn');
const importFileInput = document.getElementById('import-file-input');
const pasteCodesBtn = document.getElementById('paste-codes-btn');

function init() {
  updateStatusIndicator();
  renderRemote();
  setupEventListeners();
  if (window.lucide) window.lucide.createIcons();

  // Core Network Setup Layer
  setupMQTT();

  // Auth & Cloud Layer
  initAuth((user) => {
    updateAuthUI(user);
    if (user) {
      fetchCloudRemotes().then(() => renderRemote());
    }
  });

  // DB Sync Layer
  loadGlobalDatabase();

  console.log("%c🚀 Universal Hub Architecture Engine Started", "color: #6366f1; font-weight: bold; font-size: 1.2rem;");
}

function setupEventListeners() {
  // Navigation Tabs Processing
  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      state.activeLayout = tab.dataset.device;
      renderRemote();
    });
  });

  // Modal Sub-Tabs (Configuration)
  modalTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      modalTabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      const target = tab.dataset.tab;
      ['hardware', 'account', 'database', 'debugger'].forEach(key => {
        const pane = document.getElementById(`pane-${key}`);
        if (pane) {
          pane.style.display = key === target ? 'flex' : 'none';
        }
      });
      if (target === 'debugger') {
        renderSignalDebugger();
      }
    });
  });

  const btnCopyDebugLog = document.getElementById('btn-copy-debug-log');
  if (btnCopyDebugLog) {
    btnCopyDebugLog.addEventListener('click', () => {
      const logData = {
        captured_from_remote: state.lastCapturedSignal,
        transmitted_to_ac: state.lastTransmittedSignal,
        arduino_hardware_telemetry: state.lastTelemetry,
        all_learned_buttons: Object.keys(state.learnedCodes),
        timestamp: new Date().toISOString()
      };
      navigator.clipboard.writeText(JSON.stringify(logData, null, 2))
        .then(() => alert("📋 Diagnostic log copied to clipboard! You can paste it here to analyze."))
        .catch(() => alert("Failed to copy log. Open F12 console to view."));
    });
  }

  // Main UI Modal Interactions
  configBtn.addEventListener('click', () => configModal.classList.add('active'));
  if (closeConfigModal) {
    closeConfigModal.addEventListener('click', () => configModal.classList.remove('active'));
  }
  statusIndicator.addEventListener('click', () => {
    hardwareModal.classList.add('active');
    scanHardware();
  });
  closeHardwareModal.addEventListener('click', () => hardwareModal.classList.remove('active'));
  hwRefreshBtn.addEventListener('click', () => scanHardware());

  window.addEventListener('click', (e) => {
    if (e.target === configModal) configModal.classList.remove('active');
    if (e.target === hardwareModal) hardwareModal.classList.remove('active');
  });

  // Supabase Global Code Import
  dbImportBtn.addEventListener('click', () => {
    const brand = dbSelect.value;
    if (state.globalDeviceDatabase[brand]) {
      state.learnedCodes = { ...state.learnedCodes, ...state.globalDeviceDatabase[brand] };
      localStorage.setItem('learnedCodes', JSON.stringify(state.learnedCodes));
      syncCloudRemotes();
      renderRemote();
      alert(`Successfully downloaded ${brand} profiles from Global Database!`);
    } else {
      alert("No data available for " + brand);
    }
  });

  // USB Connection Confirmation Dialog Elements (Matches User Request)
  const usbConfirmModal = document.getElementById('usb-confirm-modal');
  const btnCancelUsb = document.getElementById('btn-cancel-usb');
  const btnConfirmUsb = document.getElementById('btn-confirm-usb');

  if (usbConnectBtn) {
    usbConnectBtn.addEventListener('click', () => {
      if (usbConfirmModal) {
        usbConfirmModal.classList.add('active');
        if (window.lucide) window.lucide.createIcons();
      } else {
        performUSBConnect();
      }
    });
  }

  if (btnCancelUsb) {
    btnCancelUsb.addEventListener('click', () => {
      if (usbConfirmModal) usbConfirmModal.classList.remove('active');
    });
  }

  if (btnConfirmUsb) {
    btnConfirmUsb.addEventListener('click', async () => {
      if (usbConfirmModal) usbConfirmModal.classList.remove('active');
      await performUSBConnect();
    });
  }

  wifiConnectBtn.addEventListener('click', async () => {
    state.connectionType = 'wifi';
    localStorage.setItem('connectionType', 'wifi');
    updateStatusIndicator();
    setupMQTT();
    wifiConfig.style.display = wifiConfig.style.display === 'none' ? 'block' : 'none';
  });

  if (btnEnableHubWifi) {
    btnEnableHubWifi.addEventListener('click', async () => {
      const sent = await enableHubWifi();
      if (!sent) {
        alert("Please connect via USB first to command the ESP32 Hub.");
      }
    });
  }

  if (btnDisableHubWifi) {
    btnDisableHubWifi.addEventListener('click', async () => {
      const sent = await disableHubWifi();
      if (!sent) {
        alert("Please connect via USB first to command the ESP32 Hub.");
      }
    });
  }

  saveWifiBtn.addEventListener('click', () => {
    if (ipInput.value) {
      state.espIp = ipInput.value;
      localStorage.setItem('espIp', state.espIp);
      state.connectionType = 'wifi';
      localStorage.setItem('connectionType', 'wifi');
      updateStatusIndicator();
      setupMQTT();
      enableHubWifi(); // Request plugged-in ESP32 to turn ON WiFi radio
      wifiConfig.style.display = 'none';
      configModal.classList.remove('active');
    }
  });

  // Hub Security Password Configuration
  const inputHubPassword = document.getElementById('input-hub-password');
  const btnSaveHubPassword = document.getElementById('btn-save-hub-password');
  if (inputHubPassword) {
    inputHubPassword.value = state.hubPassword || localStorage.getItem('hubPassword') || 'HubSecureKey2026';
  }
  if (btnSaveHubPassword) {
    btnSaveHubPassword.addEventListener('click', () => {
      const val = inputHubPassword ? inputHubPassword.value.trim() : '';
      if (!val) return alert("Please enter a valid password.");
      state.hubPassword = val;
      localStorage.setItem('hubPassword', val);
      alert("✅ Hub Security Password Saved! All commands are now signed with this key.");
    });
  }

  // Supabase Authentication Handling
  loginBtn.addEventListener('click', async () => {
    if (!isSupabaseEnabled) return alert("Cloud sync is disabled. Add a valid Supabase config and set VITE_SUPABASE_ENABLED=true to log in.");

    const { error } = await supabase.auth.signInWithPassword({
      email: emailInput.value, password: passwordInput.value
    });
    if (error) alert(error.message);
  });

  signupBtn.addEventListener('click', async () => {
    if (!isSupabaseEnabled) return alert("Cloud sync is disabled. Add a valid Supabase config and set VITE_SUPABASE_ENABLED=true to sign up.");

    const { error } = await supabase.auth.signUp({
      email: emailInput.value, password: passwordInput.value
    });
    if (error) alert(error.message);
    else alert("Success! Check your email for confirmation!");
  });

  logoutBtn.addEventListener('click', async () => {
    if (!isSupabaseEnabled) return;

    await supabase.auth.signOut();
  });

  // ========================================================
  // Backup & Storage Logic (Cross-Platform JSON Format)
  // ========================================================
  function createStandardBackupJson() {
    const acCodes = {};
    const tvCodes = {};
    const lightCodes = {};
    const fanCodes = {};

    for (const [key, val] of Object.entries(state.learnedCodes || {})) {
      if (key.startsWith('AC_')) acCodes[key] = val;
      else if (key.startsWith('TV_')) tvCodes[key] = val;
      else if (key.startsWith('LIGHT_')) lightCodes[key] = val;
      else if (key.startsWith('FAN_')) fanCodes[key] = val;
      else acCodes[key] = val;
    }

    return JSON.stringify({
      backup_type: "FULL_BACKUP",
      version: 2,
      timestamp: Date.now(),
      hubId: state.hubId || "universal-ir-hub-01",
      devices: {
        AC: {
          active_profile: 0,
          profiles: [{ index: 0, name: "AC 1", codes: acCodes }]
        },
        TV: {
          active_profile: 0,
          profiles: [{ index: 0, name: "TV 1", codes: tvCodes }]
        },
        LIGHT: {
          active_profile: 0,
          profiles: [{ index: 0, name: "Light 1", codes: lightCodes }]
        },
        FAN: {
          active_profile: 0,
          profiles: [{ index: 0, name: "Fan 1", codes: fanCodes }]
        }
      },
      custom_buttons: [],
      raw_entries: state.learnedCodes
    }, null, 2);
  }

  // 1. Export JSON File
  exportCodesBtn.addEventListener('click', () => {
    try {
      const dataStr = createStandardBackupJson();
      const blob = new Blob([dataStr], { type: 'application/json' });
      const url = URL.createObjectURL(blob);

      const link = document.createElement('a');
      link.href = url;
      link.download = `ir_hub_backup_${new Date().toISOString().split('T')[0]}.json`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (err) {
      alert("Export failed: " + err.message);
    }
  });

  // 2. Copy JSON Text to Clipboard
  if (copyCodesBtn) {
    copyCodesBtn.addEventListener('click', async () => {
      try {
        const dataStr = createStandardBackupJson();
        if (navigator.clipboard && navigator.clipboard.writeText) {
          await navigator.clipboard.writeText(dataStr);
          alert("📋 Full Backup JSON copied to clipboard! You can paste it directly into the Android app.");
        } else {
          prompt("Copy this JSON backup:", dataStr);
        }
      } catch (err) {
        alert("Copy failed: " + err.message);
      }
    });
  }

  // 3. Smart Universal Normalization for Android App & Web App JSON
  function normalizeImportedCodes(data) {
    if (!data) return {};
    if (typeof data === 'string') {
      try {
        data = JSON.parse(data);
      } catch (e) {
        return {};
      }
    }
    if (typeof data !== 'object') return {};

    const normalized = {};

    function addCode(key, value) {
      if (!value) return;
      if (typeof value === 'string') {
        try {
          value = JSON.parse(value);
        } catch (e) {
          if (value.includes(',')) {
            value = { type: 'raw', values: value };
          } else {
            return;
          }
        }
      }

      if (typeof value !== 'object' || !value.values) return;

      // Strip Android prefix like "code_AC_0_AC_TEMP_19" -> "AC_TEMP_19"
      let cleanKey = key;
      const prefixMatch = key.match(/^code_[A-Za-z0-9]+_[0-9]+_(.+)$/);
      if (prefixMatch) {
        cleanKey = prefixMatch[1];
      }

      const payload = {
        type: value.type || 'raw',
        len: String(value.len || (value.values.split(',').length)),
        values: String(value.values).trim()
      };

      normalized[cleanKey] = payload;

      // Smart mapping for Light
      if (cleanKey === 'AC_LIGHT') {
        if (!normalized['AC_LIGHT_ON']) normalized['AC_LIGHT_ON'] = payload;
        if (!normalized['AC_LIGHT_OFF']) normalized['AC_LIGHT_OFF'] = payload;
      }
    }

    // A. If Single Device Profile (data.codes)
    if (data.codes && typeof data.codes === 'object') {
      for (const [k, v] of Object.entries(data.codes)) {
        addCode(k, v);
      }
    }

    // B. If Full Hub Backup (data.devices)
    if (data.devices && typeof data.devices === 'object') {
      for (const [_, catObj] of Object.entries(data.devices)) {
        if (!catObj) continue;
        const profiles = catObj.profiles || [];
        for (const p of profiles) {
          if (p && p.codes && typeof p.codes === 'object') {
            for (const [k, v] of Object.entries(p.codes)) {
              addCode(k, v);
            }
          }
        }
      }
    }

    // C. If raw_entries is present
    if (data.raw_entries && typeof data.raw_entries === 'object') {
      for (const [k, v] of Object.entries(data.raw_entries)) {
        addCode(k, v);
      }
    }

    // D. If Custom Buttons array is present
    const customList = Array.isArray(data.buttons) ? data.buttons : (Array.isArray(data.custom_buttons) ? data.custom_buttons : (Array.isArray(data) ? data : null));
    if (customList) {
      for (const item of customList) {
        const signal = item ? (item.codeJson || item.signalData || item.code || item.irCode) : null;
        if (item && signal) {
          const btnName = (item.name || item.id || 'CUSTOM_BTN').toUpperCase().replace(/[^A-Z0-9]/g, '_');
          addCode(btnName, signal);
        }
      }
    }

    // E. Direct flat code map / legacy keys
    for (const [k, v] of Object.entries(data)) {
      if (['backup_type', 'version', 'timestamp', 'hubId', 'category', 'profile_name', 'profile_index', 'code_count', 'button_count', 'devices', 'custom_buttons', 'raw_entries', 'buttons'].includes(k)) {
        continue;
      }
      addCode(k, v);
    }

    return normalized;
  }

  function applyImportedData(rawJson) {
    const importedData = typeof rawJson === 'string' ? JSON.parse(rawJson) : rawJson;
    const normalized = normalizeImportedCodes(importedData);
    const count = Object.keys(normalized).length;

    if (count === 0) {
      throw new Error("No valid IR signal codes found in JSON.");
    }

    // Merge into state and localStorage
    state.learnedCodes = { ...state.learnedCodes, ...normalized };
    localStorage.setItem('learnedCodes', JSON.stringify(state.learnedCodes));

    syncCloudRemotes();
    renderRemote();
    return count;
  }

  // 4. Import JSON File
  importCodesBtn.addEventListener('click', () => importFileInput.click());

  importFileInput.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const count = applyImportedData(event.target.result);
        alert(`✅ Import Successful! ${count} IR button codes have been imported and mapped.`);
      } catch (err) {
        console.error("Import error:", err);
        alert("Import failed: " + err.message);
      }
      importFileInput.value = ''; // Reset for next use
    };
    reader.readAsText(file);
  });

  // 5. Paste JSON Text from Clipboard / Prompt
  if (pasteCodesBtn) {
    pasteCodesBtn.addEventListener('click', async () => {
      let text = '';
      try {
        if (navigator.clipboard && navigator.clipboard.readText) {
          text = await navigator.clipboard.readText();
        }
      } catch (clipErr) {
        console.warn("Clipboard read not permitted, falling back to prompt.", clipErr);
      }

      if (!text || (!text.includes('{') && !text.includes('['))) {
        text = prompt("Paste your JSON code database (or Android export) here:");
      }

      if (!text) return;

      try {
        const count = applyImportedData(text);
        alert(`✅ Import Successful! ${count} IR button codes have been imported and mapped.`);
      } catch (err) {
        alert("Import failed: " + err.message);
      }
    });
  }

  // Main UI Grid Traversal (Dispatch Loop)
  remoteContent.addEventListener('click', async (e) => {
    const btn = e.target.closest('.remote-btn');
    if (!btn) return;

    // Custom AC Graphic Handler (Visual Dialing)
    if (btn.id === 'AC_TEMP_UP' || btn.id === 'AC_TEMP_DOWN') {
      const display = document.getElementById('ac-temp-display');
      toggleAC(display, btn.id === 'AC_TEMP_UP' ? 1 : -1);
      renderRemote(); // Update dot highlight based on new degree

      if (!state.isLearning) {
        fireSignal('AC_TEMP_' + state.acTemp);
      }
      return;
    }

    if (state.isLearning) {
      if (btn.id === 'AC_TEMP_DISPLAY') {
        state.learningTargetId = 'AC_TEMP_' + state.acTemp;
      } else {
        state.learningTargetId = btn.id;
      }
      learningStatus.textContent = `Point original remote at Hub and press the physical button...`;
      document.querySelectorAll('.remote-btn').forEach(b => b.classList.remove('learning-target'));
      btn.classList.add('learning-target');

      // Trigger Wi-Fi poll if needed (USB serial listens automatically)
      if (state.connectionType === 'wifi') {
        console.log("%c📡 [SENSE] Listening for MQTT Learn Mode payload...", "color: #22c55e; font-weight: bold;");
        // Capture will be handled asynchronously by mqttClient.on('message') in hardware.js
      }

    } else {
      if (btn.id === 'AC_TEMP_DISPLAY') {
        fireSignal('AC_TEMP_' + state.acTemp);
      } else {
        fireSignal(btn.id);
      }
    }
  });

  // Initiating Reverse-Engineering Module
  learnBtn.addEventListener('click', async () => {
    if (state.connectionType === 'demo') return alert("Cloning requires a hardware connection.");

    if (!state.isLearning) {
      state.isLearning = true;
      learnBtn.classList.add('learning-mode');
      learnBtn.innerHTML = `Cancel Learning`; // Removed icon to avoid flickering, clean text
      learningStatus.textContent = `Step 1: Click a button on the virtual remote behind this modal to map it.`;
      startLearning();
    } else {
      // Cancel
      state.isLearning = false;
      state.learningTargetId = null;
      learnBtn.classList.remove('learning-mode');
      learnBtn.innerHTML = `<i data-lucide="mic" style="width:16px; height:16px; margin-right:8px"></i> Enter Learning Mode`;
      if (window.lucide) lucide.createIcons();
      learningStatus.textContent = "";
      document.querySelectorAll('.remote-btn').forEach(b => b.classList.remove('learning-target'));
      stopLearning();
    }
  });
}

init();
