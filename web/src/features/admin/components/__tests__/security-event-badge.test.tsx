import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import {
  SecurityEventBadge,
  getSecurityEventLabel,
} from "../security-event-badge";

describe("SecurityEventBadge", () => {
  it("renders a known event type with its humanized label", () => {
    render(<SecurityEventBadge type="login_failed" />);
    expect(screen.getByText("Login failed")).toBeInTheDocument();
  });

  it("falls back to the raw type string for unknown events", () => {
    // Simulate the backend shipping a new event type before the UI catalog
    // is updated. The `SecurityEventTypeLike` prop type accepts any string
    // via the `(string & {})` branded trick, so no cast is needed here.
    render(<SecurityEventBadge type="brand_new_event" />);
    expect(screen.getByText("brand_new_event")).toBeInTheDocument();
  });

  it("uses the danger variant for alert-severity events", () => {
    const { container } = render(
      <SecurityEventBadge type="account_locked" />,
    );
    const badge = container.querySelector('[data-comp="Badge"]');
    expect(badge).not.toBeNull();
    // toHaveClass asserts a specific utility class rather than matching a
    // substring — a brittle `className.toContain("danger")` would also
    // pass against `data-danger-level` or a "danger-outline" rename.
    expect(badge).toHaveClass("text-danger-500");
  });
});

describe("getSecurityEventLabel", () => {
  it("returns the humanized label for known types", () => {
    expect(getSecurityEventLabel("login_failed")).toBe("Login failed");
    expect(getSecurityEventLabel("account_locked")).toBe("Account locked");
  });

  it("returns the raw string for unknown types so the detail modal title agrees with the badge", () => {
    expect(getSecurityEventLabel("brand_new_event")).toBe("brand_new_event");
  });
});
