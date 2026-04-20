/*
  SUPER-BUFFER CLONER - ARDUINO UNO (Fixed Linker Error)
  - Increased Buffer to 450 pulses (Shared RAM optimization)
  - Better timing for long AC strings
*/

#include <Arduino.h>

// --- CRITICAL: High-Capacity Buffer ---
#define RAW_BUFFER_LENGTH 750 
#define IR_SEND_PIN 3
#define IR_RECEIVE_PIN 7 

#include <IRremote.hpp>

void setup() {
  Serial.begin(115200);
  
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);

  // Simplified initialization to fix the "undefined reference" error
  IrReceiver.begin(IR_RECEIVE_PIN, ENABLE_LED_FEEDBACK);
  IrSender.begin(ENABLE_LED_FEEDBACK); 

  Serial.println(F("HUB_READY"));
  Serial.println(F("DEBUG: Super-Buffer Active (450 pulses)"));
}

void loop() {
  // --- CAPTURE ---
  if (IrReceiver.decode()) {
    Serial.println(F("STATUS:SENSING_SIGNAL")); 
    uint16_t len = IrReceiver.irparams.rawlen;
    
    Serial.print(F("RAW:"));
    Serial.print(len);
    Serial.print(F(":"));
    for (uint16_t i = 1; i < len; i++) {
        Serial.print(IrReceiver.irparams.rawbuf[i] * 50);
        if (i < len - 1) Serial.print(F(","));
    }
    Serial.println();
    IrReceiver.resume(); 
  }

  // --- STREAMING TRANSMIT ---
  if (Serial.available() > 0) {
    if (Serial.find("SEND_RAW:")) {
      handleStreamingRawSend();
    }
  }
}

void handleStreamingRawSend() {
  // CRITICAL FIX: Stop receiver to prevent timer interrupts from 
  // corrupting the shared buffer or dropping Serial bytes during download!
  IrReceiver.stop(); 

  int len = Serial.parseInt();
  if (Serial.read() != ':') {
    IrReceiver.start();
    return;
  }

  // Use the shared buffer to save RAM
  uint16_t* sharedBuffer = (uint16_t*)IrReceiver.irparams.rawbuf;
  int count = 0;

  while (count < len - 1 && count < RAW_BUFFER_LENGTH) {
    sharedBuffer[count++] = (uint16_t)Serial.parseInt();
  }

  Serial.println(F("STATUS:REPLAYING_RAW_SIGNAL"));
  
  // Data Integrity Check:
  Serial.print(F("DEBUG_FIRST_TWO:"));
  Serial.print(sharedBuffer[0]);
  Serial.print(F(","));
  Serial.println(sharedBuffer[1]);

  digitalWrite(LED_BUILTIN, HIGH);
  
  IrSender.sendRaw(sharedBuffer, count, 38);
  
  digitalWrite(LED_BUILTIN, LOW);
  Serial.println(F("SEND_OK"));
  
  IrReceiver.start(); // Restart the timer interrupt
}
