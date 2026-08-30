/**
 * IR Manager Implementation
 */

#include "ir_manager.h"
#include <ArduinoJson.h>

IRManager::IRManager() : irSend(nullptr), irRecv(nullptr), learning(false), learnStartTime(0)
{
    memset(&lastLearned, 0, sizeof(IRCode));
    memset(&results, 0, sizeof(decode_results));
}

IRManager::~IRManager()
{
    if (irSend)
        delete irSend;
    if (irRecv)
        delete irRecv;
}

bool IRManager::begin()
{
    DEBUG_PRINTLN("Initializing IR Manager...");

    // Initialize IR transmitter
    irSend = new IRsend(IR_TRANSMIT_PIN);
    irSend->begin();

    // Initialize IR receiver
    irRecv = new IRrecv(IR_RECEIVE_PIN);
    irRecv->setUnknownThreshold(12);
    irRecv->enableIRIn();

    DEBUG_PRINTLN("IR Manager initialized successfully");
    return true;
}

void IRManager::update()
{
    if (learning && irRecv->decode(&results))
    {
        // Copy the decoded result to lastLearned
        lastLearned.protocol = results.decode_type;
        lastLearned.data = results.value; // Fixed: use 'data' field
        lastLearned.bits = results.bits;

        // Copy raw data if available
        if (results.rawlen > 0)
        {
            lastLearned.rawLen = results.rawlen;
            lastLearned.rawData = new uint16_t[results.rawlen];
            memcpy(lastLearned.rawData, (const void *)results.rawbuf, results.rawlen * sizeof(uint16_t));
        }

        learning = false;
        DEBUG_PRINTLN("IR code learned successfully");
        printIRCode(lastLearned);

        irRecv->resume(); // Prepare for next reception
    }

    // Check for learning timeout
    if (learning && (millis() - learnStartTime > IR_TIMEOUT_MS))
    {
        learning = false;
        DEBUG_PRINTLN("IR learning timeout");
    }
}

bool IRManager::transmitCode(const IRCode &code)
{
    if (!irSend)
        return false;

    DEBUG_PRINT("Transmitting IR code: ");
    DEBUG_PRINTLN(typeToString(code.protocol));

    if (code.rawData && code.rawLen > 0)
    {
        // Send raw data
        irSend->sendRaw(code.rawData, code.rawLen, IR_FREQUENCY);
    }
    else
    {
        // Send protocol-specific data
        switch (code.protocol)
        {
        case NEC:
            irSend->sendNEC(code.data, code.bits);
            break;
        case SONY:
            irSend->sendSony(code.data, code.bits);
            break;
        case RC5:
            irSend->sendRC5(code.data, code.bits);
            break;
        case RC6:
            irSend->sendRC6(code.data, code.bits);
            break;
        default:
            DEBUG_PRINTLN("Unsupported protocol");
            return false;
        }
    }

    return true;
}

bool IRManager::startLearning()
{
    if (!irRecv)
        return false;

    DEBUG_PRINTLN("Starting IR learning mode");
    learning = true;
    learnStartTime = millis();

    // Clear previous learned code
    if (lastLearned.rawData)
    {
        delete[] lastLearned.rawData;
        lastLearned.rawData = nullptr;
    }
    memset(&lastLearned, 0, sizeof(IRCode));

    return true;
}

bool IRManager::stopLearning()
{
    learning = false;
    DEBUG_PRINTLN("Stopped IR learning mode");
    return true;
}

bool IRManager::hasLearnedCode()
{
    return !learning && (lastLearned.protocol != UNKNOWN || lastLearned.rawLen > 0);
}

IRCode IRManager::getLearnedCode()
{
    return lastLearned;
}

String IRManager::encodeIRCode(const IRCode &code)
{
    DynamicJsonDocument doc(1024);

    doc["protocol"] = typeToString(code.protocol);
    doc["value"] = String(code.data, HEX);
    doc["bits"] = code.bits;
    doc["description"] = code.description;

    if (code.rawData && code.rawLen > 0)
    {
        JsonArray rawArray = doc.createNestedArray("raw");
        for (uint16_t i = 0; i < code.rawLen; i++)
        {
            rawArray.add(code.rawData[i]);
        }
    }

    String result;
    serializeJson(doc, result);
    return result;
}

IRCode IRManager::decodeIRCode(const String &encoded)
{
    IRCode code;
    memset(&code, 0, sizeof(IRCode));

    DynamicJsonDocument doc(1024);
    deserializeJson(doc, encoded);

    code.protocol = strToDecodeType(doc["protocol"]);
    code.data = strtoll(doc["value"], nullptr, 16);
    code.bits = doc["bits"];
    code.description = doc["description"].as<String>();

    if (doc.containsKey("raw"))
    {
        JsonArray rawArray = doc["raw"];
        code.rawLen = rawArray.size();
        code.rawData = new uint16_t[code.rawLen];
        for (uint16_t i = 0; i < code.rawLen; i++)
        {
            code.rawData[i] = rawArray[i];
        }
    }

    return code;
}

void IRManager::printIRCode(const IRCode &code)
{
    DEBUG_PRINT("Protocol: ");
    DEBUG_PRINTLN(typeToString(code.protocol));
    DEBUG_PRINT("Value: 0x");
    DEBUG_PRINTLN(String(code.data, HEX));
    DEBUG_PRINT("Bits: ");
    DEBUG_PRINTLN(code.bits);
    if (code.rawLen > 0)
    {
        DEBUG_PRINT("Raw length: ");
        DEBUG_PRINTLN(code.rawLen);
    }
}

bool IRManager::isReady()
{
    return (irSend != nullptr && irRecv != nullptr);
}

String IRManager::getStatus()
{
    DynamicJsonDocument doc(256);
    doc["ready"] = isReady();
    doc["learning"] = learning;
    doc["hasLearned"] = hasLearnedCode();

    String result;
    serializeJson(doc, result);
    return result;
}