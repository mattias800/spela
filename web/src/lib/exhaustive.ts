/**
 * Runtime-safe exhaustive-switch helpers.
 *
 * The `never` parameter makes TypeScript flag any `default:` branch that
 * becomes reachable — e.g. when a server-typed union is widened from a
 * literal set to `string`, or a new case is added to a local union and a
 * switch site isn't updated.
 *
 * Use `throwNever` when the path is unreachable in correct code (invariant
 * violations should crash loud). Use `fallbackNever` in render paths where
 * an unknown tag should degrade gracefully instead of blowing up the page.
 */

export function throwNever(value: never, context?: string): never {
  const detail = context ? ` in ${context}` : "";
  throw new Error(
    `Non-exhaustive switch${detail}: unexpected value ${JSON.stringify(value)}`,
  );
}

export function fallbackNever<T>(_value: never, defaultValue: T): T {
  return defaultValue;
}
