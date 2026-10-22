import { preview } from "vite";
import { fileURLToPath } from "node:url";

// Built output only; a separate loopback port, never a user-configured host.
const server = await preview({ configFile: false,
  root: fileURLToPath(new URL("../", import.meta.url)), build: { outDir: "build" },
  preview: { host: "127.0.0.1", port: 14173, strictPort: true },
});
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => { server.httpServer.close(); server.httpServer.closeAllConnections(); });
}
