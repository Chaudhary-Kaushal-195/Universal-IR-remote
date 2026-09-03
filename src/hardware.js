import mqtt from 'mqtt';
import { serial as polyfillSerial } from 'web-serial-polyfill';
import { state, MQTT_BROKER, MQTT_TOPIC_TX, MQTT_TOPIC_RX } from './state.js';
import { flashStatus, updateStatusIndicator, renderRemote, renderSignalDebugger } from './ui.js';
import { syncCloudRemotes } from './api.js';

export let mqttClient = null;
const MIN_RAW_CAPTURE_PULSES = 10;

function parseRawLine(line) {
  const parts = line.trim().split(":");
  if (parts.length < 3 || parts[0] !== "RAW") return null;

  const len = Number.parseInt(parts[1], 10);
  const values = parts.slice(2).join(":");
  if (!Number.isFinite(len) || !values) return null;

  return { len, values };
}

function shouldCaptureRaw(raw) {
  return Boolean(
    raw &&
    state.isLearning &&
    state.learningTargetId &&
    raw.len >= MIN_RAW_CAPTURE_PULSES
  );
}

export function setupMQTT() {
  if (mqttClient) {
    try { mqttClient.end(true); } catch (e) {}
  }
  if (state.connectionType !== 'wifi') return;

  console.log("%c🌐 Connecting to MQTT Cloud Broker (HiveMQ)...", "color: #3b82f6; font-weight: bold;");
  
  mqttClient = mqtt.connect(MQTT_BROKER, {
    keepalive: 30,
    clientId: 'WebRemote-' + Math.random().toString(16).substring(2, 10),
    reconnectPeriod: 3000
  });

  mqttClient.on('connect', () => {
    console.log("%c🟢 MQTT Connected to Cloud Broker! Listening for ESP32...", "color: #22c55e; font-weight: bold;");
    mqttClient.subscribe(MQTT_TOPIC_RX);
    updateStatusIndicator();
    // Ask ESP32 for status
    try {
      mqttClient.publish(MQTT_TOPIC_TX, JSON.stringify({ cmd: "WIFI_STATUS" }));
    } catch (e) {}
  });

  mqttClient.on('message', (topic, message) => {
    const rawData = message.toString();
    handleStatusMessages(rawData);

    const raw = parseRawLine(rawData);
    if (shouldCaptureRaw(raw)) {
      handleCapture(raw);
    }
  });

  mqttClient.on('error', (err) => {
    console.warn("MQTT connection error:", err);
  });
}

export async function performUSBConnect() {
  const isAndroid = /Android/i.test(navigator.userAgent);
  const hasNativeSerial = 'serial' in navigator;
  const hasWebUsb = 'usb' in navigator;

  if (!hasNativeSerial && !hasWebUsb) {
    alert("Neither Web Serial nor WebUSB is supported in this browser. Please use Google Chrome.");
    return;
  }
  
  try {
    let port = null;

    // On Android, native Web Serial only exposes Bluetooth, which causes "No compatible devices found"
    // for wired USB OTG. The polyfill uses WebUSB (navigator.usb) which directly supports CP2102/CH340!
    if (isAndroid && hasWebUsb) {
      console.log("Connecting via WebUSB Serial Polyfill for Android OTG...");
      try {
        port = await polyfillSerial.requestPort();
      } catch (polyErr) {
        if (polyErr.name === 'NotFoundError') {
          console.log("WebUSB closed without selection.");
          return;
        }
        console.warn("Polyfill failed, falling back to native serial...", polyErr);
        if (hasNativeSerial) {
          port = await navigator.serial.requestPort();
        } else {
          throw polyErr;
        }
      }
    } else if (hasNativeSerial) {
      try {
        port = await navigator.serial.requestPort();
      } catch (nativeErr) {
        if (nativeErr.name === 'NotFoundError') {
          console.log("Native serial closed without selection.");
          return;
        }
        if (hasWebUsb) {
          console.log("Native serial failed, trying WebUSB polyfill...", nativeErr);
          port = await polyfillSerial.requestPort();
        } else {
          throw nativeErr;
        }
      }
    } else if (hasWebUsb) {
      port = await polyfillSerial.requestPort();
    }

    if (!port) return;

    state.serialPort = port;
    await state.serialPort.open({ baudRate: 115200 });
    state.serialWriter = state.serialPort.writable.getWriter();
    
    state.connectionType = 'serial';
    localStorage.setItem('connectionType', 'serial');
    updateStatusIndicator();
    
    readSerial();
    console.log("%c🔌 USB Serial Connected!", "color: #10b981; font-weight: bold;");
    alert("✅ ESP32 Connected via USB!");
  } catch (err) {
    if (err.name === 'NotFoundError') {
      console.log("USB picker closed without selection.");
    } else {
      console.warn("Serial Connection notice:", err.message);
      alert("USB Notice: " + err.message);
    }
  }
}

export function promptUSBConnect() {
  const modal = document.getElementById('usb-confirm-modal');
  if (modal) {
    modal.classList.add('active');
  } else {
    performUSBConnect();
  }
}

export const connectUSB = promptUSBConnect;

async function readSerial() {
  const reader = state.serialPort.readable.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop();
      for (const line of lines) {
        const trimmedLine = line.trim();
        if (trimmedLine) handleStatusMessages(trimmedLine);

        const raw = parseRawLine(trimmedLine);
        if (shouldCaptureRaw(raw)) {
          handleCapture(raw);
        }
      }
    }
  } catch (err) { console.error("Reader error", err); } finally { reader.releaseLock(); }
}

export function updateHubWifiBadge(status) {
  state.hubWifiStatus = status;
  const badge = document.getElementById('hub-wifi-badge');
  if (!badge) return;
  if (status === 'ONLINE') {
    badge.textContent = 'ONLINE (WIFI ACTIVE)';
    badge.style.background = 'rgba(34, 197, 94, 0.2)';
    badge.style.color = 'var(--success)';
  } else if (status === 'CONNECTING') {
    badge.textContent = 'CONNECTING...';
    badge.style.background = 'rgba(245, 158, 11, 0.2)';
    badge.style.color = '#f59e0b';
  } else {
    badge.textContent = 'OFF (USB ONLY)';
    badge.style.background = 'rgba(100, 116, 139, 0.2)';
    badge.style.color = '#94a3b8';
  }
}

export async function sendSerialCommand(cmd) {
  if (state.serialWriter) {
    try {
      const encoder = new TextEncoder();
      await state.serialWriter.write(encoder.encode(cmd.trim() + "\n"));
      console.log(`%c[SERIAL TX] ${cmd.trim()}`, "color: #a855f7; font-weight: bold;");
      return true;
    } catch (err) {
      console.error("Failed to send serial command:", err);
      return false;
    }
  }
  return false;
}

export async function enableHubWifi() {
  console.log("%c📡 Requesting ESP32 to turn ON WiFi & MQTT...", "color: #3b82f6; font-weight: bold;");
  updateHubWifiBadge('CONNECTING');
  return await sendSerialCommand("CMD:WIFI_START");
}

export async function disableHubWifi() {
  console.log("%c🔌 Requesting ESP32 to turn OFF WiFi (Pure USB Mode)...", "color: #f59e0b; font-weight: bold;");
  updateHubWifiBadge('OFF');
  return await sendSerialCommand("CMD:WIFI_STOP");
}

export async function toggleAutoWifiBoot(enable) {
  return await sendSerialCommand(enable ? "CMD:AUTO_WIFI_ON" : "CMD:AUTO_WIFI_OFF");
}

export async function startLearning() {
  if (state.connectionType === 'serial') {
    await sendSerialCommand("CMD:LEARN_START");
  } else if (state.connectionType === 'wifi' && mqttClient && mqttClient.connected) {
    mqttClient.publish(MQTT_TOPIC_TX, JSON.stringify({ cmd: "LEARN_START" }));
  }
}

export async function stopLearning() {
  if (state.connectionType === 'serial') {
    await sendSerialCommand("CMD:LEARN_STOP");
  } else if (state.connectionType === 'wifi' && mqttClient && mqttClient.connected) {
    mqttClient.publish(MQTT_TOPIC_TX, JSON.stringify({ cmd: "LEARN_STOP" }));
  }
}

export function handleStatusMessages(line) {
  if (line.startsWith("STATUS:REPLAYING_RAW_SIGNAL")) {
    console.log("%c🔥 [EMIT] Replaying Physical Timing Pattern...", "color: #ef4444; font-weight: bold;");
    flashStatus('fire');
  } else if (line === "STATUS:SENSING_SIGNAL") {
    console.log("%c📡 [SENSE] Incoming IR Signal Detected!", "color: #22c55e; font-weight: bold;");
    flashStatus('sense');
  } else if (line === "STATUS:ONLINE") {
    console.log("%c🟢 [HUB] ESP32 is Online!", "color: #22c55e; font-weight: bold;");
    state.isDeviceOnline = true;
    updateHubWifiBadge('ONLINE');
    updateStatusIndicator();
  } else if (line === "STATUS:OFFLINE") {
    console.log("%c🔴 [HUB] ESP32 has gone Offline!", "color: #ef4444; font-weight: bold;");
    state.isDeviceOnline = false;
    updateHubWifiBadge('OFF');
    updateStatusIndicator();
  } else if (line.startsWith("STATUS:WIFI_ENABLED")) {
    console.log("%c🌐 [HUB] WiFi Radio Activated on ESP32", "color: #3b82f6; font-weight: bold;");
    updateHubWifiBadge('CONNECTING');
  } else if (line.startsWith("STATUS:WIFI_DISABLED")) {
    console.log("%c🔌 [HUB] WiFi Turned OFF on ESP32 (Clean USB Mode)", "color: #10b981; font-weight: bold;");
    updateHubWifiBadge('OFF');
  } else if (line.startsWith("STATUS:WIFI_CONNECTING")) {
    console.log("%c⏳ [HUB] ESP32 Connecting to WiFi...", "color: #f59e0b;");
    updateHubWifiBadge('CONNECTING');
  } else if (line.startsWith("STATUS:WIFI_TIMEOUT_RETRYING")) {
    console.log("%c⚠️ [HUB] WiFi Connect timed out, retrying...", "color: #ef4444;");
    updateHubWifiBadge('CONNECTING');
  } else if (line.startsWith("STATUS:WIFI_CONNECTED")) {
    console.log(`%c🟢 [HUB] ESP32 Connected to WiFi! (${line})`, "color: #22c55e; font-weight: bold;");
    updateHubWifiBadge('ONLINE');
  } else if (line.startsWith("STATUS:LEARNING_ACTIVE")) {
    console.log("%c🎯 [HUB] IR Receiver Active (Point remote at Hub)", "color: #22c55e; font-weight: bold;");
  } else if (line.startsWith("STATUS:LEARNING_IDLE") || line.startsWith("STATUS:LEARNING_COMPLETE")) {
    console.log("%c💤 [HUB] IR Receiver Idle (Noise Filter Active)", "color: #64748b;");
  } else if (line.startsWith("STATUS:AUTO_WIFI_ENABLED")) {
    console.log("%c💾 [HUB] Auto-WiFi on Boot Enabled", "color: #3b82f6; font-weight: bold;");
  } else if (line.startsWith("STATUS:AUTO_WIFI_DISABLED")) {
    console.log("%c💾 [HUB] Auto-WiFi on Boot Disabled", "color: #64748b; font-weight: bold;");
  } else if (line.startsWith("RAW:")) {
    const raw = parseRawLine(line);
    if (!raw) return;

    if (raw.len < MIN_RAW_CAPTURE_PULSES) {
      console.log(`%c⚠️ [SENSE] Ignored short IR noise (${raw.len} pulses)`, "color: #f59e0b; font-weight: bold;");
    } else if (state.isLearning && state.learningTargetId) {
      console.log(`%c📸 [CLONE] Pattern Captured (${raw.len} pulses)`, "color: #3b82f6; font-weight: bold;");
    } else {
      console.log(`%c📡 [SENSE] Raw IR Signal Seen (${raw.len} pulses)`, "color: #22c55e; font-weight: bold;");
    }
  } else if (line.startsWith("TELEMETRY:")) {
    // TELEMETRY:EXPECTED=284:RECEIVED=284:FIRST4=8950,4400,600,1650:LAST4=550,550,600,1650
    const parts = line.split(":");
    const expPart = parts.find(p => p.startsWith("EXPECTED="));
    const recPart = parts.find(p => p.startsWith("RECEIVED="));
    const f4Part = parts.find(p => p.startsWith("FIRST4="));
    const l4Part = parts.find(p => p.startsWith("LAST4="));

    const expected = expPart ? parseInt(expPart.split("=")[1]) : 0;
    const received = recPart ? parseInt(recPart.split("=")[1]) : 0;
    const first4 = f4Part ? f4Part.split("=")[1] : "";
    const last4 = l4Part ? l4Part.split("=")[1] : "";

    state.lastTelemetry = { expected, received, first4, last4, timestamp: new Date().toLocaleTimeString() };

    console.group(`%c🔬 [ARDUINO HARDWARE TELEMETRY] Pulse Verification`, "color: #7c3aed; font-size: 13px; font-weight: bold;");
    console.log(`Expected by Arduino: ${expected} pulses`);
    console.log(`Actually Received by Arduino: ${received} pulses`);
    console.log(`First 4 Timings Received: [${first4}]`);
    console.log(`Last 4 Timings Received: [${last4}]`);
    if (expected === received) {
      console.log("%c✅ [100% PULSE COUNT MATCH] All pulses downloaded into Uno RAM intact!", "color: #16a34a; font-weight: bold;");
    } else {
      console.error(`%c❌ [CORRUPTION] Web App sent ${expected} pulses, but Uno buffer only received ${received}!`, "color: #dc2626; font-weight: bold;");
    }
    console.groupEnd();

    renderSignalDebugger();
  } else if (line.startsWith("CAPTURE_INFO:")) {
    console.log(`%c📥 [UNO SENSE INFO] ${line}`, "color: #0284c7; font-weight: bold;");
  } else if (line.startsWith("WARN:DROPPED_PULSES:")) {
    console.warn(`%c⚠️ [HARDWARE BUFFER WARNING] ${line}`, "color: #f59e0b; font-weight: bold;");
  } else if (line === "SEND_OK") {
    console.log("%c✅ [OK] Replay Successful", "color: #6366f1;");
  } else if (line.startsWith("HUB_READY")) {
    console.log(`%c🔌 [LINK] ${line}`, "color: #00e676; font-weight: bold;");
  } else if (line.startsWith("HUB_ALIVE")) {
    // Keep console tidy for regular heartbeats
  } else {
    console.log("Incoming Serial:", line);
  }
}

export function handleCapture(rawInput, triggerUIRefresh) {
  const raw = typeof rawInput === 'string' ? parseRawLine(rawInput) : rawInput;
  if (!shouldCaptureRaw(raw)) return;

  const buttonId = state.learningTargetId;
  const valuesArr = raw.values.split(',').map(Number);
  
  state.learnedCodes[buttonId] = {
    type: 'raw',
    len: String(raw.len),
    values: raw.values
  };

  state.lastCapturedSignal = {
    buttonId,
    len: raw.len,
    values: raw.values,
    count: valuesArr.length,
    first8: valuesArr.slice(0, 8),
    last6: valuesArr.slice(-6),
    timestamp: new Date().toLocaleTimeString()
  };

  console.group(`%c📥 [IR SIGNAL CAPTURED FROM REMOTE] ${buttonId} (${raw.len} Pulses)`, "color: #0284c7; font-size: 13px; font-weight: 800;");
  console.log("🎯 Target Button:", buttonId);
  console.log("📊 Total Pulses:", raw.len);
  console.log("⏱️ Header Mark/Space:", `${valuesArr[0]}μs / ${valuesArr[1]}μs`);
  console.log("🔢 First 8 Timings (μs):", valuesArr.slice(0, 8));
  console.log("🔢 Last 6 Timings (μs):", valuesArr.slice(-6));
  console.log("📋 Full Raw Sequence:", raw.values);
  console.groupEnd();

  renderSignalDebugger();

  localStorage.setItem('learnedCodes', JSON.stringify(state.learnedCodes));
  syncCloudRemotes();
  stopLearning(); // Tell ESP32 / Uno to pause receiver immediately!
  
  if (triggerUIRefresh) triggerUIRefresh(buttonId);
  else {
    state.isLearning = false;
    state.learningTargetId = null;
    const learnBtn = document.getElementById('learn-btn');
    if (learnBtn) {
        learnBtn.classList.remove('learning-mode');
        learnBtn.innerHTML = `<i data-lucide="mic" style="width:16px; height:16px; margin-right:8px"></i> Enter Learning Mode`;
        if (window.lucide) window.lucide.createIcons();
    }

    document.querySelectorAll('.remote-btn').forEach(b => b.classList.remove('learning-target'));
    
    const learningStatus = document.getElementById('learning-status');
    if (learningStatus) {
        learningStatus.textContent = `Success! "${buttonId.split('_').join(' ')}" cloned!`;
        setTimeout(() => {
            learningStatus.textContent = "";
            const configModal = document.getElementById('config-modal');
            if (configModal) configModal.classList.remove('active');
        }, 2000);
    }
    
    renderRemote();
  }
}

export async function scanHardware() {
  console.log("Running Hardware Diagnostics...");
  let hasHardware = false;

  const hwCardUsb = document.getElementById('hw-card-usb');
  const hwStatusUsb = document.getElementById('hw-status-usb');
  const hwCardWifi = document.getElementById('hw-card-wifi');
  const hwStatusWifi = document.getElementById('hw-status-wifi');
  const hwNoDeviceWarning = document.getElementById('hw-no-device-warning');

  if (!hwCardUsb) return; // Prevent crashes if not mapped

  if ('serial' in navigator) {
    try {
      const ports = await navigator.serial.getPorts();
      if (ports.length > 0 || state.serialPort) {
        hwCardUsb.style.borderLeft = '3px solid var(--success)';
        hwStatusUsb.style.color = 'var(--success)';
        hwStatusUsb.textContent = 'CONNECTED';
        hasHardware = true;
      } else {
        hwCardUsb.style.borderLeft = '3px solid #64748b';
        hwStatusUsb.style.color = '#64748b';
        hwStatusUsb.textContent = 'NO PERMISSIONS';
      }
    } catch(e) {
      hwCardUsb.style.borderLeft = '3px solid var(--danger)';
      hwStatusUsb.style.color = 'var(--danger)';
      hwStatusUsb.textContent = 'FAILED';
    }
  } else {
    hwCardUsb.style.borderLeft = '3px solid var(--danger)';
    hwStatusUsb.style.color = 'var(--danger)';
    hwStatusUsb.textContent = 'NOT SUPPORTED';
  }

  if (mqttClient && mqttClient.connected) {
    hwCardWifi.style.borderLeft = '3px solid var(--success)';
    hwStatusWifi.style.color = 'var(--success)';
    hwStatusWifi.textContent = 'CONNECTED';
    hasHardware = true;
  } else {
    hwCardWifi.style.borderLeft = '3px solid #64748b';
    hwStatusWifi.style.color = '#64748b';
    hwStatusWifi.textContent = 'DISCONNECTED';
  }

  if (hasHardware) {
    hwNoDeviceWarning.style.display = 'none';
  } else {
    hwNoDeviceWarning.style.display = 'flex';
  }
}

export async function fireSignal(buttonId) {
  let signal = state.learnedCodes[buttonId];
  if (!signal && (buttonId === 'AC_LIGHT_ON' || buttonId === 'AC_LIGHT_OFF')) {
    signal = state.learnedCodes['AC_LIGHT'];
  }
  if (!signal) {
    alert("This button hasn't been programmed yet!");
    return;
  }

  const payload = signal.len + ":" + signal.values;
  const valuesArr = signal.values.split(',').map(Number);
  let transmitted = false;

  state.lastTransmittedSignal = {
    buttonId,
    len: signal.len,
    values: signal.values,
    count: valuesArr.length,
    first8: valuesArr.slice(0, 8),
    last6: valuesArr.slice(-6),
    timestamp: new Date().toLocaleTimeString()
  };

  console.group(`%c📤 [IR SIGNAL TRANSMITTING] ${buttonId} (${signal.len} Pulses)`, "color: #dc2626; font-size: 13px; font-weight: 800;");
  console.log("🎯 Target Button:", buttonId);
  console.log("📊 Pulses to Transmit:", signal.len);
  console.log("⏱️ Header Mark/Space:", `${valuesArr[0]}μs / ${valuesArr[1]}μs`);
  console.log("🔢 First 8 Timings (μs):", valuesArr.slice(0, 8));
  console.log("🔢 Last 6 Timings (μs):", valuesArr.slice(-6));
  console.groupEnd();

  renderSignalDebugger();

  // 1. Send via USB Serial if connected
  if (state.serialWriter) {
    try {
      flashStatus('fire');
      const encoder = new TextEncoder();
      const fullPayload = "SEND_RAW:" + payload + "\n";
      
      // Chunk in 32 bytes with 8ms delay to prevent Arduino AVR 64-byte serial buffer overflow
      for (let i = 0; i < fullPayload.length; i += 32) {
        const chunk = fullPayload.substring(i, i + 32);
        await state.serialWriter.write(encoder.encode(chunk));
        await new Promise(res => setTimeout(res, 8));
      }
      transmitted = true;
      console.log("%c🔌 [USB TX] Dispatched via Serial to Uno", "color: #10b981; font-weight: bold;");
    } catch (serialErr) {
      console.warn("⚠️ [USB TX NOTICE] Serial write interrupted:", serialErr.message);
      try {
        await state.serialWriter.releaseLock();
      } catch (e) {}
      state.serialWriter = null;
      updateStatusIndicator();
    }
  }

  // 2. Send via Wi-Fi MQTT if connected
  if (mqttClient && mqttClient.connected) {
    flashStatus('fire');
    const wifiPayload = JSON.stringify({
      type: 'raw',
      len: parseInt(signal.len),
      values: signal.values
    });
    mqttClient.publish(MQTT_TOPIC_TX, wifiPayload);
    transmitted = true;
    console.log("%c🌐 [MQTT TX] Dispatched via Wi-Fi", "color: #3b82f6; font-weight: bold;");
  }

  // 3. Fallback Demo flash if neither physical bridge is connected
  if (!transmitted) {
    flashStatus('fire');
    console.log(`[DEMO] Triggered ${buttonId}`, signal);
  }
}
