#pragma once
// Phone seats: the household key says how many phones may use this controller. They are counted here.
String seatList;
uint8_t seatMax = 0;

int seatUsed() {
  int n = 0;
  for (unsigned i = 0; i < seatList.length(); i++) if (seatList[i] == ',') n++;
  return n;
}

String seatJson() {
  return String("{\"used\":") + String(seatUsed()) + ",\"max\":" + String(seatMax) + "}";
}

bool seatIdOk(const String& p) {
  if (p.length() != 14) return false;
  for (unsigned i = 0; i < p.length(); i++) {
    char c = p[i];
    if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || c == '-')) return false;
  }
  return true;
}

void seatSetup() {
  seatList = prefs.getString("seats", "");
  seatMax = prefs.getUChar("seatmax", 0);
  server.on("/api/seats", HTTP_GET, []() {
    if (authed()) server.send(200, "application/json", seatJson());
  });
  server.on("/api/seat", HTTP_POST, []() {
    if (!authed()) return;
    String p = server.arg("phone");
    int mx = server.arg("max").toInt();
    if (!seatIdOk(p) || mx < 1 || mx > 50) {
      server.send(400, "application/json", "{\"error\":\"bad request\"}");
      return;
    }
    if (mx > seatMax) { seatMax = mx; prefs.putUChar("seatmax", seatMax); }
    String tag = p + ",";
    if (seatList.indexOf(tag) < 0) {
      if (seatUsed() >= seatMax) {
        server.send(403, "application/json", "{\"error\":\"full\"}");
        return;
      }
      seatList += tag;
      prefs.putString("seats", seatList);
    }
    server.send(200, "application/json", seatJson());
  });
}
