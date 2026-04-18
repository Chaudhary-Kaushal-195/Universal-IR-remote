import { supabase } from './supabaseClient.js';
import { state } from './state.js';
import { renderRemote } from './ui.js';

export async function initAuth(updateAuthUICallback) {
  const { data: { session } } = await supabase.auth.getSession();
  state.user = session?.user || null;
  updateAuthUICallback(state.user);

  supabase.auth.onAuthStateChange((_event, session) => {
    state.user = session?.user || null;
    updateAuthUICallback(state.user);
  });
}

export async function loadGlobalDatabase() {
  const { data, error } = await supabase.from('global_devices').select('*');
  if (error) {
    console.warn("Could not load global database", error);
    return;
  }
  
  if (data && data.length > 0) {
    state.globalDeviceDatabase = {};
    data.forEach(device => {
      state.globalDeviceDatabase[device.brand] = device.config_json;
    });
    console.log("Loaded global device profiles from Supabase!");
    
    const dbSelect = document.getElementById('db-select');
    if (dbSelect) {
      dbSelect.innerHTML = Object.keys(state.globalDeviceDatabase)
        .map(brand => `<option value="${brand}">${brand}</option>`)
        .join('');
    }
  }
}

export async function syncCloudRemotes() {
  if (!state.user) return;
  const { error } = await supabase
    .from('user_remotes')
    .upsert({ 
      user_id: state.user.id, 
      cloned_codes_json: state.learnedCodes 
    });
  if (error) {
    console.error("Error backing up to cloud:", error);
  } else {
    console.log("Backed up to Cloud Database!");
  }
}

export async function fetchCloudRemotes() {
  if (!state.user) return;
  const { data, error } = await supabase
    .from('user_remotes')
    .select('cloned_codes_json')
    .eq('user_id', state.user.id)
    .single();

  if (data && data.cloned_codes_json) {
    state.learnedCodes = { ...state.learnedCodes, ...data.cloned_codes_json };
    localStorage.setItem('learnedCodes', JSON.stringify(state.learnedCodes));
    console.log("Synced Cloud Settings Down to Local Device!");
  }
}
