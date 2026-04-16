import { useState, useEffect } from "react";

/**
 * Hook that animates a number from 0 to a target value using an ease-out curve.
 * Respects `prefers-reduced-motion` — skips animation and returns the final value immediately.
 * Re-animates when the target changes. Cleans up on unmount.
 */
export function useAnimatedCounter(
  target: number,
  durationMs = 400,
): number {
  const [value, setValue] = useState(0);

  useEffect(() => {
    const prefersReducedMotion = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    ).matches;

    if (prefersReducedMotion || target === 0) {
      setValue(target);
      return;
    }

    let startTime: number | null = null;
    let frameId: number;

    function tick(now: number) {
      // Capture startTime from the rAF callback's own timestamp so we don't
      // mix it with performance.now() — in jsdom the two can have different
      // time origins (e.g. when vitest's threads pool runs many tests in the
      // same process, performance.now() drifts arbitrarily ahead of the
      // window's animation clock, producing negative elapsed values).
      if (startTime === null) {
        startTime = now;
      }
      const elapsed = now - startTime;
      const progress = Math.min(elapsed / durationMs, 1);
      // Ease-out: 1 - (1 - t)^3
      const eased = 1 - Math.pow(1 - progress, 3);
      const current = eased * target;

      if (Number.isInteger(target)) {
        setValue(Math.round(current));
      } else {
        setValue(Math.round(current * 10) / 10);
      }

      if (progress < 1) {
        frameId = requestAnimationFrame(tick);
      } else {
        setValue(target);
      }
    }

    frameId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frameId);
  }, [target, durationMs]);

  return value;
}
