/**
 * Asserts that `value` is not null/undefined and returns it with the narrowed
 * type. Use inside React Query `queryFn`s that are gated by `enabled: !!x`:
 * TypeScript can't infer from `enabled` that the param is defined when the
 * queryFn runs, so the cleanest honest way to satisfy the path param type is
 * to narrow at the boundary.
 *
 * ```ts
 * return useQuery({
 *   queryFn: () =>
 *     unwrap(typedApi.GET("/api/themes/{id}/games", {
 *       params: { path: { id: invariant(themeId, "themeId") } },
 *     })),
 *   enabled: !!themeId,
 * });
 * ```
 *
 * The throw path is dead code in practice, but preferable to `as string` —
 * if the `enabled` guard ever gets removed by accident, the invariant fires
 * loudly instead of sending `undefined` to the server as the string
 * `"undefined"`.
 */
export function invariant<T>(value: T | null | undefined, name: string): T {
  if (value == null) {
    throw new Error(
      `Invariant failed: ${name} must be defined (queryFn called without enabled gate?)`,
    );
  }
  return value;
}
