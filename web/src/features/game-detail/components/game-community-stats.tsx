import { Activity, Clock } from "lucide-react";
import { Skeleton, EmptyState, StatCard, LeaderboardRow } from "@/components/ui";
import { useGameStats } from "@/hooks/use-game-stats";
import { useAuth } from "@/hooks/use-auth";
import { formatPlayTime, formatRelativeTime } from "@/lib/format";
import { cn } from "@/lib/cn";
import { rankColor } from "@/lib/rank-utils";
import type { Game } from "@/types/api";

interface GameCommunityStatsProps {
  gameId: string;
  game: Game;
}

function CommunityStatsSkeleton() {
  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2.5">
        <Skeleton className="h-5 w-5" />
        <Skeleton className="h-6 w-32" />
      </div>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {Array.from({ length: 4 }, (_, i) => (
          <div key={i} className="bg-surface-800/30 rounded-xl p-4">
            <Skeleton className="h-6 w-16 mb-1" />
            <Skeleton className="h-3 w-20" />
          </div>
        ))}
      </div>
      <div className="space-y-2">
        {Array.from({ length: 5 }, (_, i) => (
          <div
            key={i}
            className="flex items-center gap-3 rounded-xl px-3 py-2.5 bg-surface-900/50"
          >
            <Skeleton className="h-5 w-5" />
            <Skeleton className="h-8 w-8 rounded-full" />
            <Skeleton className="h-4 w-28 flex-shrink-0" />
            <Skeleton className="h-3 flex-1" />
            <Skeleton className="h-4 w-14" />
          </div>
        ))}
      </div>
    </div>
  );
}

export function GameCommunityStats({ gameId, game }: GameCommunityStatsProps) {
  const { data: stats, isLoading } = useGameStats(gameId);
  const { user } = useAuth();

  if (isLoading) {
    return (
      <section>
        <CommunityStatsSkeleton />
      </section>
    );
  }

  if (!stats || stats.totalPlayers === 0) {
    return (
      <section>
        <h2 className="text-xl font-bold text-surface-100 flex items-center gap-2.5 mb-4">
          <Activity className="h-5 w-5 text-brand-400" />
          Play Activity
        </h2>
        <EmptyState
          icon={Clock}
          title="No one has played this yet"
          description="Be the first to play! Launch this game to start tracking your time."
        />
      </section>
    );
  }

  const maxPlayTime =
    stats.topPlayers.length > 0
      ? Math.max(...stats.topPlayers.map((p) => p.playTime))
      : 1;

  return (
    <section>
      <h2 className="text-xl font-bold text-surface-100 flex items-center gap-2.5 mb-5">
        <Activity className="h-5 w-5 text-brand-400" />
        Play Activity
      </h2>

      {/* Stat mini-cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        <StatCard
          value={
            game.totalPlayTime > 0
              ? formatPlayTime(game.totalPlayTime)
              : "Not played"
          }
          label="Your time"
          highlight
        />
        <StatCard
          value={
            game.lastPlayedAt ? formatRelativeTime(game.lastPlayedAt) : "Never"
          }
          label="Last played"
        />
        <StatCard
          value={`${stats.totalPlayers}`}
          label={
            stats.totalPlayers === 1
              ? "player has played"
              : "players have played"
          }
        />
        <StatCard
          value={formatPlayTime(stats.totalPlayTime)}
          label="Total time"
        />
      </div>

      {/* Top Players */}
      {stats.topPlayers.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-surface-400 uppercase tracking-wider mb-3">
            Top Players
          </h3>
          <div className="space-y-2">
            {stats.topPlayers.map((player, index) => {
              const rank = index + 1;
              const isCurrentUser = player.userId === user?.id;
              const barWidth =
                maxPlayTime > 0 ? (player.playTime / maxPlayTime) * 100 : 0;

              return (
                <LeaderboardRow
                  key={player.userId}
                  isCurrentUser={isCurrentUser}
                  username={player.username}
                  avatarUrl={player.avatarUrl}
                  rank={
                    <span
                      className={cn(
                        "text-sm font-bold w-5 text-right",
                        rankColor(rank),
                      )}
                    >
                      {rank}
                    </span>
                  }
                >
                  <div className="flex-1 h-3 rounded-full bg-brand-500/15 overflow-hidden">
                    <div
                      className="h-full bg-brand-500 rounded-full transition-all duration-700 ease-out"
                      style={{ width: `${barWidth}%` }}
                    />
                  </div>
                  <span className="text-sm text-surface-400 whitespace-nowrap w-16 text-right">
                    {formatPlayTime(player.playTime)}
                  </span>
                </LeaderboardRow>
              );
            })}
          </div>
        </div>
      )}
    </section>
  );
}
