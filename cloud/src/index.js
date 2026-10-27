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

async function homeFromKey(env, raw) {
  const k = String(raw).trim().toUpperCase();
  if (!/^IBN-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$/.test(k)) return "";
  const v = await env.KV.get("akey:" + k);
  if (!v || !v.startsWith("used:")) return "";
  const h = v.slice(5);
  if (!/^h[0-9a-f]{8}$/.test(h)) return "";
  if (!(await env.KV.get("home:" + h))) return "";
  return "h:" + h;
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
  let cval = "1";
  if (!(await same(p.get("passcode") || "", env.PASSCODE))) {
    cval = await homeFromKey(env, p.get("passcode") || "");
  }
  if (!cval) {
    await env.KV.put(key, String(fails + 1), { expirationTtl: 3600 });
    return form("Wrong passcode.");
  }
  const code = hex(24);
  await env.KV.put("code:" + code, cval, { expirationTtl: 600 });
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
    const cv = await env.KV.get("code:" + code);
    if (!cv) return json({ error: "invalid_grant" }, 400);
    await env.KV.delete("code:" + code);
    const at = hex(32);
    const rt = hex(32);
    await env.KV.put("at:" + at, cv, { expirationTtl: 3600 });
    await env.KV.put("rt:" + rt, cv);
    return json({ token_type: "Bearer", access_token: at, refresh_token: rt, expires_in: 3600 });
  }
  if (g === "refresh_token") {
    const rt = p.get("refresh_token") || "";
    const rv = await env.KV.get("rt:" + rt);
    if (!rv) return json({ error: "invalid_grant" }, 400);
    const at = hex(32);
    await env.KV.put("at:" + at, rv, { expirationTtl: 3600 });
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
    await putIfChanged(env, "st:" + ch, on ? "1" : "0");
    return true;
  } catch (e) {
    return false;
  }
}

async function homeRec(env, h) {
  try {
    const raw = await env.KV.get("home:" + h);
    if (!raw) return null;
    const r = JSON.parse(raw);
    if (!r || typeof r.esp !== "string" || typeof r.token !== "string") return null;
    return r;
  } catch (e) {
    return null;
  }
}

function homeChOf(rec, id) {
  const s = String(id);
  if (!s.startsWith(rec.esp + ":")) return 0;
  const t = s.slice(rec.esp.length + 1);
  const n = parseInt(t, 10);
  if (String(n) !== t) return 0;
  const devs = rec.cfg && Array.isArray(rec.cfg.devices) ? rec.cfg.devices : [];
  return devs.some(d => d.ch === n) ? n : 0;
}

async function setChHome(env, h, rec, ch, on) {
  try {
    const r = await fetch(PUBLISH_URL, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: "Basic " + btoa(env.EMQX_API_ID + ":" + env.EMQX_API_SECRET)
      },
      body: JSON.stringify({
        topic: `ibn/${rec.esp}/cmd/${ch}`,
        payload: `${rec.token}:${on ? 1 : 0}`,
        qos: 1
      })
    });
    if (!r.ok) return false;
    await putIfChanged(env, "hst:" + h + ":" + ch, on ? "1" : "0");
    return true;
  } catch (e) {
    return false;
  }
}

async function homeFulfillment(env, h, body) {
  const rec = await homeRec(env, h);
  if (!rec) return json({ error: "forbidden" }, 403);
  const requestId = body.requestId;
  const input = (body.inputs || [])[0] || {};
  const intent = input.intent || "";
  const cfg = rec.cfg && Array.isArray(rec.cfg.devices) ? rec.cfg : { room: "Room 1", devices: [] };

  if (intent === "action.devices.SYNC") {
    return json({
      requestId,
      payload: {
        agentUserId: "home-" + h,
        devices: cfg.devices.map(d => {
          const t = String(d.type || "");
          const gtype = TYPES[t.toLowerCase()] || (t.startsWith("action.devices.types.") ? t : TYPES.switch);
          return {
            id: `${rec.esp}:${d.ch}`,
            type: gtype,
            traits: ["action.devices.traits.OnOff"],
            name: { name: d.name, defaultNames: [d.name], nicknames: [d.name] },
            willReportState: false,
            roomHint: cfg.room
          };
        })
      }
    });
  }

  if (intent === "action.devices.QUERY") {
    const out = {};
    for (const d of (input.payload && input.payload.devices) || []) {
      const ch = homeChOf(rec, d.id);
      if (!ch) { out[d.id] = { status: "ERROR", errorCode: "deviceNotFound" }; continue; }
      out[d.id] = { status: "SUCCESS", online: true, on: (await env.KV.get("hst:" + h + ":" + ch)) === "1" };
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
          const ch = homeChOf(rec, id);
          const ok = ch ? await setChHome(env, h, rec, ch, on) : false;
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

async function fulfillment(req, env) {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const auth = req.headers.get("authorization") || "";
  const tok = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  const tv = tok ? await env.KV.get("at:" + tok) : null;
  if (!tv) return json({ error: "unauthorized" }, 401);
  const hm = /^h:(h[0-9a-f]{8})$/.exec(tv);
  if (tv !== "1" && !hm) return json({ error: "forbidden" }, 403);
  let body;
  try { body = await req.json(); } catch (e) { return json({ error: "bad_request" }, 400); }
  if (hm) return homeFulfillment(env, hm[1], body);
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

async function putIfChanged(env, key, val) {
  const old = await env.KV.get(key);
  if (old !== val) await env.KV.put(key, val);
}

async function stateHook(req, env) {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const key = req.headers.get("x-hook-key") || "";
  if (!env.STATE_HOOK_KEY || !(await same(key, env.STATE_HOOK_KEY))) return json({ error: "unauthorized" }, 401);
  let b;
  try { b = await req.json(); } catch (e) { return json({ error: "bad_request" }, 400); }
  let p = b && b.payload !== undefined ? b.payload : b;
  if (typeof p === "string") { try { p = JSON.parse(p); } catch (e) { return json({ error: "bad_payload" }, 400); } }
  if (!p || typeof p.id !== "string" || !Array.isArray(p.channels)) return json({ error: "bad_payload" }, 400);
  if (p.id !== ESP) {
    if (!/^[A-Za-z0-9_-]{4,24}$/.test(p.id)) return json({ error: "bad_payload" }, 400);
    const h = await env.KV.get("espmap:" + p.id);
    if (!h) return json({ ok: true, skip: "unknown_esp" });
    const m = Math.min(p.channels.length, 8);
    for (let i = 0; i < m; i++) await putIfChanged(env, "hst:" + h + ":" + (i + 1), p.channels[i] ? "1" : "0");
    return json({ ok: true, home: true });
  }
  const n = Math.min(p.channels.length, 4);
  for (let i = 0; i < n; i++) await putIfChanged(env, "st:" + (i + 1), p.channels[i] ? "1" : "0");
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

async function adminNewKey(req, env) {
  const fk = "afail:" + (req.headers.get("cf-connecting-ip") || "x");
  const fails = parseInt((await env.KV.get(fk)) || "0", 10);
  if (fails >= 5) return json({ error: "locked" }, 429);
  if (!(await same(req.headers.get("x-admin-key") || "", env.PASSCODE))) {
    await env.KV.put(fk, String(fails + 1), { expirationTtl: 3600 });
    return json({ error: "unauthorized" }, 401);
  }
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const h = Array.from(crypto.getRandomValues(new Uint8Array(6)), (b) => b.toString(16).padStart(2, "0")).join("").toUpperCase();
  const key = "IBN-" + h.slice(0, 4) + "-" + h.slice(4, 8) + "-" + h.slice(8, 12);
  await env.KV.put("akey:" + key, "1");
  return json({ ok: true, key });
}

function rhex(n) {
  return Array.from(crypto.getRandomValues(new Uint8Array(n)), (b) => b.toString(16).padStart(2, "0")).join("");
}

async function homeActivate(req, env) {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const fk = "kfail:" + (req.headers.get("cf-connecting-ip") || "x");
  const fails = parseInt((await env.KV.get(fk)) || "0", 10);
  if (fails >= 5) return json({ error: "locked" }, 429);
  let b;
  try { b = await req.json(); } catch (e) { return json({ error: "bad_request" }, 400); }
  const key = String((b && b.key) || "").trim().toUpperCase();
  const esp = String((b && b.esp) || "").trim();
  if (!/^IBN-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$/.test(key) || !/^[A-Za-z0-9_-]{4,24}$/.test(esp)) return json({ error: "bad_request" }, 400);
  if ((await env.KV.get("akey:" + key)) !== "1") {
    await env.KV.put(fk, String(fails + 1), { expirationTtl: 3600 });
    return json({ error: "invalid_key" }, 403);
  }
  if (esp === ESP || (await env.KV.get("espmap:" + esp))) return json({ error: "esp_taken" }, 409);
  const base = PUBLISH_URL.replace(/\/publish$/, "");
  const H = { authorization: "Basic " + btoa(env.EMQX_API_ID + ":" + env.EMQX_API_SECRET), "content-type": "application/json" };
  const A = "/authentication/password_based:built_in_database/users";
  const Z = "/authorization/sources/built_in_database/rules/users";
  const call = async (m, p, body) => {
    try {
      const r = await fetch(base + p, { method: m, headers: H, body: body ? JSON.stringify(body) : undefined });
      return r.status;
    } catch (e) {
      return 0;
    }
  };
  const home = "h" + rhex(4);
  const user = "ibn_" + home;
  const pass = rhex(12);
  const tin = String((b && b.token) || "").trim();
  if (tin && !/^[0-9A-Fa-f]{4,32}$/.test(tin)) return json({ error: "bad_request" }, 400);
  const token = tin || rhex(16);
  if ((await call("POST", A, { user_id: user, password: pass, is_superuser: false })) !== 201) return json({ error: "broker_failed" }, 502);
  const rules = [
    { topic: "ibn/" + esp + "/#", action: "all", permission: "allow" },
    { topic: "#", action: "all", permission: "deny" }
  ];
  if ((await call("POST", Z, [{ username: user, rules }])) !== 204) {
    await call("DELETE", A + "/" + user, null);
    return json({ error: "broker_failed" }, 502);
  }
  const cfg = { room: "Room 1", devices: [1, 2, 3, 4].map((n) => ({ ch: n, type: "switch", name: "Switch " + n })) };
  await env.KV.put("home:" + home, JSON.stringify({ esp, user, token, cfg, key, t: Date.now() }));
  await env.KV.put("espmap:" + esp, home);
  await env.KV.put("akey:" + key, "used:" + home);
  return json({ ok: true, home, host: new URL(PUBLISH_URL).hostname, port: 8883, user, pass, token, esp });
}

async function homeAddPhone(req, env) {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const fk = "pfail:" + (req.headers.get("cf-connecting-ip") || "x");
  const fails = parseInt((await env.KV.get(fk)) || "0", 10);
  if (fails >= 5) return json({ error: "locked" }, 429);
  let b;
  try { b = await req.json(); } catch (e) { return json({ error: "bad_request" }, 400); }
  const esp = String((b && b.esp) || "").trim();
  const tin = String((b && b.token) || "").trim().toLowerCase();
  if (!/^[A-Za-z0-9_-]{4,24}$/.test(esp) || !/^[0-9a-f]{4,32}$/.test(tin)) return json({ error: "bad_request" }, 400);
  const ek = "pfe:" + esp;
  const efails = parseInt((await env.KV.get(ek)) || "0", 10);
  if (efails >= 10) return json({ error: "locked" }, 429);
  const home = (await env.KV.get("espmap:" + esp)) || "";
  const rec = /^h[0-9a-f]{8}$/.test(home) ? await homeRec(env, home) : null;
  if (!rec || rec.esp !== esp || !(await same(tin, String(rec.token).toLowerCase()))) {
    await env.KV.put(fk, String(fails + 1), { expirationTtl: 3600 });
    await env.KV.put(ek, String(efails + 1), { expirationTtl: 3600 });
    return json({ error: "invalid" }, 403);
  }
  const np = Number.isInteger(rec.np) && rec.np >= 0 ? rec.np : 0;
  if (np >= 4) return json({ error: "phone_limit" }, 409);
  const base = PUBLISH_URL.replace(/\/publish$/, "");
  const H = { authorization: "Basic " + btoa(env.EMQX_API_ID + ":" + env.EMQX_API_SECRET), "content-type": "application/json" };
  const A = "/authentication/password_based:built_in_database/users";
  const Z = "/authorization/sources/built_in_database/rules/users";
  const call = async (m, p, body) => {
    try {
      const r = await fetch(base + p, { method: m, headers: H, body: body ? JSON.stringify(body) : undefined });
      return r.status;
    } catch (e) {
      return 0;
    }
  };
  const n = np + 1;
  const user = "ibn_" + home + "p" + n;
  const pass = rhex(12);
  await call("DELETE", Z + "/" + user, null);
  await call("DELETE", A + "/" + user, null);
  if ((await call("POST", A, { user_id: user, password: pass, is_superuser: false })) !== 201) return json({ error: "broker_failed" }, 502);
  const rules = [
    { topic: "ibn/" + esp + "/#", action: "all", permission: "allow" },
    { topic: "#", action: "all", permission: "deny" }
  ];
  if ((await call("POST", Z, [{ username: user, rules }])) !== 204) {
    await call("DELETE", A + "/" + user, null);
    return json({ error: "broker_failed" }, 502);
  }
  rec.np = n;
  await env.KV.put("home:" + home, JSON.stringify(rec));
  return json({ ok: true, host: new URL(PUBLISH_URL).hostname, port: 8883, user, pass, esp, phones: n });
}

async function adminDelHome(req, env) {
  const fk = "afail:" + (req.headers.get("cf-connecting-ip") || "x");
  const fails = parseInt((await env.KV.get(fk)) || "0", 10);
  if (fails >= 5) return json({ error: "locked" }, 429);
  if (!(await same(req.headers.get("x-admin-key") || "", env.PASSCODE))) {
    await env.KV.put(fk, String(fails + 1), { expirationTtl: 3600 });
    return json({ error: "unauthorized" }, 401);
  }
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  let b;
  try { b = await req.json(); } catch (e) { return json({ error: "bad_request" }, 400); }
  const home = String((b && b.home) || "").trim();
  if (!/^h[0-9a-f]{8}$/.test(home)) return json({ error: "bad_request" }, 400);
  const raw = await env.KV.get("home:" + home);
  if (!raw) return json({ error: "not_found" }, 404);
  let rec;
  try { rec = JSON.parse(raw); } catch (e) { return json({ error: "bad_record" }, 500); }
  if (rec.user !== "ibn_" + home) return json({ error: "bad_record" }, 500);
  const base = PUBLISH_URL.replace(/\/publish$/, "");
  const H = { authorization: "Basic " + btoa(env.EMQX_API_ID + ":" + env.EMQX_API_SECRET) };
  const del = async (p) => {
    try { return (await fetch(base + p, { method: "DELETE", headers: H })).status; } catch (e) { return 0; }
  };
  const acl = await del("/authorization/sources/built_in_database/rules/users/" + rec.user);
  const usr = await del("/authentication/password_based:built_in_database/users/" + rec.user);
  if (usr !== 204 && usr !== 404) return json({ error: "broker_failed", acl, usr }, 502);
  for (let i = 1; i <= 4; i++) {
    const pu = rec.user + "p" + i;
    await del("/authorization/sources/built_in_database/rules/users/" + pu);
    const ps = await del("/authentication/password_based:built_in_database/users/" + pu);
    if (ps !== 204 && ps !== 404) return json({ error: "broker_failed", phone: i, ps }, 502);
  }
  await env.KV.delete("home:" + home);
  if (typeof rec.key === "string" && /^IBN-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$/.test(rec.key) && (await env.KV.get("akey:" + rec.key)) === "used:" + home) await env.KV.delete("akey:" + rec.key);
  if (rec.esp && (await env.KV.get("espmap:" + rec.esp)) === home) await env.KV.delete("espmap:" + rec.esp);
  for (let i = 1; i <= 8; i++) await env.KV.delete("hst:" + home + ":" + i);
  return json({ ok: true, acl, usr });
}

async function adminAuth(req, env) {
  const fk = "afail:" + (req.headers.get("cf-connecting-ip") || "x");
  const fails = parseInt((await env.KV.get(fk)) || "0", 10);
  if (fails >= 5) return json({ error: "locked" }, 429);
  if (!(await same(req.headers.get("x-admin-key") || "", env.PASSCODE))) {
    await env.KV.put(fk, String(fails + 1), { expirationTtl: 3600 });
    return json({ error: "unauthorized" }, 401);
  }
  return null;
}

async function adminListKeys(req, env) {
  const bad = await adminAuth(req, env);
  if (bad) return bad;
  if (req.method !== "GET" && req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const l = await env.KV.list({ prefix: "akey:", limit: 50 });
  const keys = [];
  for (const k of l.keys) {
    const key = k.name.slice(5);
    const v = (await env.KV.get(k.name)) || "";
    const hint = key.slice(0, 8) + "-****-****";
    if (v === "1") { keys.push({ hint, status: "unused" }); continue; }
    if (v.startsWith("used:")) {
      const h = v.slice(5);
      const ok = /^h[0-9a-f]{8}$/.test(h) && !!(await env.KV.get("home:" + h));
      keys.push({ hint, status: ok ? "active" : "orphan", home: h });
      continue;
    }
    keys.push({ hint, status: "unknown" });
  }
  return json({ ok: true, count: keys.length, more: !l.list_complete, keys });
}

async function adminRevokeKey(req, env) {
  const bad = await adminAuth(req, env);
  if (bad) return bad;
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  let b;
  try { b = await req.json(); } catch (e) { return json({ error: "bad_request" }, 400); }
  const key = String((b && b.key) || "").trim().toUpperCase();
  if (!/^IBN-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$/.test(key)) return json({ error: "bad_request" }, 400);
  const v = await env.KV.get("akey:" + key);
  if (v === null) return json({ error: "not_found" }, 404);
  if (v !== "1") {
    const h = v.startsWith("used:") ? v.slice(5) : "";
    if (!/^h[0-9a-f]{8}$/.test(h) || (await env.KV.get("home:" + h))) return json({ error: "key_in_use_use_delhome" }, 409);
  }
  await env.KV.delete("akey:" + key);
  return json({ ok: true });
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
    if (u.pathname === "/admin/newkey") return adminNewKey(req, env);
    if (u.pathname === "/admin/listkeys") return adminListKeys(req, env);
    if (u.pathname === "/admin/revoke") return adminRevokeKey(req, env);
    if (u.pathname === "/home/activate") return homeActivate(req, env);
    if (u.pathname === "/home/addphone") return homeAddPhone(req, env);
    if (u.pathname === "/admin/delhome") return adminDelHome(req, env);
    return new Response("Not found", { status: 404 });
  }
};
