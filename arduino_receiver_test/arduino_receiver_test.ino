/*
  BARE-METAL IR RECEIVER TEST
  - This sketch simply reads IR pulses and dumps them to the Serial Monitor
  - It generates a C++ formatted array you can copy directly!
*/
#include <Arduino.h>

// Increase buffer to 750 for massive AC double-frames
#define RAW_BUFFER_LENGTH 750 
#define IR_RECEIVE_PIN 7 

#include <IRremote.hpp>

void setup() {
  Serial.begin(115200);
  IrReceiver.begin(IR_RECEIVE_PIN, ENABLE_LED_FEEDBACK);
  Serial.println(F("Ready to receive IR signals. Point remote and press a button..."));
}

void loop() {
  if (IrReceiver.decode()) {
    Serial.println(F("\n--- NEW SIGNAL DETECTED ---"));
    
    uint16_t len = IrReceiver.irparams.rawlen;
    
    // Print the raw microsecond durations as a C++ array
    Serial.print(F("uint16_t rawData["));
    Serial.print(len - 1);
    Serial.print(F("] = {"));
    
    for (uint16_t i = 1; i < len; i++) {
        // IRremote stores ticks. Multiply by 50 to get real microseconds.
        Serial.print(IrReceiver.irparams.rawbuf[i] * 50);
        if (i < len - 1) Serial.print(F(", "));
    }
    
    Serial.println(F("};"));
    Serial.println(F("---------------------------\n"));
    
    // Resume listening for the next signal
    IrReceiver.resume(); 
  }
}
