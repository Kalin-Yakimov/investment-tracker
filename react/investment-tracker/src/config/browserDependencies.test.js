// @vitest-environment node
import { fileURLToPath } from "node:url";
import { createServer } from "vite";
import { expect, test } from "vitest";

const projectRoot = fileURLToPath(new URL("../../", import.meta.url));

test("the real Vite client resolves the date picker to its ESM component entry", async () => {
  // Node/Vitest and browser entry resolution differ. No HTTP server, app/API
  // requests or shared dev cache are needed to check the actual client resolver.
  const server = await createServer({
    root: projectRoot,
    configFile: fileURLToPath(new URL("../../vite.config.js", import.meta.url)),
    cacheDir: fileURLToPath(new URL("../../node_modules/.vite-resolution-test", import.meta.url)),
    logLevel: "silent",
    server: { middlewareMode: true, hmr: false, watch: null },
    optimizeDeps: { noDiscovery: true },
  });
  try {
    const resolved = await server.environments.client.pluginContainer.resolveId(
      "react-datepicker",
      fileURLToPath(new URL("../features/portfolio/components/RegisterPortfolioItem.jsx", import.meta.url)),
    );
    expect(resolved?.id.split("?")[0].replaceAll("\\", "/")).toMatch(/\/react-datepicker\/dist\/es\/index\.js$/);
    const stylesheet = await server.environments.client.pluginContainer.resolveId(
      "react-datepicker/dist/react-datepicker.module.css",
      fileURLToPath(new URL("../features/portfolio/components/RegisterPortfolioItem.jsx", import.meta.url)),
    );
    expect(stylesheet?.id.split("?")[0].replaceAll("\\", "/")).toMatch(/\/react-datepicker\/dist\/react-datepicker\.module\.css$/);
  } finally {
    await server.close();
  }
}, 15000);
