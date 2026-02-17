import { Badge } from "@/components/ui";
import type { ChallengeStatus } from "@/types/api";

const statusConfig: Record<
  ChallengeStatus,
  { variant: "success" | "warning" | "default"; label: string }
> = {
  active: { variant: "success", label: "Active" },
  closed: { variant: "default", label: "Closed" },
  expired: { variant: "warning", label: "Expired" },
};

interface ChallengeStatusBadgeProps {
  status: ChallengeStatus;
}

export function ChallengeStatusBadge({ status }: ChallengeStatusBadgeProps) {
  const config = statusConfig[status];
  return <Badge variant={config.variant}>{config.label}</Badge>;
}
