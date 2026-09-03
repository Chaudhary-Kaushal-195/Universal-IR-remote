import { defaultACRemoteCodes } from './defaultCodes.js';

const storedCodes = JSON.parse(localStorage.getItem('learnedCodes') || '{}');
// Merge default AC codes with any locally learned codes
const initialLearnedCodes = { ...defaultACRemoteCodes, ...storedCodes };

export const state = {
  activeLayout: 'tv',
  connectionType: localStorage.getItem('connectionType') || 'wifi',
  espIp: localStorage.getItem('espIp') || '',
  isLearning: false,
  learningTargetId: null,
  serialPort: null,
  serialWriter: null,
  user: null,
  learnedCodes: initialLearnedCodes,
  globalDeviceDatabase: {},
  acTemp: 24,
  isDeviceOnline: false,
  hubWifiStatus: 'off',
  lastCapturedSignal: null,
  lastTransmittedSignal: null,
  lastTelemetry: null
};

// Global Config
export const MQTT_BROKER = 'wss://broker.hivemq.com:8884/mqtt';
export const MQTT_TOPIC_TX = 'universalo-hub/kaushal-ir-hub-97/rx';
export const MQTT_TOPIC_RX = 'universalo-hub/kaushal-ir-hub-97/tx';
