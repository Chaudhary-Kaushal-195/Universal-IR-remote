import './style.css'
import mqtt from 'mqtt'
import { deviceDatabase as localDatabase } from './database.js'
import { supabase } from './supabaseClient.js'
import { registerSW } from 'virtual:pwa-register'

registerSW({ immediate: true });

// --- MQTT GLOBALS ---
const MQTT_BROKER = 'wss://broker.hivemq.com:8884/mqtt';
const MQTT_TOPIC_TX = 'universalo-hub/YOUR_SECRET_ID/rx'; // WE publish to ESP32 RX
const MQTT_TOPIC_RX = 'universalo-hub/YOUR_SECRET_ID/tx'; // WE listen to ESP32 TX
let mqttClient = null;

// --- HARD-CODED DATABASE ---
const hardCodedLibrary = {
  // Example: "TV_POWER": { type: "raw", len: 68, data: "9000,4500,560,560..." }
};

// State Management
let state = {
  activeLayout: 'tv',
  connectionType: localStorage.getItem('connectionType') || 'demo',
  espIp: localStorage.getItem('espIp') || '',
  isLearning: false,
  learningTargetId: null,
  serialPort: null,
  serialWriter: null,
  user: null,
  learnedCodes: {
    ...hardCodedLibrary,
    ...JSON.parse(localStorage.getItem('learnedCodes') || '{}')
  }
};

let globalDeviceDatabase = localDatabase; // Fallback to local DB initially

// DOM Elements
const remoteContent = document.getElementById('remote-content');
const tabs = document.querySelectorAll('.tab');
const configBtn = document.getElementById('config-btn');
const configModal = document.getElementById('config-modal');
const usbConnectBtn = document.getElementById('usb-connect');
const wifiConnectBtn = document.getElementById('wifi-connect');
const wifiConfig = document.getElementById('wifi-config');
const saveWifiBtn = document.getElementById('save-config');
const ipInput = document.getElementById('esp-ip');
const learnBtn = document.getElementById('learn-btn');
const learningStatus = document.getElementById('learning-status');
const statusIndicator = document.getElementById('connection-status');
const dbSelect = document.getElementById('db-select');
const dbImportBtn = document.getElementById('db-import-btn');

// Auth DOM
const authForm = document.getElementById('auth-form');
const authLoggedIn = document.getElementById('auth-logged-in');
const userEmailSpan = document.getElementById('user-email');
const authStatus = document.getElementById('auth-status');
const emailInput = document.getElementById('auth-email');
const passwordInput = document.getElementById('auth-password');
const loginBtn = document.getElementById('login-btn');
const signupBtn = document.getElementById('signup-btn');
const logoutBtn = document.getElementById('logout-btn');

// Hardware Scanner DOM
const hardwareModal = document.getElementById('hardware-modal');
const closeHardwareModal = document.getElementById('close-hardware-modal');
const hwRefreshBtn = document.getElementById('hw-refresh-btn');
const hwCardUsb = document.getElementById('hw-card-usb');
const hwStatusUsb = document.getElementById('hw-status-usb');
const hwCardWifi = document.getElementById('hw-card-wifi');
const hwStatusWifi = document.getElementById('hw-status-wifi');
const hwNoDeviceWarning = document.getElementById('hw-no-device-warning');

const templates = {
  tv: `
    <div class="remote-grid">
      <button class="remote-btn power" id="TV_POWER"><i data-lucide="power"></i><span class="btn-label">Power</span></button>
      <button class="remote-btn" id="TV_MUTE"><i data-lucide="volume-x"></i><span class="btn-label">Mute</span></button>
      <button class="remote-btn" id="TV_INPUT"><i data-lucide="log-in"></i><span class="btn-label">Input</span></button>
      
      <div style="grid-column: span 3; padding: 20px 0;">
        <div class="dpad">
          <div></div><button class="remote-btn" id="TV_UP"><i data-lucide="chevron-up"></i></button><div></div>
          <button class="remote-btn" id="TV_LEFT"><i data-lucide="chevron-left"></i></button>
          <button class="remote-btn" style="background: var(--accent-color); color: white;" id="TV_OK">OK</button>
          <button class="remote-btn" id="TV_RIGHT"><i data-lucide="chevron-right"></i></button>
          <div></div><button class="remote-btn" id="TV_DOWN"><i data-lucide="chevron-down"></i></button><div></div>
        </div>
      </div>

      <button class="remote-btn" id="TV_VOL_UP"><i data-lucide="plus"></i><span class="btn-label">Vol +</span></button>
      <button class="remote-btn" id="TV_BACK"><i data-lucide="rotate-ccw"></i><span class="btn-label">Back</span></button>
      <button class="remote-btn" id="TV_CH_UP"><i data-lucide="chevron-up"></i><span class="btn-label">CH +</span></button>
      
      <button class="remote-btn" id="TV_VOL_DOWN"><i data-lucide="minus"></i><span class="btn-label">Vol -</span></button>
      <button class="remote-btn" id="TV_HOME"><i data-lucide="home"></i><span class="btn-label">Home</span></button>
      <button class="remote-btn" id="TV_CH_DOWN"><i data-lucide="chevron-down"></i><span class="btn-label">CH -</span></button>
    </div>
  `,
  ac: `
    <div class="remote-grid">
      <button class="remote-btn power" id="AC_POWER" style="grid-column: span 3; aspect-ratio: auto; padding: 20px;">
        <i data-lucide="power"></i><span class="btn-label">Power</span>
      </button>
      
      <div style="grid-column: span 3; display: flex; align-items: center; justify-content: space-between; padding: 30px; background: rgba(255,255,255,0.02); border-radius: 20px; border: 1px solid var(--glass-border);">
        <button class="remote-btn" id="AC_TEMP_DOWN"><i data-lucide="minus"></i></button>
        <div style="text-align: center;">
          <h1 id="ac-temp-display" style="font-size: 3.5rem; font-weight: 300; letter-spacing: -2px;">AC</h1>
          <span style="color: var(--text-secondary); font-size: 0.8rem; text-transform: uppercase; letter-spacing: 1px;">Climate Control</span>
        </div>
        <button class="remote-btn" id="AC_TEMP_UP"><i data-lucide="plus"></i></button>
      </div>

      <button class="remote-btn" id="AC_MODE"><i data-lucide="wind"></i><span class="btn-label">Mode</span></button>
      <button class="remote-btn" id="AC_FAN"><i data-lucide="fan"></i><span class="btn-label">Fan</span></button>
      <button class="remote-btn" id="AC_SWING"><i data-lucide="refresh-cw"></i><span class="btn-label">Swing</span></button>
      
      <button class="remote-btn" id="AC_TIMER"><i data-lucide="clock"></i><span class="btn-label">Timer</span></button>
      <button class="remote-btn" id="AC_SLEEP"><i data-lucide="moon"></i><span class="btn-label">Sleep</span></button>
      <button class="remote-btn" id="AC_LIGHT"><i data-lucide="sun"></i><span class="btn-label">Light</span></button>
    </div>
  `
};

function init() {
  updateStatusIndicator();
  renderRemote();
  setupEventListeners();
  
  // Initialize Connectivity
  setupMQTT();
  initAuth();
  loadGlobalDatabase();

  console.log("%c🚀 Universal Hub Started", "color: #6366f1; font-weight: bold; font-size: 1.2rem;");
}

async function initAuth() {
  const { data: { session } } = await supabase.auth.getSession();
  updateAuthUI(session?.user);

  supabase.auth.onAuthStateChange((_event, session) => {
    updateAuthUI(session?.user);
  });
}

function updateAuthUI(user) {
  if (user) {
    state.user = user;
    authForm.style.display = 'none';
    authLoggedIn.style.display = 'flex';
    userEmailSpan.textContent = user.email;
    authStatus.textContent = "✅ Cloud Sync Active";
    fetchCloudRemotes(); // Auto load saved remotes
  } else {
    state.user = null;
    authForm.style.display = 'flex';
    authLoggedIn.style.display = 'none';
    authStatus.textContent = "🔒 Not Logged In";
  }
}

async function fetchCloudRemotes() {
  if (!state.user) return;
  const { data, error } = await supabase.from('user_remotes').select('cloned_codes_json').eq('user_id', state.user.id).single();
  if (data && data.cloned_codes_json) {
    state.learnedCodes = { ...state.learnedCodes, ...data.cloned_codes_json };
    localStorage.setItem('learnedCodes', JSON.stringify(state.learnedCodes));
    renderRemote();
    console.log("%c☁️ [Sync] Cloud user remotes loaded!", "color: #38bdf8; font-weight: bold;");
  }
}

async function syncCloudRemotes() {
  if (!state.user) return;
  try {
    await supabase.from('user_remotes').upsert({
      user_id: state.user.id,
      cloned_codes_json: state.learnedCodes
    });
    console.log("%c☁️ [Sync] Successfully backed up to Cloud!", "color: #38bdf8;");
  } catch(e) { }
}

async function loadGlobalDatabase() {
  try {
    const { data, error } = await supabase.from('global_devices').select('*');
    if (data && !error && data.length > 0) {
      dbSelect.innerHTML = '<option value="">-- Select Cloud Model --</option>';
      const dynamicDb = {};
      data.forEach(row => {
        dynamicDb[row.brand] = row.config_json;
        dbSelect.innerHTML += `<option value="${row.brand}">${row.brand}</option>`;
      });
      globalDeviceDatabase = dynamicDb;
      console.log("%c🔥 [Cloud] Massive Global Database synced!", "color: #f97316; font-weight: bold;");
    }
  } catch (err) { }
}

function setupMQTT() {
  console.log("Connecting to Global MQTT Broker...");
  mqttClient = mqtt.connect(MQTT_BROKER);
  
  mqttClient.on('connect', () => {
    console.log("%c🌐 [MQTT] Connected to Global Cloud!", "color: #0ea5e9; font-weight: bold;");
    mqttClient.subscribe(MQTT_TOPIC_RX);
  });

  mqttClient.on('message', (topic, message) => {
    // If we're learning, we can parse incoming signals from ESP32 globally!
    // But since the current Learning mode polls the local HTTP, we'll keep that working too.
    console.log(`MQTT Received [${topic}]: ${message.toString()}`);
  });
}

async function scanHardware() {
  console.log("Running Hardware Diagnostics...");
  let hasHardware = false;

  // 1. Mobile Native is always hardcoded as FAILED for security (done in HTML)

  // 2. USB Serial
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

  // 3. Wi-Fi / MQTT
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

  // Warning Resolution
  if (hasHardware) {
    hwNoDeviceWarning.style.display = 'none';
  } else {
    hwNoDeviceWarning.style.display = 'flex';
  }
}

function flashStatus(type) {
  const color = type === 'fire' ? '#ef4444' : '#22c55e';
  const originalShadow = statusIndicator.style.boxShadow;
  statusIndicator.style.shadow = `0 0 20px 5px ${color}`;
  statusIndicator.style.boxShadow = `0 0 20px 5px ${color}`;
  setTimeout(() => {
    statusIndicator.style.boxShadow = originalShadow;
  }, 200);
}

function updateStatusIndicator() {
  statusIndicator.className = 'status-indicator';
  if (state.connectionType === 'demo') {
    statusIndicator.classList.add('status-demo');
    statusIndicator.textContent = 'Demo Mode';
  } else if (state.connectionType === 'serial') {
    statusIndicator.classList.add(state.serialPort ? 'status-online' : 'status-offline');
    statusIndicator.textContent = state.serialPort ? 'USB Active' : 'USB Offline';
  } else {
    statusIndicator.classList.add(state.espIp ? 'status-online' : 'status-offline');
    statusIndicator.textContent = state.espIp ? 'WiFi Active' : 'WiFi Offline';
  }
}

function renderRemote() {
  remoteContent.innerHTML = templates[state.activeLayout];
  const buttons = remoteContent.querySelectorAll('.remote-btn');
  buttons.forEach(btn => {
    const id = btn.id;
    if (state.learnedCodes[id]) {
      btn.classList.add('mapped');
      btn.classList.remove('unmapped');
    } else {
      btn.classList.add('unmapped');
      btn.classList.remove('mapped');
    }
    btn.addEventListener('click', (e) => handleButtonAction(id, e));
  });
  lucide.createIcons();
}

async function handleButtonAction(id, event) {
  const btn = event.currentTarget;
  if (state.isLearning) {
    state.learningTargetId = id;
    learningStatus.textContent = `Cloning "${id.split('_').join(' ')}"... Press physical remote button now.`;
    return;
  }

  if (state.learnedCodes[id]) {
    btn.classList.add('feedback-glow');
    setTimeout(() => btn.classList.remove('feedback-glow'), 300);
    if (state.connectionType === 'demo') {
      console.log(`[DEMO] Replaying Raw Signal: ${id}`);
      return;
    }
    console.log(`%c🚀 [WEB] Sending Clone Signal for "${id.split('_').join(' ')}"...`, "color: #6366f1; font-weight: bold;");
    sendCommand(state.learnedCodes[id]);
  } else {
    const confirmLearn = confirm(`Button "${id.split('_').join(' ')}" is not cloned. Would you like to clone it now?`);
    if (confirmLearn) {
      configModal.classList.add('active');
      state.learningTargetId = id;
    }
  }
}

async function sendCommand(data) {
  if (state.connectionType === 'serial' && state.serialWriter) {
    try {
      // Use SEND_RAW for 100% compatibility
      const msg = `SEND_RAW:${data.len}:${data.values}\n`;
      await state.serialWriter.write(new TextEncoder().encode(msg));
    } catch (err) { console.error("Serial transmit failed", err); }
  } else if (state.connectionType === 'wifi') {
    // Global MQTT Control (Works from anywhere)
    try {
      let payload;
      if (data.type === 'standard') {
        payload = JSON.stringify({ type: 'standard', protocol: data.protocol, code: data.code, bits: data.bits });
      } else {
        payload = JSON.stringify({ type: 'raw', len: data.len, values: data.values });
      }

      if (mqttClient && mqttClient.connected) {
        mqttClient.publish(MQTT_TOPIC_TX, payload);
        console.log("%c✅ [OK] Global MQTT Replay Successful", "color: #6366f1;");
        flashStatus('fire');
      } else {
        throw new Error("MQTT Broker Disconnected - Check network.");
      }
    } catch (err) {
      console.error("MQTT transmit failed", err);
      alert("Failed to send command over cloud: " + err.message);
    }
  }
}

async function connectSerial() {
  try {
    state.serialPort = await navigator.serial.requestPort();
    await state.serialPort.open({ baudRate: 115200 });
    state.serialWriter = state.serialPort.writable.getWriter();
    readSerial();
    state.connectionType = 'serial';
    localStorage.setItem('connectionType', 'serial');
    updateStatusIndicator();
    configModal.classList.remove('active');
  } catch (err) {
    console.error("Serial Connection Error:", err);
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
          handleCapture(trimmedLine);
        }
      }
    }
  } catch (err) { console.error("Reader error", err); } finally { reader.releaseLock(); }
}

function handleStatusMessages(line) {
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

function handleCapture(line) {
  // Format: RAW:LEN:VALUES
  const parts = line.trim().split(":");
  if (parts.length < 3) return;

  const buttonId = state.learningTargetId;
  state.learnedCodes[buttonId] = {
    type: 'raw',
    len: parts[1],
    values: parts[2]
  };

  localStorage.setItem('learnedCodes', JSON.stringify(state.learnedCodes));
  syncCloudRemotes(); // Background sync
  
  state.isLearning = false;
  state.learningTargetId = null;
  learnBtn.classList.remove('learning-mode');
  learningStatus.textContent = `Success! "${buttonId.split('_').join(' ')}" cloned.`;
  renderRemote();
  setTimeout(() => {
    learningStatus.textContent = "";
    configModal.classList.remove('active');
    console.log("Updated Signal Library:");
    console.log(JSON.stringify(state.learnedCodes, null, 2));
  }, 2000);
}

function setupEventListeners() {
  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      tabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      state.activeLayout = tab.dataset.device;
      renderRemote();
    });
  });

  configBtn.addEventListener('click', () => configModal.classList.add('active'));
  usbConnectBtn.addEventListener('click', connectSerial);
  saveWifiBtn.addEventListener('click', () => {
    state.espIp = ipInput.value;
    localStorage.setItem('espIp', state.espIp);
    localStorage.setItem('connectionType', 'wifi');
    updateStatusIndicator();
    configModal.classList.remove('active');
  });
  
  dbImportBtn.addEventListener('click', () => {
    const selectedModel = dbSelect.value;
    if (!selectedModel) return alert("Please select a device model to import.");
    
    if (globalDeviceDatabase[selectedModel]) {
      const codes = globalDeviceDatabase[selectedModel];
      
      // Merge codes into state
      state.learnedCodes = { ...state.learnedCodes, ...codes };
      localStorage.setItem('learnedCodes', JSON.stringify(state.learnedCodes));
      syncCloudRemotes(); // Background sync

      renderRemote();
      console.log(`%c📚 [DB] Successfully imported ${selectedModel} profiling codes.`, 'color: #8b5cf6; font-weight: bold;');
      
      const confirmMsg = document.createElement('span');
      confirmMsg.textContent = " Import Successful!";
      confirmMsg.style.color = "var(--success)";
      dbImportBtn.appendChild(confirmMsg);
      setTimeout(() => confirmMsg.remove(), 2000);
    }
  });

  // Auth Events
  loginBtn.addEventListener('click', async () => {
    const { error } = await supabase.auth.signInWithPassword({
      email: emailInput.value, password: passwordInput.value
    });
    if (error) alert(error.message);
  });
  
  signupBtn.addEventListener('click', async () => {
    const { error } = await supabase.auth.signUp({
      email: emailInput.value, password: passwordInput.value
    });
    if (error) alert(error.message);
    if (error) alert(error.message);
    else alert("Success! Check your email for confirmation!");
  });
  
  logoutBtn.addEventListener('click', async () => {
    await supabase.auth.signOut();
  });

  // Hardware Scanner Events
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
  learnBtn.addEventListener('click', async () => {
    if (state.connectionType === 'demo') return alert("Cloning requires a hardware connection.");
    if (!state.learningTargetId) return alert("Please click a button on the remote first to select what to clone.");
    state.isLearning = true;
    learnBtn.classList.add('learning-mode');
    learningStatus.textContent = `Waiting for signal to clone "${state.learningTargetId.split('_').join(' ')}"...`;
    
    if (state.connectionType === 'wifi') {
      try {
        console.log("%c📡 [SENSE] Initiating WiFi Learn Mode...", "color: #22c55e; font-weight: bold;");
        const res = await fetch(`http://${state.espIp}/receive`);
        if (res.status === 408) {
          throw new Error("Timeout - No signal detected within 10 seconds.");
        }
        if (!res.ok) throw new Error("Network error during learning");
        
        const data = await res.json();
        
        if (data.len && data.values && state.isLearning) {
            flashStatus('sense');
            console.log(`%c📸 [CLONE] Pattern Captured (${data.len} pulses via WiFi)`, "color: #3b82f6; font-weight: bold;");
            
            const buttonId = state.learningTargetId;
            state.learnedCodes[buttonId] = {
              type: 'raw',
              len: data.len,
              values: data.values
            };
            
            localStorage.setItem('learnedCodes', JSON.stringify(state.learnedCodes));
            syncCloudRemotes(); // Background sync
            
            state.isLearning = false;
            state.learningTargetId = null;
            learnBtn.classList.remove('learning-mode');
            learningStatus.textContent = `Success! "${buttonId.split('_').join(' ')}" cloned via WiFi.`;
            renderRemote();
            
            setTimeout(() => {
              learningStatus.textContent = "";
              configModal.classList.remove('active');
            }, 2000);
        }
      } catch (err) {
        console.error("WiFi Learn Error:", err);
        state.isLearning = false;
        learnBtn.classList.remove('learning-mode');
        learningStatus.textContent = `Error: ${err.message}`;
        setTimeout(() => learningStatus.textContent = "", 3000);
      }
    }
  });
}

init();
