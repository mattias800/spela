import { Play, Heart, Clock, ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton, EmptyState } from "@/components/ui";
import { useRecentGames, useFavoriteGames, useToggleFavorite } from "@/hooks/use-games";
import { useAuth } from "@/hooks/use-auth";
import type { Game } from "@/types/api";

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
  favoriteIds,
  onToggleFavorite,
}: {
  games: Game[] | undefined;
  isLoading: boolean;
  favoriteIds: Set<number>;
  onToggleFavorite: (game: Game) => void;
}) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
        {Array.from({ length: 6 }, (_, i) => (
          <GameCardSkeleton key={i} />
        ))}
      </div>
    );
  }

  if (!games || games.length === 0) return null;

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
      {games.slice(0, 6).map((game) => (
        <GameCard
          key={game.id}
          game={game}
          isFavorite={favoriteIds.has(game.id)}
          onToggleFavorite={onToggleFavorite}
        />
      ))}
    </div>
  );
}

export function DashboardPage() {
  const { user } = useAuth();
  const recentGames = useRecentGames();
  const favoriteGames = useFavoriteGames();
  const toggleFavorite = useToggleFavorite();

  const favoriteIds = new Set(favoriteGames.data?.map((g) => g.id) ?? []);

  function handleToggleFavorite(game: Game) {
    toggleFavorite.mutate({ gameId: game.id, isFavorite: favoriteIds.has(game.id) });
  }

  const hasRecent = recentGames.data && recentGames.data.length > 0;
  const hasFavorites = favoriteGames.data && favoriteGames.data.length > 0;
  const showEmptyState =
    !recentGames.isLoading &&
    !favoriteGames.isLoading &&
    !hasRecent &&
    !hasFavorites;

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
            favoriteIds={favoriteIds}
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
            favoriteIds={favoriteIds}
            onToggleFavorite={handleToggleFavorite}
          />
        </section>
      )}
    </div>
  );
}
