import { defineConfig, devices } from "@playwright/test";
import { fileURLToPath } from "node:url";

const backend = fileURLToPath(new URL("../../investment-tracker/", import.meta.url));
export default defineConfig({
  testDir: "./e2e", fullyParallel: false, workers: 1, retries: 0,
  timeout: 30000, expect: { timeout: 10000 },
  reporter: [["list"], ["junit", { outputFile: "playwright-report/results.xml" }]],
  use: { baseURL: "http://127.0.0.1:14173", trace: "off", screenshot: "only-on-failure" },
  projects: [
    { name: "desktop-chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile-chromium", use: { ...devices["Pixel 7"] } },
  ],
  webServer: [
    { command: `${process.platform === "win32" ? "gradlew.bat" : "./gradlew"} --no-daemon --console=plain acceptanceServer`,
      cwd: backend, url: "http://127.0.0.1:18080/__acceptance__/ready", timeout: 180000,
      reuseExistingServer: false, stdout: "pipe", stderr: "pipe" },
    { command: "npm run build && node scripts/serve-acceptance.js", timeout: 120000,
      url: "http://127.0.0.1:14173/login", reuseExistingServer: false, stdout: "pipe", stderr: "pipe",
      env: { VITE_API_URL: "http://127.0.0.1:18080", VITE_DEPLOYMENT_TARGET: "acceptance" } },
  ],
});
