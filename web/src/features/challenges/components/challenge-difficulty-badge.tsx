import { Badge } from "@/components/ui";
import type { ChallengeDifficulty } from "@/types/api";

const difficultyConfig: Record<
  ChallengeDifficulty,
  { variant: "success" | "warning" | "danger"; label: string }
> = {
  easy: { variant: "success", label: "Easy" },
  medium: { variant: "warning", label: "Medium" },
  hard: { variant: "danger", label: "Hard" },
};

interface ChallengeDifficultyBadgeProps {
  difficulty: ChallengeDifficulty;
}

export function ChallengeDifficultyBadge({
  difficulty,
}: ChallengeDifficultyBadgeProps) {
  const config = difficultyConfig[difficulty];
  return <Badge variant={config.variant}>{config.label}</Badge>;
}
