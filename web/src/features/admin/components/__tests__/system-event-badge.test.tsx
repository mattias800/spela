import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { SystemEventBadge } from "../system-event-badge";
import { getSystemEventLabel, getSystemEventMeta } from "../system-event-meta";

describe("SystemEventBadge", () => {
  it("renders a known event type with its humanized label", () => {
    render(<SystemEventBadge type="login_failed" />);
    expect(screen.getByText("Login failed")).toBeInTheDocument();
  });

  it("falls back to the raw type string for unknown events", () => {
    render(<SystemEventBadge type="brand_new_event" />);
    expect(screen.getByText("brand_new_event")).toBeInTheDocument();
  });

  it("uses the danger variant for alert-severity events", () => {
    const { container } = render(
      <SystemEventBadge type="account_locked" />,
    );
    const badge = container.querySelector('[data-comp="Badge"]');
    expect(badge).not.toBeNull();
    expect(badge).toHaveClass("text-danger-500");
  });

  it("renders operational event types", () => {
    render(<SystemEventBadge type="ra_circuit_breaker_tripped" />);
    expect(screen.getByText("RA Circuit Breaker")).toBeInTheDocument();
  });

  it("renders core_update_failed with a clear failure label (#1667)", () => {
    render(<SystemEventBadge type="core_update_failed" />);
    expect(screen.getByText("Core Update Failed")).toBeInTheDocument();
  });
});

describe("getSystemEventLabel", () => {
  it("returns the humanized label for known types", () => {
    expect(getSystemEventLabel("login_failed")).toBe("Login failed");
    expect(getSystemEventLabel("account_locked")).toBe("Account locked");
  });

  it("returns the raw string for unknown types so the detail modal title agrees with the badge", () => {
    expect(getSystemEventLabel("brand_new_event")).toBe("brand_new_event");
  });

  it("returns labels for operational event types", () => {
    expect(getSystemEventLabel("ra_circuit_breaker_tripped")).toBe("RA Circuit Breaker");
    expect(getSystemEventLabel("rom_file_missing")).toBe("ROM Missing");
  });

  it("distinguishes core_update_failed from core_updated (#1667)", () => {
    expect(getSystemEventLabel("core_updated")).toBe("Core Updated");
    expect(getSystemEventLabel("core_update_failed")).toBe("Core Update Failed");
  });
});

describe("getSystemEventMeta core_update_failed (#1667)", () => {
  it("is a first-class operational failure entry, not the unknown fallback", () => {
    const meta = getSystemEventMeta("core_update_failed");
    expect(meta).toBeDefined();
    expect(meta?.label).toBe("Core Update Failed");
    expect(meta?.variant).toBe("warning");
    expect(meta?.severity).toBe("notice");
  });
});
