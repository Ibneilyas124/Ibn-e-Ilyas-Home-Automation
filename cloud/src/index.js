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
          OAUTH_CLIENT_SECRET: !!env.OAUTH_CLIENT_SECRET
        }
      });
    }
    if (u.pathname === "/oauth/authorize") return authorize(req, env, u);
    if (u.pathname === "/oauth/token") return token(req, env);
    return new Response("Not found", { status: 404 });
  }
};
