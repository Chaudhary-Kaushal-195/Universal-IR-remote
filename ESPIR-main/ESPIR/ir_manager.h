/**
 * IR Manager - Handles IR transmission and reception
 */

#ifndef IR_MANAGER_H
#define IR_MANAGER_H

#include <Arduino.h>
#include <IRremoteESP8266.h>
#include <IRsend.h>
#include <IRrecv.h>
#include <IRutils.h>
#include "config.h"

struct IRCode
{
    decode_type_t protocol;
    uint64_t data; // Changed from 'value' to 'data' for consistency
    uint16_t bits;
    uint16_t *rawData;
    uint16_t rawLen;
    String description;
};

class IRManager
{
private:
    IRsend *irSend;
    IRrecv *irRecv;
    decode_results results;
    bool learning;
    unsigned long learnStartTime;
    IRCode lastLearned;

public:
    IRManager();
    ~IRManager();

    bool begin();
    void update();

    // Transmission methods
    bool transmitCode(const IRCode &code);
    bool transmitRaw(uint16_t *rawData, uint16_t length);
    bool transmitProtocol(decode_type_t protocol, uint64_t value, uint16_t bits);

    // Reception methods
    bool startLearning();
    bool stopLearning();
    bool isLearning() { return learning; }
    bool hasLearnedCode();
    IRCode getLearnedCode();

    // Utility methods
    String encodeIRCode(const IRCode &code);
    IRCode decodeIRCode(const String &encoded);
    void printIRCode(const IRCode &code);

    // Status methods
    bool isReady();
    String getStatus();
};

#endif // IR_MANAGER_H