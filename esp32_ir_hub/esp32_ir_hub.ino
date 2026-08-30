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

// --- CONFIGURATION ---
const char* ssid = "Galaxy M17 5G 7A52";
const char* password = "12121212";
const char* mqtt_server = "broker.hivemq.com";
const char* mqtt_topic_rx = "universalo-hub/kaushal-ir-hub-97/rx"; // ESP32 Listens here
const char* mqtt_topic_tx = "universalo-hub/kaushal-ir-hub-97/tx"; // ESP32 Publishes status here

const uint16_t kIrLedPin = 4;   // IR Emitter Pin
const uint16_t kIrRecvPin = 27; // CHANGED PIN: GPIO 27 is quieter than Pin 15
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



void mqttCallback(char* topic, byte* payload, unsigned int length) {
  Serial.println("\n--- NEW SIGNAL RECEIVED FROM CLOUD ---");
  
  DynamicJsonDocument doc(8192);
  DeserializationError error = deserializeJson(doc, payload, length);
  
  if (error) {
    Serial.println("Error: Invalid JSON payload.");
    return;
  }

  String typeStr = doc["type"] | "standard";

  if (typeStr == "raw") {
    uint16_t rawLen = doc["len"] | 0;
    String valuesStr = doc["values"] | "";
    
    if (rawLen == 0 || valuesStr.length() == 0) return;

    Serial.printf("Storing RAW signal (Length: %d)...\n", rawLen);

    if (globalRawArray != nullptr) delete[] globalRawArray;
    globalRawArray = new uint16_t[rawLen];
    
    int i = 0;
    int pos = 0;
    int nextComma = 0;

    while (nextComma >= 0 && i < rawLen) {
      nextComma = valuesStr.indexOf(',', pos);
      if (nextComma > 0) {
        globalRawArray[i++] = valuesStr.substring(pos, nextComma).toInt();
        pos = nextComma + 1;
      } else {
        globalRawArray[i++] = valuesStr.substring(pos).toInt();
      }
      if (i % 20 == 0) yield(); 
    }

    globalRawLen = i;
    pendingTransmission = true;
    Serial.println("Signal stored and scheduled for transmission.");

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

void reconnectMQTT() {
  while (!client.connected()) {
    if (WiFi.status() != WL_CONNECTED) {
      Serial.print("Connecting to WiFi.");
      WiFi.disconnect();
      WiFi.begin(ssid, password);
      while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
      }
      Serial.println(" Connected!");
      Serial.print("IP Address (Copy to Web): ");
      Serial.println(WiFi.localIP());
    }

    Serial.print("Connecting to MQTT Broker...");
    String clientId = "ESP32Hub-" + String(random(0xffff), HEX);
    
    // Use Last Will and Testament to tell the frontend if the device drops offline
    if (client.connect(clientId.c_str(), mqtt_topic_tx, 1, true, "STATUS:OFFLINE")) {
      Serial.println(" SUCCESS!");
      client.publish(mqtt_topic_tx, "STATUS:ONLINE", true);
      client.subscribe(mqtt_topic_rx);
    } else {
      Serial.print(" FAILED, rc=");
      Serial.print(client.state());
      Serial.println(". Retrying in 5s...");
      delay(5000);
    }
  }
}

void setup() {
  Serial.begin(115200);
  irsend.begin();
  
  // Start the IR receiver with internal pull-up enabled to reduce noise
  pinMode(kIrRecvPin, INPUT_PULLUP); 
  irrecv.enableIRIn(); 
  
  pinMode(kIrLedPin, OUTPUT); // Ensure pin is ready for custom AC transmitter
  digitalWrite(kIrLedPin, LOW);
  
  client.setServer(mqtt_server, 1883);
  client.setCallback(mqttCallback);
  client.setBufferSize(8192); // Required for receiving huge AC strings
}

void loop() {
  if (!client.connected()) {
    reconnectMQTT();
  }
  client.loop();

  // --- SAFE TRANSMISSION EXECUTION ---
  if (pendingTransmission) {
    irrecv.pause(); // STOP receiver to prevent interrupt interference with RMT timing
    
    Serial.println("\n--- EMITTING IR SIGNAL ---");
    Serial.printf("Buffer Size: %d pulses\n", globalRawLen);
    for (int i = 0; i < globalRawLen; i++) {
      Serial.print(globalRawArray[i]);
      if (i < globalRawLen - 1) Serial.print(",");
    }
    Serial.println("\n--------------------------");

    sendACRaw(globalRawArray, globalRawLen);
    Serial.println("Emission Complete!");
    
    delete[] globalRawArray;
    globalRawArray = nullptr;
    pendingTransmission = false;
    
    irrecv.resume(); // RESTART receiver
  }

  // --- IR RECEIVER LOGIC ---
  if (irrecv.decode(&results)) {
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

    // Print Raw Data (Microseconds) and build MQTT payload
    Serial.print("Raw Data (");
    Serial.print(results.rawlen);
    Serial.print("): ");

    String payload;
    // Pre-allocate memory to prevent heap fragmentation
    payload.reserve(results.rawlen * 6 + 30);
    payload = "RAW:";
    payload += results.rawlen;
    payload += ":";

    for (uint16_t i = 1; i < results.rawlen; i++) {
      uint32_t usecs = results.rawbuf[i] * kRawTick;
      Serial.print(usecs);
      payload += usecs;
      if (i < results.rawlen - 1) {
        Serial.print(",");
        payload += ",";
      }
    }
    Serial.println("\n--------------------------");
    
    // Publish to the cloud so the web app can learn it over WiFi!
    if (client.publish(mqtt_topic_tx, payload.c_str())) {
      Serial.println("Published captured RAW signal to MQTT.");
    } else {
      Serial.println("Failed to publish to MQTT. Payload too large?");
    }
    }

    irrecv.resume(); // Receive the next value
  }

  static unsigned long lastHeartbeat = 0;
  if (millis() - lastHeartbeat > 10000) {
    lastHeartbeat = millis();
    Serial.printf("HUB_ALIVE: IP %s, RSSI %d\n", WiFi.localIP().toString().c_str(), WiFi.RSSI());
  }
}
