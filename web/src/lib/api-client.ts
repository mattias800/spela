import createClient from "openapi-fetch";

import type { paths } from "@/generated/api";

const API_BASE = "/api";

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

function getAccessToken(): string | null {
  return localStorage.getItem("accessToken");
}

function getRefreshToken(): string | null {
  return localStorage.getItem("refreshToken");
}

function setTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem("accessToken", accessToken);
  localStorage.setItem("refreshToken", refreshToken);
}

function clearTokens() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
}

let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = doRefresh();
  try {
    return await refreshPromise;
  } finally {
    refreshPromise = null;
  }
}

async function doRefresh(): Promise<string> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new ApiError(401, "No refresh token");
  }

  const res = await fetch(`${API_BASE}/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });

  if (!res.ok) {
    clearTokens();
    throw new ApiError(401, "Token refresh failed");
  }

  const data = await res.json();
  setTokens(data.accessToken, data.refreshToken);
  return data.accessToken;
}

// sendWithAuth is the shared transport powering `typedApi` below. It handles:
//   - injecting the bearer access token
//   - transparent 401 → /auth/refresh → retry-with-new-token
//   - hard redirect to /login on refresh failure
//
// Operates on a plain `Record<string,string>` headers object (rather than a
// `Headers` instance) so the refresh-deduplication tests — which introspect
// header values via indexing on the fetch mock's call args — keep working.
async function sendWithAuth(
  url: string,
  options: RequestInit,
): Promise<Response> {
  const baseHeaders: Record<string, string> = {
    ...(options.headers as Record<string, string> | undefined),
  };

  const doRequest = (token: string | null) => {
    const headers = { ...baseHeaders };
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
    return fetch(url, { ...options, headers });
  };

  const initialToken = getAccessToken();
  let res = await doRequest(initialToken);

  if (res.status === 401 && initialToken) {
    try {
      const newToken = await refreshAccessToken();
      res = await doRequest(newToken);
    } catch {
      clearTokens();
      window.location.href = "/login";
      throw new ApiError(401, "Session expired");
    }
  }

  return res;
}

// authedFetch is the openapi-fetch-compatible wrapper around sendWithAuth.
// openapi-fetch calls its `fetch` option with a Request object; we extract
// the url + init, delegate to sendWithAuth, and return the Response.
// Exported solely so the transport tests can exercise sendWithAuth without
// going through openapi-fetch's URL parser (which rejects relative URLs in
// jsdom).
export async function authedFetch(input: Request): Promise<Response> {
  const body = input.body ? await input.arrayBuffer() : undefined;
  const headers: Record<string, string> = {};
  input.headers.forEach((v, k) => {
    headers[k] = v;
  });
  return sendWithAuth(input.url, {
    method: input.method,
    headers,
    body,
    signal: input.signal,
    credentials: input.credentials,
    cache: input.cache,
    mode: input.mode,
    redirect: input.redirect,
    referrer: input.referrer,
    referrerPolicy: input.referrerPolicy,
    integrity: input.integrity,
    keepalive: input.keepalive,
  });
}

// `api` is the small shared namespace for token storage used across the
// frontend — auth flows set/clear tokens here, WebSocket/iframe URLs read
// the access token for bearer-on-querystring fallbacks. All HTTP calls go
// through `typedApi` below.
export const api = {
  setTokens,
  clearTokens,
  getAccessToken,
};

// typedApi is the generated-spec-aware client built on openapi-fetch. Takes
// the original OpenAPI path template + a `params.path` object and infers the
// response type from the spec. Example:
//
//   const { data } = await typedApi.GET("/api/games/{id}", {
//     params: { path: { id: gameId } },
//   });
//   // data has type components["schemas"]["GameResponse"] | undefined
//
// Shares `authedFetch` with the shared transport so 401-refresh and
// Authorization injection apply transparently.
//
// openapi-fetch returns `{ data, error, response }` on every call; the
// `unwrap()` helper below converts that into the throwing ApiError shape
// call sites already expect.
export const typedApi = createClient<paths>({
  baseUrl: "",
  fetch: authedFetch,
});

// Multipart helper. The generated OpenAPI types file fields as `string`
// (from `format: binary`), so a FormData body doesn't structurally match
// the inferred `body` parameter of typedApi.POST/PUT. `multipart()` returns
// the openapi-fetch options bag with the body cast (and matching
// bodySerializer) in one place — call sites stay free of `as`.
//
// Usage:
//   const formData = new FormData();
//   formData.append("file", file);
//   await typedApi.POST("/api/admin/bios", multipart(formData));
//
//   await typedApi.PUT("/api/admin/games/{id}/replace-rom", {
//     ...multipart(formData),
//     params: { path: { id: gameId } },
//   });
//
// The serializer returns the FormData as-is so the browser sets the correct
// multipart/form-data Content-Type + boundary.
export function multipart(formData: FormData): {
  body: never;
  bodySerializer: (body: unknown) => FormData;
} {
  return {
    body: formData as unknown as never,
    bodySerializer: (body) => {
      if (body instanceof FormData) return body;
      throw new Error(
        "multipart bodySerializer expects FormData; got " + typeof body,
      );
    },
  };
}

// unwrap resolves a { data, error, response } FetchResponse into the success
// value, throwing ApiError on failure. Hook call sites wrap their typedApi
// calls in unwrap() so errors bubble through react-query's onError / try-
// catch paths like a throwing fetcher.
export async function unwrap<D, E>(
  promise: Promise<
    | { data: D; error?: never; response: Response }
    | { data?: never; error: E; response: Response }
  >,
): Promise<D> {
  const { data, error, response } = await promise;
  if (error !== undefined) {
    const message =
      (error as { message?: string; error?: string } | undefined)?.message ??
      (error as { message?: string; error?: string } | undefined)?.error ??
      `Request failed (${response.status})`;
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) {
    return undefined as D;
  }
  return data as D;
}
