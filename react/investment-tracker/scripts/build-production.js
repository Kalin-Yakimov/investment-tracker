import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
// Deliberately opt in: ordinary npm build remains usable for local/CI smoke tests.
const vite = fileURLToPath(new URL("../node_modules/vite/bin/vite.js", import.meta.url));
const result = spawnSync(process.execPath, [vite, "build"], {
  stdio: "inherit", env: { ...process.env, VITE_DEPLOYMENT_TARGET: "production" },
});
process.exit(result.status ?? 1);
