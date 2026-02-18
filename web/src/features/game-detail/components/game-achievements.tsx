import { useState } from "react";
import { AlertTriangle, Trophy, LayoutGrid, Clock } from "lucide-react";
import {
  Card,
  CardHeader,
  CardContent,
  Skeleton,
  Badge,
} from "@/components/ui";
import {
  useGameAchievements,
  useGameAchievementProgress,
  useAchievementTimeline,
} from "@/hooks/use-retroachievements";
import { cn } from "@/lib/cn";
import { AchievementCard } from "@/features/game-detail/components/achievement-card";
import { TimelineView } from "@/features/game-detail/components/achievement-timeline";

type ViewMode = "grid" | "timeline";

const VIEW_MODE_KEY = "spela-achievements-view";

function getStoredViewMode(): ViewMode {
  try {
    const stored = localStorage.getItem(VIEW_MODE_KEY);
    if (stored === "grid" || stored === "timeline") return stored;
  } catch {
    // localStorage may be unavailable
  }
  return "grid";
}

interface GameAchievementsProps {
  gameId: string;
}

export function GameAchievements({ gameId }: GameAchievementsProps) {
  const [viewMode, setViewMode] = useState<ViewMode>(getStoredViewMode);
  const { data: achievements, isLoading: achievementsLoading } =
    useGameAchievements(gameId);
  const { data: progress, isLoading: progressLoading } =
    useGameAchievementProgress(gameId);
  const { data: timeline, isLoading: timelineLoading } =
    useAchievementTimeline(gameId);

  const isLoading = achievementsLoading || progressLoading;

  function handleViewChange(mode: ViewMode) {
    setViewMode(mode);
    try {
      localStorage.setItem(VIEW_MODE_KEY, mode);
    } catch {
      // localStorage may be unavailable
    }
  }

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-surface-100">
            Achievements
          </h2>
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

  if (!achievements || !achievements.achievements || achievements.achievements.length === 0) {
    return null;
  }

  const progressMap = new Map(
    (progress ?? []).map((p) => [p.achievementId, p]),
  );

  const unlockedCount = progressMap.size;
  const unlockedPoints = achievements.achievements
    .filter((a) => progressMap.has(a.id))
    .reduce((sum, a) => sum + a.points, 0);

  const completionPct =
    achievements.totalCount > 0
      ? Math.round((unlockedCount / achievements.totalCount) * 100)
      : 0;
  const isComplete = completionPct === 100 && unlockedCount > 0;

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-surface-100">
            Achievements
          </h2>
          <div className="flex items-center gap-3">
            <p
              className="text-sm text-surface-400"
              data-testid="achievement-progress-summary"
            >
              {unlockedCount} of {achievements.totalCount} achievements unlocked
              ({unlockedPoints} points)
            </p>
            <ViewToggle value={viewMode} onChange={handleViewChange} />
          </div>
        </div>

        {/* Progress bar */}
        <div className="mt-3 flex items-center gap-3">
          <div className="flex-1 h-2 rounded-full bg-surface-800 overflow-hidden">
            <div
              className={cn(
                "h-full rounded-full transition-all duration-700 ease-out",
                isComplete ? "bg-amber-400" : "bg-brand-500",
              )}
              style={{ width: `${completionPct}%` }}
              data-testid="achievement-progress-bar"
            />
          </div>
          <span
            className={cn(
              "text-xs font-mono",
              isComplete ? "text-amber-400" : "text-brand-400",
            )}
          >
            {completionPct}%
          </span>
          {isComplete && (
            <Badge variant="warning">
              <Trophy className="h-3 w-3 mr-1" />
              Complete!
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent>
        <div
          className="flex items-start gap-3 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 mb-6"
          data-testid="browser-warning-banner"
        >
          <AlertTriangle className="h-5 w-5 flex-shrink-0 text-amber-400 mt-0.5" />
          <p className="text-sm text-amber-200">
            Achievements are available for this game but require the Spela
            Player app. Browser emulation does not support RetroAchievements.
          </p>
        </div>

        {viewMode === "grid" ? (
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
        ) : (
          <TimelineView
            timeline={timeline}
            isLoading={timelineLoading}
            achievements={achievements.achievements}
            progressMap={progressMap}
            totalAchievements={achievements.totalCount}
          />
        )}
      </CardContent>
    </Card>
  );
}

function ViewToggle({
  value,
  onChange,
}: {
  value: ViewMode;
  onChange: (mode: ViewMode) => void;
}) {
  return (
    <div
      className="flex rounded-full bg-surface-800 p-0.5"
      data-testid="view-toggle"
    >
      <button
        onClick={() => onChange("grid")}
        className={cn(
          "flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors",
          value === "grid"
            ? "bg-brand-500/15 text-brand-400"
            : "text-surface-400 hover:text-surface-200",
        )}
        aria-label="Grid view"
      >
        <LayoutGrid className="h-3.5 w-3.5" />
        Grid
      </button>
      <button
        onClick={() => onChange("timeline")}
        className={cn(
          "flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors",
          value === "timeline"
            ? "bg-brand-500/15 text-brand-400"
            : "text-surface-400 hover:text-surface-200",
        )}
        aria-label="Timeline view"
      >
        <Clock className="h-3.5 w-3.5" />
        Timeline
      </button>
    </div>
  );
}
