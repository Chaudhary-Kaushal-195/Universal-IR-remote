import { state } from './state.js';

export const templates = {
  tv: `
    <div class="tv-handheld-wrapper">
      <div class="tv-handheld-chassis">
        <!-- Top Row: 3 Circular Buttons (Screen 2: Power, Home, Menu) -->
        <div class="mi-top-row">
          <button class="remote-btn mi-circle-btn power-btn" id="TV_POWER" title="Power">
            <i data-lucide="power"></i>
          </button>
          <button class="remote-btn mi-circle-btn home-btn" id="TV_HOME" title="Home">
            <i data-lucide="home"></i>
          </button>
          <button class="remote-btn mi-circle-btn input-btn" id="TV_INPUT" title="Input / Menu">
            <i data-lucide="menu"></i>
          </button>
        </div>

        <!-- Center: Seamless Navigation Wheel (Screen 2) -->
        <div class="mi-dpad-wheel">
          <button class="remote-btn mi-wheel-dir up" id="TV_UP" title="Up">
            <i data-lucide="chevron-up"></i>
          </button>
          <button class="remote-btn mi-wheel-dir left" id="TV_LEFT" title="Left">
            <i data-lucide="chevron-left"></i>
          </button>
          <button class="remote-btn mi-wheel-ok" id="TV_OK" title="Select / OK">
            OK
          </button>
          <button class="remote-btn mi-wheel-dir right" id="TV_RIGHT" title="Right">
            <i data-lucide="chevron-right"></i>
          </button>
          <button class="remote-btn mi-wheel-dir down" id="TV_DOWN" title="Down">
            <i data-lucide="chevron-down"></i>
          </button>
        </div>

        <!-- Bottom Capsule Bar 1: Volume & Mute (Screen 2) -->
        <div class="mi-capsule-bar vol-bar">
          <button class="remote-btn mi-capsule-btn" id="TV_VOL_DOWN" title="Volume Down">
            <i data-lucide="volume-1"></i>
          </button>
          <button class="remote-btn mi-capsule-btn mute-btn" id="TV_MUTE" title="Mute">
            <i data-lucide="volume-x"></i>
          </button>
          <button class="remote-btn mi-capsule-btn" id="TV_VOL_UP" title="Volume Up">
            <i data-lucide="volume-2"></i>
          </button>
        </div>

        <!-- Bottom Capsule Bar 2: Wide Return / Back (Screen 2) -->
        <button class="remote-btn mi-wide-back-btn" id="TV_BACK" title="Back / Return">
          <i data-lucide="undo-2"></i>
        </button>
      </div>
    </div>
  `,
  ac: `
    <div class="ac-layout mi-layout">
      <!-- Top Ambient & Temperature Display Card (Screen 3) -->
      <div class="mi-ac-display-card">
        <div class="mi-ac-meta-row">
          <span>Outside 26°C</span>
          <span>Humidity 48%</span>
          <span>Eco Pure</span>
        </div>

        <div class="mi-ac-hero-temp">
          <div class="mi-ac-big-digits">
            <span id="ac-temp-display">26</span>
            <span class="mi-ac-deg">°C</span>
          </div>
          <div class="mi-ac-mode-badge">
            <i data-lucide="snowflake" style="width: 16px; height: 16px;"></i>
            <span>Cooling</span>
          </div>
        </div>

        <div class="mi-ac-sub-status">
          <span><i data-lucide="fan" style="width: 12px; height: 12px;"></i> Speed Auto</span>
          <span><i data-lucide="move" style="width: 12px; height: 12px;"></i> Direction Mid</span>
          <span><i data-lucide="refresh-cw" style="width: 12px; height: 12px;"></i> Swing On</span>
        </div>
      </div>

      <!-- Row 1: Power ON / OFF & Mode -->
      <div class="mi-ac-power-mode-row">
        <button class="remote-btn mi-ac-power-btn power-btn-on" id="AC_POWER_ON" title="AC Power ON">
          <i data-lucide="power"></i>
          <span>ON</span>
        </button>
        <button class="remote-btn mi-ac-power-btn power-btn-off" id="AC_POWER_OFF" title="AC Power OFF">
          <i data-lucide="power"></i>
          <span>OFF</span>
        </button>
        <button class="remote-btn mi-ac-tile-btn mode-btn" id="AC_MODE">
          <i data-lucide="wind"></i>
          <span>Mode</span>
        </button>
      </div>

      <!-- Row 2: Speed | Swing | Light ON | Light OFF -->
      <div class="mi-ac-four-col-row">
        <button class="remote-btn mi-ac-tile-btn fan-btn" id="AC_FAN">
          <i data-lucide="fan"></i>
          <span>Speed</span>
        </button>
        <button class="remote-btn mi-ac-tile-btn swing-btn" id="AC_SWING">
          <i data-lucide="refresh-cw"></i>
          <span>Swing</span>
        </button>
        <button class="remote-btn mi-ac-tile-btn light-on-btn" id="AC_LIGHT_ON" title="Display Light ON">
          <i data-lucide="sun"></i>
          <span>Light ON</span>
        </button>
        <button class="remote-btn mi-ac-tile-btn light-off-btn" id="AC_LIGHT_OFF" title="Display Light OFF">
          <i data-lucide="sun-dim"></i>
          <span>Light OFF</span>
        </button>
      </div>

      <!-- Row 3: Horizontal Temperature Slider: [ - ]  Temp  [ + ] -->
      <div class="mi-ac-temp-bar">
        <button class="remote-btn mi-temp-pill-btn minus" id="AC_TEMP_DOWN" title="Decrease Temp">
          <i data-lucide="minus"></i>
        </button>
        <button class="remote-btn mi-temp-center-btn" id="AC_TEMP_DISPLAY" title="Send Temperature">
          <span class="temp-bar-label">Temp</span>
        </button>
        <button class="remote-btn mi-temp-pill-btn plus" id="AC_TEMP_UP" title="Increase Temp">
          <i data-lucide="plus"></i>
        </button>
      </div>

      <!-- Row 4: Timer | Sleep | Eco -->
      <div class="mi-ac-three-col-row">
        <button class="remote-btn mi-ac-tile-btn timer-btn" id="AC_TIMER">
          <i data-lucide="clock"></i>
          <span>Timer</span>
        </button>
        <button class="remote-btn mi-ac-tile-btn sleep-btn" id="AC_SLEEP">
          <i data-lucide="moon"></i>
          <span>Sleep</span>
        </button>
        <button class="remote-btn mi-ac-tile-btn eco-btn" id="AC_ECO" title="Eco Mode">
          <i data-lucide="sparkles"></i>
          <span>Eco</span>
        </button>
      </div>
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
  
  if (state.serialWriter && state.isDeviceOnline) {
    indicator.innerHTML = '<i data-lucide="zap" style="width:14px; height:14px;"></i> USB + Wi-Fi';
    indicator.classList.add('status-wifi');
  } else if (state.serialWriter) {
    indicator.innerHTML = '<i data-lucide="usb" style="width:14px; height:14px;"></i> USB Online';
    indicator.classList.add('status-serial');
  } else if (state.isDeviceOnline) {
    indicator.innerHTML = '<i data-lucide="wifi" style="width:14px; height:14px;"></i> Hub Online';
    indicator.classList.add('status-wifi');
  } else if (state.connectionType === 'wifi') {
    indicator.innerHTML = '<i data-lucide="wifi" style="width:14px; height:14px;"></i> Wi-Fi Ready';
    indicator.classList.add('status-demo');
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

export function renderSignalDebugger() {
  const captureBadge = document.getElementById('debug-capture-badge');
  const captureDetails = document.getElementById('debug-capture-details');
  const transmitBadge = document.getElementById('debug-transmit-badge');
  const transmitDetails = document.getElementById('debug-transmit-details');
  const matchBadge = document.getElementById('debug-match-badge');
  const echoDetails = document.getElementById('debug-echo-details');

  if (state.lastCapturedSignal && captureBadge && captureDetails) {
    const s = state.lastCapturedSignal;
    captureBadge.textContent = `${s.len} Pulses (${s.buttonId || 'Cloned'})`;
    captureBadge.style.background = '#bfdbfe';
    captureDetails.innerHTML = `
      <strong>Button:</strong> ${s.buttonId || 'Unknown'} (${s.timestamp})<br>
      <strong>Total Pulses:</strong> ${s.len} (${s.count} values)<br>
      <strong>Header:</strong> ${s.first8[0]}μs (Mark), ${s.first8[1]}μs (Space)<br>
      <strong>First 6:</strong> [${s.first8.slice(0, 6).join(', ')}]<br>
      <strong>Last 4:</strong> [${s.last6.slice(-4).join(', ')}]
    `;
  }

  if (state.lastTransmittedSignal && transmitBadge && transmitDetails) {
    const t = state.lastTransmittedSignal;
    transmitBadge.textContent = `${t.len} Pulses (${t.buttonId})`;
    transmitBadge.style.background = '#fecaca';
    transmitDetails.innerHTML = `
      <strong>Target:</strong> ${t.buttonId} (${t.timestamp})<br>
      <strong>Pulses Sent:</strong> ${t.len} (${t.count} values)<br>
      <strong>Header:</strong> ${t.first8[0]}μs (Mark), ${t.first8[1]}μs (Space)<br>
      <strong>First 6:</strong> [${t.first8.slice(0, 6).join(', ')}]<br>
      <strong>Last 4:</strong> [${t.last6.slice(-4).join(', ')}]
    `;
  }

  if (matchBadge && echoDetails && state.lastTelemetry) {
    const tel = state.lastTelemetry;
    const isMatch = tel.received === tel.expected;
    matchBadge.textContent = isMatch ? `✅ 100% MATCH (${tel.received}/${tel.expected})` : `⚠️ MISMATCH (${tel.received}/${tel.expected})`;
    matchBadge.style.background = isMatch ? '#bbf7d0' : '#fecaca';
    matchBadge.style.color = isMatch ? '#14532d' : '#991b1b';
    echoDetails.innerHTML = `
      <strong>Expected by Uno:</strong> ${tel.expected} pulses<br>
      <strong>Actually Received:</strong> ${tel.received} pulses<br>
      <strong>First 4 echoed:</strong> [${tel.first4}]<br>
      <strong>Last 4 echoed:</strong> [${tel.last4}]<br>
      <strong>Status:</strong> ${isMatch ? '<span style="color:#15803d; font-weight:bold;">All pulses received in Uno buffer!</span>' : '<span style="color:#b91c1c; font-weight:bold;">Pulses were dropped during Serial download!</span>'}
    `;
  }

  if (window.lucide) window.lucide.createIcons();
}
