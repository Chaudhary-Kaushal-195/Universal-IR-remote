/*
  Universal IR Emitter Hub (Ultra-Lightweight MQTT Version)
  Function: Connects to WiFi & MQTT, listens for IR commands, and transmits them.
  No web servers, no complex background tasks. Just pure, stable IR blasting.
*/

#include <Arduino.h>
#include <WiFi.h>
#include <IRremoteESP8266.h>
#include <IRsend.h>
#include <IRrecv.h>
#include <IRutils.h>
#include <ArduinoJson.h>
#include <PubSubClient.h>
#include <Preferences.h>

Preferences preferences;
bool wifiEnabled = false; // Default: WiFi is OFF in USB mode unless software requests it
bool isLearningMode = false; // Only listen for IR when user is cloning a remote
unsigned long wifiConnectStartTime = 0;

// --- CONFIGURATION ---
const char* ssid = "Galaxy M35 5G A733";
const char* password = "TestKaushal";
const char* mqtt_server = "broker.hivemq.com";
const char* hub_password = "TestKaushalSecure2026"; // Secret Hub Security Key
const char* mqtt_topic_rx = "universalo-hub/kaushal-ir-hub-97/rx"; // ESP32 Listens here
const char* mqtt_topic_tx = "universalo-hub/kaushal-ir-hub-97/tx"; // ESP32 Publishes status here

const uint16_t kIrLedPin = 4;   // IR Emitter Pin
const uint16_t kIrRecvPin = 27; // IR Receiver Pin
IRsend irsend(kIrLedPin);
IRrecv irrecv(kIrRecvPin);
decode_results results;

WiFiClient espClient;
PubSubClient client(espClient);

// --- DEFERRED TRANSMISSION GLOBALS ---
// We must defer transmission out of the MQTT callback because the callback runs inside
// the WiFi network context. Blocking the network context for 300ms triggers the Hardware WDT!
uint16_t* globalRawArray = nullptr;
uint16_t globalRawLen = 0;
bool pendingTransmission = false;

// --- AC SIGNAL CHUNKER (FLAWLESS HARDWARE RMT TIMING) ---
// The Arduino Uno succeeded because it used hardware timers perfectly.
// To get that same perfection on ESP32, we MUST use the official `irsend.sendRaw` (which uses the ESP32 RMT hardware).
void sendACRaw(uint16_t* rawArray, uint16_t rawLen) {
  // --- HIGH-PRECISION AC SIGNAL CHUNKER ---
  // ESP32's RMT hardware buffer struggles with massive >250 pulse arrays.
  // We split the signal at the long protocol gap (e.g., 5.2ms or 20ms).
  // CRITICAL FIX: We now use ONLY delayMicroseconds() for the gap. 
  // Standard delay() yields to the WiFi OS and ruins microsecond timing!
  
  uint16_t startIndex = 0;
  
  for (uint16_t i = 0; i < rawLen; i++) {
    // Look for a massive gap (Space, which is an odd index) > 5000us.
    // We changed this from 4000 to 5000 because your header space is 4700!
    // If we split at 4700, we corrupt the header. We only want to split at the 5400us gap!
    if (i % 2 != 0 && rawArray[i] > 5000) {
      uint16_t chunkLen = i - startIndex;
      
      // RMT expects an ODD length (must end with a MARK). 
      if (chunkLen % 2 == 0) chunkLen--; 
      
      // Send the first half
      irsend.sendRaw(rawArray + startIndex, chunkLen, 38);
      
      // Busy-wait the exact microsecond gap. This blocks the CPU completely
      // to guarantee flawless hardware timing, just like the Arduino Uno!
      delayMicroseconds(rawArray[i]);
      
      startIndex = i + 1; // Set start point for the remaining array
    }
  }
  
  // Send the remaining second half
  if (startIndex < rawLen) {
    uint16_t chunkLen = rawLen - startIndex;
    if (chunkLen % 2 == 0) chunkLen--; 
    irsend.sendRaw(rawArray + startIndex, chunkLen, 38);
  }
}

bool parseAndScheduleRaw(uint16_t rawLen, const String& valuesStr) {
  if (rawLen == 0 || valuesStr.length() == 0) return false;

  if (globalRawArray != nullptr) {
    delete[] globalRawArray;
    globalRawArray = nullptr;
  }

  globalRawArray = new uint16_t[rawLen];
  int count = 0;
  int pos = 0;
  int nextComma = 0;

  while (nextComma >= 0 && count < rawLen) {
    nextComma = valuesStr.indexOf(',', pos);
    if (nextComma > 0) {
      globalRawArray[count++] = valuesStr.substring(pos, nextComma).toInt();
      pos = nextComma + 1;
    } else {
      globalRawArray[count++] = valuesStr.substring(pos).toInt();
    }
    if (count % 20 == 0) yield();
  }

  globalRawLen = count;
  pendingTransmission = true;
  return true;
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
  Serial.println("\n--- NEW SIGNAL RECEIVED FROM CLOUD ---");
  
  DynamicJsonDocument doc(8192);
  DeserializationError error = deserializeJson(doc, payload, length);
  
  if (error) {
    Serial.println("Error: Invalid JSON payload.");
    return;
  }

  String authPass = doc["auth"] | "";
  String cmdStr = doc["cmd"] | "";

  if (cmdStr == "PING") {
    Serial.println("STATUS:PING_RECEIVED -> Replying ONLINE");
    client.publish(mqtt_topic_tx, "STATUS:ONLINE", true);
    return;
  }

  // --- HARDWARE SECURITY GATEWAY ---
  // Reject any action or transmission unless the client provides the correct secret hub password
  if (authPass != hub_password) {
    Serial.printf("⛔ [SECURITY ALERT] Unauthorized command rejected! Invalid auth: '%s'\n", authPass.c_str());
    client.publish(mqtt_topic_tx, "STATUS:SECURITY_UNAUTHORIZED_BLOCKED");
    return;
  }

  if (cmdStr == "LEARN_START") {
    isLearningMode = true;
    irrecv.resume();
    Serial.println("STATUS:LEARNING_ACTIVE");
    client.publish(mqtt_topic_tx, "STATUS:LEARNING_ACTIVE");
    return;
  } else if (cmdStr == "LEARN_STOP") {
    isLearningMode = false;
    irrecv.pause();
    Serial.println("STATUS:LEARNING_IDLE");
    client.publish(mqtt_topic_tx, "STATUS:LEARNING_IDLE");
    return;
  }

  String typeStr = doc["type"] | "standard";

  if (typeStr == "raw") {
    uint16_t rawLen = doc["len"] | 0;
    String valuesStr = doc["values"] | "";
    
    if (parseAndScheduleRaw(rawLen, valuesStr)) {
      Serial.printf("MQTT: RAW signal scheduled (%d pulses).\n", globalRawLen);
    }
  } else {
    // Handle standard NEC/Samsung/TV signals
    String protocolStr = doc["protocol"] | "NEC";
    String codeStr = doc["code"] | "0x0";
    uint16_t bits = doc["bits"] | 32;

    uint64_t code = strtoull(codeStr.c_str(), NULL, 0);
    decode_type_t protocol = strToDecodeType(protocolStr.c_str());

    Serial.printf("Transmitting Protocol: %s, Code: %s\n", protocolStr.c_str(), codeStr.c_str());
    irsend.send(protocol, code, bits);
  }
}

void startWiFiConnection() {
  if (WiFi.status() == WL_CONNECTED) return;
  Serial.printf("STATUS:WIFI_CONNECTING to '%s'...\n", ssid);
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  wifiConnectStartTime = millis();
}

void reconnectMQTT() {
  if (!wifiEnabled) return;

  if (WiFi.status() != WL_CONNECTED) {
    if (wifiConnectStartTime == 0) {
      startWiFiConnection();
    } else if (millis() - wifiConnectStartTime > 20000) {
      int st = WiFi.status();
      if (st == WL_NO_SSID_AVAIL) {
        Serial.printf("STATUS:HOTSPOT_NOT_FOUND: '%s' not visible. Ensure 2.4GHz is enabled on phone!\n", ssid);
      } else if (st == WL_CONNECT_FAILED) {
        Serial.println("STATUS:WIFI_AUTH_FAILED: Password rejected.");
      } else {
        Serial.printf("STATUS:WIFI_TIMEOUT (code %d), retrying...\n", st);
      }
      WiFi.disconnect();
      WiFi.mode(WIFI_STA);
      WiFi.begin(ssid, password);
      wifiConnectStartTime = millis();
    }
    return;
  }

  // WiFi is connected! Reset connect timer
  wifiConnectStartTime = 0;

  static unsigned long lastMqttAttempt = 0;
  if (!client.connected()) {
    if (millis() - lastMqttAttempt < 5000) return;
    lastMqttAttempt = millis();

    Serial.print("Connecting to MQTT Broker (broker.hivemq.com)...");
    String clientId = "ESP32Hub-" + String(random(0xffff), HEX);
    
    // Use Last Will and Testament to tell the frontend if the device drops offline
    if (client.connect(clientId.c_str(), mqtt_topic_tx, 1, true, "STATUS:OFFLINE")) {
      Serial.println(" SUCCESS!");
      client.publish(mqtt_topic_tx, "STATUS:ONLINE", true);
      client.subscribe(mqtt_topic_rx);
      Serial.printf("STATUS:WIFI_CONNECTED:IP:%s\n", WiFi.localIP().toString().c_str());
    } else {
      Serial.printf(" FAILED (rc=%d), will retry in 5s.\n", client.state());
    }
  }
}

void setup() {
  Serial.begin(115200);
  irsend.begin();
  
  // Start the IR receiver with internal pull-up enabled to reduce noise
  pinMode(kIrRecvPin, INPUT_PULLUP); 
  irrecv.enableIRIn(); 
  irrecv.pause(); // Start paused by default to completely eliminate WiFi RF noise!
  
  // Indicator LED (Built-in Blue LED on GPIO 2) for visible visual feedback
  pinMode(2, OUTPUT);
  digitalWrite(2, HIGH); // Flash once on boot to visually confirm firmware started!
  delay(150);
  digitalWrite(2, LOW);

  // Start with WiFi enabled so it connects to MQTT right away!
  wifiEnabled = true;
  startWiFiConnection();
  
  client.setServer(mqtt_server, 1883);
  client.setCallback(mqttCallback);
  client.setBufferSize(8192); // Required for receiving huge AC strings
  
  Serial.println(F("HUB_READY: Dual Mode Active (115200 baud)"));
}

void loop() {
  if (wifiEnabled) {
    if (WiFi.status() == WL_CONNECTED && client.connected()) {
      client.loop();

      // Periodic heartbeat every 20 seconds so mobile apps immediately know hub is active
      static unsigned long lastHeartbeat = 0;
      if (millis() - lastHeartbeat > 20000) {
        lastHeartbeat = millis();
        client.publish(mqtt_topic_tx, "STATUS:ONLINE", true);
      }
    } else {
      reconnectMQTT();
    }
  }

  // --- USB SERIAL STREAMING TRANSMIT & COMMAND HANDLING ---
  if (Serial.available() > 0) {
    String serialLine = Serial.readStringUntil('\n');
    serialLine.trim();

    if (serialLine.startsWith("SEND_RAW:")) {
      int firstColon = serialLine.indexOf(':');
      int secondColon = serialLine.indexOf(':', firstColon + 1);
      if (firstColon > 0 && secondColon > firstColon) {
        int rawLen = serialLine.substring(firstColon + 1, secondColon).toInt();
        String valuesStr = serialLine.substring(secondColon + 1);
        if (parseAndScheduleRaw(rawLen, valuesStr)) {
          Serial.println(F("STATUS:REPLAYING_RAW_SIGNAL"));
        }
      }
    } else if (serialLine == "CMD:WIFI_START" || serialLine == "WIFI:ON" || serialLine == "WIFI_CONNECT") {
      wifiEnabled = true;
      Serial.println(F("STATUS:WIFI_ENABLED"));
      startWiFiConnection();
    } else if (serialLine == "CMD:WIFI_STOP" || serialLine == "WIFI:OFF" || serialLine == "WIFI_DISCONNECT") {
      wifiEnabled = false;
      wifiConnectStartTime = 0;
      if (client.connected()) {
        client.publish(mqtt_topic_tx, "STATUS:OFFLINE", true);
        client.disconnect();
      }
      WiFi.disconnect(true);
      WiFi.mode(WIFI_OFF);
      Serial.println(F("STATUS:WIFI_DISABLED"));
    } else if (serialLine == "CMD:WIFI_STATUS") {
      if (wifiEnabled && WiFi.status() == WL_CONNECTED) {
        Serial.printf("STATUS:WIFI_ACTIVE:IP:%s:MQTT:%s\n", 
          WiFi.localIP().toString().c_str(), 
          client.connected() ? "CONNECTED" : "CONNECTING");
      } else if (wifiEnabled) {
        Serial.println(F("STATUS:WIFI_CONNECTING"));
      } else {
        Serial.println(F("STATUS:WIFI_OFF"));
      }
    } else if (serialLine == "CMD:LEARN_START" || serialLine == "LEARN:ON") {
      isLearningMode = true;
      irrecv.resume();
      Serial.println(F("STATUS:LEARNING_ACTIVE"));
    } else if (serialLine == "CMD:LEARN_STOP" || serialLine == "LEARN:OFF") {
      isLearningMode = false;
      irrecv.pause();
      Serial.println(F("STATUS:LEARNING_IDLE"));
    } else if (serialLine == "CMD:AUTO_WIFI_ON") {
      preferences.begin("irhub", false);
      preferences.putBool("auto_wifi", true);
      preferences.end();
      wifiEnabled = true;
      Serial.println(F("STATUS:AUTO_WIFI_ENABLED"));
      startWiFiConnection();
    } else if (serialLine == "CMD:AUTO_WIFI_OFF") {
      preferences.begin("irhub", false);
      preferences.putBool("auto_wifi", false);
      preferences.end();
      wifiEnabled = false;
      wifiConnectStartTime = 0;
      if (client.connected()) client.disconnect();
      WiFi.disconnect(true);
      WiFi.mode(WIFI_OFF);
      Serial.println(F("STATUS:AUTO_WIFI_DISABLED"));
    } else if (serialLine == "CMD:TEST_LED" || serialLine == "TEST") {
      Serial.println(F("TEST: Blinking Blue LED & Sending Test IR Pulse..."));
      digitalWrite(2, HIGH);
      irsend.sendNEC(0x00FFE01F, 32);
      delay(300);
      digitalWrite(2, LOW);
      Serial.println(F("TEST_OK"));
    }
  }

  // --- SAFE TRANSMISSION EXECUTION ---
  if (pendingTransmission) {
    digitalWrite(2, HIGH); // Visible visual feedback on ESP32 onboard LED!
    irrecv.pause(); // STOP receiver to prevent interrupt interference with RMT timing
    
    Serial.println("\n--- EMITTING IR SIGNAL ---");
    Serial.printf("Buffer Size: %d pulses\n", globalRawLen);
    sendACRaw(globalRawArray, globalRawLen);
    Serial.println("Emission Complete!");
    Serial.println(F("SEND_OK"));
    
    delete[] globalRawArray;
    globalRawArray = nullptr;
    pendingTransmission = false;
    digitalWrite(2, LOW);
    
    if (isLearningMode) {
      irrecv.resume(); // Only resume if learning mode was active
    }
  }

  // --- IR RECEIVER LOGIC (ONLY RUNS WHEN ACTIVELY LEARNING) ---
  if (isLearningMode && irrecv.decode(&results)) {
    // FILTER: Lowered from 40 to 15 to allow shorter TV remote signals (like your 21-pulse signal)
    if (results.rawlen > 15) {
      Serial.println("\n--- IR SIGNAL CAPTURED ---");
    
      // Print basic info (Protocol, Bits, Hex Code)
      serialPrintUint64(results.value, HEX);
      Serial.print(" (Protocol: ");
      Serial.print(typeToString(results.decode_type));
      Serial.print(", Bits: ");
      Serial.print(results.bits);
      Serial.println(")");

      String payload;
      payload.reserve(results.rawlen * 6 + 30);
      payload = "RAW:";
      payload += results.rawlen;
      payload += ":";

      for (uint16_t i = 1; i < results.rawlen; i++) {
        uint32_t usecs = results.rawbuf[i] * kRawTick;
        payload += usecs;
        if (i < results.rawlen - 1) {
          payload += ",";
        }
      }
      
      // Output clean single-line payload for USB Serial capture
      Serial.println(payload);
      
      // Also publish to MQTT if connected
      if (wifiEnabled && client.connected()) {
        if (client.publish(mqtt_topic_tx, payload.c_str())) {
          Serial.println("Published captured RAW signal to MQTT.");
        }
      }

      // Auto-pause receiver immediately after successful capture
      isLearningMode = false;
      irrecv.pause();
      Serial.println(F("STATUS:LEARNING_COMPLETE"));
    } else {
      irrecv.resume(); // Receive the next value
    }
  }

  static unsigned long lastHeartbeat = 0;
  if (millis() - lastHeartbeat > 10000) {
    lastHeartbeat = millis();
    if (wifiEnabled && WiFi.status() == WL_CONNECTED) {
      Serial.printf("HUB_ALIVE: IP %s, RSSI %d\n", WiFi.localIP().toString().c_str(), WiFi.RSSI());
    } else {
      Serial.println(F("HUB_ALIVE: USB_ACTIVE (WiFi Standby)"));
    }
  }
}
