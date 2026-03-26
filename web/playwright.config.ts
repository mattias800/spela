import { defineConfig } from "@playwright/test";

// When E2E_SERVERS_RUNNING is set (by run-e2e.sh), the server and Vite are
// already running — skip Playwright's webServer startup entirely.
const serversAlreadyRunning = !!process.env.E2E_SERVERS_RUNNING;

export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  retries: 0,
  workers: 1, // sequential — tests share server state
  use: {
    baseURL: "http://localhost:5173",
    trace: "on-first-retry",
  },
  ...(!serversAlreadyRunning && {
    webServer: {
      command: "bash e2e/start-e2e.sh",
      url: "http://localhost:5173",
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  }),
  projects: [
    {
      name: "setup",
      testMatch: /global-setup\.spec\.ts/,
    },
    {
      name: "chromium",
      use: {
        browserName: "chromium",
        storageState: "./e2e/.auth/storage-state.json",
      },
      dependencies: ["setup"],
    },
  ],
});
