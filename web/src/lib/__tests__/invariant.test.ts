import { describe, it, expect } from "vitest";
import { invariant } from "../invariant";

describe("invariant", () => {
  it("returns the value when defined", () => {
    expect(invariant("hello", "x")).toBe("hello");
    expect(invariant(0, "x")).toBe(0);
    expect(invariant(false, "x")).toBe(false);
    expect(invariant("", "x")).toBe("");
  });

  it("narrows the return type to non-null", () => {
    const maybe: string | undefined = "abc";
    const narrowed: string = invariant(maybe, "maybe");
    expect(narrowed).toBe("abc");
  });

  it("throws when value is undefined", () => {
    expect(() => invariant(undefined, "missingArg")).toThrow(
      /Invariant failed: missingArg must be defined/,
    );
  });

  it("throws when value is null", () => {
    expect(() => invariant(null, "nullArg")).toThrow(
      /Invariant failed: nullArg must be defined/,
    );
  });

  it("includes the name in the error message", () => {
    try {
      invariant(undefined, "widgetId");
      expect.fail("should have thrown");
    } catch (err) {
      expect((err as Error).message).toContain("widgetId");
    }
  });
});
