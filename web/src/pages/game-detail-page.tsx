import { useParams, useNavigate } from "react-router-dom";
import { FolderPlus } from "lucide-react";
import {
  Button,
  BackButton,
  GameDetailSkeleton,
  DropdownMenu,
} from "@/components/ui";
import { useToast } from "@/components/ui";
import {
  useGame,
  useGameSaves,
  useToggleFavorite,
  useScrapeIfNeeded,
} from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { useAuth } from "@/hooks/use-auth";
import { useScrapeGame } from "@/hooks/use-admin";
import { useConsoles } from "@/hooks/use-consoles";
import { useEffect } from "react";
import {
  useMyCollections,
  useAddGameToCollection,
} from "@/hooks/use-collections";
import { GameHero } from "@/features/game-detail/components/game-hero";
import { GameScreenshots } from "@/features/game-detail/components/game-screenshots";
import { GameCommunityStats } from "@/features/game-detail/components/game-community-stats";
import { SaveStatesList } from "@/features/game-detail/components/save-states-list";
import { GameAchievements } from "@/features/game-detail/components/game-achievements";
import { GameAchievementLeaderboard } from "@/features/game-detail/components/game-achievement-leaderboard";
import { UserRating } from "@/features/game-detail/components/user-rating";
import { RatingSummaryCard } from "@/features/game-detail/components/rating-summary";
import { GameReviews } from "@/features/game-detail/components/game-reviews";
import { SharedSavesList } from "@/features/game-detail/components/shared-saves-list";
import { GameActiveRelays } from "@/features/relays/components/game-active-relays";
import { GameChallenges } from "@/features/challenges/components/game-challenges";
import { useGameAchievements } from "@/hooks/use-retroachievements";
import type { Collection } from "@/types/api";

function AddToCollectionButton({
  gameId,
  menuItem,
}: {
  gameId: string;
  menuItem?: boolean;
}) {
  const { data: collectionsData } = useMyCollections(1, 100);
  const addGame = useAddGameToCollection();
  const { toast } = useToast();

  const collections = collectionsData?.data ?? [];

  function handleAdd(collection: Collection) {
    addGame.mutate(
      { collectionId: collection.id, gameId },
      {
        onSuccess: () => {
          toast("success", `Added to ${collection.name}`);
        },
        onError: () => {
          toast("error", "Failed to add to collection");
        },
      },
    );
  }

  return (
    <DropdownMenu
      align="right"
      className="w-64"
      trigger={
        menuItem ? (
          <Button
            variant="ghost"
            size="sm"
            className="w-full justify-start rounded-none"
          >
            <FolderPlus className="h-4 w-4" />
            Add to Collection
          </Button>
        ) : (
          <Button variant="secondary" size="sm">
            <FolderPlus className="h-5 w-5" />
            Add to Collection
          </Button>
        )
      }
    >
      {collections.length === 0 ? (
        <p className="px-3 py-2 text-sm text-surface-500">
          No collections yet. Create one first.
        </p>
      ) : (
        collections.map((collection) => (
          <Button
            key={collection.id}
            variant="ghost"
            size="sm"
            onClick={() => handleAdd(collection)}
            className="w-full justify-between rounded-none"
          >
            <span className="truncate">{collection.name}</span>
            <span className="text-xs text-surface-500 flex-shrink-0 ml-2">
              {collection.gameCount} games
            </span>
          </Button>
        ))
      )}
    </DropdownMenu>
  );
}

export function GameDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: game, isLoading } = useGame(id ?? "");
  const { data: saves } = useGameSaves(id ?? "");
  const toggleFavorite = useToggleFavorite();
  const togglePlayLater = useTogglePlayLater();
  const { user: currentUser } = useAuth();
  const scrapeGame = useScrapeGame();
  const scrapeIfNeeded = useScrapeIfNeeded();
  const { data: consoles } = useConsoles();
  const isAdmin =
    currentUser?.role === "admin" || currentUser?.role === "owner";

  const { data: gameAchievements } = useGameAchievements(id);
  const consoleInfo = consoles?.find((c) => c.id === game?.consoleId);
  const canPlayInBrowser = !!consoleInfo?.emulatorJsCore;
  const hasAchievements = (gameAchievements?.achievements?.length ?? 0) > 0;

  useEffect(() => {
    if (game && game.scrapeAttempts === 0) {
      scrapeIfNeeded.mutate(game.id);
    }
  }, [game?.id, game?.scrapeAttempts]);

  const isFavorite = game?.isFavorite ?? false;
  const isInPlayLater = game?.isInPlayLater ?? false;

  if (isLoading) {
    return (
      <div className="max-w-5xl">
        <GameDetailSkeleton aspectRatio={consoleInfo?.coverAspectRatio} />
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
      <BackButton onClick={() => navigate(-1)} />

      <GameHero
        game={game}
        aspectRatio={consoleInfo?.coverAspectRatio}
        canPlayInBrowser={canPlayInBrowser}
        isAdmin={isAdmin}
        isFavorite={isFavorite}
        isInPlayLater={isInPlayLater}
        isPlayLaterPending={togglePlayLater.isPending}
        isScraping={scrapeGame.isPending}
        hasAchievements={hasAchievements}
        hasSaves={(saves?.length ?? 0) > 0}
        extraButtons={<AddToCollectionButton gameId={game.id} />}
        extraMenuButtons={<AddToCollectionButton gameId={game.id} menuItem />}
        onPlay={() => navigate(`/games/${game.id}/play`)}
        onPlayFresh={() => navigate(`/games/${game.id}/play?fresh=true`)}
        onScrape={() => scrapeGame.mutate(game.id)}
        onToggleFavorite={() =>
          toggleFavorite.mutate({ gameId: game.id, isFavorite })
        }
        onTogglePlayLater={() =>
          togglePlayLater.mutate({ gameId: game.id, isInPlayLater })
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

      <GameChallenges gameId={game.id} saves={saves} />

      <GameActiveRelays gameId={game.id} />
    </div>
  );
}
