import type { Relay } from "@/types/api";

export const relayStatusVariant: Record<
  Relay["status"],
  "success" | "warning" | "default"
> = {
  active: "success",
  paused: "warning",
  completed: "default",
};
