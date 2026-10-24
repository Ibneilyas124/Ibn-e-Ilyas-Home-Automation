export default {
  async fetch(req) {
    const u = new URL(req.url);
    if (u.pathname === "/health") {
      return Response.json({ ok: true, service: "ibn-home-cloud" });
    }
    return new Response("Not found", { status: 404 });
  }
};
