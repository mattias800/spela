export { test, expect } from "@playwright/test";

const SERVER_URL = "http://localhost:8080";

/**
 * Reset the server database to its seed state.
 * Clears all user-generated data while preserving consoles, cores, and scanned games.
 * Call this in test.beforeAll() or test.beforeEach() for tests that mutate server state.
 */
export async function resetServer(): Promise<void> {
  const res = await fetch(`${SERVER_URL}/api/test/reset`, { method: "POST" });
  if (!res.ok) {
    throw new Error(
      `Test reset failed: ${res.status} ${await res.text()}`,
    );
  }
}
