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
import type { SecurityEventType } from "@/types/api";

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

interface SecurityEventBadgeProps {
  type: SecurityEventType;
}

export function SecurityEventBadge({ type }: SecurityEventBadgeProps) {
  const meta = SECURITY_EVENT_META[type] ?? {
    label: type,
    variant: "default" as Variant,
    icon: AlertTriangle,
    severity: "notice" as const,
  };
  const Icon = meta.icon;
  return (
    <Badge variant={meta.variant}>
      <Icon className="h-3 w-3 mr-1" />
      {meta.label}
    </Badge>
  );
}
