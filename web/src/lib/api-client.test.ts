import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { authedFetch } from "./api-client";

// Tests the shared `sendWithAuth` + refresh-deduplication transport. We call
// `authedFetch` (the openapi-fetch fetch-adapter used by typedApi) directly
// with Request objects rather than going through typedApi, because
// openapi-fetch's URL parser rejects relative URLs in jsdom.

function mockLocalStorage() {
  const store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      for (const key of Object.keys(store)) delete store[key];
    }),
    get length() {
      return Object.keys(store).length;
    },
    key: vi.fn(() => null),
  } satisfies Storage;
}

function makeRequest(path: string): Request {
  return new Request(`http://localhost${path}`, { method: "GET" });
}

describe("api token refresh deduplication", () => {
  let storage: ReturnType<typeof mockLocalStorage>;
  const originalFetch = globalThis.fetch;
  const originalLocation = window.location;

  beforeEach(() => {
    storage = mockLocalStorage();
    Object.defineProperty(globalThis, "localStorage", {
      value: storage,
      writable: true,
    });
    // Prevent navigation on session expiry
    Object.defineProperty(window, "location", {
      value: { href: "" },
      writable: true,
    });
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    Object.defineProperty(window, "location", {
      value: originalLocation,
      writable: true,
    });
    vi.restoreAllMocks();
  });

  it("deduplicates concurrent refresh calls — only one refresh request is made", async () => {
    // Set up an expired access token so requests get 401
    storage.setItem("accessToken", "expired-token");
    storage.setItem("refreshToken", "valid-refresh-token");

    let refreshCallCount = 0;

    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input.toString();

      if (url.includes("/auth/refresh")) {
        refreshCallCount++;
        // Simulate async delay so concurrent callers overlap
        await new Promise((r) => setTimeout(r, 10));
        return new Response(
          JSON.stringify({
            accessToken: "new-access-token",
            refreshToken: "new-refresh-token",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        );
      }

      // First call with expired token returns 401, retries succeed.
      const bearer = (init?.headers as Record<string, string> | undefined)?.[
        "Authorization"
      ];

      if (bearer === "Bearer expired-token") {
        return new Response("Unauthorized", { status: 401 });
      }

      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    // Fire 3 concurrent requests — all will get 401 and attempt refresh.
    const responses = await Promise.all([
      authedFetch(makeRequest("/api/test/1")),
      authedFetch(makeRequest("/api/test/2")),
      authedFetch(makeRequest("/api/test/3")),
    ]);

    expect(responses).toHaveLength(3);
    for (const res of responses) {
      expect(res.status).toBe(200);
    }
    // The key assertion: only ONE refresh call was made despite 3 concurrent 401s
    expect(refreshCallCount).toBe(1);
  });

  it("clears the refresh promise after failure so subsequent attempts can retry", async () => {
    storage.setItem("accessToken", "expired-token");
    storage.setItem("refreshToken", "bad-refresh-token");

    let refreshCallCount = 0;

    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input.toString();

      if (url.includes("/auth/refresh")) {
        refreshCallCount++;
        return new Response("Forbidden", { status: 403 });
      }

      const bearer = (init?.headers as Record<string, string> | undefined)?.[
        "Authorization"
      ];

      if (bearer === "Bearer expired-token") {
        return new Response("Unauthorized", { status: 401 });
      }

      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    // First request fails refresh → sendWithAuth throws ApiError("Session expired")
    await expect(authedFetch(makeRequest("/api/test"))).rejects.toThrow(
      "Session expired",
    );
    expect(refreshCallCount).toBe(1);

    // Set up a new token and try again — should attempt a NEW refresh (not reuse old failed promise)
    storage.setItem("accessToken", "expired-token-2");
    storage.setItem("refreshToken", "new-refresh-token");

    // Reconfigure fetch so second refresh succeeds
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input.toString();

      if (url.includes("/auth/refresh")) {
        refreshCallCount++;
        return new Response(
          JSON.stringify({
            accessToken: "fresh-token",
            refreshToken: "fresh-refresh",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        );
      }

      const bearer = (init?.headers as Record<string, string> | undefined)?.[
        "Authorization"
      ];

      if (bearer === "Bearer expired-token-2") {
        return new Response("Unauthorized", { status: 401 });
      }

      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });

    const res = await authedFetch(makeRequest("/api/test"));
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toEqual({ ok: true });
    // Second refresh happened (total = 2)
    expect(refreshCallCount).toBe(2);
  });
});
