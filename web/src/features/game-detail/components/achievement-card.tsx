import { Trophy } from "lucide-react";
import { cn } from "@/lib/cn";
import type {
  Achievement,
  GameAchievementProgress as AchievementProgress,
} from "@/types/api";

type RarityTier = "Common" | "Uncommon" | "Rare" | "Ultra Rare" | "Legendary";

interface RarityInfo {
  tier: RarityTier;
  borderClass: string;
  pillClass: string | null;
}

function getRarityInfo(rarityPercent: number): RarityInfo {
  if (rarityPercent < 1) {
    return {
      tier: "Legendary",
      borderClass: "border-purple-400 ring-1 ring-purple-500/50",
      pillClass: "bg-purple-900/60 text-purple-300 border border-purple-500",
    };
  }
  if (rarityPercent < 5) {
    return {
      tier: "Ultra Rare",
      borderClass: "border-purple-500",
      pillClass: "bg-purple-900/60 text-purple-300 border border-purple-500",
    };
  }
  if (rarityPercent < 20) {
    return {
      tier: "Rare",
      borderClass: "border-yellow-400",
      pillClass: "bg-yellow-900/60 text-yellow-300 border border-yellow-500",
    };
  }
  if (rarityPercent <= 50) {
    return {
      tier: "Uncommon",
      borderClass: "border-gray-400",
      pillClass: null,
    };
  }
  return {
    tier: "Common",
    borderClass: "border-surface-700",
    pillClass: null,
  };
}

interface AchievementCardProps {
  achievement: Achievement;
  unlocked: AchievementProgress | undefined;
}

export function AchievementCard({
  achievement,
  unlocked,
}: AchievementCardProps) {
  const rarity = getRarityInfo(achievement.rarityPercent);
  const showRarityDetails =
    unlocked && rarity.tier !== "Common" && rarity.tier !== "Uncommon";
  const showRarityPercent = unlocked && rarity.tier !== "Common";

  return (
    <div
      className={cn(
        "flex items-start gap-3 rounded-lg border p-3",
        unlocked
          ? rarity.borderClass
          : "border-surface-800 bg-surface-900 opacity-50",
        unlocked && "bg-surface-800",
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
          {showRarityDetails && rarity.pillClass && (
            <span
              className={cn(
                "flex-shrink-0 rounded-full px-1.5 py-0.5 text-xs font-medium",
                rarity.pillClass,
              )}
            >
              {rarity.tier}
            </span>
          )}
        </div>
        <p className="text-xs text-surface-400 line-clamp-2">
          {achievement.description}
        </p>
        <div className="flex items-center gap-2 mt-1">
          <p className="text-xs text-surface-500">{achievement.points} pts</p>
          {showRarityPercent && (
            <p className="text-xs text-surface-400">
              {achievement.rarityPercent.toFixed(1)}% of players
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
