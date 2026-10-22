#pragma once
// Extras: remember relay state, auto timers, wall switches, activity events, OTA update.
#include <Update.h>

#define MAX_TMR 8
#define MAX_EV 20

const uint8_t SW_PINS[] = {32, 33, 25, 26, 4, 5, 13, 14};
const int SWN = sizeof(SW_PINS);

struct Tmr { bool active; uint8_t ch; bool on; uint32_t endMs; };
struct Ev { uint32_t t; uint8_t ch; bool on; uint8_t src; };

Tmr tmrs[MAX_TMR];
Ev evs[MAX_EV];
int evCount = 0;
bool evDirty = false;
uint32_t evDirtyAt = 0;
int unsyncedNew = 0;
String bootMode = "last";
uint8_t swMode = 0;
uint32_t savedMask = 0, lastMask = 0, maskSince = 0;
bool swLast[NMAX], swStable[NMAX];
uint32_t swChange[NMAX];
uint8_t otaState = 0;
uint32_t otaRebootAt = 0;

void logEv(int i, bool on) {
  time_t now = time(nullptr);
  if (evCount < MAX_EV) evCount++;
  for (int k = evCount - 1; k > 0; k--) evs[k] = evs[k - 1];
  evs[0] = {(uint32_t)(now > 1700000000 ? now : 0), (uint8_t)(i + 1), on, srcNow};
  evDirty = true;
  evDirtyAt = millis();
  if (now <= 1700000000 && unsyncedNew < MAX_EV) unsyncedNew++;
}

void evSave() {
  String s;
  for (int i = 0; i < evCount; i++) {
    s += String(evs[i].t) + "," + String(evs[i].ch) + "," + String(evs[i].on ? 1 : 0) + "," + String(evs[i].src) + ";";
  }
  prefs.putString("evlog", s);
}

void evLoad() {
  String s = prefs.getString("evlog", "");
  evCount = 0;
  int pos = 0;
  while (pos < (int)s.length() && evCount < MAX_EV) {
    int end = s.indexOf(';', pos);
    if (end < 0) end = s.length();
    unsigned long t = 0;
    int c = 0, o = 0, r = 0;
    if (sscanf(s.substring(pos, end).c_str(), "%lu,%d,%d,%d", &t, &c, &o, &r) == 4) {
      evs[evCount++] = {(uint32_t)t, (uint8_t)c, o == 1, (uint8_t)r};
    }
    pos = end + 1;
  }
}

void evTimeFix() {
  if (unsyncedNew == 0) return;
  time_t now = time(nullptr);
  if (now < 1700000000) return;
  int left = unsyncedNew;
  for (int i = 0; i < evCount && left > 0; i++) {
    if (evs[i].t == 0) { evs[i].t = (uint32_t)now; left--; }
  }
  unsyncedNew = 0;
  evDirty = true;
  evDirtyAt = millis();
}

void evSaveTick() {
  if (evDirty && millis() - evDirtyAt > 2000) {
    evSave();
    evDirty = false;
  }
}

uint32_t maskNow() {
  uint32_t m = 0;
  for (int i = 0; i < N; i++) if (st[i]) m |= (1UL << i);
  return m;
}

void bootRestore() {
  evLoad();
  bootMode = prefs.getString("bootmode", "last");
  swMode = prefs.getUChar("swmode", 0);
  savedMask = prefs.getUInt("mask", 0);
  if (bootMode == "last") {
    srcNow = 0;
    for (int i = 0; i < N; i++) if (savedMask & (1UL << i)) apply(i, true);
    srcNow = 1;
  }
  lastMask = maskNow();
  maskSince = millis();
}

void saveMaskTick() {
  uint32_t m = maskNow();
  if (m != lastMask) { lastMask = m; maskSince = millis(); }
  if (m != savedMask && millis() - maskSince > 2000) { savedMask = m; prefs.putUInt("mask", m); }
}

bool tmrSet(int ch, uint32_t sec, bool on) {
  for (int i = 0; i < MAX_TMR; i++) if (tmrs[i].active && tmrs[i].ch == ch) tmrs[i].active = false;
  if (sec == 0) return true;
  for (int i = 0; i < MAX_TMR; i++) {
    if (!tmrs[i].active) { tmrs[i] = {true, (uint8_t)ch, on, (uint32_t)(millis() + sec * 1000UL)}; return true; }
  }
  return false;
}

void tmrTick() {
  for (int i = 0; i < MAX_TMR; i++) {
    if (tmrs[i].active && (int32_t)(millis() - tmrs[i].endMs) >= 0) {
      tmrs[i].active = false;
      srcNow = 3;
      apply(tmrs[i].ch - 1, tmrs[i].on);
      srcNow = 1;
    }
  }
}

String tmrText() {
  String s;
  for (int i = 0; i < MAX_TMR; i++) {
    if (!tmrs[i].active) continue;
    long left = (int32_t)(tmrs[i].endMs - millis()) / 1000;
    if (left < 0) left = 0;
    s += String(tmrs[i].ch) + "," + String(tmrs[i].on ? 1 : 0) + "," + String(left) + ";";
  }
  return s;
}

void featuresEarly() {
  for (int i = 0; i < N && i < SWN; i++) {
    pinMode(SW_PINS[i], INPUT_PULLUP);
    swStable[i] = digitalRead(SW_PINS[i]);
    swLast[i] = swStable[i];
    swChange[i] = millis();
  }
}

void swTick() {
  for (int i = 0; i < N && i < SWN; i++) {
    bool v = digitalRead(SW_PINS[i]);
    if (v != swLast[i]) { swLast[i] = v; swChange[i] = millis(); }
    if (v != swStable[i] && millis() - swChange[i] > 50) {
      swStable[i] = v;
      if (swMode == 2 || (swMode == 1 && v == LOW)) { srcNow = 4; apply(i, !st[i]); srcNow = 1; }
    }
  }
}

String cfgJson() {
  return String("{\"boot\":\"") + bootMode + "\",\"sw\":" + String(swMode) + ",\"swpins\":" + String(SWN) + "}";
}

void otaUpload() {
  HTTPUpload& up = server.upload();
  if (up.status == UPLOAD_FILE_START) {
    if (server.header("Authorization") != "Bearer " + token) { otaState = 1; return; }
    otaState = Update.begin(UPDATE_SIZE_UNKNOWN) ? 2 : 3;
  } else if (up.status == UPLOAD_FILE_WRITE) {
    if (otaState != 2) return;
    if (Update.write(up.buf, up.currentSize) != up.currentSize) { Update.abort(); otaState = 3; }
  } else if (up.status == UPLOAD_FILE_END) {
    if (otaState == 2) otaState = Update.end(true) ? 4 : 3;
  } else if (up.status == UPLOAD_FILE_ABORTED) {
    if (otaState == 2) Update.abort();
    otaState = 3;
  }
}

void otaDone() {
  if (otaState == 1) { server.send(401, "application/json", "{\"error\":\"unauthorized\"}"); otaState = 0; return; }
  bool ok = (otaState == 4);
  server.send(ok ? 200 : 500, "application/json", ok ? "{\"ok\":true}" : "{\"ok\":false}");
  if (ok) otaRebootAt = millis() + 1500;
  otaState = 0;
}

void featuresSetup() {
  server.on("/api/config", HTTP_GET, []() {
    if (authed()) server.send(200, "application/json", cfgJson());
  });
  server.on("/api/config", HTTP_POST, []() {
    if (!authed()) return;
    if (server.hasArg("boot") && (server.arg("boot") == "last" || server.arg("boot") == "off")) {
      bootMode = server.arg("boot");
      prefs.putString("bootmode", bootMode);
    }
    if (server.hasArg("sw")) {
      int v = server.arg("sw").toInt();
      if (v >= 0 && v <= 2) { swMode = v; prefs.putUChar("swmode", swMode); }
    }
    server.send(200, "application/json", cfgJson());
  });
  server.on("/api/timers", HTTP_GET, []() {
    if (authed()) server.send(200, "text/plain", tmrText());
  });
  server.on("/api/timer", HTTP_POST, []() {
    if (!authed()) return;
    int ch = server.arg("ch").toInt();
    long sec = server.arg("sec").toInt();
    if (ch < 1 || ch > N || sec < 0 || sec > 86400 || !server.hasArg("on")) {
      server.send(400, "application/json", "{\"error\":\"bad request\"}");
      return;
    }
    bool ok = tmrSet(ch, (uint32_t)sec, server.arg("on") == "1");
    server.send(ok ? 200 : 409, "text/plain", tmrText());
  });
  server.on("/api/events", HTTP_GET, []() {
    if (!authed()) return;
    String s;
    for (int i = 0; i < evCount; i++) {
      s += String(evs[i].t) + "," + String(evs[i].ch) + "," + String(evs[i].on ? 1 : 0) + "," + String(evs[i].src) + ";";
    }
    server.send(200, "text/plain", s);
  });
  server.on("/api/ota", HTTP_POST, otaDone, otaUpload);
}

void featuresTick() {
  tmrTick();
  swTick();
  saveMaskTick();
  evTimeFix();
  evSaveTick();
  if (otaRebootAt && millis() > otaRebootAt) ESP.restart();
}
