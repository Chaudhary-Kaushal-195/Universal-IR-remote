// Simulate a massive cloud Database of IR Codes (ZaZa style)
export const deviceDatabase = {
  "Samsung TV": {
    "TV_POWER": { type: "standard", protocol: "SAMSUNG", code: "0xE0E040BF", bits: 32 },
    "TV_MUTE":  { type: "standard", protocol: "SAMSUNG", code: "0xE0E0F00F", bits: 32 },
    "TV_INPUT": { type: "standard", protocol: "SAMSUNG", code: "0xE0E0807F", bits: 32 },
    "TV_VOL_UP": { type: "standard", protocol: "SAMSUNG", code: "0xE0E0E01F", bits: 32 },
    "TV_VOL_DOWN": { type: "standard", protocol: "SAMSUNG", code: "0xE0E0D02F", bits: 32 },
    "TV_CH_UP": { type: "standard", protocol: "SAMSUNG", code: "0xE0E048B7", bits: 32 },
    "TV_CH_DOWN": { type: "standard", protocol: "SAMSUNG", code: "0xE0E008F7", bits: 32 },
    "TV_UP": { type: "standard", protocol: "SAMSUNG", code: "0xE0E006F9", bits: 32 },
    "TV_DOWN": { type: "standard", protocol: "SAMSUNG", code: "0xE0E08679", bits: 32 },
    "TV_LEFT": { type: "standard", protocol: "SAMSUNG", code: "0xE0E0A659", bits: 32 },
    "TV_RIGHT": { type: "standard", protocol: "SAMSUNG", code: "0xE0E046B9", bits: 32 },
    "TV_OK": { type: "standard", protocol: "SAMSUNG", code: "0xE0E016E9", bits: 32 },
    "TV_HOME": { type: "standard", protocol: "SAMSUNG", code: "0xE0E0CB34", bits: 32 },
    "TV_BACK": { type: "standard", protocol: "SAMSUNG", code: "0xE0E01AE5", bits: 32 }
  },
  "LG TV": {
    "TV_POWER": { type: "standard", protocol: "NEC", code: "0x20DF10EF", bits: 32 },
    "TV_MUTE":  { type: "standard", protocol: "NEC", code: "0x20DF906F", bits: 32 },
    "TV_INPUT": { type: "standard", protocol: "NEC", code: "0x20DFD02F", bits: 32 },
    "TV_VOL_UP": { type: "standard", protocol: "NEC", code: "0x20DF40BF", bits: 32 },
    "TV_VOL_DOWN": { type: "standard", protocol: "NEC", code: "0x20DFC03F", bits: 32 },
    "TV_CH_UP": { type: "standard", protocol: "NEC", code: "0x20DF00FF", bits: 32 },
    "TV_CH_DOWN": { type: "standard", protocol: "NEC", code: "0x20DF807F", bits: 32 },
    "TV_UP": { type: "standard", protocol: "NEC", code: "0x20DF02FD", bits: 32 },
    "TV_DOWN": { type: "standard", protocol: "NEC", code: "0x20DF827D", bits: 32 },
    "TV_LEFT": { type: "standard", protocol: "NEC", code: "0x20DFE01F", bits: 32 },
    "TV_RIGHT": { type: "standard", protocol: "NEC", code: "0x20DF609F", bits: 32 },
    "TV_OK": { type: "standard", protocol: "NEC", code: "0x20DF22DD", bits: 32 },
    "TV_HOME": { type: "standard", protocol: "NEC", code: "0x20DF3EC1", bits: 32 },
    "TV_BACK": { type: "standard", protocol: "NEC", code: "0x20DF14EB", bits: 32 }
  },
  "Sony TV": {
    "TV_POWER": { type: "standard", protocol: "SONY", code: "0xA90", bits: 12 },
    "TV_VOL_UP": { type: "standard", protocol: "SONY", code: "0x490", bits: 12 },
    "TV_VOL_DOWN": { type: "standard", protocol: "SONY", code: "0xC90", bits: 12 },
    "TV_MUTE": { type: "standard", protocol: "SONY", code: "0x290", bits: 12 },
    "TV_CH_UP": { type: "standard", protocol: "SONY", code: "0x090", bits: 12 },
    "TV_CH_DOWN": { type: "standard", protocol: "SONY", code: "0x890", bits: 12 }
  }
  // Daikin, Panasonic ACs rely heavily on RAW sequence blocks of ~400 pulses because they are stateful 
  // users can clone these easily via the UI or map them if their lengths were standard.
};
