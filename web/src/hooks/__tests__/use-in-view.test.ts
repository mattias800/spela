import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useInView } from "../use-in-view";

let observerInstances: Array<{
  callback: IntersectionObserverCallback;
  options?: IntersectionObserverInit;
  observe: ReturnType<typeof vi.fn>;
  disconnect: ReturnType<typeof vi.fn>;
}>;

beforeEach(() => {
  observerInstances = [];

  (globalThis as Record<string, unknown>).IntersectionObserver = class MockIntersectionObserver {
    callback: IntersectionObserverCallback;
    options?: IntersectionObserverInit;
    observe = vi.fn();
    disconnect = vi.fn();
    unobserve = vi.fn();
    root = null;
    rootMargin: string;
    thresholds: number[] = [];
    takeRecords = vi.fn(() => [] as IntersectionObserverEntry[]);

    constructor(callback: IntersectionObserverCallback, options?: IntersectionObserverInit) {
      this.callback = callback;
      this.options = options;
      this.rootMargin = options?.rootMargin ?? "";
      observerInstances.push(this);
    }
  } as unknown as typeof IntersectionObserver;
});

function triggerIntersection(index: number, isIntersecting: boolean) {
  const observer = observerInstances[index];
  observer.callback(
    [{ isIntersecting } as IntersectionObserverEntry],
    {} as IntersectionObserver,
  );
}

describe("useInView", () => {
  it("returns isInView=false initially", () => {
    const { result } = renderHook(() => useInView());
    expect(result.current.isInView).toBe(false);
  });

  it("observes the element when ref is attached", () => {
    const { result } = renderHook(() => useInView());

    const node = document.createElement("div");
    act(() => {
      result.current.ref(node);
    });

    expect(observerInstances).toHaveLength(1);
    expect(observerInstances[0].observe).toHaveBeenCalledWith(node);
  });

  it("sets isInView=true when element intersects", () => {
    const { result } = renderHook(() => useInView());

    const node = document.createElement("div");
    act(() => {
      result.current.ref(node);
    });

    act(() => {
      triggerIntersection(0, true);
    });

    expect(result.current.isInView).toBe(true);
  });

  it("disconnects after first intersection (one-shot)", () => {
    const { result } = renderHook(() => useInView());

    const node = document.createElement("div");
    act(() => {
      result.current.ref(node);
    });

    act(() => {
      triggerIntersection(0, true);
    });

    expect(observerInstances[0].disconnect).toHaveBeenCalled();
  });

  it("does not set isInView when entry is not intersecting", () => {
    const { result } = renderHook(() => useInView());

    const node = document.createElement("div");
    act(() => {
      result.current.ref(node);
    });

    act(() => {
      triggerIntersection(0, false);
    });

    expect(result.current.isInView).toBe(false);
    expect(observerInstances[0].disconnect).not.toHaveBeenCalled();
  });

  it("uses rootMargin from options", () => {
    const { result } = renderHook(() => useInView({ rootMargin: "100px" }));

    const node = document.createElement("div");
    act(() => {
      result.current.ref(node);
    });

    expect(observerInstances[0].options?.rootMargin).toBe("100px");
  });

  it("uses default rootMargin of 200px", () => {
    const { result } = renderHook(() => useInView());

    const node = document.createElement("div");
    act(() => {
      result.current.ref(node);
    });

    expect(observerInstances[0].options?.rootMargin).toBe("200px");
  });

  it("disconnects old observer when ref changes to new node", () => {
    const { result } = renderHook(() => useInView());

    const node1 = document.createElement("div");
    act(() => {
      result.current.ref(node1);
    });

    const firstObserver = observerInstances[0];

    const node2 = document.createElement("div");
    act(() => {
      result.current.ref(node2);
    });

    expect(firstObserver.disconnect).toHaveBeenCalled();
    expect(observerInstances).toHaveLength(2);
    expect(observerInstances[1].observe).toHaveBeenCalledWith(node2);
  });
});
