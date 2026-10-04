#include <WiFi.h>
#include <WebServer.h>
#include <uri/UriBraces.h>
#include <ESPmDNS.h>
#include <Preferences.h>
#include "secrets.h"

#define DEVICE_ID "ESP32-SARFRAZ-ROOM"
#define FW_VERSION "0.1.0"
#define RELAY_ACTIVE_LOW true

const uint8_t PINS[] = {16, 17, 18, 19};
const int N = sizeof(PINS);
bool st[N];
WebServer server(80);
Preferences prefs;
String token;

void apply(int i, bool on) {
  st[i] = on;
  digitalWrite(PINS[i], (on != RELAY_ACTIVE_LOW) ? HIGH : LOW);
}

bool authed() {
  if (server.header("Authorization") == "Bearer " + token) return true;
  server.send(401, "application/json", "{\"error\":\"unauthorized\"}");
  return false;
}

String stateJson() {
  String s = "{\"id\":\"" DEVICE_ID "\",\"channels\":[";
  for (int i = 0; i < N; i++) {
    if (i) s += ",";
    s += st[i] ? "true" : "false";
  }
  return s + "]}";
}

void setup() {
  Serial.begin(115200);
  for (int i = 0; i < N; i++) {
    pinMode(PINS[i], OUTPUT);
    apply(i, false);
  }
  prefs.begin("ibn", false);
  token = prefs.getString("token", "");
  if (token.length() == 0) {
    for (int i = 0; i < 4; i++) token += String(esp_random(), HEX);
    prefs.putString("token", token);
  }
  Serial.println("TOKEN: " + token);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) delay(300);
  Serial.println("IP: " + WiFi.localIP().toString());
  MDNS.begin(DEVICE_ID);
  MDNS.addService("ibnhome", "tcp", 80);
  const char* keys[] = {"Authorization"};
  server.collectHeaders(keys, 1);
  server.on("/api/info", HTTP_GET, []() {
    server.send(200, "application/json",
      "{\"id\":\"" DEVICE_ID "\",\"firmware\":\"" FW_VERSION "\",\"channels\":" + String(N) + "}");
  });
  server.on("/api/state", HTTP_GET, []() {
    if (authed()) server.send(200, "application/json", stateJson());
  });
  server.on(UriBraces("/api/channel/{}"), HTTP_POST, []() {
    if (!authed()) return;
    int n = server.pathArg(0).toInt();
    if (n < 1 || n > N || !server.hasArg("on")) {
      server.send(400, "application/json", "{\"error\":\"bad request\"}");
      return;
    }
    apply(n - 1, server.arg("on") == "1");
    server.send(200, "application/json", stateJson());
  });
  server.begin();
}

void loop() {
  server.handleClient();
  if (WiFi.status() != WL_CONNECTED) { WiFi.reconnect(); delay(3000); }
}
