#pragma once
// Schedules that run ON the ESP32. They keep working when the phone is off.
#define TZ_SECONDS 18000
#define MAX_SCHED 20

struct Sched { uint8_t h, m, days, ch; bool on; };
Sched scheds[MAX_SCHED];
int schedCount = 0;
long lastKey = -1;
uint32_t lastCheck = 0;

void schedParse(const String& s) {
  schedCount = 0;
  int pos = 0;
  while (pos < (int)s.length() && schedCount < MAX_SCHED) {
    int end = s.indexOf(';', pos);
    if (end < 0) end = s.length();
    int h, m, d, c, o;
    if (sscanf(s.substring(pos, end).c_str(), "%d,%d,%d,%d,%d", &h, &m, &d, &c, &o) == 5 &&
        h >= 0 && h < 24 && m >= 0 && m < 60 && c >= 1 && c <= N) {
      scheds[schedCount++] = {(uint8_t)h, (uint8_t)m, (uint8_t)(d & 127), (uint8_t)c, o == 1};
    }
    pos = end + 1;
  }
}

String schedText() {
  String s;
  for (int i = 0; i < schedCount; i++) {
    Sched& x = scheds[i];
    s += String(x.h) + "," + String(x.m) + "," + String(x.days) + "," + String(x.ch) + "," + String(x.on ? 1 : 0) + ";";
  }
  return s;
}

void schedTick() {
  if (millis() - lastCheck < 1000) return;
  lastCheck = millis();
  time_t now = time(nullptr);
  if (now < 1700000000) return;
  struct tm t;
  localtime_r(&now, &t);
  long key = (long)t.tm_yday * 1440 + t.tm_hour * 60 + t.tm_min;
  if (key == lastKey) return;
  lastKey = key;
  for (int i = 0; i < schedCount; i++) {
    Sched& s = scheds[i];
    if (s.h == t.tm_hour && s.m == t.tm_min && (s.days & (1 << t.tm_wday))) apply(s.ch - 1, s.on);
  }
}

void schedSetup() {
  configTime(TZ_SECONDS, 0, "pool.ntp.org", "time.google.com");
  schedParse(prefs.getString("sched", ""));
  server.on("/api/schedules", HTTP_GET, []() {
    if (authed()) server.send(200, "text/plain", schedText());
  });
  server.on("/api/schedules", HTTP_POST, []() {
    if (!authed()) return;
    if (!server.hasArg("data")) {
      server.send(400, "application/json", "{\"error\":\"bad request\"}");
      return;
    }
    schedParse(server.arg("data"));
    prefs.putString("sched", schedText());
    server.send(200, "text/plain", schedText());
  });
  server.on("/api/time", HTTP_GET, []() {
    if (!authed()) return;
    time_t now = time(nullptr);
    bool ok = now > 1700000000;
    struct tm t;
    localtime_r(&now, &t);
    char b[64];
    snprintf(b, sizeof(b), "{\"synced\":%s,\"time\":\"%02d:%02d\"}", ok ? "true" : "false", t.tm_hour, t.tm_min);
    server.send(200, "application/json", b);
  });
}
