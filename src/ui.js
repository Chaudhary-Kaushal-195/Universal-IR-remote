import { state } from './state.js';

export const templates = {
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
      <div style="grid-column: span 3; display: flex; gap: 10px;">
        <button class="remote-btn power" id="AC_POWER_ON" style="flex: 1; aspect-ratio: auto; padding: 15px; background: rgba(34, 197, 94, 0.1); border: 1px solid var(--success); color: var(--success);">
          <i data-lucide="power"></i><span class="btn-label" style="font-weight: bold;">ON</span>
        </button>
        <button class="remote-btn power" id="AC_POWER_OFF" style="flex: 1; aspect-ratio: auto; padding: 15px; background: rgba(239, 68, 68, 0.1); border: 1px solid var(--danger); color: var(--danger);">
          <i data-lucide="power"></i><span class="btn-label" style="font-weight: bold;">OFF</span>
        </button>
      </div>
      
      <div style="grid-column: span 3; display: flex; align-items: center; justify-content: space-between; padding: 30px; background: rgba(255,255,255,0.02); border-radius: 20px; border: 1px solid var(--glass-border);">
        <button class="remote-btn" id="AC_TEMP_DOWN"><i data-lucide="minus"></i></button>
        <button class="remote-btn" id="AC_TEMP_DISPLAY" style="text-align: center; border: none; background: transparent; padding: 10px; display: flex; flex-direction: column; align-items: center; justify-content: center;">
          <h1 id="ac-temp-display" style="font-size: 3.5rem; font-weight: 300; letter-spacing: -2px; margin: 0;">AC</h1>
          <span style="color: var(--text-secondary); font-size: 0.8rem; text-transform: uppercase; letter-spacing: 1px;">Climate Control</span>
        </button>
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

export function renderRemote() {
  const remoteContent = document.getElementById('remote-content');
  const tabs = document.querySelectorAll('.tab');
  if (!remoteContent) return;

  remoteContent.innerHTML = templates[state.activeLayout];
  
  tabs.forEach(t => {
    t.classList.toggle('active', t.dataset.device === state.activeLayout);
  });
  
  if (state.activeLayout === 'ac') {
    const acDisplay = document.getElementById('ac-temp-display');
    if (acDisplay) {
        acDisplay.textContent = state.acTemp + "°";
    }
  }

  document.querySelectorAll('.remote-btn').forEach(btn => {
    btn.classList.remove('programmed'); // Clear previous states on re-render
    if (btn.id === 'AC_TEMP_DISPLAY') {
      if (state.learnedCodes['AC_TEMP_' + state.acTemp]) {
        btn.classList.add('programmed');
      }
    } else if (state.learnedCodes[btn.id]) {
      btn.classList.add('programmed');
    }
  });

  if (window.lucide) window.lucide.createIcons();
}

export function flashStatus(type) {
  const indicator = document.getElementById('connection-status');
  if (!indicator) return;

  const color = type === 'fire' ? '#ef4444' : '#22c55e';
  const originalShadow = indicator.style.boxShadow;
  indicator.style.boxShadow = `0 0 20px ${color}, inset 0 0 10px ${color}`;
  
  setTimeout(() => {
    indicator.style.boxShadow = originalShadow;
  }, 200);
}

export function updateStatusIndicator() {
  const indicator = document.getElementById('connection-status');
  if (!indicator) return;

  indicator.className = 'status-indicator';
  if (state.connectionType === 'serial') {
    indicator.innerHTML = '<i data-lucide="usb" style="width:14px; height:14px;"></i> USB Connected';
    indicator.classList.add('status-serial');
  } else if (state.connectionType === 'wifi') {
    if (state.isDeviceOnline) {
        indicator.innerHTML = '<i data-lucide="wifi" style="width:14px; height:14px;"></i> Hub Online';
        indicator.classList.add('status-wifi');
    } else {
        indicator.innerHTML = '<i data-lucide="cloud-off" style="width:14px; height:14px;"></i> Hub Offline';
        indicator.classList.add('status-demo'); // Grey color
    }
  } else {
    indicator.innerHTML = '<i data-lucide="radio" style="width:14px; height:14px;"></i> Demo Mode';
    indicator.classList.add('status-demo');
  }
  
  if (window.lucide) window.lucide.createIcons();
}

export function toggleAC(tempDisplay, delta) {
  state.acTemp += delta;
  if (state.acTemp < 16) state.acTemp = 16;
  if (state.acTemp > 30) state.acTemp = 30;
  tempDisplay.textContent = state.acTemp + "°";
}

export function updateAuthUI(user, cloudEnabled = true) {
  const authForm = document.getElementById('auth-form');
  const authLoggedIn = document.getElementById('auth-logged-in');
  const userEmailSpan = document.getElementById('user-email');
  const authStatus = document.getElementById('auth-status');

  if (!authForm || !authLoggedIn) return;

  if (!cloudEnabled) {
    authStatus.textContent = "Cloud sync disabled; local backup/export is available";
    authForm.style.display = 'none';
    authLoggedIn.style.display = 'none';
    return;
  }

  if (user) {
    authStatus.textContent = "🔒 Synced securely via Supabase";
    authForm.style.display = 'none';
    authLoggedIn.style.display = 'flex';
    userEmailSpan.textContent = user.email;
  } else {
    authStatus.textContent = "🔒 Login to backup your clones to the cloud";
    authForm.style.display = 'flex';
    authLoggedIn.style.display = 'none';
  }
}
