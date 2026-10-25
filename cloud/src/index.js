export default {
  async fetch(req, env) {
    const u = new URL(req.url);
    if (u.pathname === "/health") {
      return Response.json({
        ok: true,
        service: "ibn-home-cloud",
        kv: !!env.KV,
        secrets: {
          EMQX_API_ID: !!env.EMQX_API_ID,
          EMQX_API_SECRET: !!env.EMQX_API_SECRET,
          PASSCODE: !!env.PASSCODE
        }
      });
    }
    return new Response("Not found", { status: 404 });
  }
};
