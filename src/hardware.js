import mqtt from 'mqtt';
import { state, MQTT_BROKER, MQTT_TOPIC_TX, MQTT_TOPIC_RX } from './state.js';
import { flashStatus, updateStatusIndicator, renderRemote } from './ui.js';
import { syncCloudRemotes } from './api.js';

export let mqttClient = null;

export function setupMQTT() {
  if (mqttClient) {
    mqttClient.end();
  }
  if (state.connectionType !== 'wifi') return;

  mqttClient = mqtt.connect(MQTT_BROKER);

  mqttClient.on('connect', () => {
    console.log("%c🌐 Global MQTT Connected!", "color: #3b82f6; font-weight: bold;");
    mqttClient.subscribe(MQTT_TOPIC_RX);
    updateStatusIndicator();
  });

  mqttClient.on('message', (topic, message) => {
    const rawData = message.toString();
    handleStatusMessages(rawData);
  });
}

export async function connectUSB() {
  if (!('serial' in navigator)) {
    return alert("Web Serial API not supported in this browser. Use Chrome.");
  }
  
  try {
    state.serialPort = await navigator.serial.requestPort();
    await state.serialPort.open({ baudRate: 115200 });
    state.serialWriter = state.serialPort.writable.getWriter();
    
    state.connectionType = 'serial';
    localStorage.setItem('connectionType', 'serial');
    updateStatusIndicator();
    
    readSerial();
  } catch (err) {
    alert("Serial Connection Failed: " + err.message);
  }
}

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
        
        if (trimmedLine.startsWith("RAW:") && state.isLearning && state.learningTargetId) {
          handleCapture(`RAW:${trimmedLine.split(":")[1]}:${trimmedLine.split(":")[2]}`);
        }
      }
    }
  } catch (err) { console.error("Reader error", err); } finally { reader.releaseLock(); }
}

export function handleStatusMessages(line) {
  if (line.startsWith("STATUS:REPLAYING_RAW_SIGNAL")) {
    console.log("%c🔥 [EMIT] Replaying Physical Timing Pattern...", "color: #ef4444; font-weight: bold;");
    flashStatus('fire');
  } else if (line === "STATUS:SENSING_SIGNAL") {
    console.log("%c📡 [SENSE] Incoming IR Signal Detected!", "color: #22c55e; font-weight: bold;");
    flashStatus('sense');
  } else if (line.startsWith("RAW:")) {
    console.log(`%c📸 [CLONE] Pattern Captured (${line.split(":")[1]} pulses)`, "color: #3b82f6; font-weight: bold;");
  } else if (line === "SEND_OK") {
    console.log("%c✅ [OK] Replay Successful", "color: #6366f1;");
  } else if (line === "HUB_READY") {
    console.log("%c🔌 [LINK] Arduino Hub Ready for Cloning", "color: #00e676; font-weight: bold;");
  } else {
    console.log("Incoming Serial:", line);
  }
}

export function handleCapture(line, triggerUIRefresh) {
  const parts = line.trim().split(":");
  if (parts.length < 3) return;

  const buttonId = state.learningTargetId;
  const len = parts[1];
  const values = parts.slice(2).join(":"); // in case values somehow had colons
  
  state.learnedCodes[buttonId] = {
    type: 'raw',
    len: len,
    values: values
  };

  localStorage.setItem('learnedCodes', JSON.stringify(state.learnedCodes));
  syncCloudRemotes();
  
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
        learningStatus.textContent = `Success! "${buttonId.split('_').join(' ')}" cloned via USB.`;
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
  const signal = state.learnedCodes[buttonId];
  if (!signal) {
    alert("This button hasn't been programmed yet!");
    return;
  }

  const payload = signal.len + ":" + signal.values;

  if (state.connectionType === 'serial' && state.serialWriter) {
    flashStatus('fire');
    const encoder = new TextEncoder();
    const fullPayload = "SEND_RAW:" + payload + "\n";
    
    // Chunk transmission to prevent Arduino 64-byte RX buffer overrun
    for (let i = 0; i < fullPayload.length; i += 32) {
      const chunk = fullPayload.substring(i, i + 32);
      await state.serialWriter.write(encoder.encode(chunk));
      await new Promise(res => setTimeout(res, 5)); // 5ms breathing room
    }
  } else if (state.connectionType === 'wifi') {
    flashStatus('fire');
    if (mqttClient && mqttClient.connected) {
      mqttClient.publish(MQTT_TOPIC_TX, payload);
      console.log("%c🌐 [MQTT TX] Payload Dispatch to ESP32...", "color: #3b82f6;");
    } else {
      console.warn("MQTT Not Connected. Attempting fallback basic HTTP emit...");
      try {
        await fetch(`http://${state.espIp}/emit`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `payload=${encodeURIComponent(payload)}`
        });
      } catch(e) {
        console.error("HTTP emit failed", e);
      }
    }
  } else if (state.connectionType === 'demo') {
    flashStatus('fire');
    console.log(`[DEMO] Triggered ${buttonId}`, signal);
  }
}
