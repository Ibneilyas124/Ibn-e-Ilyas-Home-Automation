const enc = new TextEncoder();

function hex(n) {
  const b = new Uint8Array(n);
  crypto.getRandomValues(b);
  return [...b].map(x => x.toString(16).padStart(2, "0")).join("");
}

async function same(a, b) {
  const ha = new Uint8Array(await crypto.subtle.digest("SHA-256", enc.encode(String(a))));
  const hb = new Uint8Array(await crypto.subtle.digest("SHA-256", enc.encode(String(b))));
  let d = 0;
  for (let i = 0; i < ha.length; i++) d |= ha[i] ^ hb[i];
  return d === 0;
}

function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function redirectOk(uri) {
  return uri.startsWith("https://oauth-redirect.googleusercontent.com/r/") ||
    uri.startsWith("https://oauth-redirect-sandbox.googleusercontent.com/r/");
}

function json(o, status = 200) {
  return Response.json(o, { status, headers: { "cache-control": "no-store" } });
}

function html(body, status = 200) {
  return new Response(`<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Ibn e Ilyas Home</title><body style="font-family:sans-serif;max-width:420px;margin:40px auto;padding:0 16px">${body}</body>`, {
    status,
    headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" }
  });
}

async function authorize(req, env, u) {
  const p = req.method === "POST" ? new URLSearchParams(await req.text()) : u.searchParams;
  const cid = p.get("client_id") || "";
  const ruri = p.get("redirect_uri") || "";
  const state = p.get("state") || "";
  const rt = p.get("response_type") || "";
  if (!(await same(cid, env.OAUTH_CLIENT_ID)) || rt !== "code" || !redirectOk(ruri)) {
    return html("<p>Invalid request.</p>", 400);
  }
  const form = (msg) => html(`<h2>Ibn e Ilyas Home</h2><p>${msg}</p><form method="post"><input type="hidden" name="client_id" value="${esc(cid)}"><input type="hidden" name="redirect_uri" value="${esc(ruri)}"><input type="hidden" name="state" value="${esc(state)}"><input type="hidden" name="response_type" value="code"><input type="password" name="passcode" placeholder="Passcode" autofocus style="width:100%;padding:12px;font-size:18px"><button style="width:100%;padding:12px;margin-top:12px;font-size:18px">Link</button></form>`);
  if (req.method !== "POST") return form("Enter your passcode to link Google Home.");
  const key = "fail:" + (req.headers.get("cf-connecting-ip") || "x");
  const fails = parseInt((await env.KV.get(key)) || "0", 10);
  if (fails >= 5) return html("<p>Too many attempts. Try again in an hour.</p>", 429);
  if (!(await same(p.get("passcode") || "", env.PASSCODE))) {
    await env.KV.put(key, String(fails + 1), { expirationTtl: 3600 });
    return form("Wrong passcode.");
  }
  const code = hex(24);
  await env.KV.put("code:" + code, "1", { expirationTtl: 600 });
  const to = new URL(ruri);
  to.searchParams.set("code", code);
  if (state) to.searchParams.set("state", state);
  return Response.redirect(to.toString(), 302);
}

async function token(req, env) {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const p = new URLSearchParams(await req.text());
  let cid = p.get("client_id") || "";
  let sec = p.get("client_secret") || "";
  const auth = req.headers.get("authorization") || "";
  if (auth.startsWith("Basic ")) {
    try {
      const [a, b] = atob(auth.slice(6)).split(":");
      cid = cid || a;
      sec = sec || b;
    } catch (e) {}
  }
  if (!(await same(cid, env.OAUTH_CLIENT_ID)) || !(await same(sec, env.OAUTH_CLIENT_SECRET))) {
    return json({ error: "invalid_client" }, 401);
  }
  const g = p.get("grant_type");
  if (g === "authorization_code") {
    const code = p.get("code") || "";
    if (!(await env.KV.get("code:" + code))) return json({ error: "invalid_grant" }, 400);
    await env.KV.delete("code:" + code);
    const at = hex(32);
    const rt = hex(32);
    await env.KV.put("at:" + at, "1", { expirationTtl: 3600 });
    await env.KV.put("rt:" + rt, "1");
    return json({ token_type: "Bearer", access_token: at, refresh_token: rt, expires_in: 3600 });
  }
  if (g === "refresh_token") {
    const rt = p.get("refresh_token") || "";
    if (!(await env.KV.get("rt:" + rt))) return json({ error: "invalid_grant" }, 400);
    const at = hex(32);
    await env.KV.put("at:" + at, "1", { expirationTtl: 3600 });
    return json({ token_type: "Bearer", access_token: at, expires_in: 3600 });
  }
  return json({ error: "unsupported_grant_type" }, 400);
}

const ESP = "ESP32-24BD6F";
const PUBLISH_URL = "https://i1301b28.ala.us-east-1.emqxsl.com:8443/api/v5/publish";
const ROOM = "Room 1";
const DEVICES = [
  { ch: 1, type: "action.devices.types.FAN", name: "Fan" },
  { ch: 2, type: "action.devices.types.OUTLET", name: "Socket" },
  { ch: 3, type: "action.devices.types.LIGHT", name: "Light" },
  { ch: 4, type: "action.devices.types.TV", name: "TV" }
];

function chOf(id) {
  const s = String(id);
  if (!s.startsWith(ESP + ":")) return 0;
  const n = parseInt(s.slice(ESP.length + 1), 10);
  return n >= 1 && n <= 4 ? n : 0;
}

async function setCh(env, ch, on) {
  try {
    const r = await fetch(PUBLISH_URL, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: "Basic " + btoa(env.EMQX_API_ID + ":" + env.EMQX_API_SECRET)
      },
      body: JSON.stringify({
        topic: `ibn/${ESP}/cmd/${ch}`,
        payload: `${env.ESP32_TOKEN}:${on ? 1 : 0}`,
        qos: 1
      })
    });
    if (!r.ok) return false;
    await env.KV.put("st:" + ch, on ? "1" : "0");
    return true;
  } catch (e) {
    return false;
  }
}

async function fulfillment(req, env) {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const auth = req.headers.get("authorization") || "";
  const tok = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!tok || !(await env.KV.get("at:" + tok))) return json({ error: "unauthorized" }, 401);
  let body;
  try { body = await req.json(); } catch (e) { return json({ error: "bad_request" }, 400); }
  const requestId = body.requestId;
  const input = (body.inputs || [])[0] || {};
  const intent = input.intent || "";
  const cfg = await loadCfg(env);

  if (intent === "action.devices.SYNC") {
    return json({
      requestId,
      payload: {
        agentUserId: "ibn-owner",
        devices: cfg.devices.map(d => ({
          id: `${ESP}:${d.ch}`,
          type: d.type,
          traits: ["action.devices.traits.OnOff"],
          name: { name: d.name, defaultNames: [d.name], nicknames: [d.name] },
          willReportState: false,
          roomHint: cfg.room
        }))
      }
    });
  }

  if (intent === "action.devices.QUERY") {
    const out = {};
    for (const d of (input.payload && input.payload.devices) || []) {
      const ch = chOf(d.id);
      if (!ch) { out[d.id] = { status: "ERROR", errorCode: "deviceNotFound" }; continue; }
      out[d.id] = { status: "SUCCESS", online: true, on: (await env.KV.get("st:" + ch)) === "1" };
    }
    return json({ requestId, payload: { devices: out } });
  }

  if (intent === "action.devices.EXECUTE") {
    const results = [];
    for (const c of (input.payload && input.payload.commands) || []) {
      const ids = (c.devices || []).map(d => d.id);
      for (const e of c.execution || []) {
        if (e.command !== "action.devices.commands.OnOff") {
          results.push({ ids, status: "ERROR", errorCode: "functionNotSupported" });
          continue;
        }
        const on = !!(e.params && e.params.on);
        for (const id of ids) {
          const ch = chOf(id);
          const ok = ch ? await setCh(env, ch, on) : false;
          results.push(ok
            ? { ids: [id], status: "SUCCESS", states: { on, online: true } }
            : { ids: [id], status: "ERROR", errorCode: ch ? "hardError" : "deviceNotFound" });
        }
      }
    }
    return json({ requestId, payload: { commands: results } });
  }

  if (intent === "action.devices.DISCONNECT") return json({});
  return json({ requestId, error: "unsupported_intent" }, 400);
}

function b64u(buf) {
  const b = new Uint8Array(buf);
  let t = "";
  for (let i = 0; i < b.length; i++) t += String.fromCharCode(b[i]);
  return btoa(t).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function googleToken(env) {
  const cached = await env.KV.get("gtoken");
  if (cached) return cached;
  const sa = JSON.parse(env.GOOGLE_SA_JSON);
  const now = Math.floor(Date.now() / 1000);
  const head = b64u(enc.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claim = b64u(enc.encode(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/homegraph",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600
  })));
  const pem = sa.private_key.replace(/-----[A-Z ]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), c => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc.encode(head + "." + claim));
  const jwt = head + "." + claim + "." + b64u(sig);
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt })
  });
  if (!r.ok) throw new Error("token_" + r.status);
  const j = await r.json();
  await env.KV.put("gtoken", j.access_token, { expirationTtl: 3000 });
  return j.access_token;
}

async function reportState(env, channels) {
  if (!env.GOOGLE_SA_JSON) return "no_sa";
  try {
    const cfg = await loadCfg(env);
    const states = {};
    for (const d of cfg.devices) states[`${ESP}:${d.ch}`] = { on: !!channels[d.ch - 1], online: true };
    const tok = await googleToken(env);
    const r = await fetch("https://homegraph.googleapis.com/v1/devices:reportStateAndNotification", {
      method: "POST",
      headers: { authorization: "Bearer " + tok, "content-type": "application/json" },
      body: JSON.stringify({ requestId: hex(8), agentUserId: "ibn-owner", payload: { devices: { states } } })
    });
    if (r.status === 401) await env.KV.delete("gtoken");
    return r.ok ? "ok" : "http_" + r.status;
  } catch (e) {
    return "err_" + String(e && e.message ? e.message : e).slice(0, 40);
  }
}

async function stateHook(req, env) {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const key = req.headers.get("x-hook-key") || "";
  if (!env.STATE_HOOK_KEY || !(await same(key, env.STATE_HOOK_KEY))) return json({ error: "unauthorized" }, 401);
  let b;
  try { b = await req.json(); } catch (e) { return json({ error: "bad_request" }, 400); }
  let p = b && b.payload !== undefined ? b.payload : b;
  if (typeof p === "string") { try { p = JSON.parse(p); } catch (e) { return json({ error: "bad_payload" }, 400); } }
  if (!p || p.id !== ESP || !Array.isArray(p.channels)) return json({ error: "bad_payload" }, 400);
  const n = Math.min(p.channels.length, 4);
  for (let i = 0; i < n; i++) await env.KV.put("st:" + (i + 1), p.channels[i] ? "1" : "0");
  const rs = await reportState(env, p.channels);
  return json({ ok: true, rs });
}

const TYPES = {
  light: "action.devices.types.LIGHT", fan: "action.devices.types.FAN",
  outlet: "action.devices.types.OUTLET", switch: "action.devices.types.SWITCH",
  tv: "action.devices.types.TV", heater: "action.devices.types.HEATER", ac: "action.devices.types.AC_UNIT"
};

async function loadCfg(env) {
  try {
    const raw = await env.KV.get("cfg");
    if (raw) {
      const c = JSON.parse(raw);
      if (c && Array.isArray(c.devices) && c.devices.length) return c;
    }
  } catch (e) {}
  return { room: ROOM, devices: DEVICES };
}

async function adminConfig(req, env) {
  const fk = "afail:" + (req.headers.get("cf-connecting-ip") || "x");
  const fails = parseInt((await env.KV.get(fk)) || "0", 10);
  if (fails >= 5) return json({ error: "locked" }, 429);
  if (!(await same(req.headers.get("x-admin-key") || "", env.PASSCODE))) {
    await env.KV.put(fk, String(fails + 1), { expirationTtl: 3600 });
    return json({ error: "unauthorized" }, 401);
  }
  if (req.method === "GET") return json(await loadCfg(env));
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  let b;
  try { b = await req.json(); } catch (e) { return json({ error: "bad_request" }, 400); }
  const room = String(b.room || "").trim();
  if (!room || room.length > 40 || !Array.isArray(b.devices) || b.devices.length < 1 || b.devices.length > 4) {
    return json({ error: "bad_config" }, 400);
  }
  const devices = [];
  const seen = new Set();
  for (const d of b.devices) {
    const ch = parseInt(d.ch, 10);
    const name = String(d.name || "").trim();
    const type = TYPES[String(d.type || "").toLowerCase()];
    if (!(ch >= 1 && ch <= 4) || seen.has(ch) || !name || name.length > 30 || !type) {
      return json({ error: "bad_device" }, 400);
    }
    seen.add(ch);
    devices.push({ ch, type, name });
  }
  const cfg = { room, devices };
  await env.KV.put("cfg", JSON.stringify(cfg));
  return json({ ok: true, cfg });
}

export default {
  async fetch(req, env) {
    const u = new URL(req.url);
    if (u.pathname === "/health") {
      return json({
        ok: true,
        service: "ibn-home-cloud",
        kv: !!env.KV,
        secrets: {
          EMQX_API_ID: !!env.EMQX_API_ID,
          EMQX_API_SECRET: !!env.EMQX_API_SECRET,
          PASSCODE: !!env.PASSCODE,
          OAUTH_CLIENT_ID: !!env.OAUTH_CLIENT_ID,
          OAUTH_CLIENT_SECRET: !!env.OAUTH_CLIENT_SECRET,
          ESP32_TOKEN: !!env.ESP32_TOKEN,
          STATE_HOOK_KEY: !!env.STATE_HOOK_KEY,
          GOOGLE_SA_JSON: !!env.GOOGLE_SA_JSON
        }
      });
    }
    if (u.pathname === "/oauth/authorize") return authorize(req, env, u);
    if (u.pathname === "/oauth/token") return token(req, env);
    if (u.pathname === "/fulfillment") return fulfillment(req, env);
    if (u.pathname === "/state-hook") return stateHook(req, env);
    if (u.pathname === "/admin/config") return adminConfig(req, env);
    return new Response("Not found", { status: 404 });
  }
};
