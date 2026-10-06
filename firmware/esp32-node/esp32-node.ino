#include <WiFi.h>
#include <WebServer.h>
#include <uri/UriBraces.h>
#include <ESPmDNS.h>
#include <DNSServer.h>
#include <Preferences.h>

#define FW_VERSION "0.4.0"
#define RELAY_ACTIVE_LOW true

const uint8_t PINS[] = {16, 17, 18, 19};
const int N = sizeof(PINS);
bool st[N];
WebServer server(80);
DNSServer dns;
Preferences prefs;
String token, deviceId;
uint32_t restartAt = 0;

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
  String s = "{\"id\":\"" + deviceId + "\",\"channels\":[";
  for (int i = 0; i < N; i++) {
    if (i) s += ",";
    s += st[i] ? "true" : "false";
  }
  return s + "]}";
}

void runPortal() {
  bool saved = prefs.getString("ssid", "").length() > 0;
  WiFi.mode(WIFI_AP);
  WiFi.softAP(("IbnEIlyas-" + deviceId.substring(6)).c_str());
  dns.start(53, "*", WiFi.softAPIP());
  server.on("/save", HTTP_POST, []() {
    prefs.putString("ssid", server.arg("s"));
    prefs.putString("pass", server.arg("p"));
    restartAt = millis() + 30000;
    server.send(200, "text/html", "<meta name=viewport content='width=device-width'>"
      "<h3>Saved</h3>Device: " + deviceId + "<br>Token:<br><b>" + token + "</b>"
      "<br><br>Copy this token. Restarting in 30s.");
  });
  auto page = []() {
    server.send(200, "text/html",
      "<meta name=viewport content='width=device-width'><h2>Ibn e Ilyas Home Setup</h2>"
      "<form method=post action=/save>Wi-Fi name<br><input name=s><br>Password<br>"
      "<input name=p type=password><br><br><button>Save</button></form>");
  };
  server.onNotFound(page);
  server.on("/", page);
  server.begin();
  uint32_t t0 = millis();
  while (true) {
    dns.processNextRequest();
    server.handleClient();
    if (restartAt && millis() > restartAt) ESP.restart();
    if (saved && millis() - t0 > 180000) ESP.restart();
  }
}

void connectWifi() {
  String ssid = prefs.getString("ssid", "");
  if (ssid.length() > 0) {
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid.c_str(), prefs.getString("pass", "").c_str());
    for (int i = 0; i < 60 && WiFi.status() != WL_CONNECTED; i++) delay(500);
    if (WiFi.status() == WL_CONNECTED) return;
  }
  runPortal();
}

#include "schedules.h"
#include "ws.h"

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
  char buf[16];
  snprintf(buf, sizeof(buf), "ESP32-%06X", (uint32_t)(ESP.getEfuseMac() >> 24));
  deviceId = buf;
  Serial.println("ID: " + deviceId + " TOKEN: " + token);
  connectWifi();
  Serial.println("IP: " + WiFi.localIP().toString());
  MDNS.begin(deviceId.c_str());
  MDNS.addService("ibnhome", "tcp", 80);
  const char* keys[] = {"Authorization"};
  server.collectHeaders(keys, 1);
  schedSetup();
  wsSetup();
  server.on("/api/info", HTTP_GET, []() {
    server.send(200, "application/json", "{\"id\":\"" + deviceId +
      "\",\"firmware\":\"" FW_VERSION "\",\"channels\":" + String(N) + "}");
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
  schedTick();
  wsLoop();
  if (WiFi.status() != WL_CONNECTED) { WiFi.reconnect(); delay(3000); }
}
