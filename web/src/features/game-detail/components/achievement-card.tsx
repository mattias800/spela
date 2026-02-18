import { Trophy } from "lucide-react";
import { cn } from "@/lib/cn";
import type {
  Achievement,
  GameAchievementProgress as AchievementProgress,
} from "@/types/api";

interface AchievementCardProps {
  achievement: Achievement;
  unlocked: AchievementProgress | undefined;
}

export function AchievementCard({
  achievement,
  unlocked,
}: AchievementCardProps) {
  return (
    <div
      className={cn(
        "flex items-start gap-3 rounded-lg border p-3",
        unlocked
          ? "border-surface-700 bg-surface-800"
          : "border-surface-800 bg-surface-900 opacity-50",
      )}
      data-testid={`achievement-${achievement.id}`}
    >
      <img
        src={achievement.badgeUrl}
        alt={achievement.title}
        className="h-12 w-12 flex-shrink-0 rounded"
      />
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium text-surface-100 truncate">
            {achievement.title}
          </p>
          {unlocked && (
            <Trophy className="h-3.5 w-3.5 flex-shrink-0 text-amber-400" />
          )}
        </div>
        <p className="text-xs text-surface-400 line-clamp-2">
          {achievement.description}
        </p>
        <p className="text-xs text-surface-500 mt-1">
          {achievement.points} pts
        </p>
      </div>
    </div>
  );
}
