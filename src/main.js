import './style.css';
import { registerSW } from 'virtual:pwa-register';
import { supabase } from './supabaseClient.js';
import { state } from './state.js';
import { initAuth, fetchCloudRemotes, syncCloudRemotes, loadGlobalDatabase } from './api.js';
import { setupMQTT, connectUSB, scanHardware, fireSignal, handleCapture } from './hardware.js';
import { renderRemote, updateStatusIndicator, toggleAC, updateAuthUI } from './ui.js';

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

const hardwareModal = document.getElementById('hardware-modal');
const closeHardwareModal = document.getElementById('close-hardware-modal');
const hwRefreshBtn = document.getElementById('hw-refresh-btn');

function init() {
  updateStatusIndicator();
  renderRemote();
  setupEventListeners();
  
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

  // Main UI Modal Interactions
  configBtn.addEventListener('click', () => configModal.classList.add('active'));
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

  // Internal Hardware Assignments
  usbConnectBtn.addEventListener('click', connectUSB);
  wifiConnectBtn.addEventListener('click', () => {
    wifiConfig.style.display = wifiConfig.style.display === 'none' ? 'block' : 'none';
  });
  
  saveWifiBtn.addEventListener('click', () => {
    if (ipInput.value) {
      state.espIp = ipInput.value;
      localStorage.setItem('espIp', state.espIp);
      state.connectionType = 'wifi';
      localStorage.setItem('connectionType', 'wifi');
      updateStatusIndicator();
      setupMQTT();
      wifiConfig.style.display = 'none';
      configModal.classList.remove('active');
    }
  });

  // Supabase Authentication Handling
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
    else alert("Success! Check your email for confirmation!");
  });
  
  logoutBtn.addEventListener('click', async () => {
    await supabase.auth.signOut();
  });

  // Main UI Grid Traversal (Dispatch Loop)
  remoteContent.addEventListener('click', async (e) => {
    const btn = e.target.closest('.remote-btn');
    if (!btn) return;
    
    // Custom AC Graphic Handler
    if (btn.id === 'AC_TEMP_UP' || btn.id === 'AC_TEMP_DOWN') {
      const display = document.getElementById('ac-temp-display');
      toggleAC(display, btn.id === 'AC_TEMP_UP' ? 1 : -1);
      return; 
    }

    if (state.isLearning) {
      state.learningTargetId = btn.id;
      learningStatus.textContent = `Point original remote at Hub and press "${btn.textContent.trim()}"...`;
      document.querySelectorAll('.remote-btn').forEach(b => b.classList.remove('learning-target'));
      btn.classList.add('learning-target');
    } else {
      fireSignal(btn.id);
    }
  });

  // Initiating Reverse-Engineering Module
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
        if (res.status === 408) throw new Error("Timeout - No signal detected within 10 seconds.");
        if (!res.ok) throw new Error("Network error during learning");
        
        const data = await res.json();
        if (data.len && data.values && state.isLearning) {
            handleCapture(`RAW:${data.len}:${data.values}`, (buttonId) => {
                state.isLearning = false;
                state.learningTargetId = null;
                learnBtn.classList.remove('learning-mode');
                learningStatus.textContent = `Success! "${buttonId.split('_').join(' ')}" cloned via WiFi.`;
                renderRemote();
                setTimeout(() => {
                  learningStatus.textContent = "";
                  configModal.classList.remove('active');
                }, 2000);
            });
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
