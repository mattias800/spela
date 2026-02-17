import { CheckCircle, Timer, Shield } from "lucide-react";
import type { ChallengeType } from "@/types/api";

const typeConfig: Record<
  ChallengeType,
  { icon: typeof CheckCircle; label: string }
> = {
  completion: { icon: CheckCircle, label: "Completion" },
  speedrun: { icon: Timer, label: "Speedrun" },
  survival: { icon: Shield, label: "Survival" },
};

interface ChallengeTypeIconProps {
  type: ChallengeType;
  className?: string;
  showLabel?: boolean;
}

export function ChallengeTypeIcon({
  type,
  className,
  showLabel,
}: ChallengeTypeIconProps) {
  const config = typeConfig[type];
  const Icon = config.icon;

  if (showLabel) {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs text-surface-400">
        <Icon className={className ?? "h-3.5 w-3.5"} />
        {config.label}
      </span>
    );
  }

  return <Icon className={className ?? "h-4 w-4 text-surface-400"} />;
}
