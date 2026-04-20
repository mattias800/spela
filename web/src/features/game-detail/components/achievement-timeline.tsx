import { Clock } from "lucide-react";
import { Skeleton, Badge, StatCard } from "@/components/ui";
import { formatRelativeTime, formatPlayTime, formatDate } from "@/lib/format";
import type {
  Achievement,
  GameAchievementProgress as AchievementProgress,
  AchievementTimelineEntry,
} from "@/types/api";

interface TimelineViewProps {
  timeline:
    | {
        timeline: AchievementTimelineEntry[] | null;
        unlockedCount: number;
        earnedPoints: number;
      }
    | undefined;
  isLoading: boolean;
  achievements: Achievement[];
  progressMap: Map<number, AchievementProgress>;
  totalAchievements: number;
}

export function TimelineView({
  timeline,
  isLoading,
  achievements,
  progressMap,
  totalAchievements,
}: TimelineViewProps) {
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
              latestUnlock ? formatRelativeTime(latestUnlock.unlockedAt) : "--"
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
                <TimelineEntryCard key={entry.achievementRaId} entry={entry} />
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
            <span className="text-xs text-surface-500">{entry.points} pts</span>
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
