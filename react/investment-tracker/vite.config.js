import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { readPublicEnvironment } from "./src/config/publicEnvironment.js";

const projectRoot = fileURLToPath(new URL(".", import.meta.url));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, projectRoot, ["VITE_", "REACT_APP_"]);
  const publicEnv = readPublicEnvironment(env);
  return {
    plugins: [react()],
    resolve: {
      alias: [{
        // Version 7.3 publishes a UMD `browser` entry whose default import is
        // a module object in Vite 8. Use its existing ESM component in dev,
        // build and tests; the exact match leaves stylesheet imports alone.
        find: /^react-datepicker$/,
        replacement: fileURLToPath(new URL("./node_modules/react-datepicker/dist/es/index.js", import.meta.url)),
      }],
    },
    // Only public frontend settings are mapped. Legacy names keep existing
    // local configuration working without editing ignored .env files.
    define: {
      "import.meta.env.VITE_API_URL": JSON.stringify(
        publicEnv.VITE_API_URL,
      ),
      "import.meta.env.VITE_ITEM_SEARCH_DEBOUNCE_MS": JSON.stringify(
        publicEnv.VITE_ITEM_SEARCH_DEBOUNCE_MS,
      ),
    },
    server: {
      host: "localhost",
      port: 3000,
      strictPort: true,
      fs: { strict: true, allow: [projectRoot] },
    },
    preview: { host: "localhost", port: 3000, strictPort: true },
    build: {
      // Keep the existing deployment output directory.
      outDir: "build",
      target: ["chrome87", "edge88", "firefox78", "safari14"],
    },
    test: {
      environment: "jsdom",
      environmentOptions: { jsdom: { url: "http://localhost:3000" } },
      setupFiles: ["./src/setupTests.js"],
      include: ["src/**/*.test.{js,jsx}"],
      globals: false,
      clearMocks: true,
      restoreMocks: true,
    },
  };
});
