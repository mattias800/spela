import createClient from "openapi-fetch";

import type { paths } from "@/generated/api";
import type {
  ApiGetPath,
  ApiPostPath,
  ApiPutPath,
  ApiDeletePath,
} from "./api-routes";

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

// sendWithAuth is the shared transport used by both the legacy `api` object
// and the typed openapi-fetch `typedApi` client below. It handles:
//   - injecting the bearer access token
//   - transparent 401 → /auth/refresh → retry-with-new-token
//   - hard redirect to /login on refresh failure
//
// Operates on a plain `Record<string,string>` headers object (rather than a
// `Headers` instance) so the existing refresh-deduplication tests — which
// introspect header values via indexing on the fetch mock's call args — keep
// working without modification.
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
async function authedFetch(input: Request): Promise<Response> {
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

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE}${path}`;
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string> | undefined),
  };

  if (options.body && typeof options.body === "string" && !("Content-Type" in headers)) {
    headers["Content-Type"] = "application/json";
  }

  const res = await sendWithAuth(url, { ...options, headers });

  if (!res.ok) {
    const body = await res.text();
    let message: string;
    try {
      const parsed = JSON.parse(body);
      message = parsed.message ?? parsed.error ?? body;
    } catch {
      message = body;
    }
    throw new ApiError(res.status, message || `Request failed (${res.status})`);
  }

  if (res.status === 204) {
    return undefined as T;
  }

  return res.json();
}

export const api = {
  get: <T>(path: ApiGetPath) => request<T>(path),

  post: <T>(path: ApiPostPath, body?: unknown) =>
    request<T>(path, {
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
    }),

  put: <T>(path: ApiPutPath, body?: unknown) =>
    request<T>(path, {
      method: "PUT",
      body: body ? JSON.stringify(body) : undefined,
    }),

  delete: <T>(path: ApiDeletePath) => request<T>(path, { method: "DELETE" }),

  upload: <T>(path: ApiPostPath, formData: FormData) =>
    request<T>(path, { method: "POST", body: formData }),

  uploadPut: <T>(path: ApiPutPath, formData: FormData) =>
    request<T>(path, { method: "PUT", body: formData }),

  setTokens,
  clearTokens,
  getAccessToken,
};

// typedApi is the generated-spec-aware client. Unlike `api` above (which takes
// already-interpolated paths and an explicit `<T>` return generic), typedApi
// takes the original OpenAPI path template + a `params.path` object and
// infers the response type from the spec. Example:
//
//   const { data } = await typedApi.GET("/api/games/{id}", {
//     params: { path: { id: gameId } },
//   });
//   // data has type components["schemas"]["GameResponse"] | undefined
//
// Call sites can migrate to typedApi incrementally; both clients share the
// same underlying transport (authedFetch) so 401-refresh and Authorization
// injection behave identically.
//
// openapi-fetch returns `{ data, error, response }` on every call; the
// `unwrap()` helper below converts that into the throwing ApiError shape
// call sites already expect, so migrations can be mechanical.
export const typedApi = createClient<paths>({
  baseUrl: "",
  fetch: authedFetch,
});

// Multipart bodySerializer for openapi-fetch. The generated path spec types
// file fields as `string` (from `format: binary` in OpenAPI), so multipart
// call sites need to pass a FormData-compatible body via an `unknown` cast.
// Use like:
//
//   const formData = new FormData();
//   formData.append("file", file);
//   await typedApi.POST("/api/admin/bios", {
//     body: formData as unknown as never,
//     bodySerializer: multipartBodySerializer,
//   });
//
// The serializer returns the FormData as-is so the browser sets the correct
// multipart/form-data Content-Type + boundary.
export function multipartBodySerializer(body: unknown): FormData {
  if (body instanceof FormData) return body;
  throw new Error(
    "multipartBodySerializer expects a FormData body; got " + typeof body,
  );
}

// unwrap resolves a { data, error, response } FetchResponse into the success
// value, throwing ApiError on failure. Matches the throw-on-error behavior
// of the legacy `api` object so migrated call sites don't need to reshape
// their error handling (react-query onError, try/catch, etc. all keep working).
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
