import { useParams, useNavigate } from "react-router-dom";
import { ArrowLeft, FolderPlus } from "lucide-react";
import { Button, GameDetailSkeleton } from "@/components/ui";
import { useToast } from "@/components/ui";
import { useGame, useGameSaves, useToggleFavorite, useScrapeIfNeeded } from "@/hooks/use-games";
import { useAuth } from "@/hooks/use-auth";
import { useScrapeGame } from "@/hooks/use-admin";
import { useConsoles } from "@/hooks/use-consoles";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import { useEffect, useState, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useMyCollections, useAddGameToCollection } from "@/hooks/use-collections";
import { GameHero } from "@/components/game-detail/game-hero";
import { GameScreenshots } from "@/components/game-detail/game-screenshots";
import { GameCommunityStats } from "@/components/game-detail/game-community-stats";
import { SaveStatesList } from "@/components/game-detail/save-states-list";
import { GameAchievements } from "@/components/game-detail/game-achievements";
import { GameAchievementLeaderboard } from "@/components/game-detail/game-achievement-leaderboard";
import { UserRating } from "@/components/game-detail/user-rating";
import { RatingSummaryCard } from "@/components/game-detail/rating-summary";
import { GameReviews } from "@/components/game-detail/game-reviews";
import { SharedSavesList } from "@/components/game-detail/shared-saves-list";
import { useGameAchievements } from "@/hooks/use-retroachievements";
import type { Collection } from "@/types/api";

function AddToCollectionButton({ gameId }: { gameId: string }) {
  const [open, setOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const { data: collectionsData } = useMyCollections(1, 100);
  const addGame = useAddGameToCollection();
  const { toast } = useToast();

  const collections = collectionsData?.data ?? [];

  useEffect(() => {
    if (!open) return;
    function handleClickOutside(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  function handleAdd(collection: Collection) {
    addGame.mutate(
      { collectionId: collection.id, gameId },
      {
        onSuccess: () => {
          toast("success", `Added to ${collection.name}`);
          setOpen(false);
        },
        onError: () => {
          toast("error", "Failed to add to collection");
        },
      },
    );
  }

  return (
    <div className="relative" ref={dropdownRef}>
      <Button
        variant="secondary"
        size="sm"
        onClick={() => setOpen(!open)}
      >
        <FolderPlus className="h-4 w-4" />
        Add to Collection
      </Button>
      {open && (
        <div className="absolute right-0 top-full mt-2 z-40 w-64 rounded-xl bg-surface-900 border border-surface-800 shadow-2xl py-1 max-h-64 overflow-y-auto">
          {collections.length === 0 ? (
            <p className="px-3 py-2 text-sm text-surface-500">
              No collections yet. Create one first.
            </p>
          ) : (
            collections.map((collection) => (
              <button
                key={collection.id}
                onClick={() => handleAdd(collection)}
                className="w-full text-left px-3 py-2 text-sm text-surface-200 hover:bg-surface-800 transition-colors flex items-center justify-between"
              >
                <span className="truncate">{collection.name}</span>
                <span className="text-xs text-surface-500 flex-shrink-0 ml-2">
                  {collection.gameCount} games
                </span>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}

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
        extraButtons={<AddToCollectionButton gameId={game.id} />}
        onPlay={() => navigate(`/games/${game.id}/play`)}
        onScrape={() => scrapeGame.mutate(game.id)}
        onToggleFavorite={() =>
          toggleFavorite.mutate({ gameId: game.id, isFavorite })
        }
      />

      <GameCommunityStats gameId={game.id} game={game} />

      {/* Rating section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-1 space-y-4">
          <UserRating gameId={game.id} />
          <RatingSummaryCard gameId={game.id} />
        </div>
        <div className="lg:col-span-2">
          <GameReviews gameId={game.id} />
        </div>
      </div>

      <GameAchievementLeaderboard gameId={game.id} />

      <GameScreenshots
        screenshotUrls={game.screenshotUrls}
        gameTitle={game.title}
      />

      <GameAchievements gameId={game.id} />

      <SaveStatesList saves={saves} gameId={game.id} />

      <SharedSavesList gameId={game.id} />
    </div>
  );
}
