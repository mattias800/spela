import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useAnimatedCounter } from "@/hooks/use-animated-counter";

function mockMatchMedia(prefersReducedMotion: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches:
        prefersReducedMotion &&
        query === "(prefers-reduced-motion: reduce)",
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

describe("useAnimatedCounter", () => {
  beforeEach(() => {
    mockMatchMedia(false);
  });

  it("returns the target value when target is 0", () => {
    const { result } = renderHook(() => useAnimatedCounter(0));
    expect(result.current).toBe(0);
  });

  it("respects prefers-reduced-motion by returning target immediately", () => {
    mockMatchMedia(true);
    const { result } = renderHook(() => useAnimatedCounter(42));
    expect(result.current).toBe(42);
  });

  it("respects prefers-reduced-motion for decimal values", () => {
    mockMatchMedia(true);
    const { result } = renderHook(() => useAnimatedCounter(88.5));
    expect(result.current).toBe(88.5);
  });

  it("starts at 0 for non-zero targets when animation is enabled", () => {
    const { result } = renderHook(() => useAnimatedCounter(100, 400));
    // The initial render should start at 0 (before any rAF tick)
    expect(result.current).toBe(0);
  });

  it("eventually reaches the target value", async () => {
    const { result } = renderHook(() => useAnimatedCounter(100, 50));

    await waitFor(
      () => {
        expect(result.current).toBe(100);
      },
      { timeout: 1000 },
    );
  });

  it("eventually reaches decimal target value", async () => {
    const { result } = renderHook(() => useAnimatedCounter(88.5, 50));

    await waitFor(
      () => {
        expect(result.current).toBe(88.5);
      },
      { timeout: 1000 },
    );
  });

  it("only animates once per mount (does not re-animate on rerender)", async () => {
    const { result, rerender } = renderHook(
      ({ target }) => useAnimatedCounter(target, 50),
      { initialProps: { target: 50 } },
    );

    // Wait for animation to complete
    await waitFor(
      () => {
        expect(result.current).toBe(50);
      },
      { timeout: 1000 },
    );

    // Rerender with a new target — the hook should NOT re-animate
    // because hasAnimated.current is already true
    rerender({ target: 100 });

    // The value should stay at 50
    expect(result.current).toBe(50);
  });
});
