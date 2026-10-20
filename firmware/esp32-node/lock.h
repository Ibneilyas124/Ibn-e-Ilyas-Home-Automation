#pragma once
// Proof that this controller is genuine: an owner-signed certificate for its ID, kept in flash.
String certB64;

void lockSetup() {
  certB64 = prefs.getString("cert", "");
  server.on("/api/cert", HTTP_GET, []() {
    server.send(200, "application/json", "{\"cert\":\"" + certB64 + "\"}");
  });
  server.on("/api/provision", HTTP_POST, []() {
    if (!authed()) return;
    String c = server.arg("cert");
    if (c.length() < 80 || c.length() > 120) {
      server.send(400, "application/json", "{\"error\":\"bad request\"}");
      return;
    }
    certB64 = c;
    prefs.putString("cert", certB64);
    server.send(200, "application/json", "{\"ok\":true}");
  });
}
