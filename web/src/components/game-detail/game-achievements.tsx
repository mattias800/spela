import { useState } from "react";
import { AlertTriangle, Trophy, LayoutGrid, Clock } from "lucide-react";
import { Card, CardHeader, CardContent, Skeleton, Badge } from "@/components/ui";
import {
  useGameAchievements,
  useGameAchievementProgress,
  useAchievementTimeline,
} from "@/hooks/use-retroachievements";
import { formatRelativeTime, formatPlayTime, formatDate } from "@/lib/format";
import { cn } from "@/lib/cn";
import type {
  Achievement,
  GameAchievementProgress as AchievementProgress,
  AchievementTimelineEntry,
} from "@/types/api";

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
            Player app. Browser emulation does not support
            RetroAchievements.
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

function TimelineView({
  timeline,
  isLoading,
  achievements,
  progressMap,
  totalAchievements,
}: {
  timeline:
    | { timeline: AchievementTimelineEntry[]; unlockedCount: number; earnedPoints: number }
    | undefined;
  isLoading: boolean;
  achievements: Achievement[];
  progressMap: Map<number, AchievementProgress>;
  totalAchievements: number;
}) {
  if (isLoading) {
    return <TimelineSkeleton />;
  }

  const timelineEntries = timeline?.timeline ?? [];

  // Build mini stat summary
  const fastestUnlock =
    timelineEntries.length > 0
      ? timelineEntries.reduce((min, e) =>
          e.playTimeAtUnlock < min.playTimeAtUnlock ? e : min,
        )
      : null;
  const latestUnlock =
    timelineEntries.length > 0
      ? timelineEntries.reduce((latest, e) =>
          new Date(e.unlockedAt) > new Date(latest.unlockedAt) ? e : latest,
        )
      : null;

  // Group by date
  const grouped = groupByDate(timelineEntries);

  // Find locked achievements (not in timeline)
  const unlockedIds = new Set(timelineEntries.map((e) => e.achievementRaId));
  const lockedAchievements = achievements.filter(
    (a) => !progressMap.has(a.id) && !unlockedIds.has(a.id),
  );

  return (
    <div data-testid="timeline-view">
      {/* Mini stat summary */}
      {timelineEntries.length > 0 && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <StatCard
            value={`${timeline?.unlockedCount ?? timelineEntries.length} / ${totalAchievements}`}
            label="Total achievements"
            highlight
          />
          <StatCard
            value={
              fastestUnlock
                ? formatPlayTime(fastestUnlock.playTimeAtUnlock)
                : "--"
            }
            label="Fastest unlock"
          />
          <StatCard
            value={
              latestUnlock
                ? formatRelativeTime(latestUnlock.unlockedAt)
                : "--"
            }
            label="Latest unlock"
          />
          <StatCard
            value={`${timeline?.earnedPoints ?? 0}`}
            label="Total points earned"
          />
        </div>
      )}

      {/* Timeline */}
      <div className="relative pl-6">
        {/* Vertical line */}
        <div className="absolute left-[5px] top-0 bottom-0 w-0.5 bg-brand-500/30" />

        {grouped.map(([dateLabel, entries]) => (
          <div key={dateLabel} className="mb-6 last:mb-0">
            <p className="text-xs font-semibold uppercase tracking-wider text-surface-500 mb-3 -ml-6 pl-6">
              {dateLabel}
            </p>
            <div className="space-y-3">
              {entries.map((entry) => (
                <TimelineEntryCard
                  key={entry.achievementRaId}
                  entry={entry}
                />
              ))}
            </div>
          </div>
        ))}

        {/* Locked achievements */}
        {lockedAchievements.length > 0 && (
          <div className="mt-6">
            <p className="text-sm text-surface-500 mb-3 -ml-6 pl-6">
              Remaining achievements
            </p>
            <div className="space-y-3">
              {lockedAchievements.map((a) => (
                <LockedTimelineEntry key={a.id} achievement={a} />
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function StatCard({
  value,
  label,
  highlight,
}: {
  value: string;
  label: string;
  highlight?: boolean;
}) {
  return (
    <div className="bg-surface-800/30 rounded-xl p-4">
      <p
        className={cn(
          "text-lg font-bold",
          highlight ? "text-brand-400" : "text-surface-100",
        )}
      >
        {value}
      </p>
      <p className="text-xs text-surface-500 mt-0.5">{label}</p>
    </div>
  );
}

function TimelineEntryCard({ entry }: { entry: AchievementTimelineEntry }) {
  return (
    <div className="relative flex items-start gap-3">
      {/* Node */}
      <div className="absolute -left-6 top-2 h-3 w-3 rounded-full bg-brand-400 border-2 border-surface-900" />

      {/* Card */}
      <div className="flex items-start gap-3 rounded-lg border border-surface-700 bg-surface-800 p-3 flex-1">
        <img
          src={entry.badgeUrl}
          alt={entry.title}
          className="h-10 w-10 flex-shrink-0 rounded"
        />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-medium text-surface-100 truncate">
              {entry.title}
            </p>
            <span className="text-xs text-surface-500">
              {entry.points} pts
            </span>
            {entry.isHardcore && (
              <Badge variant="warning" className="text-[10px] px-1.5 py-0">
                HC
              </Badge>
            )}
          </div>
          <p className="text-xs text-surface-400 line-clamp-2">
            {entry.description}
          </p>
          <div className="flex items-center gap-3 mt-1.5">
            <span
              className="text-xs text-surface-400"
              title={new Date(entry.unlockedAt).toLocaleString()}
            >
              {formatRelativeTime(entry.unlockedAt)}
            </span>
            {entry.playTimeAtUnlock > 0 && (
              <span className="flex items-center gap-1 text-xs font-mono text-surface-400">
                <Clock className="h-3.5 w-3.5 text-surface-500" />
                after {formatPlayTime(entry.playTimeAtUnlock)}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function LockedTimelineEntry({ achievement }: { achievement: Achievement }) {
  return (
    <div className="relative flex items-start gap-3 opacity-40">
      {/* Node */}
      <div className="absolute -left-6 top-2 h-3 w-3 rounded-full bg-surface-700 border-2 border-surface-900" />

      {/* Card */}
      <div className="flex items-start gap-3 rounded-lg border border-surface-800 bg-surface-900 p-3 flex-1">
        <img
          src={achievement.badgeUrl}
          alt={achievement.title}
          className="h-10 w-10 flex-shrink-0 rounded grayscale"
        />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-medium text-surface-100 truncate">
              {achievement.title}
            </p>
            <span className="text-xs text-surface-500">
              {achievement.points} pts
            </span>
          </div>
          <p className="text-xs text-surface-400 line-clamp-2">
            {achievement.description}
          </p>
          <p className="text-xs text-surface-600 italic mt-1.5">
            Not yet unlocked
          </p>
        </div>
      </div>
    </div>
  );
}

function TimelineSkeleton() {
  return (
    <div className="space-y-4" data-testid="timeline-skeleton">
      {Array.from({ length: 4 }, (_, i) => (
        <div key={i} className="flex items-start gap-3">
          <Skeleton className="h-3 w-3 rounded-full flex-shrink-0" />
          <div className="flex items-start gap-3 flex-1">
            <Skeleton className="h-10 w-10 rounded flex-shrink-0" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-3 w-full" />
              <Skeleton className="h-3 w-24" />
            </div>
          </div>
        </div>
      ))}
    </div>
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
        <p className="text-xs text-surface-500 mt-1">
          {achievement.points} pts
        </p>
      </div>
    </div>
  );
}

function groupByDate(
  entries: AchievementTimelineEntry[],
): [string, AchievementTimelineEntry[]][] {
  const groups = new Map<string, AchievementTimelineEntry[]>();

  // Sort newest first
  const sorted = [...entries].sort(
    (a, b) =>
      new Date(b.unlockedAt).getTime() - new Date(a.unlockedAt).getTime(),
  );

  for (const entry of sorted) {
    const dateLabel = formatDate(entry.unlockedAt);
    const existing = groups.get(dateLabel);
    if (existing) {
      existing.push(entry);
    } else {
      groups.set(dateLabel, [entry]);
    }
  }

  return Array.from(groups.entries());
}
