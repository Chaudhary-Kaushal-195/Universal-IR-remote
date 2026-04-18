/*
  Universal IR Remote Hub
  Hardware: ESP32 + IR Emitter + IR Receiver
  Library: IRremoteESP8266, ESPAsyncWebServer, AsyncTCP, PubSubClient (MQTT)
*/

#include <Arduino.h>
#include <WiFi.h>
#include <ESPAsyncWebServer.h>
#include <IRremoteESP8266.h>
#include <IRsend.h>
#include <IRrecv.h>
#include <IRutils.h>
#include <ArduinoJson.h>
#include <PubSubClient.h>

// --- CONFIGURATION ---
const char* ssid = "Galaxy M35 5G A733";
const char* password = "fd69krye6b38cfd";
const char* mqtt_server = "broker.hivemq.com";
const char* mqtt_topic_rx = "universalo-hub/YOUR_SECRET_ID/rx"; // ESP32 Listens here
const char* mqtt_topic_tx = "universalo-hub/YOUR_SECRET_ID/tx"; // ESP32 Publishes here
const uint16_t mqtt_port = 1883;

const uint16_t kIrLedPin = 4;     // IR Emitter Pin
const uint16_t kIrRecvPin = 14;   // IR Receiver Pin
const uint32_t kBaudRate = 115200;
const uint16_t kCaptureBufferSize = 1024;
const uint8_t kTimeout = 50;

// --- OBJECTS ---
IRsend irsend(kIrLedPin);
IRrecv irrecv(kIrRecvPin, kCaptureBufferSize, kTimeout, true);
decode_results results;
AsyncWebServer server(80);

WiFiClient espClient;
PubSubClient client(espClient);

void mqttCallback(char* topic, byte* payload, unsigned int length) {
  Serial.print("MQTT message arrived on topic: ");
  Serial.println(topic);

  DynamicJsonDocument doc(8192);
  deserializeJson(doc, payload, length);
  
  String typeStr = doc["type"] | "standard";

  if (typeStr == "raw") {
    uint16_t rawLen = doc["len"] | 0;
    String valuesStr = doc["values"] | "";
    
    Serial.print("Sending RAW IR from MQTT... Len: ");
    Serial.println(rawLen);

    if (rawLen > 0 && valuesStr.length() > 0) {
      uint16_t* rawArray = new uint16_t[rawLen];
      int pos = 0;
      int nextComma = 0;
      int i = 0;
      while (nextComma >= 0 && i < rawLen) {
          nextComma = valuesStr.indexOf(',', pos);
          if (nextComma > 0) {
              rawArray[i++] = valuesStr.substring(pos, nextComma).toInt();
              pos = nextComma + 1;
          } else {
              rawArray[i++] = valuesStr.substring(pos).toInt();
          }
      }
      
      irsend.sendRaw(rawArray, i, 38); // Replay at 38kHz
      delete[] rawArray;
    }
  } else {
    String protocolStr = doc["protocol"] | "NEC";
    String codeStr = doc["code"] | "0x0";
    uint16_t bits = doc["bits"] | 32;

    uint64_t code = strtoull(codeStr.c_str(), NULL, 0);
    decode_type_t protocol = strToDecodeType(protocolStr.c_str());

    Serial.print("Sending IR from MQTT: ");
    Serial.print(protocolStr);
    Serial.print(" Code: ");
    Serial.println(codeStr);

    irsend.send(protocol, code, bits);
  }
}

void reconnectMQTT() {
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    String clientId = "ESP32Client-";
    clientId += String(random(0xffff), HEX);
    if (client.connect(clientId.c_str())) {
      Serial.println("connected to HiveMQ");
      // Subscribe to commands coming from the web app
      client.subscribe(mqtt_topic_rx);
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      delay(5000);
    }
  }
}

void setup() {
  Serial.begin(kBaudRate);
  
  // Initialize IR
  irsend.begin();
  irrecv.enableIRIn();

  // Connect WiFi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi Connected!");
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());

  // Setup MQTT
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(mqttCallback);

  // --- LOCAL API Endpoints ---
  // GET /status
  server.on("/status", HTTP_GET, [](AsyncWebServerRequest *request){
    request->send(200, "application/json", "{\"status\":\"online\"}");
  });

  // POST /send
  server.on("/send", HTTP_POST, [](AsyncWebServerRequest * request){
    // Handled in callback
  }, NULL, [](AsyncWebServerRequest * request, uint8_t *data, size_t len, size_t index, size_t total) {
    static String body = "";
    if (index == 0) {
      body = "";
      body.reserve(total);
    }
    for (size_t i = 0; i < len; i++) body += (char)data[i];
    if (index + len == total) {
      // Process local POST standard/raw signals mapping identically to MQTT
      DynamicJsonDocument doc(8192);
      deserializeJson(doc, body);
      // Local sending bypasses MQTT, handled natively right here
      mqttCallback("local-wifi", (byte*)body.c_str(), body.length());
      request->send(200, "application/json", "{\"status\":\"sent\"}");
    }
  });

  // GET /receive (Blocks until signal received or times out)
  server.on("/receive", HTTP_GET, [](AsyncWebServerRequest *request){
    Serial.println("Entering Local HTTP Receive mode...");
    unsigned long startTime = millis();
    bool captured = false;

    while (millis() - startTime < 10000) { // 10 second timeout
      if (irrecv.decode(&results)) {
        String protocol = typeToString(results.decode_type);
        String code = "0x" + uint64ToString(results.value, 16);
        
        String rawValuesStr = "";
        uint16_t rawCount = results.rawlen - 1;
        for (uint16_t i = 1; i < results.rawlen; i++) {
          rawValuesStr += String(results.rawbuf[i] * 2); 
          if (i < results.rawlen - 1) rawValuesStr += ",";
        }
        
        DynamicJsonDocument doc(8192);
        doc["protocol"] = protocol;
        doc["code"] = code;
        doc["bits"] = results.bits;
        doc["len"] = rawCount;
        doc["values"] = rawValuesStr;

        String response;
        serializeJson(doc, response);
        request->send(200, "application/json", response);
        
        // Also fire off the copied signal over MQTT so any web app listening globally can store it!
        client.publish(mqtt_topic_tx, response.c_str());

        irrecv.resume();
        captured = true;
        break;
      }
      delay(10);
    }

    if (!captured) {
      request->send(408, "application/json", "{\"error\":\"timeout\"}");
    }
  });

  // CORS Handle
  DefaultHeaders::Instance().addHeader("Access-Control-Allow-Origin", "*");
  DefaultHeaders::Instance().addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
  DefaultHeaders::Instance().addHeader("Access-Control-Allow-Headers", "Content-Type");

  server.onNotFound([](AsyncWebServerRequest *request) {
    if (request->method() == HTTP_OPTIONS) request->send(200);
    else request->send(404);
  });

  server.begin();
}

void loop() {
  if (!client.connected()) {
    reconnectMQTT();
  }
  client.loop();
}
