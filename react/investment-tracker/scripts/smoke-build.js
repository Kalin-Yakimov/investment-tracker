import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { preview } from "vite";

const root = fileURLToPath(new URL("../", import.meta.url));
let server;
let checks = 0;

async function request(base, route) {
  const response = await fetch(new URL(route, base), { signal: AbortSignal.timeout(10000) });
  assert.equal(response.status, 200, `Built output must serve ${route}`);
  checks += 1;
  return response;
}

try {
  // Use only the built files, on a temporary loopback port. Never start the API
  // or execute the app in a browser, so no database/provider calls are possible.
  server = await preview({ root, logLevel: "error", preview: { host: "127.0.0.1", port: 0, strictPort: true } });
  const address = server.httpServer.address();
  const base = `http://127.0.0.1:${address.port}/`;
  const html = await (await request(base, "/")).text();
  assert.match(html, /id="root"/);
  assert.doesNotMatch(html, /\/@vite\/client|\/src\/index\.jsx/, "Production output must not depend on dev entry points");
  for (const route of ["/login", "/registration", "/activate-account", "/forgot-password", "/tracker", "/inventory-calculator", "/viewItem/42", "/editItem/42", "/admin"]) {
    assert.equal(await (await request(base, route)).text(), html, `SPA fallback must serve the entry at ${route}`);
  }
  const assets = [...html.matchAll(/(?:src|href)="(\/assets\/[^"?#]+)"/g)].map(match => match[1]);
  assert.ok(assets.some(asset => asset.endsWith(".js")) && assets.some(asset => asset.endsWith(".css")));
  for (const asset of assets) {
    const response = await request(base, asset);
    assert.match(response.headers.get("content-type"), asset.endsWith(".js") ? /javascript/ : /css/);
    assert.ok((await response.arrayBuffer()).byteLength > 0);
  }
  for (const asset of ["/favicon.ico", "/logo192.png", "/logo512.png", "/manifest.json", "/robots.txt"]) {
    const response = await request(base, asset);
    assert.doesNotMatch(response.headers.get("content-type"), /text\/html/, `Public asset ${asset} must not be an SPA fallback`);
    await response.arrayBuffer();
  }
  console.log(`Production HTTP smoke: ${checks} checks passed (no backend or provider calls).`);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  if (server) {
    await new Promise((resolve, reject) => {
      server.httpServer.close(error => error ? reject(error) : resolve());
      server.httpServer.closeIdleConnections();
    });
  }
}
