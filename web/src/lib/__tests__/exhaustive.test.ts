import { describe, it, expect } from "vitest";
import { throwNever, fallbackNever } from "@/lib/exhaustive";

describe("throwNever", () => {
  it("throws with the stringified value when called at runtime", () => {
    expect(() => throwNever("unexpected" as never)).toThrow(
      /Non-exhaustive switch.*unexpected/,
    );
  });

  it("includes the context label when provided", () => {
    expect(() => throwNever("x" as never, "handleStatus")).toThrow(
      /in handleStatus/,
    );
  });
});

describe("fallbackNever", () => {
  it("returns the provided default when an unknown tag reaches it", () => {
    const result = fallbackNever("surprise" as never, "fallback");
    expect(result).toBe("fallback");
  });
});
