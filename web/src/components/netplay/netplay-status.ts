import type { NetplaySession } from "@/types/api";

export const netplayStatusVariant: Record<
  NetplaySession["status"],
  "success" | "warning" | "default"
> = {
  waiting: "warning",
  in_progress: "success",
  ended: "default",
};

export const netplayStatusLabel: Record<NetplaySession["status"], string> = {
  waiting: "Waiting for player",
  in_progress: "In Progress",
  ended: "Ended",
};

export const endReasonLabel: Record<
  NonNullable<NetplaySession["endReason"]>,
  string
> = {
  host_left: "Host left",
  client_left: "Client left",
  timeout: "Expired",
  completed: "Completed",
};
