#pragma once
// Schedules that run ON the ESP32. They keep working when the phone is off.
#define TZ_SECONDS 18000
#define MAX_SCHED 20

struct Sched { uint8_t h, m, days, ch; bool on; };
Sched scheds[MAX_SCHED];
int schedCount = 0;
bool schedDirty = true;
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

// Sunrise / sunset schedules (separate list, runs on the ESP32).
#define MAX_SUN 8
struct SunS { uint8_t ev; int16_t off; uint8_t days, ch; bool on; };
SunS suns[MAX_SUN];
int sunCount = 0;
float sunLat = 26.84f, sunLon = 68.12f;
long lastSunKey = -1;
uint32_t lastSunCheck = 0;

double dsin(double d) { return sin(d * PI / 180.0); }
double dcos(double d) { return cos(d * PI / 180.0); }
double dtan(double d) { return tan(d * PI / 180.0); }
double nrm(double x, double m) { while (x < 0) x += m; while (x >= m) x -= m; return x; }

// Minutes after local midnight for sunrise (rise=true) or sunset, or -1 if there is none that day.
int sunMin(int yd, bool rise) {
  double lngH = sunLon / 15.0;
  double tt = yd + ((rise ? 6.0 : 18.0) - lngH) / 24.0;
  double M = 0.9856 * tt - 3.289;
  double L = nrm(M + 1.916 * dsin(M) + 0.020 * dsin(2 * M) + 282.634, 360.0);
  double RA = nrm(atan(0.91764 * dtan(L)) * 180.0 / PI, 360.0);
  RA += floor(L / 90.0) * 90.0 - floor(RA / 90.0) * 90.0;
  RA /= 15.0;
  double sd = 0.39782 * dsin(L);
  double cd = cos(asin(sd));
  double cH = (dcos(90.833) - sd * dsin(sunLat)) / (cd * dcos(sunLat));
  if (cH > 1.0 || cH < -1.0) return -1;
  double H = acos(cH) * 180.0 / PI;
  if (rise) H = 360.0 - H;
  H /= 15.0;
  double T = H + RA - 0.06571 * tt - 6.622;
  double ut = nrm(T - lngH, 24.0);
  double loc = nrm(ut + TZ_SECONDS / 3600.0, 24.0);
  return (int)(loc * 60.0 + 0.5);
}

void sunParse(const String& s) {
  sunCount = 0;
  int pos = 0;
  while (pos < (int)s.length() && sunCount < MAX_SUN) {
    int end = s.indexOf(';', pos);
    if (end < 0) end = s.length();
    int e, o, d, c, v;
    if (sscanf(s.substring(pos, end).c_str(), "%d,%d,%d,%d,%d", &e, &o, &d, &c, &v) == 5 &&
        (e == 0 || e == 1) && o >= -720 && o <= 720 && c >= 1 && c <= N) {
      suns[sunCount++] = {(uint8_t)e, (int16_t)o, (uint8_t)(d & 127), (uint8_t)c, v == 1};
    }
    pos = end + 1;
  }
}

String sunText() {
  String s;
  for (int i = 0; i < sunCount; i++) {
    SunS& x = suns[i];
    s += String(x.ev) + "," + String(x.off) + "," + String(x.days) + "," + String(x.ch) + "," + String(x.on ? 1 : 0) + ";";
  }
  return s;
}

void sunTick() {
  if (millis() - lastSunCheck < 1000) return;
  lastSunCheck = millis();
  time_t now = time(nullptr);
  if (now < 1700000000) return;
  struct tm tm2;
  localtime_r(&now, &tm2);
  long key = (long)tm2.tm_yday * 1440 + tm2.tm_hour * 60 + tm2.tm_min;
  if (key == lastSunKey) return;
  lastSunKey = key;
  int cur = tm2.tm_hour * 60 + tm2.tm_min;
  for (int i = 0; i < sunCount; i++) {
    SunS& x = suns[i];
    if (!(x.days & (1 << tm2.tm_wday))) continue;
    int base = sunMin(tm2.tm_yday + 1, x.ev == 0);
    if (base < 0) continue;
    int tgt = base + x.off;
    if (tgt < 0 || tgt >= 1440) continue;
    if (tgt == cur) { srcNow = 2; apply(x.ch - 1, x.on); srcNow = 1; }
  }
}

void sunSetup() {
  sunLat = prefs.getFloat("slat", 26.84f);
  sunLon = prefs.getFloat("slon", 68.12f);
  sunParse(prefs.getString("sunsched", ""));
  server.on("/api/sunsched", HTTP_GET, []() {
    if (authed()) server.send(200, "text/plain", sunText());
  });
  server.on("/api/sunsched", HTTP_POST, []() {
    if (!authed()) return;
    if (!server.hasArg("data")) {
      server.send(400, "application/json", "{\"error\":\"bad request\"}");
      return;
    }
    sunParse(server.arg("data"));
    prefs.putString("sunsched", sunText());
    lastSunKey = -1;
    server.send(200, "text/plain", sunText());
  });
  server.on("/api/sun", HTTP_GET, []() {
    if (!authed()) return;
    time_t now = time(nullptr);
    int r = -1, st2 = -1;
    if (now > 1700000000) {
      struct tm tm3;
      localtime_r(&now, &tm3);
      r = sunMin(tm3.tm_yday + 1, true);
      st2 = sunMin(tm3.tm_yday + 1, false);
    }
    char b[96];
    snprintf(b, sizeof(b), "{\"lat\":%.3f,\"lon\":%.3f,\"rise\":%d,\"set\":%d}", sunLat, sunLon, r, st2);
    server.send(200, "application/json", b);
  });
  server.on("/api/sun", HTTP_POST, []() {
    if (!authed()) return;
    if (server.hasArg("lat") && server.hasArg("lon")) {
      float la = server.arg("lat").toFloat();
      float lo = server.arg("lon").toFloat();
      if (la > -66.0f && la < 66.0f && lo > -180.0f && lo < 180.0f) {
        sunLat = la; sunLon = lo;
        prefs.putFloat("slat", sunLat);
        prefs.putFloat("slon", sunLon);
        lastSunKey = -1;
      }
    }
    server.send(200, "application/json", "{\"ok\":true}");
  });
}

void schedTick() {
  sunTick();
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
    if (s.h == t.tm_hour && s.m == t.tm_min && (s.days & (1 << t.tm_wday))) { srcNow = 2; apply(s.ch - 1, s.on); srcNow = 1; }
  }
}

void schedSetup() {
  configTime(TZ_SECONDS, 0, "pool.ntp.org", "time.google.com");
  schedParse(prefs.getString("sched", ""));
  sunSetup();
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
    prefs.putString("sched", schedText()); schedDirty = true;
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
