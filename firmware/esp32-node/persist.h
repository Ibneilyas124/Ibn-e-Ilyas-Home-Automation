#pragma once
// Power-cut recovery: timers survive a reboot, missed schedules catch up.
bool pRestored = false;
uint32_t pLastSave = 0, pLastChk = 0;
String pSig = "x";

String pSigNow() {
  String s;
  for (int i = 0; i < MAX_TMR; i++) {
    if (tmrs[i].active) s += String(tmrs[i].ch) + "," + String(tmrs[i].on ? 1 : 0) + "," + String(tmrs[i].endMs) + ";";
  }
  return s;
}

void pSaveTimers(time_t now) {
  String s;
  for (int i = 0; i < MAX_TMR; i++) {
    if (!tmrs[i].active) continue;
    long left = (int32_t)(tmrs[i].endMs - millis()) / 1000;
    if (left < 0) left = 0;
    s += String(tmrs[i].ch) + "," + String(tmrs[i].on ? 1 : 0) + "," + String((unsigned long)now + left) + ";";
  }
  prefs.putString("tmrs", s);
}

void pRestoreTimers(time_t now) {
  String s = prefs.getString("tmrs", "");
  int pos = 0;
  while (pos < (int)s.length()) {
    int end = s.indexOf(';', pos);
    if (end < 0) end = s.length();
    int c = 0, o = 0;
    unsigned long e = 0;
    if (sscanf(s.substring(pos, end).c_str(), "%d,%d,%lu", &c, &o, &e) == 3 && c >= 1 && c <= N) {
      if ((time_t)e > now) {
        tmrSet(c, (uint32_t)((time_t)e - now), o == 1);
      } else {
        srcNow = 3;
        apply(c - 1, o == 1);
        srcNow = 1;
      }
    }
    pos = end + 1;
  }
}

void pCatchUpSched(time_t now, time_t since) {
  struct tm t;
  localtime_r(&now, &t);
  time_t mid = now - (t.tm_hour * 3600L + t.tm_min * 60L + t.tm_sec);
  time_t best[NMAX + 1];
  int act[NMAX + 1];
  for (int i = 0; i <= NMAX; i++) { best[i] = 0; act[i] = 0; }
  for (int k = 0; k <= 7; k++) {
    time_t day = mid - (time_t)k * 86400L;
    int wd = ((t.tm_wday - k) % 7 + 7) % 7;
    for (int j = 0; j < schedCount; j++) {
      Sched& s = scheds[j];
      if (s.ch < 1 || s.ch > NMAX || !(s.days & (1 << wd))) continue;
      time_t cand = day + s.h * 3600L + s.m * 60L;
      if (cand > since && cand <= now && cand >= best[s.ch]) { best[s.ch] = cand; act[s.ch] = s.on ? 1 : 0; }
    }
  }
  for (int ch = 1; ch <= N; ch++) {
    if (best[ch]) { srcNow = 2; apply(ch - 1, act[ch] == 1); srcNow = 1; }
  }
}

void persistTick() {
  if (millis() - pLastChk < 1000) return;
  pLastChk = millis();
  time_t now = time(nullptr);
  if (now < 1700000000) return;
  if (!pRestored) {
    pRestored = true;
    uint32_t last = prefs.getUInt("lastseen", 0);
    if (last > 1700000000UL && (uint32_t)now > last) pCatchUpSched(now, (time_t)last);
    pRestoreTimers(now);
    prefs.putUInt("lastseen", (uint32_t)now);
    pLastSave = millis();
    pSig = "x";
  }
  if (millis() - pLastSave > 900000UL) {
    prefs.putUInt("lastseen", (uint32_t)now);
    pLastSave = millis();
  }
  String sig = pSigNow();
  if (sig != pSig) { pSaveTimers(now); pSig = sig; }
}
