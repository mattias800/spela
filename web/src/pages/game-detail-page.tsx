import { useParams, useNavigate } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { Button, GameDetailSkeleton } from "@/components/ui";
import { useGame, useGameSaves, useToggleFavorite, useScrapeIfNeeded } from "@/hooks/use-games";
import { useAuth } from "@/hooks/use-auth";
import { useScrapeGame } from "@/hooks/use-admin";
import { useConsoles } from "@/hooks/use-consoles";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { GameHero } from "@/components/game-detail/game-hero";
import { GameScreenshots } from "@/components/game-detail/game-screenshots";
import { GameCommunityStats } from "@/components/game-detail/game-community-stats";
import { SaveStatesList } from "@/components/game-detail/save-states-list";
import { GameAchievements } from "@/components/game-detail/game-achievements";
import { useGameAchievements } from "@/hooks/use-retroachievements";

export function GameDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: game, isLoading } = useGame(id ?? "");
  const { data: saves } = useGameSaves(id ?? "");
  const toggleFavorite = useToggleFavorite();
  const { user: currentUser } = useAuth();
  const scrapeGame = useScrapeGame();
  const scrapeIfNeeded = useScrapeIfNeeded();
  const queryClient = useQueryClient();
  const { data: consoles } = useConsoles();
  const isAdmin = currentUser?.role === "admin" || currentUser?.role === "owner";

  const { data: gameAchievements } = useGameAchievements(id);
  const consoleInfo = consoles?.find((c) => c.id === game?.consoleId);
  const canPlayInBrowser = !!consoleInfo?.emulatorJsCore;
  const hasAchievements = (gameAchievements?.achievements.length ?? 0) > 0;

  useEffect(() => {
    if (game && game.scrapeAttempts === 0) {
      scrapeIfNeeded.mutate(game.id, {
        onSuccess: () => {
          setTimeout(() => {
            queryClient.invalidateQueries({ queryKey: ["game", game.id] });
          }, 4000);
        },
      });
    }
  }, [game?.id, game?.scrapeAttempts]);

  useWebSocketEvent("game_scraped", (payload: { id?: string }) => {
    if (payload.id === id) {
      queryClient.invalidateQueries({ queryKey: ["game", id] });
    }
  });

  const isFavorite = game?.isFavorite ?? false;

  if (isLoading) {
    return (
      <div className="max-w-5xl">
        <GameDetailSkeleton />
      </div>
    );
  }

  if (!game) {
    return (
      <div className="text-center py-20">
        <p className="text-surface-400">Game not found</p>
        <Button variant="ghost" onClick={() => navigate(-1)} className="mt-4">
          Go back
        </Button>
      </div>
    );
  }

  return (
    <div className="max-w-5xl space-y-8">
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-2 text-sm text-surface-400 hover:text-surface-100 transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        Back
      </button>

      <GameHero
        game={game}
        canPlayInBrowser={canPlayInBrowser}
        isAdmin={isAdmin}
        isFavorite={isFavorite}
        isScraping={scrapeGame.isPending}
        hasAchievements={hasAchievements}
        onPlay={() => navigate(`/games/${game.id}/play`)}
        onScrape={() => scrapeGame.mutate(game.id)}
        onToggleFavorite={() =>
          toggleFavorite.mutate({ gameId: game.id, isFavorite })
        }
      />

      <GameCommunityStats gameId={game.id} game={game} />

      <GameScreenshots
        screenshotUrls={game.screenshotUrls}
        gameTitle={game.title}
      />

      <GameAchievements gameId={game.id} />

      <SaveStatesList saves={saves} gameId={game.id} />
    </div>
  );
}
