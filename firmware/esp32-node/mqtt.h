#pragma once
// Remote control over MQTT (cloud broker). Does nothing until a broker host is saved.
#include <PubSubClient.h>
#include <WiFiClientSecure.h>

WiFiClient mqPlain;
WiFiClientSecure mqSecure;
PubSubClient mq;
String mqHost, mqUser, mqPass, mqBase;
uint16_t mqPort = 8883;
bool mqState[NMAX];
bool mqNeedPublish = true;
uint32_t mqNextTry = 0;
int mqFails = 0;

void mqCallback(char* topic, byte* payload, unsigned int len) {
  String t = topic;
  String pre = mqBase + "/cmd/";
  String tp = mqBase + "/tmr/";
  if (t.startsWith(tp)) {
    int tn = t.substring(tp.length()).toInt();
    String tm;
    for (unsigned int i = 0; i < len && i < 80; i++) tm += (char)payload[i];
    int c1 = tm.indexOf(':');
    int c2 = tm.lastIndexOf(':');
    if (c1 < 0 || c2 <= c1 || tm.substring(0, c1) != token) return;
    if (tn < 1 || tn > N) return;
    long tsec = tm.substring(c1 + 1, c2).toInt();
    if (tsec < 0 || tsec > 86400) return;
    tmrSet(tn, (uint32_t)tsec, tm.substring(c2 + 1) == "1");
    return;
  }
  if (!t.startsWith(pre)) return;
  int n = t.substring(pre.length()).toInt();
  String m;
  for (unsigned int i = 0; i < len && i < 80; i++) m += (char)payload[i];
  int c = m.lastIndexOf(':');
  if (c < 0 || m.substring(0, c) != token) return;
  if (n < 1 || n > N) return;
  srcNow = 1;
  apply(n - 1, m.substring(c + 1) == "1");
  srcNow = 1;
}

void mqLoadCfg() {
  mqHost = prefs.getString("mqh", "");
  mqPort = prefs.getUShort("mqp", 8883);
  mqUser = prefs.getString("mqu", "");
  mqPass = prefs.getString("mqw", "");
  mqBase = "ibn/" + deviceId;
  if (mq.connected()) mq.disconnect();
  mqSecure.setInsecure();
  mq.setClient(mqPort == 8883 ? (Client&)mqSecure : (Client&)mqPlain);
  mq.setServer(mqHost.c_str(), mqPort);
  mq.setCallback(mqCallback);
  mq.setSocketTimeout(4);
  mq.setKeepAlive(30);
  mqFails = 0;
  mqNextTry = 0;
  mqNeedPublish = true;
}

String mqInfo() {
  return "{\"host\":\"" + mqHost + "\",\"port\":" + String(mqPort) + ",\"user\":\"" + mqUser +
         "\",\"connected\":" + (mq.connected() ? "true" : "false") + "}";
}

void mqPublishState() {
  mq.publish((mqBase + "/state").c_str(), stateJson().c_str(), true);
  for (int i = 0; i < N; i++) mqState[i] = st[i];
  mqNeedPublish = false;
}

void mqTick() {
  if (mqHost.length() == 0) return;
  if (WiFi.status() != WL_CONNECTED) return;
  if (!mq.connected()) {
    if (millis() < mqNextTry) return;
    String willT = mqBase + "/online";
    bool ok = mq.connect(("ibn-" + deviceId).c_str(),
                         mqUser.length() ? mqUser.c_str() : nullptr,
                         mqPass.length() ? mqPass.c_str() : nullptr,
                         willT.c_str(), 1, true, "0");
    if (!ok) {
      if (mqFails < 6) mqFails++;
      mqNextTry = millis() + 15000UL * mqFails;
      return;
    }
    mqFails = 0;
    mq.subscribe((mqBase + "/cmd/#").c_str());
    mq.subscribe((mqBase + "/tmr/#").c_str());
    mq.publish(willT.c_str(), "1", true);
    mqNeedPublish = true;
  }
  mq.loop();
  if (!mqNeedPublish) {
    for (int i = 0; i < N; i++) {
      if (mqState[i] != st[i]) { mqNeedPublish = true; break; }
    }
  }
  if (mqNeedPublish && mq.connected()) mqPublishState();
}

void mqSetup() {
  mqLoadCfg();
  server.on("/api/mqtt", HTTP_GET, []() {
    if (authed()) server.send(200, "application/json", mqInfo());
  });
  server.on("/api/mqtt", HTTP_POST, []() {
    if (!authed()) return;
    if (server.hasArg("h")) prefs.putString("mqh", server.arg("h"));
    if (server.hasArg("p")) {
      int p = server.arg("p").toInt();
      if (p > 0 && p < 65536) prefs.putUShort("mqp", (uint16_t)p);
    }
    if (server.hasArg("u")) prefs.putString("mqu", server.arg("u"));
    if (server.hasArg("w")) prefs.putString("mqw", server.arg("w"));
    mqLoadCfg();
    server.send(200, "application/json", mqInfo());
  });
}
