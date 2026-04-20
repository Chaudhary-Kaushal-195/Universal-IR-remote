/*
  BARE-METAL IR EMITTER TEST
  - Copy the uint16_t array from the Receiver sketch's Serial Monitor
  - Paste it over the dummy array below
  - Open Serial Monitor, type 's', and press Enter to fire!
*/
#include <Arduino.h>
#define IR_SEND_PIN 3 // Hardcoded PWM pin for Uno

#include <IRremote.hpp>

// =============================================================
// 👇 PASTE YOUR GENERATED ARRAY FROM THE RECEIVER MONITOR BELOW 👇

uint16_t rawData[199] = {4350, 4350, 550, 1600, 550, 550, 500, 1600, 550, 1600, 500, 550, 550, 1600, 500, 550, 550, 1600, 500, 550, 550, 1600, 500, 550, 550, 550, 500, 1600, 550, 500, 550, 1600, 550, 500, 550, 1600, 500, 1650, 500, 1600, 550, 1600, 500, 550, 550, 1600, 500, 550, 550, 1600, 500, 550, 550, 550, 500, 550, 500, 550, 550, 1600, 500, 550, 550, 1600, 500, 550, 550, 1600, 500, 550, 550, 1600, 500, 550, 550, 550, 500, 1600, 550, 550, 500, 1600, 550, 550, 500, 1600, 550, 500, 550, 1600, 550, 1600, 500, 550, 500, 1650, 500, 550, 550, 5150, 4350, 4350, 550, 1600, 550, 550, 500, 1600, 500, 1650, 500, 550, 550, 1600, 500, 550, 550, 1600, 500, 550, 550, 1600, 500, 550, 550, 550, 500, 1600, 550, 550, 500, 1600, 550, 550, 500, 1600, 550, 1600, 500, 1650, 500, 1600, 550, 550, 500, 1600, 550, 550, 550, 1550, 550, 550, 500, 550, 500, 550, 550, 550, 500, 1600, 500, 600, 500, 1600, 500, 550, 550, 1600, 500, 550, 550, 1600, 500, 550, 500, 600, 500, 1600, 550, 550, 500, 1600, 500, 600, 500, 1600, 500, 600, 500, 1600, 500, 1650, 500, 550, 500, 1650, 500, 550, 500};
// =============================================================

void setup() {
  Serial.begin(115200);
  pinMode(LED_BUILTIN, OUTPUT);
  IrSender.begin(ENABLE_LED_FEEDBACK); 
  
  Serial.println(F("Ready to emit. Type 's' in the text box above and press Enter to fire."));
}

void loop() {
  if (Serial.available() > 0) {
    char cmd = Serial.read();
    
    if (cmd == 's' || cmd == 'S') {
      Serial.println(F("FIRING SIGNAL..."));
      digitalWrite(LED_BUILTIN, HIGH);
      
      // Calculate how many items are in your pasted array dynamically
      int dataLength = sizeof(rawData) / sizeof(rawData[0]);
      
      // Blast the signal via Pin 3 at 38 kHz
      IrSender.sendRaw(rawData, dataLength, 38);
      
      digitalWrite(LED_BUILTIN, LOW);
      Serial.println(F("DONE. Did the AC respond?"));
    }
  }
}
