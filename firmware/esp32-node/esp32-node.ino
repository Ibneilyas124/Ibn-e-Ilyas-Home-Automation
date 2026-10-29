#include <WiFi.h>
#include <WebServer.h>
#include <uri/UriBraces.h>
#include <ESPmDNS.h>
#include <DNSServer.h>
#include <Preferences.h>

#define FW_VERSION "0.10.11"
#define RELAY_ACTIVE_LOW true

const uint8_t PINS[] = {16, 17, 18, 19, 21, 22, 23, 27};
const int NMAX = sizeof(PINS);
int N = 4;
bool st[NMAX];
WebServer server(80);
DNSServer dns;
Preferences prefs;
String token, deviceId;
uint32_t restartAt = 0;
uint8_t srcNow = 1;
void logEv(int i, bool on);
void featuresTick();
void featuresEarly();

void apply(int i, bool on) {
  if (st[i] != on) logEv(i, on);
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

#include <esp_system.h>
// ---- DIAG (fw 0.10.11) ----
static int diagRst = -1;
static int diagDisc = 0;
static int diagDiscCount = 0;
static void diagOnWifi(WiFiEvent_t ev, WiFiEventInfo_t info) {
  if (ev == ARDUINO_EVENT_WIFI_STA_DISCONNECTED) {
    diagDisc = info.wifi_sta_disconnected.reason;
    diagDiscCount++;
  }
}
static const char* diagRstName(int r) {
  switch (r) {
    case 1: return "poweron";
    case 2: return "ext";
    case 3: return "sw";
    case 4: return "panic";
    case 5: return "int_wdt";
    case 6: return "task_wdt";
    case 7: return "wdt";
    case 8: return "deepsleep";
    case 9: return "brownout";
    default: return "other";
  }
}
static void diagSaveFallback() {
  prefs.putUInt("apn", prefs.getUInt("apn", 0) + 1);
  if (diagRst != 3) prefs.putInt("lrr", diagRst);
  prefs.putInt("ldr", diagDisc);
  prefs.putInt("lst", (int)WiFi.status());
}
static String diagJson() {
  String s = "{\"reset\":\"" + String(diagRstName(diagRst)) + "\"";
  s += ",\"discNow\":" + String(diagDisc);
  s += ",\"discCount\":" + String(diagDiscCount);
  s += ",\"upSec\":" + String(millis() / 1000);
  s += ",\"rssi\":" + String(WiFi.RSSI());
  s += ",\"heap\":" + String(ESP.getFreeHeap());
  s += ",\"apFalls\":" + String(prefs.getUInt("apn", 0));
  int lr = prefs.getInt("lrr", -1);
  s += ",\"lastFallReset\":\"" + String(lr < 0 ? "none" : diagRstName(lr)) + "\"";
  s += ",\"lastFallDisc\":" + String(prefs.getInt("ldr", -1));
  s += ",\"lastFallStatus\":" + String(prefs.getInt("lst", -1));
  return s + "}";
}
static void diagInit() {
  static bool done = false;
  if (done) return;
  done = true;
  diagRst = (int)esp_reset_reason();
  WiFi.onEvent(diagOnWifi);
}

void runPortal() {
  bool saved = prefs.getString("ssid", "").length() > 0;
  if (saved) diagSaveFallback();
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
    featuresTick();
    if (restartAt && millis() > restartAt) ESP.restart();
    if (saved && millis() - t0 > 60000 && WiFi.softAPgetStationNum() == 0) ESP.restart();
  }
}

void connectWifi() {
  diagInit();
  String ssid = prefs.getString("ssid", "");
  if (ssid.length() > 0) {
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid.c_str(), prefs.getString("pass", "").c_str());
    for (int i = 0; i < 180 && WiFi.status() != WL_CONNECTED; i++) {
      for (int k = 0; k < 10; k++) { featuresTick(); delay(50); }
    }
    if (WiFi.status() == WL_CONNECTED) return;
  }
  runPortal();
}

#include "schedules.h"
#include "ws.h"
#include "features.h"
#include "lock.h"
#include "seats.h"
#include "mqtt.h"

#include "persist.h"

void setup() {
  Serial.begin(115200);
  prefs.begin("ibn", false);
  N = constrain((int)prefs.getUChar("nch", 4), 1, NMAX);
  for (int i = 0; i < N; i++) {
    pinMode(PINS[i], OUTPUT);
    apply(i, false);
  }
  bootRestore();
  featuresEarly();
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
  featuresSetup();
  lockSetup();
  seatSetup();
  mqSetup();
  wsSetup();
  server.on("/api/channels", HTTP_POST, []() {
    if (!authed()) return;
    int n = server.arg("n").toInt();
    if (n < 1 || n > NMAX) {
      server.send(400, "application/json", "{\"error\":\"n 1 se 8\"}");
      return;
    }
    prefs.putUChar("nch", n);
    server.send(200, "application/json", "{\"channels\":" + String(n) + ",\"restart\":true}");
    delay(500);
    ESP.restart();
  });
  server.on("/api/info", HTTP_GET, []() {
    server.send(200, "application/json", "{\"id\":\"" + deviceId +
      "\",\"firmware\":\"" FW_VERSION "\",\"channels\":" + String(N) + "}");
  });
  server.on("/api/diag", HTTP_GET, []() {
    if (authed()) server.send(200, "application/json", diagJson());
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
  featuresTick();
  persistTick();
  mqTick();
  static uint32_t lastTry = 0;
  if (WiFi.status() != WL_CONNECTED) {
    if (millis() - lastTry > 5000) { lastTry = millis(); WiFi.reconnect(); }
  }
}
