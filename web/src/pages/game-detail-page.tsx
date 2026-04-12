import { useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import {
  Button,
  BackButton,
  Section,
  GameDetailSkeleton,
  Modal,
} from "@/components/ui";
import { PageLayout, SectionList } from "@/components/layout";
import { useToast } from "@/components/ui";
import {
  useGame,
  useToggleFavorite,
  useScrapeIfNeeded,
} from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { useAuth } from "@/hooks/use-auth";
import { useScrapeGame, useRefreshAchievements } from "@/hooks/use-admin";
import { useConsoles } from "@/hooks/use-consoles";
import { useEffect } from "react";
import {
  useMyCollections,
  useAddGameToCollection,
} from "@/hooks/use-collections";
import { GameHero } from "@/features/game-detail/components/game-hero";
import { GameScreenshots } from "@/features/game-detail/components/game-screenshots";
import { GameCommunityStats } from "@/features/game-detail/components/game-community-stats";
import { GameAchievementLeaderboard } from "@/features/game-detail/components/game-achievement-leaderboard";
import { RatingSummaryCard } from "@/features/game-detail/components/rating-summary";
import { GameReviews } from "@/features/game-detail/components/game-reviews";
import { SharedSavesList } from "@/features/game-detail/components/shared-saves-list";
import { GameActiveSharedSessions } from "@/features/shared-sessions/components/game-active-shared-sessions";
import { GameSessions } from "@/features/sessions/components/game-sessions";
import { useGameSessions } from "@/hooks/use-sessions";
import { GameChallenges } from "@/features/challenges/components/game-challenges";
import {
  useGameAchievements,
  useGameAchievementProgress,
} from "@/hooks/use-retroachievements";
import { useGameSeries, useGameFranchises } from "@/hooks/use-explore";
import { useBiosStatus } from "@/hooks/use-bios";
import { BiosWarningBanner } from "@/features/bios/components/bios-warning-banner";
import { ScrapeMatchModal } from "@/features/game-detail/components/scrape-match-modal";
import { ReplaceRomModal } from "@/features/game-detail/components/replace-rom-modal";
import { TimeToBeatCard } from "@/features/game-detail/components/time-to-beat-card";
import { GameVariantsSection } from "@/features/game-detail/components/game-variants-section";
import { BasedOnLink } from "@/features/game-detail/components/based-on-link";
import { StandaloneRomHacksSection } from "@/features/game-detail/components/standalone-rom-hacks-section";
import { api } from "@/lib/api-client";
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
  const toggleFavorite = useToggleFavorite();
  const togglePlayLater = useTogglePlayLater();
  const { user: currentUser } = useAuth();
  const scrapeGame = useScrapeGame();
  const refreshAchievements = useRefreshAchievements();
  const scrapeIfNeeded = useScrapeIfNeeded();
  const { data: consoles } = useConsoles();
  const isAdmin =
    currentUser?.role === "admin" || currentUser?.role === "owner";

  const { data: biosData } = useBiosStatus();
  const { data: sessions } = useGameSessions(id ?? "");
  const { data: gameAchievements } = useGameAchievements(id);
  const { data: achievementProgress } = useGameAchievementProgress(id);
  const { data: gameSeries } = useGameSeries(id);
  const { data: gameFranchises } = useGameFranchises(id);
  const consoleInfo = consoles?.find((c) => c.id === game?.consoleId);
  const canPlayInBrowser = !!consoleInfo?.emulatorJsCore;
  const hasAchievements = (gameAchievements?.achievements?.length ?? 0) > 0;
  const achievementCount = gameAchievements?.totalCount ?? 0;
  const achievementUnlocked = achievementProgress
    ? achievementProgress.length
    : undefined;
  const isDemo = consoleInfo?.abbreviation === "ADEMO" || consoleInfo?.abbreviation === "DDEMO";
  const [showCollectionPicker, setShowCollectionPicker] = useState(false);
  const [showScrapeMatch, setShowScrapeMatch] = useState(false);
  const [showReplaceRom, setShowReplaceRom] = useState(false);

  useEffect(() => {
    if (game && game.scrapeAttempts === 0) {
      scrapeIfNeeded.mutate(game.id);
    }
  }, [game?.id, game?.scrapeAttempts]);

  const isFavorite = game?.isFavorite ?? false;
  const isInPlayLater = game?.isInPlayLater ?? false;
  const isPlayable = game?.playable ?? true;

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
    isPlayable &&
    (game?.biosStatus === "missing" ||
      (biosConsole?.status === "missing" && biosConsole.biosRequired));
  const missingBiosFiles =
    biosConsole?.files
      .filter((f) => f.status === "missing" && f.required)
      .map((f) => f.fileName) ?? [];

  function handleDownloadRom() {
    if (!game) return;
    const token = api.getAccessToken();
    const tokenSuffix = token
      ? `?token=${encodeURIComponent(token)}`
      : "";
    window.open(`/api/games/${game.id}/download${tokenSuffix}`, "_blank");
  }

  return (
    <PageLayout
      renderHeader={() => (
        <GameHero
        game={game}
        canPlayInBrowser={canPlayInBrowser}
        isAdmin={isAdmin}
        isFavorite={isFavorite}
        isInPlayLater={isInPlayLater}
        isPlayLaterPending={togglePlayLater.isPending}
        isScraping={scrapeGame.isPending}
        hasAchievements={hasAchievements}
        achievementCount={achievementCount}
        achievementUnlocked={achievementUnlocked}
        biosMissing={showBiosWarning}
        isDemo={isDemo}
        onPlay={() => navigate(`/games/${game.id}/play/${sessions && sessions.length > 0 ? sessions[0].id : "new"}`)}
        onScrape={() => scrapeGame.mutate(game.id)}
        onRefreshAchievements={() => refreshAchievements.mutate(game.id)}
        isRefreshingAchievements={refreshAchievements.isPending}
        onToggleFavorite={() =>
          toggleFavorite.mutate({ gameId: game.id, isFavorite })
        }
        onTogglePlayLater={() =>
          togglePlayLater.mutate({ gameId: game.id, isInPlayLater })
        }
        onAddToCollection={() => setShowCollectionPicker(true)}
        onFixMatch={
          isAdmin && game.scrapeAttempts > 0
            ? () => setShowScrapeMatch(true)
            : undefined
        }
        onDownloadRom={!isPlayable ? handleDownloadRom : undefined}
        onReplaceRom={
          isAdmin && game.verificationStatus !== "verified"
            ? () => setShowReplaceRom(true)
            : undefined
        }
      />
      )}
    >
      <SectionList className="max-w-5xl">
      <BackButton onClick={() => navigate(-1)} />

      {showBiosWarning && (
        <BiosWarningBanner
          message={`Missing BIOS: ${game!.consoleName} requires firmware files to play. ${missingBiosFiles.length > 0 ? missingBiosFiles.join(", ") + " not found." : ""}`}
          isAdmin={isAdmin}
          missingFiles={missingBiosFiles}
        />
      )}

      <ScrapeMatchModal
        gameId={game.id}
        currentTitle={game.title}
        fileName={game.fileName}
        currentScraperId={game.scraperId}
        open={showScrapeMatch}
        onClose={() => setShowScrapeMatch(false)}
      />

      <ReplaceRomModal
        gameId={game.id}
        open={showReplaceRom}
        onClose={() => setShowReplaceRom(false)}
      />

      <CollectionPickerModal
        gameId={game.id}
        open={showCollectionPicker}
        onClose={() => setShowCollectionPicker(false)}
      />

      {/* Based on / Series & Franchise links */}
      {(game.parentGame ||
        (gameSeries && gameSeries.length > 0) ||
        (gameFranchises && gameFranchises.length > 0)) && (
        <div
          className="flex flex-wrap items-center gap-3"
          data-testid="series-franchise-links"
        >
          {game.parentGame && <BasedOnLink parentGame={game.parentGame} />}
          {gameSeries?.map((s) => (
            <Link
              key={`series-${s.id}`}
              to={`/explore/series/${s.id}`}
              className="inline-flex items-center gap-1.5 text-sm text-surface-300 hover:text-brand-400 transition-colors bg-surface-900/60 border border-surface-800/50 rounded-lg px-3 py-1.5 hover:border-surface-700/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            >
              Part of{" "}
              <span className="font-semibold text-surface-100">{s.name}</span>
              <span className="text-xs text-surface-500">
                ({s.libraryGames}/{s.totalGames} games)
              </span>
            </Link>
          ))}
          {gameFranchises?.map((f) => (
            <Link
              key={`franchise-${f.id}`}
              to={`/explore/franchise/${f.id}`}
              className="inline-flex items-center gap-1.5 text-sm text-surface-300 hover:text-brand-400 transition-colors bg-surface-900/60 border border-surface-800/50 rounded-lg px-3 py-1.5 hover:border-surface-700/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            >
              Part of{" "}
              <span className="font-semibold text-surface-100">{f.name}</span>
              <span className="text-xs text-surface-500">
                ({f.libraryGames}/{f.totalGames} games)
              </span>
            </Link>
          ))}
        </div>
      )}

      {!isDemo && <TimeToBeatCard game={game} />}

      {!isDemo && <GameSessions gameId={game.id} />}

      {game.variants && game.variants.length > 0 && (
        <GameVariantsSection variants={game.variants} />
      )}

      {game.romHacks && game.romHacks.length > 0 && (
        <StandaloneRomHacksSection romHacks={game.romHacks} />
      )}

      <Section className="p-6">
        <GameCommunityStats gameId={game.id} game={game} />
      </Section>

      {/* Rating section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        <Section className="p-6">
          <RatingSummaryCard gameId={game.id} />
        </Section>
        <Section className="p-6">
          <GameReviews gameId={game.id} />
        </Section>
      </div>

      {isPlayable && !isDemo && <GameAchievementLeaderboard gameId={game.id} />}

      <GameScreenshots
        screenshotUrls={game.screenshotUrls}
        gameTitle={game.title}
      />

      {isPlayable && !isDemo && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <Section className="p-6">
            <SharedSavesList gameId={game.id} />
          </Section>
          <Section className="p-6">
            <GameChallenges gameId={game.id} />
          </Section>
        </div>
      )}

      {isPlayable && !isDemo && <GameActiveSharedSessions gameId={game.id} />}
    </SectionList>
    </PageLayout>
  );
}
