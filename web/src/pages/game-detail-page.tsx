import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Button,
  BackButton,
  GameDetailSkeleton,
  Modal,
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
import { CoverArtSelector } from "@/features/game-detail/components/cover-art-selector";
import { useGameAchievements } from "@/hooks/use-retroachievements";
import { useBiosStatus } from "@/hooks/use-bios";
import { BiosWarningBanner } from "@/features/bios/components/bios-warning-banner";
import type { Collection } from "@/types/api";

function CollectionPickerModal({
  gameId,
  open,
  onClose,
}: {
  gameId: string;
  open: boolean;
  onClose: () => void;
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
          onClose();
        },
        onError: () => {
          toast("error", "Failed to add to collection");
        },
      },
    );
  }

  return (
    <Modal open={open} onClose={onClose} title="Add to Collection" size="sm">
      <div className="max-h-64 overflow-y-auto -mx-6 -mb-6 px-6 pb-6">
        {collections.length === 0 ? (
          <p className="py-4 text-sm text-surface-500 text-center">
            No collections yet. Create one first.
          </p>
        ) : (
          <div className="space-y-1">
            {collections.map((collection) => (
              <button
                key={collection.id}
                onClick={() => handleAdd(collection)}
                className="w-full flex items-center justify-between px-3 py-2.5 text-sm text-surface-200 hover:bg-surface-800 hover:text-surface-100 rounded-lg transition-colors cursor-pointer"
              >
                <span className="truncate">{collection.name}</span>
                <span className="text-xs text-surface-500 flex-shrink-0 ml-2">
                  {collection.gameCount} games
                </span>
              </button>
            ))}
          </div>
        )}
      </div>
    </Modal>
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

  const { data: biosData } = useBiosStatus();
  const { data: gameAchievements } = useGameAchievements(id);
  const consoleInfo = consoles?.find((c) => c.id === game?.consoleId);
  const canPlayInBrowser = !!consoleInfo?.emulatorJsCore;
  const hasAchievements = (gameAchievements?.achievements?.length ?? 0) > 0;
  const [showCollectionPicker, setShowCollectionPicker] = useState(false);

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

  const biosConsole = biosData?.consoles.find(
    (c) => c.consoleId === game?.consoleId,
  );
  const showBiosWarning =
    game?.biosStatus === "missing" ||
    (biosConsole?.status === "missing" && biosConsole.biosRequired);
  const missingBiosFiles =
    biosConsole?.files
      .filter((f) => f.status === "missing" && f.required)
      .map((f) => f.fileName) ?? [];

  return (
    <div className="max-w-5xl space-y-8">
      <BackButton onClick={() => navigate(-1)} />

      {showBiosWarning && (
        <BiosWarningBanner
          message={`Missing BIOS: ${game!.consoleName} requires firmware files to play. ${missingBiosFiles.length > 0 ? missingBiosFiles.join(", ") + " not found." : ""}`}
          isAdmin={isAdmin}
          missingFiles={missingBiosFiles}
        />
      )}

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
        onPlay={() => navigate(`/games/${game.id}/play`)}
        onPlayFresh={() => navigate(`/games/${game.id}/play?fresh=true`)}
        onScrape={() => scrapeGame.mutate(game.id)}
        onToggleFavorite={() =>
          toggleFavorite.mutate({ gameId: game.id, isFavorite })
        }
        onTogglePlayLater={() =>
          togglePlayLater.mutate({ gameId: game.id, isInPlayLater })
        }
        onAddToCollection={() => setShowCollectionPicker(true)}
      />

      <CollectionPickerModal
        gameId={game.id}
        open={showCollectionPicker}
        onClose={() => setShowCollectionPicker(false)}
      />

      {isAdmin && (
        <CoverArtSelector
          gameId={game.id}
          aspectRatio={consoleInfo?.coverAspectRatio}
        />
      )}

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

      <GameAchievements gameId={game.id} achievementsWarning={game.achievementsWarning} />

      <SaveStatesList saves={saves} gameId={game.id} />

      <SharedSavesList gameId={game.id} />

      <GameChallenges gameId={game.id} saves={saves} />

      <GameActiveRelays gameId={game.id} />
    </div>
  );
}
