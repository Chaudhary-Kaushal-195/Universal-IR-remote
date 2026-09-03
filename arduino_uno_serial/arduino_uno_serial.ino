/*
  SUPER-BUFFER CLONER - ARDUINO UNO (High-Precision Telemetry & AC Support)
  - Diagnostic Telemetry & Pulse Counter Verification
  - Robust Serial Buffer Handling for Long AC Strings
*/

#include <Arduino.h>

#define RAW_BUFFER_LENGTH 750 
#define IR_SEND_PIN 3
#define IR_RECEIVE_PIN 7 

#include <IRremote.hpp>

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(100); // Fast timeout for serial reads
  
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);

  IrReceiver.begin(IR_RECEIVE_PIN, ENABLE_LED_FEEDBACK);
  IrSender.begin(ENABLE_LED_FEEDBACK); 

  Serial.println(F("HUB_READY"));
  Serial.println(F("DEBUG: Arduino Uno IR Hub with Telemetry Active"));
}

void loop() {
  // --- 1. CAPTURE FROM PHYSICAL REMOTE ---
  if (IrReceiver.decode()) {
    uint16_t len = IrReceiver.irparams.rawlen;
    
    // Ignore short noise pulses
    if (len >= 8) {
      Serial.println(F("STATUS:SENSING_SIGNAL")); 
      
      // Telemetry summary
      Serial.print(F("CAPTURE_INFO:LEN="));
      Serial.print(len);
      Serial.print(F(":FIRST4="));
      for (uint16_t k = 1; k <= 4 && k < len; k++) {
        Serial.print(IrReceiver.irparams.rawbuf[k] * 50);
        if (k < 4 && k < len - 1) Serial.print(F(","));
      }
      Serial.println();

      // Full raw string for Web App storage
      Serial.print(F("RAW:"));
      Serial.print(len);
      Serial.print(F(":"));
      for (uint16_t i = 1; i < len; i++) {
        Serial.print(IrReceiver.irparams.rawbuf[i] * 50);
        if (i < len - 1) Serial.print(F(","));
      }
      Serial.println();
    }
    IrReceiver.resume(); 
  }

  // --- 2. TRANSMIT COMMAND FROM WEB APP ---
  if (Serial.available() > 0) {
    if (Serial.find("SEND_RAW:")) {
      handleStreamingRawSend();
    }
  }
}

void handleStreamingRawSend() {
  IrReceiver.stop(); 

  int expectedLen = Serial.parseInt();
  
  // Skip colon separator
  while (Serial.available() > 0 && Serial.peek() != ':') {
    Serial.read();
  }
  if (Serial.peek() == ':') Serial.read();

  uint16_t* sharedBuffer = (uint16_t*)IrReceiver.irparams.rawbuf;
  int count = 0;
  int targetCount = expectedLen - 1;

  unsigned long startWait = millis();
  while (count < targetCount && count < RAW_BUFFER_LENGTH && (millis() - startWait < 3500)) {
    if (Serial.available() > 0) {
      sharedBuffer[count++] = (uint16_t)Serial.parseInt();
      startWait = millis(); // Refresh timeout on incoming byte
    }
  }

  Serial.println(F("STATUS:REPLAYING_RAW_SIGNAL"));
  
  // Diagnostic Telemetry sent back to Web App:
  Serial.print(F("TELEMETRY:EXPECTED="));
  Serial.print(targetCount);
  Serial.print(F(":RECEIVED="));
  Serial.print(count);
  Serial.print(F(":FIRST4="));
  for (int j = 0; j < 4 && j < count; j++) {
    Serial.print(sharedBuffer[j]);
    if (j < 3 && j < count - 1) Serial.print(F(","));
  }
  Serial.print(F(":LAST4="));
  int startLast = (count > 4) ? (count - 4) : 0;
  for (int j = startLast; j < count; j++) {
    Serial.print(sharedBuffer[j]);
    if (j < count - 1) Serial.print(F(","));
  }
  Serial.println();

  if (count < targetCount) {
    Serial.print(F("WARN:DROPPED_PULSES:Expected "));
    Serial.print(targetCount);
    Serial.print(F(" but only got "));
    Serial.println(count);
  }

  digitalWrite(LED_BUILTIN, HIGH);
  IrSender.sendRaw(sharedBuffer, count, 38);
  digitalWrite(LED_BUILTIN, LOW);

  Serial.println(F("SEND_OK"));
  IrReceiver.start();
}
