export const state = {
  activeLayout: 'tv',
  connectionType: localStorage.getItem('connectionType') || 'demo',
  espIp: localStorage.getItem('espIp') || '',
  isLearning: false,
  learningTargetId: null,
  serialPort: null,
  serialWriter: null,
  user: null,
  learnedCodes: JSON.parse(localStorage.getItem('learnedCodes') || '{}'),
  globalDeviceDatabase: {},
  acTemp: 24,
  isDeviceOnline: false
};

// Global Config
export const MQTT_BROKER = 'wss://broker.hivemq.com:8884/mqtt';
export const MQTT_TOPIC_TX = 'universalo-hub/kaushal-ir-hub-97/rx';
export const MQTT_TOPIC_RX = 'universalo-hub/kaushal-ir-hub-97/tx';
