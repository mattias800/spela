import { Play, Heart, Clock, ChevronRight, Gamepad2, Trophy, Activity, Users } from "lucide-react";
import { Link } from "react-router-dom";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import { Badge, GameCardSkeleton, Skeleton, EmptyState } from "@/components/ui";
import { PersonalStatsCard } from "@/components/dashboard/personal-stats-card";
import { useRecentGames, useFavoriteGames, useToggleFavorite, useGames } from "@/hooks/use-games";
import { useRecentAchievements } from "@/hooks/use-retroachievements";
import { useAuth } from "@/hooks/use-auth";
import { ActivityFeed } from "@/components/social/activity-feed";
import { OnlineUsers } from "@/components/social/online-users";
import { formatRelativeTime } from "@/lib/format";
import type { Game, RecentAchievement } from "@/types/api";

function SectionHeader({
  title,
  icon: Icon,
  linkTo,
}: {
  title: string;
  icon: typeof Play;
  linkTo?: string;
}) {
  return (
    <div className="flex items-center justify-between mb-5">
      <div className="flex items-center gap-2.5">
        <Icon className="h-5 w-5 text-brand-400" />
        <h2 className="text-xl font-bold text-surface-100">{title}</h2>
      </div>
      {linkTo && (
        <Link
          to={linkTo}
          className="flex items-center gap-1 text-sm text-surface-400 hover:text-brand-400 transition-colors"
        >
          View all
          <ChevronRight className="h-4 w-4" />
        </Link>
      )}
    </div>
  );
}

function GameRow({
  games,
  isLoading,
  onToggleFavorite,
}: {
  games: Game[] | undefined;
  isLoading: boolean;
  onToggleFavorite: (game: Game) => void;
}) {
  if (isLoading) {
    return (
      <GameGrid>
        {Array.from({ length: 6 }, (_, i) => (
          <GameCardSkeleton key={i} />
        ))}
      </GameGrid>
    );
  }

  if (!games || games.length === 0) return null;

  return (
    <GameGrid>
      {games.slice(0, 6).map((game) => (
        <GameCard
          key={game.id}
          game={game}
          onToggleFavorite={onToggleFavorite}
        />
      ))}
    </GameGrid>
  );
}

function RecentAchievementsSkeleton() {
  return (
    <div className="flex gap-4 overflow-x-auto pb-2">
      {Array.from({ length: 4 }, (_, i) => (
        <div
          key={i}
          className="flex-shrink-0 w-48 rounded-xl bg-surface-800/50 p-4 space-y-3"
        >
          <Skeleton className="h-12 w-12 rounded" />
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-3 w-24" />
          <Skeleton className="h-3 w-16" />
        </div>
      ))}
    </div>
  );
}

function RecentAchievementCard({
  achievement,
}: {
  achievement: RecentAchievement;
}) {
  return (
    <Link
      to={`/games/${achievement.gameId}`}
      className="flex-shrink-0 w-48 rounded-xl bg-surface-800/50 p-4 hover:bg-surface-800/80 transition-colors"
      data-testid={`recent-achievement-${achievement.achievementRaId}`}
    >
      <img
        src={achievement.badgeUrl}
        alt={achievement.title}
        className="h-12 w-12 rounded"
      />
      <p className="text-sm font-medium text-surface-100 truncate mt-3">
        {achievement.title}
      </p>
      <p className="text-xs text-surface-500 truncate">
        {achievement.gameTitle}
      </p>
      <div className="flex items-center gap-2 mt-2">
        <span className="text-xs text-surface-400">
          {formatRelativeTime(achievement.unlockedAt)}
        </span>
        <Badge variant="brand" className="text-[10px] px-1.5 py-0">
          {achievement.points} pts
        </Badge>
      </div>
      {achievement.isHardcore && (
        <Badge variant="warning" className="text-[10px] px-1.5 py-0 mt-1">
          Hardcore
        </Badge>
      )}
    </Link>
  );
}

function RecentAchievementsSection() {
  const { data: achievements, isLoading } = useRecentAchievements();

  if (isLoading) {
    return (
      <section data-testid="recent-achievements-section">
        <SectionHeader title="Recent Achievements" icon={Trophy} linkTo="/stats" />
        <RecentAchievementsSkeleton />
      </section>
    );
  }

  if (!achievements || achievements.length === 0) {
    return null;
  }

  return (
    <section data-testid="recent-achievements-section">
      <SectionHeader title="Recent Achievements" icon={Trophy} linkTo="/stats" />
      <div className="flex gap-4 overflow-x-auto pb-2">
        {achievements.slice(0, 5).map((achievement) => (
          <RecentAchievementCard
            key={achievement.achievementRaId}
            achievement={achievement}
          />
        ))}
      </div>
    </section>
  );
}

export function DashboardPage() {
  const { user } = useAuth();
  const recentGames = useRecentGames();
  const favoriteGames = useFavoriteGames();
  const allGames = useGames({ pageSize: 12, sortBy: "title", sortOrder: "asc" });
  const { toggle: handleToggleFavorite } = useToggleFavorite();

  const hasRecent = recentGames.data && recentGames.data.length > 0;
  const hasFavorites = favoriteGames.data && favoriteGames.data.length > 0;
  const hasGames = allGames.data && allGames.data.data && allGames.data.data.length > 0;
  const isLoading = recentGames.isLoading || favoriteGames.isLoading || allGames.isLoading;
  const showEmptyState = !isLoading && !hasRecent && !hasFavorites && !hasGames;

  return (
    <div className="space-y-10">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">
          Welcome back, {user?.username}
        </h1>
        <p className="mt-1 text-surface-400">
          Pick up where you left off or discover something new.
        </p>
      </div>

      <PersonalStatsCard />

      <RecentAchievementsSection />

      {showEmptyState && (
        <EmptyState
          icon={Play}
          title="No games yet"
          description="Your game library is empty. Ask an admin to scan for games or add some ROMs to get started."
        />
      )}

      {(recentGames.isLoading || hasRecent) && (
        <section>
          <SectionHeader title="Continue Playing" icon={Clock} linkTo="/games" />
          <GameRow
            games={recentGames.data}
            isLoading={recentGames.isLoading}
            onToggleFavorite={handleToggleFavorite}

          />
        </section>
      )}

      {(favoriteGames.isLoading || hasFavorites) && (
        <section>
          <SectionHeader title="Favorites" icon={Heart} linkTo="/favorites" />
          <GameRow
            games={favoriteGames.data}
            isLoading={favoriteGames.isLoading}
            onToggleFavorite={handleToggleFavorite}

          />
        </section>
      )}

      {!hasRecent && !hasFavorites && (allGames.isLoading || hasGames) && (
        <section>
          <SectionHeader title="Discover" icon={Gamepad2} linkTo="/games" />
          <GameRow
            games={allGames.data?.data}
            isLoading={allGames.isLoading}
            onToggleFavorite={handleToggleFavorite}

          />
        </section>
      )}

      {/* Social widgets */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2">
          <ActivityFeed compact maxItems={5} />
        </div>
        <div className="lg:col-span-1">
          <div className="rounded-2xl bg-surface-900/50 p-5">
            <OnlineUsers />
          </div>
        </div>
      </div>
    </div>
  );
}
