import { AlertTriangle, Trophy } from "lucide-react";
import { Card, CardHeader, CardContent, Skeleton } from "@/components/ui";
import { useGameAchievements, useGameAchievementProgress } from "@/hooks/use-retroachievements";
import type { Achievement, GameAchievementProgress as AchievementProgress } from "@/types/api";

interface GameAchievementsProps {
  gameId: string;
}

export function GameAchievements({ gameId }: GameAchievementsProps) {
  const { data: achievements, isLoading: achievementsLoading } = useGameAchievements(gameId);
  const { data: progress, isLoading: progressLoading } = useGameAchievementProgress(gameId);

  const isLoading = achievementsLoading || progressLoading;

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-surface-100">Achievements</h2>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} className="h-20 w-full rounded-lg" />
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!achievements || achievements.achievements.length === 0) {
    return null;
  }

  const progressMap = new Map(
    (progress ?? []).map((p) => [p.achievementId, p]),
  );

  const unlockedCount = progressMap.size;
  const unlockedPoints = achievements.achievements
    .filter((a) => progressMap.has(a.id))
    .reduce((sum, a) => sum + a.points, 0);

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-surface-100">Achievements</h2>
          <p className="text-sm text-surface-400" data-testid="achievement-progress-summary">
            {unlockedCount} of {achievements.totalCount} achievements unlocked ({unlockedPoints} points)
          </p>
        </div>
      </CardHeader>
      <CardContent>
        <div
          className="flex items-start gap-3 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 mb-6"
          data-testid="browser-warning-banner"
        >
          <AlertTriangle className="h-5 w-5 flex-shrink-0 text-amber-400 mt-0.5" />
          <p className="text-sm text-amber-200">
            Achievements are available for this game but require the Spela Player app. Browser emulation does not support RetroAchievements.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {achievements.achievements.map((achievement) => {
            const unlocked = progressMap.get(achievement.id);
            return (
              <AchievementCard
                key={achievement.id}
                achievement={achievement}
                unlocked={unlocked}
              />
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}

function AchievementCard({
  achievement,
  unlocked,
}: {
  achievement: Achievement;
  unlocked: AchievementProgress | undefined;
}) {
  return (
    <div
      className={`flex items-start gap-3 rounded-lg border p-3 ${
        unlocked
          ? "border-surface-700 bg-surface-800"
          : "border-surface-800 bg-surface-900 opacity-50"
      }`}
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
        <p className="text-xs text-surface-500 mt-1">{achievement.points} pts</p>
      </div>
    </div>
  );
}
