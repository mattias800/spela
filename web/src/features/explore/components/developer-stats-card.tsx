import { Link } from "react-router-dom";
import { Clock, Gamepad2, Heart, Trophy } from "lucide-react";
import { formatPlayTime } from "@/lib/format";
import type { EntityUserStats } from "@/types/api";

interface DeveloperStatsCardProps {
  userStats: EntityUserStats;
  totalGames: number;
}

export function DeveloperStatsCard({
  userStats,
  totalGames,
}: DeveloperStatsCardProps) {
  return (
    <section data-comp="DeveloperStatsCard" data-testid="developer-user-stats">
      <h2 className="text-xl font-bold text-surface-100 mb-4">Your Stats</h2>
      <div className="rounded-xl border border-white/[0.06] bg-surface-900/50 p-5">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-4">
          {/* Total play time */}
          <div className="flex items-start gap-3">
            <div className="flex-shrink-0 p-2 rounded-lg bg-brand-500/10">
              <Clock className="h-4 w-4 text-brand-400" />
            </div>
            <div>
              <p className="text-sm font-semibold text-surface-100">
                {formatPlayTime(userStats.totalPlayTime)}
              </p>
              <p className="text-xs text-surface-500">Play time</p>
            </div>
          </div>

          {/* Games played */}
          <div className="flex items-start gap-3">
            <div className="flex-shrink-0 p-2 rounded-lg bg-emerald-500/10">
              <Gamepad2 className="h-4 w-4 text-emerald-400" />
            </div>
            <div>
              <p className="text-sm font-semibold text-surface-100">
                {userStats.gamesPlayed}/{totalGames}
              </p>
              <p className="text-xs text-surface-500">Games played</p>
            </div>
          </div>

          {/* Favorites */}
          <div className="flex items-start gap-3">
            <div className="flex-shrink-0 p-2 rounded-lg bg-danger-500/10">
              <Heart className="h-4 w-4 text-danger-500" />
            </div>
            <div>
              <p className="text-sm font-semibold text-surface-100">
                {userStats.favoriteCount}
              </p>
              <p className="text-xs text-surface-500">Favorites</p>
            </div>
          </div>

          {/* Most played */}
          {userStats.mostPlayedGame && (
            <div className="flex items-start gap-3">
              <div className="flex-shrink-0 p-2 rounded-lg bg-amber-500/10">
                <Trophy className="h-4 w-4 text-amber-400" />
              </div>
              <div className="min-w-0">
                <p className="text-xs text-surface-500">Most played</p>
                <Link
                  to={`/games/${userStats.mostPlayedGame.id}`}
                  className="text-sm font-semibold text-surface-100 hover:text-brand-400 transition-colors truncate block"
                >
                  {userStats.mostPlayedGame.title}
                </Link>
              </div>
            </div>
          )}
        </div>

        {/* Most played game with cover art */}
        {userStats.mostPlayedGame?.coverUrl && (
          <Link
            to={`/games/${userStats.mostPlayedGame.id}`}
            className="flex items-center gap-3 mt-3 pt-3 border-t border-white/[0.06] group"
            data-testid="developer-most-played-game"
          >
            <img
              src={userStats.mostPlayedGame.coverUrl}
              alt={userStats.mostPlayedGame.title}
              className="w-10 h-14 rounded-lg object-cover flex-shrink-0"
              loading="lazy"
            />
            <div className="min-w-0">
              <p className="text-sm font-semibold text-surface-100 group-hover:text-brand-400 transition-colors truncate">
                {userStats.mostPlayedGame.title}
              </p>
              <p className="text-xs text-surface-500">
                {formatPlayTime(userStats.mostPlayedGame.totalPlayTime)}
              </p>
            </div>
          </Link>
        )}
      </div>
    </section>
  );
}
