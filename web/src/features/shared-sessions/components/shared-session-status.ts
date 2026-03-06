import type { SharedSession } from "@/types/api";

export const sharedSessionStatusVariant: Record<
  SharedSession["status"],
  "success" | "warning" | "default"
> = {
  active: "success",
  paused: "warning",
  completed: "default",
};
