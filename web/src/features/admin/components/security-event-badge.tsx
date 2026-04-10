import {
  CheckCircle,
  XCircle,
  Lock,
  ShieldOff,
  AlertTriangle,
  UserX,
  KeyRound,
  type LucideIcon,
} from "lucide-react";
import { Badge } from "@/components/ui";
import type { SecurityEventType, SecurityEventTypeLike } from "@/types/api";

type Variant = "default" | "brand" | "success" | "warning" | "danger";

interface EventMeta {
  label: string;
  variant: Variant;
  icon: LucideIcon;
  /** Severity for sorting filter chips and visually weighting rows. */
  severity: "info" | "notice" | "alert";
}

// Event type → presentation metadata. Keep the catalog in one place so the
// filter chips and the row badge stay in sync.
export const SECURITY_EVENT_META: Record<SecurityEventType, EventMeta> = {
  login_success: {
    label: "Login success",
    variant: "success",
    icon: CheckCircle,
    severity: "info",
  },
  login_failed: {
    label: "Login failed",
    variant: "warning",
    icon: XCircle,
    severity: "notice",
  },
  login_locked: {
    label: "Login on locked account",
    variant: "warning",
    icon: Lock,
    severity: "notice",
  },
  login_blocked: {
    label: "Login blocked",
    variant: "warning",
    icon: ShieldOff,
    severity: "notice",
  },
  account_locked: {
    label: "Account locked",
    variant: "danger",
    icon: Lock,
    severity: "alert",
  },
  revoked_token_used: {
    label: "Revoked token used",
    variant: "danger",
    icon: AlertTriangle,
    severity: "alert",
  },
  disabled_account_token: {
    label: "Disabled account token",
    variant: "danger",
    icon: UserX,
    severity: "alert",
  },
  token_user_missing: {
    label: "Token for missing user",
    variant: "danger",
    icon: UserX,
    severity: "alert",
  },
  stale_token_version: {
    label: "Stale token version",
    variant: "warning",
    icon: KeyRound,
    severity: "notice",
  },
};

// The prop accepts `SecurityEventTypeLike` so this component is a real
// defensive path when the backend ships a new event type before the UI
// catalog is updated. The `(string & {})` trick in the type preserves
// autocomplete for the known union while still accepting arbitrary
// strings — unknown types get a generic amber warning badge with the
// raw type string as the label instead of crashing the row renderer.
interface SecurityEventBadgeProps {
  type: SecurityEventTypeLike;
}

const UNKNOWN_EVENT_META: EventMeta = {
  label: "Unknown event",
  variant: "default",
  icon: AlertTriangle,
  severity: "notice",
};

/**
 * getSecurityEventLabel returns a human-readable label for an event type,
 * falling back to the raw type string when the catalog doesn't know it.
 * Exported so the detail modal title and the badge agree on the fallback.
 */
export function getSecurityEventLabel(type: SecurityEventTypeLike): string {
  const meta = (SECURITY_EVENT_META as Record<string, EventMeta>)[type];
  return meta?.label ?? type;
}

export function SecurityEventBadge({ type }: SecurityEventBadgeProps) {
  const meta =
    (SECURITY_EVENT_META as Record<string, EventMeta>)[type] ??
    UNKNOWN_EVENT_META;
  const Icon = meta.icon;
  const label = getSecurityEventLabel(type);
  return (
    <Badge variant={meta.variant}>
      <Icon aria-hidden="true" className="h-3 w-3 mr-1" />
      {label}
    </Badge>
  );
}
