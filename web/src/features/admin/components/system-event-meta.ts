import {
  AlertTriangle,
  CheckCircle,
  Cpu,
  Download,
  FileWarning,
  KeyRound,
  KeySquare,
  Lock,
  RefreshCw,
  ShieldOff,
  UserX,
  XCircle,
  type LucideIcon,
} from "lucide-react";
import type { SystemEventType, SystemEventTypeLike } from "@/types/api";

type Variant = "default" | "brand" | "success" | "warning" | "danger";

export interface EventMeta {
  label: string;
  variant: Variant;
  icon: LucideIcon;
  severity: "info" | "notice" | "alert";
}

export const SYSTEM_EVENT_META: Record<SystemEventType, EventMeta> = {
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
  ra_circuit_breaker_tripped: {
    label: "RA Circuit Breaker",
    variant: "danger",
    icon: AlertTriangle,
    severity: "alert",
  },
  scraper_repeated_errors: {
    label: "Scraper Errors",
    variant: "warning",
    icon: Cpu,
    severity: "notice",
  },
  rom_file_missing: {
    label: "ROM Missing",
    variant: "warning",
    icon: FileWarning,
    severity: "notice",
  },
  api_credentials_invalid: {
    label: "Invalid Credentials",
    variant: "danger",
    icon: KeySquare,
    severity: "alert",
  },
  emulatorjs_load_failed: {
    label: "Emulator Load Failed",
    variant: "danger",
    icon: AlertTriangle,
    severity: "alert",
  },
  core_updated: {
    label: "Core Updated",
    variant: "default",
    icon: RefreshCw,
    severity: "info",
  },
  core_update_failed: {
    label: "Core Update Failed",
    variant: "warning",
    icon: RefreshCw,
    severity: "notice",
  },
  bios_download_failed: {
    label: "BIOS Download Failed",
    variant: "warning",
    icon: Download,
    severity: "notice",
  },
};

export const UNKNOWN_EVENT_META: EventMeta = {
  label: "Unknown event",
  variant: "default",
  icon: AlertTriangle,
  severity: "notice",
};

export function getSystemEventMeta(
  type: SystemEventTypeLike,
): EventMeta | undefined {
  return (SYSTEM_EVENT_META as Record<string, EventMeta>)[type];
}

export function getSystemEventLabel(type: SystemEventTypeLike): string {
  return getSystemEventMeta(type)?.label ?? type;
}
