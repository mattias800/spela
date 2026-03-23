import { useState } from "react";
import { Trophy, Pencil } from "lucide-react";
import { cn } from "@/lib/cn";
import { useAuth } from "@/hooks/use-auth";
import { usePublicShowcase } from "@/hooks/use-showcase";
import type { ShowcaseAchievement } from "@/types/api";
import { ShowcaseEditor } from "./showcase-editor";

type RarityTier = "Common" | "Uncommon" | "Rare" | "Ultra Rare" | "Legendary";

function getRarityBorder(rarityPercent: number): {
  tier: RarityTier;
  borderClass: string;
} {
  if (rarityPercent < 1) {
    return {
      tier: "Legendary",
      borderClass: "border-purple-400 ring-1 ring-purple-500/50",
    };
  }
  if (rarityPercent < 5) {
    return { tier: "Ultra Rare", borderClass: "border-purple-500" };
  }
  if (rarityPercent < 20) {
    return { tier: "Rare", borderClass: "border-yellow-400" };
  }
  if (rarityPercent <= 50) {
    return { tier: "Uncommon", borderClass: "border-gray-400" };
  }
  return { tier: "Common", borderClass: "border-surface-700" };
}

function getRarityPillClass(tier: RarityTier): string | null {
  switch (tier) {
    case "Legendary":
    case "Ultra Rare":
      return "bg-purple-900/60 text-purple-300 border border-purple-500";
    case "Rare":
      return "bg-yellow-900/60 text-yellow-300 border border-yellow-500";
    default:
      return null;
  }
}

function ShowcaseCard({
  achievement,
}: {
  achievement: ShowcaseAchievement;
}) {
  const rarity = getRarityBorder(achievement.rarityPercent ?? 100);
  const pillClass = getRarityPillClass(rarity.tier);

  return (
    <div className="flex flex-col items-center gap-2 w-36 flex-shrink-0">
      {/* Badge */}
      <div
        className={cn(
          "w-16 h-16 rounded-lg border-2 overflow-hidden bg-surface-800",
          rarity.borderClass,
        )}
      >
        {achievement.badgeUrl ? (
          <img
            src={achievement.badgeUrl}
            alt={achievement.title ?? "Achievement"}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <Trophy className="h-6 w-6 text-surface-500" />
          </div>
        )}
      </div>

      {/* Title */}
      <p className="text-sm font-medium text-surface-200 text-center line-clamp-2 leading-tight">
        {achievement.title || "Unknown"}
      </p>

      {/* Game title */}
      <p className="text-xs text-surface-500 text-center line-clamp-1 -mt-1">
        {achievement.gameTitle || ""}
      </p>

      {/* Rarity + points */}
      <div className="flex items-center gap-1.5">
        {achievement.points != null && achievement.points > 0 && (
          <span className="text-xs text-surface-400">
            {achievement.points} pts
          </span>
        )}
        {pillClass && (
          <span
            className={cn("text-[10px] px-1.5 py-0.5 rounded-full", pillClass)}
          >
            {rarity.tier}
          </span>
        )}
      </div>
    </div>
  );
}

export function AchievementShowcase({ userId }: { userId: string }) {
  const { user } = useAuth();
  const { data: showcase, isLoading } = usePublicShowcase(userId);
  const [isEditorOpen, setIsEditorOpen] = useState(false);

  const isOwnProfile = user?.id?.toString() === userId;
  const hasShowcase = showcase && showcase.length > 0;

  if (isLoading) return null;
  if (!hasShowcase && !isOwnProfile) return null;

  return (
    <section>
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2.5">
          <Trophy className="h-5 w-5 text-brand-400" />
          <h2 className="text-lg font-bold text-surface-100">
            Featured Achievements
          </h2>
        </div>
        {isOwnProfile && (
          <button
            onClick={() => setIsEditorOpen(true)}
            className="flex items-center gap-1.5 text-sm text-surface-400 hover:text-brand-400 transition-colors"
          >
            <Pencil className="h-3.5 w-3.5" />
            Edit
          </button>
        )}
      </div>

      {hasShowcase ? (
        <div className="flex gap-4 overflow-x-auto pb-2">
          {showcase.map((a) => (
            <ShowcaseCard key={a.achievementRaId} achievement={a} />
          ))}
        </div>
      ) : (
        <p className="text-sm text-surface-500">
          Pin your proudest achievements here
        </p>
      )}

      {isEditorOpen && (
        <ShowcaseEditor onClose={() => setIsEditorOpen(false)} />
      )}
    </section>
  );
}
