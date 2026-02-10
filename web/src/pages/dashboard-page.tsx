import { Play, Heart, Clock, ChevronRight, Gamepad2 } from "lucide-react";
import { Link } from "react-router-dom";
import { GameCard } from "@/components/game-card";
import { GameCardSkeleton, EmptyState } from "@/components/ui";
import { useRecentGames, useFavoriteGames, useToggleFavorite, useGames } from "@/hooks/use-games";
import { useConsoles } from "@/hooks/use-consoles";
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
  onToggleFavorite,
  browserPlayableConsoles,
}: {
  games: Game[] | undefined;
  isLoading: boolean;
  onToggleFavorite: (game: Game) => void;
  browserPlayableConsoles?: Set<string>;
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
          canPlayInBrowser={browserPlayableConsoles?.has(game.consoleId)}
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
  const allGames = useGames({ pageSize: 12, sortBy: "title", sortOrder: "asc" });
  const toggleFavorite = useToggleFavorite();
  const { data: consoles } = useConsoles();

  const browserPlayableConsoles = new Set(
    consoles?.filter((c) => !!c.emulatorJsCore).map((c) => c.id) ?? [],
  );

  function handleToggleFavorite(game: Game) {
    toggleFavorite.mutate({ gameId: game.id, isFavorite: game.isFavorite });
  }

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
            browserPlayableConsoles={browserPlayableConsoles}
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
            browserPlayableConsoles={browserPlayableConsoles}
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
            browserPlayableConsoles={browserPlayableConsoles}
          />
        </section>
      )}
    </div>
  );
}
