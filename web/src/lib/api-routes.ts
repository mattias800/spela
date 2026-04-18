/**
 * Typed API route definitions, derived directly from the generated
 * OpenAPI spec. No hand-maintenance required — adding/renaming an
 * endpoint server-side flows through `npm run openapi:gen` into the
 * `paths` map and into these unions automatically.
 *
 * The spec uses `{id}`-style placeholders. Callers in `api-client.ts`
 * pass URLs already interpolated as `${variable}` template literals
 * (e.g. `` `/games/${id}` ``). The `ToTemplate<P>` mapper below rewrites
 * each `{name}` placeholder in a spec path to a TS `${string}` template
 * fragment so the call site shape is preserved.
 *
 * The spec paths include the `/api/` prefix; `api-client.ts` prepends
 * `API_BASE = "/api"` to every call, so we strip that prefix here.
 */
import type { paths } from "@/generated/api";

// Compile-time hook so this file always pulls in `@/generated/api`.
export type GeneratedPaths = keyof paths;

// Strip the leading "/api" from a spec path so the type matches the
// (stripped) URL strings callers pass to api.get/post/etc.
type StripApiPrefix<P extends string> = P extends `/api${infer Rest}` ? Rest : P;

// Convert spec-style "{name}" placeholders into TS template-literal
// `${string}` fragments. Recurses through any number of placeholders.
type ToTemplate<P extends string> =
  P extends `${infer Pre}{${string}}${infer Post}`
    ? `${Pre}${string}${ToTemplate<Post>}`
    : P;

// Convenience: full transformation from a spec path key to the runtime
// pattern `api-client.ts` accepts.
type Route<P extends string> = ToTemplate<StripApiPrefix<P>>;

// Allow an optional `?query=...` suffix on any route. The spec doesn't
// model query strings as part of the path key, so this lets callers
// freely append them.
type WithQuery<T extends string> = T | `${T}?${string}`;

// Per-method path filters: pick path keys whose entry in `paths`
// declares the matching HTTP verb.
type PathsByMethod<M extends string> = {
  [K in keyof paths]: paths[K] extends { [m in M]: unknown } ? K : never;
}[keyof paths];

export type ApiGetPath = WithQuery<Route<PathsByMethod<"get"> & string>>;
// Gin-only POST endpoints (multipart uploads + binary save uploads).
// These don't appear in the OpenAPI spec because huma doesn't model
// multipart bodies / opaque binary streams cleanly. Curated by hand.
type GinOnlyPostPath =
  | "/admin/bios"
  | "/admin/rom-hacks"
  | "/admin/uploads"
  | `/sessions/${string}/saves`
  | `/sessions/${string}/saves/auto`
  | `/games/${string}/sessions/from-shared-save/${string}`;
export type ApiPostPath = WithQuery<
  Route<PathsByMethod<"post"> & string> | GinOnlyPostPath
>;
type GinOnlyPutPath = `/admin/games/${string}/replace-rom`;
export type ApiPutPath = WithQuery<
  Route<PathsByMethod<"put"> & string> | GinOnlyPutPath
>;
export type ApiDeletePath = WithQuery<Route<PathsByMethod<"delete"> & string>>;
export type ApiPatchPath = WithQuery<Route<PathsByMethod<"patch"> & string>>;
