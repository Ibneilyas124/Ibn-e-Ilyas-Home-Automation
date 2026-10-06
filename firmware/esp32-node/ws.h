#pragma once
// Live state push over WebSocket (port 81). First message from the app must be: auth <token>
#include <WebSocketsServer.h>

WebSocketsServer wss(81);
bool wsAuth[WEBSOCKETS_SERVER_CLIENT_MAX];
bool lastSt[N];

void wsEvent(uint8_t num, WStype_t type, uint8_t* payload, size_t length) {
  if (type == WStype_DISCONNECTED) {
    wsAuth[num] = false;
    return;
  }
  if (type != WStype_TEXT || wsAuth[num]) return;
  String m = (const char*)payload;
  if (m == "auth " + token) {
    wsAuth[num] = true;
    String s = stateJson();
    wss.sendTXT(num, s);
  } else {
    wss.disconnect(num);
  }
}

void wsSetup() {
  for (int i = 0; i < WEBSOCKETS_SERVER_CLIENT_MAX; i++) wsAuth[i] = false;
  for (int i = 0; i < N; i++) lastSt[i] = st[i];
  wss.begin();
  wss.onEvent(wsEvent);
}

void wsLoop() {
  wss.loop();
  bool changed = false;
  for (int i = 0; i < N; i++) {
    if (st[i] != lastSt[i]) {
      lastSt[i] = st[i];
      changed = true;
    }
  }
  if (!changed) return;
  String s = stateJson();
  for (int i = 0; i < WEBSOCKETS_SERVER_CLIENT_MAX; i++) {
    if (wsAuth[i]) wss.sendTXT(i, s);
  }
}
